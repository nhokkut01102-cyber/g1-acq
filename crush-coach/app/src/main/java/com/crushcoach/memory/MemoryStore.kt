package com.crushcoach.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.crushcoach.ai.ChatLine
import com.crushcoach.ai.ChatLogMerge
import com.crushcoach.ai.CrushMemory
import com.crushcoach.ai.JournalNote
import com.crushcoach.ai.LoggedMessage
import com.crushcoach.ai.Side
import com.crushcoach.ai.UsedSuggestion
import java.text.Normalizer

data class StoredMessage(val id: Long, val message: LoggedMessage)

data class StoredMemory(val chatKey: String, val title: String?, val memory: CrushMemory, val updatedAt: Long)

data class MemoryStats(val messages: Int, val undigested: Int, val lastDigestAt: Long?)

/**
 * Kho lưu trên máy (SQLite, không sao lưu lên cloud):
 * tin nhắn đã đọc với crush, gợi ý đã dùng, sổ tay cô đọng và nhật ký từng ngày.
 * Mọi hàm đều chạy đồng bộ — gọi từ luồng nền.
 */
class MemoryStore private constructor(context: Context) :
    SQLiteOpenHelper(context, "crush_memory.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE messages(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                chat_key TEXT NOT NULL, app TEXT, title TEXT,
                side TEXT NOT NULL, text TEXT NOT NULL,
                seen_at INTEGER NOT NULL, digested INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX idx_messages_chat ON messages(chat_key, id)")
        db.execSQL("CREATE TABLE choices(id INTEGER PRIMARY KEY AUTOINCREMENT, chat_key TEXT NOT NULL, text TEXT NOT NULL, at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE memory(chat_key TEXT PRIMARY KEY, title TEXT, json TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE journal(chat_key TEXT NOT NULL, day TEXT NOT NULL, note TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY(chat_key, day))")
        // state: 1 = người dùng bấm ghi nhớ, -1 = người dùng tắt ghi nhớ (kể cả khi trùng tên crush).
        db.execSQL("CREATE TABLE tracked(chat_key TEXT PRIMARY KEY, title TEXT, state INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ---- Chat nào được ghi nhớ ----

    /** Chat được ghi nhớ nếu người dùng bấm 📌, hoặc tên chat chứa tên crush (trừ khi đã tắt). */
    fun isTracked(chatKey: String, title: String?, crushName: String): Boolean {
        when (trackState(chatKey)) {
            1 -> return true
            -1 -> return false
        }
        val name = normalize(crushName)
        return name.length >= 2 && title != null && normalize(title).contains(name)
    }

    private fun trackState(chatKey: String): Int? =
        readableDatabase.rawQuery("SELECT state FROM tracked WHERE chat_key=?", arrayOf(chatKey)).use {
            if (it.moveToFirst()) it.getInt(0) else null
        }

    @Synchronized
    fun setTracked(chatKey: String, title: String?, tracked: Boolean) {
        writableDatabase.insertWithOnConflict(
            "tracked", null,
            ContentValues().apply {
                put("chat_key", chatKey)
                put("title", title)
                put("state", if (tracked) 1 else -1)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    // ---- Tin nhắn ----

    /** Lưu những dòng mới so với lần trước. Trả về số dòng đã lưu thêm. */
    @Synchronized
    fun appendVisible(chatKey: String, app: String, title: String?, visible: List<ChatLine>, now: Long): Int {
        val fresh = ChatLogMerge.newLines(tail(chatKey, 40), visible)
        if (fresh.isEmpty()) return 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            fresh.forEachIndexed { i, line ->
                db.insert("messages", null, ContentValues().apply {
                    put("chat_key", chatKey)
                    put("app", app)
                    put("title", title)
                    put("side", line.side.name)
                    put("text", line.text)
                    put("seen_at", now + i) // giữ đúng thứ tự trong cùng một lần đọc
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return fresh.size
    }

    private fun tail(chatKey: String, n: Int): List<ChatLine> =
        readableDatabase.rawQuery(
            "SELECT side, text FROM messages WHERE chat_key=? ORDER BY id DESC LIMIT ?",
            arrayOf(chatKey, n.toString()),
        ).use { c ->
            buildList { while (c.moveToNext()) add(ChatLine(side(c.getString(0)), c.getString(1))) }
        }.asReversed()

    fun undigested(chatKey: String, limit: Int): List<StoredMessage> =
        readableDatabase.rawQuery(
            "SELECT id, side, text, seen_at FROM messages WHERE chat_key=? AND digested=0 ORDER BY id LIMIT ?",
            arrayOf(chatKey, limit.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(StoredMessage(c.getLong(0), LoggedMessage(side(c.getString(1)), c.getString(2), c.getLong(3))))
                }
            }
        }

    fun chatsNeedingDigest(): List<Pair<String, String?>> =
        readableDatabase.rawQuery(
            "SELECT chat_key, MAX(title) FROM messages WHERE digested=0 GROUP BY chat_key", null,
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) } }

    /** Đã đến lúc cô đọng: có tin chưa cô đọng và đã hơn 1 ngày kể từ lần cô đọng trước (hoặc từ tin cũ nhất). */
    fun shouldDigest(chatKey: String, now: Long): Boolean {
        val oldest = readableDatabase.rawQuery(
            "SELECT MIN(seen_at) FROM messages WHERE chat_key=? AND digested=0", arrayOf(chatKey),
        ).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null } ?: return false
        val since = memory(chatKey)?.updatedAt ?: oldest
        return now - since >= DAY_MS
    }

    // ---- Gợi ý đã dùng ----

    @Synchronized
    fun logChoice(chatKey: String, text: String, now: Long) {
        writableDatabase.insert("choices", null, ContentValues().apply {
            put("chat_key", chatKey)
            put("text", text)
            put("at", now)
        })
    }

    fun choicesBetween(chatKey: String, from: Long, to: Long): List<UsedSuggestion> =
        readableDatabase.rawQuery(
            "SELECT text, at FROM choices WHERE chat_key=? AND at BETWEEN ? AND ? ORDER BY at",
            arrayOf(chatKey, from.toString(), to.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(UsedSuggestion(c.getString(0), c.getLong(1))) } }

    // ---- Sổ tay & nhật ký ----

    fun memory(chatKey: String): StoredMemory? =
        readableDatabase.rawQuery(
            "SELECT title, json, updated_at FROM memory WHERE chat_key=?", arrayOf(chatKey),
        ).use { c ->
            if (!c.moveToFirst()) return null
            val m = CrushMemory.fromJson(c.getString(1)) ?: return null
            StoredMemory(chatKey, c.getString(0), m, c.getLong(2))
        }

    fun allMemories(): List<StoredMemory> =
        readableDatabase.rawQuery("SELECT chat_key FROM memory ORDER BY updated_at DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }.mapNotNull { memory(it) }

    fun journal(chatKey: String, n: Int): List<JournalNote> =
        readableDatabase.rawQuery(
            "SELECT day, note FROM journal WHERE chat_key=? ORDER BY at DESC LIMIT ?",
            arrayOf(chatKey, n.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(JournalNote(c.getString(0), c.getString(1))) } }.asReversed()

    /** Phần cô đọng gửi kèm khi gợi ý: sổ tay + vài dòng nhật ký gần nhất. Null nếu chưa có. */
    fun memoryPrompt(chatKey: String): String? {
        val m = memory(chatKey)?.memory?.takeIf { !it.isEmpty() } ?: return null
        val notes = journal(chatKey, 3)
        return buildString {
            append(m.toPromptText())
            if (notes.isNotEmpty()) {
                append("\n\nNhật ký gần đây:")
                notes.forEach { append("\n- ${it.day}: ${it.note}") }
            }
        }
    }

    /** Lưu kết quả một lần cô đọng: sổ tay mới, nhật ký ngày, đánh dấu tin đã cô đọng — tất cả hoặc không gì cả. */
    @Synchronized
    fun commitDigest(
        chatKey: String,
        title: String?,
        memory: CrushMemory,
        day: String,
        dayNote: String,
        messageIds: List<Long>,
        now: Long,
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict("memory", null, ContentValues().apply {
                put("chat_key", chatKey)
                put("title", title)
                put("json", memory.toJson())
                put("updated_at", now)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            if (dayNote.isNotBlank()) {
                val existing = db.rawQuery(
                    "SELECT note FROM journal WHERE chat_key=? AND day=?", arrayOf(chatKey, day),
                ).use { if (it.moveToFirst()) it.getString(0) else null }
                db.insertWithOnConflict("journal", null, ContentValues().apply {
                    put("chat_key", chatKey)
                    put("day", day)
                    put("note", if (existing.isNullOrBlank()) dayNote else "$existing $dayNote")
                    put("at", now)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            messageIds.chunked(500).forEach { ids ->
                db.execSQL("UPDATE messages SET digested=1 WHERE id IN (${ids.joinToString(",")})")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun stats(): MemoryStats {
        val db = readableDatabase
        val total = db.rawQuery("SELECT COUNT(*) FROM messages", null).use { it.moveToFirst(); it.getInt(0) }
        val pending = db.rawQuery("SELECT COUNT(*) FROM messages WHERE digested=0", null).use { it.moveToFirst(); it.getInt(0) }
        val last = db.rawQuery("SELECT MAX(updated_at) FROM memory", null).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
        return MemoryStats(total, pending, last)
    }

    @Synchronized
    fun clearAll() {
        val db = writableDatabase
        listOf("messages", "choices", "memory", "journal", "tracked").forEach { db.delete(it, null, null) }
    }

    private fun side(s: String) = runCatching { Side.valueOf(s) }.getOrDefault(Side.UNKNOWN)

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000

        @Volatile
        private var instance: MemoryStore? = null

        fun get(context: Context): MemoryStore =
            instance ?: synchronized(this) {
                instance ?: MemoryStore(context.applicationContext).also { instance = it }
            }

        /** Bỏ dấu, chữ thường: "Linh Đan" -> "linh dan", để so tên chat với tên crush. */
        fun normalize(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace('đ', 'd').replace('Đ', 'D')
                .lowercase()
                .trim()
    }
}

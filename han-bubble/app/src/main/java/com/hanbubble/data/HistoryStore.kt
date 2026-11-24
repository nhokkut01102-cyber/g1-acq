package com.hanbubble.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.hanbubble.ai.KoreanResult

data class Entry(val id: Long, val createdAt: Long, val app: String?, val result: KoreanResult)

/** Sổ học: mọi câu đã dịch, lưu trên máy (SQLite). Hàm chạy đồng bộ — gọi từ luồng nền. */
class HistoryStore private constructor(context: Context) :
    SQLiteOpenHelper(context, "han_history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE entries(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                app TEXT, source TEXT NOT NULL, json TEXT NOT NULL)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun insert(app: String?, result: KoreanResult, now: Long): Long =
        writableDatabase.insert("entries", null, ContentValues().apply {
            put("created_at", now)
            put("updated_at", now)
            put("app", app)
            put("source", result.source)
            put("json", result.toJson())
        })

    /** Cập nhật một mục (khi câu dở dang được viết tiếp). Trả về false nếu mục không còn. */
    @Synchronized
    fun update(id: Long, result: KoreanResult, now: Long): Boolean =
        writableDatabase.update("entries", ContentValues().apply {
            put("updated_at", now)
            put("source", result.source)
            put("json", result.toJson())
        }, "id=?", arrayOf(id.toString())) > 0

    fun list(limit: Int = 1000): List<Entry> =
        readableDatabase.rawQuery(
            "SELECT id, created_at, app, json, source FROM entries ORDER BY created_at DESC, id DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val r = KoreanResult.fromJson(c.getString(3), source = c.getString(4)) ?: continue
                    add(Entry(c.getLong(0), c.getLong(1), c.getString(2), r))
                }
            }
        }

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM entries", null).use {
        it.moveToFirst()
        it.getInt(0)
    }

    @Synchronized
    fun delete(id: Long) {
        writableDatabase.delete("entries", "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun clear() {
        writableDatabase.delete("entries", null, null)
    }

    companion object {
        @Volatile
        private var instance: HistoryStore? = null

        fun get(context: Context): HistoryStore =
            instance ?: synchronized(this) {
                instance ?: HistoryStore(context.applicationContext).also { instance = it }
            }
    }
}

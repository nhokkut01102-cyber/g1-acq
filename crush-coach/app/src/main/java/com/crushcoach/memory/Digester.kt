package com.crushcoach.memory

import android.content.Context
import com.crushcoach.Prefs
import com.crushcoach.ai.CrushAi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Cô đọng tin nhắn đã lưu thành "Sổ tay về crush".
 * Mỗi lần chỉ gửi cho AI: sổ tay cũ + vài dòng nhật ký + tin MỚI kể từ lần trước.
 */
class Digester(private val context: Context) {

    /** Cô đọng mọi cuộc trò chuyện đang có tin mới. Trả về số tin đã cô đọng. */
    suspend fun runAll(): Int = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val store = MemoryStore.get(context)
        val chats = store.chatsNeedingDigest()
        if (chats.isEmpty()) return@withContext 0
        val ai = CrushAi.get(prefs.apiKey, prefs.model)
        var total = 0
        for ((chatKey, title) in chats) {
            while (true) {
                val batch = store.undigested(chatKey, BATCH)
                if (batch.isEmpty()) break
                val messages = batch.map { it.message }
                val old = store.memory(chatKey)
                val choices = store.choicesBetween(
                    chatKey,
                    messages.first().seenAt - MemoryStore.DAY_MS,
                    messages.last().seenAt,
                )
                val result = ai.digest(
                    profile = prefs.profile(),
                    chatTitle = title,
                    old = old?.memory,
                    recentNotes = store.journal(chatKey, 5),
                    messages = messages,
                    choices = choices,
                )
                // Tin quá ít để rút ra gì (vd chỉ "hi"): giữ nguyên sổ tay cũ.
                val memory = if (result.memory.isEmpty()) old?.memory ?: result.memory else result.memory
                store.commitDigest(
                    chatKey = chatKey,
                    title = title,
                    memory = memory,
                    day = DAY_FMT.format(Instant.ofEpochMilli(messages.last().seenAt).atZone(ZoneId.systemDefault())),
                    dayNote = result.dayNote,
                    messageIds = batch.map { it.id },
                    now = System.currentTimeMillis(),
                )
                total += batch.size
            }
        }
        total
    }

    companion object {
        /** Số tin tối đa mỗi lần gửi; nhiều hơn thì cô đọng làm nhiều lượt nối tiếp. */
        private const val BATCH = 800
        private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
    }
}

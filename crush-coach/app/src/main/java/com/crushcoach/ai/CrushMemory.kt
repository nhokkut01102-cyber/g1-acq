package com.crushcoach.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "Sổ tay về crush": bản cô đọng AI tự viết lại mỗi ngày từ tin nhắn đã lưu.
 * Khi gợi ý trả lời, AI chỉ đọc sổ tay này (cùng màn hình hiện tại), không đọc lại cả lịch sử.
 */
data class CrushMemory(
    val summary: String = "",
    val stage: String = "",
    val aboutCrush: List<String> = emptyList(),
    val whatWorks: List<String> = emptyList(),
    val whatToAvoid: List<String> = emptyList(),
    val openThreads: List<String> = emptyList(),
) {
    fun isEmpty() = summary.isBlank() && stage.isBlank() && aboutCrush.isEmpty() &&
        whatWorks.isEmpty() && whatToAvoid.isEmpty() && openThreads.isEmpty()

    /** Dạng chữ để nhét vào lời nhắn gửi AI. */
    fun toPromptText(): String = buildString {
        if (summary.isNotBlank()) appendLine("Tổng quan: $summary")
        if (stage.isNotBlank()) appendLine("Giai đoạn & xu hướng: $stage")
        section("Điều đã biết về crush", aboutCrush)
        section("Cách nhắn đã hiệu quả", whatWorks)
        section("Nên tránh", whatToAvoid)
        section("Chuyện đang dang dở / cần nhớ", openThreads)
    }.trim()

    /** Dạng chữ đẹp để hiện trong app. */
    fun toDisplayText(): String = buildString {
        if (summary.isNotBlank()) appendLine("💞 $summary")
        if (stage.isNotBlank()) appendLine("📍 $stage")
        section("🧡 Về crush", aboutCrush)
        section("✅ Nên làm", whatWorks)
        section("⛔ Nên tránh", whatToAvoid)
        section("🧵 Chuyện dang dở", openThreads)
    }.trim()

    private fun StringBuilder.section(title: String, items: List<String>) {
        if (items.isEmpty()) return
        appendLine()
        appendLine("$title:")
        items.forEach { appendLine("• $it") }
    }

    fun toJson(): String {
        val n = mapper.createObjectNode()
        n.put("summary", summary)
        n.put("stage", stage)
        fun arr(name: String, items: List<String>) = n.putArray(name).also { a -> items.forEach { a.add(it) } }
        arr("about_crush", aboutCrush)
        arr("what_works", whatWorks)
        arr("what_to_avoid", whatToAvoid)
        arr("open_threads", openThreads)
        return mapper.writeValueAsString(n)
    }

    companion object {
        private val mapper = ObjectMapper()

        fun fromJson(json: String?): CrushMemory? {
            if (json.isNullOrBlank()) return null
            return try {
                fromNode(mapper.readTree(json))
            } catch (e: Exception) {
                null
            }
        }

        internal fun fromNode(n: JsonNode): CrushMemory {
            fun list(name: String) = n.path(name).mapNotNull { it.asText("").trim().ifEmpty { null } }
            return CrushMemory(
                summary = n.path("summary").asText("").trim(),
                stage = n.path("stage").asText("").trim(),
                aboutCrush = list("about_crush"),
                whatWorks = list("what_works"),
                whatToAvoid = list("what_to_avoid"),
                openThreads = list("open_threads"),
            )
        }
    }
}

/** Một dòng tin đã lưu, kèm thời điểm app nhìn thấy nó (gần đúng với lúc gửi). */
data class LoggedMessage(val side: Side, val text: String, val seenAt: Long)

/** Một gợi ý của app mà người dùng đã chèn hoặc sao chép. */
data class UsedSuggestion(val text: String, val at: Long)

/** Ghi chú nhật ký một ngày, vd ("24/11", "Rủ đi cà phê, crush đồng ý"). */
data class JournalNote(val day: String, val note: String)

data class DigestResult(val memory: CrushMemory, val dayNote: String)

/** Ghép các dòng đang thấy trên màn hình vào lịch sử đã lưu mà không bị trùng hay lộn thứ tự. */
object ChatLogMerge {
    /**
     * @param tail các dòng cuối đã lưu của cuộc trò chuyện (cũ → mới).
     * @param visible các dòng đang thấy trên màn hình (cũ → mới).
     * @return các dòng mới cần lưu thêm. Rỗng nếu không có gì mới, hoặc nếu không ghép được
     *   (thường là đang cuộn lên xem tin cũ — khi đó không lưu để khỏi lộn thứ tự).
     */
    fun newLines(tail: List<ChatLine>, visible: List<ChatLine>): List<ChatLine> {
        if (visible.isEmpty()) return emptyList()
        if (tail.isEmpty()) return visible.toList()
        for (k in minOf(tail.size, visible.size) downTo 1) {
            val suffix = tail.subList(tail.size - k, tail.size)
            // Một dòng ngắn kiểu "ok", "haha" khớp một mình thì dễ khớp nhầm chỗ khác.
            if (k == 1 && tail.size > 1 && suffix[0].text.length < 6) continue
            for (i in visible.size - k downTo 0) {
                if (visible.subList(i, i + k) == suffix) return visible.subList(i + k, visible.size).toList()
            }
        }
        return emptyList()
    }
}

/** Soạn nội dung gửi AI khi cô đọng sổ tay. Tách riêng để kiểm thử được. */
object DigestPrompt {
    private val timeFmt = DateTimeFormatter.ofPattern("dd/MM HH:mm")

    fun build(
        profile: CoachProfile,
        chatTitle: String?,
        old: CrushMemory?,
        recentNotes: List<JournalNote>,
        messages: List<LoggedMessage>,
        choices: List<UsedSuggestion>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        fun time(ms: Long) = timeFmt.format(Instant.ofEpochMilli(ms).atZone(zone))
        val used = choices.map { norm(it.text) }.toSet()

        appendLine("Cuộc trò chuyện: ${chatTitle?.ifBlank { null } ?: "(không rõ tên)"}")
        appendLine("Thông tin mình tự khai:")
        appendLine("- Tên của mình: ${profile.myName.ifBlank { "(không nói)" }}")
        appendLine("- Tên/biệt danh crush: ${profile.crushName.ifBlank { "(không nói)" }}")
        appendLine("- Cách xưng hô: ${profile.pronouns.ifBlank { "(theo đoạn chat)" }}")
        appendLine("- Giai đoạn mình tự đánh giá: ${profile.stage.ifBlank { "(không nói)" }}")
        if (profile.notes.isNotBlank()) appendLine("- Ghi chú: ${profile.notes}")
        appendLine()

        if (old == null || old.isEmpty()) {
            appendLine("SỔ TAY CŨ: (chưa có — đây là lần cô đọng đầu tiên)")
        } else {
            appendLine("SỔ TAY CŨ:")
            appendLine(old.toPromptText())
        }
        if (recentNotes.isNotEmpty()) {
            appendLine()
            appendLine("Nhật ký các ngày gần đây:")
            recentNotes.forEach { appendLine("- ${it.day}: ${it.note}") }
        }
        appendLine()
        appendLine("TIN NHẮN MỚI KỂ TỪ LẦN CÔ ĐỌNG TRƯỚC (giờ là lúc app nhìn thấy tin, gần đúng lúc gửi):")
        messages.forEach { m ->
            val mark = if (m.side == Side.ME && norm(m.text) in used) "  ⟵ (mình dùng gợi ý của app)" else ""
            appendLine("[${time(m.seenAt)}] [${m.side.tag}] ${m.text}$mark")
        }
        if (choices.isNotEmpty()) {
            appendLine()
            appendLine("Các gợi ý của app mà mình đã chọn dùng (có thể đã sửa trước khi gửi):")
            choices.forEach { appendLine("- [${time(it.at)}] ${it.text}") }
        }
    }

    private fun norm(s: String) = s.trim().lowercase()
}

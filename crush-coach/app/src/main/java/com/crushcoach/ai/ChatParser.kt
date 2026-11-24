package com.crushcoach.ai

/** Một mẩu chữ đọc được trên màn hình, toạ độ tính theo khung cửa sổ của app chat. */
data class TextItem(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY get() = (top + bottom) / 2

    fun contains(o: TextItem) = left <= o.left && top <= o.top && right >= o.right && bottom >= o.bottom

    fun overlaps(o: TextItem) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

data class ParsedChat(val title: String?, val lines: List<ChatLine>)

/**
 * Biến đống chữ rời rạc trên màn hình thành danh sách tin nhắn có thứ tự và có bên.
 * Không dùng API Android để có thể kiểm thử trên JVM.
 *
 * Quy ước gần như mọi app chat dùng: tin của mình nằm sát mép phải, tin của người kia sát mép trái,
 * còn mốc thời gian / thông báo hệ thống thì nằm giữa.
 */
object ChatParser {
    private const val MAX_LINES = 40
    private const val MAX_CHARS = 600

    private val noise = listOf(
        Regex("^\\d{1,2}[:h.]\\d{2}(\\s?(AM|PM|SA|CH))?$", RegexOption.IGNORE_CASE),
        Regex("^(Đã xem|Đã gửi|Đã nhận|Đang gửi|Seen|Sent|Delivered|Read)\\b.*", RegexOption.IGNORE_CASE),
        Regex("^(Aa|Nhắn tin|Tin nhắn|Message|Soạn tin( nhắn)?|Type a message|Write a message)(\\s?\\.{3}|…)?$", RegexOption.IGNORE_CASE),
        Regex(".*(đang soạn tin|đang nhập|is typing|typing…|typing\\.\\.\\.).*", RegexOption.IGNORE_CASE),
    )

    fun isNoise(text: String) = text.isBlank() || noise.any { it.matches(text.trim()) }

    /**
     * @param frameWidth, frameHeight kích thước cửa sổ app chat.
     * @param inputTop mép trên của ô nhập tin (bỏ qua mọi thứ bên dưới), hoặc null nếu không thấy.
     */
    fun parse(items: List<TextItem>, frameWidth: Int, frameHeight: Int, inputTop: Int?): ParsedChat {
        if (frameWidth <= 0 || frameHeight <= 0) return ParsedChat(null, emptyList())
        val headerBottom = (frameHeight * 0.13f).toInt()
        val bodyBottom = inputTop?.takeIf { it > headerBottom } ?: (frameHeight * 0.94f).toInt()

        val valid = items
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() && it.right > it.left && it.bottom > it.top }

        val title = valid
            .filter { it.bottom <= headerBottom && it.top >= 0 }
            .sortedWith(compareBy({ it.top }, { it.left }))
            .map { it.text }
            .firstOrNull { it.length in 2..60 && !isNoise(it) }

        val body = valid.filter { it.centerY in (headerBottom + 1) until bodyBottom && !isNoise(it.text) }

        // Khung bao (vd: cả dòng "Linh, chắc là rảnh á, 10:20") chứa sẵn chữ của ô con -> bỏ khung, giữ ô con.
        val leaves = body.filterNot { a ->
            body.any { b -> b !== a && a.contains(b) && a.text != b.text && a.text.contains(b.text) }
        }

        val ordered = mutableListOf<TextItem>()
        for (item in leaves.sortedWith(compareBy({ it.top }, { it.left }))) {
            if (ordered.none { it.text == item.text && it.overlaps(item) }) ordered += item
        }

        val lines = ordered.map { ChatLine(sideOf(it, frameWidth), clip(it.text)) }
        return ParsedChat(title, lines.takeLast(MAX_LINES))
    }

    fun sideOf(item: TextItem, frameWidth: Int): Side {
        val leftGap = item.left.toFloat() / frameWidth
        val rightGap = (frameWidth - item.right).toFloat() / frameWidth
        return when {
            rightGap + 0.06f < leftGap -> Side.ME
            leftGap + 0.06f < rightGap -> Side.THEM
            else -> Side.UNKNOWN
        }
    }

    private fun clip(s: String) = if (s.length <= MAX_CHARS) s else s.take(MAX_CHARS) + "…"

    /** Dấu vân tay của mấy tin cuối: đổi nghĩa là có tin mới. */
    fun signature(appId: String?, parsed: ParsedChat): String? {
        if (parsed.lines.isEmpty()) return null
        val tail = parsed.lines.takeLast(3).joinToString("|") { "${it.side.name}:${it.text}" }
        return "$appId|${parsed.title}|$tail"
    }

    /** Bên của tin nhắn cuối cùng xác định được bên (bỏ qua dòng ở giữa). */
    fun lastSide(parsed: ParsedChat): Side? = parsed.lines.lastOrNull { it.side != Side.UNKNOWN }?.side
}

package com.hanbubble.ai

/**
 * Theo dõi chữ trong từng ô nhập và quyết định lúc nào một câu "đã viết xong" để đem đi dịch.
 *
 * - Câu kết thúc bằng . ! ? … hoặc xuống dòng: dịch khi ngừng gõ một chút.
 * - Câu chưa có dấu kết thúc: dịch khi ngừng gõ lâu hơn, hoặc khi ô được xoá trắng (vừa bấm gửi).
 * - Nếu câu dở dang đã được dịch rồi mà người dùng gõ tiếp / sửa lại, bản dịch mới thay cho bản cũ
 *   (cùng một mục trong Sổ học) thay vì thêm mục mới.
 *
 * Không dùng API Android để kiểm thử được trên JVM.
 */
class SentenceTracker {

    /** Một câu cần dịch. [replacesEntry] khác null nghĩa là cập nhật mục đã có trong Sổ học. */
    data class Task(val field: String, val text: String, val replacesEntry: Long?)

    private class Tail(val text: String, var entryId: Long?)

    private class FieldState {
        var text = ""
        val handled = LinkedHashSet<String>()
        var tail: Tail? = null
    }

    private val fields = HashMap<String, FieldState>()

    /** Gọi mỗi khi chữ trong ô thay đổi. Chỉ trả về việc khi ô vừa bị xoá trắng (thường là vừa gửi tin). */
    fun onText(field: String, text: String): List<Task> {
        val st = fields.getOrPut(field) { FieldState() }
        val prev = st.text
        st.text = text
        if (text.isNotBlank() || prev.isBlank()) return emptyList()
        val tasks = pending(field, st, prev, includeTail = true)
        fields.remove(field)
        return tasks
    }

    /**
     * Gọi khi người dùng ngừng gõ. [includeTail] = true sau khi ngừng lâu: dịch luôn cả câu
     * chưa có dấu kết thúc ở cuối.
     */
    fun onPause(field: String, includeTail: Boolean): List<Task> {
        val st = fields[field] ?: return emptyList()
        return pending(field, st, st.text, includeTail)
    }

    /** Ghi nhận mục Sổ học vừa lưu cho một câu, để lần sửa sau thay đúng mục đó. */
    fun bind(field: String, text: String, entryId: Long) {
        val tail = fields[field]?.tail ?: return
        if (norm(tail.text) == norm(text)) tail.entryId = entryId
    }

    private fun pending(field: String, st: FieldState, text: String, includeTail: Boolean): List<Task> {
        val (sentences, rest) = split(text)
        val tasks = ArrayList<Task>()
        for (s in sentences) {
            val key = norm(s)
            if (key in st.handled || !worthTranslating(s)) continue
            st.handled += key
            val tail = st.tail
            st.tail = null
            // Câu dở đã dịch, giờ chỉ thêm dấu chấm: không cần dịch lại.
            if (tail != null && norm(tail.text) == key) continue
            tasks += Task(field, s, tail?.entryId)
        }
        if (includeTail && worthTranslating(rest)) {
            val key = norm(rest)
            val tail = st.tail
            if (key !in st.handled && (tail == null || norm(tail.text) != key)) {
                tasks += Task(field, rest, tail?.entryId)
                st.tail = Tail(rest, null)
            }
        }
        return tasks
    }

    companion object {
        private val terminators = setOf('.', '!', '?', '…', '。', '！', '？')

        /** Tách thành các câu đã kết thúc và phần còn dở ở cuối. */
        fun split(text: String): Pair<List<String>, String> {
            val sentences = ArrayList<String>()
            val cur = StringBuilder()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '\n') {
                    cur.toString().trim().takeIf { it.isNotEmpty() }?.let { sentences += it }
                    cur.clear()
                } else if (c in terminators) {
                    cur.append(c)
                    while (i + 1 < text.length && text[i + 1] in terminators) cur.append(text[++i])
                    // Dấu chấm giữa hai chữ số (3.5) không phải hết câu.
                    val decimal = c == '.' && i + 1 < text.length && text[i + 1].isDigit() &&
                        cur.length >= 2 && cur[cur.length - 2].isDigit()
                    if (!decimal) {
                        cur.toString().trim().takeIf { it.isNotEmpty() }?.let { sentences += it }
                        cur.clear()
                    }
                } else {
                    cur.append(c)
                }
                i++
            }
            return sentences to cur.toString().trim()
        }

        /** Có chữ cái và đủ dài thì mới đáng dịch (bỏ qua "ok", số, emoji, đường link). */
        fun worthTranslating(s: String): Boolean {
            val t = s.trim()
            if (t.length < 2 || t.none { it.isLetter() }) return false
            if (t.startsWith("http://") || t.startsWith("https://")) return false
            return true
        }

        private fun norm(s: String) = s.trim().trimEnd(*terminators.toCharArray()).trim().lowercase()
    }
}

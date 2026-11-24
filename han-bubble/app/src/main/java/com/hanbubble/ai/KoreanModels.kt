package com.hanbubble.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/** Từ loại, kèm màu dùng chung cho khung dịch và Sổ học. */
enum class Pos(val id: String, val label: String, val korean: String, val color: Int) {
    NOUN("noun", "Danh từ", "명사", 0xFF1565C0.toInt()),
    PRONOUN("pronoun", "Đại từ", "대명사", 0xFF00838F.toInt()),
    VERB("verb", "Động từ", "동사", 0xFFC62828.toInt()),
    ADJECTIVE("adjective", "Tính từ", "형용사", 0xFFE65100.toInt()),
    ADVERB("adverb", "Trạng từ", "부사", 0xFF2E7D32.toInt()),
    PARTICLE("particle", "Trợ từ", "조사", 0xFF6A1B9A.toInt()),
    ENDING("ending", "Đuôi / ngữ pháp", "어미", 0xFFAD1457.toInt()),
    NUMBER("number", "Số từ", "수사", 0xFF6D4C41.toInt()),
    DETERMINER("determiner", "Định từ", "관형사", 0xFF827717.toInt()),
    INTERJECTION("interjection", "Thán từ", "감탄사", 0xFFF57F17.toInt()),
    CONJUNCTION("conjunction", "Liên từ", "접속사", 0xFF455A64.toInt()),
    PUNCTUATION("punctuation", "Dấu câu", "문장 부호", 0xFF757575.toInt()),
    OTHER("other", "Khác", "기타", 0xFF757575.toInt());

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: OTHER

        /** Các từ loại hiện trong bảng chú thích màu. */
        val legend = listOf(NOUN, PRONOUN, VERB, ADJECTIVE, ADVERB, PARTICLE, ENDING, NUMBER, DETERMINER, INTERJECTION, CONJUNCTION)
    }
}

/** Văn phong (mức độ kính ngữ) của câu tiếng Hàn. */
enum class Register(val id: String, val korean: String, val label: String) {
    BANMAL("banmal", "반말", "Thân mật"),
    HAEYO("haeyo", "해요체", "Lịch sự thân thiện"),
    HAMNIDA("hamnida", "합니다체", "Trang trọng");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/** Người dùng muốn văn phong nào: tự đoán theo cách viết, hoặc cố định một kiểu. */
enum class StyleSetting(val label: String, val register: Register?) {
    AUTO("Tự động theo cách mình viết", null),
    BANMAL("Luôn 반말 (thân mật)", Register.BANMAL),
    HAEYO("Luôn 해요체 (lịch sự thân thiện)", Register.HAEYO),
    HAMNIDA("Luôn 합니다체 (trang trọng)", Register.HAMNIDA),
}

/** Một mẩu của câu tiếng Hàn: gốc từ hoặc trợ từ / đuôi, theo đúng thứ tự trong câu. */
data class Segment(val text: String, val pos: Pos, val base: String, val meaning: String)

/** Một điểm ngữ pháp: dạng gốc, nghĩa, dạng đã chia trong câu và cách chia. */
data class GrammarPoint(val pattern: String, val meaning: String, val inSentence: String, val explanation: String)

data class KoreanResult(
    val source: String,
    val sourceLanguage: String,
    val korean: String,
    val romanization: String,
    val meaningVi: String,
    val register: Register?,
    val registerReason: String,
    val segments: List<Segment>,
    val grammar: List<GrammarPoint>,
    val correction: String,
) {
    fun toJson(): String {
        val n = mapper.createObjectNode()
        n.put("source", source)
        n.put("source_language", sourceLanguage)
        n.put("korean", korean)
        n.put("romanization", romanization)
        n.put("meaning_vi", meaningVi)
        n.put("register", register?.id ?: "")
        n.put("register_reason", registerReason)
        val segs = n.putArray("segments")
        segments.forEach { s ->
            segs.addObject().put("text", s.text).put("pos", s.pos.id).put("base", s.base).put("meaning", s.meaning)
        }
        val gr = n.putArray("grammar")
        grammar.forEach { g ->
            gr.addObject().put("pattern", g.pattern).put("meaning", g.meaning)
                .put("in_sentence", g.inSentence).put("explanation", g.explanation)
        }
        n.put("correction", correction)
        return mapper.writeValueAsString(n)
    }

    companion object {
        private val mapper = ObjectMapper()

        /** Đọc kết quả của AI (hoặc bản đã lưu). [source] ghi đè câu gốc nếu được truyền. */
        fun fromJson(json: String, source: String? = null): KoreanResult? = try {
            fromNode(mapper.readTree(json.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()), source)
        } catch (e: Exception) {
            null
        }

        private fun fromNode(n: JsonNode, source: String?): KoreanResult? {
            fun s(node: JsonNode, name: String) = node.path(name).asText("").trim()
            val korean = s(n, "korean")
            if (korean.isEmpty()) return null
            return KoreanResult(
                source = source ?: s(n, "source"),
                sourceLanguage = s(n, "source_language"),
                korean = korean,
                romanization = s(n, "romanization"),
                meaningVi = s(n, "meaning_vi"),
                register = Register.of(s(n, "register")),
                registerReason = s(n, "register_reason"),
                // Không trim chữ của mẩu: khoảng trắng giữa các từ cũng là một phần của câu.
                segments = n.path("segments").mapNotNull { x ->
                    val t = x.path("text").asText("")
                    if (t.isEmpty()) null else Segment(t, Pos.of(s(x, "pos")), s(x, "base"), s(x, "meaning"))
                },
                grammar = n.path("grammar").mapNotNull { x ->
                    val p = s(x, "pattern")
                    if (p.isEmpty()) null else GrammarPoint(p, s(x, "meaning"), s(x, "in_sentence"), s(x, "explanation"))
                },
                correction = s(n, "correction"),
            )
        }
    }
}

/** Đoạn [start, end) trong câu tiếng Hàn được tô theo từ loại. */
data class ColorRange(val start: Int, val end: Int, val pos: Pos)

object SegmentAligner {
    /**
     * Tìm vị trí từng mẩu trong câu theo thứ tự, để tô màu đúng chỗ kể cả khi AI
     * bỏ sót hay thừa khoảng trắng. Mẩu nào không tìm thấy thì bỏ qua.
     */
    fun align(korean: String, segments: List<Segment>): List<ColorRange> {
        val out = ArrayList<ColorRange>()
        var cursor = 0
        for (seg in segments) {
            val needle = seg.text.trim()
            if (needle.isEmpty()) continue
            val at = korean.indexOf(needle, cursor)
            if (at < 0) continue
            out += ColorRange(at, at + needle.length, seg.pos)
            cursor = at + needle.length
        }
        return out
    }
}

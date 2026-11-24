package com.crushcoach.ai

/** Ai đã gửi một dòng tin nhắn, đoán từ vị trí bong bóng trên màn hình. */
enum class Side(val tag: String) {
    THEM("Crush"),
    ME("Mình"),
    UNKNOWN("?"),
}

data class ChatLine(val side: Side, val text: String)

/** Những gì người dùng tự khai trong app về mối quan hệ. */
data class CoachProfile(
    val myName: String = "",
    val crushName: String = "",
    val pronouns: String = "",
    val stage: String = "",
    val style: String = "",
    val notes: String = "",
)

/** Ngữ cảnh của một lần nhờ AI: màn hình chat hiện tại + câu hỏi (nếu có). */
data class CoachRequest(
    val appName: String,
    val chatTitle: String? = null,
    val lines: List<ChatLine> = emptyList(),
    val draft: String? = null,
    /** Ảnh chụp màn hình JPEG đã mã hóa base64 (không xuống dòng), hoặc null. */
    val screenshotJpegBase64: String? = null,
    val question: String? = null,
    val previousSuggestions: List<String> = emptyList(),
)

data class Suggestion(val style: String, val text: String, val why: String)

data class CoachResult(
    val mood: String,
    val read: String,
    val suggestions: List<Suggestion>,
    val tips: List<String>,
    val answer: String,
    val warning: String,
)

/** Lỗi đã được dịch sang câu tiếng Việt dễ hiểu để hiện thẳng cho người dùng. */
class CoachException(message: String, cause: Throwable? = null) : Exception(message, cause)

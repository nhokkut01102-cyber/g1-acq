package com.hanbubble.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.OverloadedException
import com.anthropic.errors.PaymentRequiredException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.ServiceUnavailableException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaFallbacksParam
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import java.time.Duration

/** Các model cho người dùng chọn. Mặc định là model rẻ nhất. */
object AiModels {
    data class Option(val id: String, val label: String)

    val options = listOf(
        Option("claude-haiku-5-5", "Claude Haiku 5.5 — rẻ nhất, ~10–20đ/câu (mặc định)"),
        Option("claude-sonnet-5-5", "Claude Sonnet 5.5 — giải thích kỹ hơn, ~200đ/câu"),
        Option("claude-opus-5-5", "Claude Opus 5.5 — chuẩn nhất, ~400đ/câu"),
    )

    const val DEFAULT = "claude-haiku-5-5"

    /** Haiku không có chuyển model dự phòng phía máy chủ khi bị từ chối. */
    fun supportsServerFallback(model: String) = !model.startsWith("claude-haiku")
}

data class TranslateRequest(
    val text: String,
    /** App nơi câu được viết (vd Messenger) — giúp đoán văn phong. */
    val appName: String? = null,
    val style: StyleSetting = StyleSetting.AUTO,
)

class HanException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Dịch một câu sang tiếng Hàn và phân tích từ loại + ngữ pháp trong một lần gọi Claude.
 * Hàm [translate] chạy đồng bộ (blocking) — gọi từ luồng nền.
 */
class HanAi internal constructor(private val client: AnthropicClient, private val model: String) {

    fun translate(request: TranslateRequest): KoreanResult {
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(SYSTEM_PROMPT)
            .addUserMessageOfContentBlockParams(listOf(BetaContentBlockParam.ofText(userText(request))))
            .outputConfig(
                BetaOutputConfig.builder()
                    // Dịch từng câu cần nhanh: effort thấp.
                    .effort(BetaOutputConfig.Effort.LOW)
                    .format(BetaJsonOutputFormat.builder().schema(SCHEMA).build())
                    .build()
            )
        if (AiModels.supportsServerFallback(model)) {
            builder.addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                .fallbacks(BetaFallbacksParam.ofDefault())
        }

        val response = try {
            client.beta().messages().create(builder.build())
        } catch (e: Exception) {
            throw translateError(e)
        }
        when (response.stopReason().orElse(null)) {
            BetaStopReason.REFUSAL -> throw HanException("AI không dịch câu này. Thử viết khác đi nhé.")
            BetaStopReason.MAX_TOKENS -> throw HanException("Bản phân tích bị cắt giữa chừng, thử lại nhé.")
            else -> Unit
        }
        val text = response.content()
            .mapNotNull { it.text().orElse(null)?.text() }
            .joinToString("")
            .trim()
        return KoreanResult.fromJson(text, source = request.text)
            ?: throw HanException("AI trả kết quả sai định dạng, thử lại nhé.")
    }

    companion object {
        @Volatile private var cached: Triple<String, String, HanAi>? = null

        fun get(apiKey: String, model: String): HanAi {
            val key = apiKey.trim()
            if (key.isEmpty()) throw HanException("Chưa có API key. Mở app Hàn Bubble để nhập nhé.")
            cached?.let { (k, m, ai) -> if (k == key && m == model) return ai }
            val client = AnthropicOkHttpClient.builder()
                .apiKey(key)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(2)
                .build()
            return HanAi(client, model).also { cached = Triple(key, model, it) }
        }

        internal fun userText(r: TranslateRequest): String = buildString {
            appendLine("Câu mình vừa viết:")
            appendLine(r.text)
            appendLine()
            r.appName?.takeIf { it.isNotBlank() }?.let { appendLine("Viết trong app: $it") }
            val reg = r.style.register
            if (reg == null) {
                appendLine("Văn phong: tự chọn theo cách mình viết.")
            } else {
                appendLine("Văn phong: bắt buộc dùng ${reg.korean} (${reg.label}).")
            }
        }

        private fun translateError(e: Exception): HanException {
            val msg = when (e) {
                is UnauthorizedException -> "API key không đúng hoặc đã bị thu hồi."
                is PermissionDeniedException -> "API key này chưa được phép dùng model đã chọn."
                is NotFoundException -> "Không tìm thấy model. Thử chọn model khác trong app."
                is PaymentRequiredException -> "Tài khoản Anthropic đã hết credit. Nạp thêm tại console.anthropic.com."
                is RateLimitException -> "Gõ nhanh quá, AI chưa kịp dịch. Đợi vài giây nhé."
                is OverloadedException, is ServiceUnavailableException, is InternalServerException ->
                    "Máy chủ AI đang bận, thử lại sau ít phút nhé."
                is BadRequestException -> {
                    val detail = e.apiMessage().orElse("")
                    if (detail.contains("credit", ignoreCase = true))
                        "Tài khoản Anthropic đã hết credit. Nạp thêm tại console.anthropic.com."
                    else "Yêu cầu không hợp lệ: $detail"
                }
                is AnthropicServiceException -> "Lỗi từ máy chủ AI (${e.statusCode()}): ${e.apiMessage().orElse("")}"
                is AnthropicIoException -> "Không kết nối được tới AI. Kiểm tra mạng nhé."
                else -> "Có lỗi xảy ra: ${e.message ?: e.javaClass.simpleName}"
            }
            return HanException(msg, e)
        }

        private fun str(description: String) = mapOf("type" to "string", "description" to description)

        private val SCHEMA: BetaJsonOutputFormat.Schema = BetaJsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    mapOf(
                        "source_language" to str("Mã ngôn ngữ của câu gốc, vd vi, en, ko."),
                        "korean" to str("Câu tiếng Hàn hoàn chỉnh, tự nhiên như người Hàn nói."),
                        "romanization" to str("Phiên âm Latinh (Romanization chuẩn) của câu tiếng Hàn."),
                        "meaning_vi" to str("Nghĩa tiếng Việt sát với câu tiếng Hàn."),
                        "register" to mapOf(
                            "type" to "string",
                            "enum" to Register.entries.map { it.id },
                            "description" to "Văn phong của câu tiếng Hàn: banmal (반말), haeyo (해요체), hamnida (합니다체).",
                        ),
                        "register_reason" to str("Một câu ngắn: vì sao chọn văn phong này dựa trên cách mình viết."),
                        "segments" to mapOf(
                            "type" to "array",
                            "description" to "Câu tiếng Hàn chia thành các mẩu theo đúng thứ tự, ghép lại (kể cả khoảng trắng) phải đúng bằng câu tiếng Hàn. Tách gốc từ khỏi trợ từ và đuôi: 저는 → 저 + 는; 가고 싶어요 → 가 + 고 싶어요.",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "text" to str("Chữ đúng như trong câu (khoảng trắng tách riêng thành một mẩu với pos=punctuation)."),
                                    "pos" to mapOf(
                                        "type" to "string",
                                        "enum" to Pos.entries.map { it.id },
                                        "description" to "Từ loại. Đuôi câu, vĩ tố, cấu trúc ngữ pháp gắn sau gốc động/tính từ dùng ending.",
                                    ),
                                    "base" to str("Dạng gốc: động/tính từ ghi dạng từ điển (가다, 예쁘다); trợ từ/đuôi ghi dạng gốc (은/는, -고 싶다, -아/어요); khoảng trắng và dấu câu để rỗng."),
                                    "meaning" to str("Nghĩa tiếng Việt ngắn; rỗng với khoảng trắng và dấu câu."),
                                ),
                                "required" to listOf("text", "pos", "base", "meaning"),
                                "additionalProperties" to false,
                            ),
                        ),
                        "grammar" to mapOf(
                            "type" to "array",
                            "description" to "Mỗi cấu trúc ngữ pháp / đuôi câu quan trọng trong câu (1–5 mục).",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "pattern" to str("Ngữ pháp gốc, vd -고 싶다, -(으)ㄹ 거예요, 은/는."),
                                    "meaning" to str("Nghĩa / cách dùng bằng tiếng Việt, ngắn."),
                                    "in_sentence" to str("Dạng đã chia xuất hiện trong câu, vd 가고 싶어요."),
                                    "explanation" to str("Cách chia theo văn phong của câu, vd 가다 → 가 + -고 싶다 → 가고 싶어요 (해요체). Nêu dạng 반말/합니다체 tương ứng nếu hữu ích."),
                                ),
                                "required" to listOf("pattern", "meaning", "in_sentence", "explanation"),
                                "additionalProperties" to false,
                            ),
                        ),
                        "correction" to str("Nếu câu gốc đã là tiếng Hàn: giải thích ngắn các lỗi đã sửa (rỗng nếu câu đúng). Nếu câu gốc không phải tiếng Hàn: rỗng."),
                    )
                )
            )
            .putAdditionalProperty(
                "required",
                JsonValue.from(
                    listOf(
                        "source_language", "korean", "romanization", "meaning_vi", "register",
                        "register_reason", "segments", "grammar", "correction",
                    )
                )
            )
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()

        private val SYSTEM_PROMPT = """
Bạn là gia sư tiếng Hàn cho người Việt. Người học đang nhắn tin / viết bằng tiếng Việt (đôi khi tiếng Anh hoặc tiếng Hàn) và muốn thấy ngay câu đó nói bằng tiếng Hàn thế nào để học.

Với mỗi câu:
1. Dịch sang tiếng Hàn tự nhiên, đúng nghĩa và đúng cảm xúc, như người Hàn thật sự nói hoặc nhắn — không dịch word-by-word.
2. Chọn văn phong theo cách người học viết, trừ khi họ yêu cầu cố định:
   - 반말 (thân mật): viết suồng sã, teencode, viết tắt, xưng mày–tao / tớ–cậu, nói với bạn bè, người yêu, người nhỏ tuổi hơn.
   - 해요체 (lịch sự thân thiện): có "ạ", "dạ", xưng anh/chị/em lễ phép, nói với người lớn hơn hoặc mới quen. Dùng khi không rõ.
   - 합니다체 (trang trọng): văn bản công việc, thông báo, "kính gửi", "xin", phát biểu.
   Chia toàn bộ đuôi câu nhất quán theo văn phong đã chọn.
3. Chia câu tiếng Hàn thành các mẩu theo thứ tự, ghép lại phải đúng bằng câu: tách gốc danh/động/tính từ khỏi trợ từ (은/는, 이/가, 을/를, 에, 에서…) và khỏi đuôi/cấu trúc ngữ pháp (-고 싶다, -아/어요, -(으)ㄹ 거예요…). Khoảng trắng là một mẩu riêng (pos = punctuation). Ghi dạng gốc và nghĩa tiếng Việt cho từng mẩu.
4. Giải thích 1–5 điểm ngữ pháp chính: ngữ pháp gốc, nghĩa, dạng đã chia trong câu và cách chia theo văn phong.
5. Nếu câu gốc đã là tiếng Hàn: giữ ý, sửa lỗi (chính tả, trợ từ, chia đuôi) và giải thích ngắn trong "correction".

Viết mọi giải thích bằng tiếng Việt, ngắn gọn, dễ hiểu cho người mới học. Không thêm ý mà câu gốc không có.
""".trim()
    }
}

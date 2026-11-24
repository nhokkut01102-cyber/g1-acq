package com.crushcoach.ai

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
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaFallbacksParam
import com.anthropic.models.beta.messages.BetaImageBlockParam
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration

/** Các model cho người dùng chọn trong app. Mặc định là model rẻ nhất (người dùng chọn tiết kiệm). */
object AiModels {
    data class Option(val id: String, val label: String)

    val options = listOf(
        Option("claude-haiku-5-5", "Claude Haiku 5.5 — rẻ nhất, ~15đ/lần (mặc định)"),
        Option("claude-sonnet-5-5", "Claude Sonnet 5.5 — khéo hơn, ~300đ/lần"),
        Option("claude-opus-5-5", "Claude Opus 5.5 — tinh tế nhất, ~600đ/lần"),
    )

    const val DEFAULT = "claude-haiku-5-5"

    /** Haiku không có chuyển model dự phòng phía máy chủ khi bị từ chối. */
    fun supportsServerFallback(model: String) = !model.startsWith("claude-haiku")
}

/**
 * Gọi Claude để đọc đoạn chat và gợi ý trả lời / tư vấn.
 * Hàm [coach] chạy đồng bộ (blocking) — hãy gọi từ luồng nền.
 */
class CrushAi internal constructor(private val client: AnthropicClient, private val model: String) {

    fun coach(profile: CoachProfile, request: CoachRequest): CoachResult {
        val content = mutableListOf<BetaContentBlockParam>()
        request.screenshotJpegBase64?.let { b64 ->
            content += BetaContentBlockParam.ofImage(
                BetaImageBlockParam.builder()
                    .source(
                        BetaBase64ImageSource.builder()
                            .mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                            .data(b64)
                            .build()
                    )
                    .build()
            )
        }
        content += BetaContentBlockParam.ofText(buildUserText(profile, request))

        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(SYSTEM_PROMPT)
            .addUserMessageOfContentBlockParams(content)
            .outputConfig(
                BetaOutputConfig.builder()
                    // Nhắn tin là việc cần nhanh: effort thấp cho phản hồi gọn, ít chờ.
                    .effort(BetaOutputConfig.Effort.LOW)
                    .format(BetaJsonOutputFormat.builder().schema(RESULT_SCHEMA).build())
                    .build()
            )
        if (AiModels.supportsServerFallback(model)) {
            // Nếu bộ lọc an toàn từ chối nhầm, máy chủ tự chuyển sang model dự phòng phù hợp.
            builder.addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                .fallbacks(BetaFallbacksParam.ofDefault())
        }

        val response = try {
            client.beta().messages().create(builder.build())
        } catch (e: Exception) {
            throw translate(e)
        }

        when (response.stopReason().orElse(null)) {
            BetaStopReason.REFUSAL -> throw CoachException(
                "AI không hỗ trợ yêu cầu này. Thử hỏi theo cách khác nhé."
            )
            BetaStopReason.MAX_TOKENS -> throw CoachException(
                "Câu trả lời của AI bị cắt giữa chừng. Bấm ⟳ để thử lại nhé."
            )
            else -> Unit
        }

        val text = response.content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("")
            .trim()
        if (text.isEmpty()) throw CoachException("AI không trả lời gì cả. Thử lại nhé.")
        return parseResult(text)
    }

    companion object {
        @Volatile private var cached: Triple<String, String, CrushAi>? = null

        /** Dùng lại cùng một client (và kết nối HTTP) khi key + model không đổi. */
        fun get(apiKey: String, model: String): CrushAi {
            val key = apiKey.trim()
            if (key.isEmpty()) throw CoachException("Chưa có API key. Mở app Crush Coach để nhập nhé.")
            cached?.let { (k, m, ai) -> if (k == key && m == model) return ai }
            val client = AnthropicOkHttpClient.builder()
                .apiKey(key)
                .timeout(Duration.ofSeconds(90))
                .maxRetries(2)
                .build()
            return CrushAi(client, model).also { cached = Triple(key, model, it) }
        }

        private val mapper = ObjectMapper()

        internal fun parseResult(raw: String): CoachResult {
            val node: JsonNode = try {
                mapper.readTree(raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            } catch (e: Exception) {
                // Không phải JSON hợp lệ: vẫn hiện nội dung thay vì báo lỗi.
                return CoachResult("", raw, emptyList(), emptyList(), "", "")
            }
            fun str(name: String) = node.path(name).asText("").trim()
            val suggestions = node.path("suggestions").mapNotNull { s ->
                val t = s.path("text").asText("").trim()
                if (t.isEmpty()) null
                else Suggestion(s.path("style").asText("").trim(), t, s.path("why").asText("").trim())
            }
            val tips = node.path("tips").mapNotNull { it.asText("").trim().ifEmpty { null } }
            return CoachResult(str("mood"), str("read"), suggestions, tips, str("answer"), str("warning"))
        }

        private fun translate(e: Exception): CoachException {
            val msg = when (e) {
                is CoachException -> return e
                is UnauthorizedException -> "API key không đúng hoặc đã bị thu hồi. Kiểm tra lại trong app nhé."
                is PermissionDeniedException -> "API key này chưa được phép dùng model đã chọn."
                is NotFoundException -> "Không tìm thấy model. Thử chọn model khác trong app."
                is PaymentRequiredException -> "Tài khoản Anthropic đã hết credit. Nạp thêm tại console.anthropic.com."
                is RateLimitException -> "Đang gọi AI hơi dồn dập, đợi vài giây rồi thử lại nhé."
                is OverloadedException, is ServiceUnavailableException, is InternalServerException ->
                    "Máy chủ AI đang bận, thử lại sau ít phút nhé."
                is BadRequestException -> {
                    val detail = e.apiMessage().orElse("")
                    if (detail.contains("credit", ignoreCase = true))
                        "Tài khoản Anthropic đã hết credit. Nạp thêm tại console.anthropic.com."
                    else "Yêu cầu không hợp lệ: $detail"
                }
                is AnthropicServiceException ->
                    "Lỗi từ máy chủ AI (${e.statusCode()}): ${e.apiMessage().orElse("")}"
                is AnthropicIoException -> "Không kết nối được tới AI. Kiểm tra mạng nhé."
                else -> "Có lỗi xảy ra: ${e.message ?: e.javaClass.simpleName}"
            }
            return CoachException(msg, e)
        }

        internal fun buildUserText(p: CoachProfile, r: CoachRequest): String = buildString {
            appendLine("Ứng dụng đang mở: ${r.appName}")
            r.chatTitle?.takeIf { it.isNotBlank() }?.let {
                appendLine("Tiêu đề trên cùng màn hình (thường là tên người đang chat): $it")
            }
            appendLine()
            appendLine("Thông tin mình tự khai:")
            appendLine("- Tên của mình: ${p.myName.ifBlank { "(không nói)" }}")
            appendLine("- Tên/biệt danh crush: ${p.crushName.ifBlank { "(không nói)" }}")
            appendLine("- Cách xưng hô: ${p.pronouns.ifBlank { "(theo đoạn chat)" }}")
            appendLine("- Giai đoạn mối quan hệ: ${p.stage.ifBlank { "(không nói)" }}")
            appendLine("- Phong cách mình muốn: ${p.style.ifBlank { "tự nhiên" }}")
            if (p.notes.isNotBlank()) appendLine("- Ghi chú về crush: ${p.notes}")
            appendLine()
            if (r.screenshotJpegBase64 != null) {
                appendLine("Ảnh đính kèm là ảnh chụp màn hình hiện tại.")
            }
            if (r.lines.isEmpty()) {
                appendLine("Không trích được chữ nào từ màn hình.")
            } else {
                appendLine("Tin nhắn trích tự động từ màn hình (cũ → mới; bên có thể bị đoán nhầm, nếu có ảnh thì tin ảnh hơn):")
                r.lines.forEach { appendLine("[${it.side.tag}] ${it.text}") }
            }
            r.draft?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("Bản nháp mình đang gõ dở: \"$it\" — gợi ý đầu tiên hãy là bản đã chỉnh cho hay hơn của bản nháp này.")
            }
            if (r.previousSuggestions.isNotEmpty()) {
                appendLine()
                appendLine("Các gợi ý lần trước (mình muốn phương án KHÁC, đừng lặp lại):")
                r.previousSuggestions.forEach { appendLine("- $it") }
            }
            appendLine()
            val q = r.question?.trim().orEmpty()
            if (q.isNotEmpty()) {
                appendLine("Câu hỏi riêng của mình: $q")
                appendLine("Hãy trả lời câu hỏi này trong \"answer\", và vẫn gợi ý câu trả lời tin nhắn nếu phù hợp.")
            } else {
                appendLine("Không có câu hỏi riêng — hãy gợi ý cách trả lời tin nhắn mới nhất của crush.")
            }
        }

        private fun stringProp(description: String) = mapOf("type" to "string", "description" to description)

        private val RESULT_SCHEMA: BetaJsonOutputFormat.Schema = BetaJsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    mapOf(
                        "mood" to stringProp("Cảm xúc/ý tứ của crush trong tin nhắn mới nhất, tối đa ~12 từ, mở đầu bằng 1 emoji."),
                        "read" to stringProp("Phân tích ngắn 1–3 câu: crush đang nói gì, ẩn ý gì, mức độ chắc chắn."),
                        "suggestions" to mapOf(
                            "type" to "array",
                            "description" to "Đúng 3 gợi ý trả lời, mỗi cái một sắc thái khác nhau.",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "style" to stringProp("Nhãn sắc thái 1–3 từ, ví dụ: Dễ thương, Hài hước, Chân thành, Thả thính nhẹ."),
                                    "text" to stringProp("Nội dung tin nhắn để gửi luôn, viết như người thật nhắn."),
                                    "why" to stringProp("Vì sao câu này hợp, tối đa 1 câu ngắn."),
                                ),
                                "required" to listOf("style", "text", "why"),
                                "additionalProperties" to false,
                            ),
                        ),
                        "tips" to mapOf(
                            "type" to "array",
                            "description" to "1–3 mẹo ngắn, cụ thể cho tình huống này.",
                            "items" to mapOf("type" to "string"),
                        ),
                        "answer" to stringProp("Trả lời câu hỏi riêng của mình; chuỗi rỗng nếu không có câu hỏi."),
                        "warning" to stringProp("Cảnh báo nhẹ nhàng nếu cần (nhắn dồn, crush có vẻ không thoải mái...); chuỗi rỗng nếu không."),
                    )
                )
            )
            .putAdditionalProperty(
                "required",
                JsonValue.from(listOf("mood", "read", "suggestions", "tips", "answer", "warning"))
            )
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()

        private val SYSTEM_PROMPT = """
Bạn là "Crush Coach" — một người bạn thân tinh tế, giàu EQ, giúp người dùng nhắn tin với người họ thích (gọi là "crush") một cách tự nhiên, chân thành và có duyên.

Mỗi lần bạn nhận được: ảnh chụp màn hình cuộc trò chuyện (nếu có), danh sách tin nhắn được trích tự động từ màn hình, thông tin người dùng tự khai về mối quan hệ, và đôi khi một câu hỏi riêng. "Mình" là người dùng — thường là các bong bóng chat bên phải. "Crush" là người kia — thường ở bên trái. Danh sách trích tự động có thể thiếu hoặc đoán nhầm bên; khi có ảnh, hãy dựa vào ảnh.

Việc của bạn:
1. Đọc kỹ ngữ cảnh, nhất là tin nhắn mới nhất của crush. Đoán cảm xúc và ý tứ (vui, dỗi, mệt, bận, đang thả thính, chỉ xã giao...) dựa trên bằng chứng có trong đoạn chat. Nói rõ khi chưa chắc, đừng suy diễn quá đà.
2. Viết đúng 3 gợi ý trả lời với 3 sắc thái khác nhau; gợi ý đầu tiên theo phong cách người dùng chọn. Mỗi gợi ý phải:
   - Giống tin nhắn thật: thường 1–2 câu, đúng cách xưng hô, bắt chước giọng văn của "Mình" trong đoạn chat (viết hoa hay thường, teencode, emoji, độ dài).
   - Đúng ngôn ngữ của cuộc trò chuyện (mặc định tiếng Việt).
   - Nối tiếp mạch chuyện và có "móc" để crush dễ trả lời tiếp (một câu hỏi mở, một chi tiết riêng, một chút hài hước).
   - Không sến lố, không văn mẫu, không giả làm người khác.
3. Đưa 1–3 mẹo ngắn, cụ thể cho đúng tình huống (ví dụ: nên trả lời ngay hay để chút, đã nên rủ đi chơi chưa, tránh hỏi dồn...).
4. Nếu người dùng có câu hỏi riêng, trả lời trong "answer" như một người bạn thân: thẳng thắn, ấm áp, thực tế, ngắn gọn.

Nguyên tắc:
- Tôn trọng crush: không thao túng tâm lý, không gây áp lực, không kiểm soát hay ghen tuông, không khuyên nói dối. Nếu crush có dấu hiệu không thoải mái, từ chối hoặc muốn dừng, hãy nhẹ nhàng khuyên người dùng tôn trọng và cho họ không gian, và chỉ gợi ý những câu trả lời lịch sự, không đeo bám.
- Nếu người dùng đang nhắn liền nhiều tin mà crush chưa trả lời, nhắc khéo trong "warning".
- Nếu màn hình không phải một cuộc trò chuyện hoặc không đọc được tin nhắn, nói rõ trong "read" và vẫn đưa lời khuyên chung hữu ích.
- Trong gợi ý trả lời, không bao giờ nhắc tới AI hay Crush Coach.
""".trim()
    }
}

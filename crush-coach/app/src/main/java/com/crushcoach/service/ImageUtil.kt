package com.crushcoach.service

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.max
import kotlin.math.roundToInt

object ImageUtil {
    /** Cạnh dài tối đa Claude xử lý mà không phải thu nhỏ thêm. */
    private const val MAX_EDGE = 1568

    /** Thu nhỏ ảnh chụp màn hình và mã hóa JPEG base64 để gửi cho AI. */
    fun toJpegBase64(src: Bitmap, quality: Int = 80): String {
        val longest = max(src.width, src.height)
        val scaled = if (longest > MAX_EDGE) {
            val k = MAX_EDGE.toFloat() / longest
            Bitmap.createScaledBitmap(src, (src.width * k).roundToInt(), (src.height * k).roundToInt(), true)
        } else {
            src
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== src) scaled.recycle()
        src.recycle()
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }
}

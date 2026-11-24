package com.hanbubble.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

/** Đọc câu tiếng Hàn bằng giọng đọc có sẵn trên máy (miễn phí, không cần mạng nếu đã cài giọng). */
class Speaker(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun speak(text: String) {
        val engine = tts
        if (engine == null) {
            pending = text
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) pending?.let { say(it) } else toast("Không mở được bộ đọc giọng nói trên máy.")
                pending = null
            }
            return
        }
        if (ready) say(text) else pending = text
    }

    private fun say(text: String) {
        val engine = tts ?: return
        val res = engine.setLanguage(Locale.KOREAN)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            toast("Máy chưa có giọng đọc tiếng Hàn. Cài trong Cài đặt → Hệ thống → Ngôn ngữ → Chuyển văn bản thành giọng nói.")
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "han-bubble")
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}

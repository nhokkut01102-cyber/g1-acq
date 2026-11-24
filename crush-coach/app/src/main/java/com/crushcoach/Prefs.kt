package com.crushcoach

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.crushcoach.ai.AiModels
import com.crushcoach.ai.CoachProfile

/** Toàn bộ cài đặt của app, lưu riêng trên máy (không sao lưu lên cloud). */
class Prefs(context: Context) {
    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("crush_coach", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString(KEY_API, "").orEmpty()
        set(v) = sp.edit { putString(KEY_API, v.trim()) }

    var model: String
        get() = sp.getString(KEY_MODEL, null)
            ?.takeIf { id -> AiModels.options.any { it.id == id } } ?: AiModels.DEFAULT
        set(v) = sp.edit { putString(KEY_MODEL, v) }

    var myName by text("my_name")
    var crushName by text("crush_name")
    var pronouns by text("pronouns")
    var stage by text("stage")
    var style by text("style")
    var notes by text("notes")

    var bubbleEnabled: Boolean
        get() = sp.getBoolean(KEY_BUBBLE, false)
        set(v) = sp.edit { putBoolean(KEY_BUBBLE, v) }

    var autoMode: Boolean
        get() = sp.getBoolean("auto_mode", true)
        set(v) = sp.edit { putBoolean("auto_mode", v) }

    var useScreenshot: Boolean
        // Mặc định tắt cho rẻ; app vẫn tự gửi ảnh khi không đọc được chữ trên màn hình.
        get() = sp.getBoolean("use_screenshot", false)
        set(v) = sp.edit { putBoolean("use_screenshot", v) }

    var watchAllApps: Boolean
        get() = sp.getBoolean("watch_all", false)
        set(v) = sp.edit { putBoolean("watch_all", v) }

    var bubbleX: Int
        get() = sp.getInt("bubble_x", -1)
        set(v) = sp.edit { putInt("bubble_x", v) }

    var bubbleY: Int
        get() = sp.getInt("bubble_y", -1)
        set(v) = sp.edit { putInt("bubble_y", v) }

    fun profile() = CoachProfile(
        myName = myName,
        crushName = crushName,
        pronouns = pronouns,
        stage = stage,
        style = style,
        notes = notes,
    )

    private fun text(key: String) = object : kotlin.properties.ReadWriteProperty<Prefs, String> {
        override fun getValue(thisRef: Prefs, property: kotlin.reflect.KProperty<*>) =
            sp.getString(key, "").orEmpty()

        override fun setValue(thisRef: Prefs, property: kotlin.reflect.KProperty<*>, value: String) =
            sp.edit { putString(key, value.trim()) }
    }

    companion object {
        const val KEY_API = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_BUBBLE = "bubble_enabled"

        val STAGES = listOf(
            "Mới quen",
            "Đang tìm hiểu",
            "Đang thả thính qua lại",
            "Bạn thân, muốn tiến xa hơn",
            "Đã hẹn hò vài lần",
            "Đang yêu nhau",
            "Đang giận nhau, cần làm lành",
            "Người yêu cũ",
        )

        val STYLES = listOf(
            "Tự nhiên",
            "Dễ thương",
            "Hài hước",
            "Chân thành, sâu sắc",
            "Lãng mạn",
            "Ngầu, kiệm lời",
            "Thả thính khéo léo",
        )
    }
}

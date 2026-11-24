package com.hanbubble

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.hanbubble.ai.AiModels
import com.hanbubble.ai.StyleSetting

/** Cài đặt của app, lưu riêng trên máy (không sao lưu lên cloud). */
class Prefs(context: Context) {
    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("han_bubble", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "").orEmpty()
        set(v) = sp.edit { putString("api_key", v.trim()) }

    var model: String
        get() = sp.getString("model", null)?.takeIf { id -> AiModels.options.any { it.id == id } } ?: AiModels.DEFAULT
        set(v) = sp.edit { putString("model", v) }

    var style: StyleSetting
        get() = runCatching { StyleSetting.valueOf(sp.getString("style", null) ?: "") }.getOrDefault(StyleSetting.AUTO)
        set(v) = sp.edit { putString("style", v.name) }

    var showRoman: Boolean
        get() = sp.getBoolean("show_roman", true)
        set(v) = sp.edit { putBoolean("show_roman", v) }

    var expandDetails: Boolean
        get() = sp.getBoolean("expand_details", false)
        set(v) = sp.edit { putBoolean("expand_details", v) }

    var bubbleEnabled: Boolean
        get() = sp.getBoolean(KEY_BUBBLE, false)
        set(v) = sp.edit { putBoolean(KEY_BUBBLE, v) }

    var bubbleX: Int
        get() = sp.getInt("bubble_x", -1)
        set(v) = sp.edit { putInt("bubble_x", v) }

    var bubbleY: Int
        get() = sp.getInt("bubble_y", -1)
        set(v) = sp.edit { putInt("bubble_y", v) }

    companion object {
        const val KEY_BUBBLE = "bubble_enabled"
    }
}

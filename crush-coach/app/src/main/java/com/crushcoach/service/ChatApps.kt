package com.crushcoach.service

/** Các app nhắn tin phổ biến mà bong bóng tự theo dõi khi đang BẬT. */
object ChatApps {
    private val known = mapOf(
        "com.facebook.orca" to "Messenger",
        "com.facebook.mlite" to "Messenger Lite",
        "com.facebook.katana" to "Facebook",
        "com.facebook.lite" to "Facebook Lite",
        "com.zing.zalo" to "Zalo",
        "com.instagram.android" to "Instagram",
        "com.instagram.barcelona" to "Threads",
        "org.telegram.messenger" to "Telegram",
        "org.telegram.messenger.web" to "Telegram",
        "org.thunderdog.challegram" to "Telegram X",
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.google.android.apps.messaging" to "Tin nhắn",
        "com.samsung.android.messaging" to "Tin nhắn Samsung",
        "jp.naver.line.android" to "LINE",
        "com.viber.voip" to "Viber",
        "com.snapchat.android" to "Snapchat",
        "com.discord" to "Discord",
        "com.ss.android.ugc.trill" to "TikTok",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.tinder" to "Tinder",
        "com.bumble.app" to "Bumble",
        "co.hinge.app" to "Hinge",
        "com.kakao.talk" to "KakaoTalk",
        "com.tencent.mm" to "WeChat",
        "com.twitter.android" to "X (Twitter)",
        "com.skype.raider" to "Skype",
    )

    fun isChat(pkg: String?) = pkg != null && pkg in known

    fun label(pkg: String?): String = when {
        pkg == null -> "Không rõ"
        else -> known[pkg] ?: pkg
    }
}

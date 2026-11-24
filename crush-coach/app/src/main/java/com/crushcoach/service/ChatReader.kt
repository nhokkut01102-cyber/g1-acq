package com.crushcoach.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.crushcoach.ai.ChatLine
import com.crushcoach.ai.ChatParser
import com.crushcoach.ai.ParsedChat
import com.crushcoach.ai.Side
import com.crushcoach.ai.TextItem

/** Ảnh chụp "bằng chữ" của cuộc trò chuyện đang mở. */
data class ChatSnapshot(
    val packageName: String?,
    val appName: String,
    val title: String?,
    val lines: List<ChatLine>,
    val draft: String?,
    val signature: String?,
    val lastSide: Side?,
    /** Có ô nhập tin ở nửa dưới màn hình, tức là đang ở trong một cuộc trò chuyện. */
    val hasChatInput: Boolean,
) {
    val chatKey get() = "$packageName|$title"
}

/** Đọc cây giao diện của app chat qua Trợ năng, và chèn chữ vào ô nhập tin. */
object ChatReader {
    private const val MAX_NODES = 2500
    private const val MAX_DEPTH = 64
    private val ignoredPackages = setOf("com.android.systemui")
    private val skippedClasses = listOf("Button", "ImageButton", "ImageView", "CheckBox", "Switch")

    fun findChatRoot(service: AccessibilityService): AccessibilityNodeInfo? {
        val own = service.packageName
        val windows = try {
            service.windows
        } catch (e: Exception) {
            emptyList<AccessibilityWindowInfo>()
        }
        val appWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        // Ưu tiên cửa sổ đang được chọn, sau đó tới cửa sổ app bất kỳ trên cùng.
        for (preferActive in listOf(true, false)) {
            for (w in appWindows) {
                if (preferActive && !(w.isActive || w.isFocused)) continue
                val root = w.root ?: continue
                val pkg = root.packageName?.toString()
                if (pkg == own || pkg in ignoredPackages) continue
                return root
            }
        }
        return service.rootInActiveWindow?.takeIf { it.packageName?.toString() != own }
    }

    fun read(service: AccessibilityService): ChatSnapshot {
        val root = findChatRoot(service)
            ?: return ChatSnapshot(null, "Không rõ", null, emptyList(), null, null, null, false)
        val pkg = root.packageName?.toString()

        val frame = Rect().also { root.getBoundsInScreen(it) }
        val items = ArrayList<TextItem>()
        var inputTop: Int? = null
        var draft: String? = null
        var visited = 0
        val r = Rect()

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (visited++ > MAX_NODES || depth > MAX_DEPTH) return
            if (!node.isVisibleToUser) return
            node.getBoundsInScreen(r)
            if (node.isEditable) {
                // Ô nhập tin là ô sửa được thấp nhất (ô tìm kiếm thường nằm trên cùng).
                val relTop = r.top - frame.top
                val current = inputTop
                if (current == null || relTop > current) {
                    inputTop = relTop
                    val showingHint = node.isShowingHintText ||
                        (node.hintText != null && node.hintText?.toString() == node.text?.toString())
                    draft = if (showingHint) null else node.text?.toString()?.trim()?.ifEmpty { null }
                }
                return
            }
            val cls = node.className?.toString().orEmpty()
            val label = node.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                ?: node.contentDescription?.toString()?.trim()
            if (!label.isNullOrEmpty() && skippedClasses.none { cls.endsWith(it) }) {
                items += TextItem(label, r.left - frame.left, r.top - frame.top, r.right - frame.left, r.bottom - frame.top)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                visit(child, depth + 1)
            }
        }
        visit(root, 0)

        val parsed: ParsedChat = ChatParser.parse(items, frame.width(), frame.height(), inputTop)
        return ChatSnapshot(
            packageName = pkg,
            appName = ChatApps.label(pkg),
            title = parsed.title,
            lines = parsed.lines,
            draft = draft,
            signature = ChatParser.signature(pkg, parsed),
            lastSide = ChatParser.lastSide(parsed),
            hasChatInput = (inputTop ?: -1) > frame.height() / 2,
        )
    }

    /** Ghi [text] vào ô nhập tin của app chat. Trả về false nếu app không cho. */
    fun insertText(service: AccessibilityService, text: String): Boolean {
        val root = findChatRoot(service) ?: return false
        val input = findEditable(root) ?: return false
        input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }?.let { return it }
        var best: AccessibilityNodeInfo? = null
        var bestBottom = Int.MIN_VALUE
        val r = Rect()
        var visited = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (visited++ > MAX_NODES || depth > MAX_DEPTH) return
            if (node.isEditable && node.isVisibleToUser) {
                node.getBoundsInScreen(r)
                // Ô nhập tin thường là ô sửa được nằm thấp nhất màn hình.
                if (r.bottom > bestBottom) {
                    bestBottom = r.bottom
                    best = node
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, depth + 1) }
        }
        visit(root, 0)
        return best
    }
}

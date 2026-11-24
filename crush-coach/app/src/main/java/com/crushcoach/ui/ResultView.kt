package com.crushcoach.ui

import android.view.LayoutInflater
import android.view.View
import com.crushcoach.ai.CoachResult
import com.crushcoach.databinding.ItemSuggestionBinding
import com.crushcoach.databinding.ViewResultBinding

/** Vẽ kết quả của AI vào khối view_result (dùng ở cả bảng nổi lẫn màn hình chính). */
class ResultView(private val b: ViewResultBinding) {

    fun showLoading(message: String) {
        clear()
        b.loadingRow.visibility = View.VISIBLE
        b.loadingText.text = message
    }

    fun showError(message: String) {
        clear()
        b.errorText.show(message)
    }

    fun show(
        result: CoachResult,
        onCopy: (String) -> Unit,
        onInsert: ((String) -> Unit)?,
    ) {
        clear()
        b.moodText.show(result.mood)
        b.readText.show(result.read)
        if (result.answer.isNotBlank()) {
            b.answerTitle.visibility = View.VISIBLE
            b.answerText.show(result.answer)
        }
        if (result.suggestions.isNotEmpty()) {
            b.suggestTitle.visibility = View.VISIBLE
            val inflater = LayoutInflater.from(b.root.context)
            result.suggestions.forEach { s ->
                val item = ItemSuggestionBinding.inflate(inflater, b.suggestions, false)
                item.styleTag.text = s.style.ifBlank { "Gợi ý" }
                item.suggestionText.text = s.text
                item.whyText.show(s.why)
                item.btnCopy.setOnClickListener { onCopy(s.text) }
                if (onInsert != null) {
                    item.btnInsert.setOnClickListener { onInsert(s.text) }
                    item.suggestionText.setOnClickListener { onInsert(s.text) }
                } else {
                    item.btnInsert.visibility = View.GONE
                    item.suggestionText.setOnClickListener { onCopy(s.text) }
                }
                b.suggestions.addView(item.root)
            }
        }
        b.warningText.show(result.warning.takeIf { it.isNotBlank() }?.let { "⚠️ $it" })
        if (result.tips.isNotEmpty()) {
            b.tipsTitle.visibility = View.VISIBLE
            b.tipsText.show(result.tips.joinToString("\n") { "• $it" })
        }
    }

    fun clear() {
        b.loadingRow.visibility = View.GONE
        listOf(
            b.errorText, b.moodText, b.readText, b.answerTitle, b.answerText,
            b.suggestTitle, b.warningText, b.tipsTitle, b.tipsText,
        ).forEach { it.visibility = View.GONE }
        b.suggestions.removeAllViews()
    }

    private fun android.widget.TextView.show(text: String?) {
        if (text.isNullOrBlank()) {
            visibility = View.GONE
        } else {
            this.text = text
            visibility = View.VISIBLE
        }
    }
}

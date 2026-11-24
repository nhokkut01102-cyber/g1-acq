package com.hanbubble.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.hanbubble.R
import com.hanbubble.ai.KoreanResult
import com.hanbubble.ai.Pos
import com.hanbubble.ai.SegmentAligner
import com.hanbubble.databinding.ViewKoreanBinding

/** Vẽ một câu đã dịch: câu tiếng Hàn tô màu theo từ loại, phiên âm, nghĩa, văn phong, từ vựng & ngữ pháp. */
object KoreanView {

    /** Câu tiếng Hàn với mỗi mẩu được tô màu theo từ loại. */
    fun colored(result: KoreanResult): CharSequence {
        val sb = SpannableStringBuilder(result.korean)
        for (r in SegmentAligner.align(result.korean, result.segments)) {
            if (r.pos == Pos.PUNCTUATION || r.pos == Pos.OTHER) continue
            sb.setSpan(ForegroundColorSpan(r.pos.color), r.start, r.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    /**
     * Gắn kết quả vào khối view_korean.
     * @param expanded mở sẵn phần từ loại & ngữ pháp hay không.
     */
    fun bind(b: ViewKoreanBinding, result: KoreanResult, showRoman: Boolean, expanded: Boolean) {
        val ctx = b.root.context
        b.sourceText.text = "✍️ ${result.source}"
        b.koreanText.text = colored(result)
        b.romanText.text = result.romanization
        b.romanText.visibility = if (showRoman && result.romanization.isNotBlank()) View.VISIBLE else View.GONE
        b.meaningText.text = "🇻🇳 ${result.meaningVi}"
        b.meaningText.visibility = if (result.meaningVi.isNotBlank()) View.VISIBLE else View.GONE
        val reg = result.register
        b.registerChip.text = if (reg != null) "${reg.korean} · ${reg.label}" else "Văn phong: ?"

        b.details.removeAllViews()
        buildDetails(ctx, b.details, result)
        setExpanded(b, expanded)
        b.detailsToggle.setOnClickListener { setExpanded(b, b.details.visibility != View.VISIBLE) }
    }

    private fun setExpanded(b: ViewKoreanBinding, expanded: Boolean) {
        b.details.visibility = if (expanded) View.VISIBLE else View.GONE
        b.detailsToggle.setText(if (expanded) R.string.details_hide else R.string.details_show)
    }

    private fun buildDetails(ctx: Context, box: LinearLayout, r: KoreanResult) {
        if (r.registerReason.isNotBlank()) {
            box.addView(text(ctx, "💡 ${r.registerReason}", 13f, R.color.ink_muted).apply { setPadding(0, dp(ctx, 6), 0, 0) })
        }
        if (r.correction.isNotBlank()) {
            box.addView(text(ctx, "✏️ ${r.correction}", 14f, R.color.warn_ink).apply {
                setBackgroundResource(R.drawable.box_warn)
                val p = dp(ctx, 10)
                setPadding(p, p, p, p)
                (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(ctx, 8)
            })
        }

        val words = r.segments.filter { it.pos != Pos.PUNCTUATION && it.text.isNotBlank() }
        if (words.isNotEmpty()) {
            box.addView(heading(ctx, "🧩 Từ loại"))
            words.forEach { s ->
                val sb = SpannableStringBuilder()
                append(sb, s.text.trim(), ForegroundColorSpan(s.pos.color), StyleSpan(Typeface.BOLD))
                if (s.base.isNotBlank() && s.base != s.text.trim()) sb.append("  →  ").append(s.base)
                sb.append("   ")
                append(sb, s.pos.label, ForegroundColorSpan(s.pos.color), RelativeSizeSpan(0.85f))
                if (s.meaning.isNotBlank()) sb.append("\n").append(s.meaning)
                box.addView(text(ctx, sb, 14f, R.color.ink).apply { setPadding(0, dp(ctx, 6), 0, 0) })
            }
        }

        if (r.grammar.isNotEmpty()) {
            box.addView(heading(ctx, "📐 Ngữ pháp"))
            r.grammar.forEach { g ->
                val sb = SpannableStringBuilder()
                append(sb, g.pattern, ForegroundColorSpan(Pos.ENDING.color), StyleSpan(Typeface.BOLD))
                if (g.meaning.isNotBlank()) sb.append("  ·  ").append(g.meaning)
                if (g.inSentence.isNotBlank()) {
                    sb.append("\nTrong câu: ")
                    append(sb, g.inSentence, StyleSpan(Typeface.BOLD))
                }
                if (g.explanation.isNotBlank()) sb.append("\n").append(g.explanation)
                box.addView(text(ctx, sb, 14f, R.color.ink).apply { setPadding(0, dp(ctx, 8), 0, 0) })
            }
        }
    }

    /** Bảng chú thích màu từ loại. */
    fun fillLegend(group: ChipGroup) {
        group.removeAllViews()
        Pos.legend.forEach { p ->
            group.addView(Chip(group.context).apply {
                text = "${p.label} · ${p.korean}"
                setTextColor(p.color)
                isClickable = false
                isCheckable = false
                chipStrokeWidth = 0f
            })
        }
    }

    private fun append(sb: SpannableStringBuilder, s: String, vararg spans: Any) {
        val start = sb.length
        sb.append(s)
        spans.forEach { sb.setSpan(it, start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }

    private fun heading(ctx: Context, s: String) = text(ctx, s, 14f, R.color.ink).apply {
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(ctx, 12), 0, 0)
    }

    private fun text(ctx: Context, s: CharSequence, sizeSp: Float, color: Int) = TextView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        text = s
        textSize = sizeSp
        setTextColor(ContextCompat.getColor(ctx, color))
        setLineSpacing(0f, 1.15f)
    }

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}

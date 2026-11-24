package com.hanbubble.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView

/** ScrollView cao tối đa một tỉ lệ màn hình, để khung dịch không che hết app bên dưới. */
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ScrollView(context, attrs) {

    var maxHeightFraction = 0.42f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val max = (resources.displayMetrics.heightPixels * maxHeightFraction).toInt()
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST))
    }
}

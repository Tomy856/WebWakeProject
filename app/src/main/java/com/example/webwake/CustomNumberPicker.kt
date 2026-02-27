package com.example.webwake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.NumberPicker

class CustomNumberPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : NumberPicker(context, attrs) {

    private val selectedPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        color = 0xFFFFFFFF.toInt()
        textSize = spToPx(42f)
        isFakeBoldText = true
    }

    private val dimPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        color = 0xFF555555.toInt()
        textSize = spToPx(42f)
        isFakeBoldText = true
    }

    private fun spToPx(sp: Float): Float =
        sp * context.resources.displayMetrics.scaledDensity

    init {
        // リフレクションでPaintを差し替え
        try {
            val f = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
            f.isAccessible = true
            (f.get(this) as? Paint)?.apply {
                color = 0xFF555555.toInt()
                isFakeBoldText = true
            }
        } catch (_: Exception) {}
        try {
            val f = NumberPicker::class.java.getDeclaredField("mSelectedTextColor")
            f.isAccessible = true
            f.setInt(this, 0xFFFFFFFF.toInt())
        } catch (_: Exception) {}
        try {
            val f = NumberPicker::class.java.getDeclaredField("mTextColor")
            f.isAccessible = true
            f.setInt(this, 0xFF555555.toInt())
        } catch (_: Exception) {}
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 選択行の中心Y座標
        val centerY = (height / 2).toFloat()
        // 選択行の高さ（アイテム1つ分）
        val itemHeight = height / 3f  // 上中下3行表示

        // 現在の値を取得
        val displayedValues = displayedValues
        val currentValue = value
        val minVal = minValue
        val maxVal = maxValue

        fun getDisplayText(v: Int): String {
            return if (displayedValues != null && v - minVal < displayedValues.size)
                displayedValues[v - minVal]
            else
                v.toString()
        }

        // 選択行（中央）を白で再描画
        val centerText = getDisplayText(currentValue)
        canvas.drawText(centerText, (width / 2).toFloat(), centerY + selectedPaint.textSize / 3, selectedPaint)

        // 上の行（暗い）
        if (currentValue > minVal) {
            val topText = getDisplayText(currentValue - 1)
            canvas.drawText(topText, (width / 2).toFloat(), centerY - itemHeight + dimPaint.textSize / 3, dimPaint)
        }

        // 下の行（暗い）
        if (currentValue < maxVal) {
            val bottomText = getDisplayText(currentValue + 1)
            canvas.drawText(bottomText, (width / 2).toFloat(), centerY + itemHeight + dimPaint.textSize / 3, dimPaint)
        }
    }
}

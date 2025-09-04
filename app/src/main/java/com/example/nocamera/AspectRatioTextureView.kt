package com.example.nocamera

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView

class AspectRatioTextureView(context: Context, attrs: AttributeSet?) : TextureView(context, attrs) {
    private var ratioWidth = 0
    private var ratioHeight = 0

    constructor(context: Context) : this(context, null)

    fun setAspectRatio(width: Int, height: Int) {
        if (width < 0 || height < 0) throw IllegalArgumentException("Size cannot be negative.")
        ratioWidth = width
        ratioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = measuredWidth
        val height = measuredHeight
        if (ratioWidth == 0 || ratioHeight == 0) {
            setMeasuredDimension(width, height)
        } else {
            val ratio = ratioWidth.toFloat() / ratioHeight.toFloat()
            val calculatedHeight = (width / ratio).toInt()
            if (calculatedHeight <= height) {
                setMeasuredDimension(width, calculatedHeight)
            } else {
                val calculatedWidth = (height * ratio).toInt()
                setMeasuredDimension(calculatedWidth, height)
            }
        }
    }
}

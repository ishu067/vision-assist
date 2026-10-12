package com.visionassist.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class FrameOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var frame: Bitmap? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun updateFrame(nextFrame: Bitmap) {
        frame?.recycle()
        frame = nextFrame
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        frame?.let { bitmap ->
            canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, width, height), paint)
        }
    }

    override fun onDetachedFromWindow() {
        frame?.recycle()
        frame = null
        super.onDetachedFromWindow()
    }
}

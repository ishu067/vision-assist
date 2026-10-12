package com.visionassist.android

import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager

class MediaProjectionCapture(
    private val context: Context,
    private val projection: MediaProjection,
    private val onFrame: (Bitmap) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null

    fun start() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        reader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ imageReader ->
            imageReader.acquireLatestImage()?.use { image ->
                val plane = image.planes.firstOrNull() ?: return@use
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val padded = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888,
                )
                padded.copyPixelsFromBuffer(plane.buffer)
                val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
                padded.recycle()
                onFrame(cropped)
            }
        }, handler)

        display = projection.createVirtualDisplay(
            "VisionAssistCapture",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface,
            null,
            handler,
        )
    }

    fun stop() {
        display?.release()
        display = null
        reader?.close()
        reader = null
        projection.stop()
    }
}

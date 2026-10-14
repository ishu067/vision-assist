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
    private val onStopped: () -> Unit,
    private val onFailure: (Exception) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var started = false
    private var stopped = false
    private var callbackRegistered = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            if (stopped) return
            val cleanupFailure = releaseResources() ?: return onStopped()
            onFailure(cleanupFailure)
        }
    }

    fun start() {
        check(!started) { "Screen capture has already been started." }
        check(!stopped) { "Screen capture has already been stopped." }

        try {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            require(width > 0 && height > 0 && metrics.densityDpi > 0) {
                "The display returned invalid capture dimensions."
            }

            projection.registerCallback(projectionCallback, handler)
            callbackRegistered = true
            reader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
            reader?.setOnImageAvailableListener(::onImageAvailable, handler)
            display = requireNotNull(
                projection.createVirtualDisplay(
                    "VisionAssistCapture",
                    width,
                    height,
                    metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader?.surface,
                    null,
                    handler,
                ),
            ) { "Android could not create a virtual display for screen capture." }
            started = true
        } catch (exception: Exception) {
            try {
                stop()
            } catch (cleanupException: Exception) {
                exception.addSuppressed(cleanupException)
            }
            throw exception
        }
    }

    fun stop() {
        if (stopped) return
        val cleanupFailure = releaseResources()
        try {
            projection.stop()
        } catch (stopException: Exception) {
            if (cleanupFailure != null) {
                cleanupFailure.addSuppressed(stopException)
            } else {
                throw stopException
            }
        }
        if (cleanupFailure != null) throw cleanupFailure
    }

    private fun onImageAvailable(imageReader: ImageReader) {
        try {
            imageReader.acquireLatestImage()?.use { image ->
                val plane = image.planes.firstOrNull()
                    ?: throw IllegalStateException("Captured frame did not contain an image plane.")
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                require(pixelStride > 0 && rowStride >= pixelStride * image.width) {
                    "Captured frame has invalid pixel or row stride."
                }
                val rowPadding = rowStride - pixelStride * image.width
                val padded = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888,
                )
                val cropped = try {
                    padded.copyPixelsFromBuffer(plane.buffer)
                    Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                } finally {
                    padded.recycle()
                }
                try {
                    onFrame(cropped)
                } catch (exception: Exception) {
                    cropped.recycle()
                    throw exception
                }
            }
        } catch (exception: Exception) {
            if (!stopped) {
                try {
                    stop()
                } catch (stopException: Exception) {
                    exception.addSuppressed(stopException)
                }
                onFailure(exception)
            }
        }
    }

    private fun releaseResources(): Exception? {
        if (stopped) return null
        stopped = true
        var failure: Exception? = null
        try {
            reader?.setOnImageAvailableListener(null, null)
        } catch (exception: Exception) {
            failure = exception
        }
        try {
            display?.release()
        } catch (exception: Exception) {
            if (failure == null) failure = exception else failure.addSuppressed(exception)
        }
        display = null
        try {
            reader?.close()
        } catch (exception: Exception) {
            if (failure == null) failure = exception else failure.addSuppressed(exception)
        }
        reader = null
        if (callbackRegistered) {
            try {
                projection.unregisterCallback(projectionCallback)
            } catch (exception: Exception) {
                if (failure == null) failure = exception else failure.addSuppressed(exception)
            }
            callbackRegistered = false
        }
        return failure
    }
}

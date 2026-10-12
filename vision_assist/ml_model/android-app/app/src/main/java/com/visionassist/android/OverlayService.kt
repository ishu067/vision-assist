package com.visionassist.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import org.opencv.android.OpenCVLoader

/**
 * Owns the consented screen-capture pipeline and non-touchable overlay window.
 */
class OverlayService : Service() {
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var capture: MediaProjectionCapture? = null
    private var projection: MediaProjection? = null
    private val processor = OpenCvFrameProcessor()
    private var openCvReady = false

    override fun onCreate() {
        super.onCreate()
        openCvReady = OpenCVLoader.initLocal()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) return START_NOT_STICKY
        val foregroundPackage = intent?.getStringExtra(EXTRA_FOREGROUND_PACKAGE)
        if (SensitiveAppPolicy.shouldSuspend(foregroundPackage)) {
            stopCapture()
            hideOverlay()
            return START_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val resultData = intent?.let(::readResultData)
        if (resultCode >= 0 && resultData != null && capture == null) {
            startForegroundCapture(resultCode, resultData)
        }
        if (overlayView == null) showOverlay()
        return START_STICKY
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = FrameOverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager?.addView(overlayView, params)
    }

    override fun onDestroy() {
        stopCapture()
        hideOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hideOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        windowManager = null
    }

    private fun startForegroundCapture(resultCode: Int, resultData: Intent) {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("VisionAssist is active")
            .setContentText("Screen enhancement is running on this device")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, resultData)
        projection?.let { mediaProjection ->
            val profile = ProfileStorage(this).load() ?: VisualProfile()
            capture = MediaProjectionCapture(this, mediaProjection) { frame ->
                val processed = if (openCvReady) {
                    runCatching { processor.process(frame, profile) }.getOrElse { frame }
                } else {
                    frame
                }
                if (processed !== frame) frame.recycle()
                (overlayView as? FrameOverlayView)?.post {
                    (overlayView as? FrameOverlayView)?.updateFrame(processed)
                }
            }.also(MediaProjectionCapture::start)
        }
    }

    private fun stopCapture() {
        capture?.stop()
        capture = null
        projection = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VisionAssist screen enhancement",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun readResultData(intent: Intent): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
    } else {
        intent.getParcelableExtra(EXTRA_RESULT_DATA)
    }

    companion object {
        private const val CHANNEL_ID = "vision_assist_capture"
        private const val NOTIFICATION_ID = 2301
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        const val EXTRA_FOREGROUND_PACKAGE = "foreground_package"
    }
}

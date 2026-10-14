package com.visionassist.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
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
    private var processingErrorReported = false

    override fun onCreate() {
        super.onCreate()
        openCvReady = OpenCVLoader.initLocal()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            hideOverlay()
            ProfileStorage(this).setCaptureActive(false)
            publishStatus(false, "Screen enhancement stopped.")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            if (!Settings.canDrawOverlays(this)) {
                failStart(startId, "Overlay access is not allowed. Enable it in Android Settings and try again.")
                return START_NOT_STICKY
            }
            val foregroundPackage = intent?.getStringExtra(EXTRA_FOREGROUND_PACKAGE)
            if (SensitiveAppPolicy.shouldSuspend(foregroundPackage)) {
                stopCapture()
                hideOverlay()
                ProfileStorage(this).setCaptureActive(false)
                publishStatus(false, "Enhancement suspended for a sensitive app.")
                stopSelf(startId)
                return START_NOT_STICKY
            }

            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
            val resultData = intent?.let(::readResultData)
            if (resultCode < 0 || resultData == null) {
                failStart(startId, "Screen-capture consent data was unavailable. Start again from VisionAssist.")
                return START_NOT_STICKY
            }
            if (capture == null) startForegroundCapture(resultCode, resultData)
            if (overlayView == null) showOverlay()
            ProfileStorage(this).setCaptureActive(true)
            val processingMessage = if (openCvReady) {
                "Screen enhancement is running."
            } else {
                "Capture is running, but OpenCV did not initialize; frames are shown without enhancement."
            }
            publishStatus(true, processingMessage)
            return START_NOT_STICKY
        } catch (exception: Exception) {
            failStart(startId, "Could not start screen enhancement: ${exception.localizedMessage ?: "unexpected service error"}")
            return START_NOT_STICKY
        }
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
        ProfileStorage(this).setCaptureActive(false)
        publishStatus(false, "Screen enhancement stopped.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hideOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        windowManager = null
    }

    private fun startForegroundCapture(resultCode: Int, resultData: Intent) {
        val stopIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("VisionAssist is active")
            .setContentText("Screen enhancement is running. Use Stop to end capture.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "Stop",
                    stopIntent,
                ).build(),
            )
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
        val mediaProjection = requireNotNull(manager.getMediaProjection(resultCode, resultData)) {
            "Android did not provide a screen-capture session."
        }
        projection = mediaProjection
        val profile = ProfileStorage(this).load() ?: VisualProfile()
        capture = MediaProjectionCapture(this, mediaProjection) { frame ->
            val processed = if (openCvReady) {
                try {
                    processor.process(frame, profile)
                } catch (exception: Exception) {
                    Log.e(TAG, "Frame enhancement failed; displaying the original frame", exception)
                    if (!processingErrorReported) {
                        processingErrorReported = true
                        publishStatus(
                            true,
                            "Capture is running, but a frame could not be enhanced. The original frame is being shown.",
                        )
                    }
                    frame
                }
            } else {
                frame
            }
            if (processed !== frame) frame.recycle()
            (overlayView as? FrameOverlayView)?.post {
                (overlayView as? FrameOverlayView)?.updateFrame(processed)
            }
        }.also(MediaProjectionCapture::start)
    }

    private fun stopCapture() {
        capture?.stop()
        capture = null
        projection = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun failStart(startId: Int, message: String) {
        Log.e(TAG, message)
        stopCapture()
        hideOverlay()
        ProfileStorage(this).setCaptureActive(false)
        publishStatus(false, message)
        stopSelf(startId)
    }

    private fun publishStatus(running: Boolean, message: String) {
        sendBroadcast(
            Intent(ACTION_STATUS_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_STATUS_RUNNING, running)
                .putExtra(EXTRA_STATUS_MESSAGE, message),
        )
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
        private const val TAG = "VisionAssistService"
        private const val CHANNEL_ID = "vision_assist_capture"
        private const val NOTIFICATION_ID = 2301
        const val ACTION_STOP = "com.visionassist.android.action.STOP"
        const val ACTION_STATUS_CHANGED = "com.visionassist.android.action.STATUS_CHANGED"
        const val EXTRA_STATUS_RUNNING = "status_running"
        const val EXTRA_STATUS_MESSAGE = "status_message"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        const val EXTRA_FOREGROUND_PACKAGE = "foreground_package"
    }
}

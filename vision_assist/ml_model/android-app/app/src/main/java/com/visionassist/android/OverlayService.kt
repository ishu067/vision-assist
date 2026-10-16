package com.visionassist.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader

/**
 * Owns the consented screen-capture pipeline and non-touchable overlay window.
 */
class OverlayService : Service() {
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var capture: MediaProjectionCapture? = null
    private var projection: MediaProjection? = null
    private var activeProfile = VisualProfile()
    private val processor = OpenCvFrameProcessor()
    private val handler = Handler(Looper.getMainLooper())
    private var openCvReady = false
    private var processingErrorReported = false
    private var firstFrameReceived = false
    private var serviceActive = false
    private var guardReceiverRegistered = false
    private var overlaySuppressedForVisionAssist = false

    private val accessibilityGuardReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!serviceActive) return
            when (intent?.action) {
                ForegroundAppAccessibilityService.ACTION_FOREGROUND_PACKAGE_CHANGED -> {
                    val packageName = intent.getStringExtra(
                        ForegroundAppAccessibilityService.EXTRA_GUARD_VALUE,
                    )
                    if (packageName.isNullOrBlank()) {
                        failSession("Foreground-app detection returned no app name; enhancement was suspended.")
                    } else if (SensitiveAppPolicy.shouldSuspend(packageName)) {
                        failSession("A potentially sensitive app was detected; enhancement was suspended.")
                    } else if (packageName == this@OverlayService.packageName) {
                        pauseOverlayForVisionAssist()
                    } else {
                        resumeOverlayForOtherApp()
                    }
                }

                ForegroundAppAccessibilityService.ACTION_GUARD_UNAVAILABLE -> {
                    val detail = intent.getStringExtra(
                        ForegroundAppAccessibilityService.EXTRA_GUARD_VALUE,
                    ).orEmpty()
                    failSession(
                        if (detail.isBlank()) {
                            "Foreground-app detection is unavailable; enhancement was suspended."
                        } else {
                            "$detail Enhancement was suspended."
                        },
                    )
                }
            }
        }
    }

    private val captureStartupTimeout = Runnable {
        if (serviceActive && !firstFrameReceived) {
            failSession("Android granted capture, but no screen frame arrived. Stop and try again.")
        }
    }

    private val accessibilityWatchdog = object : Runnable {
        override fun run() {
            if (!serviceActive) return
            if (!AccessibilityGuard.isAvailable(this@OverlayService)) {
                failSession("Foreground-app detection stopped responding; enhancement was suspended.")
                return
            }
            handler.postDelayed(this, ACCESSIBILITY_WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerAccessibilityGuardReceiver()
        openCvReady = try {
            OpenCVLoader.initLocal()
        } catch (exception: Exception) {
            Log.e(TAG, "OpenCV initialization failed", exception)
            false
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSession("Screen enhancement stopped.")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
            val resultData = intent?.let(::readResultData)
            if (resultCode != Activity.RESULT_OK || resultData == null) {
                failStart(startId, "Screen-capture consent data was unavailable. Start again from VisionAssist.")
                return START_NOT_STICKY
            }

            if (serviceActive) {
                publishStatus(true, storedStatusMessage())
                return START_NOT_STICKY
            }

            startForegroundNotification()
            if (!Settings.canDrawOverlays(this)) {
                failStart(startId, "Overlay access is not allowed. Enable it in Android Settings and try again.")
                return START_NOT_STICKY
            }
            if (!AccessibilityGuard.isAvailable(this)) {
                failStart(
                    startId,
                    "Foreground-app detection is unavailable. Enable VisionAssist in Android Accessibility settings, then try again.",
                )
                return START_NOT_STICKY
            }
            val foregroundPackage = intent?.getStringExtra(EXTRA_FOREGROUND_PACKAGE)
                ?: ProfileStorage(this).lastForegroundPackage()
            if (SensitiveAppPolicy.shouldSuspend(foregroundPackage)) {
                stopSession("Enhancement suspended for a sensitive app.")
                stopSelf(startId)
                return START_NOT_STICKY
            }

            overlaySuppressedForVisionAssist = foregroundPackage == packageName
            processingErrorReported = false
            firstFrameReceived = false
            serviceActive = true
            captureServiceActive = true
            publishStatus(true, "Starting screen capture. Waiting for the first frame…")
            startForegroundCapture(resultCode, resultData)
            if (!overlaySuppressedForVisionAssist && overlayView == null) showOverlay()
            handler.postDelayed(captureStartupTimeout, CAPTURE_START_TIMEOUT_MS)
            handler.postDelayed(accessibilityWatchdog, ACCESSIBILITY_WATCHDOG_INTERVAL_MS)
            return START_NOT_STICKY
        } catch (exception: Exception) {
            failStart(
                startId,
                "Could not start screen enhancement: ${exception.localizedMessage ?: "unexpected service error"}",
            )
            return START_NOT_STICKY
        }
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = FrameOverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager?.addView(view, params)
        overlayView = view
    }

    private fun pauseOverlayForVisionAssist() {
        if (overlaySuppressedForVisionAssist) return
        overlaySuppressedForVisionAssist = true
        hideOverlay()
        val message = overlayPausedMessage()
        publishStatus(true, message)
        updateNotification("VisionAssist overlay paused", message)
    }

    private fun resumeOverlayForOtherApp() {
        if (!overlaySuppressedForVisionAssist) return
        try {
            showOverlay()
            overlaySuppressedForVisionAssist = false
            val message = "Screen capture is running. Use Stop to end the session."
            publishStatus(true, message)
            updateNotification("VisionAssist is active", message)
        } catch (exception: Exception) {
            failSession(
                "Could not resume the enhancement overlay: ${exception.localizedMessage ?: "unexpected overlay error"}",
            )
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(captureStartupTimeout)
        handler.removeCallbacks(accessibilityWatchdog)
        if (guardReceiverRegistered) {
            unregisterReceiver(accessibilityGuardReceiver)
            guardReceiverRegistered = false
        }
        val wasActive = serviceActive
        val cleanupFailure = releaseCapture()
        hideOverlay()
        serviceActive = false
        overlaySuppressedForVisionAssist = false
        captureServiceActive = false
        if (wasActive) {
            val message = if (cleanupFailure == null) {
                "Screen enhancement stopped unexpectedly. Start it again from VisionAssist."
            } else {
                "Screen enhancement stopped unexpectedly; capture cleanup reported an error. Start it again."
            }
            publishStatus(false, message)
        } else {
            ProfileStorage(this).setCaptureActive(false)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hideOverlay() {
        overlayView?.let { view ->
            windowManager?.removeView(view)
        }
        overlayView = null
        windowManager = null
    }

    private fun startForegroundCapture(resultCode: Int, resultData: Intent) {
        val manager = requireNotNull(getSystemService(MediaProjectionManager::class.java)) {
            "Android's screen-capture service is unavailable."
        }
        val mediaProjection = requireNotNull(manager.getMediaProjection(resultCode, resultData)) {
            "Android did not provide a screen-capture session."
        }
        projection = mediaProjection
        val profile = ProfileStorage(this).load() ?: VisualProfile()
        activeProfile = profile
        capture = MediaProjectionCapture(
            context = this,
            projection = mediaProjection,
            onFrame = ::onCapturedFrame,
            onStopped = ::onProjectionStopped,
            onFailure = ::onCaptureFailure,
        ).also(MediaProjectionCapture::start)
    }

    private fun startForegroundNotification() {
        val stopIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("VisionAssist is starting")
            .setContentText("Waiting for the first captured screen frame.")
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
    }

    private fun onCapturedFrame(frame: android.graphics.Bitmap) {
        if (!serviceActive) {
            frame.recycle()
            return
        }
        if (!firstFrameReceived) {
            firstFrameReceived = true
            handler.removeCallbacks(captureStartupTimeout)
            val message = if (overlaySuppressedForVisionAssist) {
                overlayPausedMessage()
            } else if (openCvReady) {
                "Screen capture is running. Use Stop to end the session."
            } else {
                "Capture is running, but OpenCV did not initialize; frames are shown without enhancement."
            }
            publishStatus(true, message)
            updateNotification(
                if (overlaySuppressedForVisionAssist) "VisionAssist overlay paused" else "VisionAssist is active",
                message,
            )
        }

        if (overlaySuppressedForVisionAssist) {
            frame.recycle()
            return
        }

        val processed = if (openCvReady) {
            try {
                processor.process(frame, activeProfile)
            } catch (exception: Exception) {
                Log.e(TAG, "Frame enhancement failed; displaying the original frame", exception)
                if (!processingErrorReported) {
                    processingErrorReported = true
                    publishStatus(
                        true,
                        "Capture continues, but a frame could not be enhanced. The original frame is shown.",
                    )
                    updateNotification(
                        "VisionAssist capture warning",
                        "A frame could not be enhanced; the original is shown.",
                    )
                }
                frame
            }
        } else {
            frame
        }
        if (processed !== frame) frame.recycle()

        val view = overlayView as? FrameOverlayView
        if (view == null || !view.post { view.updateFrame(processed) }) {
            processed.recycle()
        }
    }

    private fun onProjectionStopped() {
        handler.removeCallbacks(captureStartupTimeout)
        capture = null
        projection = null
        if (!serviceActive) return
        hideOverlay()
        serviceActive = false
        overlaySuppressedForVisionAssist = false
        captureServiceActive = false
        ProfileStorage(this).setCaptureActive(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        publishStatus(false, "Android ended screen capture. Start again from VisionAssist if needed.")
        stopSelf()
    }

    private fun onCaptureFailure(exception: Exception) {
        Log.e(TAG, "Screen capture failed", exception)
        failSession("Screen capture failed: ${exception.localizedMessage ?: "unexpected capture error"}. Start again.")
    }

    private fun stopSession(message: String) {
        handler.removeCallbacks(captureStartupTimeout)
        handler.removeCallbacks(accessibilityWatchdog)
        serviceActive = false
        overlaySuppressedForVisionAssist = false
        captureServiceActive = false
        val cleanupFailure = releaseCapture()
        hideOverlay()
        ProfileStorage(this).setCaptureActive(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (cleanupFailure == null) {
            publishStatus(false, message)
        } else {
            Log.e(TAG, "Capture cleanup failed", cleanupFailure)
            publishStatus(false, "$message Capture cleanup reported an error: ${cleanupFailure.localizedMessage}")
        }
    }

    private fun failStart(startId: Int, message: String) {
        Log.e(TAG, message)
        stopSession(message)
        stopSelf(startId)
    }

    private fun failSession(message: String) {
        Log.e(TAG, message)
        stopSession(message)
        stopSelf()
    }

    private fun releaseCapture(): Exception? {
        handler.removeCallbacks(captureStartupTimeout)
        val currentCapture = capture
        capture = null
        projection = null
        return try {
            currentCapture?.stop()
            null
        } catch (exception: Exception) {
            exception
        }
    }

    private fun updateNotification(title: String, message: String) {
        val stopIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
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
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun storedStatusMessage(): String = ProfileStorage(this).captureStatusMessage()

    private fun overlayPausedMessage(): String =
        "Capture is running. The overlay is paused while VisionAssist is open to prevent a screen-within-screen effect."

    private fun publishStatus(active: Boolean, message: String) {
        ProfileStorage(this).setCaptureStatus(active, message)
        sendBroadcast(
            Intent(ACTION_STATUS_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_STATUS_RUNNING, active)
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

    private fun registerAccessibilityGuardReceiver() {
        val filter = IntentFilter().apply {
            addAction(ForegroundAppAccessibilityService.ACTION_FOREGROUND_PACKAGE_CHANGED)
            addAction(ForegroundAppAccessibilityService.ACTION_GUARD_UNAVAILABLE)
        }
        ContextCompat.registerReceiver(
            this,
            accessibilityGuardReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        guardReceiverRegistered = true
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
        private const val CAPTURE_START_TIMEOUT_MS = 15_000L
        private const val ACCESSIBILITY_WATCHDOG_INTERVAL_MS = 2_500L
        const val ACTION_STOP = "com.visionassist.android.action.STOP"
        const val ACTION_STATUS_CHANGED = "com.visionassist.android.action.STATUS_CHANGED"
        const val EXTRA_STATUS_RUNNING = "status_running"
        const val EXTRA_STATUS_MESSAGE = "status_message"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        const val EXTRA_FOREGROUND_PACKAGE = "foreground_package"

        @Volatile
        var captureServiceActive: Boolean = false
            private set
    }
}

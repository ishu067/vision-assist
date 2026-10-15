package com.visionassist.android

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.visionassist.android.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: ProfileStorage
    private val demoExecutor = Executors.newSingleThreadExecutor()
    private var syntheticPreview: Bitmap? = null
    private var enhancedPreview: Bitmap? = null
    private var statusReceiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != OverlayService.ACTION_STATUS_CHANGED) return
            val running = intent.getBooleanExtra(OverlayService.EXTRA_STATUS_RUNNING, false)
            val message = intent.getStringExtra(OverlayService.EXTRA_STATUS_MESSAGE).orEmpty()
            showCaptureStatus(running, message)
        }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            storage.save(readProfile())
            binding.actionMessage.text = "Starting capture. Keep VisionAssist open while Android starts the service."
            val serviceIntent = Intent(this, OverlayService::class.java).apply {
                putExtra(OverlayService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(OverlayService.EXTRA_RESULT_DATA, result.data)
            }
            try {
                ContextCompat.startForegroundService(this, serviceIntent)
            } catch (exception: Exception) {
                showCaptureStatus(
                    false,
                    "Android could not start the capture service: ${exception.localizedMessage ?: "unexpected service error"}",
                )
            }
        } else {
            showCaptureStatus(false, "Screen-capture consent was not granted. Enhancement did not start.")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refreshPermissionStatus()
        binding.actionMessage.text = if (granted) {
            "Notifications are allowed. Tap Start screen enhancement when you’re ready."
        } else {
            "Allow notifications before starting so the ongoing capture and Stop control remain visible."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        storage = ProfileStorage(this)
        restoreProfile()

        binding.saveButton.setOnClickListener {
            storage.save(readProfile())
            binding.actionMessage.text = "Your profile was saved on this device."
        }
        binding.overlaySettingsButton.setOnClickListener { openOverlaySettings() }
        binding.accessibilitySettingsButton.setOnClickListener { openAccessibilitySettings() }
        binding.notificationPermissionButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        binding.startButton.setOnClickListener { startEnhancement() }
        binding.stopButton.setOnClickListener { stopEnhancement() }
        binding.demoButton.setOnClickListener { runOfflineDemo() }
        refreshPermissionStatus()
        refreshCaptureStatus()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(OverlayService.ACTION_STATUS_CHANGED)
        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        statusReceiverRegistered = true
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        if (::storage.isInitialized && ::binding.isInitialized) {
            refreshCaptureStatus()
        }
    }

    override fun onStop() {
        if (statusReceiverRegistered) {
            unregisterReceiver(statusReceiver)
            statusReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        demoExecutor.shutdownNow()
        clearDemoPreview()
        super.onDestroy()
    }

    private fun readProfile(): VisualProfile = VisualProfile(
        condition = binding.conditionInput.text.toString(),
        spherePower = binding.sphereInput.text.toString().toFloatOrNull() ?: 0f,
        cylinderPower = binding.cylinderInput.text.toString().toFloatOrNull() ?: 0f,
        brightness = (binding.brightnessInput.progress - 100).toFloat(),
        contrast = (binding.contrastInput.progress / 100f).coerceIn(0.5f, 2.5f),
        clahe = binding.claheInput.isChecked,
    )

    private fun restoreProfile() {
        val profile = storage.load() ?: return
        binding.conditionInput.setText(profile.condition)
        binding.sphereInput.setText(profile.spherePower.toString())
        binding.cylinderInput.setText(profile.cylinderPower.toString())
        binding.brightnessInput.progress = (profile.brightness + 100).roundToInt().coerceIn(0, 200)
        binding.contrastInput.progress = (profile.contrast * 100).roundToInt().coerceIn(50, 200)
        binding.claheInput.isChecked = profile.clahe
    }

    private fun startEnhancement() {
        storage.save(readProfile())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            binding.actionMessage.text =
                "Allow notifications so you can see the ongoing capture and stop it safely."
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            binding.actionMessage.text = "Allow overlay access, return here, then tap Start screen enhancement again."
            openOverlaySettings()
            return
        }
        binding.actionMessage.text = "Android will ask you to approve screen capture."
        if (!AccessibilityGuard.isServiceEnabled(this)) {
            binding.actionMessage.text =
                "Enable VisionAssist foreground-app detection in Accessibility settings before starting."
            openAccessibilitySettings()
            return
        }
        if (!AccessibilityGuard.hasRecentHeartbeat(this)) {
            binding.actionMessage.text =
                "Foreground-app detection is enabled but not responding. Re-enable it in Accessibility settings before starting."
            openAccessibilitySettings()
            return
        }
        try {
            val manager = requireNotNull(
                getSystemService(android.media.projection.MediaProjectionManager::class.java),
            ) { "Android's screen-capture service is unavailable." }
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        } catch (exception: Exception) {
            showCaptureStatus(
                false,
                "Could not open Android's screen-capture prompt: ${exception.localizedMessage ?: "unexpected error"}",
            )
        }
    }

    private fun stopEnhancement() {
        binding.stopButton.isEnabled = false
        binding.actionMessage.text = "Stopping screen enhancement…"
        try {
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
        } catch (exception: Exception) {
            binding.stopButton.isEnabled = true
            binding.actionMessage.text =
                "Could not send the stop request: ${exception.localizedMessage ?: "unexpected service error"}"
        }
    }

    private fun runOfflineDemo() {
        val profile = readProfile()
        storage.save(profile)
        clearDemoPreview()
        binding.demoStatus.text = "Preparing a synthetic sample. No screen is being captured."
        binding.demoButton.isEnabled = false

        demoExecutor.execute {
            var source: Bitmap? = null
            var enhanced: Bitmap? = null
            try {
                check(OpenCVLoader.initLocal()) { "OpenCV could not initialize on this device." }
                source = createSyntheticDemoFrame()
                enhanced = OpenCvFrameProcessor().process(source, profile)
                val sourceResult = source
                val enhancedResult = enhanced
                source = null
                enhanced = null
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        sourceResult.recycle()
                        enhancedResult.recycle()
                        return@runOnUiThread
                    }
                    syntheticPreview = sourceResult
                    enhancedPreview = enhancedResult
                    binding.syntheticPreview.setImageBitmap(sourceResult)
                    binding.enhancedPreview.setImageBitmap(enhancedResult)
                    binding.demoPreviewGroup.visibility = android.view.View.VISIBLE
                    binding.demoStatus.text =
                        "Offline demo active: synthetic imagery only. Current brightness, contrast, and adaptive-contrast settings were applied; this is not live capture."
                    binding.demoButton.isEnabled = !storage.isCaptureActive()
                }
            } catch (exception: Exception) {
                source?.recycle()
                enhanced?.recycle()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    binding.demoStatus.text =
                        "Offline demo could not run: ${exception.localizedMessage ?: "unexpected image-processing error"}"
                    binding.demoButton.isEnabled = !storage.isCaptureActive()
                }
            }
        }
    }

    private fun clearDemoPreview() {
        binding.syntheticPreview.setImageDrawable(null)
        binding.enhancedPreview.setImageDrawable(null)
        binding.demoPreviewGroup.visibility = android.view.View.GONE
        syntheticPreview?.recycle()
        enhancedPreview?.recycle()
        syntheticPreview = null
        enhancedPreview = null
    }

    private fun createSyntheticDemoFrame(): Bitmap {
        val bitmap = Bitmap.createBitmap(DEMO_IMAGE_WIDTH, DEMO_IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(239, 243, 249))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(28, 47, 78) }
        canvas.drawRect(0f, 0f, DEMO_IMAGE_WIDTH.toFloat(), 82f, paint)
        paint.color = Color.WHITE
        paint.textSize = 30f
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        canvas.drawText("SYNTHETIC DEMO", 28f, 51f, paint)

        paint.color = Color.rgb(37, 86, 160)
        paint.textSize = 27f
        canvas.drawText("Visual settings preview", 32f, 132f, paint)
        paint.color = Color.rgb(73, 85, 103)
        paint.textSize = 19f
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        canvas.drawText("Generated sample content - not a captured screen", 32f, 165f, paint)

        val tones = intArrayOf(58, 91, 124, 157, 190, 223)
        val swatchWidth = 96f
        tones.forEachIndexed { index, tone ->
            paint.color = Color.rgb(tone, tone, tone)
            val left = 32f + index * (swatchWidth + 10f)
            canvas.drawRoundRect(RectF(left, 197f, left + swatchWidth, 258f), 8f, 8f, paint)
        }

        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(32f, 284f, 688f, 431f), 14f, 14f, paint)
        paint.color = Color.rgb(35, 74, 127)
        paint.textSize = 22f
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        canvas.drawText("Profile controls", 55f, 324f, paint)
        paint.color = Color.rgb(78, 91, 111)
        paint.textSize = 18f
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        canvas.drawText("Brightness  |  Contrast  |  Adaptive contrast", 55f, 361f, paint)
        paint.color = Color.rgb(222, 231, 245)
        canvas.drawRoundRect(RectF(55f, 382f, 650f, 399f), 8f, 8f, paint)
        paint.color = Color.rgb(48, 101, 173)
        canvas.drawRoundRect(RectF(55f, 382f, 392f, 399f), 8f, 8f, paint)
        return bitmap
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
        )
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun applySystemBarInsets() {
        WindowInsetsControllerCompat(window, binding.root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val padding = (20 * resources.displayMetrics.density).roundToInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                padding + insets.left,
                padding + insets.top,
                padding + insets.right,
                padding + insets.bottom,
            )
            windowInsets
        }
    }

    private fun refreshPermissionStatus() {
        val overlayGranted = Settings.canDrawOverlays(this)
        binding.overlayPermissionStatus.text = if (overlayGranted) {
            "Overlay access: Allowed"
        } else {
            "Overlay access: Needed to display the enhanced screen"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationsGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            binding.notificationPermissionStatus.text = if (notificationsGranted) {
                "Notifications: Allowed"
            } else {
                "Notifications: Required before capture so the Stop action is accessible"
            }
            binding.notificationPermissionButton.isEnabled = !notificationsGranted
            binding.notificationPermissionButton.text =
                if (notificationsGranted) "Notifications allowed" else "Allow notifications"
        } else {
            binding.notificationPermissionStatus.text = "Notifications: Runtime permission not required on this Android version"
            binding.notificationPermissionButton.isEnabled = false
            binding.notificationPermissionButton.text = "Notification permission not required"
        }

        val accessibilityEnabled = AccessibilityGuard.isServiceEnabled(this)
        val accessibilityResponding = accessibilityEnabled && AccessibilityGuard.hasRecentHeartbeat(this)
        binding.accessibilityStatus.text = when {
            accessibilityResponding ->
                "Foreground-app detection: Enabled and responding"
            accessibilityEnabled ->
                "Foreground-app detection: Enabled in Settings, but not responding"
            else ->
                "Foreground-app detection: Not enabled"
        }
        binding.accessibilitySettingsButton.text = if (accessibilityResponding) {
            "Review Accessibility settings"
        } else {
            "Enable foreground-app detection"
        }
    }

    private fun showCaptureStatus(running: Boolean, message: String) {
        if (::storage.isInitialized) storage.setCaptureStatus(running, message)
        binding.captureStatus.text = if (running && message.isNotBlank()) {
            message
        } else if (running) {
            "Screen enhancement is active."
        } else {
            "Not running"
        }
        binding.startButton.isEnabled = !running
        binding.stopButton.isEnabled = running
        if (running) binding.demoButton.isEnabled = false
        else if (syntheticPreview != null) binding.demoButton.isEnabled = true
        if (!running && message.isNotBlank()) binding.actionMessage.text = message
    }

    private fun refreshCaptureStatus() {
        if (storage.isCaptureActive() && !OverlayService.captureServiceActive) {
            storage.setCaptureStatus(
                false,
                "The previous capture session is no longer active. Start again when ready.",
            )
        }
        showCaptureStatus(storage.isCaptureActive(), storage.captureStatusMessage())
    }

    private companion object {
        const val DEMO_IMAGE_WIDTH = 720
        const val DEMO_IMAGE_HEIGHT = 460
    }
}

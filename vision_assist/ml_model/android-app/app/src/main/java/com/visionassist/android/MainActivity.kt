package com.visionassist.android

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: ProfileStorage
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
}

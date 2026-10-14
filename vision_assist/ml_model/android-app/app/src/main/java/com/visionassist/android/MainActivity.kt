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
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            showCaptureStatus(false, "Screen-capture consent was not granted. Enhancement did not start.")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshPermissionStatus()
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
        binding.notificationPermissionButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        binding.startButton.setOnClickListener { startEnhancement() }
        binding.stopButton.setOnClickListener { stopEnhancement() }
        refreshPermissionStatus()
        showCaptureStatus(storage.isCaptureActive(), "")
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
            showCaptureStatus(storage.isCaptureActive(), "")
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
        if (!Settings.canDrawOverlays(this)) {
            binding.actionMessage.text = "Allow overlay access, return here, then tap Start screen enhancement again."
            openOverlaySettings()
            return
        }
        binding.actionMessage.text = "Android will ask you to approve screen capture."
        val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun stopEnhancement() {
        startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
        binding.actionMessage.text = "Stopping screen enhancement…"
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
        )
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
                "Notifications: Not allowed; Android may limit visible service notifications"
            }
            binding.notificationPermissionButton.isEnabled = !notificationsGranted
            binding.notificationPermissionButton.text =
                if (notificationsGranted) "Notifications allowed" else "Allow notifications"
        } else {
            binding.notificationPermissionStatus.text = "Notifications: Runtime permission not required on this Android version"
            binding.notificationPermissionButton.isEnabled = false
            binding.notificationPermissionButton.text = "Notification permission not required"
        }
    }

    private fun showCaptureStatus(running: Boolean, message: String) {
        binding.captureStatus.text = if (running) {
            "Running — screen capture and overlay are active."
        } else {
            "Not running"
        }
        binding.startButton.isEnabled = !running
        binding.stopButton.isEnabled = running
        if (message.isNotBlank()) binding.actionMessage.text = message
    }
}

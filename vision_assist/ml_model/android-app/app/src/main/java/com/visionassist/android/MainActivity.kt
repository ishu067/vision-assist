package com.visionassist.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.visionassist.android.databinding.ActivityMainBinding
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: ProfileStorage
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, OverlayService::class.java).apply {
                putExtra(OverlayService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(OverlayService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "Overlay enabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        storage = ProfileStorage(this)
        restoreProfile()

        binding.saveButton.setOnClickListener {
            storage.save(readProfile())
            Toast.makeText(this, "Profile saved on this device", Toast.LENGTH_SHORT).show()
        }
        binding.overlayButton.setOnClickListener { enableOverlay() }
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

    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        storage.save(readProfile())
        val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }
}

package com.recallos

import android.os.Bundle
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.recallos.ingestion.ScreenshotMonitorService
import com.recallos.ui.RecallOsApp

class MainActivity : ComponentActivity() {
    private val photoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) ScreenshotMonitorService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startScreenshotMonitorIfAllowed()
        setContent {
            RecallOsApp()
        }
    }

    private fun startScreenshotMonitorIfAllowed() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ScreenshotMonitorService.start(this)
        } else {
            photoPermissionLauncher.launch(permission)
        }
    }
}

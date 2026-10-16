package com.sambhavdwivedi.callin.core.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.sambhavdwivedi.callin.core.network.TokenStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Requests every runtime permission CALLIN needs, one system dialog
 * at a time, the very first time the user reaches Home — mic,
 * notifications, camera, then the "ignore battery optimizations"
 * dialog. That last one is what stops manufacturer battery savers
 * (common on Xiaomi/Vivo/OnePlus/Oppo) from force-killing the app
 * process in the background, which would otherwise silently drop
 * incoming calls even when FCM itself is working correctly.
 *
 * Runs exactly once per install — tracked in TokenStore so even a
 * cold restart doesn't repeat it.
 */
@Composable
fun RequestAppPermissionsOnce(tokenStore: TokenStore) {
    val context = LocalContext.current
    val pendingResume = remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        pendingResume.value?.invoke()
        pendingResume.value = null
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        pendingResume.value?.invoke()
        pendingResume.value = null
    }

    LaunchedEffect(Unit) {
        if (tokenStore.getPermissionsRequested()) return@LaunchedEffect

        suspend fun requestPermission(permission: String) {
            suspendCancellableCoroutine<Unit> { cont ->
                pendingResume.value = { if (cont.isActive) cont.resume(Unit) }
                permissionLauncher.launch(permission)
            }
        }

        requestPermission(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermission(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermission(Manifest.permission.CAMERA)

        val powerManager = context.getSystemService(PowerManager::class.java)
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            suspendCancellableCoroutine<Unit> { cont ->
                pendingResume.value = { if (cont.isActive) cont.resume(Unit) }
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
                runCatching { batteryLauncher.launch(intent) }
                    .onFailure { cont.resume(Unit) }
            }
        }

        tokenStore.setPermissionsRequested(true)
    }
}

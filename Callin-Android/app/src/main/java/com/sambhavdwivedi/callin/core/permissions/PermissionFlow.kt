package com.sambhavdwivedi.callin.core.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.sambhavdwivedi.callin.core.network.TokenStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Requests every runtime permission CALLIN needs, one system dialog
 * at a time, the very first time the user reaches Home after
 * completing their profile — mic first (needed for any call at
 * all), then notifications (Android 13+, needed to alert about
 * incoming calls), then camera (needed only for QR scanning).
 *
 * Runs exactly once per install: the outcome (granted or denied) is
 * never re-asked automatically — tracked in TokenStore so even a
 * cold restart doesn't repeat it. If the user denies one, later
 * screens that actually need it (call, scan) prompt again through
 * Android's normal per-feature request flow, same as any other app.
 */
@Composable
fun RequestAppPermissionsOnce(tokenStore: TokenStore) {
    val pendingResume = remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        pendingResume.value?.invoke()
        pendingResume.value = null
    }

    LaunchedEffect(Unit) {
        if (tokenStore.getPermissionsRequested()) return@LaunchedEffect

        suspend fun requestOne(permission: String) {
            suspendCancellableCoroutine<Unit> { cont ->
                pendingResume.value = { if (cont.isActive) cont.resume(Unit) }
                launcher.launch(permission)
            }
        }

        requestOne(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestOne(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestOne(Manifest.permission.CAMERA)

        tokenStore.setPermissionsRequested(true)
    }
}

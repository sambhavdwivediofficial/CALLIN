package com.sambhavdwivedi.callin.core.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.sambhavdwivedi.callin.core.network.TokenStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private val REQUIRED_PERMISSIONS: List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    add(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private const val RECHECK_INTERVAL_MILLIS = 4L * 24 * 60 * 60 * 1000 // 4 days

/**
 * Every time Home appears, checks which of CALLIN's required
 * permissions are still missing. If any are missing AND it's been
 * 4+ days since the last check (or this is the very first check),
 * re-prompts — one system dialog at a time — for exactly the
 * missing ones. Once every permission is granted, this becomes a
 * no-op forever (no interval even needed to check: a fully-granted
 * state has nothing left to ask for). Call is denied → user is not
 * nagged every single app open, only every 4 days, matching what
 * Android itself does for its own permission re-prompt cadence.
 */
@Composable
fun RequestAppPermissionsOnce(tokenStore: TokenStore) {
    val context = LocalContext.current
    val pendingResume = remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        pendingResume.value?.invoke()
        pendingResume.value = null
    }

    LaunchedEffect(Unit) {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return@LaunchedEffect

        val lastCheck = tokenStore.getLastPermissionCheckMillis()
        val now = System.currentTimeMillis()
        if (lastCheck != 0L && now - lastCheck < RECHECK_INTERVAL_MILLIS) return@LaunchedEffect

        suspend fun requestOne(permission: String) {
            suspendCancellableCoroutine<Unit> { cont ->
                pendingResume.value = { if (cont.isActive) cont.resume(Unit) }
                launcher.launch(permission)
            }
        }

        for (permission in missing) {
            requestOne(permission)
        }

        tokenStore.setLastPermissionCheckMillis(now)
    }
}

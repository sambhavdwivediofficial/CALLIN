package com.sambhavdwivedi.callin.ui.call

import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sambhavdwivedi.callin.core.di.AppContainer
import com.sambhavdwivedi.callin.core.ringtone.RingtoneAssets
import com.sambhavdwivedi.callin.core.ringtone.RingtoneOption
import com.sambhavdwivedi.callin.ui.theme.CallinColors
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.coroutines.launch

@Composable
fun RingtonePickerSheet(container: AppContainer, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val options = remember { RingtoneAssets.list(context) }
    var selected by remember { mutableStateOf<String?>(null) }
    var playingFile by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(Unit) {
        selected = container.ringtoneStore.getSelected() ?: options.firstOrNull()?.fileName
    }

    fun stopPlayback() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        playingFile = null
    }

    DisposableEffect(Unit) { onDispose { stopPlayback() } }

    fun togglePlay(option: RingtoneOption) {
        if (playingFile == option.fileName) {
            stopPlayback()
            return
        }
        stopPlayback()
        player = runCatching {
            MediaPlayer().apply {
                val afd = context.assets.openFd(RingtoneAssets.assetPath(option.fileName))
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setAudioStreamType(AudioManager.STREAM_RING)
                setOnCompletionListener { playingFile = null }
                prepare()
                start()
            }
        }.getOrNull()
        playingFile = option.fileName
    }

    @OptIn(ExperimentalMaterial3Api::class)
    ModalBottomSheet(onDismissRequest = { stopPlayback(); onDismiss() }, containerColor = CallinColors.Background) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Ringtone", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose the sound that plays for incoming calls.",
                color = CallinColors.TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))

            if (options.isEmpty()) {
                Text("No ringtones found.", color = CallinColors.TextSecondary, fontSize = 14.sp)
            }

            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = option.fileName
                            scope.launch { container.ringtoneStore.setSelected(option.fileName) }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, CallinColors.TextSecondary, CircleShape)
                            .background(if (selected == option.fileName) Color(0xFF2478D4) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {}

                    Spacer(Modifier.width(14.dp))

                    Text(
                        text = option.label,
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = if (playingFile == option.fileName) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { togglePlay(option) }
                    )
                }
                Divider(color = CallinColors.TextSecondary.copy(alpha = 0.15f))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

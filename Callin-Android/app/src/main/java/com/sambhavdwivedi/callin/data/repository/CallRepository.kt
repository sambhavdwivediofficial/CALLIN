package com.sambhavdwivedi.callin.data.repository

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import com.sambhavdwivedi.callin.core.call.CallForegroundService
import com.sambhavdwivedi.callin.core.call.CallNotifier
import com.sambhavdwivedi.callin.core.network.SignalingClient
import com.sambhavdwivedi.callin.core.ringtone.RingtoneAssets
import com.sambhavdwivedi.callin.core.signaling.CallInvitePayload
import com.sambhavdwivedi.callin.core.signaling.IceCandidatePayload
import com.sambhavdwivedi.callin.core.signaling.SdpPayload
import com.sambhavdwivedi.callin.core.signaling.SignalingMessage
import com.sambhavdwivedi.callin.core.signaling.SignalingType
import com.sambhavdwivedi.callin.core.storage.CallDirection
import com.sambhavdwivedi.callin.core.storage.RecentCallEntry
import com.sambhavdwivedi.callin.core.storage.RecentCallsStore
import com.sambhavdwivedi.callin.core.storage.RingtoneStore
import com.sambhavdwivedi.callin.core.webrtc.IceServers
import com.sambhavdwivedi.callin.core.webrtc.WebRtcClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.UUID

data class CallPeerInfo(
    val callId: String,
    val peerId: String,
    val peerUsername: String,
    val peerDisplayName: String?,
    val peerAvatarUrl: String?,
)

sealed interface CallUiState {
    data object Idle : CallUiState
    data class Outgoing(val info: CallPeerInfo, val ringing: Boolean) : CallUiState
    data class Incoming(val info: CallPeerInfo) : CallUiState
    data class Active(val info: CallPeerInfo, val startedAtMillis: Long) : CallUiState
    data class Ended(val info: CallPeerInfo, val reason: String) : CallUiState
}

private const val RING_TIMEOUT_MS = 20_000L

class CallRepository(
    context: Context,
    private val signalingClient: SignalingClient,
    private val ringtoneStore: RingtoneStore,
    private val recentCallsStore: RecentCallsStore,
    private val myUserId: () -> String?,
    private val lookupPeer: (String) -> Triple<String, String?, String?>?,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true }
    private val notifier = CallNotifier(appContext)

    private val _state = MutableStateFlow<CallUiState>(CallUiState.Idle)
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private var webRtc: WebRtcClient? = null
    private var isCaller = false
    private var started = false
    private var attemptStartedAtMillis = 0L

    private var ringbackPlayer: MediaPlayer? = null
    private var ringtonePlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var ringTimeoutJob: Job? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            signalingClient.incoming.collect { msg -> handleMessage(msg) }
        }
    }

    private fun setState(newState: CallUiState) {
        _state.value = newState
        notifier.update(newState)
        updateSounds(newState)
        updateProximityLock(newState)
        manageRingTimeout(newState)
        updateForegroundService(newState)
    }

    // ---- keeps the process (and this whole call) alive through
    // screen lock / app backgrounding — see CallForegroundService ----
    private fun updateForegroundService(state: CallUiState) {
        val intent = Intent(appContext, CallForegroundService::class.java)
        if (state is CallUiState.Idle) {
            appContext.stopService(intent)
        } else {
            ContextCompat.startForegroundService(appContext, intent)
        }
    }

    private fun manageRingTimeout(state: CallUiState) {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = when (state) {
            is CallUiState.Outgoing, is CallUiState.Incoming -> scope.launch {
                delay(RING_TIMEOUT_MS)
                when (val current = _state.value) {
                    is CallUiState.Outgoing -> cancelCall()
                    is CallUiState.Incoming -> { recordMissedIncoming(current.info); rejectCall() }
                    else -> Unit
                }
            }
            else -> null
        }
    }

    private fun updateSounds(state: CallUiState) {
        when (state) {
            is CallUiState.Outgoing -> { stopIncomingAlert(); if (state.ringing) startRingback() else stopRingback() }
            is CallUiState.Incoming -> { stopRingback(); startIncomingAlert() }
            else -> { stopRingback(); stopIncomingAlert() }
        }
    }

    private fun startRingback() {
        if (ringbackPlayer != null) return
        ringbackPlayer = runCatching {
            MediaPlayer().apply {
                val afd = appContext.assets.openFd("ringback.mp3")
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setAudioStreamType(AudioManager.STREAM_VOICE_CALL)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    private fun stopRingback() {
        ringbackPlayer?.let { runCatching { it.stop() }; runCatching { it.release() } }
        ringbackPlayer = null
    }

    private fun startIncomingAlert() {
        if (ringtonePlayer != null) return
        scope.launch {
            val fileName = ringtoneStore.getSelected() ?: RingtoneAssets.list(appContext).firstOrNull()?.fileName
            ringtonePlayer = runCatching {
                MediaPlayer().apply {
                    if (fileName != null) {
                        val afd = appContext.assets.openFd(RingtoneAssets.assetPath(fileName))
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                    }
                    setAudioStreamType(AudioManager.STREAM_RING)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
        }
        vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
    }

    private fun stopIncomingAlert() {
        ringtonePlayer?.let { runCatching { it.stop() }; runCatching { it.release() } }
        ringtonePlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun updateProximityLock(state: CallUiState) {
        if (state is CallUiState.Active) {
            if (proximityWakeLock == null) {
                val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                    @Suppress("DEPRECATION")
                    proximityWakeLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "callin:proximity")
                    proximityWakeLock?.acquire()
                }
            }
        } else {
            proximityWakeLock?.let { if (it.isHeld) it.release() }
            proximityWakeLock = null
        }
    }

    private fun handleMessage(msg: SignalingMessage) {
        val myId = myUserId()
        when (msg.type) {
            SignalingType.CALL_INVITE -> if (myId != null) onInvite(msg, myId)
            SignalingType.CALL_ACCEPT -> onAccept(msg)
            SignalingType.CALL_REJECT -> onTerminal("declined")
            SignalingType.CALL_CANCEL -> onTerminal("cancelled")
            SignalingType.CALL_END -> onTerminal("ended")
            SignalingType.WEBRTC_OFFER -> onOffer(msg)
            SignalingType.WEBRTC_ANSWER -> onAnswer(msg)
            SignalingType.WEBRTC_CANDIDATE -> onRemoteCandidate(msg)
            else -> Unit
        }
    }

    private fun onInvite(msg: SignalingMessage, myId: String) {
        val callId = msg.callId ?: return
        if (msg.from == myId) {
            val current = _state.value
            if (current is CallUiState.Outgoing) setState(current.copy(info = current.info.copy(callId = callId), ringing = true))
            return
        }
        if (_state.value !is CallUiState.Idle) return
        val callerId = msg.from ?: return
        val peer = lookupPeer(callerId)
        attemptStartedAtMillis = System.currentTimeMillis()
        setState(
            CallUiState.Incoming(
                CallPeerInfo(callId, callerId, peer?.first ?: "Unknown", peer?.second, peer?.third)
            )
        )
    }

    private fun onAccept(msg: SignalingMessage) {
        val info = currentInfo() ?: return
        val startedAt = if (msg.timestamp > 0) msg.timestamp else System.currentTimeMillis()
        if (isCaller) {
            val client = ensureWebRtc()
            client.start(IceServers.defaults())
            client.setSpeakerOn(_isSpeakerOn.value)
            client.createOffer { sdp -> sendSdp(SignalingType.WEBRTC_OFFER, info.peerId, sdp) }
        }
        setState(CallUiState.Active(info, startedAt))
    }

    private fun onTerminal(reason: String) {
        val info = currentInfo() ?: return
        recordCallEnd(info, reason)
        cleanupWebRtc()
        setState(CallUiState.Ended(info, reason))
    }

    private fun onOffer(msg: SignalingMessage) {
        val current = _state.value
        if (current !is CallUiState.Active) return
        val payload = msg.payload ?: return
        val sdpPayload = runCatching { json.decodeFromJsonElement(SdpPayload.serializer(), payload) }.getOrNull() ?: return
        val client = ensureWebRtc()
        client.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, sdpPayload.sdp))
        client.createAnswer { sdp -> sendSdp(SignalingType.WEBRTC_ANSWER, current.info.peerId, sdp) }
    }

    private fun onAnswer(msg: SignalingMessage) {
        val payload = msg.payload ?: return
        val sdpPayload = runCatching { json.decodeFromJsonElement(SdpPayload.serializer(), payload) }.getOrNull() ?: return
        webRtc?.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, sdpPayload.sdp))
    }

    private fun onRemoteCandidate(msg: SignalingMessage) {
        val payload = msg.payload ?: return
        val c = runCatching { json.decodeFromJsonElement(IceCandidatePayload.serializer(), payload) }.getOrNull() ?: return
        webRtc?.addRemoteIceCandidate(IceCandidate(c.sdpMid, c.sdpMLineIndex, c.candidate))
    }

    fun startCall(peerId: String, peerUsername: String, peerDisplayName: String?, peerAvatarUrl: String?) {
        if (_state.value !is CallUiState.Idle) return
        isCaller = true
        attemptStartedAtMillis = System.currentTimeMillis()
        setState(CallUiState.Outgoing(CallPeerInfo(UUID.randomUUID().toString(), peerId, peerUsername, peerDisplayName, peerAvatarUrl), ringing = false))
        val payload = json.encodeToJsonElement(CallInvitePayload.serializer(), CallInvitePayload(peerId))
        signalingClient.send(SignalingMessage(type = SignalingType.CALL_INVITE, to = peerId, payload = payload))
    }

    fun cancelCall() {
        val info = (_state.value as? CallUiState.Outgoing)?.info ?: return
        signalingClient.send(SignalingMessage(type = SignalingType.CALL_CANCEL, callId = info.callId, to = info.peerId))
        recordCallEnd(info, "cancelled")
        cleanupWebRtc()
        setState(CallUiState.Idle)
    }

    fun acceptCall() {
        val info = (_state.value as? CallUiState.Incoming)?.info ?: return
        isCaller = false
        val client = ensureWebRtc()
        client.start(IceServers.defaults())
        client.setSpeakerOn(_isSpeakerOn.value)
        signalingClient.send(SignalingMessage(type = SignalingType.CALL_ACCEPT, callId = info.callId, to = info.peerId))
    }

    fun rejectCall() {
        val info = (_state.value as? CallUiState.Incoming)?.info ?: return
        signalingClient.send(SignalingMessage(type = SignalingType.CALL_REJECT, callId = info.callId, to = info.peerId))
        setState(CallUiState.Idle)
    }

    fun endCall() {
        val info = currentInfo() ?: return
        signalingClient.send(SignalingMessage(type = SignalingType.CALL_END, callId = info.callId, to = info.peerId))
        recordCallEnd(info, "ended")
        cleanupWebRtc()
        setState(CallUiState.Idle)
    }

    fun dismissEnded() { if (_state.value is CallUiState.Ended) setState(CallUiState.Idle) }

    fun toggleMute() {
        val next = !_isMuted.value
        webRtc?.setMuted(next)
        _isMuted.value = next
    }

    fun toggleSpeaker() {
        val next = !_isSpeakerOn.value
        webRtc?.setSpeakerOn(next)
        _isSpeakerOn.value = next
    }

    private fun recordCallEnd(info: CallPeerInfo, reason: String) {
        val current = _state.value
        val wasActive = current is CallUiState.Active
        val durationSeconds = if (wasActive) ((System.currentTimeMillis() - (current as CallUiState.Active).startedAtMillis) / 1000).coerceAtLeast(0) else 0L
        scope.launch {
            recentCallsStore.record(
                RecentCallEntry(
                    peerId = info.peerId, peerUsername = info.peerUsername, peerDisplayName = info.peerDisplayName,
                    peerAvatarUrl = info.peerAvatarUrl,
                    direction = if (isCaller) CallDirection.OUTGOING else CallDirection.INCOMING,
                    timestampMillis = if (attemptStartedAtMillis > 0) attemptStartedAtMillis else System.currentTimeMillis(),
                    durationSeconds = durationSeconds, missed = !wasActive,
                )
            )
        }
    }

    private fun recordMissedIncoming(info: CallPeerInfo) {
        scope.launch {
            recentCallsStore.record(
                RecentCallEntry(
                    peerId = info.peerId, peerUsername = info.peerUsername, peerDisplayName = info.peerDisplayName,
                    peerAvatarUrl = info.peerAvatarUrl, direction = CallDirection.INCOMING,
                    timestampMillis = if (attemptStartedAtMillis > 0) attemptStartedAtMillis else System.currentTimeMillis(),
                    durationSeconds = 0, missed = true,
                )
            )
        }
    }

    private fun currentInfo(): CallPeerInfo? = when (val s = _state.value) {
        is CallUiState.Outgoing -> s.info
        is CallUiState.Incoming -> s.info
        is CallUiState.Active -> s.info
        else -> null
    }

    private fun ensureWebRtc(): WebRtcClient {
        return webRtc ?: WebRtcClient(
            context = appContext,
            onLocalIceCandidate = { candidate ->
                val info = currentInfo() ?: return@WebRtcClient
                val payload = json.encodeToJsonElement(IceCandidatePayload.serializer(), IceCandidatePayload(candidate.sdp, candidate.sdpMid ?: "", candidate.sdpMLineIndex))
                signalingClient.send(SignalingMessage(type = SignalingType.WEBRTC_CANDIDATE, to = info.peerId, payload = payload))
            },
            onIceStateChanged = { },
        ).also { webRtc = it }
    }

    private fun sendSdp(type: String, to: String, sdp: SessionDescription) {
        val payload = json.encodeToJsonElement(SdpPayload.serializer(), SdpPayload(sdp.description, sdp.type.canonicalForm()))
        signalingClient.send(SignalingMessage(type = type, to = to, payload = payload))
    }

    private fun cleanupWebRtc() {
        stopRingback()
        stopIncomingAlert()
        proximityWakeLock?.let { if (it.isHeld) it.release() }
        proximityWakeLock = null
        webRtc?.release()
        webRtc = null
        _isMuted.value = false
        _isSpeakerOn.value = false
        isCaller = false
        attemptStartedAtMillis = 0L
    }
}

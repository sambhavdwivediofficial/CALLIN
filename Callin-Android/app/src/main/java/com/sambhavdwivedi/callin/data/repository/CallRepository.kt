package com.sambhavdwivedi.callin.data.repository

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import com.sambhavdwivedi.callin.core.network.SignalingClient
import com.sambhavdwivedi.callin.core.signaling.CallInvitePayload
import com.sambhavdwivedi.callin.core.signaling.IceCandidatePayload
import com.sambhavdwivedi.callin.core.signaling.SdpPayload
import com.sambhavdwivedi.callin.core.signaling.SignalingMessage
import com.sambhavdwivedi.callin.core.signaling.SignalingType
import com.sambhavdwivedi.callin.core.webrtc.IceServers
import com.sambhavdwivedi.callin.core.webrtc.WebRtcClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

class CallRepository(
    context: Context,
    private val signalingClient: SignalingClient,
    private val myUserId: () -> String?,
    private val lookupPeer: (String) -> Triple<String, String?, String?>?,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<CallUiState>(CallUiState.Idle)
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private var webRtc: WebRtcClient? = null
    private var isCaller = false
    private var started = false

    // ---- ringback (caller hears "trrr...trrr") and incoming
    // ringtone + vibration (callee) ----
    private var ringbackTone: ToneGenerator? = null
    private var ringtonePlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            signalingClient.incoming.collect { msg -> handleMessage(msg) }
        }
    }

    private fun setState(newState: CallUiState) {
        _state.value = newState
        updateSounds(newState)
    }

    private fun updateSounds(state: CallUiState) {
        when (state) {
            is CallUiState.Outgoing -> {
                stopIncomingAlert()
                if (state.ringing) startRingback() else stopRingback()
            }
            is CallUiState.Incoming -> {
                stopRingback()
                startIncomingAlert()
            }
            else -> {
                stopRingback()
                stopIncomingAlert()
            }
        }
    }

    private fun startRingback() {
        if (ringbackTone != null) return
        ringbackTone = runCatching {
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80).also {
                it.startTone(ToneGenerator.TONE_SUP_RINGTONE, -1) // -1 = plays continuously until stopped
            }
        }.getOrNull()
    }

    private fun stopRingback() {
        ringbackTone?.let {
            runCatching { it.stopTone() }
            runCatching { it.release() }
        }
        ringbackTone = null
    }

    private fun startIncomingAlert() {
        if (ringtonePlayer != null) return
        val uri = RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtonePlayer = runCatching {
            MediaPlayer().apply {
                setDataSource(appContext, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()

        vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val pattern = longArrayOf(0, 800, 600)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun stopIncomingAlert() {
        ringtonePlayer?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        ringtonePlayer = null
        vibrator?.cancel()
        vibrator = null
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
            if (current is CallUiState.Outgoing) {
                setState(current.copy(info = current.info.copy(callId = callId), ringing = true))
            }
            return
        }

        if (_state.value !is CallUiState.Idle) return
        val callerId = msg.from ?: return
        val peer = lookupPeer(callerId)
        setState(
            CallUiState.Incoming(
                CallPeerInfo(
                    callId = callId,
                    peerId = callerId,
                    peerUsername = peer?.first ?: "Unknown",
                    peerDisplayName = peer?.second,
                    peerAvatarUrl = peer?.third,
                )
            )
        )
    }

    private fun onAccept(msg: SignalingMessage) {
        val info = currentInfo() ?: return
        val startedAt = if (msg.timestamp > 0) msg.timestamp else System.currentTimeMillis()

        if (isCaller) {
            val client = ensureWebRtc()
            client.start(IceServers.defaults())
            client.createOffer { sdp -> sendSdp(SignalingType.WEBRTC_OFFER, info.peerId, sdp) }
        }
        setState(CallUiState.Active(info, startedAt))
    }

    private fun onTerminal(reason: String) {
        val info = currentInfo() ?: return
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
        setState(
            CallUiState.Outgoing(
                CallPeerInfo(UUID.randomUUID().toString(), peerId, peerUsername, peerDisplayName, peerAvatarUrl),
                ringing = false
            )
        )
        val payload = json.encodeToJsonElement(CallInvitePayload.serializer(), CallInvitePayload(peerId))
        signalingClient.send(SignalingMessage(type = SignalingType.CALL_INVITE, to = peerId, payload = payload))
    }

    fun cancelCall() {
        val info = (_state.value as? CallUiState.Outgoing)?.info ?: return
        signalingClient.send(SignalingMessage(type = SignalingType.CALL_CANCEL, callId = info.callId, to = info.peerId))
        cleanupWebRtc()
        setState(CallUiState.Idle)
    }

    fun acceptCall() {
        val info = (_state.value as? CallUiState.Incoming)?.info ?: return
        isCaller = false
        ensureWebRtc().start(IceServers.defaults())
        signalingClient.send(SignalingMessage(type = SignalingType.CALL_ACCEPT, callId = info.callId, to = info.peerId))
        // Moves to Active once the (now correctly echoed) call.accept comes back — see onAccept().
    }

    fun rejectCall() {
        val info = (_state.value as? CallUiState.Incoming)?.info ?: return
        signalingClient.send(SignalingMessage(type = SignalingType.CALL_REJECT, callId = info.callId, to = info.peerId))
        setState(CallUiState.Idle)
    }

    fun endCall() {
        val info = currentInfo() ?: return
        signalingClient.send(SignalingMessage(type = SignalingType.CALL_END, callId = info.callId, to = info.peerId))
        cleanupWebRtc()
        setState(CallUiState.Idle)
    }

    fun dismissEnded() {
        if (_state.value is CallUiState.Ended) setState(CallUiState.Idle)
    }

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
                val payload = json.encodeToJsonElement(
                    IceCandidatePayload.serializer(),
                    IceCandidatePayload(candidate.sdp, candidate.sdpMid ?: "", candidate.sdpMLineIndex)
                )
                signalingClient.send(SignalingMessage(type = SignalingType.WEBRTC_CANDIDATE, to = info.peerId, payload = payload))
            },
            onIceStateChanged = { /* hook for a "reconnecting" indicator later */ },
        ).also { webRtc = it }
    }

    private fun sendSdp(type: String, to: String, sdp: SessionDescription) {
        val payload = json.encodeToJsonElement(SdpPayload.serializer(), SdpPayload(sdp.description, sdp.type.canonicalForm()))
        signalingClient.send(SignalingMessage(type = type, to = to, payload = payload))
    }

    private fun cleanupWebRtc() {
        stopRingback()
        stopIncomingAlert()
        webRtc?.release()
        webRtc = null
        _isMuted.value = false
        _isSpeakerOn.value = true
        isCaller = false
    }
}

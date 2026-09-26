package com.sambhavdwivedi.callin.data.repository

import android.content.Context
import com.sambhavdwivedi.callin.core.network.SignalingClient
import com.sambhavdwivedi.callin.core.signaling.CallInvitePayload
import com.sambhavdwivedi.callin.core.signaling.IceCandidatePayload
import com.sambhavdwivedi.callin.core.signaling.SdpPayload
import com.sambhavdwivedi.callin.core.signaling.SignalingMessage
import com.sambhavdwivedi.callin.core.signaling.SignalingType
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

/**
 * Owns the whole call lifecycle: every call.* / webrtc.* signaling
 * message and the WebRtcClient it drives, boiled down to one
 * [state] the UI renders from. One instance for the app process
 * (like SignalingClient) — started once at login, so an incoming
 * call is caught no matter which screen is on top.
 *
 * The app only ever tracks one call at a time (matching the
 * backend's "one call at a time" rule), so messages are applied to
 * whatever the current call is rather than being matched by call_id.
 */
class CallRepository(
    context: Context,
    private val signalingClient: SignalingClient,
    private val myUserId: () -> String?,
    private val lookupPeer: (String) -> Triple<String, String?, String?>?, // username, displayName, avatarUrl
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

    fun start() {
        if (started) return
        started = true
        scope.launch {
            signalingClient.incoming.collect { msg -> handleMessage(msg) }
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
            // Echo of our own invite — the call now has its real
            // server-assigned ID, and we know it reached the server.
            val current = _state.value
            if (current is CallUiState.Outgoing) {
                _state.value = current.copy(info = current.info.copy(callId = callId), ringing = true)
            }
            return
        }

        // Someone else calling us.
        if (_state.value !is CallUiState.Idle) return // already on a call — backend already rejects this, just guard locally too
        val callerId = msg.from ?: return
        val peer = lookupPeer(callerId)
        _state.value = CallUiState.Incoming(
            CallPeerInfo(
                callId = callId,
                peerId = callerId,
                peerUsername = peer?.first ?: "Unknown",
                peerDisplayName = peer?.second,
                peerAvatarUrl = peer?.third,
            )
        )
    }

    private fun onAccept(msg: SignalingMessage) {
        val info = currentInfo() ?: return
        val startedAt = if (msg.timestamp > 0) msg.timestamp else System.currentTimeMillis()

        if (isCaller) {
            val client = ensureWebRtc()
            client.start()
            client.createOffer { sdp -> sendSdp(SignalingType.WEBRTC_OFFER, info.peerId, sdp) }
        }
        _state.value = CallUiState.Active(info, startedAt)
    }

    private fun onTerminal(reason: String) {
        val info = currentInfo() ?: return
        cleanupWebRtc()
        _state.value = CallUiState.Ended(info, reason)
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

    // ---- Outgoing ----

    fun startCall(peerId: String, peerUsername: String, peerDisplayName: String?, peerAvatarUrl: String?) {
        if (_state.value !is CallUiState.Idle) return
        isCaller = true
        _state.value = CallUiState.Outgoing(
            CallPeerInfo(UUID.randomUUID().toString(), peerId, peerUsername, peerDisplayName, peerAvatarUrl),
            ringing = false
        )
        val payload = json.encodeToJsonElement(CallInvitePayload.serializer(), CallInvitePayload(peerId))
        signalingClient.send(SignalingMessage(type = SignalingType.CALL_INVITE, to = peerId, payload = payload))
    }

    fun cancelCall() {
        val info = (_state.value as? CallUiState.Outgoing)?.info ?: return
        signalingClient.send(SignalingMessage(type = SignalingType.CALL_CANCEL, callId = info.callId, to = info.peerId))
        cleanupWebRtc()
        _state.value = CallUiState.Idle
    }

    // ---- Incoming ----

    fun acceptCall() {
        val info = (_state.value as? CallUiState.Incoming)?.info ?: return
        isCaller = false
        ensureWebRtc().start()
        signalingClient.send(SignalingMessage(type = SignalingType.CALL_ACCEPT, callId = info.callId, to = info.peerId))
        // Moves to Active once the (echoed) call.accept arrives back — see onAccept().
    }

    fun rejectCall() {
        val info = (_state.value as? CallUiState.Incoming)?.info ?: return
        signalingClient.send(SignalingMessage(type = SignalingType.CALL_REJECT, callId = info.callId, to = info.peerId))
        _state.value = CallUiState.Idle
    }

    // ---- Active ----

    fun endCall() {
        val info = currentInfo() ?: return
        signalingClient.send(SignalingMessage(type = SignalingType.CALL_END, callId = info.callId, to = info.peerId))
        cleanupWebRtc()
        _state.value = CallUiState.Idle
    }

    fun dismissEnded() {
        if (_state.value is CallUiState.Ended) _state.value = CallUiState.Idle
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

    // ---- helpers ----

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
            onIceConnectionChange = { /* hook for a "reconnecting" indicator later */ },
        ).also { webRtc = it }
    }

    private fun sendSdp(type: String, to: String, sdp: SessionDescription) {
        val payload = json.encodeToJsonElement(SdpPayload.serializer(), SdpPayload(sdp.description, sdp.type.canonicalForm()))
        signalingClient.send(SignalingMessage(type = type, to = to, payload = payload))
    }

    private fun cleanupWebRtc() {
        webRtc?.release()
        webRtc = null
        _isMuted.value = false
        _isSpeakerOn.value = true
        isCaller = false
    }
}

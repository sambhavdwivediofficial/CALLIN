package com.sambhavdwivedi.callin.core.webrtc

import android.content.Context
import android.media.AudioManager
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Thin wrapper around one audio-only WebRTC PeerConnection for the
 * lifetime of a single call. A fresh instance is created per call by
 * CallRepository and released the moment the call ends.
 *
 * [start] takes the ICE server list from the outside (see
 * [IceServers]) so STUN and TURN fallback can be swapped without
 * touching this class.
 */
class WebRtcClient(
    context: Context,
    private val onLocalIceCandidate: (IceCandidate) -> Unit,
    private val onIceStateChanged: (PeerConnection.IceConnectionState) -> Unit,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null

    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false

    init {
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(appContext)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val audioDeviceModule = JavaAudioDeviceModule.builder(appContext).createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }

    fun start(iceServers: List<PeerConnection.IceServer>) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, PeerConnectionObserverImpl())

        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("callin_audio", audioSource)
        peerConnection?.addTrack(localAudioTrack, listOf("callin_stream"))
    }

    fun createOffer(onCreated: (SessionDescription) -> Unit) {
        peerConnection?.createOffer(CreateSdpObserver { sdp ->
            peerConnection?.setLocalDescription(SetSdpObserver {}, sdp)
            onCreated(sdp)
        }, MediaConstraints())
    }

    fun createAnswer(onCreated: (SessionDescription) -> Unit) {
        peerConnection?.createAnswer(CreateSdpObserver { sdp ->
            peerConnection?.setLocalDescription(SetSdpObserver {}, sdp)
            onCreated(sdp)
        }, MediaConstraints())
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(
            SetSdpObserver {
                remoteDescriptionSet = true
                pendingRemoteCandidates.forEach { peerConnection?.addIceCandidate(it) }
                pendingRemoteCandidates.clear()
            },
            sdp
        )
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        if (remoteDescriptionSet) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            pendingRemoteCandidates.add(candidate)
        }
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun setSpeakerOn(on: Boolean) {
        audioManager.isSpeakerphoneOn = on
    }

    fun release() {
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        runCatching { localAudioTrack?.dispose() }
        peerConnection = null
        localAudioTrack = null
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    private inner class PeerConnectionObserverImpl : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate != null) onLocalIceCandidate(candidate)
        }
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            if (state != null) onIceStateChanged(state)
        }
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
    }
}

private class CreateSdpObserver(private val onSuccess: (SessionDescription) -> Unit) : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {
        if (sdp != null) onSuccess(sdp)
    }
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}

private class SetSdpObserver(private val onSuccess: () -> Unit) : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {}
    override fun onSetSuccess() {
        onSuccess()
    }
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}

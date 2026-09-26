package com.sambhavdwivedi.callin.core.webrtc

import android.content.Context
import android.media.AudioManager
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.EglBase
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
 * CallRepository and released the moment the call ends — nothing
 * here is reused across calls.
 *
 * Only a public STUN server is configured (Google's). This is
 * enough for most home/mobile networks where at least one side has
 * a directly reachable NAT mapping; calls between two devices both
 * behind symmetric/carrier-grade NAT will fail to connect audio
 * until a TURN server is added later (Callin-Go can host coturn).
 */
class WebRtcClient(
    context: Context,
    private val onLocalIceCandidate: (IceCandidate) -> Unit,
    private val onIceConnectionChange: (PeerConnection.IceConnectionState) -> Unit,
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

    /** Opens the mic and creates the PeerConnection. Call once, right when the call is about to actually happen. */
    fun start() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) = onLocalIceCandidate(candidate)
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = onIceConnectionChange(state)
            override fun onSignalingChange(p0: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })

        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("callin_audio", audioSource)
        peerConnection?.addTrack(localAudioTrack, listOf("callin_stream"))
    }

    fun createOffer(onCreated: (SessionDescription) -> Unit) {
        peerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                onCreated(sdp)
            }
        }, MediaConstraints())
    }

    fun createAnswer(onCreated: (SessionDescription) -> Unit) {
        peerConnection?.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                onCreated(sdp)
            }
        }, MediaConstraints())
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                pendingRemoteCandidates.forEach { peerConnection?.addIceCandidate(it) }
                pendingRemoteCandidates.clear()
            }
        }, sdp)
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        if (remoteDescriptionSet) peerConnection?.addIceCandidate(candidate)
        else pendingRemoteCandidates.add(candidate)
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
}

private open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}



//package com.sambhavdwivedi.callin.core.webrtc
//
//import android.content.Context
//import android.media.AudioManager
//import org.webrtc.AudioTrack
//import org.webrtc.DataChannel
//import org.webrtc.IceCandidate
//import org.webrtc.MediaConstraints
//import org.webrtc.MediaStream
//import org.webrtc.PeerConnection
//import org.webrtc.PeerConnectionFactory
//import org.webrtc.RtpReceiver
//import org.webrtc.SdpObserver
//import org.webrtc.SessionDescription
//import org.webrtc.audio.JavaAudioDeviceModule
//
///**
// * Thin wrapper around one audio-only WebRTC PeerConnection for the
// * lifetime of a single call. A fresh instance is created per call by
// * CallRepository and released the moment the call ends — nothing
// * here is reused across calls.
// *
// * Only a public STUN server is configured (Google's). This is
// * enough for most home/mobile networks where at least one side has
// * a directly reachable NAT mapping; calls between two devices both
// * behind symmetric/carrier-grade NAT will fail to connect audio
// * until a TURN server is added later (Callin-Go can host coturn).
// */
//class WebRtcClient(
//    context: Context,
//    private val onLocalIceCandidate: (IceCandidate) -> Unit,
//    private val onIceConnectionChange: (PeerConnection.IceConnectionState) -> Unit,
//) {
//    private val appContext = context.applicationContext
//    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
//
//    private val peerConnectionFactory: PeerConnectionFactory
//    private var peerConnection: PeerConnection? = null
//    private var localAudioTrack: AudioTrack? = null
//
//    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
//    private var remoteDescriptionSet = false
//
//    init {
//        val initOptions = PeerConnectionFactory.InitializationOptions.builder(appContext)
//            .setEnableInternalTracer(false)
//            .createInitializationOptions()
//        PeerConnectionFactory.initialize(initOptions)
//
//        val audioDeviceModule = JavaAudioDeviceModule.builder(appContext).createAudioDeviceModule()
//
//        peerConnectionFactory = PeerConnectionFactory.builder()
//            .setAudioDeviceModule(audioDeviceModule)
//            .createPeerConnectionFactory()
//    }
//
//    /** Opens the mic and creates the PeerConnection. Call once, right when the call is about to actually happen. */
//    fun start() {
//        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
//        audioManager.isSpeakerphoneOn = true
//
//        val iceServers = listOf(
//            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
//        )
//        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
//            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
//        }
//
//        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
//            override fun onIceCandidate(candidate: IceCandidate): Unit = onLocalIceCandidate(candidate)
//
//            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState): Unit =
//                this@WebRtcClient.onIceConnectionChange(state)
//
//            override fun onSignalingChange(p0: PeerConnection.SignalingState) {}
//            override fun onIceConnectionReceivingChange(p0: Boolean) {}
//            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState) {}
//            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>) {}
//            override fun onAddStream(p0: MediaStream?) {}
//            override fun onRemoveStream(p0: MediaStream?) {}
//            override fun onDataChannel(p0: DataChannel?) {}
//            override fun onRenegotiationNeeded() {}
//            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
//        })
//
//        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
//        localAudioTrack = peerConnectionFactory.createAudioTrack("callin_audio", audioSource)
//        peerConnection?.addTrack(localAudioTrack, listOf("callin_stream"))
//    }
//
//    fun createOffer(onCreated: (SessionDescription) -> Unit) {
//        peerConnection?.createOffer(object : SdpObserverAdapter() {
//            override fun onCreateSuccess(p0: SessionDescription?) {
//                val sdp = p0 ?: return
//                peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
//                onCreated(sdp)
//            }
//        }, MediaConstraints())
//    }
//
//    fun createAnswer(onCreated: (SessionDescription) -> Unit) {
//        peerConnection?.createAnswer(object : SdpObserverAdapter() {
//            override fun onCreateSuccess(p0: SessionDescription?) {
//                val sdp = p0 ?: return
//                peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
//                onCreated(sdp)
//            }
//        }, MediaConstraints())
//    }
//
//    fun setRemoteDescription(sdp: SessionDescription) {
//        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
//            override fun onSetSuccess() {
//                remoteDescriptionSet = true
//                pendingRemoteCandidates.forEach { peerConnection?.addIceCandidate(it) }
//                pendingRemoteCandidates.clear()
//            }
//        }, sdp)
//    }
//
//    fun addRemoteIceCandidate(candidate: IceCandidate) {
//        if (remoteDescriptionSet) peerConnection?.addIceCandidate(candidate)
//        else pendingRemoteCandidates.add(candidate)
//    }
//
//    fun setMuted(muted: Boolean) {
//        localAudioTrack?.setEnabled(!muted)
//    }
//
//    fun setSpeakerOn(on: Boolean) {
//        audioManager.isSpeakerphoneOn = on
//    }
//
//    fun release() {
//        runCatching { peerConnection?.close() }
//        runCatching { peerConnection?.dispose() }
//        runCatching { localAudioTrack?.dispose() }
//        peerConnection = null
//        localAudioTrack = null
//        audioManager.mode = AudioManager.MODE_NORMAL
//        audioManager.isSpeakerphoneOn = false
//    }
//}
//
//private open class SdpObserverAdapter : SdpObserver {
//    override fun onCreateSuccess(p0: SessionDescription?) {}
//    override fun onSetSuccess() {}
//    override fun onCreateFailure(p0: String?) {}
//    override fun onSetFailure(p0: String?) {}
//}

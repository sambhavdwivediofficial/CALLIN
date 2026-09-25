package com.sambhavdwivedi.callin.core.signaling

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Mirrors the backend's signaling.Message envelope exactly
 * (Callin-Go/internal/signaling/message.go). Payload stays as a raw
 * JsonElement so each message type can carry its own shape without
 * this envelope changing — decoded further by whoever handles that
 * specific type.
 */
@Serializable
data class SignalingMessage(
    val type: String,
    @SerialName("call_id") val callId: String? = null,
    val from: String? = null,
    val to: String? = null,
    val payload: JsonElement? = null,
    val timestamp: Long = 0
)

object SignalingType {
    const val CALL_INVITE = "call.invite"
    const val CALL_ACCEPT = "call.accept"
    const val CALL_REJECT = "call.reject"
    const val CALL_CANCEL = "call.cancel"
    const val CALL_END = "call.end"
    const val WEBRTC_OFFER = "webrtc.offer"
    const val WEBRTC_ANSWER = "webrtc.answer"
    const val WEBRTC_CANDIDATE = "webrtc.ice_candidate"
    const val PING = "ping"
    const val PONG = "pong"
    const val ERROR = "error"
}

@Serializable
data class CallInvitePayload(@SerialName("callee_id") val calleeId: String)

@Serializable
data class SdpPayload(val sdp: String, val type: String) // "offer" | "answer"

@Serializable
data class IceCandidatePayload(
    val candidate: String,
    @SerialName("sdp_mid") val sdpMid: String,
    @SerialName("sdp_mline_index") val sdpMLineIndex: Int
)

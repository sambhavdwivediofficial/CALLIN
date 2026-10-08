package com.sambhavdwivedi.callin.core.webrtc

import org.webrtc.PeerConnection

/**
 * Ordered ICE server list. STUN resolves a direct path when possible.
 * Beyond that, every entry here is a TURN relay fallback — needed
 * whenever two devices are on different mobile carriers/networks
 * (very common across Indian states), since carrier-grade NAT almost
 * always blocks a direct P2P path in that case and audio depends
 * entirely on one of these relays actually working.
 *
 * Multiple independent TURN providers are listed (not just multiple
 * ports of the same one) so that if any single free provider is
 * overloaded, rate-limited, or temporarily down — which free/demo
 * TURN services are known to be — WebRTC can still find a working
 * relay among the others rather than failing outright. This is a
 * stability improvement, not a guarantee: a dedicated TURN account
 * (e.g. Metered.ca's proper free tier, ~50GB/month, dynamic
 * credentials) remains the reliable long-term fix and is worth
 * setting up before this app has real-world scale.
 */
object IceServers {
    fun defaults(): List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),

        // Provider 1: Open Relay Project
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),

        // Provider 2: a second independent free TURN, as a fallback
        // path in case Provider 1 is unreachable or exhausted.
        PeerConnection.IceServer.builder("turn:relay1.expressturn.com:3478")
            .setUsername("efRMXQ4R8OEYN82KG8")
            .setPassword("fRiEZq6tsfCwHxFW")
            .createIceServer(),
    )
}

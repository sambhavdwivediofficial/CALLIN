package com.sambhavdwivedi.callin.core.webrtc

import org.webrtc.PeerConnection

/**
 * Ordered ICE server list used for every call. Google's public STUN
 * server resolves a direct path when possible (fastest, lowest
 * latency — audio goes device-to-device). The Open Relay Project
 * TURN servers are a free, publicly shared relay used automatically
 * as a fallback whenever a direct/STUN path can't be found — both
 * devices behind carrier-grade NAT, restrictive mobile-data
 * firewalls, different networks entirely, etc. This is exactly the
 * "server relays the call" behavior that was asked for, done the
 * standard WebRTC way (TURN) instead of a hand-rolled relay: WebRTC
 * itself decides per-call whether a direct path or the relay is
 * used, with no code difference on either end.
 *
 * This public relay is fine for now but is shared with everyone
 * else using the same free credentials, so it can be slow or
 * rate-limited under real load. Once usage grows, replace this list
 * with a self-hosted coturn server on a small VPS with a public IP —
 * same shape, just your own `turn:` URL + username + credential.
 */
object IceServers {
    fun defaults(): List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
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
    )
}

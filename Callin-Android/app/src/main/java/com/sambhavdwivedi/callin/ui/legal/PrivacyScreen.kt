package com.sambhavdwivedi.callin.ui.legal

import androidx.compose.runtime.Composable

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    LegalScaffold(title = "Privacy Policy", onBack = onBack) {
        LegalSection("1. What we collect", "Your email and name from Google Sign-In, the username and display name you choose, and basic call metadata (who called whom, when, and for how long) — never the audio content of your calls.")
        LegalSection("2. How we use it", "To identify you to other CALLIN users, connect your calls, show your call history, and send incoming-call notifications when the app is in the background.")
        LegalSection("3. What we don't do", "We don't sell your data, and we don't listen to or record your calls. Audio is relayed only to establish your connection, never stored.")
        LegalSection("4. Sharing", "Your username, display name, and avatar are visible to other CALLIN users you call or who call you. Your email is never shown to other users.")
        LegalSection("5. Your choices", "You can request account deletion at any time, which removes your profile and call history from our systems.")
        LegalSection("6. Contact", "Questions about this policy can be sent to the app's support contact once CALLIN is publicly released.")
    }
}

package com.sambhavdwivedi.callin.ui.legal

import androidx.compose.runtime.Composable

@Composable
fun TermsScreen(onBack: () -> Unit) {
    LegalScaffold(title = "Terms of Service", onBack = onBack) {
        LegalSection("1. Using CALLIN", "CALLIN lets you make and receive voice calls with other CALLIN users over the internet. You must be at least 13 years old to create an account.")
        LegalSection("2. Your account", "You're responsible for the activity on your account and for keeping your sign-in credentials secure. Your username, once set, cannot be changed.")
        LegalSection("3. Acceptable use", "You agree not to use CALLIN to harass, abuse, or harm others, to send spam, or to attempt to disrupt the service for other users.")
        LegalSection("4. Calls and data", "Calls are transmitted directly between devices whenever possible, using WebRTC. CALLIN's servers coordinate call setup and, where needed, relay audio to help calls connect.")
        LegalSection("5. Availability", "CALLIN is provided \"as is\" while in active development. Features and availability may change without notice.")
        LegalSection("6. Changes to these terms", "We may update these terms as CALLIN evolves. Continued use of the app means you accept the current version.")
    }
}

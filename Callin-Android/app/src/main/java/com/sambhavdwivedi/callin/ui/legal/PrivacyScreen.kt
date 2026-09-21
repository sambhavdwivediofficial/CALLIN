//package com.sambhavdwivedi.callin.ui.legal
//
//import androidx.compose.runtime.Composable
//
//@Composable
//fun PrivacyScreen(onBack: () -> Unit) {
//    LegalScaffold(title = "Privacy Policy", onBack = onBack) {
//        LegalSection("1. What we collect", "Your email and name from Google Sign-In, the username and display name you choose, and basic call metadata (who called whom, when, and for how long) — never the audio content of your calls.")
//        LegalSection("2. How we use it", "To identify you to other CALLIN users, connect your calls, show your call history, and send incoming-call notifications when the app is in the background.")
//        LegalSection("3. What we don't do", "We don't sell your data, and we don't listen to or record your calls. Audio is relayed only to establish your connection, never stored.")
//        LegalSection("4. Sharing", "Your username, display name, and avatar are visible to other CALLIN users you call or who call you. Your email is never shown to other users.")
//        LegalSection("5. Your choices", "You can request account deletion at any time, which removes your profile and call history from our systems.")
//        LegalSection("6. Contact", "Questions about this policy can be sent to the app's support contact once CALLIN is publicly released.")
//    }
//}
package com.sambhavdwivedi.callin.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sambhavdwivedi.callin.ui.theme.CallinColors
import androidx.compose.runtime.remember

// ─────────────────────────────────────────────────────────────
// Privacy Policy
// ─────────────────────────────────────────────────────────────

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    LegalScaffold(title = "Privacy Policy", onBack = onBack) {
        LegalDocument(
            heroIcon = Icons.Outlined.Security,
            heroTitle = "Your privacy, by design",
            heroSubtitle = "CALLIN is built for voice calls and nothing else. This policy explains, in plain language, " +
                    "what information we handle, why we handle it, and the control you have over it.",
            highlightsTitle = "Privacy at a glance",
            highlights = privacyHighlights,
            sections = privacySections,
        )
    }
}

private val privacyHighlights = listOf(
    "We never record, store, or listen to the audio of your calls.",
    "We never sell your personal data.",
    "Your email address is never shown to other users.",
    "CALLIN does not ask for your phone number or read your contact book.",
    "You can ask us to delete your account and data at any time.",
)

private val privacySections = listOf(
    PolicySectionData(
        icon = Icons.Outlined.Info,
        title = "Introduction and scope",
        intro = "CALLIN (“CALLIN”, “we”, “us”) is an internet-based voice calling application developed and operated by " +
                "Sambhav Dwivedi. This Privacy Policy explains how we collect, use, share, and protect information when " +
                "you use the CALLIN Android app and the backend services that power it (together, the “Service”).",
        outro = "By creating an account or using the Service, you confirm that you have read and understood this policy. " +
                "If you do not agree with it, please do not use CALLIN.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.Person,
        title = "Information you provide",
        intro = "We collect only what is needed to create your account and let other people reach you.",
        points = listOf(
            PolicyPoint(
                "Google account details",
                "When you sign in with Google, we receive your verified email address and a unique Google account " +
                        "identifier. We never receive or store your Google password."
            ),
            PolicyPoint(
                "Profile details",
                "Your first name, last name, and the username you choose. Your display name is created from your " +
                        "first and last name."
            ),
            PolicyPoint(
                "Profile photo",
                "An optional picture you upload. It is stored with our cloud storage provider and delivered through " +
                        "a link so that it can be shown to other CALLIN users."
            ),
            PolicyPoint(
                "Messages to us",
                "Anything you send when you contact us for support or to make a privacy request."
            ),
        ),
    ),
    PolicySectionData(
        icon = Icons.Outlined.DataUsage,
        title = "Information created when you use CALLIN",
        points = listOf(
            PolicyPoint(
                "Call records",
                "Who called whom, when a call started, connected, and ended, how it ended (for example completed, " +
                        "declined, missed, or cancelled), and how long it lasted. Never the audio."
            ),
            PolicyPoint(
                "Notification and device data",
                "A push-notification token issued by Firebase Cloud Messaging, your device platform, and when your " +
                        "device was last active, so we can alert you to incoming calls while the app is in the background."
            ),
            PolicyPoint(
                "Sign-in session data",
                "Secure sign-in tokens, kept in private app storage on your device so that you stay signed in."
            ),
            PolicyPoint(
                "Call-setup messages",
                "Technical messages such as session descriptions and network candidates pass through our signaling " +
                        "server in real time to connect two devices. They are used only for that purpose and are not stored."
            ),
            PolicyPoint(
                "Technical and security logs",
                "Standard server logs (request time, endpoint, response status, and IP address) that help us keep the " +
                        "Service reliable, investigate problems, and defend against abuse, including per-IP rate limiting."
            ),
        ),
    ),
    PolicySectionData(
        icon = Icons.Outlined.Block,
        title = "What we do not collect",
        intro = "Good privacy is as much about what we leave out as what we keep.",
        points = listOf(
            PolicyPoint("Call audio", "CALLIN never records, transcribes, or stores what you say."),
            PolicyPoint(
                "Your phone number",
                "Accounts are built on your Google account and username, not on your phone number."
            ),
            PolicyPoint("Your contact book", "CALLIN does not read your phone’s address book."),
            PolicyPoint("Your location", "We do not collect GPS or precise location data."),
            PolicyPoint("Payment details", "CALLIN does not collect or process payment card information."),
        ),
    ),
    PolicySectionData(
        icon = Icons.Outlined.Key,
        title = "Permissions on your device",
        intro = "CALLIN asks for a permission only when a feature needs it. You can review or revoke any permission " +
                "in your Android system settings at any time.",
        points = listOf(
            PolicyPoint(
                "Microphone",
                "Required so the other person can hear you. Audio is captured only while a call is active."
            ),
            PolicyPoint(
                "Notifications",
                "Lets CALLIN alert you to incoming calls and call events, including when the app is closed."
            ),
            PolicyPoint(
                "Nearby devices (Bluetooth)",
                "Used to route call audio to your headset or car kit."
            ),
            PolicyPoint(
                "Camera",
                "Used only if you choose to scan a QR code inside the app."
            ),
            PolicyPoint(
                "Photos",
                "Choosing a profile photo uses Android’s system photo picker, which gives CALLIN access only to the " +
                        "single image you select."
            ),
            PolicyPoint("Network access", "Required to make and receive internet calls."),
        ),
    ),
    PolicySectionData(
        icon = Icons.Outlined.PhoneInTalk,
        title = "How calls work and how they are secured",
        intro = "CALLIN uses WebRTC, an open industry standard for real-time communication. Our servers coordinate " +
                "calls, but they are not part of the audio path whenever a direct connection is possible.",
        points = listOf(
            PolicyPoint(
                "Call setup",
                "Setup messages travel over an authenticated connection to our server, which passes them to the " +
                        "person you are calling."
            ),
            PolicyPoint(
                "Direct connection",
                "Where possible, audio travels directly between the two devices."
            ),
            PolicyPoint(
                "Relay fallback",
                "When a direct path is not available, for example on some mobile or corporate networks, audio is " +
                        "forwarded through a relay server that only passes along encrypted packets."
            ),
            PolicyPoint(
                "Encryption",
                "Call audio is encrypted in transit using the standard WebRTC security protocols (DTLS-SRTP)."
            ),
            PolicyPoint("No recording", "CALLIN does not record calls."),
        ),
        note = "IP address visibility: to open a direct connection, WebRTC exchanges network addresses between the two " +
                "devices. This means the person you are speaking with may be able to see your IP address during a call. " +
                "This is inherent to peer-to-peer calling, so only call people you trust.",
        outro = "Please remember that other people, or other apps and devices, may be able to record what they hear. " +
                "Laws about recording calls vary by country.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.Sync,
        title = "How we use your information",
        intro = "We use your information only to run and protect CALLIN. Specifically, to:",
        points = listOf(
            PolicyPoint(text = "Create and secure your account and sign you in."),
            PolicyPoint(text = "Connect your calls and alert you to incoming calls, including when the app is in the background."),
            PolicyPoint(text = "Show your call history and present your profile to other users."),
            PolicyPoint(text = "Prevent spam, fraud, abuse, and security incidents."),
            PolicyPoint(text = "Maintain, troubleshoot, and improve the reliability and performance of the Service."),
            PolicyPoint(text = "Respond to your requests and send essential service notices."),
            PolicyPoint(text = "Comply with our legal obligations."),
        ),
        outro = "We do not sell your personal data, and we do not use it to build advertising profiles.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.Gavel,
        title = "Legal bases for processing",
        intro = "Where privacy laws require a legal basis for using your data, we rely on the following:",
        points = listOf(
            PolicyPoint("Contract", "Using your data is necessary to provide the Service you signed up for."),
            PolicyPoint(
                "Legitimate interests",
                "Keeping CALLIN secure, preventing abuse, and maintaining a reliable service."
            ),
            PolicyPoint(
                "Consent",
                "For optional device permissions such as microphone and notifications, which you can withdraw at any time."
            ),
            PolicyPoint("Legal obligation", "When we must retain or disclose data to comply with the law."),
        ),
        outro = "In India, we also rely on your consent and on the “legitimate uses” recognised by the Digital Personal " +
                "Data Protection Act, 2023.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.Share,
        title = "How we share information",
        intro = "We do not sell your personal data. We share information only in the ways described here.",
        points = listOf(
            PolicyPoint(
                "Other CALLIN users",
                "Your username, display name, and profile photo are visible to other CALLIN users, for example in the " +
                        "contact directory, on call screens, and in call history. Your email address is never shown to other users."
            ),
            PolicyPoint(
                "Service providers",
                "Trusted companies that help us operate CALLIN and are bound to protect your data: Google (sign-in and " +
                        "Firebase Cloud Messaging for push notifications), Supabase (profile-photo storage), and our cloud " +
                        "hosting and database infrastructure."
            ),
            PolicyPoint(
                "Legal and safety",
                "When required by law or legal process, or to protect the rights, safety, and security of our users, " +
                        "the public, or the Service."
            ),
            PolicyPoint(
                "Business changes",
                "If CALLIN is ever merged, acquired, or transferred, your data will remain protected under this policy " +
                        "and we will tell you about any material change."
            ),
            PolicyPoint("With your consent", "In any other case, only when you ask us to."),
        ),
        outro = "Third-party services have their own privacy policies, which govern how they handle your data.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.Timer,
        title = "Data retention",
        intro = "We keep information only for as long as it is needed for the purposes described in this policy.",
        points = listOf(
            PolicyPoint("Account and profile data", "Kept while your account is active."),
            PolicyPoint("Call history", "Kept until you delete your account."),
            PolicyPoint(
                "Notification tokens",
                "Kept while a device is registered, and removed when you delete your account."
            ),
            PolicyPoint(
                "Server logs",
                "Kept for a limited period needed for security and troubleshooting, then deleted or anonymised."
            ),
            PolicyPoint(
                "Backups",
                "Deleted data may remain in secure backups for a short period until they are overwritten."
            ),
            PolicyPoint("Call audio and call-setup messages", "Never stored."),
        ),
    ),
    PolicySectionData(
        icon = Icons.Outlined.ManageAccounts,
        title = "Your rights and choices",
        intro = "Depending on where you live, you may have rights over your personal data. We aim to honour them for " +
                "every CALLIN user.",
        points = listOf(
            PolicyPoint("Access and portability", "Ask for a copy of the personal data we hold about you."),
            PolicyPoint(
                "Correction",
                "Ask us to fix inaccurate data. Some details, such as your username, are permanent by design."
            ),
            PolicyPoint(
                "Deletion",
                "Ask us to delete your account. This removes your profile, photo, notification tokens, and call history " +
                        "from our systems, subject to backup cycles and legal requirements."
            ),
            PolicyPoint(
                "Consent and permissions",
                "Withdraw any permission in Android settings. Some features may stop working without it."
            ),
            PolicyPoint(
                "Complaints",
                "You may raise a concern with us at any time, and you have the right to complain to your local " +
                        "data-protection authority."
            ),
        ),
        outro = "We may need to verify your identity before acting on a request, and we will respond within a reasonable " +
                "time in line with applicable law.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.Lock,
        title = "Security",
        intro = "We use layered safeguards to protect your information:",
        points = listOf(
            PolicyPoint(
                "Encryption in transit",
                "Connections between the app and our servers use TLS, and call audio is protected with DTLS-SRTP."
            ),
            PolicyPoint(
                "Trusted sign-in",
                "Sign-in is handled by Google, so CALLIN never sees your Google password."
            ),
            PolicyPoint(
                "Short-lived access",
                "Access tokens are short-lived and every protected request is authenticated."
            ),
            PolicyPoint("Abuse protection", "Rate limiting and input validation help guard against misuse."),
            PolicyPoint(
                "Data minimisation",
                "The most sensitive part of a call, the audio, is never collected in the first place."
            ),
        ),
        note = "No system is perfectly secure. If you think your account has been compromised, sign out and contact us " +
                "straight away. If a data breach affects you, we will notify you and the relevant authorities as " +
                "required by law.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.Public,
        title = "International data processing",
        intro = "Our infrastructure and service providers may process information in countries other than your own. " +
                "When that happens, we take reasonable steps to make sure your information receives protection " +
                "consistent with this policy and applicable law.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.ChildCare,
        title = "Children’s privacy",
        intro = "CALLIN is not directed at children under 13, and we do not knowingly collect personal data from them. " +
                "Where the law of your country sets a higher age for consenting to data processing, you must meet that " +
                "age or have the permission of a parent or guardian.",
        outro = "If you believe a child has created an account, please contact us and we will delete it.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.Update,
        title = "Changes to this policy",
        intro = "We may update this policy as CALLIN evolves. When we do, we will change the “Updated” date at the top " +
                "of this page, and for material changes we will give reasonable notice in the app before they take effect.",
        outro = "Continuing to use CALLIN after an update means you accept the revised policy, where permitted by law.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.SupportAgent,
        title = "Contact and grievances",
        intro = "For privacy questions, data requests, or complaints, contact the developer, Sambhav Dwivedi, through the " +
                "website linked at the bottom of this page. Please include the username on your account so we can find it.",
        note = "For your safety, never share passwords, and avoid posting personal information in public places such as " +
                "open issue trackers.",
    ),
)

// ─────────────────────────────────────────────────────────────
// Shared building blocks (also used by TermsScreen)
// ─────────────────────────────────────────────────────────────

internal const val LEGAL_UPDATED_LABEL = "21 Sep 2026"

private const val DEVELOPER_NAME = "Sambhav Dwivedi"
private const val DEVELOPER_SITE = "https://www.sambhavdwivedi.in"
private const val DEVELOPER_SITE_LABEL = "www.sambhavdwivedi.in"
private const val REPO_URL = "https://github.com/sambhavdwivediofficial/CALLIN"
private const val REPO_LABEL = "github.com/sambhavdwivediofficial/CALLIN"

/** One bullet in a legal section. [lead] is an optional bold label in front of the sentence. */
internal data class PolicyPoint(
    val lead: String? = null,
    val text: String,
)

/** One numbered section of a legal document. */
internal data class PolicySectionData(
    val icon: ImageVector,
    val title: String,
    val intro: String? = null,
    val points: List<PolicyPoint> = emptyList(),
    val note: String? = null,
    val outro: String? = null,
)

/**
 * Renders a complete legal page inside [LegalScaffold]: hero, highlights card,
 * numbered sections, and the developer footer. Uses only CallinColors tokens.
 */
@Composable
internal fun LegalDocument(
    heroIcon: ImageVector,
    heroTitle: String,
    heroSubtitle: String,
    highlightsTitle: String,
    highlights: List<String>,
    sections: List<PolicySectionData>,
) {
    LegalHero(icon = heroIcon, title = heroTitle, subtitle = heroSubtitle)

    Spacer(Modifier.height(24.dp))

    LegalHighlights(title = highlightsTitle, items = highlights)

    sections.forEachIndexed { index, section ->
        LegalDivider()
        PolicySection(number = index + 1, data = section)
    }

    LegalDivider()

    DeveloperFooter()
}

@Composable
private fun LegalHero(icon: ImageVector, title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        IconBadge(
            icon = icon,
            size = 56.dp,
            iconSize = 28.dp,
            tint = CallinColors.TextPrimary
        )

        Spacer(Modifier.height(18.dp))

        Text(
            text = title,
            color = CallinColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 32.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = subtitle,
            color = CallinColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaChip("Updated $LEGAL_UPDATED_LABEL")
            MetaChip("CALLIN for Android")
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(
                width = 1.dp,
                color = CallinColors.TextSecondary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = CallinColors.TextSecondary,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun LegalHighlights(title: String, items: List<String>) {
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CallinColors.TextSecondary.copy(alpha = 0.06f))
            .border(1.dp, CallinColors.TextSecondary.copy(alpha = 0.22f), shape)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = CallinColors.TextPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = CallinColors.TextSecondary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = item,
                    color = CallinColors.TextPrimary.copy(alpha = 0.92f),
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PolicySection(number: Int, data: PolicySectionData) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = data.icon,
                size = 38.dp,
                iconSize = 19.dp,
                tint = CallinColors.TextPrimary
            )

            Spacer(Modifier.width(14.dp))

            Text(
                text = "$number. ${data.title}",
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 23.sp,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
        }

        data.intro?.let {
            Spacer(Modifier.height(12.dp))
            BodyText(it)
        }

        if (data.points.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            data.points.forEach { point -> PolicyBullet(point) }
        }

        data.note?.let {
            Spacer(Modifier.height(12.dp))
            PolicyNote(it)
        }

        data.outro?.let {
            Spacer(Modifier.height(12.dp))
            BodyText(it)
        }
    }
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        color = CallinColors.TextSecondary,
        fontSize = 14.sp,
        lineHeight = 22.sp
    )
}

@Composable
private fun PolicyBullet(point: PolicyPoint) {
    val lead = point.lead

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 9.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(CallinColors.TextSecondary)
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = buildAnnotatedString {
                if (lead != null) {
                    withStyle(
                        SpanStyle(
                            color = CallinColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(lead)
                    }
                    append(" – ")
                }
                append(point.text)
            },
            color = CallinColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PolicyNote(text: String) {
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CallinColors.TextSecondary.copy(alpha = 0.07f))
            .border(1.dp, CallinColors.TextSecondary.copy(alpha = 0.25f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = CallinColors.TextPrimary,
            modifier = Modifier.size(18.dp)
        )

        Spacer(Modifier.width(10.dp))

        Text(
            text = text,
            color = CallinColors.TextPrimary.copy(alpha = 0.9f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun IconBadge(
    icon: ImageVector,
    size: Dp,
    iconSize: Dp,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(CallinColors.TextSecondary.copy(alpha = 0.08f))
            .border(1.dp, CallinColors.TextSecondary.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun LegalDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 22.dp),
        thickness = 1.dp,
        color = CallinColors.TextSecondary.copy(alpha = 0.14f)
    )
}

// ─────────────────────────────────────────────────────────────
// Developer footer: website + GitHub, both open in the browser
// ─────────────────────────────────────────────────────────────

@Composable
private fun DeveloperFooter() {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
    ) {
        FooterLinkRow(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Language,
                    contentDescription = null,
                    tint = CallinColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = buildAnnotatedString {
                withStyle(SpanStyle(color = CallinColors.TextSecondary)) {
                    append("Developer ")
                }
                withStyle(
                    SpanStyle(
                        color = CallinColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append(DEVELOPER_NAME)
                }
            },
            subtitle = DEVELOPER_SITE_LABEL,
            onClickLabel = "Open the developer website",
            onClick = { uriHandler.openUri(DEVELOPER_SITE) }
        )

        Spacer(Modifier.height(10.dp))

        FooterLinkRow(
            icon = {
                Icon(
                    imageVector = GithubIcon,
                    contentDescription = null,
                    tint = CallinColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = CallinColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append("CALLIN on GitHub")
                }
            },
            subtitle = REPO_LABEL,
            onClickLabel = "Open the CALLIN GitHub repository",
            onClick = { uriHandler.openUri(REPO_URL) }
        )

        Spacer(Modifier.height(22.dp))

        val currentYear = remember { java.time.Year.now().value }

        Text(
            text = "© $currentYear $DEVELOPER_NAME. All rights reserved.",
            color = CallinColors.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FooterLinkRow(
    icon: @Composable () -> Unit,
    title: AnnotatedString,
    subtitle: String,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, CallinColors.TextSecondary.copy(alpha = 0.25f), shape)
            .clickable(
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = CallinColors.TextPrimary,
                fontSize = 15.sp
            )
            Text(
                text = subtitle,
                color = CallinColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = CallinColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * GitHub mark (Octicons "mark-github", 16px grid) as an ImageVector, so no
 * drawable resource is needed. Tinted by the Icon that displays it.
 */
private const val GITHUB_MARK_PATH =
    "M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49" +
            "-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82" +
            ".72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15" +
            "-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82" +
            ".44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2" +
            " 0 .21.15.46.55.38A8.013 8.013 0 0 0 16 8c0-4.42-3.58-8-8-8z"

private val GithubIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Github",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).addPath(
        pathData = addPathNodes(GITHUB_MARK_PATH),
        fill = SolidColor(Color.White)
    ).build()
}

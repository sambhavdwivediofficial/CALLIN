package com.sambhavdwivedi.callin.ui.legal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable

@Composable
fun TermsScreen(onBack: () -> Unit) {
    LegalScaffold(title = "Terms of Service", onBack = onBack) {
        LegalDocument(
            heroIcon = Icons.Outlined.Description,
            heroTitle = "Clear terms for a clear service",
            heroSubtitle = "These Terms set out the rules for using CALLIN. They are written to be readable, fair, " +
                    "and to keep the Service safe for everyone who uses it.",
            highlightsTitle = "Terms at a glance",
            highlights = termsHighlights,
            sections = termsSections,
        )
    }
}

private val termsHighlights = listOf(
    "CALLIN is for voice calls between CALLIN users over the internet.",
    "Be respectful: no harassment, spam, or abuse.",
    "CALLIN is not a phone line and cannot place emergency calls.",
    "Your username is permanent once you set it.",
    "You can stop using CALLIN and request deletion of your account at any time.",
)

private val termsSections = listOf(
    PolicySectionData(
        icon = Icons.Outlined.Description,
        title = "Agreement to these Terms",
        intro = "These Terms of Service (“Terms”) are a binding agreement between you and Sambhav Dwivedi, the developer " +
                "and operator of CALLIN (“we”, “us”). By creating an account or using the CALLIN app and related " +
                "services (the “Service”), you agree to these Terms and to our Privacy Policy.",
        outro = "If you do not agree, please do not use CALLIN. If you use the Service on behalf of an organisation, you " +
                "confirm that you have the authority to bind it to these Terms.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.Call,
        title = "About the Service",
        intro = "CALLIN lets registered users make and receive voice calls with other CALLIN users over the internet, " +
                "using Wi-Fi or mobile data. The Service includes the Android app, the servers that coordinate calls " +
                "and notifications, and related features such as your profile, contacts, and call history.",
        points = listOf(
            PolicyPoint(
                "Voice only",
                "CALLIN supports voice calls. It does not offer video calling or messaging."
            ),
            PolicyPoint(
                "CALLIN users only",
                "CALLIN does not connect to the traditional telephone network. You can only call other CALLIN users."
            ),
        ),
    ),
    PolicySectionData(
        icon = Icons.Outlined.VerifiedUser,
        title = "Eligibility",
        intro = "To use CALLIN, you must:",
        points = listOf(
            PolicyPoint(
                text = "Be at least 13 years old, or older if the law where you live requires a higher age to enter " +
                        "into this agreement."
            ),
            PolicyPoint(text = "If you are a minor in your country, have the permission of a parent or guardian."),
            PolicyPoint(
                text = "Have the legal capacity to accept these Terms, and not have been previously suspended or " +
                        "removed from the Service."
            ),
            PolicyPoint(text = "Provide accurate information when you register."),
        ),
    ),
    PolicySectionData(
        icon = Icons.Outlined.Person,
        title = "Your account",
        points = listOf(
            PolicyPoint(
                "Sign-in",
                "You sign in with your Google account. You are responsible for keeping it secure and for all activity " +
                        "that happens under your CALLIN account."
            ),
            PolicyPoint(
                "Username",
                "Your username is permanent once set and cannot be changed, so choose carefully. It must be 3–18 " +
                        "characters: lowercase letters, numbers, underscores, and hyphens."
            ),
            PolicyPoint(
                "Names and photo",
                "Your first name, last name, and photo must be yours to use, must not impersonate anyone, and must " +
                        "not be unlawful, offensive, or infringing."
            ),
            PolicyPoint(
                "Security",
                "Tell us promptly if you suspect unauthorised use of your account."
            ),
        ),
        outro = "We may remove or change usernames, names, or photos that break these Terms.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.PhoneInTalk,
        title = "Making and receiving calls",
        points = listOf(
            PolicyPoint(
                "Ringing",
                "A call rings on the recipient’s device, and they are free to accept, decline, or ignore it."
            ),
            PolicyPoint(
                "One call at a time",
                "A user can be on only one call at once. If someone is busy, your call may not connect."
            ),
            PolicyPoint(
                "Network dependence",
                "Call quality depends on your device, your network, and the other person’s connection. CALLIN works to " +
                        "keep calls stable and can reconnect when your network changes, for example between Wi-Fi and " +
                        "mobile data, but we cannot guarantee an uninterrupted call."
            ),
            PolicyPoint(
                "Data charges",
                "Calls use internet data. Your mobile carrier or internet provider may charge you for it."
            ),
            PolicyPoint(
                "Background alerts",
                "To be reachable when the app is closed, CALLIN needs notification and related permissions. " +
                        "Battery-saver and app-sleep settings on some devices can delay or block alerts."
            ),
            PolicyPoint(
                "Audio routing",
                "Depending on your device, you can switch between earpiece, speaker, and connected Bluetooth devices " +
                        "during a call."
            ),
        ),
    ),
    PolicySectionData(
        icon = Icons.Outlined.Warning,
        title = "Emergency calls",
        intro = "CALLIN is not a replacement for a mobile or landline telephone service.",
        note = "CALLIN cannot place calls to emergency services such as 112, 100, 101, 102, or 108 (India), 911 (US), " +
                "or 999 (UK). Never rely on CALLIN in an emergency. Always use a phone service that supports emergency calling.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.Block,
        title = "Acceptable use",
        intro = "You agree to use CALLIN lawfully and respectfully. You must not:",
        points = listOf(
            PolicyPoint(
                text = "Harass, threaten, stalk, intimidate, or abuse anyone, including by repeatedly calling someone " +
                        "who does not want to hear from you."
            ),
            PolicyPoint(text = "Send spam, make robocalls or bulk unsolicited calls, or run scams or phishing attempts."),
            PolicyPoint(
                text = "Impersonate another person or organisation, or misrepresent your identity or affiliation."
            ),
            PolicyPoint(
                text = "Use the Service for anything unlawful, fraudulent, defamatory, hateful, or sexually " +
                        "exploitative, or in any way that harms minors."
            ),
            PolicyPoint(
                text = "Record a call, or share a recording of one, without the consent required by applicable law."
            ),
            PolicyPoint(
                text = "Disrupt or overload the Service, bypass rate limits or security measures, or probe for " +
                        "vulnerabilities without our written permission."
            ),
            PolicyPoint(
                text = "Access another person’s account, or use bots, scrapers, or other automated means to collect " +
                        "user information or the contact directory."
            ),
            PolicyPoint(
                text = "Copy, reverse engineer, decompile, or create derivative works from the Service, except where " +
                        "the law allows it."
            ),
            PolicyPoint(text = "Infringe anyone’s privacy or intellectual-property rights."),
        ),
        outro = "If you see abuse, tell us through the contact details below and include the username involved.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.PrivacyTip,
        title = "Privacy and your data",
        intro = "Our Privacy Policy explains what we collect, how we use it, and the choices you have. It forms part of " +
                "these Terms. In short:",
        points = listOf(
            PolicyPoint(
                "Calls are not recorded",
                "CALLIN never records, stores, or listens to your call audio."
            ),
            PolicyPoint(
                "How calls travel",
                "Calls are sent directly between devices whenever possible using WebRTC, and are encrypted in transit. " +
                        "Our servers coordinate setup and, where needed, relay encrypted audio to help calls connect."
            ),
            PolicyPoint(
                "IP address",
                "During a direct call, the other participant’s device may be able to see your IP address. Only call " +
                        "people you trust."
            ),
            PolicyPoint(
                "Your profile",
                "Your username, display name, and photo are visible to other CALLIN users."
            ),
        ),
    ),
    PolicySectionData(
        icon = Icons.Outlined.Copyright,
        title = "Ownership and licence",
        intro = "CALLIN, including its name, logo, design, source code, and content, is owned by Sambhav Dwivedi and is " +
                "protected by copyright and other laws. © ${java.time.Year.now().value} Sambhav Dwivedi. All rights reserved.",
        points = listOf(
            PolicyPoint(
                "Your licence",
                "We grant you a limited, personal, non-exclusive, non-transferable, revocable licence to install and " +
                        "use the CALLIN app for its intended purpose, in line with these Terms."
            ),
            PolicyPoint(
                "Restrictions",
                "No other rights are granted. You may not copy, modify, distribute, sublicense, sell, or create " +
                        "derivative works from CALLIN without our prior written permission."
            ),
            PolicyPoint(
                "Your content",
                "You keep ownership of the profile details and photo you provide. You give us a limited licence to host " +
                        "and display them solely to operate the Service."
            ),
            PolicyPoint(
                "Feedback",
                "If you send us suggestions, we may use them without any obligation to you."
            ),
        ),
    ),
    PolicySectionData(
        icon = Icons.Outlined.Cloud,
        title = "Third-party services",
        intro = "CALLIN relies on third-party services, including Google (Sign-In and Firebase Cloud Messaging) and " +
                "cloud storage and hosting providers. Their own terms and privacy policies apply to the parts of the " +
                "Service they provide.",
        outro = "We are not responsible for third-party services outside our control, or for outages or changes on their " +
                "side that affect CALLIN.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.Update,
        title = "Availability and changes to the Service",
        intro = "We work to keep CALLIN reliable, but we do not promise that it will always be available, uninterrupted, " +
                "or error-free.",
        points = listOf(
            PolicyPoint(
                text = "We may add, change, suspend, or discontinue features, with or without notice, for reasons such " +
                        "as maintenance, security, legal requirements, or technical limits."
            ),
            PolicyPoint(
                text = "We may release app updates, and some updates may be required to keep using the Service."
            ),
        ),
    ),
    PolicySectionData(
        icon = Icons.Outlined.Lock,
        title = "Suspension and termination",
        points = listOf(
            PolicyPoint(
                "You",
                "You may stop using CALLIN at any time and can request deletion of your account as described in the " +
                        "Privacy Policy."
            ),
            PolicyPoint(
                "Us",
                "We may suspend, restrict, or end your access, with or without notice, if you break these Terms, put " +
                        "others or the Service at risk, or if the law requires it."
            ),
            PolicyPoint(
                "What continues",
                "Provisions that by their nature should continue, such as ownership, disclaimers, limits on liability, " +
                        "and governing law, survive termination."
            ),
        ),
    ),
    PolicySectionData(
        icon = Icons.Outlined.Warning,
        title = "Disclaimers",
        intro = "To the fullest extent permitted by law, CALLIN is provided “as is” and “as available”, without " +
                "warranties of any kind, whether express or implied, including warranties of merchantability, fitness " +
                "for a particular purpose, and non-infringement.",
        outro = "We do not warrant call quality, that every call or notification will be delivered, or that the Service " +
                "will meet your particular needs. We are not responsible for what other users say or do, and you are " +
                "responsible for your own interactions on CALLIN.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.Gavel,
        title = "Limitation of liability",
        intro = "To the fullest extent permitted by law, we will not be liable for any indirect, incidental, special, " +
                "consequential, or punitive damages, or for loss of profits, data, or goodwill, arising from your use " +
                "of, or inability to use, the Service. This includes missed or dropped calls and delayed or undelivered " +
                "notifications.",
        outro = "Our total liability for any claim relating to the Service will not exceed the amount you paid to use it, " +
                "if any, in the 12 months before the event that gave rise to the claim. Nothing in these Terms limits " +
                "liability that cannot be limited by law.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.Sync,
        title = "Changes to these Terms",
        intro = "We may revise these Terms from time to time. The updated version takes effect when it is posted in the " +
                "app with a new “Updated” date, and we will give reasonable notice of material changes.",
        outro = "If you do not agree to the revised Terms, stop using CALLIN and delete your account. Continuing to use " +
                "the Service after they take effect means you accept them.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.Public,
        title = "Governing law and disputes",
        intro = "These Terms are governed by the laws of India, without regard to conflict-of-law rules. Subject to any " +
                "mandatory consumer-protection rights you have where you live, the courts of India have jurisdiction " +
                "over disputes arising from these Terms or the Service.",
        outro = "We encourage you to contact us first so we can try to resolve any issue informally.",
    ),
    PolicySectionData(
        icon = Icons.Outlined.Description,
        title = "General terms",
        points = listOf(
            PolicyPoint(
                "Entire agreement",
                "These Terms and the Privacy Policy are the whole agreement between you and us about CALLIN."
            ),
            PolicyPoint("Severability", "If any part is found unenforceable, the rest remains in effect."),
            PolicyPoint("No waiver", "Our not enforcing a right is not a waiver of it."),
            PolicyPoint(
                "Assignment",
                "You may not transfer your account or your rights under these Terms. We may transfer ours as part of " +
                        "a business change."
            ),
            PolicyPoint("Headings", "Headings are for convenience only."),
        ),
    ),
    PolicySectionData(
        icon = Icons.Outlined.SupportAgent,
        title = "Contact",
        intro = "Questions about these Terms, or want to report abuse or a safety concern? Contact the developer, " +
                "Sambhav Dwivedi, through the website linked at the bottom of this page. Include the username involved " +
                "and a short description of what happened.",
    ),
)

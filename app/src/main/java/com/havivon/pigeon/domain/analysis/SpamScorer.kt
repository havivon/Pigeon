package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.SpamReason

/** What the analysers need to know about one incoming message. */
data class IncomingSms(
    /** The sender as the network gave it: a phone number, or an alphanumeric id such as "Leumi". */
    val address: String,
    /** The display name when it differs from the address (a contact's name); null if there is none. */
    val senderName: String? = null,
    val body: String,
    /** The sender is in the user's contacts. */
    val isContact: Boolean = false,
    /** Senders the user marked "not spam", as addresses. Phone numbers compare by digits. */
    val allowedSenders: Collection<String> = emptyList(),
    /** Words and phrases the user blocks. A message containing one is always spam. */
    val blockedKeywords: Collection<String> = emptyList(),
)

/** The spam filter's answer: a [score] and, for the UI, the [reasons] behind it. */
data class SpamVerdict(val score: Int, val reasons: List<SpamReason>) {
    val isSpam: Boolean get() = score >= SpamScorer.SPAM_THRESHOLD
}

/**
 * Scores a message for spam on the device, with no server and no model: a handful of signs, each worth
 * some points, in Hebrew and English. The signs are weak on their own and strong together: a stranger
 * writing "הסר" is an advertiser, a stranger writing "זכית" with a shortened link is a scam.
 */
object SpamScorer {

    /**
     * The score from which a message goes to the spam folder. Every message from an unknown sender starts
     * at [UNKNOWN_SENDER], so it takes about two more signs: a shortened link and a bait phrase, a look-alike
     * domain alone, or a phrase from the strong list plus any other. One moderate sign, such as "הסר"
     * (which Israeli law requires in advertising), never decides by itself.
     */
    const val SPAM_THRESHOLD = 50

    /** The score of a message containing a word the user blocked: far above the threshold. */
    const val BLOCKED_SCORE = 100

    private const val UNKNOWN_SENDER = 15
    private const val SHORT_LINK = 30
    private const val LOOKALIKE_DOMAIN = 45
    private const val BRAND_SENDER = 40
    private const val MANY_LINKS = 15
    private const val MANY_LINKS_FROM = 3

    /** Points from phrases are capped, so a long ad full of mild words does not outweigh a real link signal. */
    private const val PHRASE_CAP = 60

    private const val STRONG = 25
    private const val MODERATE = 15
    private const val MILD = 10

    /**
     * A bait or pressure phrase. [label] is what the UI shows; [variants] are the spellings looked for,
     * as whole words (Hebrew ones also with ה/ו/ב/ל/מ/ש/כ in front).
     */
    private class Phrase(val label: String, val points: Int, vararg val variants: String)

    private val PHRASES = listOf(
        // Prizes and bait.
        Phrase("זכית", STRONG, "זכית", "זכיתם"),
        Phrase("won", STRONG, "you won", "you've won", "you have won", "winner", "you are a winner"),
        Phrase("claim", STRONG, "claim your", "claim now", "claim prize", "claim reward", "to claim"),
        Phrase("מתנה חינם", STRONG, "מתנה חינם", "מתנה בחינם", "פרס כספי", "הגרלה"),
        Phrase("free gift", STRONG, "free gift", "free prize", "lottery", "jackpot"),
        // Parcels: the most common scam in Israel.
        Phrase("חבילה ממתינה", STRONG, "חבילה ממתינה", "החבילה שלך ממתינה", "חבילה מחכה", "החבילה ממתינה", "החבילה מחכה"),
        Phrase("package waiting", STRONG, "package is waiting", "package waiting", "parcel is waiting", "package on hold", "package is on hold", "parcel on hold"),
        Phrase("דמי משלוח", STRONG, "דמי משלוח", "דמי טיפול", "תשלום חסר", "עמלת שחרור"),
        Phrase("delivery fee", STRONG, "delivery fee", "redelivery", "failed delivery", "delivery failed", "customs fee"),
        // Loans and money.
        Phrase("הלוואה", STRONG, "הלוואה", "הלוואות", "בלי בדיקות", "ללא בדיקות", "אישור מיידי"),
        Phrase("loan", STRONG, "loan", "loans", "payday", "pre-approved", "no credit check", "instant approval"),
        Phrase("קזינו", STRONG, "קזינו", "הימורים", "קזינו אונליין"),
        Phrase("casino", STRONG, "casino", "betting", "sportsbook"),
        // Phishing: account threats.
        Phrase("החשבון ייחסם", STRONG, "ייחסם", "יחסם", "הושעה", "נחסם", "אמת את הפרטים", "אמתו את הפרטים", "עדכן את הפרטים", "פעילות חריגה"),
        Phrase("account suspended", STRONG, "account suspended", "account locked", "account has been locked", "verify your account", "unusual activity", "confirm your identity"),
        // Calls to action.
        Phrase("לחץ כאן", MODERATE, "לחץ כאן", "לחצו כאן", "הקש כאן", "לחץ על הקישור", "לחצו על הקישור"),
        Phrase("click here", MODERATE, "click here", "click the link", "tap here", "tap the link", "click below"),
        // The opt-out line. Israeli law requires it in every ad, so it is a sign of an advertiser, not of a scam.
        Phrase("הסר", MODERATE, "הסר", "הסרה", "להסרה"),
        Phrase("STOP", MODERATE, "reply stop", "text stop", "send stop", "stop to opt out", "unsubscribe", "opt out"),
        // Pressure and promotions.
        Phrase("היום בלבד", MILD, "היום בלבד", "לזמן מוגבל", "דחוף", "מיידי"),
        Phrase("act now", MILD, "act now", "limited time", "today only", "urgent", "final notice", "hurry"),
        Phrase("מבצע", MILD, "מבצע", "הטבה", "הנחה", "חינם", "בונוס"),
        Phrase("free", MILD, "free", "discount", "sale", "bonus", "offer"),
    )

    /**
     * The verdict for [sms]. A saved contact or an allowed sender is never spam, whatever the text says;
     * otherwise a blocked keyword always is.
     */
    fun score(sms: IncomingSms): SpamVerdict {
        if (sms.isContact) return SpamVerdict(0, emptyList())
        val key = Text.senderKey(sms.address)
        if (sms.allowedSenders.any { Text.senderKey(it) == key }) return SpamVerdict(0, emptyList())

        val words = Text.words(sms.body)
        val reasons = ArrayList<SpamReason>()
        var score = UNKNOWN_SENDER
        reasons += SpamReason.UnknownSender

        val links = LinkAnalyzer.findLinks(sms.body)
        if (links.any { it.isShortener }) {
            score += SHORT_LINK
            reasons += SpamReason.ShortLink
        }
        if (links.any { it.isLookalike }) {
            score += LOOKALIKE_DOMAIN
            reasons += SpamReason.LookalikeDomain
        }
        if (impersonatesBrand(sms, links)) {
            score += BRAND_SENDER
            reasons += SpamReason.BrandSender
        }
        if (links.size >= MANY_LINKS_FROM) {
            score += MANY_LINKS
            reasons += SpamReason.ManyLinks
        }

        var phrasePoints = 0
        for (phrase in PHRASES) {
            if (phrase.variants.any { Text.containsPhrase(words, it) }) {
                phrasePoints += phrase.points
                reasons += SpamReason.Keyword(phrase.label)
            }
        }
        score += minOf(phrasePoints, PHRASE_CAP)

        val blocked = sms.blockedKeywords.filter { it.isNotBlank() && Text.containsPhrase(words, it) }
        if (blocked.isNotEmpty()) {
            score = maxOf(score, BLOCKED_SCORE)
            reasons.addAll(0, blocked.map { SpamReason.BlockedKeyword(it.trim()) })
        }
        return SpamVerdict(score, reasons)
    }

    /** An alphanumeric sender named like a known company, whose message links somewhere that company does not live. */
    private fun impersonatesBrand(sms: IncomingSms, links: List<LinkInfo>): Boolean {
        if (links.isEmpty() || !Text.isAlphanumericSender(sms.address)) return false
        val brand = LinkAnalyzer.brandNamed(sms.address) ?: sms.senderName?.let { LinkAnalyzer.brandNamed(it) } ?: return false
        return links.any { !LinkAnalyzer.isGenuineHost(brand, it.host) }
    }
}

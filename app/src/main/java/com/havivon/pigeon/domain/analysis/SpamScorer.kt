package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.Folder
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
    /** The user wrote in this conversation: someone they talk to, treated like a contact. */
    val userWrote: Boolean = false,
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
     * The version of these rules, stored with what spam-on-read judged ([SpamOnRead]): when it changes, the
     * conversations are judged again with the new rules. Raise it with every change of the rules. The
     * user's own "Not spam" and "Mark as spam" are never judged over.
     * 1: the first rules. 2: polls and political campaigns, and the opt-out line together with an ad.
     * 3: money bait, home-made short links, and the opt-out line from an unknown sender id with a link.
     */
    const val VERSION = 3

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

    /** A poll ("סקר", "survey") and a vote ("הצבעה", "vote"): moderate each, a residents' committee writes them too. */
    private const val POLL_SURVEY = 15
    private const val POLL_VOTE = 15

    /** Elections, parties, candidates, the prime minister: strong, and alone from a stranger still below the threshold. */
    private const val POLL_POLITICS = 25

    /** "הקישו 1", "השיבו 2", "reply 1": counted only beside another poll sign ("השב 1 לאישור התור" is a clinic). */
    private const val POLL_REPLY_DIGIT = 15

    private const val POLL_CAP = 50

    /**
     * The opt-out line together with what makes it an ad, added once: with the opt-out itself and the unknown
     * sender, enough for the threshold. It counts for a stranger's phone number with a link or ad wording, for an
     * unknown sender id ("BituaCheck") with a link and ad or bait wording, and for any sender with a poll. A known
     * brand's sender id ([KNOWN_BRANDS], the banks, insurers and couriers) with "מבצע ... להסרה" stays below: that
     * is its ordinary advertising, which the law requires to say "הסר". Never for a service notice: a code, a
     * bank, an insurer, a delivery, an appointment or the municipality.
     */
    private const val OPT_OUT_AD = 20

    /**
     * Money bait: "ייתכן שמגיע לכם כסף", "החזר מס", "בדיקה חינם", "חיסכון של ₪2,300", "you may be owed". The
     * hook of the insurance, pension, tax-refund and loan ads; with a link and an opt-out line from a stranger,
     * well over the threshold. A real refund notice from the bank or the tax authority comes without the opt-out
     * line and the home-made short link, so it stays below.
     */
    private const val MONEY_BAIT = 25

    private val MONEY_BAIT_WORDS = words(
        "מגיע לך כסף", "מגיע לכם כסף", "ייתכן שמגיע", "יתכן שמגיע", "ייתכן ומגיע", "יתכן ומגיע", "אולי מגיע",
        "החזר מס", "החזרי מס", "החזר כספי", "החזרים כספיים", "בדיקה חינם", "בדיקה בחינם", "בדיקה ללא עלות",
        "בדיקת זכאות", "בדיקת קרן פנסיה", "בדיקת ביטוחים", "הנחה מגיעה", "כסף שמגיע", "הכסף שמגיע", "הכסף שלכם מחכה",
        "you may be owed", "you could be owed", "you are owed", "money you are owed", "claim your refund",
        "claim your money", "free check", "free review", "free assessment", "check your eligibility",
    )

    /** "חיסכון של ₪2,300", "חסכו 2,300 ₪", "save $500": a saving promised as a sum. In the normalized text. */
    private val SAVING_SUM = Regex(
        "(?:חיסכון|חסכון|חסכו|תחסכו|לחסוך|save|saving|savings)\\s+(?:של\\s+|of\\s+|up to\\s+|עד\\s+)?(?:₪|\\$|€)?\\s?\\d",
    )

    /**
     * Well-known Israeli and international brands whose sender id sends ordinary advertising with the opt-out
     * line. Compared as the whole sender, without spaces, dots and hyphens ("Super-Pharm" is "superpharm"). The
     * banks, insurers and couriers of [FolderClassifier] count as known too.
     */
    private val KNOWN_BRANDS = setOf(
        "superpharm", "סופרפארם", "shufersal", "שופרסל", "ramilevy", "רמילוי", "yochananof", "יוחננוף", "victory", "ויקטורי",
        "osherad", "אושרעד", "tivtaam", "טיבטעם", "carrefour", "קרפור", "goodpharm", "גודפארם", "bepharm", "fox", "פוקס",
        "castro", "קסטרו", "renuar", "רנואר", "golf", "גולף", "zara", "hm", "ikea", "איקאה", "acehardware", "ace", "אייס",
        "homecenter", "הוםסנטר", "officedepot", "maxstock", "מקססטוק", "terminalx", "ksp", "bug", "באג", "ivory", "איבורי",
        "cellcom", "סלקום", "partner", "פרטנר", "pelephone", "פלאפון", "hotmobile", "hot", "הוט", "yes", "bezeq", "בזק",
        "golan", "גולן", "019", "012", "013", "dominos", "דומינוס", "pizzahut", "mcdonalds", "מקדונלדס", "aroma",
        "ארומה", "cofix", "קופיקס", "burgeranch", "wolt", "10bis", "תןביס", "gett", "yango", "uber", "elal", "אלעל",
        "israir", "ישראייר", "arkia", "ארקיע", "maccabi", "מכבי", "clalit", "כללית", "meuhedet", "מאוחדת", "leumit",
        "לאומית", "netflix", "spotify", "google", "apple", "microsoft", "amazon", "aliexpress", "shein", "temu", "ebay",
        "paypal", "booking", "airbnb", "ravkav", "רבקו", "pango", "פנגו", "cellopark", "סלופארק",
    )

    /**
     * A bait or pressure phrase. [label] is what the UI shows; [variants] are the spellings looked for,
     * as whole words (Hebrew ones also with ה/ו/ב/ל/מ/ש/כ in front). [ad] marks wording that makes an
     * opt-out line an ad's ("מבצע", "זכית"); a call to action or the opt-out itself is not.
     */
    private class Phrase(val label: String, val points: Int, val variants: List<String>, val ad: Boolean = true, val optOut: Boolean = false)

    private fun phrase(label: String, points: Int, vararg variants: String) = Phrase(label, points, variants.toList())

    private val PHRASES = listOf(
        // Prizes and bait.
        phrase("זכית", STRONG, "זכית", "זכיתם"),
        phrase("won", STRONG, "you won", "you've won", "you have won", "winner", "you are a winner"),
        phrase("claim", STRONG, "claim your", "claim now", "claim prize", "claim reward", "to claim"),
        phrase("מתנה חינם", STRONG, "מתנה חינם", "מתנה בחינם", "פרס כספי", "הגרלה"),
        phrase("free gift", STRONG, "free gift", "free prize", "lottery", "jackpot"),
        // Parcels: the most common scam in Israel.
        phrase("חבילה ממתינה", STRONG, "חבילה ממתינה", "החבילה שלך ממתינה", "חבילה מחכה", "החבילה ממתינה", "החבילה מחכה"),
        phrase("package waiting", STRONG, "package is waiting", "package waiting", "parcel is waiting", "package on hold", "package is on hold", "parcel on hold"),
        phrase("דמי משלוח", STRONG, "דמי משלוח", "דמי טיפול", "תשלום חסר", "עמלת שחרור"),
        phrase("delivery fee", STRONG, "delivery fee", "redelivery", "failed delivery", "delivery failed", "customs fee"),
        // Loans and money.
        phrase("הלוואה", STRONG, "הלוואה", "הלוואות", "בלי בדיקות", "ללא בדיקות", "אישור מיידי"),
        phrase("loan", STRONG, "loan", "loans", "payday", "pre-approved", "no credit check", "instant approval"),
        phrase("קזינו", STRONG, "קזינו", "הימורים", "קזינו אונליין"),
        phrase("casino", STRONG, "casino", "betting", "sportsbook"),
        // Phishing: account threats.
        phrase("החשבון ייחסם", STRONG, "ייחסם", "יחסם", "הושעה", "נחסם", "אמת את הפרטים", "אמתו את הפרטים", "עדכן את הפרטים", "פעילות חריגה"),
        phrase("account suspended", STRONG, "account suspended", "account locked", "account has been locked", "verify your account", "unusual activity", "confirm your identity"),
        // Calls to action: "לחץ כאן להסרה" is how many opt-out lines read, so they do not make it an ad.
        Phrase("לחץ כאן", MODERATE, listOf("לחץ כאן", "לחצו כאן", "הקש כאן", "לחץ על הקישור", "לחצו על הקישור"), ad = false),
        Phrase("click here", MODERATE, listOf("click here", "click the link", "tap here", "tap the link", "click below"), ad = false),
        // The opt-out line. Israeli law requires it in every ad, so it is a sign of an advertiser, not of a scam.
        Phrase("הסר", MODERATE, listOf("הסר", "הסרה", "להסרה"), ad = false, optOut = true),
        Phrase("STOP", MODERATE, listOf("reply stop", "text stop", "send stop", "stop to opt out", "stop to end", "unsubscribe", "opt out"), ad = false, optOut = true),
        // Pressure and promotions.
        phrase("היום בלבד", MILD, "היום בלבד", "לזמן מוגבל", "דחוף", "מיידי"),
        phrase("act now", MILD, "act now", "limited time", "today only", "urgent", "final notice", "hurry"),
        phrase("מבצע", MILD, "מבצע", "הטבה", "הנחה", "חינם", "בונוס"),
        phrase("free", MILD, "free", "discount", "sale", "bonus", "offer"),
    )

    /** Keyword phrases, split into words once rather than for every message. */
    private fun words(vararg phrase: String): List<List<String>> = phrase.map { Text.words(it) }

    private val POLL_SURVEY_WORDS = words("סקר", "סקרים", "שאלון", "poll", "polls", "survey", "questionnaire")

    private val POLL_VOTE_WORDS = words(
        "הצבעה", "להצביע", "תצביע", "תצביעי", "תצביעו", "הצביעו", "מצביע", "מצביעים",
        "vote", "votes", "voting", "voter", "voters", "ballot",
    )

    /** The words of elections and parties. Not "כנסת", which is also the synagogue's ("בית הכנסת"). */
    private val POLL_POLITICS_WORDS = words(
        "בחירות", "מפלגה", "מפלגות", "מועמד", "מועמדת", "מועמדים", "ראש הממשלה", "ראשות הממשלה", "קלפי", "קואליציה",
        "אופוזיציה", "ליכוד", "יש עתיד", "ישראל ביתנו", "יהדות התורה", "הציונות הדתית", "המחנה הממלכתי",
        "election", "elections", "candidate", "candidates", "prime minister", "political party", "polling station", "knesset",
    )

    /** The verb in front of the digit of "הקישו 1", "השיבו 2", "reply 1". */
    private val REPLY_VERBS = words(
        "הקישו", "הקש", "הקישי", "השיבו", "השב", "השיבי", "השבי", "שלחו", "שלח", "ענו", "ענה", "עני",
        "press", "reply", "text", "dial", "send",
    )

    /** Words of an appointment or a public service's notice, which may carry an opt-out line and a link. */
    private val SERVICE_WORDS = words(
        "התור", "תורך", "לתור", "קביעת תור", "זימון תור", "appointment", "קופת חולים", "מרפאה", "clinic",
        "עירייה", "עיריית", "municipality", "מועצה מקומית",
    )

    /** The folders of a service notice, whose opt-out line is no ad. */
    private val SERVICE_FOLDERS = setOf(Folder.Codes, Folder.Banks, Folder.Insurance, Folder.Deliveries)

    private val NOT_SPAM = SpamVerdict(0, emptyList())

    /**
     * The verdict for [sms]. A saved contact, someone the user wrote to or an allowed sender is never spam,
     * whatever the text says; otherwise a blocked keyword always is.
     */
    fun score(sms: IncomingSms): SpamVerdict {
        if (sms.isContact || sms.userWrote) return NOT_SPAM
        val key = Text.senderKey(sms.address)
        if (sms.allowedSenders.any { Text.senderKey(it) == key }) return NOT_SPAM

        val words = Text.words(sms.body)
        val reasons = ArrayList<SpamReason>()
        var score = UNKNOWN_SENDER
        reasons += SpamReason.UnknownSender

        val links = LinkAnalyzer.findLinks(sms.body)
        // A known shortener, or a home-made one such as "lp6.me/nejcz" (LinkAnalyzer.looksShortened)
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

        val poll = pollPoints(words)
        if (poll > 0) {
            score += poll
            reasons += SpamReason.Poll
        }

        var phrasePoints = 0
        var optOut = false
        var adWording = false
        for (phrase in PHRASES) {
            if (phrase.variants.any { Text.containsPhrase(words, it) }) {
                phrasePoints += phrase.points
                reasons += SpamReason.Keyword(phrase.label)
                optOut = optOut || phrase.optOut
                adWording = adWording || phrase.ad
            }
        }
        score += minOf(phrasePoints, PHRASE_CAP)

        val bait = MONEY_BAIT_WORDS.any { Text.containsWords(words, it) } || SAVING_SUM.containsMatchIn(Text.normalize(sms.body))
        if (bait) {
            score += MONEY_BAIT
            reasons += SpamReason.MoneyBait
        }

        if (optOut) {
            val stranger = Text.phoneKey(sms.address) != null
            val unknownId = Text.isAlphanumericSender(sms.address) && !isKnownBrand(sms)
            val advertises = poll > 0 ||
                (stranger && (links.isNotEmpty() || adWording || bait)) ||
                (unknownId && links.isNotEmpty() && (adWording || bait))
            if (advertises && !isServiceNotice(sms, words)) score += OPT_OUT_AD
        }

        val blocked = sms.blockedKeywords.filter { it.isNotBlank() && Text.containsPhrase(words, it) }
        if (blocked.isNotEmpty()) {
            score = maxOf(score, BLOCKED_SCORE)
            reasons.addAll(0, blocked.map { SpamReason.BlockedKeyword(it.trim()) })
        }
        return SpamVerdict(score, reasons)
    }

    /** Whether [body] has the words of a poll or a political campaign: the [SpamReason.Poll] sign, whoever sent it. */
    fun isPoll(body: String): Boolean = pollPoints(Text.words(body)) > 0

    /** The poll and political-campaign points of a message's [words]; 0 when it has none of their words. */
    private fun pollPoints(words: List<String>): Int {
        var points = 0
        if (POLL_SURVEY_WORDS.any { Text.containsWords(words, it) }) points += POLL_SURVEY
        if (POLL_VOTE_WORDS.any { Text.containsWords(words, it) }) points += POLL_VOTE
        if (POLL_POLITICS_WORDS.any { Text.containsWords(words, it) }) points += POLL_POLITICS
        if (points > 0 && hasReplyDigit(words)) points += POLL_REPLY_DIGIT
        return minOf(points, POLL_CAP)
    }

    /** "הקישו 1", "והשיבו 2", "reply 1": a reply verb right in front of a single digit. */
    private fun hasReplyDigit(words: List<String>): Boolean {
        for (i in 0 until words.size - 1) {
            val next = words[i + 1]
            if (next.length != 1 || !next[0].isDigit()) continue
            if (REPLY_VERBS.any { Text.containsWords(listOf(words[i]), it) }) return true
        }
        return false
    }

    /**
     * A code, a bank, an insurer, a delivery or an appointment: an opt-out line there does not make it an ad. The
     * shape is judged as if there were no opt-out line ([FolderClassifier.serviceFolder]): the classifier files every
     * ad in All, which would make every notice with "להסרה" look like an ad here.
     */
    private fun isServiceNotice(sms: IncomingSms, words: List<String>): Boolean =
        SERVICE_WORDS.any { Text.containsWords(words, it) } ||
            CodeExtractor.extract(sms.body) != null ||
            FolderClassifier.serviceFolder(sms) in SERVICE_FOLDERS

    /** A well-known brand's sender id ([KNOWN_BRANDS]) or a known bank, insurer or courier. */
    private fun isKnownBrand(sms: IncomingSms): Boolean {
        val compact = Text.words(sms.address.replace('-', ' ').replace('.', ' ')).joinToString("")
        return compact in KNOWN_BRANDS || FolderClassifier.isInstitution(sms) || LinkAnalyzer.brandNamed(sms.address) != null
    }

    /** An alphanumeric sender named like a known company, whose message links somewhere that company does not live. */
    private fun impersonatesBrand(sms: IncomingSms, links: List<LinkInfo>): Boolean {
        if (links.isEmpty() || !Text.isAlphanumericSender(sms.address)) return false
        val brand = LinkAnalyzer.brandNamed(sms.address) ?: sms.senderName?.let { LinkAnalyzer.brandNamed(it) } ?: return false
        return links.any { !LinkAnalyzer.isGenuineHost(brand, it.host) }
    }
}

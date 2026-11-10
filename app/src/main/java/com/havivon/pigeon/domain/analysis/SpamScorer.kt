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
     * 4: the common Israeli scams (an authority asking to click or pay, investment and crypto, a request for card
     * details or a code), a number from abroad, a sum of money or a call to move to WhatsApp, the "פרסומת" marker,
     * and pressure next to a link. The rules in plain words: docs/spam-rules.md.
     * 5: drugs for sale (a drug word with a sales cue), a link or number that sends the reader to another number than
     * the sender's own, and the wording of a sale ("מחיר מוזל", "מתנה בכל קניה", "special price").
     */
    const val VERSION = 5

    /**
     * The score from which a message goes to the spam folder. Every message from an unknown sender starts
     * at [UNKNOWN_SENDER], so it takes about two more signs: a shortened link and a bait phrase, a look-alike
     * domain alone, or a phrase from the strong list plus any other. One moderate sign, such as "הסר"
     * (which Israeli law requires in advertising) or a number from abroad, never decides by itself; nor do the
     * mild signs of the shape (a sum, a phone number, capitals), capped at [STRUCTURE_CAP]. Three signs decide
     * alone, because no genuine sender writes them: an authority asking to click or pay off its own domains
     * ([GOV_IMPERSONATION]), a request for card details or a code ([ASKS_FOR_DETAILS]), and a look-alike domain.
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

    /**
     * Points from phrases (the list below, pressure next to a link and the "פרסומת" marker) are capped, so a long ad
     * full of mild words does not outweigh a real link signal. The signs of version 4 that name a scheme (an
     * authority, an investment, a request for details) are counted outside the cap, as money bait is: each is one
     * sign, added once however many of its words the message has.
     */
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

    // --- version 4: the common Israeli scams, the sender's country, the shape of the message ---

    /**
     * An authority asking to click or pay, from a sender that is not that authority or with a link off its domains
     * ([impersonatesAuthority]): "פיקוד העורף", "רשות המסים", "ביטוח לאומי", "חוב אגרה בכביש 6", "קנס ... לתשלום".
     * With the unknown sender, over the threshold by itself: the authorities never send these.
     */
    private const val GOV_IMPERSONATION = 40

    /** An investment or crypto word ("ביטקוין", "תשואה מובטחת", "רווח יומי"): strong, below the threshold alone. */
    private const val INVESTMENT = 25

    /** The investment word together with "join our group" ("הצטרפו לקבוצה"), the usual shape of the scheme. */
    private const val INVESTMENT_GROUP = 35

    /**
     * A request for card details or a code ("הזינו את פרטי הכרטיס", "שלחו לנו את הקוד", "confirm your card"), not
     * negated ("אין למסור את הקוד", "never send this code"). With the unknown sender, over the threshold: no one but a
     * thief asks for these in a text. A known brand's own request with no link off its domains does not count.
     */
    private const val ASKS_FOR_DETAILS = 35

    /** A phone number from outside Israel (+ or 00 and a country code other than 972): moderate, the user lives in Israel. */
    private const val FOREIGN_NUMBER = 15

    // --- version 5: drugs for sale, and a number other than the sender's own ---

    /** Drugs offered for sale: a drug word together with a sales cue ([sellsDrugs]). Alone with the unknown sender, over the threshold. */
    private const val DRUGS = 40

    /**
     * A link or a number in the text that sends the reader to a number other than the sender's own ("wa.me/9725...",
     * "t.me/...", "WhatsApp 054-..."). Counted in place of the 10 points of [SpamReason.ContactElsewhere], and
     * outside the cap of the shape signs. A business writing its own number from its own number is not it.
     */
    private const val REDIRECT_NUMBER = 20

    /** Each mild sign of the shape: a sum of money, a call to phone or to move to WhatsApp, long English capitals. */
    private const val STRUCTURE = 10

    /** The shape signs together never weigh more than this: alone with the unknown sender, still below. */
    private const val STRUCTURE_CAP = 20

    /** A pressure phrase in a message with a link: "דחוף, לחצו כאן", "final notice: pay at". */
    private const val URGENT_WITH_LINK = 15

    /**
     * The domains of the Israeli authorities, whose links never count as a stray link: gov.il and every ministry,
     * authority and court under it (taxes.gov.il, btl.gov.il, police.gov.il, eca.gov.il), the Home Front Command's
     * oref.org.il, the toll roads' kvish6.co.il and fastlane.co.il, and the municipalities' muni.il.
     */
    private val GOV_DOMAINS = listOf("gov.il", "oref.org.il", "kvish6.co.il", "fastlane.co.il", "muni.il")

    /**
     * The sender ids of the authorities and toll roads, found anywhere in the sender written as one run
     * ("Kvish6", "BTL-Gov", "PikudHaoref"). The banks, insurers (National Insurance among them) and couriers of
     * [FolderClassifier] count as genuine too.
     */
    private val GOV_SENDER_MARKS = listOf(
        "govil", "kvish6", "כביש6", "fastlane", "netivmahir", "נתיבמהיר", "pikudhaoref", "פיקודהעורף", "oref", "misim",
        "רשותהמסים", "taxauthority", "bituachleumi", "ביטוחלאומי", "israelpolice", "משטרתישראל", "רשותהאכיפה", "הוצאהלפועל",
    )

    /** Short authority ids that count only as the whole sender. */
    private val GOV_SENDER_NAMES = setOf("gov", "btl", "police", "משטרה", "eca", "mot", "moh", "taxes", "misrad")

    /** An authority the message speaks for: the claim scammers make. Not every ministry: a clinic quotes "משרד הבריאות". */
    private val AUTHORITY_WORDS = words(
        "פיקוד העורף", "רשות המסים", "רשות המיסים", "מס הכנסה", "ביטוח לאומי", "משרד הממשלה", "משרדי הממשלה", "משרד התחבורה",
        "משרד הרישוי", "רשות האכיפה", "הוצאה לפועל", "משטרת ישראל", "כביש 6", "כביש חוצה ישראל", "נתיב מהיר",
        "הנתיב המהיר", "חוב אגרה", "חוב האגרה", "אגרת כביש", "אגרת רישוי", "אגרת רישיון", "ייצא לאכיפה", "יועבר לאכיפה",
        "יעבור לאכיפה", "gov.il", "home front command", "tax authority", "national insurance", "ministry of transport",
        "highway 6", "toll road", "unpaid toll", "toll debt", "israel police",
    )

    private val HOME_FRONT = words("פיקוד העורף", "home front command")

    /** A fine or a ticket: an authority's word only with a request to pay through a link. */
    private val DEBT_WORDS = words("קנס", "קנסות", "דוח", "דו\"ח", "fine", "penalty", "traffic ticket", "parking ticket")

    /** A request to pay. Not "שלם" alone, which is also "whole". */
    private val PAY_WORDS = words(
        "לתשלום", "יש לשלם", "נא לשלם", "שלם עכשיו", "שלמו עכשיו", "שלם כעת", "שלמו כעת", "שלם כאן", "שלמו כאן",
        "שלם את", "שלמו את", "יש להסדיר", "להסדרת", "הסדירו", "pay now", "pay here", "pay your", "pay the", "pay online",
        "to pay", "make a payment", "payment required", "settle",
    )

    /** What the scam asks beside the authority's name and a stray link: to pay, update, collect a refund or install. */
    private val AUTHORITY_ACTIONS = words(
        "עדכן", "עדכנו", "עדכני", "אמת", "אמתו", "אמתי", "החזר", "החזרים", "זיכוי", "הורד", "הורידו", "התקן", "התקינו",
        "אפליקציה", "האפליקציה", "ייחסם", "יחסם", "יבוטל", "עיקול", "חוב", "קנס", "update", "verify", "confirm", "refund",
        "download", "install", "app", "apk", "suspended", "blocked", "debt", "fine", "penalty", "unpaid",
    )

    private val DOWNLOAD_WORDS = words(
        "הורד", "הורידו", "התקן", "התקינו", "אפליקציה", "האפליקציה", "יישומון", "download", "install", "app", "apk",
    )

    /** A municipality's own id writes about parking fines: the fine rule does not count for it. */
    private val MUNICIPAL_WORDS = words("עירייה", "עיריית", "מועצה", "municipality", "city of")

    /** An investment or crypto scheme, in its own words. Not "השקעה" alone: "תודה על ההשקעה" is a thank-you. */
    private val INVESTMENT_WORDS = words(
        "ביטקוין", "קריפטו", "מטבעות קריפטו", "מטבע קריפטו", "מטבעות דיגיטליים", "בינה מלאכותית למסחר", "מסחר אוטומטי",
        "רובוט מסחר", "בוט מסחר", "תשואה מובטחת", "תשואות מובטחות", "רווח מובטח", "רווחים מובטחים", "רווח יומי",
        "רווחים יומיים", "הכנסה פסיבית", "קבוצת השקעות", "קבוצת ההשקעות", "קבוצת מסחר", "קבוצת המסחר", "הזדמנות השקעה",
        "השקעה בטוחה", "פורקס", "אופציות בינאריות", "איתותי מסחר",
        "bitcoin", "btc", "crypto", "cryptocurrency", "investment", "investments", "ai trading", "trading bot",
        "auto trading", "guaranteed return", "guaranteed returns", "guaranteed profit", "daily profit", "daily profits",
        "daily returns", "passive income", "forex", "trading signals", "investment group",
    )

    /** "Join our group": with an investment word, the scheme's usual shape; with a promise of profit, the scheme itself. */
    private val GROUP_JOIN = words(
        "הצטרפו לקבוצה", "הצטרף לקבוצה", "הצטרפי לקבוצה", "הצטרפו לקבוצת", "הצטרף לקבוצת", "join our group", "join the group",
        "join our whatsapp", "join our telegram",
    )

    private val PROFIT_WORDS = words(
        "רווח", "רווחים", "תשואה", "תשואות", "להרוויח", "הרוויחו", "תרוויחו", "profit", "profits", "returns", "earn", "income",
    )

    /** The verbs of a request for details, imperative, each with its Hebrew prefixes. */
    private val DETAIL_VERBS = words(
        "הזן", "הזינו", "הזיני", "להזין", "הכנס", "הכניסו", "הקלד", "הקלידו", "אמת", "אמתו", "אמתי", "לאמת", "עדכן", "עדכנו",
        "עדכני", "לעדכן", "שלח", "שלחו", "שלחי", "מסור", "מסרו", "מסרי", "למסור", "השב", "השיבו",
        "enter", "confirm", "verify", "update", "send", "provide", "reply",
    )

    /**
     * The verbs that hand a code to someone: only these ask for it. Entering the code where it was asked for ("הזינו
     * את הקוד באתר", "enter the code to verify") is what every one-time code message says.
     */
    private val HAND_OVER_VERBS = words(
        "שלח", "שלחו", "שלחי", "מסור", "מסרו", "מסרי", "למסור", "השב", "השיבו", "send", "provide", "reply",
    )

    /** A word allowed between the verb and what it asks for: "שלחו לנו את הקוד", "send us the code", "reply with". */
    private val DETAIL_FILLERS = setOf("לנו", "לי", "אלינו", "אליי", "כאן", "עכשיו", "מיד", "כעת", "us", "me", "with", "here", "now")

    /** What the request asks for. */
    private val DETAIL_OBJECTS = words(
        "את פרטי הכרטיס", "פרטי הכרטיס", "פרטי כרטיס", "פרטי כרטיס האשראי", "את פרטי כרטיס האשראי", "פרטי האשראי",
        "את פרטי האשראי", "פרטי אשראי", "את מספר הכרטיס", "מספר הכרטיס", "מספר כרטיס האשראי", "פרטי חשבון", "פרטי החשבון",
        "את פרטי החשבון", "פרטי חשבון הבנק", "את הסיסמה", "הסיסמה",
        "your card details", "your card", "card details", "your credit card", "credit card details", "your card number",
        "card number", "your cvv", "the cvv", "your bank details", "bank details", "your account details", "account details",
        "your password", "your pin",
    )

    /** A one-time code, asked for only with [HAND_OVER_VERBS]. */
    private val CODE_OBJECTS = words(
        "את הקוד", "הקוד", "קוד האימות", "את קוד האימות", "the code", "this code", "your code", "the otp", "your otp",
    )

    /** A no in front of the request: "אין למסור את הקוד", "לעולם אל ...", "never send this code", "do not". */
    private val NEGATIONS = setOf("אל", "לא", "אין", "אסור", "לעולם", "never", "not", "don't", "dont", "no")

    /** How many words in front of the verb a negation is looked for: "never share or send this code". */
    private const val NEGATION_REACH = 3

    /** An English verb after one of these is a statement, not a request: "we will send the code". */
    private val NOT_IMPERATIVE = setOf("we", "i", "will", "they", "you", "can", "may", "would", "should", "we'll", "i'll", "it")

    /** "מסרו את הקוד לשליח": the delivery's pickup code is handed to the courier, not to a thief. */
    private val TO_COURIER = setOf("לשליח", "לנהג", "courier", "driver")

    /** Chat links that move the conversation off SMS. */
    private val CHAT_HOSTS = setOf("wa.me", "t.me", "chat.whatsapp.com", "api.whatsapp.com", "whatsapp.com", "telegram.me", "telegram.org")

    /** A phone number in the text: Israeli (050-1234567, 03-1234567, +972...), a business star number (*1234), or foreign. */
    private val PHONE_IN_TEXT = Regex(
        "(?<![\\d+])(?:\\+972[-\\s]?|0)(?:[23489]|5\\d|7\\d)[-\\s]?\\d{3}[-\\s]?\\d{4}(?!\\d)" +
            "|(?<![\\d*])\\*\\d{4}(?!\\d)|(?<![\\d+])\\+(?!972)\\d[\\d\\s-]{7,16}\\d",
    )

    /** The words that ask to phone or to chat elsewhere. */
    private val CONTACT_WORDS = words(
        "התקשר", "התקשרו", "התקשרי", "חייג", "חייגו", "חייגי", "צור קשר", "צרו קשר", "ליצירת קשר", "פנה", "פנו", "לפרטים",
        "לבירורים", "שלחו הודעה", "שלח הודעה", "וואטסאפ", "ווטסאפ", "ווצאפ", "טלגרם", "whatsapp", "telegram", "call", "contact",
        "text us", "message us", "text me", "message me", "dial",
    )

    /** The words of drugs sold in a text. Slang that is also an everyday word is in [AMBIGUOUS_DRUG_WORDS]. */
    private val DRUG_WORDS = words(
        "קנאביס", "קאנביס", "קנביס", "מריחואנה", "גראס", "וויד", "חשיש", "טלגראס", "קוקאין", "אקסטזי", "קטמין",
        "cannabis", "marijuana", "weed", "thc", "cocaine", "ecstasy", "mdma", "lsd", "ketamine",
    )

    /** Slang that is also an everyday word ("ויד" is "ו" + "יד", "hash" is a sign): counted with a weight in grams or "discreet". */
    private val AMBIGUOUS_DRUG_WORDS = words("ויד", "hash")

    /** The wording of a hidden delivery. A drug word of its own, but it needs a sales cue other than the delivery. */
    private val DISCREET_WORDS = words(
        "משלוח דיסקרטי", "משלוחים דיסקרטיים", "דיסקרטיות מלאה", "discreet delivery", "discreet shipping",
    )

    /** What follows "weed" in the garden's: "weed killer", "weed control". */
    private val GARDEN_WORDS = setOf("killer", "control", "whacker", "wacker", "eater", "barrier", "spray", "removal", "puller")

    /** A weight: "5 ג'", "10 גרם", "3.5g", "1 oz". In the normalized text. */
    private val DRUG_WEIGHT = Regex("\\d+(?:[.,]\\d+)?\\s?(?:גרם|גר'|גר|ג'|gr|grams?|g|oz)(?![\\p{L}])")

    /** A sales cue besides the delivery: a price, an offer, an order, a purchase. A sum of money counts too. */
    private val SALES_WORDS = words(
        "מחיר", "מחירים", "מבצע", "מבצעים", "הנחה", "למכירה", "מכירה", "מלאי", "הזמנה", "הזמנות", "להזמנות", "להזמין",
        "הזמינו", "לקנות", "קנה", "קנו", "קניה", "קנייה", "קניות", "מתנה", "בונוס", "price", "prices", "sale", "order",
        "orders", "buy", "stock", "shop", "gift", "deal",
    )

    /** The delivery as a cue: with a drug word, "משלוחים" is a sales sign; beside "discreet" it is only its wording. */
    private val DELIVERY_WORDS = words("משלוח", "משלוחים", "שליח", "שליחויות", "delivery", "shipping", "courier")

    /** Latin letters from which the share of capitals is judged, and the share that counts as shouting. */
    private const val CAPS_MIN_LETTERS = 40
    private const val CAPS_SHARE = 0.7

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
        // The state lottery and the sports pools
        "lotto", "לוטו", "pais", "פיס", "mifalhapais", "מפעלהפיס", "toto", "טוטו",
        // News services, whose alerts quote the authorities ("פיקוד העורף: ...") with a link to the article
        "ynet", "mako", "walla", "וואלה", "haaretz", "הארץ", "kan", "כאן", "n12", "maariv", "מעריב", "israelhayom", "ישראלהיום",
    )

    /**
     * A bait or pressure phrase. [label] is what the UI shows; [variants] are the spellings looked for,
     * as whole words (Hebrew ones also with ה/ו/ב/ל/מ/ש/כ in front). [ad] marks wording that makes an
     * opt-out line an ad's ("מבצע", "זכית"); a call to action or the opt-out itself is not.
     */
    private class Phrase(
        val label: String,
        val points: Int,
        val variants: List<String>,
        val ad: Boolean = true,
        val optOut: Boolean = false,
        /** Pressure ("דחוף", "act now"): worth [URGENT_WITH_LINK] instead of [points] when the message has a link. */
        val urgent: Boolean = false,
    )

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
        phrase("package waiting", STRONG, "package is waiting", "package waiting", "parcel is waiting", "package on hold", "package is on hold", "parcel on hold", "parcel is on hold"),
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
        Phrase(
            "היום בלבד", MILD,
            listOf(
                "היום בלבד", "לזמן מוגבל", "דחוף", "מיידי", "מיידית", "באופן מיידי", "התראה אחרונה", "הודעה אחרונה",
                "אזהרה אחרונה", "תוך 24 שעות", "הזדמנות אחרונה",
            ),
            urgent = true,
        ),
        Phrase(
            "act now", MILD,
            listOf(
                "act now", "limited time", "today only", "urgent", "final notice", "final warning", "hurry", "immediately",
                "within 24 hours", "last chance", "expires today",
            ),
            urgent = true,
        ),
        phrase("מבצע", MILD, "מבצע", "הטבה", "הנחה", "חינם", "בונוס"),
        phrase("free", MILD, "free", "discount", "sale", "bonus", "offer"),
        // The wording of a sale (version 5)
        phrase(
            "מחיר מוזל", MILD, "מחיר מוזל", "במחיר מוזל", "מתנה בכל קניה", "מתנה בכל קנייה", "בכל קניה", "בכל קנייה", "במבצע",
            "מבצע מיוחד", "הנחה מיוחדת", "price drop", "special price", "free with every purchase",
        ),
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

        if (isForeignNumber(sms.address)) {
            score += FOREIGN_NUMBER
            reasons += SpamReason.ForeignNumber
        }

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
                phrasePoints += if (phrase.urgent && links.isNotEmpty()) URGENT_WITH_LINK else phrase.points
                reasons += SpamReason.Keyword(phrase.label)
                optOut = optOut || phrase.optOut
                adWording = adWording || phrase.ad
            }
        }
        // "פרסומת" at the start: the ad marker the law asks for, worth the same as the opt-out line and counted once with it
        if (FolderClassifier.hasAdMarker(sms.body)) {
            if (!optOut) {
                phrasePoints += MODERATE
                reasons += SpamReason.Keyword(if (Text.hasHebrew(sms.body)) "פרסומת" else "Ad")
            }
            optOut = true
        }
        score += minOf(phrasePoints, PHRASE_CAP)

        val bait = MONEY_BAIT_WORDS.any { Text.containsWords(words, it) } || SAVING_SUM.containsMatchIn(Text.normalize(sms.body))
        if (bait) {
            score += MONEY_BAIT
            reasons += SpamReason.MoneyBait
        }

        val service by lazy { isServiceNotice(sms, words) }

        if (impersonatesAuthority(sms, words, links)) {
            score += GOV_IMPERSONATION
            reasons += SpamReason.GovImpersonation
        }
        val investment = investmentPoints(sms.body, words)
        if (investment > 0) {
            score += investment
            reasons += SpamReason.Investment
        }
        if (asksForDetails(words) && !isOwnBrandRequest(sms, links)) {
            score += ASKS_FOR_DETAILS
            reasons += SpamReason.AsksForDetails
        }
        if (!isKnownBrand(sms) && !isGovSender(sms.address) && !service && sellsDrugs(sms.body, words, links)) {
            score += DRUGS
            reasons += SpamReason.Drugs
        }

        // The shape of the message: mild, capped, and only from a stranger: never for a known brand's or an authority's
        // id, nor for a service notice (a bank's charge names its sum, a clinic its fee)
        if (!service && !isKnownBrand(sms) && !isGovSender(sms.address)) {
            var structure = 0
            if (FolderClassifier.hasAmount(sms.body)) {
                structure += STRUCTURE
                reasons += SpamReason.MoneyAmount
            }
            if (redirectsToOtherNumber(sms.address, sms.body, words, links)) {
                // In place of the 10 points of "asks to chat elsewhere", and outside the cap of the shape
                score += REDIRECT_NUMBER
                reasons += SpamReason.RedirectNumber
            } else if (asksToContactElsewhere(sms.body, words, links)) {
                structure += STRUCTURE
                reasons += SpamReason.ContactElsewhere
            }
            if (isShouting(sms.body)) {
                structure += STRUCTURE
                reasons += SpamReason.AllCaps
            }
            score += minOf(structure, STRUCTURE_CAP)
        }

        if (optOut) {
            val stranger = Text.phoneKey(sms.address) != null
            val unknownId = Text.isAlphanumericSender(sms.address) && !isKnownBrand(sms)
            val advertises = poll > 0 ||
                (stranger && (links.isNotEmpty() || adWording || bait)) ||
                (unknownId && links.isNotEmpty() && (adWording || bait))
            if (advertises && !service) score += OPT_OUT_AD
        }

        val blocked = sms.blockedKeywords.filter { it.isNotBlank() && Text.containsPhrase(words, it) }
        if (blocked.isNotEmpty()) {
            score = maxOf(score, BLOCKED_SCORE)
            reasons.addAll(0, blocked.map { SpamReason.BlockedKeyword(it.trim()) })
        }
        return SpamVerdict(score, reasons)
    }

    /** A phone number with a country code other than Israel's: "+1 555...", "0044 7700...". Not a short code or a sender id. */
    private fun isForeignNumber(address: String): Boolean = Text.phoneKey(address)?.startsWith("+") == true

    /**
     * An authority's name with a request to click or pay that the authority would not send:
     * - "פיקוד העורף" with a link to download an app, from any sender: it never sends these;
     * - the authority ([AUTHORITY_WORDS]) with a link off [GOV_DOMAINS] and a request to pay, update, collect a refund
     *   or install ([AUTHORITY_ACTIONS]), or with a request to pay and no link from a sender that is not an authority;
     * - a fine or a ticket ([DEBT_WORDS]) with a request to pay through a link off [GOV_DOMAINS], except from a
     *   municipality's own id.
     * A known brand, bank, insurer or courier that names an authority ("לפי הנחיות פיקוד העורף") is left to its own
     * signs ([SpamReason.BrandSender] judges its links). A genuine authority's id ([isGovSender]) is judged by its links.
     */
    private fun impersonatesAuthority(sms: IncomingSms, words: List<String>, links: List<LinkInfo>): Boolean {
        val authority = AUTHORITY_WORDS.any { Text.containsWords(words, it) }
        val debt = DEBT_WORDS.any { Text.containsWords(words, it) }
        if (!authority && !debt) return false
        val homeFront = HOME_FRONT.any { Text.containsWords(words, it) }
        if (links.isNotEmpty() && homeFront && DOWNLOAD_WORDS.any { Text.containsWords(words, it) }) return true
        val govSender = isGovSender(sms.address)
        if (!govSender && isKnownBrand(sms)) return false
        val strayLink = links.any { link -> GOV_DOMAINS.none { link.host == it || link.host.endsWith(".$it") } }
        val payAsk = PAY_WORDS.any { Text.containsWords(words, it) }
        if (authority && strayLink && (payAsk || AUTHORITY_ACTIONS.any { Text.containsWords(words, it) })) return true
        if (authority && links.isEmpty() && payAsk && !govSender) return true
        val municipal = Text.isAlphanumericSender(sms.address) && MUNICIPAL_WORDS.any { Text.containsWords(words, it) }
        return debt && payAsk && strayLink && !municipal
    }

    /** The sender id of an authority or a toll road ([GOV_SENDER_MARKS], [GOV_SENDER_NAMES]). Never a phone number. */
    private fun isGovSender(address: String): Boolean {
        if (!Text.isAlphanumericSender(address)) return false
        val compact = Text.words(address.replace('-', ' ').replace('.', ' ')).joinToString("")
        return compact in GOV_SENDER_NAMES || GOV_SENDER_MARKS.any { compact.contains(it) }
    }

    /**
     * [INVESTMENT] for an investment or crypto word, [INVESTMENT_GROUP] when "join our group" comes with it, and
     * [INVESTMENT] for "join our group" with a promise of profit or a sum ("הצטרפו לקבוצה והרוויחו 2,000 ₪ ביום"). A
     * residents' committee's "הצטרפו לקבוצה של הבניין" has neither.
     */
    private fun investmentPoints(body: String, words: List<String>): Int {
        val invest = INVESTMENT_WORDS.any { Text.containsWords(words, it) }
        val group = GROUP_JOIN.any { Text.containsWords(words, it) }
        return when {
            invest && group -> INVESTMENT_GROUP
            invest -> INVESTMENT
            group && (PROFIT_WORDS.any { Text.containsWords(words, it) } || FolderClassifier.hasAmount(body)) -> INVESTMENT
            else -> 0
        }
    }

    /**
     * A request for card details, an account's details, a password or a code: an imperative verb ([DETAIL_VERBS]),
     * at most one filler word, then what it asks for ([DETAIL_OBJECTS], or [CODE_OBJECTS] after a verb that hands it
     * over). Not when a negation stands up to
     * [NEGATION_REACH] words before ("אין למסור את הקוד לאף אחד", "never send this code"), not an English statement
     * ("we will send the code"), and not a pickup code handed to the courier ("מסרו את הקוד לשליח").
     */
    private fun asksForDetails(words: List<String>): Boolean {
        for (i in words.indices) {
            if (DETAIL_VERBS.none { Text.matchesAt(words, i, it) }) continue
            if ((maxOf(0, i - NEGATION_REACH) until i).any { words[it] in NEGATIONS }) continue
            if (i > 0 && words[i - 1] in NOT_IMPERATIVE) continue
            val start = if (i + 1 < words.size && words[i + 1] in DETAIL_FILLERS) i + 2 else i + 1
            val handsOver = HAND_OVER_VERBS.any { Text.matchesAt(words, i, it) }
            val target = DETAIL_OBJECTS.firstOrNull { Text.matchesAt(words, start, it) }
                ?: CODE_OBJECTS.firstOrNull { handsOver && Text.matchesAt(words, start, it) }
                ?: continue
            val after = start + target.size
            if (words.getOrNull(after) in TO_COURIER || words.getOrNull(after + 1) in TO_COURIER) continue
            return true
        }
        return false
    }

    /**
     * A known brand asking for its own customer's card ("Cellcom: יש לעדכן את פרטי הכרטיס באתר"), with no link or only
     * links to its own domains. A link elsewhere is the scam's ([SpamReason.BrandSender] too).
     */
    private fun isOwnBrandRequest(sms: IncomingSms, links: List<LinkInfo>): Boolean {
        if (!isKnownBrand(sms)) return false
        if (links.isEmpty()) return true
        val brand = LinkAnalyzer.brandNamed(sms.address) ?: return false
        return links.all { LinkAnalyzer.isGenuineHost(brand, it.host) }
    }

    /** A chat link (wa.me, t.me, a WhatsApp group), or a phone number in the text with a word asking to call or write. */
    private fun asksToContactElsewhere(body: String, words: List<String>, links: List<LinkInfo>): Boolean {
        if (links.any { link -> CHAT_HOSTS.any { link.host == it || link.host.endsWith(".$it") } }) return true
        return PHONE_IN_TEXT.containsMatchIn(body) && CONTACT_WORDS.any { Text.containsWords(words, it) }
    }

    /**
     * A link or a number in the text that is not the sender's own number: "wa.me/9725..." or "api.whatsapp.com/send?phone="
     * with another number, any "t.me" link (a username, never the sender's number), or a mobile number (05x, +972 5x,
     * or from abroad) next to a word that asks to call or write. A chat link with no number in it (a group invitation)
     * is [asksToContactElsewhere]'s alone. Numbers compare by digits, +972 and 0 alike.
     */
    private fun redirectsToOtherNumber(address: String, body: String, words: List<String>, links: List<LinkInfo>): Boolean {
        val own = Text.phoneKey(address)
        for (link in links) {
            val host = link.host
            if (host == "t.me" || host.endsWith(".t.me") || host == "telegram.me") return true
            if (host == "wa.me" || host == "api.whatsapp.com" || host.endsWith(".wa.me")) {
                val digits = WA_NUMBER.find(link.text)?.groupValues?.get(1) ?: continue
                if (Text.phoneKey(if (digits.startsWith("0")) digits else "+$digits") != own) return true
            }
        }
        if (CONTACT_WORDS.none { Text.containsWords(words, it) }) return false
        // The links are left out: a number inside "wa.me/9725..." was judged above
        val text = StringBuilder(body)
        for (l in links) for (i in l.range) if (i < text.length) text.setCharAt(i, ' ')
        return MOBILE_IN_TEXT.findAll(text).any { Text.phoneKey(it.value.trim()) != own }
    }

    /** The number of a "wa.me/972501234567" or "api.whatsapp.com/send?phone=972501234567" link. */
    private val WA_NUMBER = Regex("(?:wa\\.me/|[?&]phone=)\\+?(\\d{7,15})", RegexOption.IGNORE_CASE)

    /** A mobile number in the text (050-1234567, +972 54 123 4567) or one from abroad. Not a landline: a shop's own line. */
    private val MOBILE_IN_TEXT = Regex(
        "(?<![\\d+])(?:\\+972[-\\s]?|0)5\\d[-\\s]?\\d{3}[-\\s]?\\d{4}(?!\\d)|(?<![\\d+])\\+(?!972)\\d[\\d\\s-]{7,16}\\d",
    )

    /**
     * Drugs offered for sale: a drug word ([DRUG_WORDS], or slang in [AMBIGUOUS_DRUG_WORDS] with a weight or a hidden
     * delivery) together with a sales cue: a price or an offer, a sum, an order, a purchase, a delivery, a chat link or a
     * shortened one, or a call to move elsewhere. A hidden-delivery phrase ([DISCREET_WORDS]) needs a cue besides the
     * delivery. A mention with no cue ("ראית את הכתבה על קנאביס רפואי?") is a word, and a plain link (an article) is no cue.
     * A known brand, a service notice and an authority are left out by the caller.
     */
    private fun sellsDrugs(body: String, words: List<String>, links: List<LinkInfo>): Boolean {
        val discreet = DISCREET_WORDS.any { Text.containsWords(words, it) }
        // "weed killer" is the garden's: the word is left out when a gardening word follows it
        val words = words.filterIndexed { i, w -> !(w == "weed" && words.getOrNull(i + 1) in GARDEN_WORDS) }
        val weight = DRUG_WEIGHT.containsMatchIn(Text.normalize(body))
        val drug = DRUG_WORDS.any { Text.containsWords(words, it) } ||
            (AMBIGUOUS_DRUG_WORDS.any { Text.containsWords(words, it) } && (weight || discreet))
        if (!drug && !discreet) return false
        val chat = asksToContactElsewhere(body, words, links) || links.any { it.isShortener }
        val sales = SALES_WORDS.any { Text.containsWords(words, it) } || FolderClassifier.hasAmount(body) || chat
        return if (drug) sales || DELIVERY_WORDS.any { Text.containsWords(words, it) } else sales
    }

    /** At least [CAPS_MIN_LETTERS] Latin letters, [CAPS_SHARE] of them capitals: "URGENT YOUR ACCOUNT IS LOCKED ...". */
    private fun isShouting(body: String): Boolean {
        val latin = body.filter { it in 'a'..'z' || it in 'A'..'Z' }
        return latin.length >= CAPS_MIN_LETTERS && latin.count { it.isUpperCase() } >= latin.length * CAPS_SHARE
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
            CodeExtractor.extract(sms.body, sms.address, knownPerson = sms.isContact || sms.userWrote) != null ||
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

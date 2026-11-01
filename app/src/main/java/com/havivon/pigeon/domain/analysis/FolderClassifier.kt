package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.Folder

/**
 * Sorts a message into one of the automatic folders. The order is the priority, first match wins:
 *
 * 1. [Folder.Spam]: the [SpamScorer] verdict says spam.
 * 2. [Folder.Codes]: [CodeExtractor] finds a one-time code. A bank's "קוד 123456 לאישור חיוב" is a code first.
 * 3. [Folder.Banks]: the sender is a known bank, card company or payment app, or the text is shaped like a
 *    transaction: an amount of money together with a card ending, money that moved (charged, withdrawn,
 *    deposited), or the balance of an account.
 * 4. [Folder.Deliveries]: the sender is a known courier, or the text is shaped like a shipment update: a
 *    tracking number, or the user's package or order together with what happened to it.
 * 5. [Folder.Personal]: the sender is a saved contact or an ordinary phone number.
 *
 * Anything else (an advertiser, a service with an alphanumeric id) stays out of the automatic folders and
 * is returned as [Folder.All], the folder that holds every conversation. A lone word never decides: an ad
 * that says "כרטיס" (a ticket, too), "יתרה", "חיוב", "אשראי" or "משלוח חינם" is neither a bank nor a delivery.
 * The text rules of 3 and 4 are skipped for saved contacts: a friend writing "the package arrived" is still
 * personal. A bank or courier saved as a contact is still recognised by its name.
 */
object FolderClassifier {

    /** Classifies [sms]. Pass [spam] when the verdict is already computed, to avoid scoring twice. */
    fun classify(sms: IncomingSms, spam: SpamVerdict = SpamScorer.score(sms)): Folder {
        if (spam.isSpam) return Folder.Spam
        if (CodeExtractor.extract(sms.body) != null) return Folder.Codes
        val name = sms.senderName?.let { Text.words(it) }.orEmpty()
        val address = Text.words(sms.address)
        // A contact named "Max" is a person: the names that are also everyday words count only for a sender id
        if (isBank(address, plainNames = true) || isBank(name, plainNames = !sms.isContact)) return Folder.Banks
        if (isCourier(address) || isCourier(name)) return Folder.Deliveries
        if (!sms.isContact) {
            val normalized = Text.normalize(sms.body)
            val words = Text.words(sms.body)
            if (isTransaction(normalized, words)) return Folder.Banks
            if (isShipmentUpdate(sms.body, normalized, words)) return Folder.Deliveries
        }
        if (sms.isContact || Text.phoneKey(sms.address) != null) return Folder.Personal
        return Folder.All
    }

    /** Keyword phrases, split into words once rather than for every message. */
    private fun phrases(vararg phrase: String): List<List<String>> = phrase.map { Text.words(it) }

    // --- banks by sender ---

    /*
     * The institutions recognised by name, in Hebrew and as the sender ids they use:
     * - Israeli banks: Leumi, Hapoalim (Poalim), Discount, Mizrahi-Tefahot, the First International (FIBI,
     *   Beinleumi), Yahav, Mercantile, Jerusalem, Massad, Otsar Hahayal, One Zero, Pepper (Leumi's digital bank);
     * - card companies: Isracard, Max (formerly Leumi Card), Cal (Visa Cal), American Express, Diners,
     *   Mastercard, Visa;
     * - payment apps that move the user's money: bit, PayBox, PayPal;
     * - international banks: Chase, Citi, HSBC, Barclays, Wells Fargo, Revolut; and any sender with the
     *   word "bank" or "בנק" of its own.
     */

    /**
     * Distinctive names, found anywhere in the sender written as one run ("BankHapoalim", "Isracard-IL",
     * "בנק מזרחי טפחות"). None of them is an everyday word or a common surname.
     */
    private val BANK_MARKS = listOf(
        "bankleumi", "leumicard", "hapoalim", "bankpoalim", "discountbank", "bankdiscount", "tefahot", "tfahot",
        "beinleumi", "fibibank", "bankyahav", "mercantile", "bankjerusalem", "bankmassad", "otsarhahayal",
        "otzarhahayal", "onezerobank", "isracard", "americanexpress", "dinersclub", "mastercard", "paybox", "paypal",
        "citibank", "wellsfargo", "barclays", "revolut",
        "בנקלאומי", "הפועלים", "בנקדיסקונט", "טפחות", "הבינלאומי", "בנקיהב", "מרכנתיל", "בנקירושלים", "בנקמסד",
        "אוצרהחייל", "וואןזירו", "ישראכרט", "אמריקןאקספרס", "דיינרס", "מאסטרקארד", "פייבוקס",
    )

    /**
     * Names that are also ordinary words, places, shops or surnames ("Max Stock", "Leumit", "ביטוח לאומי",
     * "Dan Mizrahi"): they count only when they are the whole sender, or the whole sender but for words
     * of [BANK_AFFIXES] ("Max-IT", "Visa Cal", "Leumi Card").
     */
    private val BANK_NAMES = setOf(
        "leumi", "poalim", "discount", "mizrahi", "fibi", "yahav", "jerusalem", "massad", "onezero", "pepper",
        "max", "cal", "visa", "amex", "diners", "bit", "chase", "citi", "hsbc",
        "לאומי", "דיסקונט", "מזרחי", "יהב", "ירושלים", "מסד", "פפר", "מקס", "כאל", "ויזה", "ביט",
    )

    private val BANK_AFFIXES = setOf("card", "cards", "il", "it", "isr", "israel", "visa", "cal", "כרטיסי", "אשראי", "ויזה", "כאל")

    /** A word of its own that makes the sender a bank: "Bank of X", "בנק X". */
    private val BANK_WORDS = setOf("bank", "בנק", "הבנק")

    /** Whether the sender's words name a bank; [plainNames] lets the names in [BANK_NAMES] count too. */
    private fun isBank(senderWords: List<String>, plainNames: Boolean): Boolean {
        if (senderWords.isEmpty()) return false
        if (senderWords.any { it in BANK_WORDS }) return true
        val compact = senderWords.joinToString("")
        if (BANK_MARKS.any { compact.contains(it) }) return true
        if (!plainNames) return false
        val rest = senderWords.filter { it !in BANK_AFFIXES }
        // "Visa Cal": every word an affix, and one of them a name
        if (rest.isEmpty()) return senderWords.any { it in BANK_NAMES }
        return rest.size == 1 && rest[0] in BANK_NAMES
    }

    // --- banks by content ---

    private const val CURRENCY = "(?:₪|ש\"ח|שח|שקלים|שקל|nis|ils|usd|eur|gbp|\\$|€|£|דולר|דולרים|יורו|אירו)"
    private const val NUMBER = "\\d[\\d,]*(?:\\.\\d+)?"

    /** An amount of money, in the normalized text: "150 ש"ח", "₪3,200", "$45.00", "45.00 usd". */
    private val AMOUNT = Regex("(?<![\\p{L}])$CURRENCY\\s?-?\\s?$NUMBER|$NUMBER\\s?$CURRENCY(?![\\p{L}])")

    /** "בכרטיס המסתיים ב-4821", "card ending in 4821", "x4821", "****4821". */
    private val CARD_ENDING = Regex(
        "(?:המסתיים|שמסתיים|המסתיימת|שמסתיימת|מסתיים|מסתיימת|שסיומו|שסיומה)\\s*(?:בספרות|ב)?\\s*-?\\s*\\d{4}(?!\\d)" +
            "|ending(?:\\s+(?:in|with))?\\s*[x*•]*\\s*\\d{4}(?!\\d)|(?<![\\p{L}\\d])(?:x{1,4}|\\*{2,}|•{2,})\\d{4}(?!\\d)",
    )

    /**
     * Money that moved, written as done. Not an offer ("ללא חיוב", "עסקה משתלמת"), and not the words that
     * have an everyday meaning too ("הזוכה" is the winner, "המבצע נמשך" is a sale that goes on).
     */
    private val MONEY_MOVED = phrases(
        "חויב", "חויבה", "חויבת", "חויבו", "זוכית", "הופקד", "הופקדה", "הופקדו", "זוכה חשבונך", "חשבונך זוכה", "משיכת מזומן",
        "בוצע חיוב", "בוצעה עסקה", "בוצעה משיכה", "בוצעה הפקדה", "בוצעה העברה", "בוצע זיכוי",
        "חיוב בסך", "עסקה בסך", "משיכה בסך", "הפקדה בסך", "זיכוי בסך", "העברה בסך", "עסקה בכרטיס", "חיוב בכרטיס",
        "debited", "credited", "withdrawn", "deposited", "transaction", "was charged", "were charged", "has been charged",
        "have been charged", "charged to your", "withdrawal of", "deposit of", "payment received",
    )

    private val BALANCE = phrases("יתרה", "יתרת", "יתרתך", "balance")

    /** The user's bank account; not a shop's points or gift card, nor a phone bill ("יתרת החשבון"). */
    private val ACCOUNT = phrases("חשבונך", "חשבונכם", "בחשבון", "לחשבון", "מחשבון", "עו\"ש", "account", "checking", "savings")

    /** Words that only mean money moved when the user's account is named too: "נמשכו 500 ₪ מחשבונך". */
    private val MOVED_WITH_ACCOUNT = phrases(
        "נמשך", "נמשכו", "הועבר", "הועברו", "התקבל", "התקבלו", "שולם", "שולמו", "הוחזר", "הוחזרו",
        "transferred", "received", "sent", "paid", "refunded",
    )

    private fun isTransaction(normalized: String, words: List<String>): Boolean {
        if (!AMOUNT.containsMatchIn(normalized)) return false
        if (CARD_ENDING.containsMatchIn(normalized)) return true
        if (MONEY_MOVED.any { Text.containsWords(words, it) }) return true
        if (ACCOUNT.none { Text.containsWords(words, it) }) return false
        return BALANCE.any { Text.containsWords(words, it) } || MOVED_WITH_ACCOUNT.any { Text.containsWords(words, it) }
    }

    // --- deliveries ---

    /** Courier names found anywhere in the sender written as one run ("IsraelPost", "DHL-Express"). */
    private val COURIER_MARKS = listOf("israelpost", "cheetah", "fedex", "aramex", "dhl", "usps", "boxit", "דוארישראל", "צ'יטה", "ארמקס")

    /** Courier names that count as a word of their own: "UPS" but not "Groups", "וולט" but not "וולטר". */
    private val COURIER_WORDS = setOf("ups", "wolt", "hfd", "courier", "postal", "דואר", "וולט", "בלדר", "בלדרות", "שליח", "שליחויות")

    private fun isCourier(senderWords: List<String>): Boolean {
        if (senderWords.isEmpty()) return false
        val compact = senderWords.joinToString("")
        return COURIER_MARKS.any { compact.contains(it) } || senderWords.any { it in COURIER_WORDS }
    }

    /** A postal tracking number, as written ("RR123456789IL"). */
    private val POSTAL_ID = Regex("(?<![A-Za-z0-9])[A-Z]{2}\\d{9}[A-Z]{2}(?![A-Za-z0-9])")

    /** A tracking number named as such, in the normalized text: "מספר מעקב: 5501234", "tracking number 1Z999". */
    private val NAMED_TRACKING = Regex("(?:מספר|מס'|מס)\\s*(?:ה)?מעקב\\s*:?\\s*[a-z0-9-]{6,}|tracking\\s*(?:number|no\\.?|#|id)?\\s*:?\\s*[a-z0-9-]*\\d[a-z0-9-]{5,}")

    /** The user's own shipment: definite or "yours", as an update is written (an ad says "משלוח חינם"). */
    private val SHIPMENT = phrases(
        "החבילה", "המשלוח", "ההזמנה", "השליח", "חבילה עבורך", "חבילה ממתינה", "משלוח עבורך",
        "your package", "your parcel", "your shipment", "your order", "your delivery", "the courier", "your courier",
    )

    /** What happened to it, in the past or the present as an update says it (an ad promises the future). */
    private val SHIPMENT_STATUS = phrases(
        "יצא", "יצאה", "יוצא", "יוצאת", "נשלח", "נשלחה", "נמסר", "נמסרה", "הגיע", "הגיעה", "ממתין", "ממתינה", "מוכן", "מוכנה",
        "בדרך", "לאיסוף", "נאסף", "נאספה", "בלוקר", "בסניף", "בנקודת",
        "shipped", "dispatched", "delivered", "out for delivery", "on its way", "on the way", "arrived",
        "ready for pickup", "ready for collection", "awaiting pickup", "in transit", "picked up",
    )

    private fun isShipmentUpdate(body: String, normalized: String, words: List<String>): Boolean {
        if (POSTAL_ID.containsMatchIn(body) || NAMED_TRACKING.containsMatchIn(normalized)) return true
        return SHIPMENT.any { Text.containsWords(words, it) } && SHIPMENT_STATUS.any { Text.containsWords(words, it) }
    }
}

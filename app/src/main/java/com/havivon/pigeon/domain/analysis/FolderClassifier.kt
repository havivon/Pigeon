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
 * 4. [Folder.Insurance]: the sender is a known insurer (by its name in the list, never for a word such as "ביטוח"
 *    or "insurance" in an unknown sender: "BituaCheck" and "Bituach4U" are advertisers), or the text
 *    is shaped like the user's own policy: a policy or claim number, or a kind of insurance ("ביטוח רכב",
 *    "הפוליסה", "health insurance") together with what happened to it (renewed, expires, approved, received).
 *    Never an ad: a text with an opt-out line or with offer words ("הצעת מחיר", "השוו", "quote") is not.
 * 5. [Folder.Telecom]: the sender is a known telecom company (HOT, Partner, Pelephone, Cellcom, Bezeq...), or the text is a
 *    line's notice (the browsing settings, a technician's visit, an unpaid line's bill) that names a telecom company or a line.
 * 6. [Folder.Health]: the sender is a health fund, hospital or ambulance service, or the text is an appointment, a prescription,
 *    a test or a sick note. A health fund's feedback form or perk is not a notice.
 * 7. [Folder.Bills]: the sender is a utility, a water corporation or a municipality, or the text is a bill (a consumer number, a
 *    debt, a fee, "תושבים יקרים") or a payment confirmation.
 * 8. [Folder.Transport]: the sender is a parking app, Rav-Kav, a toll road, a taxi app, a railway or bus operator, a tracker
 *    company or the Ministry of Transport ("mot", whose notices are the vehicle's licence and test), or the text is a parking,
 *    ride or vehicle-test notice.
 * 9. [Folder.Travel]: the sender is an airline, a hotel chain or a booking site, or the text is a flight, a check-in or a hotel booking.
 * 10. [Folder.Shopping]: the sender is a known shop, restaurant or pharmacy chain (its shipment update is a Delivery), or the text
 *    is a receipt, an invoice, an order confirmation or a voucher.
 * 11. [Folder.Deliveries]: the sender is a known courier, or the text is shaped like a shipment update: a
 *    tracking number, or the user's package or order together with what happened to it.
 * 12. [Folder.Personal]: the sender is a saved contact, or someone the user wrote to. An unknown phone number
 *    is not personal (without the contacts permission only the people written to are).
 *
 * Why this order: the more specific institution wins over the more general one. A bank's charge is a bank's even when it names
 * a shop; a pharmacy's prescription is Health although its sender is a shop (the sender is only the weakest sign); a shop's "your
 * order was shipped" is a delivery, but its receipt is Shopping; a courier's update stays a delivery. Spam and codes come first
 * because they are the user's safety and the one-tap copy.
 *
 * An ad (a message with an opt-out line: "הסר", "להסרה", "STOP", "unsubscribe", or opening with the ad marker "פרסומת", "Ad:", both
 * of which Israeli law requires only in advertising) is filed by its sender alone: from a known institution's sender id (the
 * Phoenix, Cellcom, Shufersal...) it stays in that institution's category, so the conversation does not leave it whenever the
 * latest message is an ad; its text never decides, so an advertiser's ad that says "ביטוח" or "סלולר" goes to [Folder.All] (or
 * to [Folder.Spam] when the scorer says so). A code with an opt-out line is still a code.
 *
 * The senders are recognised first (banks, then insurers, then couriers), then the shapes of the text in
 * the same order. Anything else (an advertiser, a service with an alphanumeric id, a stranger's number) stays
 * out of the automatic folders and is returned as [Folder.All], the folder that holds every conversation. A lone
 * word never decides: an ad that says "כרטיס" (a ticket, too), "יתרה", "חיוב", "אשראי", "ביטוח" or "משלוח חינם"
 * is neither a bank, an insurer nor a delivery. The text rules are skipped for saved contacts and the people the
 * user wrote to: a friend writing "the package arrived" is still personal. A bank, insurer or courier saved as a
 * contact is still recognised by its name.
 */
object FolderClassifier {

    /** Classifies [sms]. Pass [spam] when the verdict is already computed, to avoid scoring twice. */
    fun classify(sms: IncomingSms, spam: SpamVerdict = SpamScorer.score(sms)): Folder {
        if (spam.isSpam) return Folder.Spam
        if (CodeExtractor.extract(sms.body, sms.address, knownPerson = sms.isContact || sms.userWrote) != null) return Folder.Codes
        val known = sms.isContact || sms.userWrote
        val service = serviceFolder(sms)
        if (service != null) return if (isAd(sms.body, Text.words(sms.body))) senderFolder(sms) ?: Folder.All else service
        return if (known) Folder.Personal else Folder.All
    }

    /**
     * The service folder [sms] is shaped for, whether or not it is an ad: [Folder.Banks], [Folder.Insurance], [Folder.Telecom],
     * [Folder.Health], [Folder.Bills], [Folder.Transport], [Folder.Travel], [Folder.Shopping] or [Folder.Deliveries] by its
     * sender or the shape of its text, or null. [SpamScorer] asks it so that a real
     * institution's notice with an opt-out line is not taken for a stranger's ad.
     */
    /**
     * The folder of [sms] by its sender alone (a known bank, insurer, service, courier or shop), or null: what an ad is filed
     * by (see the class comment). A saved contact counts only by the distinctive names, as in [serviceFolder].
     */
    private fun senderFolder(sms: IncomingSms): Folder? {
        val name = sms.senderName?.let { Text.words(it) }.orEmpty()
        val address = Text.words(sms.address)
        if (isBank(address, plainNames = true) || isBank(name, plainNames = !sms.isContact)) return Folder.Banks
        if (isInsurer(address, plainNames = true) || isInsurer(name, plainNames = !sms.isContact)) return Folder.Insurance
        ServiceCategories.folderBySender(address, name, sms.isContact, Text.words(sms.body))?.let { return it }
        if (isCourier(address) || isCourier(name)) return Folder.Deliveries
        return if (ServiceCategories.isShoppingSender(address, name, sms.isContact)) Folder.Shopping else null
    }

    internal fun serviceFolder(sms: IncomingSms): Folder? {
        val name = sms.senderName?.let { Text.words(it) }.orEmpty()
        val address = Text.words(sms.address)
        // A contact named "Max" is a person: the names that are also everyday words count only for a sender id
        if (isBank(address, plainNames = true) || isBank(name, plainNames = !sms.isContact)) return Folder.Banks
        if (isInsurer(address, plainNames = true) || isInsurer(name, plainNames = !sms.isContact)) return Folder.Insurance
        val words = Text.words(sms.body)
        ServiceCategories.folderBySender(address, name, sms.isContact, words)?.let { return it }
        if (isCourier(address) || isCourier(name)) return Folder.Deliveries
        val shop = ServiceCategories.isShoppingSender(address, name, sms.isContact)
        if (sms.isContact || sms.userWrote) return if (shop) Folder.Shopping else null
        val normalized = Text.normalize(sms.body)
        if (isTransaction(normalized, words)) return Folder.Banks
        if (isInsuranceNotice(normalized, words)) return Folder.Insurance
        ServiceCategories.folderByText(words)?.let { return it }
        val shipment = isShipmentUpdate(sms.body, normalized, words)
        // A shop's own "your order was shipped" is a delivery; anything else from a shop, and a receipt from anyone, is shopping
        if (shop) return if (shipment) Folder.Deliveries else Folder.Shopping
        if (ServiceCategories.isShoppingNotice(words)) return Folder.Shopping
        return if (shipment) Folder.Deliveries else null
    }

    /**
     * Whether the sender id (or a saved contact's name) is one of the known banks, insurers or couriers, or a known telecom
     * company, health fund, utility, transport service, airline or shop ([ServiceCategories]).
     */
    internal fun isInstitution(sms: IncomingSms): Boolean {
        val address = Text.words(sms.address)
        val name = sms.senderName?.let { Text.words(it) }.orEmpty()
        return ServiceCategories.isKnownSender(address, name) || isBank(address, plainNames = true) || isInsurer(address, plainNames = true) || isCourier(address) ||
            isBank(name, plainNames = false) || isInsurer(name, plainNames = false) || isCourier(name)
    }

    /** The opt-out line, which Israeli law requires only in advertising. */
    private val OPT_OUT = phrases(
        "הסר", "הסרה", "להסרה", "reply stop", "text stop", "send stop", "stop to opt out", "stop to end", "unsubscribe", "opt out",
    )

    /**
     * Whether the message is an ad: its [words] carry an opt-out line, or its [body] opens with the ad marker
     * ([hasAdMarker]). Both are what Israeli law (section 30A of the Communications Law) requires of advertising.
     */
    internal fun isAd(body: String, words: List<String>): Boolean = OPT_OUT.any { Text.containsWords(words, it) } || hasAdMarker(body)

    /**
     * "פרסומת" as one of the first three words ("פרסומת: מבצע...", "שופרסל - פרסומת"), or "פרסום" / "Ad" /
     * "Advertisement" as the very first word followed by ":", "-", "|", ")" or "]" ("(פרסום)", "Ad: 20% off"). The
     * law asks every ad to open with "פרסומת"; the shorter "פרסום" and "Ad" are everyday words, so they count only
     * as such a label. Elsewhere in the text the word is just a word ("ראיתי פרסומת בטלוויזיה").
     */
    internal fun hasAdMarker(body: String): Boolean {
        val normalized = Text.normalize(body)
        return AD_MARKER.containsMatchIn(normalized) || AD_LABEL.containsMatchIn(normalized)
    }

    private val AD_MARKER = Regex("^[^\\p{L}\\p{N}]*(?:[\\p{L}\\p{N}'\"]+[^\\p{L}\\p{N}]+){0,2}פרסומת(?![\\p{L}\\p{N}])")

    private val AD_LABEL = Regex("^[^\\p{L}\\p{N}]*(?:פרסום|ad|advert|advertisement)\\s*[:\\-|)\\]]")

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
     * - international banks: Chase, Citi, HSBC, Barclays, Wells Fargo, Revolut.
     * Only these names: a sender with the word "bank" or "בנק" and no known name ("BankCheck", "Bank Deals") is
     * not a bank.
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

    private val BANK_AFFIXES = setOf(
        "card", "cards", "il", "it", "isr", "israel", "visa", "cal", "bank", "בנק", "כרטיסי", "אשראי", "ויזה", "כאל",
    )

    /** Whether the sender's words name a bank; [plainNames] lets the names in [BANK_NAMES] count too. */
    private fun isBank(senderWords: List<String>, plainNames: Boolean): Boolean {
        if (senderWords.isEmpty()) return false
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

    /** Whether [body] names an amount of money ("150 ש"ח", "₪3,200", "$45", "20 NIS"). */
    internal fun hasAmount(body: String): Boolean = AMOUNT.containsMatchIn(Text.normalize(body))

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
        "בוצע תשלום", "התקבל תשלום", "התקבלה תשלום", "בקשת תשלום", "חיוב בסך", "עסקה בסך", "משיכה בסך", "הפקדה בסך", "זיכוי בסך", "העברה בסך", "עסקה בכרטיס", "חיוב בכרטיס",
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

    // --- insurance by sender ---

    /*
     * The insurers recognised by name, in Hebrew and as the sender ids they use:
     * - Israeli insurers: Harel, Migdal, Clal, the Phoenix (FNX), Menora Mivtachim, Ayalon, Bituach Yashir
     *   (Direct Insurance), AIG, Shomera, Hachshara, Libra, Wesure, 9 Million (9000000), Shirbit, Dikla;
     * - National Insurance (ביטוח לאומי, BTL), the state's: its messages are about the user's own benefits;
     * - international: Allianz, AXA, Generali, Zurich, Lemonade.
     * Only these names: "ביטוח", "bituach" or "insurance" in an unknown sender ("BituaCheck", "Bituach4U",
     * "Insure-Now") is an advertiser's id, not an insurer's.
     */

    /**
     * Distinctive names, found anywhere in the sender written as one run ("HarelIns", "BituachYashir", "הראל ביטוח").
     * Each is a whole insurer's name, never a word an advertiser uses alone ("ביטוח", "bituach", "insurance").
     */
    private val INSURER_MARKS = listOf(
        "harelins", "migdalins", "clalins", "clalbit", "phoenixins", "fnxins", "ayalonins", "hachsharains", "shirbit",
        "wesure", "9000000", "9million", "allianz", "generali", "bituachyashir", "bituahyashir", "menoramivtachim", "bituachleumi", "bituahleumi",
        "ביטוחישיר", "מנורהמבטחים", "ביטוחלאומי", "הראלביטוח", "מגדלביטוח", "כללביטוח", "איילוןביטוח", "הכשרהביטוח",
        "שומרהביטוח", "שירביט", "הפניקס",
    )

    /**
     * Names that are also everyday words, places or surnames ("מגדל" is a tower, "כלל" a rule, "Zurich" a city,
     * "Lemonade" a drink, "Harel" a first name): only as the whole sender, or the whole sender but for words of
     * [INSURER_AFFIXES] ("Harel-Health", "Menora IL").
     */
    private val INSURER_NAMES = setOf(
        "harel", "migdal", "clal", "phoenix", "fnx", "menora", "ayalon", "aig", "shomera", "hachshara", "libra", "dikla",
        "btl", "axa", "zurich", "lemonade",
        "הראל", "מגדל", "כלל", "פניקס", "מנורה", "איילון", "שומרה", "הכשרה", "ליברה", "דקלה",
    )

    private val INSURER_AFFIXES = setOf("ins", "insurance", "il", "israel", "health", "group", "ביטוח", "בריאות", "קבוצת")

    /** Whether the sender's words name an insurer; [plainNames] lets the names in [INSURER_NAMES] count too. */
    private fun isInsurer(senderWords: List<String>, plainNames: Boolean): Boolean {
        if (senderWords.isEmpty()) return false
        val compact = senderWords.joinToString("")
        if (INSURER_MARKS.any { compact.contains(it) }) return true
        if (!plainNames) return false
        val rest = senderWords.filter { it !in INSURER_AFFIXES }
        return rest.size == 1 && rest[0] in INSURER_NAMES
    }

    // --- insurance by content ---

    /** "פוליסה מס' 12345678", "policy number: 5501234": a policy named with its number, in the normalized text. */
    private val POLICY_NUMBER = Regex(
        "(?:פוליסה|הפוליסה|פוליסת|פוליסתך|policy)\\s*(?:ביטוח\\s*)?(?:מס'|מס|מספר|number|no\\.?|#)?\\s*:?\\s*-?\\s*\\d{5,}",
    )

    /** "תביעה מס' 4455", "claim #123456": a claim named with its number. */
    private val CLAIM_NUMBER = Regex(
        "(?:תביעה|התביעה|תביעתך|תביעת|claim)\\s*(?:מס'|מס|מספר|number|no\\.?|#)?\\s*:?\\s*-?\\s*\\d{4,}",
    )

    /** The user's own insurance, named by its kind or as the policy. */
    private val INSURANCE_KINDS = phrases(
        "ביטוח רכב", "ביטוח הרכב", "ביטוח דירה", "ביטוח הדירה", "ביטוח בריאות", "ביטוח חיים", "ביטוח נסיעות", "ביטוח חובה",
        "ביטוח מקיף", "ביטוח משכנתא", "ביטוח סיעודי", "הפוליסה", "פוליסת", "פוליסה", "פוליסתך", "הפרמיה", "פרמיה",
        "car insurance", "home insurance", "health insurance", "life insurance", "travel insurance", "your insurance",
        "your policy", "insurance policy", "your premium", "your claim",
    )

    /** What happened to it, as a notice says it. */
    private val INSURANCE_STATUS = phrases(
        "חודש", "חודשה", "תחודש", "יחודש", "מתחדש", "מתחדשת", "חידוש", "לחידוש", "פג", "פגה", "יפוג", "תפוג", "בתוקף",
        "הסתיים", "הסתיימה", "התקבל", "התקבלה", "אושר", "אושרה", "נדחה", "נדחתה", "שולם", "שולמה", "נקלט", "נקלטה",
        "בטיפול", "הונפק", "הונפקה", "בוטל", "בוטלה", "עודכן", "עודכנה",
        "renewed", "renewal", "renews", "expires", "expired", "expiring", "approved", "received", "issued", "cancelled",
        "canceled", "is due", "has been updated", "was paid", "has been paid",
    )

    /** An ad's words: an insurance ad says "השוו", "הצעת מחיר" or "get a quote". The opt-out line is judged apart ([isAd]): a policy notice may carry it. */
    private val INSURANCE_AD = phrases(
        "מבצע", "הנחה", "הצעה", "הצעת מחיר", "השוו", "השוואת", "חסכו", "חיסכון", "זול", "הזול",
        "משתלם", "הצטרפו", "compare", "quote", "save", "cheapest", "discount",
        "offer", "deal", "join",
    )

    private fun isInsuranceNotice(normalized: String, words: List<String>): Boolean {
        if (INSURANCE_AD.any { Text.containsWords(words, it) }) return false
        if (POLICY_NUMBER.containsMatchIn(normalized) || CLAIM_NUMBER.containsMatchIn(normalized)) return true
        return INSURANCE_KINDS.any { Text.containsWords(words, it) } && INSURANCE_STATUS.any { Text.containsWords(words, it) }
    }

    // --- deliveries ---

    /** Courier names found anywhere in the sender written as one run ("IsraelPost", "DHL-Express"). */
    private val COURIER_MARKS = listOf("israelpost", "cheetah", "fedex", "aramex", "dhl", "usps", "boxit", "דוארישראל", "צ'יטה", "ציטה", "ארמקס")

    /**
     * Courier names that count as a word of their own: "UPS" but not "Groups", "וולט" but not "וולטר". Only names:
     * a sender called "Courier" or "שליחויות" is not known to be one (its update is still found by its text).
     */
    private val COURIER_WORDS = setOf("ups", "wolt", "hfd", "chita", "וולט")

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
        "החבילה", "המשלוח", "ההזמנה", "הזמנתך", "הזמנתכם", "חבילתך", "משלוחך", "השליח", "שליח", "השליחות", "קיבלת משלוח", "קיבלתם משלוח", "קיבלת חבילה", "חבילה עבורך", "חבילה ממתינה", "משלוח עבורך",
        "your package", "your parcel", "your shipment", "your order", "your delivery", "the courier", "your courier",
    )

    /** What happened to it, in the past or the present as an update says it (an ad promises the future). */
    private val SHIPMENT_STATUS = phrases(
        "יצא", "יצאה", "יוצא", "יוצאת", "נשלח", "נשלחה", "נמסר", "נמסרה", "הגיע", "הגיעה", "ממתין", "ממתינה", "מוכן", "מוכנה",
        "בדרך", "לאיסוף", "נאסף", "נאספה", "להימסר", "נארזה", "ביצוע שליחות", "יגיע אליך", "יגיעו אליך", "תואם", "תואמה", "תואמו", "מסר", "מסירה", "עודכנה", "בלוקר", "בסניף", "בנקודת",
        "shipped", "dispatched", "delivered", "out for delivery", "on its way", "on the way", "arrived",
        "ready for pickup", "ready for collection", "awaiting pickup", "in transit", "picked up",
    )

    /**
     * A shipment named with its own number ("משלוח 87468519", "משלוח מספר XLT98670", "חבילה שמספרה 41767255",
     * "shipment AB1234567"): a number of 6 or more characters with at least 5 digits, which an ad does not have.
     */
    private val SHIPMENT_NUMBER = Regex(
        "(?:משלוח|חבילה|shipment|parcel|package)\\s*(?:מספר|מס'|מס|שמספרו|שמספרה|number|no\\.?|#)?\\s*:?\\s*#?[a-z]{0,8}\\d{5,}[a-z0-9]*",
    )

    /** What happened to a shipment that is named with its number: said as plain fact, in any form. */
    private val NUMBERED_STATUS = phrases(
        "נמסר", "נמסרה", "נאסף", "נאספה", "נאספו", "מסירת", "יגיע", "תגיע", "ממתין", "ממתינה", "יצא", "יצאה", "נשלח", "נשלחה", "בדרך",
        "הגיע", "הגיעה", "מוכן", "מוכנה", "לאיסוף", "בוטל", "בוטלה", "נדרש", "נקלט", "נקלטה", "התקבל",
        "delivered", "shipped", "arrived", "out for delivery", "ready", "picked up",
    )

    /** The place between a digit and a Hebrew letter written without a space. */
    internal val DIGIT_LETTER_JOIN = Regex("(?<=\\d)(?=[א-ת])|(?<=[א-ת])(?=\\d)")

    private fun isShipmentUpdate(body: String, normalized: String, words: List<String>): Boolean {
        if (POSTAL_ID.containsMatchIn(body) || NAMED_TRACKING.containsMatchIn(normalized)) return true
        if (SHIPMENT_NUMBER.containsMatchIn(normalized)) {
            // "XLT98670נאסף": the status written right after the number is still its own word
            val spaced = Text.words(normalized.replace(DIGIT_LETTER_JOIN, " "))
            if (NUMBERED_STATUS.any { Text.containsWords(spaced, it) }) return true
        }
        return SHIPMENT.any { Text.containsWords(words, it) } && SHIPMENT_STATUS.any { Text.containsWords(words, it) }
    }
}

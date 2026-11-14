package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.Folder

/**
 * The six service categories added in 0.7.0: [Folder.Telecom], [Folder.Health], [Folder.Bills], [Folder.Transport],
 * [Folder.Travel] and [Folder.Shopping]. Like the banks, insurers and couriers of [FolderClassifier], each is recognised in
 * two ways and no other:
 *
 * - **By the sender**: a known name (a telecom company, a health fund, a utility, a parking app...). A name that is also an
 *   everyday word ("Partner", "HOT", "Correct", "Smile", "Dan") counts only as the whole sender id (or the whole sender but for
 *   [SenderList.affixes] such as "Mobile" or "IL"), and never for the name of a saved contact. A distinctive name
 *   ("Pelephone", "Superpharm") counts anywhere in the sender. A word such as "bill", "health" or "travel" in an unknown
 *   sender id is an advertiser's, not a service's.
 * - **By the shape of the text**: what only a notice of that service says ("נקבע לך תור", "מס' צרכן", "להפעלת חניה",
 *   "הגיעה אליך קבלה"), never a single everyday word. Each shape needs its own cue and, where the words are common, a second one.
 *
 * An ad (an opt-out line or the ad marker) is never in these categories, whoever sends it: [FolderClassifier.classify] files it in All.
 * The sorting order, when a message fits several, is documented there.
 */
internal object ServiceCategories {

    /**
     * The known senders of one category. [marks] are distinctive names found anywhere in the sender written as one run;
     * [names] are everyday words that count only as the whole sender (or the whole sender but for [affixes]); [codes] are
     * numeric sender ids (short codes) that count as the whole sender.
     */
    private class SenderList(
        val marks: List<String>,
        val names: Set<String> = emptySet(),
        val affixes: Set<String> = emptySet(),
        val codes: Set<String> = emptySet(),
    ) {
        /** Whether the sender's words name one of them; [plainNames] lets [names] and [codes] count too. */
        fun matches(senderWords: List<String>, plainNames: Boolean): Boolean {
            if (senderWords.isEmpty()) return false
            val compact = senderWords.joinToString("")
            if (marks.any { compact.contains(it) }) return true
            if (!plainNames) return false
            if (senderWords.size == 1 && senderWords[0] in codes) return true
            val rest = senderWords.filter { it !in affixes }
            if (rest.isEmpty()) return senderWords.any { it in names }
            return rest.size == 1 && rest[0] in names
        }
    }

    // --- telecom ---

    /*
     * HOT, Partner, Pelephone (and its short code 4458), Cellcom, Golan Telecom, 012 and 019, We4G / wecom, Bezeq (and its
     * short code 199), Rami Levy Communications, Xfone and yes. "Rami Levy" alone is the supermarket (Shopping).
     */
    private val TELECOM_SENDERS = SenderList(
        marks = listOf(
            "pelephone", "cellcom", "hotmobile", "hotnet", "golantelecom", "we4g", "wecom", "bezeq", "xfone", "yesplus",
            "ramilevycom", "ramilevymob", "partnertv", "partnerhome",
            "פלאפון", "סלקום", "הוטמובייל", "גולןטלקום", "רמילויתקשורת", "רמילוימובייל", "אקספון",
        ),
        names = setOf("hot", "partner", "golan", "yes", "012", "019", "bezeq", "הוט", "פרטנר", "גולן", "יס", "בזק"),
        affixes = setOf("mobile", "il", "israel", "net", "tel", "telecom", "cellular", "tv", "מובייל", "תקשורת", "ישראל"),
        codes = setOf("4458", "199"),
    )

    /** A telecom company named in the text (not "yes" or "partner", which are everyday words). */
    private val TELECOM_BRANDS = phrases(
        "פלאפון", "סלקום", "פרטנר", "הוט מובייל", "הוט נט", "גולן טלקום", "בזק", "רמי לוי תקשורת", "אקספון", "we4g", "pelephone",
        "cellcom", "bezeq", "xfone", "partner tv", "golan telecom", "019", "012 מובייל",
    )

    /** What only a phone or internet line has: the context that lets a billing or support phrase be a telecom notice. */
    private val TELECOM_CONTEXT = phrases(
        "סלולר", "סלולרי", "סלולרית", "אינטרנט", "סיבים", "גלישה", "הגלישה", "טלוויזיה", "ממיר", "ראוטר", "מודם", "הקו שלך", "מספר הקו",
        "קו הנייד", "חבילת הגלישה", "ג'יגה",
    )

    /** Each group is a telecom notice when all its phrases are in the text, and the text names a telecom company or line. */
    private val TELECOM_NOTICES: List<List<List<String>>> = listOf(
        groupOf("הגדרות הגלישה"),
        groupOf("הגדרות האינטרנט"),
        groupOf("דואר דיגיטלי", "באזור האישי"),
        groupOf("לקוח יקר", "חשבונך", "טרם שולם"),
        groupOf("לקוחה יקרה", "חשבונך", "טרם שולם"),
        groupOf("לקוח יקר", "חשבונכם", "טרם שולם"),
        groupOf("עודכן זיכוי", "חשבונך"),
        groupOf("עודכן זיכוי", "בחשבונית"),
        groupOf("תודה שפנית"),
        groupOf("תודה שפניתם"),
        groupOf("טכנאי", "תיאום"),
        groupOf("טכנאי", "תואם"),
        groupOf("טכנאי", "ביקור"),
        groupOf("טכנאי", "הגעת"),
        groupOf("טכנאי", "מועד"),
    )

    private fun isTelecomNotice(words: List<String>): Boolean {
        if (TELECOM_NOTICES.none { group -> group.all { Text.containsWords(words, it) } }) return false
        return TELECOM_BRANDS.any { Text.containsWords(words, it) } || TELECOM_CONTEXT.any { Text.containsWords(words, it) }
    }

    // --- health ---

    /*
     * The health funds (Clalit, Maccabi, Meuhedet, Leumit), MADA, Sheba, Tipat Halav, Mushlam, Shila, MOR, Smile, odoro and the
     * hospitals. The pharmacies (Super-Pharm, Be) are shops by their sender (Shopping) but their prescription notices are health.
     */
    private val HEALTH_SENDERS = SenderList(
        marks = listOf(
            "clalit", "maccabitivi", "maccabihealth", "meuhedet", "shebabeyond", "shebayashir", "tipathalav", "odoro", "ichilov", "assuta",
            "soroka", "hospital", "מרכזרפואי", "ביתחולים", "טיפתחלב", "קופתחולים", "איכילוב", "אסותא", "סורוקה", "שירותיבריאות",
        ),
        names = setOf(
            "maccabi", "leumit", "mada", "sheba", "mushlam", "shila", "mor", "smile", "hadassah", "rambam", "wolfson", "beilinson",
            "כללית", "מכבי", "מאוחדת", "לאומית", "מדא", "שיבא", "משולם", "שילה", "מור", "סמייל", "הדסה", "רמבם", "וולפסון", "בילינסון",
        ),
        affixes = setOf("il", "israel", "health", "clinic", "app", "tivi", "בריאות", "קופת", "חולים", "שירותי"),
    )

    /** What a medical notice says on its own: an appointment, a prescription, a test, a sick note, a remote visit. */
    private val HEALTH_STRONG = phrases(
        "נקבע לך תור", "נקבע לכם תור", "נקבע תור", "תורך נקבע", "תזכורת לתור", "תזכורת תור", "תזכורת לתורך", "התור שלך",
        "אישור מחלה", "ביקור מרחוק", "ביקור וידאו", "בבית המרקחת", "מרשם", "מרשמים", "למעבדה", "בדיקות דם", "תוצאות הבדיקה",
        "תוצאות בדיקה", "הפניה לבדיקה", "your appointment", "appointment reminder", "your prescription", "prescription is ready",
    )

    /** The words of a clinic, which say it is a medical notice only beside an appointment or a visit ([HEALTH_CONTEXT]). */
    private val HEALTH_WEAK = phrases("מרפאה", "במרפאה", "מרפאת", "ד\"ר", "דר'", "רופא", "רופאה", "clinic", "dr.")

    private val HEALTH_CONTEXT = phrases(
        "תור", "תורך", "התור", "פגישה", "בדיקה", "בדיקות", "ביקור", "טיפול", "להגיע", "הגעה", "appointment", "visit",
    )

    /** A feedback form or a perk: sent by a health fund too, and no notice of a service. */
    private val HEALTH_FEEDBACK = phrases("שאלון קצר", "שאלון", "משוב", "הטבה", "הטבות", "סקר", "מבצע", "הנחה", "survey", "feedback", "rate us")

    /**
     * Drugs and potency pills sold "without a prescription" use the clinic's words ("מרשם", "רפואי"): a text with one of these is
     * the seller's, never a health notice (and so never the service notice that would excuse it from the spam rules).
     */
    private val HEALTH_SCAM = phrases(
        "בלי מרשם", "ללא מרשם", "בלי צורך במרשם", "ללא צורך במרשם", "without prescription", "no prescription", "ויאגרה", "סיאליס", "זיקפה",
        "קנאביס", "קאנביס", "מריחואנה", "גראס", "חשיש", "וויד", "cannabis", "marijuana", "weed", "thc", "viagra", "cialis",
    )

    private fun hasHealthNotice(words: List<String>): Boolean {
        if (HEALTH_SCAM.any { Text.containsWords(words, it) }) return false
        if (HEALTH_STRONG.any { Text.containsWords(words, it) }) return true
        if (HEALTH_FEEDBACK.any { Text.containsWords(words, it) }) return false
        return HEALTH_WEAK.any { Text.containsWords(words, it) } && HEALTH_CONTEXT.any { Text.containsWords(words, it) }
    }

    /** A feedback form or a perk, with no notice in it: "קצת על החוויה שלך, שאלון קצר". Not health even from a health fund. */
    private fun isHealthFeedbackOnly(words: List<String>): Boolean =
        HEALTH_FEEDBACK.any { Text.containsWords(words, it) } && HEALTH_STRONG.none { Text.containsWords(words, it) }

    // --- bills and services ---

    /*
     * The gas companies (Pazgas, Supergas, Amisragas), Tami4, the Israel Electric Corporation, the water corporations ("מי ..."
     * or "תאגיד"), the municipalities and councils (any "עיריית"/"מועצה"), Moked 106 and 109, GovPay and MySender receipts.
     * The Ministry of Transport ("mot") is Transport: its notices are about the vehicle (licence, test).
     */
    private val BILLS_SENDERS = SenderList(
        marks = listOf(
            "pazgas", "supergas", "amisragas", "tami4", "israelelectric", "חברתהחשמל", "פזגז", "סופרגז", "אמישרגז", "תמי4",
            "תאגיד", "moked106", "moked109", "מוקד106", "מוקד109", "govpay", "mysender", "עיריית", "עירייה",
            "מועצת", "מועצה", "municipality", "ramatgan",
        ),
        names = setOf("iec"),
        affixes = setOf("il", "israel", "ltd", "בעמ"),
    )

    private val WATER_PREFIXES = setOf("מי", "mei")

    /** A bill or a notice of a utility or municipality, said as such. */
    private val BILLS_NOTICES = phrases(
        "מס' צרכן", "מספר צרכן", "יתרת חוב", "חש' לתשלום", "חשבון לתשלום", "חשבונית לתשלום", "ארנונה", "תושבים יקרים", "תושבות יקרות",
        "תושבות ותושבים", "תושבי העיר", "הודעת עירייה", "הודעה מהעירייה", "תאגיד המים", "צריכת מים", "צריכת חשמל", "חשבון החשמל",
        "חשבון המים", "חשבון הגז", "חשבון ארנונה", "קריאת מונה", "מונה המים", "מונה החשמל",
    )

    private val FEE_WORDS = phrases("אגרה", "אגרת")

    /** A vehicle's fee is the vehicle's (Transport), not a bill: "אגרת רישוי". */
    private val VEHICLE_FEE = phrases("רישוי", "רישיון", "כביש", "טסט", "רכב")

    private val PAYMENT_WORD = phrases("תשלום")
    private val PAYMENT_DONE = phrases("בוצע בהצלחה")

    private fun hasBillNotice(words: List<String>): Boolean {
        if (BILLS_NOTICES.any { Text.containsWords(words, it) }) return true
        if (FEE_WORDS.any { Text.containsWords(words, it) } && VEHICLE_FEE.none { Text.containsWords(words, it) }) return true
        return PAYMENT_WORD.any { Text.containsWords(words, it) } && PAYMENT_DONE.any { Text.containsWords(words, it) }
    }

    // --- transport ---

    /*
     * Pango, Cellopark, Rav-Kav, Kvish 6 and Fastlane, Gett, Yango, Israel Railways, the bus operators (Egged, Metropoline, Kavim,
     * Superbus, Afikim, Nateev Express), Ituran and Pointer, and the Ministry of Transport ("mot": the vehicle's licence and test).
     */
    private val TRANSPORT_SENDERS = SenderList(
        marks = listOf(
            "pango", "cellopark", "ravkav", "kvish6", "fastlane", "israelrail", "metropoline", "superbus", "nateevexpress", "ituran",
            "פנגו", "סלופארק", "רבקו", "כביש6", "נתיבמהיר", "רכבתישראל", "מטרופולין", "סופרבס", "נתיבאקספרס", "איתוראן",
        ),
        names = setOf("gett", "yango", "uber", "bolt", "egged", "kavim", "afikim", "pointer", "mot", "rail", "אגד", "קווים", "אפיקים", "פוינטר", "רכבת"),
        affixes = setOf("il", "israel", "app", "ltd", "ישראל"),
    )

    private val TRANSPORT_NOTICES = phrases(
        "להפעלת חניה", "להפסקת חניה", "הופעלה חניה", "חניה הופעלה", "החניה הופעלה", "החניה הסתיימה", "החניה הופסקה", "סיום חניה",
        "סיום החניה", "התחלת חניה", "תחילת חניה", "חניה באזור", "תוקף החניה", "טעינת רב קו", "כרטיס רב קו", "רב קו", "ravkav", "rav kav",
        "הנהג בדרך", "הנהג הגיע", "הנהג שלך", "המונית שלך", "מונית בדרך", "המונית הגיעה", "הנסיעה שלך", "נסיעתך", "חשבונית נסיעה",
        "חשבונית כביש 6", "נסיעה בכביש 6", "חשבונית נתיב מהיר", "מבחן רישוי", "טסט שנתי", "הטסט לרכב", "טסט לרכב", "הטסט שלך",
        "מועד הטסט", "תזכורת לטסט", "אגרת רישוי", "אגרת רישיון", "תוקף הרישוי", "רישוי הרכב", "רישוי רכב", "רישיון הרכב", "your driver", "your ride", "your trip has ended",
        "driver is arriving", "parking has started", "parking ended",
    )

    private val TEST_WORD = phrases("טסט", "לטסט")
    private val VEHICLE_WORD = phrases("רכב", "הרכב", "מוסך", "לרכב", "רישוי")

    private fun hasTransportNotice(words: List<String>): Boolean {
        if (TRANSPORT_NOTICES.any { Text.containsWords(words, it) }) return true
        return TEST_WORD.any { Text.containsWords(words, it) } && VEHICLE_WORD.any { Text.containsWords(words, it) }
    }

    // --- travel ---

    /*
     * EL AL (and EL AL Matmid), Israir, Arkia, the hotel chains (Fattal, Isrotel, Dan) and the booking sites. The other airlines
     * and sites are in the list by their distinctive names.
     */
    private val TRAVEL_SENDERS = SenderList(
        marks = listOf(
            "israir", "arkia", "fattal", "isrotel", "danhotels", "danpanorama", "airbnb", "ryanair", "wizzair", "easyjet", "lufthansa",
            "aegean", "turkishairlines", "expedia", "agoda", "tripadvisor", "ישראייר", "ארקיע", "פתאל", "ישרוטל", "אלעל",
        ),
        names = setOf("elal", "dan", "booking", "hotels"),
        affixes = setOf("matmid", "il", "israel", "airlines", "com", "mobile", "app"),
    )

    private val FLIGHT_WORDS = phrases("טיסה", "הטיסה", "טיסתך", "טיסתכם", "flight", "your flight")

    private val FLIGHT_CUES = phrases(
        "צ'ק אין", "check in", "check-in", "עלייה למטוס", "עליה למטוס", "boarding", "gate", "שער", "טרמינל", "terminal", "הזמנה",
        "הזמנתך", "אישור", "booking", "confirmation", "כרטיס", "ticket",
    )

    private val TRAVEL_NOTICES = phrases(
        "הזמנת מלון", "הזמנתך במלון", "הזמנתכם במלון", "הזמנתך למלון", "hotel reservation", "hotel booking", "boarding pass",
        "כרטיס עלייה למטוס", "כרטיס עליה למטוס", "web check-in", "online check-in", "צ'ק אין אונליין", "צ'ק אין לטיסה", "אישור הזמנת טיסה",
    )

    private fun hasTravelNotice(words: List<String>): Boolean {
        if (TRAVEL_NOTICES.any { Text.containsWords(words, it) }) return true
        return FLIGHT_WORDS.any { Text.containsWords(words, it) } && FLIGHT_CUES.any { Text.containsWords(words, it) }
    }

    // --- shopping ---

    /*
     * ACE, Payngo, Super-Pharm and the other pharmacies' shops, MShuk, Pizza Hut, BINO EAT, Correct, MODAN, NEHALIM, Shufersal,
     * Rami Levy (the supermarket), HTZ / Htzone and HtzoneGift, Bee gift, MyOrder, buyme, PaisPlus / Pais Plus / ManuiPais (the
     * Pais benefits club) and LogisTeam.
     */
    private val SHOPPING_SENDERS = SenderList(
        marks = listOf(
            "payngo", "superpharm", "bepharm", "goodpharm", "mshuk", "pizzahut", "binoeat", "nehalim", "shufersal", "ramilevy", "htzone",
            "beegift", "myorder", "buyme", "paisplus", "manuipais", "logisteam", "סופרפארם", "שופרסל", "רמילוי", "פיינגו", "פיצההאט",
        ),
        names = setOf("ace", "htz", "correct", "modan", "אייס", "נחלים", "מודן"),
        affixes = setOf("il", "israel", "online", "shop", "store", "gift"),
    )

    /** A receipt, an invoice or the confirmation of an order, as it is written when the shop sends it. */
    private val SHOPPING_NOTICES = phrases(
        "הגיעה אליך קבלה", "הגיעה אליכם קבלה", "הקבלה שלך", "קבלה דיגיטלית", "קישור לקבלה", "לצפייה בקבלה", "לצפיה בקבלה",
        "חשבונית מס", "חשבוניתך", "החשבונית שלך", "לצפייה בחשבונית", "לצפיה בחשבונית", "אישור הזמנה מספר", "אישור הזמנה מס",
        "אישור הזמנתך", "בחירתך נקלטה", "קישור לסיכום הזמנה", "קישור לסיכום ההזמנה", "סיכום הזמנה", "שובר מתנה", "השובר שלך",
        "הזמנתך התקבלה", "הזמנתכם התקבלה", "תודה על הקנייה", "תודה על קנייתך", "תודה שקנית", "תודה שרכשת", "your receipt",
        "order confirmation", "thank you for your purchase", "your order confirmation", "order summary",
    )

    private val RECEIPT_WORDS = phrases("קבלה", "חשבונית")
    private val PURCHASE_WORDS = phrases("הזמנה", "ההזמנה", "רכישה", "קנייה", "קניה", "רכשת", "קנית", "purchase", "order")
    private val VOUCHER_WORDS = phrases("שובר")
    private val VOUCHER_CUES = phrases("תוקף", "בתוקף", "לממש", "מימוש", "קוד", "מתנה", "buyme")

    private fun hasShoppingNotice(words: List<String>): Boolean {
        if (SHOPPING_NOTICES.any { Text.containsWords(words, it) }) return true
        if (RECEIPT_WORDS.any { Text.containsWords(words, it) } && PURCHASE_WORDS.any { Text.containsWords(words, it) }) return true
        return VOUCHER_WORDS.any { Text.containsWords(words, it) } && VOUCHER_CUES.any { Text.containsWords(words, it) }
    }

    /** The sites that host an online receipt; their short links are the receipt's, not a scam's ([isReceiptWithLink]). */
    private val RECEIPT_HOSTS = listOf("wee.ai", "payper.co.il", "payper.link", "payper.io", "payper.app")

    /**
     * Whether [host] is a receipt site and the [body] is a receipt or an order: the one case a short link is no sign of spam.
     * Only these hosts, and only with the words of a receipt; any other short link, and these hosts in an unrelated text, still count.
     */
    fun isReceiptWithLink(host: String, body: String): Boolean {
        if (RECEIPT_HOSTS.none { host == it || host.endsWith(".$it") }) return false
        return hasShoppingNotice(Text.words(body))
    }

    // --- the folders ---

    /** Whether the sender id ([address]) or a saved contact's [name] is one of the known senders of the six categories. */
    fun isKnownSender(address: List<String>, name: List<String>): Boolean =
        senderLists.any { it.matches(address, plainNames = true) || it.matches(name, plainNames = false) } ||
            isWaterCorporation(address, plainNames = true)

    private val senderLists = listOf(TELECOM_SENDERS, HEALTH_SENDERS, BILLS_SENDERS, TRANSPORT_SENDERS, TRAVEL_SENDERS, SHOPPING_SENDERS)

    private fun isWaterCorporation(senderWords: List<String>, plainNames: Boolean): Boolean =
        plainNames && senderWords.size >= 2 && senderWords[0] in WATER_PREFIXES

    /**
     * The category a known sender stands for, in the order Telecom, Health, Bills, Transport, Travel; null when it is none of them.
     * A health fund's feedback form or perk ([isHealthFeedbackOnly]) is not a notice, so it does not make the message Health.
     * [isContact]: the sender [name] is a saved contact's, so only its distinctive names count.
     */
    fun folderBySender(address: List<String>, name: List<String>, isContact: Boolean, bodyWords: List<String>): Folder? {
        fun named(list: SenderList) = list.matches(address, plainNames = true) || list.matches(name, plainNames = !isContact)
        if (named(TELECOM_SENDERS)) return Folder.Telecom
        if (named(HEALTH_SENDERS) && !isHealthFeedbackOnly(bodyWords)) return Folder.Health
        if (named(BILLS_SENDERS) || isWaterCorporation(address, plainNames = true) || isWaterCorporation(name, plainNames = !isContact)) return Folder.Bills
        if (named(TRANSPORT_SENDERS)) return Folder.Transport
        if (named(TRAVEL_SENDERS)) return Folder.Travel
        return null
    }

    /** Whether the sender is a known shop, restaurant or pharmacy chain; its notices are Shopping unless they are more specific. */
    fun isShoppingSender(address: List<String>, name: List<String>, isContact: Boolean): Boolean =
        SHOPPING_SENDERS.matches(address, plainNames = true) || SHOPPING_SENDERS.matches(name, plainNames = !isContact)

    /** The category the shape of the text stands for, in the order Telecom, Health, Bills, Transport, Travel; null when it is none of them. */
    fun folderByText(words: List<String>): Folder? = when {
        isTelecomNotice(words) -> Folder.Telecom
        hasHealthNotice(words) -> Folder.Health
        hasBillNotice(words) -> Folder.Bills
        hasTransportNotice(words) -> Folder.Transport
        hasTravelNotice(words) -> Folder.Travel
        else -> null
    }

    /** Whether the text is a receipt, an invoice or an order confirmation. */
    fun isShoppingNotice(words: List<String>): Boolean = hasShoppingNotice(words)

    private fun phrases(vararg phrase: String): List<List<String>> = phrase.map { Text.words(it) }

    private fun groupOf(vararg phrase: String): List<List<String>> = phrase.map { Text.words(it) }
}

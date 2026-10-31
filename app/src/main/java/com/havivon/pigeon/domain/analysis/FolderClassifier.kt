package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.Folder

/**
 * Sorts a message into one of the automatic folders. The order is the priority, first match wins:
 *
 * 1. [Folder.Spam]: the [SpamScorer] verdict says spam.
 * 2. [Folder.Codes]: [CodeExtractor] finds a one-time code. A bank's "קוד 123456 לאישור חיוב" is a code first.
 * 3. [Folder.Banks]: the sender is a bank or card company, or the text talks about charges, balances, cards.
 * 4. [Folder.Deliveries]: the sender is a courier, or the text talks about a delivery, a package, tracking.
 * 5. [Folder.Personal]: the sender is a saved contact or an ordinary phone number.
 *
 * Anything else (an advertiser, a service with an alphanumeric id) stays out of the automatic folders and
 * is returned as [Folder.All], the folder that holds every conversation. The text rules of 3 and 4 are
 * skipped for saved contacts: a friend writing "the package arrived" is still personal. A bank or courier
 * saved as a contact is still recognised by its name.
 */
object FolderClassifier {

    /** Classifies [sms]. Pass [spam] when the verdict is already computed, to avoid scoring twice. */
    fun classify(sms: IncomingSms, spam: SpamVerdict = SpamScorer.score(sms)): Folder {
        if (spam.isSpam) return Folder.Spam
        if (CodeExtractor.extract(sms.body) != null) return Folder.Codes
        val words = Text.words(sms.body)
        val sender = Text.words(listOfNotNull(sms.senderName, sms.address).joinToString(" "))
        if (isNamedLike(sender, BANK_NAMES, BANK_NAME_WORDS) || (!sms.isContact && BANK_WORDS.any { Text.containsPhrase(words, it) })) return Folder.Banks
        if (isNamedLike(sender, COURIER_NAMES, COURIER_NAME_WORDS) || (!sms.isContact && DELIVERY_WORDS.any { Text.containsPhrase(words, it) })) return Folder.Deliveries
        if (sms.isContact || Text.phoneKey(sms.address) != null) return Folder.Personal
        return Folder.All
    }

    /**
     * Whether a sender name or id contains one of [fused] (written as one run, "BankLeumi" or "Bank Leumi"),
     * or one of [wholeWords] as a word of its own, so that "Max" is a card company but "Maxim Pizza" is not.
     */
    private fun isNamedLike(senderWords: List<String>, fused: List<String>, wholeWords: List<String>): Boolean {
        val compact = senderWords.joinToString("")
        return fused.any { compact.contains(it) } || wholeWords.any { Text.containsPhrase(senderWords, it) }
    }

    private val BANK_NAMES = listOf("leumi", "hapoalim", "poalim", "isracard", "mizrahi", "tefahot", "discountbank", "bankdiscount", "mastercard")

    private val BANK_NAME_WORDS = listOf(
        "bank", "max", "cal", "visa", "בנק", "לאומי", "הפועלים", "דיסקונט", "מזרחי", "טפחות", "הבינלאומי", "ישראכרט", "מקס", "כאל", "ויזה", "מאסטרקארד",
    )

    private val BANK_WORDS = listOf(
        "חיוב", "חויב", "חויבת", "יתרה", "כרטיס", "אשראי", "עסקה", "משיכה", "זיכוי", "הפקדה", "עו\"ש", "העברה בנקאית",
        "transaction", "balance", "debit", "debited", "credited", "credit card", "card ending", "overdraft",
    )

    private val COURIER_NAMES = listOf("israelpost", "cheetah", "fedex", "aramex", "dhl")

    private val COURIER_NAME_WORDS = listOf(
        "dhl", "ups", "wolt", "courier", "postal", "דואר", "צ'יטה", "ארמקס", "וולט", "בלדר", "שליח",
    )

    private val DELIVERY_WORDS = listOf(
        "משלוח", "חבילה", "שליח", "שליחה", "מעקב", "delivery", "delivered", "package", "parcel", "tracking", "shipment", "shipped", "courier",
    )
}

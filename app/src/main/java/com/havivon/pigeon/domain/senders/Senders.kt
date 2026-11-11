package com.havivon.pigeon.domain.senders

import com.havivon.pigeon.domain.PhoneNumbers

/** One row of the provider's light read for the sender list: which conversation an address is in, and when it was last used. */
data class AddressRow(val threadId: Long, val address: String, val dateMillis: Long)

/**
 * Everyone the user ever exchanged a message with, one entry per sender: the same person written "+972 52-555-0123" and
 * "0525550123" is one entry, [key] being their [PhoneNumbers.lookupKey]. [address] is the way it was last written;
 * [name] the contact's name, null for a number that is not in the contacts and for a sender ID.
 */
data class Sender(
    val key: String,
    val address: String,
    val name: String?,
    /** Every conversation this sender is in, for the filter. */
    val threadIds: Set<Long>,
    val lastMillis: Long,
) {
    /** What the entry is called: the contact's name, else the address. */
    val label: String get() = name ?: address
    val conversationCount: Int get() = threadIds.size
}

/**
 * The senders in [rows] (incoming and outgoing messages alike), newest first. Addresses are grouped by their normalised
 * key; blank addresses are left out. [nameOf] gives a contact's name for the address as it was last written.
 */
fun distinctSenders(rows: List<AddressRow>, nameOf: (String) -> String? = { null }): List<Sender> {
    class Acc(var address: String, var last: Long) {
        val threads = HashSet<Long>()
    }
    val byKey = LinkedHashMap<String, Acc>()
    for (row in rows) {
        val address = row.address.trim()
        val key = PhoneNumbers.lookupKey(address) ?: continue
        val acc = byKey.getOrPut(key) { Acc(address, row.dateMillis) }
        acc.threads += row.threadId
        if (row.dateMillis > acc.last) {
            acc.last = row.dateMillis
            acc.address = address
        }
    }
    return byKey.map { (key, acc) ->
        Sender(key, acc.address, nameOf(acc.address)?.takeIf { it.isNotBlank() }, acc.threads, acc.last)
    }.sortedByDescending { it.lastMillis }
}

/**
 * Finds the senders a typed text means. Case-insensitive substring on the name and the address; a typed number matches
 * however it is written ("054...", "+97254...", "54...", with dashes or spaces). Best first: a match at the start,
 * then at the start of a word, then anywhere; ties go to the most recent. An empty text gives the newest senders.
 */
object SenderMatcher {

    const val LIMIT = 50
    const val RECENT = 20

    /** Digits, and the punctuation people type in a number. */
    private fun looksLikeNumber(q: String) = q.any { it.isDigit() } && q.all { it.isDigit() || it in "+-() ." }

    /** A number's digits without the Israeli country code and the trunk zero: "+972 54-1" and "054-1" are both "541". */
    internal fun national(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        if (raw.trim().startsWith("+") || digits.startsWith("00")) {
            digits = digits.removePrefix("00")
            if (digits.startsWith("972")) digits = digits.drop(3)
        } else if (digits.startsWith("972") && digits.length >= 11) {
            digits = digits.drop(3)
        }
        return digits.removePrefix("0")
    }

    /** 0 prefix, 1 start of a word, 2 anywhere, null no match. */
    private fun textRank(text: String, q: String): Int? {
        var i = text.indexOf(q)
        if (i < 0) return null
        if (i == 0) return 0
        while (i >= 0) {
            if (i == 0 || !text[i - 1].isLetterOrDigit()) return 1
            i = text.indexOf(q, i + 1)
        }
        return 2
    }

    private fun rank(sender: Sender, q: String, number: Boolean, qNational: String): Int? {
        val name = sender.name?.lowercase()?.let { textRank(it, q) }
        val address = sender.address.lowercase()
        val byAddress = when {
            PhoneNumbers.isPhoneNumber(address) -> if (number) {
                val nat = national(address)
                if (qNational.isEmpty()) 1 else nat.indexOf(qNational).let { if (it < 0) null else if (it == 0) 0 else 2 }
            } else {
                null
            }
            else -> textRank(address, q)
        }
        return listOfNotNull(name, byAddress).minOrNull()
    }

    fun suggest(senders: List<Sender>, query: String, limit: Int = LIMIT): List<Sender> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return senders.sortedByDescending { it.lastMillis }.take(RECENT)
        val number = looksLikeNumber(q)
        val qNational = if (number) national(q) else ""
        return senders.mapNotNull { s -> rank(s, q, number, qNational)?.let { it to s } }
            .sortedWith(compareBy<Pair<Int, Sender>> { it.first }.thenByDescending { it.second.lastMillis })
            .take(limit)
            .map { it.second }
    }
}

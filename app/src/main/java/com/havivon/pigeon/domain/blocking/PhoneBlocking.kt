package com.havivon.pigeon.domain.blocking

import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.SpamVerdict
import com.havivon.pigeon.domain.repository.SpamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/**
 * Android's own list of blocked numbers (`BlockedNumberContract.BlockedNumbers`), the one list every
 * messaging and phone app on the phone shares: "Block" in Google Messages, Samsung Messages or the Phone app
 * writes to it, and Android itself drops calls and SMS from a number on it before any app sees them.
 *
 * Only the default SMS app (and the default phone app) may read or change it. Google's and Samsung's own
 * spam-folder verdicts and their "not spam" lists are kept privately inside those apps; no other app can
 * read them, so this list is all Pigeon can share with them.
 */
interface SystemBlockList {
    /**
     * The numbers on the list as the system stores them, again whenever it changes; null while Pigeon may not read
     * it (not the default SMS app, or a user of the phone who may not block numbers).
     */
    val numbers: Flow<List<String>?>

    /** Adds [number]; false when Pigeon may not change the list now or the system refused. A number already on it is left as it is (true). */
    suspend fun block(number: String): Boolean

    /** Removes every entry that is the same sender as [number] ("+972 54..." and "054..."); how many went. */
    suspend fun unblock(number: String): Int

    companion object {
        /** No list (a viewer, the tests): nothing to read, nothing written. */
        val None = object : SystemBlockList {
            override val numbers: Flow<List<String>?> = flowOf(null)
            override suspend fun block(number: String) = false
            override suspend fun unblock(number: String) = 0
        }
    }
}

/** The lookup keys of the blocked numbers Pigeon saw last time, to tell which were blocked since (see [PhoneAwareSpamRepository.sync]). */
interface SeenBlockedMemory {
    /** Null before the first time: nothing was seen yet. */
    fun load(): Set<String>?
    fun save(keys: Set<String>)
}

/** The rules that turn the phone's blocked list into spam verdicts. Pure, for the tests. */
object BlockedOnPhone {

    /** A number that can be on the list: a phone number, not a sender id such as "Leumi" (Android blocks numbers only). */
    fun blockable(address: String): Boolean = PhoneNumbers.isPhoneNumber(address) && PhoneNumbers.normalize(address).removePrefix("+").length >= 3

    /** Whether [address] is on [blocked], under any way its number is written. */
    fun isOn(blocked: List<String>, address: String): Boolean = blocked.any { PhoneNumbers.same(it, address) }

    /**
     * The stored verdicts and one verdict ([SpamReason.BlockedOnPhone]) for every blocked number that has none, as if
     * the user had marked it spam in Pigeon: a stored verdict on a blocked number gets the reason too. A sender on
     * the user's "not spam" list ([notSpam]) is left out, as "Not spam" keeps the filter away; "Mark as spam" (or
     * blocking it again on the phone) takes it off that list first.
     */
    fun merge(stored: List<SpamVerdict>, blocked: List<String>, notSpam: Set<String>): List<SpamVerdict> {
        val numbers = blocked.filter(::blockable)
        if (numbers.isEmpty()) return stored
        val blockedKeys = numbers.mapNotNullTo(HashSet()) { PhoneNumbers.lookupKey(it) }
        val out = stored.mapTo(ArrayList(stored.size + numbers.size)) { v ->
            if (PhoneNumbers.lookupKey(v.address) in blockedKeys && SpamReason.BlockedOnPhone !in v.reasons) v.copy(reasons = v.reasons + SpamReason.BlockedOnPhone) else v
        }
        val judged = stored.mapNotNullTo(HashSet()) { PhoneNumbers.lookupKey(it.address) }
        val allowed = notSpam.mapNotNullTo(HashSet()) { PhoneNumbers.lookupKey(it) }
        for (n in numbers.distinctBy { PhoneNumbers.lookupKey(it) }) {
            val key = PhoneNumbers.lookupKey(n) ?: continue
            if (key in judged || key in allowed) continue
            out += SpamVerdict(PhoneNumbers.normalize(n), threadId = 0, reasons = listOf(SpamReason.BlockedOnPhone), judgedAtMillis = 0)
        }
        return out
    }
}

/**
 * The spam verdicts with the phone's blocked list ([SystemBlockList]) in them, over the verdicts Pigeon stores
 * ([stored]):
 *
 * - A blocked number is spam ([BlockedOnPhone.merge]), unless the user said "Not spam" in Pigeon after it was
 *   blocked. A number blocked after the user's "Not spam" (in the Phone app, say) is taken off the "not spam"
 *   list ([sync]): the latest choice wins.
 * - "Mark as spam" ([SpamReason.MarkedByUser]) also blocks the number on the phone when [blockOnPhone] says so
 *   (the setting, on by default): it can then neither call nor text, in any app. Only phone numbers; the filter's
 *   own verdicts never block anything.
 * - "Not spam" on a number that is blocked on the phone offers to unblock it ([unblockOffers]): the app asks,
 *   since that changes the whole phone, and [confirmUnblock] or [keepBlocked] answers.
 *
 * While Pigeon may not read the list (a viewer) all of this does nothing, and the stored verdicts pass as they are.
 */
class PhoneAwareSpamRepository(
    private val stored: SpamRepository,
    private val system: SystemBlockList,
    private val blockOnPhone: suspend () -> Boolean,
    private val seen: SeenBlockedMemory,
) : SpamRepository {

    override val verdicts: Flow<List<SpamVerdict>> =
        combine(stored.verdicts, stored.notSpam, system.numbers) { v, n, b -> BlockedOnPhone.merge(v, b.orEmpty(), n) }

    override val notSpam: Flow<Set<String>> = stored.notSpam

    private val _unblockOffers = MutableStateFlow<List<String>>(emptyList())

    /** The numbers just marked "Not spam" that are still blocked on the phone: the app asks whether to unblock them. */
    val unblockOffers: StateFlow<List<String>> = _unblockOffers.asStateFlow()

    private suspend fun blockedNow(): List<String> = system.numbers.first().orEmpty()

    override suspend fun verdictFor(address: String): SpamVerdict? {
        val blocked = blockedNow()
        val verdict = stored.verdictFor(address)
        val onPhone = BlockedOnPhone.blockable(address) && BlockedOnPhone.isOn(blocked, address)
        return when {
            !onPhone -> verdict
            verdict != null -> BlockedOnPhone.merge(listOf(verdict), blocked, emptySet()).single()
            stored.isNotSpam(address) -> null
            else -> SpamVerdict(PhoneNumbers.normalize(address), 0, listOf(SpamReason.BlockedOnPhone), 0)
        }
    }

    override suspend fun markSpam(address: String, threadId: Long, reasons: List<SpamReason>): Boolean {
        val marked = stored.markSpam(address, threadId, reasons)
        if (marked && SpamReason.MarkedByUser in reasons && BlockedOnPhone.blockable(address) && blockOnPhone()) {
            val blocked = system.numbers.first()
            if (blocked != null && !BlockedOnPhone.isOn(blocked, address)) system.block(address)
        }
        return marked
    }

    override suspend fun markNotSpam(address: String) {
        stored.markNotSpam(address)
        if (BlockedOnPhone.blockable(address) && BlockedOnPhone.isOn(blockedNow(), address)) {
            _unblockOffers.update { list -> if (list.any { PhoneNumbers.same(it, address) }) list else list + address }
        }
    }

    override suspend fun isNotSpam(address: String): Boolean = stored.isNotSpam(address)

    override suspend fun removeFromNotSpam(address: String) = stored.removeFromNotSpam(address)

    /** "Unblock": the offered numbers come off the phone's blocked list (they are "not spam" already). */
    suspend fun confirmUnblock() {
        val numbers = _unblockOffers.value
        _unblockOffers.value = emptyList()
        for (n in numbers) system.unblock(n)
    }

    /** "Keep blocked": not spam in Pigeon, still blocked on the phone. */
    fun keepBlocked() {
        _unblockOffers.value = emptyList()
    }

    /**
     * Called with every new reading of the phone's list: a number that was not on it last time was blocked since,
     * which beats an earlier "Not spam" in Pigeon (it comes off that list, so the number is spam again). The first
     * reading only remembers what is there; an unreadable list ([numbers] null) changes nothing.
     */
    suspend fun sync(numbers: List<String>?) {
        if (numbers == null) return
        val phoneNumbers = numbers.filter(BlockedOnPhone::blockable)
        val keys = phoneNumbers.mapNotNullTo(HashSet()) { PhoneNumbers.lookupKey(it) }
        val before = seen.load()
        if (before == keys) return
        seen.save(keys)
        if (before == null) return
        for (n in phoneNumbers) if (PhoneNumbers.lookupKey(n) !in before && stored.isNotSpam(n)) stored.removeFromNotSpam(n)
    }

    /** Keeps [sync] running on the phone's list; returns only when the list's flow ends. */
    suspend fun syncForever() {
        system.numbers.collect { sync(it) }
    }
}

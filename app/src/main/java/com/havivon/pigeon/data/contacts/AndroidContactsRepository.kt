package com.havivon.pigeon.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.havivon.pigeon.data.sms.changes
import com.havivon.pigeon.data.sms.long
import com.havivon.pigeon.data.sms.queryList
import com.havivon.pigeon.data.sms.string
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.Contact
import com.havivon.pigeon.domain.repository.ContactsRepository
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One row of the phone-numbers table: a contact has one row per number. */
data class PhoneRow(val contactId: Long, val name: String, val number: String)

/** Folds the per-number rows into contacts, keeping the provider's order (by name) and dropping repeated numbers. */
fun groupContacts(rows: List<PhoneRow>): List<Contact> =
    rows.groupBy { it.contactId }.map { (id, group) ->
        val numbers = mutableListOf<String>()
        for (row in group) if (numbers.none { PhoneNumbers.same(it, row.number) }) numbers += row.number
        Contact(id = id, name = group.first().name, numbers = numbers)
    }

/** Contacts whose name contains [query] (any case), or with a number that contains its digits. */
fun filterContacts(contacts: List<Contact>, query: String): List<Contact> {
    val q = query.trim()
    if (q.isEmpty()) return contacts
    return contacts.filter { c -> c.name.contains(q, ignoreCase = true) || c.numbers.any { PhoneNumbers.matchesQuery(it, q) } }
}

/**
 * The name of each number in [rows], by [PhoneNumbers.lookupKey]. Where two contacts share a number the
 * first one (the provider's order, by name) keeps it.
 */
fun nameIndex(rows: List<PhoneRow>): Map<String, String> {
    val index = HashMap<String, String>(rows.size * 2)
    for (row in rows) {
        val key = PhoneNumbers.lookupKey(row.number) ?: continue
        if (key !in index) index[key] = row.name
    }
    return index
}

/**
 * [ContactsRepository] over the phone's contacts. The inbox asks for the name of every conversation on
 * every change, so the names come from one read of the phone-numbers table, kept as a map from number to
 * name ([nameIndex]) until the contacts change; never a lookup per address.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidContactsRepository(private val context: Context) : ContactsRepository {

    private val resolver = context.contentResolver

    @Volatile
    private var index: Map<String, String>? = null

    /** Counts the contact changes, so an index read while one happened is not kept. */
    private val generation = AtomicInteger()
    private val reading = Mutex()

    @Volatile
    private var observing = false

    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            generation.incrementAndGet()
            index = null
        }
    }

    init {
        observe()
    }

    /** Watches the contacts, so the index is read again after a change; tried again until the permission is there. */
    private fun observe() {
        if (observing) return
        try {
            resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            observing = true
        } catch (_: SecurityException) {
            // No contacts permission yet: nothing is cached either
        }
    }

    private fun allowed(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    override val contacts: Flow<List<Contact>> =
        resolver.changes(ContactsContract.Contacts.CONTENT_URI)
            .mapLatest { readContacts() }
            .flowOn(Dispatchers.IO)

    override suspend fun displayName(address: String): String? {
        if (address.isBlank() || !allowed()) return null
        val key = PhoneNumbers.lookupKey(address) ?: return null
        return names()[key]
    }

    override suspend fun displayNames(addresses: Collection<String>): Map<String, String?> {
        if (!allowed()) return addresses.associateWith { null }
        val index = names()
        return addresses.associateWith { a -> PhoneNumbers.lookupKey(a)?.let { index[it] } }
    }

    private suspend fun names(): Map<String, String> {
        index?.let { return it }
        observe()
        return reading.withLock {
            index ?: run {
                val before = generation.get()
                val read = withContext(Dispatchers.IO) { nameIndex(readPhoneRows()) }
                // The contacts changed while they were read: use this read now, read again next time
                if (generation.get() == before) index = read
                read
            }
        }
    }

    override suspend fun search(query: String): List<Contact> = filterContacts(contacts.first(), query)

    private fun readContacts(): List<Contact> = if (allowed()) groupContacts(readPhoneRows()) else emptyList()

    private fun readPhoneRows(): List<PhoneRow> =
        resolver.queryList(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        ) { c ->
            val number = c.string(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (number.isBlank()) null
            else PhoneRow(
                contactId = c.long(ContactsContract.CommonDataKinds.Phone.CONTACT_ID),
                name = c.string(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY).ifBlank { number },
                number = number,
            )
        }
}

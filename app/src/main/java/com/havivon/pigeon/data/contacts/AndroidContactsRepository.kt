package com.havivon.pigeon.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.havivon.pigeon.data.sms.changes
import com.havivon.pigeon.data.sms.long
import com.havivon.pigeon.data.sms.queryList
import com.havivon.pigeon.data.sms.string
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.Contact
import com.havivon.pigeon.domain.repository.ContactsRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
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

/** A cached "no contact for this address". */
private const val NO_NAME = ""

/**
 * [ContactsRepository] over the phone's contacts. Names are cached per address, since the inbox
 * asks for every conversation on every change, and the cache empties whenever the contacts change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidContactsRepository(private val context: Context) : ContactsRepository {

    private val resolver = context.contentResolver
    private val names = ConcurrentHashMap<String, String>()

    init {
        try {
            resolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) = names.clear()
                },
            )
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
        names[address]?.let { return it.ifEmpty { null } }
        val name = withContext(Dispatchers.IO) {
            resolver.queryList(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)),
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            ) { it.string(ContactsContract.PhoneLookup.DISPLAY_NAME).ifBlank { null } }.firstOrNull()
        }
        names[address] = name ?: NO_NAME
        return name
    }

    override suspend fun search(query: String): List<Contact> = filterContacts(contacts.first(), query)

    private fun readContacts(): List<Contact> {
        if (!allowed()) return emptyList()
        val rows = resolver.queryList(
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
        return groupContacts(rows)
    }
}

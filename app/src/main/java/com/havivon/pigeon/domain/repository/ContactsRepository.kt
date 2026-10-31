package com.havivon.pigeon.domain.repository

import com.havivon.pigeon.domain.model.Contact
import kotlinx.coroutines.flow.Flow

/** Names and numbers from the phone's contacts. Without READ_CONTACTS everything is empty (or null). */
interface ContactsRepository {

    /** Every contact that has a phone number, by name; re-read when the contacts change. */
    val contacts: Flow<List<Contact>>

    /** The contact's name for an address (a number or a sender ID), or null when there is none. */
    suspend fun displayName(address: String): String?

    /** Contacts whose name or number matches [query], for the new-message picker. */
    suspend fun search(query: String): List<Contact>
}

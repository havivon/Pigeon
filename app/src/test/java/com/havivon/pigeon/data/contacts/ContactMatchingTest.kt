package com.havivon.pigeon.data.contacts

import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactMatchingTest {

    @Test
    fun `rows of one contact fold into one contact, repeated numbers once`() {
        val contacts = groupContacts(
            listOf(
                PhoneRow(1, "Dana", "052-555-0123"),
                PhoneRow(2, "Avi", "+15555550100"),
                PhoneRow(1, "Dana", "+972 52 555 0123"),
                PhoneRow(1, "Dana", "03-555-0199"),
            ),
        )
        assertEquals(
            listOf(Contact(1, "Dana", listOf("052-555-0123", "03-555-0199")), Contact(2, "Avi", listOf("+15555550100"))),
            contacts,
        )
    }

    private val contacts = listOf(
        Contact(1, "Dana Levi", listOf("+972525550123")),
        Contact(2, "דני", listOf("0545550111")),
        Contact(3, "Avi", listOf("+15555550100")),
    )

    @Test
    fun `search by name in any case and in Hebrew`() {
        assertEquals(listOf(1L), filterContacts(contacts, "levi").map { it.id })
        assertEquals(listOf(2L), filterContacts(contacts, "דנ").map { it.id })
    }

    @Test
    fun `search by number with a local prefix`() {
        assertEquals(listOf(1L), filterContacts(contacts, "052-555").map { it.id })
    }

    @Test
    fun `an empty query lists everyone`() {
        assertEquals(3, filterContacts(contacts, "  ").size)
    }

    @Test
    fun `one read of the contacts names every way a number is written`() {
        val index = nameIndex(
            listOf(
                PhoneRow(1, "Dana", "052-555-0123"),
                PhoneRow(2, "Avi", "+15555550100"),
                PhoneRow(3, "Dana's twin", "+972 52 555 0123"),
                PhoneRow(4, "Office", "03-555-0199"),
                PhoneRow(5, "Bank", "Leumi"),
            ),
        )
        fun name(address: String) = PhoneNumbers.lookupKey(address)?.let { index[it] }
        assertEquals("Dana", name("+972525550123"))
        assertEquals("Dana", name("0525550123"))
        assertEquals("Avi", name("5555550100"))
        assertEquals("Office", name("+972 3-555-0199"))
        assertEquals("Bank", name("LEUMI"))
        assertEquals(null, name("0525550124"))
        assertEquals(null, name(""))
    }
}

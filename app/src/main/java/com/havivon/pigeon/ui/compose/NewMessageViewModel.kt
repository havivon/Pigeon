package com.havivon.pigeon.ui.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.data.parseRecipients
import com.havivon.pigeon.data.sms.MAX_SEND_LENGTH
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.repository.ContactsRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEBOUNCE_MS = 150L
private const val MAX_SUGGESTIONS = 30

/** No real message goes to more; the same cap as an `sms:` link. */
private const val MAX_RECIPIENTS = 20

data class Recipient(val address: String, val name: String? = null)

/** A contact's number offered under the recipient field; [name] is null for the typed number itself. */
data class Suggestion(val name: String?, val number: String)

data class NewMessageUiState(
    val recipients: List<Recipient> = emptyList(),
    val query: String = "",
    val suggestions: List<Suggestion> = emptyList(),
    val body: String = "",
    val sending: Boolean = false,
) {
    /** Sending takes a recipient (a chip, or a valid number still in the field) and some text. */
    val canSend: Boolean get() = !sending && body.isNotBlank() && (recipients.isNotEmpty() || typedNumber(query) != null)
}

sealed interface NewMessageEvent {
    /** Sent to one person: their conversation opens. */
    data class OpenThread(val threadId: Long) : NewMessageEvent
    /** Sent to several people: each gets their own SMS, in their own conversation. */
    data class SentToMany(val count: Int) : NewMessageEvent
    data object Failed : NewMessageEvent
}

/** The number in the field, when it is one (the same rule as an `sms:` link's recipients). */
fun typedNumber(text: String): String? = parseRecipients(text.trim()).singleOrNull()?.takeIf { text.count(Char::isDigit) >= 3 }

/**
 * The new-message screen. A draft opened from another app's `sms:` link arrives prefilled; it is
 * never sent by itself, only by the user's tap on Send.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NewMessageViewModel(
    private val contacts: ContactsRepository,
    private val messages: MessageRepository,
    private val sender: SmsSender,
    initialRecipients: List<String>,
    initialBody: String,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val recipients = MutableStateFlow(initialRecipients.take(MAX_RECIPIENTS).map { Recipient(it) })
    private val query = MutableStateFlow("")
    private val body = MutableStateFlow(initialBody.take(MAX_SEND_LENGTH))
    private val sending = MutableStateFlow(false)

    /** The fields' text as typed, straight from here: never behind the keyboard. */
    val queryText: StateFlow<String> = query.asStateFlow()
    val bodyText: StateFlow<String> = body.asStateFlow()

    private val events = Channel<NewMessageEvent>(Channel.BUFFERED)
    val eventFlow: Flow<NewMessageEvent> = events.receiveAsFlow()

    private val suggestions = query.debounce(DEBOUNCE_MS).flatMapLatest { q ->
        if (q.isBlank()) flowOf(emptyList()) else flow { emit(suggestionsFor(q)) }
    }.onStart { emit(emptyList()) }

    val state: StateFlow<NewMessageUiState> = combine(recipients, query, suggestions, body, sending) { r, q, s, b, busy ->
        NewMessageUiState(r, q, if (q.isBlank()) emptyList() else s.filter { sug -> r.none { PhoneNumbers.same(it.address, sug.number) } }, b, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewMessageUiState(recipients.value, body = body.value))

    init {
        // Names for the numbers a link brought, when they are contacts
        viewModelScope.launch {
            val named = recipients.value.map { it.copy(name = contacts.displayName(it.address)) }
            recipients.update { current -> current.map { r -> named.firstOrNull { it.address == r.address } ?: r } }
        }
    }

    private suspend fun suggestionsFor(q: String): List<Suggestion> {
        val fromContacts = contacts.search(q).flatMap { c -> c.numbers.map { Suggestion(c.name, it) } }.take(MAX_SUGGESTIONS)
        val typed = typedNumber(q)?.takeIf { t -> fromContacts.none { PhoneNumbers.same(it.number, t) } }
        return listOfNotNull(typed?.let { Suggestion(null, it) }) + fromContacts
    }

    /** A comma or semicolon ends a number, as in other messaging apps. */
    fun setQuery(value: String) {
        if (value.endsWith(',') || value.endsWith(';')) {
            query.value = value.dropLast(1)
            commitTyped()
        } else {
            query.value = value
        }
    }

    fun setBody(value: String) {
        body.value = value.take(MAX_SEND_LENGTH)
    }

    fun add(suggestion: Suggestion) {
        addRecipient(Recipient(suggestion.number, suggestion.name))
        query.value = ""
    }

    /** The typed number becomes a chip; false (and the text stays) when it is not a number. */
    fun commitTyped(): Boolean {
        val number = typedNumber(query.value) ?: return false
        addRecipient(Recipient(number))
        query.value = ""
        return true
    }

    private fun addRecipient(r: Recipient) {
        recipients.update { list -> if (list.size >= MAX_RECIPIENTS || list.any { PhoneNumbers.same(it.address, r.address) }) list else list + r }
    }

    fun remove(r: Recipient) {
        recipients.update { it - r }
    }

    /** Only from the Send button. */
    fun send() {
        commitTyped()
        val to = recipients.value.map { it.address }
        val text = body.value.trim()
        if (to.isEmpty() || text.isEmpty() || sending.value) return
        sending.value = true
        appScope.launch {
            val ids = sender.send(to, text)
            val event = when {
                ids.isEmpty() -> NewMessageEvent.Failed
                to.size == 1 -> NewMessageEvent.OpenThread(messages.message(ids.first())?.threadId ?: messages.threadIdFor(to))
                else -> NewMessageEvent.SentToMany(to.size)
            }
            if (event != NewMessageEvent.Failed) body.value = ""
            sending.value = false
            events.trySend(event)
        }
    }
}

package com.havivon.pigeon.data

import android.content.Intent
import com.havivon.pigeon.data.sms.MAX_SEND_LENGTH
import com.havivon.pigeon.domain.model.ComposeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The schemes Android routes to the default SMS app. MMS is out of scope, but an mms: link still opens an SMS draft. */
private val SMS_SCHEMES = setOf("sms", "smsto", "mms", "mmsto")

/** No real link has more; the cap keeps a hostile intent from flooding the draft. */
private const val MAX_RECIPIENTS = 20
private const val MAX_ADDRESS_LENGTH = 40

/** Characters a phone number may be written with. Anything else (a sender ID, a script) is dropped. */
private val ADDRESS_CHARS = Regex("[0-9+*#().\\- ]+")

/**
 * The recipients of an `sms:` link's scheme-specific part ("+972525550123,0525550124?body=..."),
 * each a plausible phone number; anything else is dropped.
 */
fun parseRecipients(schemeSpecificPart: String?): List<String> =
    schemeSpecificPart.orEmpty().substringBefore('?')
        .split(',', ';')
        .map { it.trim() }
        .filter { it.length in 1..MAX_ADDRESS_LENGTH && ADDRESS_CHARS.matches(it) && it.any(Char::isDigit) }
        .distinct()
        .take(MAX_RECIPIENTS)

/** The `body=` parameter of an `sms:` link ("sms:123?body=Hi"), already decoded by Uri. */
fun bodyParameter(schemeSpecificPart: String?): String? {
    val query = schemeSpecificPart?.substringAfter('?', "")?.takeIf { it.isNotEmpty() } ?: return null
    return query.split('&').firstOrNull { it.startsWith("body=") }?.removePrefix("body=")
}

/**
 * A draft from another app's `sms:`/`smsto:` intent, or null when the intent is not one. The text
 * comes from the `sms_body` extra, EXTRA_TEXT or the link's `body=`, in that order, cut to the
 * longest message the app sends. It only ever opens a draft: sending takes the user's tap.
 */
fun composeRequestOf(scheme: String?, schemeSpecificPart: String?, smsBody: String?, extraText: String?): ComposeRequest? {
    if (scheme?.lowercase() !in SMS_SCHEMES) return null
    val body = smsBody ?: extraText ?: bodyParameter(schemeSpecificPart) ?: ""
    return ComposeRequest(recipients = parseRecipients(schemeSpecificPart), body = body.take(MAX_SEND_LENGTH))
}

/**
 * What MainActivity was opened for, held until the screens take it: a draft from another app, or
 * the conversation of a tapped notification. Every value from an intent is validated here, since
 * MainActivity is exported and any app can start it.
 */
object LaunchRequests {

    /** The thread to open, as put on MainActivity's intent by a notification tap. */
    const val EXTRA_THREAD_ID = "com.havivon.pigeon.extra.THREAD_ID"

    /** The extra other apps use for the draft text in an `sms:` intent (not a public constant). */
    private const val EXTRA_SMS_BODY = "sms_body"

    private val _compose = MutableStateFlow<ComposeRequest?>(null)
    private val _openThread = MutableStateFlow<Long?>(null)

    val compose: StateFlow<ComposeRequest?> = _compose.asStateFlow()
    val openThread: StateFlow<Long?> = _openThread.asStateFlow()

    fun consumeCompose() {
        _compose.value = null
    }

    fun consumeOpenThread() {
        _openThread.value = null
    }

    fun handle(intent: Intent?) {
        if (intent == null) return
        try {
            when (intent.action) {
                Intent.ACTION_SEND, Intent.ACTION_SENDTO -> {
                    val data = intent.data ?: return
                    composeRequestOf(
                        scheme = data.scheme,
                        schemeSpecificPart = data.schemeSpecificPart,
                        smsBody = intent.getStringExtra(EXTRA_SMS_BODY),
                        extraText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                    )?.let { _compose.value = it }
                }
                else -> intent.getLongExtra(EXTRA_THREAD_ID, 0L).takeIf { it > 0 }?.let { _openThread.value = it }
            }
        } catch (_: RuntimeException) {
            // A malformed intent from another app (bad parcel, wrong extra types): ignore it
        }
    }
}

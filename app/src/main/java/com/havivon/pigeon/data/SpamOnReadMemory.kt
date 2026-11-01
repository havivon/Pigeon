package com.havivon.pigeon.data

import android.content.Context
import com.havivon.pigeon.domain.analysis.Judged
import com.havivon.pigeon.domain.analysis.JudgedMemory

/**
 * What spam-on-read judged, in the app's private preferences: the rules' version, conversation ids and
 * message times only, never a sender or a text. Without it every start would judge every conversation again.
 */
class SpamOnReadMemory(context: Context) : JudgedMemory {

    private val prefs = context.applicationContext.getSharedPreferences("spam_on_read", Context.MODE_PRIVATE)

    override fun load(): Judged = decodeJudged(prefs.getString(KEY, null))

    override fun save(judged: Judged) {
        prefs.edit().putString(KEY, encodeJudged(judged)).apply()
    }

    private companion object {
        const val KEY = "judged"
    }
}

/** The version before it was stored: what 0.3.9 and earlier wrote ("12:1700000000000;...") was judged with rules 1. */
private const val FIRST_VERSION = 1

/** "v2;12:1700000000000;13:1700000500000": the rules' version, then conversation id and newest message time. */
fun encodeJudged(judged: Judged): String =
    (listOf("v${judged.version}") + judged.threads.entries.map { "${it.key}:${it.value}" }).joinToString(";")

/** What [encodeJudged] wrote; a damaged entry is skipped (that conversation is judged again). Nothing stored is version 0. */
fun decodeJudged(text: String?): Judged {
    if (text.isNullOrEmpty()) return Judged(0, emptyMap())
    var version = FIRST_VERSION
    val out = HashMap<Long, Long>()
    for (entry in text.split(';')) {
        if (entry.startsWith("v")) {
            version = entry.drop(1).toIntOrNull() ?: 0
            continue
        }
        val id = entry.substringBefore(':').toLongOrNull() ?: continue
        val date = entry.substringAfter(':', "").toLongOrNull() ?: continue
        out[id] = date
    }
    return Judged(version, out)
}

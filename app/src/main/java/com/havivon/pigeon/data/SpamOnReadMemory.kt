package com.havivon.pigeon.data

import android.content.Context
import com.havivon.pigeon.domain.analysis.JudgedMemory

/**
 * What spam-on-read judged, in the app's private preferences: conversation ids and message times only,
 * never a sender or a text. Without it a viewer's every start would judge every conversation again.
 */
class SpamOnReadMemory(context: Context) : JudgedMemory {

    private val prefs = context.applicationContext.getSharedPreferences("spam_on_read", Context.MODE_PRIVATE)

    override fun load(): Map<Long, Long> = decodeJudged(prefs.getString(KEY, null))

    override fun save(judged: Map<Long, Long>) {
        prefs.edit().putString(KEY, encodeJudged(judged)).apply()
    }

    private companion object {
        const val KEY = "judged"
    }
}

/** "12:1700000000000;13:1700000500000". */
fun encodeJudged(judged: Map<Long, Long>): String = judged.entries.joinToString(";") { "${it.key}:${it.value}" }

/** The map [encodeJudged] wrote; a damaged entry is skipped (that conversation is judged again). */
fun decodeJudged(text: String?): Map<Long, Long> {
    if (text.isNullOrEmpty()) return emptyMap()
    val out = HashMap<Long, Long>()
    for (entry in text.split(';')) {
        val id = entry.substringBefore(':').toLongOrNull() ?: continue
        val date = entry.substringAfter(':', "").toLongOrNull() ?: continue
        out[id] = date
    }
    return out
}

package com.havivon.pigeon.data.sms

import com.havivon.pigeon.domain.model.SmsConversation
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * The inbox's list as it was last read, so the next launch shows it at once instead of a loading sign while the system
 * SMS database is read again (every message, then the newest text of each conversation and the contacts' names). It is
 * only a first picture: the full read starts at the same moment and replaces it, so nothing that came meanwhile is missed.
 *
 * Kept in the app's cache directory (app-private, never in a backup, and the system may clear it), and only while
 * Pigeon may read SMS. Locked chats are kept without their text ([forSaving]).
 */
class ConversationSnapshot(private val file: File) {

    /** The last list, or null when there is none or it cannot be read (an old or broken file is dropped). */
    fun load(): List<SmsConversation>? {
        if (!file.isFile) return null
        return try {
            decode(file.readText())
        } catch (e: IOException) {
            null
        } catch (e: JSONException) {
            file.delete()
            null
        }
    }

    /** Keeps [list] for the next launch, written whole or not at all (a temp file, then a rename). */
    fun save(list: List<SmsConversation>) {
        try {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(encode(list))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (_: IOException) {
            // Only a faster start is lost: the next launch reads the database as before
        }
    }

    /** No list is kept (Pigeon may no longer read SMS). */
    fun clear() {
        file.delete()
    }

    companion object {
        private const val VERSION = 1

        /** [list] with the text of the [locked] chats left out: a locked chat's message is never copied anywhere. */
        fun forSaving(list: List<SmsConversation>, locked: Set<Long>): List<SmsConversation> =
            list.map { if (it.threadId in locked) it.copy(snippet = "") else it }

        fun encode(list: List<SmsConversation>): String {
            val items = JSONArray()
            for (c in list) {
                items.put(
                    JSONObject()
                        .put("t", c.threadId)
                        .put("a", JSONArray(c.addresses))
                        .put("n", c.contactName ?: JSONObject.NULL)
                        .put("s", c.snippet)
                        .put("d", c.dateMillis)
                        .put("u", c.unreadCount)
                        .put("f", c.hasFailed)
                        .put("i", c.incomingCount)
                        .put("c", c.totalCount)
                        .put("w", c.userWrote)
                        .put("r", JSONArray(c.unreadIds)),
                )
            }
            return JSONObject().put("v", VERSION).put("list", items).toString()
        }

        /** Throws [JSONException] for a file of another version or a broken one. */
        fun decode(text: String): List<SmsConversation> {
            val root = JSONObject(text)
            if (root.getInt("v") != VERSION) throw JSONException("version")
            val items = root.getJSONArray("list")
            return List(items.length()) { i ->
                val o = items.getJSONObject(i)
                val addresses = o.getJSONArray("a")
                val unread = o.getJSONArray("r")
                SmsConversation(
                    threadId = o.getLong("t"),
                    addresses = List(addresses.length()) { addresses.getString(it) },
                    contactName = if (o.isNull("n")) null else o.getString("n"),
                    snippet = o.getString("s"),
                    dateMillis = o.getLong("d"),
                    unreadCount = o.getInt("u"),
                    hasFailed = o.getBoolean("f"),
                    incomingCount = o.getInt("i"),
                    totalCount = o.getInt("c"),
                    userWrote = o.getBoolean("w"),
                    unreadIds = List(unread.length()) { unread.getLong(it) },
                )
            }
        }
    }
}

package com.havivon.pigeon.ui.common

import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.SmsConversation
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * The automatic folder of each conversation, kept from one launch to the next: classifying all of them again (900
 * conversations) took a second of every start. One cache for the whole app, so every screen that maps conversations
 * shares it, and kept in the app's cache directory (app-private, never in a backup).
 *
 * It holds no message text and no sender: for each conversation only its thread id, a 64-bit fingerprint of what the
 * folder is decided from (the senders, the contact's name, the last message, whether the user wrote) and the folder. A
 * conversation whose fingerprint changed is classified again. A file of another [version] of the app (other rules) is
 * dropped whole.
 */
class AnalysisCache(private val file: File?, private val version: Long) {

    private class Entry(val fingerprint: Long, val folder: Folder)

    private val entries = ConcurrentHashMap<Long, Entry>()

    @Volatile
    private var loaded = false

    @Volatile
    private var dirty = false

    /** The folder kept for [c], or null when there is none or [c] changed since. */
    fun folderFor(c: SmsConversation): Folder? {
        load()
        return entries[c.threadId]?.takeIf { it.fingerprint == fingerprint(c) }?.folder
    }

    fun put(c: SmsConversation, folder: Folder) {
        load()
        entries[c.threadId] = Entry(fingerprint(c), folder)
        dirty = true
    }

    /** Writes the cache when anything changed since the last time (after a list was mapped). */
    fun flush() {
        val target = file ?: return
        if (!dirty) return
        dirty = false
        val items = JSONArray()
        for ((thread, e) in entries) items.put(JSONArray().put(thread).put(e.fingerprint).put(e.folder.name))
        try {
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.writeText(JSONObject().put("v", version).put("e", items).toString())
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        } catch (_: IOException) {
            // Only a faster start is lost
        }
    }

    @Synchronized
    private fun load() {
        if (loaded) return
        loaded = true
        val source = file?.takeIf { it.isFile } ?: return
        try {
            val root = JSONObject(source.readText())
            if (root.getLong("v") != version) {
                source.delete()
                return
            }
            val items = root.getJSONArray("e")
            for (i in 0 until items.length()) {
                val e = items.getJSONArray(i)
                val folder = Folder.entries.firstOrNull { it.name == e.getString(2) } ?: continue
                entries.putIfAbsent(e.getLong(0), Entry(e.getLong(1), folder))
            }
        } catch (_: IOException) {
            // Read nothing: every conversation is classified as before
        } catch (_: JSONException) {
            source.delete()
        }
    }

    companion object {
        /** FNV-1a over what the folder is decided from; equal input, equal fingerprint (no text is kept, only this number). */
        fun fingerprint(c: SmsConversation): Long {
            var h = -0x340d631b7bdddcdbL // FNV offset basis
            fun add(s: String) {
                for (ch in s) {
                    h = h xor ch.code.toLong()
                    h *= 0x100000001b3L // FNV prime
                }
                h = h xor 0x1fL
                h *= 0x100000001b3L
            }
            c.addresses.forEach(::add)
            add(c.contactName ?: "\u0000")
            add(c.snippet)
            add(if (c.userWrote) "1" else "0")
            return h
        }
    }
}

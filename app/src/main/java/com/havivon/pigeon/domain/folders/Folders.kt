package com.havivon.pigeon.domain.folders

import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.repository.FoldersRepository
import com.havivon.pigeon.domain.repository.SpamRepository
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * The key a move by hand is kept under. Folders are per conversation, and a conversation with one sender
 * is that sender: its key is [PhoneNumbers.lookupKey], so "+972 52-555-0123" and "052-5550123" (the same
 * person, written two ways, or in a thread Android opened again) share it and the move follows the sender.
 * A group (several senders) is keyed by its thread.
 */
object FolderKeys {
    private const val THREAD = "thread:"

    fun of(threadId: Long, addresses: List<String>): String =
        addresses.mapNotNull { PhoneNumbers.lookupKey(it) }.distinct().singleOrNull() ?: "$THREAD$threadId"

    /** Whether a conversation with these [addresses] is one sender's (its key is the sender's), not a group's. */
    fun bySender(addresses: List<String>): Boolean = addresses.mapNotNull { PhoneNumbers.lookupKey(it) }.distinct().size == 1

    /** The key of one sender (an incoming message). */
    fun sender(address: String): String? = PhoneNumbers.lookupKey(address)

    /** A group's key, from its thread. */
    fun forThread(threadId: Long): String = "$THREAD$threadId"

    /** The thread inside a group's key, or null for a sender's key. */
    fun threadOf(key: String): Long? = if (key.startsWith(THREAD)) key.removePrefix(THREAD).toLongOrNull() else null
}

/** What is wrong with a name typed for a category. */
enum class FolderNameProblem { Blank, TooLong, Taken }

object FolderNames {
    /** The longest name a category may have: it must fit on a chip. */
    const val MAX_LENGTH = 24

    /** The name as kept: trimmed, inner runs of spaces as one. */
    fun clean(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    private fun key(name: String) = clean(name).lowercase(Locale.ROOT)

    /**
     * Why [name] cannot be used, or null when it can: blank, longer than [MAX_LENGTH], or the name of a built-in
     * folder ([reserved], in both languages) or of another of the user's categories, whatever the case
     * ("work" and "Work" are one). [renaming] is the category being renamed: its own name is free.
     */
    fun problem(name: String, reserved: Collection<String>, existing: List<CustomFolder>, renaming: Long? = null): FolderNameProblem? {
        val clean = clean(name)
        if (clean.isEmpty()) return FolderNameProblem.Blank
        if (clean.length > MAX_LENGTH) return FolderNameProblem.TooLong
        val k = key(clean)
        if (reserved.any { key(it) == k }) return FolderNameProblem.Taken
        if (existing.any { it.id != renaming && key(it.name) == k }) return FolderNameProblem.Taken
        return null
    }
}

/**
 * "Move to category": the one place the moves are made, for the inbox's sheet, the conversation's menu and
 * the settings. A move never touches Android's SMS database, so it works for a viewer too.
 *
 * - To [Folder.Spam]: the existing "Mark as spam" (the user's mark beats an earlier "Not spam"); a move by
 *   hand is dropped, so "Not spam" later brings the conversation back to the automatic sorting.
 * - To a built-in folder or a category: kept by [FolderKeys], ahead of the automatic sorting, for every
 *   future message too. A spam conversation is first "Not spam" (its senders join the allow-list).
 * - To null, "Automatic": the move is dropped (and a spam conversation is "Not spam").
 */
class FolderMoves(
    private val folders: FoldersRepository,
    private val spam: SpamRepository,
    /** The built-in folders' names in every language of the app: no category may take one. */
    private val reserved: Collection<String>,
) {
    /** Why [name] cannot be a category's (see [FolderNames.problem]), or null. */
    fun problem(name: String, existing: List<CustomFolder>, renaming: Long? = null): FolderNameProblem? =
        FolderNames.problem(name, reserved, existing, renaming)

    suspend fun move(threadId: Long, addresses: List<String>, isSpam: Boolean, target: FolderChoice?) {
        val key = FolderKeys.of(threadId, addresses)
        if (target == FolderChoice.BuiltIn(Folder.Spam)) {
            folders.clearOverride(key)
            for (a in addresses) {
                // The user's own mark overrides an earlier "not spam"
                spam.removeFromNotSpam(a)
                spam.markSpam(a, threadId, listOf(SpamReason.MarkedByUser))
            }
            return
        }
        if (isSpam) addresses.forEach { spam.markNotSpam(it) }
        if (target == null || target == FolderChoice.BuiltIn(Folder.All)) folders.clearOverride(key) else folders.setOverride(key, target)
    }

    /** A new category named [name] (coloured [argb], if given), with the conversation moved into it; null (nothing made) when the name cannot be used. */
    suspend fun createAndMove(name: String, threadId: Long, addresses: List<String>, isSpam: Boolean, argb: Long? = null): Long? {
        val id = create(name, argb) ?: return null
        move(threadId, addresses, isSpam, FolderChoice.Custom(id))
        return id
    }

    /** A new category at the end of the list, with a colour if [argb] is given; null when the name cannot be used. */
    suspend fun create(name: String, argb: Long? = null): Long? {
        if (problem(name, folders.folders.first()) != null) return null
        return folders.add(FolderNames.clean(name), argb)
    }

    /** Gives a built-in folder or a category a colour, or takes it away (null). Never touches a message. */
    suspend fun setColor(choice: FolderChoice, argb: Long?) = folders.setColor(choice, argb)

    /** False, and nothing changes, when the name cannot be used. */
    suspend fun rename(id: Long, name: String): Boolean {
        if (problem(name, folders.folders.first(), renaming = id) != null) return false
        folders.rename(id, FolderNames.clean(name))
        return true
    }

    /** Deletes the category; its conversations go back to the automatic sorting. */
    suspend fun delete(id: Long) = folders.delete(id)

    suspend fun reorder(idsInOrder: List<Long>) = folders.reorder(idsInOrder)
}

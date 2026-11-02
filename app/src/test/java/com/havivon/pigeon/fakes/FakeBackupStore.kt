package com.havivon.pigeon.fakes

import com.havivon.pigeon.domain.backup.BackupStore
import com.havivon.pigeon.domain.backup.RestorePlan
import com.havivon.pigeon.domain.model.BlockedKeyword
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Favorite
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.Reminder
import kotlinx.coroutines.flow.update

/** [BackupStore] over the in-memory fakes: it replaces what the plan covers and leaves the rest, as the Room version does. */
class FakeBackupStore(
    private val rules: FakeRules,
    private val folders: FakeFolders,
    private val pins: FakePins,
    private val locks: FakeLocks,
    private val spam: FakeSpam,
    private val keywords: FakeBlockedKeywords,
    private val favorites: FakeFavorites,
    private val reminders: FakeReminders,
) : BackupStore {

    var replaced = 0
        private set

    override suspend fun replace(plan: RestorePlan): List<Reminder> {
        replaced++
        rules.state.value = plan.rules.mapIndexed { i, r -> r.copy(id = i + 1) }
        val categories = plan.categories.mapIndexed { i, name -> CustomFolder(i + 1L, name, plan.categoryColors.getOrNull(i)) }
        plan.folderColors?.let { folders.builtInColorState.value = it }
        folders.folderState.value = categories
        folders.overrideState.value = plan.moves.mapNotNull { m ->
            val choice = m.folder?.let { FolderChoice.BuiltIn(it) } ?: m.categoryIndex?.let { categories.getOrNull(it) }?.let { FolderChoice.Custom(it.id) }
            choice?.let { m.key to it }
        }.toMap()
        plan.pinnedThreadIds?.let { pins.state.value = it }
        plan.lockedThreadIds?.let { locks.state.value = it }
        spam.notSpamState.value = plan.notSpam.toSet()
        keywords.state.value = plan.blockedWords.mapIndexed { i, w -> BlockedKeyword(i.toLong(), w) }
        var inserted = emptyList<Reminder>()
        plan.reminders?.let { planned ->
            val next = (reminders.state.value.maxOfOrNull { it.id } ?: 0L) + 100
            inserted = planned.mapIndexed { i, r -> Reminder(next + i, r.threadId, r.messageId, r.text, r.triggerAtMillis, done = false) }
            reminders.state.update { list -> list.filter { it.done } + inserted }
        }
        plan.favorites?.let { planned ->
            favorites.state.value = planned.map { Favorite(it.messageId, it.messageId, it.threadId, it.address, it.senderName, it.body, it.dateMillis, it.starredAtMillis) }
        }
        return inserted
    }
}

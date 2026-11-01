package com.havivon.pigeon.ui.keywords

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havivon.pigeon.ui.navigation.pigeonViewModel

/** The blocked-words screen on real data. */
@Composable
fun BlockedKeywordsRoute(onBack: () -> Unit) {
    val vm = pigeonViewModel { app -> BlockedKeywordsViewModel(app.blockedKeywords) }
    val keywords by vm.keywords.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val duplicate by vm.duplicate.collectAsStateWithLifecycle()
    BlockedKeywordsScreen(
        keywords = keywords,
        draft = draft,
        duplicate = duplicate,
        onDraft = vm::setDraft,
        onAdd = vm::add,
        onDelete = vm::delete,
        onBack = onBack,
    )
}

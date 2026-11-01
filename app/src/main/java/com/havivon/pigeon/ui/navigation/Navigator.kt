package com.havivon.pigeon.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.havivon.pigeon.data.AppContainer

/** The app's screens. Navigation is a plain back stack of these, held by [Navigator]. */
sealed interface Screen {
    data object Inbox : Screen
    /** A conversation; with [messageId] it opens scrolled to that message (the Unread list). */
    data class Thread(val threadId: Long, val messageId: Long? = null) : Screen
    data object Spam : Screen
    data object Search : Screen
    /** A new message; [recipients] and [body] come prefilled from an `sms:` link. */
    data class NewMessage(val recipients: List<String> = emptyList(), val body: String = "") : Screen
    data object Settings : Screen

    /** The colour rules, opened from the settings. */
    data object Rules : Screen

    /** The blocked words, opened from the settings. */
    data object BlockedKeywords : Screen
}

/** A screen on the stack, with its own ViewModels: they are cleared when the screen is popped. */
class BackStackEntry(val id: Long, val screen: Screen) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/**
 * The back stack. A ViewModel itself, so the stack and the screens' ViewModels survive a rotation;
 * everything is cleared with the activity.
 */
class Navigator : ViewModel() {
    private var nextId = 0L

    val stack: SnapshotStateList<BackStackEntry> = mutableStateListOf(entry(Screen.Inbox))

    /** The language the screens' ViewModels were built in (their labels are in it). */
    private var builtFor: String? = null

    val top: BackStackEntry get() = stack.last()

    private fun entry(screen: Screen) = BackStackEntry(nextId++, screen)

    fun push(screen: Screen) {
        if (top.screen != screen) stack += entry(screen)
    }

    /** False when only the inbox is left (the system then closes the app). */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex).viewModelStore.clear()
        return true
    }

    fun replaceTop(screen: Screen) {
        if (stack.size <= 1) {
            push(screen)
            return
        }
        val old = stack.removeAt(stack.lastIndex)
        stack += entry(screen)
        old.viewModelStore.clear()
    }

    /** Removes every screen that matches, keeping the inbox at the bottom. */
    fun removeAll(predicate: (Screen) -> Boolean) {
        val gone = stack.drop(1).filter { predicate(it.screen) }
        stack.removeAll(gone)
        gone.forEach { it.viewModelStore.clear() }
    }

    fun popToInbox() = removeAll { true }

    /**
     * After a language change the activity is recreated but the ViewModels survive, with labels
     * ("Today", weekdays) in the old language: they are rebuilt.
     */
    fun ensureLanguage(tag: String) {
        if (builtFor != null && builtFor != tag) {
            val screens = stack.map { it.screen }
            stack.forEach { it.viewModelStore.clear() }
            stack.clear()
            screens.forEach { stack += entry(it) }
        }
        builtFor = tag
    }

    override fun onCleared() {
        stack.forEach { it.viewModelStore.clear() }
    }
}

/**
 * Shows the top screen of [navigator], giving it its own ViewModel store and remembered state
 * (the inbox keeps its scroll position under a conversation). System back pops the stack;
 * screens add their own back handling (leaving multi-select) on top.
 */
@Composable
fun ScreenHost(navigator: Navigator, content: @Composable (Screen) -> Unit) {
    val holder = rememberSaveableStateHolder()
    val entry = navigator.top
    BackHandler(enabled = navigator.stack.size > 1) { navigator.pop() }
    val ids = navigator.stack.map { it.id }
    val seen = remember { mutableSetOf<Long>() }
    LaunchedEffect(ids) {
        (seen - ids.toSet()).forEach { holder.removeState(it) }
        seen.retainAll(ids.toSet())
        seen.addAll(ids)
    }
    key(entry.id) {
        holder.SaveableStateProvider(entry.id) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides entry) { content(entry.screen) }
        }
    }
}

/** A ViewModel of the current screen, built from the app's [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> pigeonViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = AppContainer.from(LocalContext.current)
    // Only used when the screen has no such ViewModel yet
    val factory: ViewModelProvider.Factory = viewModelFactory { initializer { create(container) } }
    return viewModel(factory = factory)
}

package com.havivon.pigeon.domain.model

import androidx.annotation.StringRes
import com.havivon.pigeon.R

enum class Folder(@StringRes val labelRes: Int) {
    All(R.string.common_folder_all),
    Personal(R.string.common_folder_personal),
    Banks(R.string.common_folder_banks),
    Insurance(R.string.common_folder_insurance),
    Codes(R.string.common_folder_codes),
    Deliveries(R.string.common_folder_deliveries),
    Spam(R.string.common_folder_spam),
    ;

    /** Whether the user can give this folder a colour: not "All" (it holds everything), nor Spam (it keeps its own red). */
    val colourable: Boolean get() = this != All && this != Spam
}

/**
 * A category the user made ("Work", "Family"): a chip after the built-in folders, in the user's order.
 * [argb] is its colour, if the user gave it one (none by default).
 */
data class CustomFolder(val id: Long, val name: String, val argb: Long? = null)

/**
 * Where the user put a conversation by hand ("Move to category"), ahead of the automatic sorting: one of
 * the built-in folders (never [Folder.All] or [Folder.Spam]: spam is the spam verdict), or one of theirs.
 * The inbox also uses it for the chosen chip, where [Folder.All] is a choice like any other.
 */
sealed interface FolderChoice {
    data class BuiltIn(val folder: Folder) : FolderChoice
    data class Custom(val id: Long) : FolderChoice
}

/**
 * The categories as a row is labelled with them: the user's own ([custom], with their colours) and the colours
 * the user gave to built-in folders ([builtInColors]; Personal, Banks, Insurance, Codes and Deliveries can have
 * one, All and Spam never do: spam keeps its own red).
 */
data class Categories(val custom: List<CustomFolder> = emptyList(), val builtInColors: Map<Folder, Long> = emptyMap()) {
    /** The colour of [choice], or null for none. */
    fun colorOf(choice: FolderChoice): Long? = when (choice) {
        is FolderChoice.BuiltIn -> if (choice.folder.colourable) builtInColors[choice.folder] else null
        is FolderChoice.Custom -> custom.firstOrNull { it.id == choice.id }?.argb
    }

    companion object {
        val None = Categories()
    }
}

/**
 * The small label saying which category a conversation (or one of its messages) is in. [argb] is the category's
 * own colour, null when the user gave it none (the tag is then neutral, or takes the colour of a rule that matches).
 */
data class CategoryTag(val label: String, val argb: Long? = null)

/**
 * What colours a row: a matching colour rule first (the user's explicit choice for that sender or word), then the
 * colour of the category it is in, then nothing.
 */
fun rowColor(rule: ColorRule?, category: CategoryTag?): Long? = rule?.argb ?: category?.argb

/** Why the spam filter flagged a conversation. The UI turns each reason into text. */
sealed interface SpamReason {
    data object UnknownSender : SpamReason
    data object ShortLink : SpamReason
    data object LookalikeDomain : SpamReason
    data class Keyword(val word: String) : SpamReason
    /** A word from the user's own blocked list. */
    data class BlockedKeyword(val word: String) : SpamReason
    /** Three or more links in one message. */
    data object ManyLinks : SpamReason
    /** An alphanumeric sender named like a known company, with a link that does not go to that company. */
    data object BrandSender : SpamReason
    /** The user moved it to spam by hand ("Mark as spam"). */
    data object MarkedByUser : SpamReason
    /** A poll or a political campaign: "סקר", "לאיזו מפלגה", "השיבו 1", "election", "reply 1". */
    data object Poll : SpamReason
    /** Money bait: "ייתכן שמגיע לכם כסף", "החזר מס", "בדיקה חינם", "you may be owed". */
    data object MoneyBait : SpamReason
    /** Claims to be a government body, the toll roads or the courts, and asks to click or pay: "פיקוד העורף", "חוב אגרה", "קנס". */
    data object GovImpersonation : SpamReason
    /** An investment or crypto scheme: "ביטקוין", "תשואה מובטחת", "רווח יומי", "guaranteed return". */
    data object Investment : SpamReason
    /** Asks for card details or a code: "הזינו את פרטי הכרטיס", "שלחו את הקוד", "confirm your card". */
    data object AsksForDetails : SpamReason
    /** A phone number from outside Israel. */
    data object ForeignNumber : SpamReason
    /** Asks to call a number or to move to WhatsApp or Telegram (wa.me, t.me). */
    data object ContactElsewhere : SpamReason
    /** Names a sum of money: "₪", ש"ח, "NIS", "$". */
    data object MoneyAmount : SpamReason
    /** Long English text in capital letters. */
    data object AllCaps : SpamReason
    /**
     * The number is on the phone's own blocked list (Android's `BlockedNumberContract`), where Google Messages,
     * Samsung Messages and the Phone app put the numbers the user blocked.
     */
    data object BlockedOnPhone : SpamReason
}

sealed interface RuleMatch {
    data class Sender(val name: String) : RuleMatch
    data class Keyword(val word: String) : RuleMatch
}

/** A user rule that frames matching conversations and bubbles in [argb]. The first match wins. */
data class ColorRule(
    val id: Int,
    val label: String,
    val match: RuleMatch,
    val argb: Long,
    val enabled: Boolean = true,
)

data class Conversation(
    /** The system thread id. */
    val id: Long,
    val name: String,
    val snippet: String,
    val time: String,
    val avatarArgb: Long,
    val unread: Int = 0,
    val pinned: Boolean = false,
    val ruleId: Int? = null,
    val folder: Folder = Folder.Personal,
    /** A one-time code found in the last message, offered as a copy button. */
    val code: String? = null,
    /** Why the spam filter flagged it; empty when it is not spam. */
    val spamReasons: List<SpamReason> = emptyList(),
    /** The other side's addresses as the system stores them (for calling and spam marks). */
    val addresses: List<String> = emptyList(),
    /** Behind fingerprint or screen lock: the list shows no text of it. */
    val locked: Boolean = false,
    /** How many messages came in (the Spam folder counts a sender's messages). */
    val messageCount: Int = 0,
    /** The user's own category it was moved to; its [folder] is then [Folder.All] (no built-in one). */
    val customFolderId: Long? = null,
    /** The user moved it by hand ([folder] or [customFolderId] is theirs, not the automatic sorting's). */
    val folderOverridden: Boolean = false,
    /** Which category it is in, with that category's colour; null when it is in none (only "All"). */
    val category: CategoryTag? = null,
) {
    val isSpam: Boolean get() = spamReasons.isNotEmpty()
}

/** One unread incoming message, as a row of the inbox's unread list (the "Unread" toggle): the message, not its whole conversation. */
data class UnreadMessage(
    /** The system message id. */
    val messageId: Long,
    /** The conversation it belongs to (a tap opens it at this message). */
    val threadId: Long,
    /** The contact's name, otherwise the number. */
    val name: String,
    /** The message text; the "locked chat" line for a locked conversation. */
    val text: String,
    val time: String,
    val avatarArgb: Long,
    /** The first colour rule that matches the sender or the message text. */
    val ruleId: Int? = null,
    /** A one-time code found in the text, offered as a copy button. */
    val code: String? = null,
    /** Behind fingerprint or screen lock: no text, no code. */
    val locked: Boolean = false,
    /** The category of its conversation, with that category's colour; null when it is in none. */
    val category: CategoryTag? = null,
)

data class Message(
    /** The system message id. */
    val id: Long,
    val text: String,
    val time: String,
    val outgoing: Boolean,
    /** A date or time found in the text, offered as "create a reminder": the button's label. */
    val reminder: String? = null,
    /** When that reminder would fire. */
    val reminderAtMillis: Long? = null,
    /** A reminder was already created from this message. */
    val reminderSet: Boolean = false,
    val status: SendStatus = SendStatus.Done,
    val starred: Boolean = false,
    /** The frame colour of the first colour rule that matches this message, if any. */
    val ruleArgb: Long? = null,
)

/** Where an outgoing message stands. Incoming messages are always [Done]. */
enum class SendStatus { Done, Sending, Failed }

/** A message the user starred, shown by the favourites widget. */
data class FavoriteMessage(
    val id: Int,
    val sender: String,
    val text: String,
    val date: String,
    val avatarArgb: Long,
)

sealed interface ThreadItem {
    data class Day(val label: String) : ThreadItem
    data class Msg(val message: Message) : ThreadItem
}

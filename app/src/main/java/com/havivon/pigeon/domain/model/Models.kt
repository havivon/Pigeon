package com.havivon.pigeon.domain.model

import androidx.annotation.StringRes
import com.havivon.pigeon.R

enum class Folder(@StringRes val labelRes: Int) {
    All(R.string.common_folder_all),
    Personal(R.string.common_folder_personal),
    Banks(R.string.common_folder_banks),
    Codes(R.string.common_folder_codes),
    Deliveries(R.string.common_folder_deliveries),
    Spam(R.string.common_folder_spam),
}

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
    val id: Int,
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
) {
    val isSpam: Boolean get() = spamReasons.isNotEmpty()
}

data class Message(
    val id: Int,
    val text: String,
    val time: String,
    val outgoing: Boolean,
    /** A date or time found in the text, offered as "create a reminder". */
    val reminder: String? = null,
)

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

package com.hebsms.app.domain.model

enum class Folder(val label: String) {
    All("הכל"),
    Personal("אישי"),
    Banks("בנקים"),
    Codes("קודים"),
    Deliveries("משלוחים"),
    Spam("ספאם"),
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
    val spamReasons: List<String> = emptyList(),
) {
    val isSpam: Boolean get() = spamReasons.isNotEmpty()
}

data class Message(
    val id: Int,
    val text: String,
    val time: String,
    val outgoing: Boolean,
    val delivered: Boolean = false,
    /** A date or time found in the text, offered as "create a reminder". */
    val reminder: String? = null,
)

sealed interface ThreadItem {
    data class Day(val label: String) : ThreadItem
    data class Msg(val message: Message) : ThreadItem
}

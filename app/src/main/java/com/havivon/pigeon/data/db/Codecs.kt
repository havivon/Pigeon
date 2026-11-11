package com.havivon.pigeon.data.db

import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SpamReason

/**
 * Spam reasons as one text column: a line per reason. Unknown lines are skipped, so a database
 * written by a later version (with new reasons) still reads.
 */
object SpamReasonCodec {
    private const val UNKNOWN_SENDER = "unknown_sender"
    private const val SHORT_LINK = "short_link"
    private const val LOOKALIKE_DOMAIN = "lookalike_domain"
    private const val MANY_LINKS = "many_links"
    private const val BRAND_SENDER = "brand_sender"
    private const val MARKED_BY_USER = "marked_by_user"
    private const val POLL = "poll"
    private const val MONEY_BAIT = "money_bait"
    private const val GOV_IMPERSONATION = "gov_impersonation"
    private const val INVESTMENT = "investment"
    private const val ASKS_FOR_DETAILS = "asks_for_details"
    private const val FOREIGN_NUMBER = "foreign_number"
    private const val CONTACT_ELSEWHERE = "contact_elsewhere"
    private const val DRUGS = "drugs"
    private const val REDIRECT_NUMBER = "redirect_number"
    private const val MONEY_AMOUNT = "money_amount"
    private const val ALL_CAPS = "all_caps"
    private const val BLOCKED_ON_PHONE = "blocked_on_phone"
    private const val KEYWORD = "keyword:"
    private const val BLOCKED_KEYWORD = "blocked:"

    fun encode(reasons: List<SpamReason>): String = reasons.joinToString("\n") { r ->
        when (r) {
            SpamReason.UnknownSender -> UNKNOWN_SENDER
            SpamReason.ShortLink -> SHORT_LINK
            SpamReason.LookalikeDomain -> LOOKALIKE_DOMAIN
            SpamReason.ManyLinks -> MANY_LINKS
            SpamReason.BrandSender -> BRAND_SENDER
            SpamReason.MarkedByUser -> MARKED_BY_USER
            SpamReason.Poll -> POLL
            SpamReason.MoneyBait -> MONEY_BAIT
            SpamReason.GovImpersonation -> GOV_IMPERSONATION
            SpamReason.Investment -> INVESTMENT
            SpamReason.AsksForDetails -> ASKS_FOR_DETAILS
            SpamReason.ForeignNumber -> FOREIGN_NUMBER
            SpamReason.ContactElsewhere -> CONTACT_ELSEWHERE
            SpamReason.Drugs -> DRUGS
            SpamReason.RedirectNumber -> REDIRECT_NUMBER
            SpamReason.MoneyAmount -> MONEY_AMOUNT
            SpamReason.AllCaps -> ALL_CAPS
            SpamReason.BlockedOnPhone -> BLOCKED_ON_PHONE
            // A line break inside the word would split the reason
            is SpamReason.Keyword -> KEYWORD + r.word.oneLine()
            is SpamReason.BlockedKeyword -> BLOCKED_KEYWORD + r.word.oneLine()
        }
    }

    private fun String.oneLine() = replace('\n', ' ').replace('\r', ' ')

    fun decode(value: String): List<SpamReason> = value.split('\n').mapNotNull { line ->
        when {
            line == UNKNOWN_SENDER -> SpamReason.UnknownSender
            line == SHORT_LINK -> SpamReason.ShortLink
            line == LOOKALIKE_DOMAIN -> SpamReason.LookalikeDomain
            line == MANY_LINKS -> SpamReason.ManyLinks
            line == BRAND_SENDER -> SpamReason.BrandSender
            line == MARKED_BY_USER -> SpamReason.MarkedByUser
            line == POLL -> SpamReason.Poll
            line == MONEY_BAIT -> SpamReason.MoneyBait
            line == GOV_IMPERSONATION -> SpamReason.GovImpersonation
            line == INVESTMENT -> SpamReason.Investment
            line == ASKS_FOR_DETAILS -> SpamReason.AsksForDetails
            line == FOREIGN_NUMBER -> SpamReason.ForeignNumber
            line == CONTACT_ELSEWHERE -> SpamReason.ContactElsewhere
            line == DRUGS -> SpamReason.Drugs
            line == REDIRECT_NUMBER -> SpamReason.RedirectNumber
            line == MONEY_AMOUNT -> SpamReason.MoneyAmount
            line == ALL_CAPS -> SpamReason.AllCaps
            line == BLOCKED_ON_PHONE -> SpamReason.BlockedOnPhone
            line.startsWith(KEYWORD) -> SpamReason.Keyword(line.removePrefix(KEYWORD))
            line.startsWith(BLOCKED_KEYWORD) -> SpamReason.BlockedKeyword(line.removePrefix(BLOCKED_KEYWORD))
            else -> null
        }
    }
}

fun ColorRuleEntity.toRule(): ColorRule = ColorRule(
    id = id,
    label = label,
    match = if (kind == ColorRuleEntity.KIND_KEYWORD) RuleMatch.Keyword(value) else RuleMatch.Sender(value),
    argb = argb,
    enabled = enabled,
)

fun ColorRule.toEntity(position: Int): ColorRuleEntity = ColorRuleEntity(
    id = id,
    position = position,
    label = label,
    kind = when (match) {
        is RuleMatch.Sender -> ColorRuleEntity.KIND_SENDER
        is RuleMatch.Keyword -> ColorRuleEntity.KIND_KEYWORD
    },
    value = when (val m = match) {
        is RuleMatch.Sender -> m.name
        is RuleMatch.Keyword -> m.word
    },
    argb = argb,
    enabled = enabled,
)

/**
 * The positions after a drag: [idsInOrder] first, in that order, then every other rule (or category) in its
 * current order. Unknown ids are ignored. Returns id → new position.
 */
fun <K> reorderedPositions(currentInOrder: List<K>, idsInOrder: List<K>): Map<K, Int> {
    val known = currentInOrder.toSet()
    val listed = idsInOrder.filter { it in known }.distinct()
    val rest = currentInOrder.filter { it !in listed.toSet() }
    return (listed + rest).withIndex().associate { (index, id) -> id to index }
}

/**
 * A move by hand as two columns: the built-in folder's name, or the user's category id. Null for a value
 * this version does not know (a folder a later version added), which then counts as no move.
 */
object FolderChoiceCodec {
    fun decode(builtIn: String?, customFolderId: Long?): FolderChoice? = when {
        customFolderId != null -> FolderChoice.Custom(customFolderId)
        builtIn != null -> Folder.entries.firstOrNull { it.name == builtIn && it.isMoveTarget }?.let { FolderChoice.BuiltIn(it) }
        else -> null
    }

    /** The two columns, or null for a choice that is not kept (All, Spam). */
    fun encode(choice: FolderChoice): Pair<String?, Long?>? = when (choice) {
        is FolderChoice.Custom -> null to choice.id
        is FolderChoice.BuiltIn -> if (choice.folder.isMoveTarget) choice.folder.name to null else null
    }

}

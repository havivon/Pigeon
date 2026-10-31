package com.havivon.pigeon.data.db

import com.havivon.pigeon.domain.model.ColorRule
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
    private const val KEYWORD = "keyword:"

    fun encode(reasons: List<SpamReason>): String = reasons.joinToString("\n") { r ->
        when (r) {
            SpamReason.UnknownSender -> UNKNOWN_SENDER
            SpamReason.ShortLink -> SHORT_LINK
            SpamReason.LookalikeDomain -> LOOKALIKE_DOMAIN
            // A line break inside the word would split the reason
            is SpamReason.Keyword -> KEYWORD + r.word.replace('\n', ' ').replace('\r', ' ')
        }
    }

    fun decode(value: String): List<SpamReason> = value.split('\n').mapNotNull { line ->
        when {
            line == UNKNOWN_SENDER -> SpamReason.UnknownSender
            line == SHORT_LINK -> SpamReason.ShortLink
            line == LOOKALIKE_DOMAIN -> SpamReason.LookalikeDomain
            line.startsWith(KEYWORD) -> SpamReason.Keyword(line.removePrefix(KEYWORD))
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
 * The positions after a drag: [idsInOrder] first, in that order, then every other rule in its
 * current order. Unknown ids are ignored. Returns id → new position.
 */
fun reorderedPositions(currentInOrder: List<Int>, idsInOrder: List<Int>): Map<Int, Int> {
    val known = currentInOrder.toSet()
    val listed = idsInOrder.filter { it in known }.distinct()
    val rest = currentInOrder.filter { it !in listed.toSet() }
    return (listed + rest).withIndex().associate { (index, id) -> id to index }
}

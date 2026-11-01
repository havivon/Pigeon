package com.havivon.pigeon.data.db

import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SpamReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecsTest {

    @Test
    fun `spam reasons survive the round trip`() {
        val reasons = listOf(
            SpamReason.UnknownSender, SpamReason.ShortLink, SpamReason.LookalikeDomain, SpamReason.Keyword("זכית"),
            SpamReason.BlockedKeyword("הלוואה"), SpamReason.ManyLinks, SpamReason.BrandSender, SpamReason.MarkedByUser,
        )
        assertEquals(reasons, SpamReasonCodec.decode(SpamReasonCodec.encode(reasons)))
    }

    @Test
    fun `no reasons is an empty column and back`() {
        assertEquals("", SpamReasonCodec.encode(emptyList()))
        assertTrue(SpamReasonCodec.decode("").isEmpty())
    }

    @Test
    fun `a line break in a keyword cannot split it`() {
        val decoded = SpamReasonCodec.decode(SpamReasonCodec.encode(listOf(SpamReason.Keyword("click\nhere"))))
        assertEquals(listOf(SpamReason.Keyword("click here")), decoded)
    }

    @Test
    fun `reasons from a later version are skipped`() {
        assertEquals(listOf(SpamReason.ShortLink), SpamReasonCodec.decode("future_reason\nshort_link"))
    }

    @Test
    fun `colour rules survive the round trip`() {
        val sender = ColorRule(id = 4, label = "Bank", match = RuleMatch.Sender("Leumi"), argb = 0xFF2E7D32, enabled = false)
        val keyword = ColorRule(id = 5, label = "Deliveries", match = RuleMatch.Keyword("משלוח"), argb = 0xFFEF6C00)
        assertEquals(sender, sender.toEntity(position = 0).toRule())
        assertEquals(keyword, keyword.toEntity(position = 1).toRule())
        assertEquals(ColorRuleEntity.KIND_KEYWORD, keyword.toEntity(1).kind)
    }

    @Test
    fun `reorder puts the listed rules first, the rest after in their order`() {
        assertEquals(mapOf(3 to 0, 1 to 1, 2 to 2), reorderedPositions(listOf(1, 2, 3), listOf(3, 1, 2)))
        assertEquals(mapOf(3 to 0, 1 to 1, 2 to 2, 4 to 3), reorderedPositions(listOf(1, 2, 3, 4), listOf(3)))
    }

    @Test
    fun `reorder ignores unknown and repeated ids`() {
        assertEquals(mapOf(2 to 0, 1 to 1), reorderedPositions(listOf(1, 2), listOf(9, 2, 2)))
    }
}

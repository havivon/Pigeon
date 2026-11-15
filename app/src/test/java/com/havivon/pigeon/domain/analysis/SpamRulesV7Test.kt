package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.seam.IncomingMessageHook
import com.havivon.pigeon.fakes.FakeBlockedKeywords
import com.havivon.pigeon.fakes.FakeContacts
import com.havivon.pigeon.fakes.FakeMessageRepository
import com.havivon.pigeon.fakes.FakeSpam
import com.havivon.pigeon.fakes.conversation
import com.havivon.pigeon.fakes.sms
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of version 7 ([SpamScorer.VERSION]): a one-time code is never spam by score alone; receipts on receipt sites. All made up. */
class SpamRulesV7Test {

    private fun verdict(body: String, address: String = "0541234567", blocked: List<String> = emptyList()) =
        SpamScorer.score(IncomingSms(address, null, body, blockedKeywords = blocked))

    @Test
    fun theRulesAreVersionSeven() {
        assertTrue("version 7 or a later one", SpamScorer.VERSION >= 7)
    }

    @Test
    fun anInsurersCodeWithAnOptOutLineAndAShortLinkIsNotSpam() {
        // The shape of an insurer's login code that carries its newsletter's footer: opt-out line and a shortened link
        val body = "הקוד שלך לכניסה לאזור האישי הוא 662011. לפרטים נוספים ולמבצעי הקיץ של החברה: bit.ly/summer2024 להסרה השב הסר"
        for (address in listOf("Harel", "HarelIns", "0525556677", "ShopX", "+972541234567")) {
            val v = verdict(body, address)
            assertFalse("$address -> ${v.score} ${v.reasons}", v.isSpam)
            assertTrue("$address keeps below the threshold", v.score < SpamScorer.SPAM_THRESHOLD)
        }
        assertEquals(com.havivon.pigeon.domain.model.Folder.Codes, FolderClassifier.classify(IncomingSms("Harel", null, body)))
    }

    @Test
    fun aCodeFromAStrangerWithManySignsIsStillBelowTheThreshold() {
        val body = "Your verification code is 482913. Claim your prize now: https://bit.ly/x7Kq9 reply STOP to opt out. Free gift!"
        assertFalse(verdict(body, "+447700900123").isSpam)
        assertFalse(verdict("Code 8821 לא לשתף. הצטרפו לקבוצת ההשקעות שלנו bit.ly/inv הסר", "0551112222").isSpam)
    }

    @Test
    fun aBlockedWordStillMakesACodeMessageSpam() {
        val body = "קוד האימות שלך 482913, מבצע מיוחד לחברי המועדון"
        assertFalse(verdict(body, "ClubX").isSpam)
        assertTrue(verdict(body, "ClubX", blocked = listOf("מועדון")).isSpam)
    }

    @Test
    fun theGuardOnlyTouchesMessagesWithACode() {
        // The same footer and link with no code: the scoring is as before
        assertTrue(verdict("מבצעי הקיץ של החברה: bit.ly/summer2024 זכית בפרס, לחץ כאן להסרה השב הסר", "0525556677").isSpam)
        assertTrue(verdict("זכית! לחץ כאן bit.ly/x חיוב יתרה בחשבון", "0551234567").isSpam)
    }

    @Test
    fun aReceiptOnAReceiptSiteIsNoShortLinkScam() {
        val receipt = "הגיעה אליך קבלה על סך 145 ₪: wee.ai/a1b2c"
        val v = verdict(receipt, "0771234567")
        assertFalse("${v.score} ${v.reasons}", v.isSpam)
        assertFalse(SpamReason.ShortLink in v.reasons)
        assertFalse(SpamReason.ShortLink in verdict("הקבלה שלך: payper.co.il/x9", "0771234567").reasons)
        // Not a blanket pass: only these sites, and only with the words of a receipt; any other short link in a receipt still counts
        assertTrue(ServiceCategories.isReceiptWithLink("wee.ai", receipt))
        assertTrue(ServiceCategories.isReceiptWithLink("www.payper.co.il", "החשבונית שלך: payper.co.il/x9"))
        assertFalse(ServiceCategories.isReceiptWithLink("wee.ai", "היי, הצצו בקישור הזה: wee.ai/a1b2c"))
        assertFalse(ServiceCategories.isReceiptWithLink("bit.ly", receipt))
        assertTrue(SpamReason.ShortLink in verdict("הגיעה אליך קבלה: bit.ly/a1b2c", "0771234567").reasons)
    }

    @Test
    fun theNewCategoriesBrandsWithTheirAdsStayBelowTheThreshold() {
        for ((body, address) in listOf(
            "מבצע סלולר! 50GB ב-29 ₪ בלבד. להסרה השב הסר" to "Cellcom",
            "הטבה מיוחדת לחברי מכבי: 20% הנחה בחנויות. להסרה השב הסר" to "Maccabi",
            "טיסה לאתונה ב-199$! להסרה השב הסר" to "Israir",
            "מבצע סוף עונה! 20% הנחה בכל החנות. להסרה השב הסר" to "Shufersal",
            "פרסומת: הנחה בנסיעה הראשונה. להסרה השב הסר" to "Gett",
        )) {
            assertFalse("$address -> ${verdict(body, address).reasons}", verdict(body, address).isSpam)
        }
    }

    // --- an earlier verdict of the filter on a code is dropped when the message is read again ---

    private val spam = FakeSpam()
    private val messages = FakeMessageRepository()
    private val blocked = FakeBlockedKeywords()
    private val contacts = FakeContacts(emptyList())
    private val realHook = SpamFilterHook(contacts, spam, blocked) { t -> messages.recentMessages(t, 50).any { it.type.outgoing } }
    private val hook = IncomingMessageHook { realHook.onStored(it) }
    private val release = CodeSpamRelease(spam, blocked)
    private val judge = SpamOnRead(hook, release = release::release) { t -> messages.recentMessages(t, 50).lastOrNull { !it.type.outgoing } }

    private fun seed(body: String, address: String = "Harel") {
        messages.allMessages.value = listOf(sms(10, 1, body, 1_000, address = address, type = SmsType.Inbox))
    }

    private val codeBody = "הקוד שלך לכניסה הוא 662011. מבצעי הקיץ: bit.ly/summer2024 להסרה השב הסר"

    @Test
    fun `a verdict of the filter on a code is dropped, so the conversation leaves Spam`() = runTest {
        seed(codeBody, "ShopX")
        spam.markSpam("ShopX", 1, listOf(SpamReason.ShortLink, SpamReason.Keyword("הסר")))
        judge.judge(listOf(conversation(1, "ShopX", dateMillis = 1_000)), spam.verdicts.first())
        assertNull(spam.verdictFor("ShopX"))
        // The sender is not on the allow-list: the filter keeps judging what comes next
        assertTrue(spam.notSpamState.value.isEmpty())
    }

    @Test
    fun `the user's own mark, a blocked word and a message without a code are never released`() = runTest {
        seed(codeBody, "ShopX")
        spam.markSpam("ShopX", 1, listOf(SpamReason.MarkedByUser))
        judge.judge(listOf(conversation(1, "ShopX", dateMillis = 1_000)), spam.verdicts.first())
        assertEquals(1, spam.verdictState.value.size)

        spam.verdictState.value = emptyList()
        spam.markSpam("ShopX", 1, listOf(SpamReason.BlockedKeyword("קיץ")))
        val again = SpamOnRead(hook, release = release::release) { t -> messages.recentMessages(t, 50).lastOrNull { !it.type.outgoing } }
        again.judge(listOf(conversation(1, "ShopX", dateMillis = 1_000)), spam.verdicts.first())
        assertEquals(1, spam.verdictState.value.size)

        spam.verdictState.value = emptyList()
        seed("זכית! לחץ כאן bit.ly/x חיוב יתרה בחשבון", "ShopX")
        spam.markSpam("ShopX", 1, listOf(SpamReason.ShortLink))
        SpamOnRead(hook, release = release::release) { t -> messages.recentMessages(t, 50).lastOrNull { !it.type.outgoing } }
            .judge(listOf(conversation(1, "ShopX", dateMillis = 1_000)), spam.verdicts.first())
        assertEquals(1, spam.verdictState.value.size)
    }

    @Test
    fun `version 8 - a verdict of the filter on a known sender's survey is dropped, the user's own mark is not`() = runTest {
        val survey = "שלום, נשמח למענה על שאלון קצר על הביקור במרפאה: https://survey.example.co.il/q להסרה השב הסר"
        seed(survey, "MaccabiSKR")
        spam.markSpam("MaccabiSKR", 1, listOf(SpamReason.Poll, SpamReason.Keyword("הסר")))
        judge.judge(listOf(conversation(1, "MaccabiSKR", dateMillis = 1_000)), spam.verdicts.first())
        assertNull(spam.verdictFor("MaccabiSKR"))

        spam.markSpam("MaccabiSKR", 1, listOf(SpamReason.MarkedByUser))
        SpamOnRead(hook, release = release::release) { t -> messages.recentMessages(t, 50).lastOrNull { !it.type.outgoing } }
            .judge(listOf(conversation(1, "MaccabiSKR", dateMillis = 1_000)), spam.verdicts.first())
        assertEquals(1, spam.verdictState.value.size)
    }
}

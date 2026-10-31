package com.havivon.pigeon.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkAnalyzerTest {

    private fun hosts(text: String) = LinkAnalyzer.findLinks(text).map { it.host }

    @Test
    fun findsLinksInHebrewAndEnglishText() {
        val cases = listOf(
            "זכית בפרס! לחץ כאן: bit.ly/x7Kq9" to listOf("bit.ly"),
            "בקרו באתר www.leumi.co.il." to listOf("www.leumi.co.il"),
            "ראה LEUMI.CO.IL/Login עכשיו" to listOf("leumi.co.il"),
            "פרטים בקישור tinyurl.com/abc123 תודה" to listOf("tinyurl.com"),
            "Visit https://Example.COM/Path?x=1&y=2 today" to listOf("example.com"),
            "HTTP://BIT.LY/ABC" to listOf("bit.ly"),
            "go to (bit.ly/abc), ok" to listOf("bit.ly"),
            "שלח ל-bit.ly/abc" to listOf("bit.ly"),
            "היכנסו:hapoalim.co.il/x" to listOf("hapoalim.co.il"),
            "Click http://paypal.com:8080/login now" to listOf("paypal.com"),
            "a.com and b.co.il/x and https://c.org" to listOf("a.com", "b.co.il", "c.org"),
        )
        for ((text, expected) in cases) assertEquals(text, expected, hosts(text))
    }

    @Test
    fun trailingPunctuationIsNotPartOfTheLink() {
        val cases = listOf(
            "ראה https://bit.ly/3AbCd." to "https://bit.ly/3AbCd",
            "Open bit.ly/abc, then reply" to "bit.ly/abc",
            "Is it bit.ly/abc?" to "bit.ly/abc",
            "(https://t.co/abc)" to "https://t.co/abc",
            "see \"cutt.ly/AbC\"!" to "cutt.ly/AbC",
            "www.leumi.co.il." to "www.leumi.co.il",
            "כתובת: israelpost.co.il!" to "israelpost.co.il",
        )
        for ((text, expected) in cases) {
            val link = LinkAnalyzer.findLinks(text).single()
            assertEquals(text, expected, link.text)
            assertEquals(text, expected, text.substring(link.range))
        }
    }

    @Test
    fun rtlTextEmojiAndDirectionMarksEndTheLink() {
        val rtl = "‏ראה ‪https://goo.gl/abc‬ בבקשה"
        val link = LinkAnalyzer.findLinks(rtl).single()
        assertEquals("https://goo.gl/abc", link.text)
        assertEquals("goo.gl", link.host)

        assertEquals("cutt.ly/AbC", LinkAnalyzer.findLinks("🎁 cutt.ly/AbC 🎁").single().text)
        assertEquals("bit.ly/abc", LinkAnalyzer.findLinks("bit.ly/abc🙏").single().text)
        assertEquals("bit.ly/abc", LinkAnalyzer.findLinks("bit.ly/abcותודה").single().text)
    }

    @Test
    fun textThatOnlyLooksLikeALinkIsIgnored() {
        val cases = listOf(
            "הפגישה ב-10.30 ואחר כך 3.90 ₪",
            "מחיר 12.50 ושלום",
            "e.g. this and i.e. that",
            "open file.pdf or notes.txt",
            "mail me at user@example.com",
            "Thanks.It was good",
            "Version 1.2.3 is out",
            "ok.So what",
            "",
        )
        for (text in cases) assertEquals(text, emptyList<String>(), hosts(text))
    }

    @Test
    fun shortenersAreRecognised() {
        val shorteners = listOf(
            "bit.ly/a", "https://tinyurl.com/a", "t.co/a", "goo.gl/a", "cutt.ly/a", "tiny.cc/a", "rb.gy/a", "is.gd/a",
            "s.id/a", "ow.ly/a", "www.bit.ly/a", "BIT.LY/A", "shorturl.at/a", "t.ly/a", "bit.do/a",
        )
        for (url in shorteners) {
            val link = LinkAnalyzer.findLinks("לחץ $url").single()
            assertTrue(url, link.isShortener)
        }
        for (url in listOf("leumi.co.il/a", "https://example.com/bit.ly", "wit.ly.example.com/a", "habit.ly/a", "gov.il")) {
            assertTrue(url, LinkAnalyzer.findLinks("ראה $url").none { it.isShortener })
        }
    }

    @Test
    fun realHostIsExposed() {
        val link = LinkAnalyzer.findLinks("https://paypal.com@evil-site.top/login").single()
        assertEquals("evil-site.top", link.host)
        assertTrue(link.hasUserInfo)
        assertTrue(link.isLookalike)
        assertFalse(LinkAnalyzer.findLinks("https://leumi.co.il/login").single().hasUserInfo)
    }

    @Test
    fun punycodeHostsAreFlagged() {
        val link = LinkAnalyzer.findLinks("https://xn--pypal-4ve.com/login").single()
        assertTrue(link.isPunycode)
        assertTrue(link.isLookalike)
        assertTrue(LinkAnalyzer.findLinks("xn--80ak6aa92e.com/x").single().isPunycode)
        assertFalse(LinkAnalyzer.findLinks("example.com/x").single().isPunycode)
    }

    @Test
    fun lookalikeHostsAreFlaggedWithTheBrandTheyImitate() {
        val cases = listOf(
            "isr-post.co" to "israelpost",
            "israelpost-il.com" to "israelpost",
            "israel-post.top" to "israelpost",
            "doar-israel.com" to "israelpost",
            "israpost.co.il" to "israelpost",
            "isreal-post.com" to "israelpost",
            "doar.israelpost.co.il.pay-now.xyz" to "israelpost",
            "gov-il.refund.com" to "gov.il",
            "gov.il.tax-refund.top" to "gov.il",
            "govil.co" to "gov.il",
            "leumi-secure.com" to "leumi",
            "bank-leumi.xyz" to "leumi",
            "leum1.co.il" to "leumi",
            "leuimi.co.il" to "leumi",
            "hapoalim-online.net" to "hapoalim",
            "mizrahi-tefahot-login.com" to "mizrahi",
            "discountbank-il.com" to "discount",
            "paypa1.com" to "paypal",
            "paypall.com" to "paypal",
            "payapl.com" to "paypal",
            "paypal-support.top" to "paypal",
            "www.paypal.com.verify-id.xyz" to "paypal",
            "amaz0n-orders.com" to "amazon",
            "arnazon.com" to "amazon",
            "amazon.com.evil.top" to "amazon",
            "netflix-billing.net" to "netflix",
            "netf1ix.com" to "netflix",
            "usps-tracking.top" to "usps",
            "usps.package-fee.com" to "usps",
            "dhl-parcel.info" to "dhl",
            "dhl24-track.com" to "dhl",
            "fedex-delivery.xyz" to "fedex",
        )
        for ((host, brand) in cases) assertEquals(host, brand, LinkAnalyzer.lookalikeOf(host))
    }

    @Test
    fun genuineAndUnrelatedHostsAreNotFlagged() {
        val hosts = listOf(
            "israelpost.co.il", "doar.israelpost.co.il", "www.israelpost.co.il", "www.gov.il", "tel-aviv.gov.il", "my.gov.il",
            "leumi.co.il", "www.leumi.co.il", "bankleumi.co.il", "leumi-card.co.il", "hapoalim.co.il", "www.bankhapoalim.co.il",
            "discountbank.co.il", "mizrahi-tefahot.co.il", "isracard.co.il", "paypal.com", "www.paypal.com", "paypal.me",
            "amazon.com", "amazon.co.il", "smile.amazon.com", "s3.amazonaws.com", "usps.com", "dhl.com", "fedex.com",
            "netflix.com", "ups.com", "bit.ly", "example.com", "google.com", "discount-shoes.co.il", "lumi.com", "maxim.co.il",
            "amazing-deals.com", "groups.google.com", "cups.com", "postnet.co.il", "dhlx.com", "mypost.co.il", "wolt.com",
        )
        for (host in hosts) assertNull(host, LinkAnalyzer.lookalikeOf(host))
    }

    @Test
    fun findLinksFillsInTheLookalikeBrand() {
        val link = LinkAnalyzer.findLinks("חבילה ממתינה לך: isr-post.co/pay").single()
        assertEquals("isr-post.co", link.host)
        assertEquals("israelpost", link.lookalikeOf)
        assertTrue(link.isLookalike)
        assertFalse(link.isShortener)

        val genuine = LinkAnalyzer.findLinks("מעקב: https://israelpost.co.il/track").single()
        assertNull(genuine.lookalikeOf)
        assertFalse(genuine.isLookalike)
    }

    @Test
    fun senderNamesMapToBrands() {
        val cases = listOf(
            "Leumi" to "leumi", "BANKLEUMI" to "leumi", "Bank Hapoalim" to "hapoalim", "Israel Post" to "israelpost",
            "IsraelPost" to "israelpost", "דואר ישראל" to "israelpost", "לאומי" to "leumi", "DHL" to "dhl", "DHL Express" to "dhl",
            "PayPal" to "paypal", "GOV.IL" to "gov.il", "gov-il" to "gov.il", "Isracard" to "isracard",
        )
        for ((sender, brand) in cases) assertEquals(sender, brand, LinkAnalyzer.brandNamed(sender))
        for (sender in listOf("Cheetah", "050-1234567", "Groups", "+972541234567", "", "דני כהן", "Shufersal")) {
            assertNull(sender, LinkAnalyzer.brandNamed(sender))
        }
    }

    @Test
    fun genuineHostCheckIsPerBrand() {
        assertTrue(LinkAnalyzer.isGenuineHost("leumi", "www.leumi.co.il"))
        assertFalse(LinkAnalyzer.isGenuineHost("leumi", "hapoalim.co.il"))
        assertFalse(LinkAnalyzer.isGenuineHost("leumi", "leumi.co.il.evil.top"))
        assertFalse(LinkAnalyzer.isGenuineHost("no-such-brand", "leumi.co.il"))
        assertNotNull(LinkAnalyzer.findLinks("leumi.co.il").singleOrNull())
    }

    @Test
    fun realLookingScamsAndGenuineMessages() {
        val scam = LinkAnalyzer.findLinks("דואר ישראל: החבילה שלך ממתינה. עדכן כתובת ושלם 3.90 ₪: https://israelpost-track.top/IL123 תודה").single()
        assertEquals("israelpost-track.top", scam.host)
        assertEquals("israelpost", scam.lookalikeOf)

        val bank = LinkAnalyzer.findLinks("בנק לאומי: יש לאמת את חשבונך https://leumi-verify.com/a?x=1").single()
        assertEquals("leumi", bank.lookalikeOf)

        val genuine = LinkAnalyzer.findLinks("מעקב אחר החבילה: https://israelpost.co.il/itemtrace?itemcode=RR123456789IL. תודה").single()
        assertEquals("israelpost.co.il", genuine.host)
        assertFalse(genuine.isLookalike)
        assertEquals("https://israelpost.co.il/itemtrace?itemcode=RR123456789IL", genuine.text)
    }

    @Test
    fun linkRangesPointAtTheText() {
        val text = "ראה bit.ly/a וגם https://leumi.co.il/x."
        for (link in LinkAnalyzer.findLinks(text)) assertEquals(link.text, text.substring(link.range))
        assertEquals(2, LinkAnalyzer.findLinks(text).size)
    }
}

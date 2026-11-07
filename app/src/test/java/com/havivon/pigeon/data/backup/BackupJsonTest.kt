package com.havivon.pigeon.data.backup

import com.havivon.pigeon.domain.backup.BackupError
import com.havivon.pigeon.domain.backup.BackupFavorite
import com.havivon.pigeon.domain.backup.BackupHash
import com.havivon.pigeon.domain.backup.BackupLimits
import com.havivon.pigeon.domain.backup.BackupMove
import com.havivon.pigeon.domain.backup.BackupParse
import com.havivon.pigeon.domain.backup.BackupReminder
import com.havivon.pigeon.domain.backup.BackupRule
import com.havivon.pigeon.domain.backup.BackupSettings
import com.havivon.pigeon.domain.backup.ChatKeys
import com.havivon.pigeon.domain.backup.SettingsBackup
import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppTheme
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.RuleMatch
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {

    private fun ok(text: String): SettingsBackup {
        val result = BackupJson.parse(text)
        assertTrue("expected a parsed file, got $result", result is BackupParse.Ok)
        return (result as BackupParse.Ok).backup
    }

    private fun failure(text: String): BackupError {
        val result = BackupJson.parse(text)
        assertTrue("expected a refusal, got $result", result is BackupParse.Failed)
        return (result as BackupParse.Failed).error
    }

    /** A valid file with [body] (more root fields) after the format and version. */
    private fun file(body: String = "", version: Int = 1) = """{"format":"pigeon-settings","version":$version${if (body.isEmpty()) "" else ",$body"}}"""

    private val sample = SettingsBackup(
        exportedAtMillis = 1_793_000_000_000L,
        appVersion = "0.4.5",
        settings = BackupSettings(AppLanguage.Hebrew, AppTheme.Dark, AppIcon.Origami, true, false, true, false, true, blockSpamOnPhone = false, autoCopyCodes = true),
        rules = listOf(
            BackupRule("Bank", RuleMatch.Sender("Leumi"), 0xFFE53935L, true),
            BackupRule("", RuleMatch.Keyword("parcel"), 0x80102030L, false),
        ),
        categories = listOf("Work", "Family"),
        categoryColors = mapOf("Work" to 0xFF2F80EDL),
        folderColors = mapOf(Folder.Banks to 0xFF1E9E6AL, Folder.Codes to 0xFFE0559BL),
        moves = listOf(BackupMove("525550123", Folder.Banks, null), BackupMove("group:525550123,525550124", null, "Work")),
        pinned = listOf("525550123"),
        locked = listOf("525550124"),
        notSpam = listOf("+972525550123"),
        blockedWords = listOf("casino", "loan"),
        reminders = listOf(BackupReminder(1_800_000_000_000L, "525550123", 1_793_000_000_001L), BackupReminder(1_800_000_000_500L, "525550124", null)),
        favorites = listOf(BackupFavorite("525550123", 1_793_000_000_002L, BackupHash.of("hello"), 1_793_000_000_003L)),
    )

    @Test
    fun `a written file reads back the same`() {
        assertEquals(sample, ok(BackupJson.encode(sample)))
    }

    @Test
    fun `the file names its format and version`() {
        val json = BackupJson.encode(sample).replace(" ", "")
        assertTrue(json.contains("\"format\":\"pigeon-settings\""))
        assertTrue(json.contains("\"version\":2"))
        assertTrue(json.contains("\"appVersion\":\"0.4.5\""))
        assertTrue(json.contains("\"exportedAt\""))
    }

    @Test
    fun `blocking on the phone and copying codes go in the file, and an older file without them leaves them alone`() {
        val json = BackupJson.encode(sample).replace(" ", "")
        assertTrue(json.contains("\"blockSpamOnPhone\":false"))
        assertTrue(json.contains("\"autoCopyCodes\":true"))
        val older = ok(file(""""settings":{"lockScreenText":false}""", version = 2)).settings
        assertEquals(false, older.lockScreenText)
        assertNull(older.blockSpamOnPhone)
        assertNull(older.autoCopyCodes)
    }

    @Test
    fun `welcome and permission flags are never written`() {
        val json = BackupJson.encode(sample)
        assertFalse(json.contains("welcome"))
        assertFalse(json.contains("permissionsAsked"))
    }

    // Colours of the categories (version 2), and the version 1 files that have none

    @Test
    fun `a version 1 file is still read, with no colours`() {
        val backup = ok(file(""""categories":["Work"],"categoryColors":{"Work":"#FF2F80ED"},"folderColors":{"Banks":"#FF1E9E6A"}""", version = 1))
        assertEquals(listOf("Work"), backup.categories)
        assertTrue("a version 1 file has no colours, whatever it holds", backup.categoryColors.isEmpty())
        assertNull("so an import keeps the phone's colours of the built-in folders", backup.folderColors)
    }

    @Test
    fun `a version 2 file carries the colours of the categories and of the built-in folders`() {
        val backup = ok(
            file(
                """"categories":["Work","Family"],"categoryColors":{"work":"#FF2F80ED","Family":"#E0559B","Nope":"#FF000000"},""" +
                    """"folderColors":{"Banks":"#FF1E9E6A","Spam":"#FFFF0000","All":"#FFFF0000","Mars":"#FF000000"}""",
                version = 2,
            ),
        )
        assertEquals(mapOf("Work" to 0xFF2F80EDL, "Family" to 0xFFE0559BL), backup.categoryColors)
        assertEquals("Spam and All never get a colour, unknown folders are ignored", mapOf(Folder.Banks to 0xFF1E9E6AL), backup.folderColors)
    }

    @Test
    fun `a colour that is not a colour is dropped, the category stays`() {
        val backup = ok(file(""""categories":["Work","Family"],"categoryColors":{"Work":"red","Family":12}""", version = 2))
        assertEquals(listOf("Work", "Family"), backup.categories)
        assertTrue(backup.categoryColors.isEmpty())
    }

    @Test
    fun `a version 2 file without colours reads as none, and leaves the built-in ones alone`() {
        val backup = ok(file(""""categories":["Work"]""", version = 2))
        assertTrue(backup.categoryColors.isEmpty())
        assertNull(backup.folderColors)
    }

    // Refused files

    @Test
    fun `not JSON is refused`() {
        assertEquals(BackupError.NotJson, failure("this is not json"))
        assertEquals(BackupError.NotJson, failure(""))
        assertEquals(BackupError.NotJson, failure("[1,2,3]"))
        assertEquals(BackupError.NotJson, failure("{\"format\":"))
    }

    @Test
    fun `absurdly deep nesting is refused without a crash`() {
        val deep = "{\"a\":" + "[".repeat(200_000)
        assertEquals(BackupError.NotJson, failure(deep))
    }

    @Test
    fun `another format is refused`() {
        assertEquals(BackupError.WrongFormat, failure("""{"format":"something-else","version":1}"""))
        assertEquals(BackupError.WrongFormat, failure("""{"version":1}"""))
        assertEquals(BackupError.WrongFormat, failure("""{"format":42,"version":1}"""))
    }

    @Test
    fun `a missing or broken version is refused`() {
        assertEquals(BackupError.WrongFormat, failure("""{"format":"pigeon-settings"}"""))
        assertEquals(BackupError.WrongFormat, failure("""{"format":"pigeon-settings","version":"1"}"""))
        assertEquals(BackupError.WrongFormat, failure(file(version = 0)))
        assertEquals(BackupError.WrongFormat, failure(file(version = -3)))
    }

    @Test
    fun `a newer version is refused with its own reason`() {
        assertEquals(BackupError.TooNew, failure(file(version = 3)))
        assertEquals(BackupError.TooNew, failure(file(version = 999)))
    }

    @Test
    fun `an oversized file is refused`() {
        assertEquals(BackupError.TooLarge, (BackupJson.parse(ByteArray(BackupLimits.MAX_FILE_BYTES + 1) { ' '.code.toByte() }) as BackupParse.Failed).error)
        val big = file("\"pad\":\"" + "x".repeat(BackupLimits.MAX_FILE_BYTES) + "\"")
        assertEquals(BackupError.TooLarge, failure(big))
    }

    @Test
    fun `a file of the allowed size is read`() {
        val padded = file("\"pad\":\"" + "x".repeat(BackupLimits.MAX_FILE_BYTES - 200) + "\"")
        assertTrue(BackupJson.parse(padded.toByteArray()) is BackupParse.Ok)
    }

    @Test
    fun `reading stops one byte past the limit`() {
        val data = ByteArray(10_000)
        assertEquals(101, BackupFiles.readAtMost(ByteArrayInputStream(data), 101).size)
        assertEquals(10_000, BackupFiles.readAtMost(ByteArrayInputStream(data), 20_000).size)
    }

    // A valid file with bad parts

    @Test
    fun `unknown fields are ignored`() {
        val b = ok(file("\"future\":{\"x\":[1,2]},\"settings\":{\"theme\":\"dark\",\"hologram\":true},\"pinned\":[\"1\"],\"colorRules\":[{\"kind\":\"sender\",\"value\":\"Bank\",\"color\":\"#FF0000\",\"extra\":1}]"))
        assertEquals(AppTheme.Dark, b.settings.theme)
        assertEquals(listOf("1"), b.pinned)
        assertEquals(1, b.rules.size)
    }

    @Test
    fun `a bad enum leaves that setting alone`() {
        val b = ok(file("\"settings\":{\"language\":\"klingon\",\"theme\":\"DARK\",\"appIcon\":7,\"appLock\":\"yes\",\"lockScreenText\":false}"))
        assertNull(b.settings.language)
        assertEquals(AppTheme.Dark, b.settings.theme)
        assertNull(b.settings.appIcon)
        assertNull(b.settings.appLock)
        assertEquals(false, b.settings.lockScreenText)
        assertEquals(2, b.settings.count)
    }

    @Test
    fun `settings of the wrong type or missing are simply absent`() {
        assertEquals(0, ok(file("\"settings\":[1,2]")).settings.count)
        assertEquals(0, ok(file()).settings.count)
    }

    @Test
    fun `colours are validated`() {
        fun rule(color: String) = ok(file("\"colorRules\":[{\"kind\":\"keyword\",\"value\":\"x\",\"color\":$color}]")).rules
        assertEquals(0xFFFF0000L, rule("\"#FF0000\"").single().argb)
        assertEquals(0x80FF0000L, rule("\"#80FF0000\"").single().argb)
        assertEquals(0xFFAABBCCL, rule("\"#aabbcc\"").single().argb)
        for (bad in listOf("\"red\"", "\"#12345\"", "\"#GGGGGG\"", "\"FF0000\"", "\"#FF00000000\"", "16711680", "null", "\"\"")) {
            assertTrue("$bad should be refused", rule(bad).isEmpty())
        }
    }

    @Test
    fun `rules with a bad kind or value are dropped`() {
        val b = ok(
            file(
                "\"colorRules\":[" +
                    "{\"kind\":\"sender\",\"value\":\"A\",\"color\":\"#FF0000\"}," +
                    "{\"kind\":\"banana\",\"value\":\"B\",\"color\":\"#FF0000\"}," +
                    "{\"kind\":\"sender\",\"value\":\"  \",\"color\":\"#FF0000\"}," +
                    "{\"kind\":\"sender\",\"value\":5,\"color\":\"#FF0000\"}," +
                    "\"not an object\"," +
                    "{\"kind\":\"keyword\",\"value\":\"K\",\"color\":\"#00FF00\",\"enabled\":false}]",
            ),
        )
        assertEquals(listOf("A", "K"), b.rules.map { (it.match as? RuleMatch.Sender)?.name ?: (it.match as RuleMatch.Keyword).word })
        assertEquals(listOf(true, false), b.rules.map { it.enabled })
    }

    @Test
    fun `every string is length limited`() {
        val long = { n: Int -> "x".repeat(n) }
        val b = ok(
            file(
                "\"appVersion\":\"${long(BackupLimits.MAX_VERSION_NAME + 1)}\"," +
                    "\"colorRules\":[" +
                    "{\"kind\":\"sender\",\"value\":\"${long(BackupLimits.MAX_VALUE + 1)}\",\"color\":\"#FF0000\"}," +
                    "{\"kind\":\"sender\",\"value\":\"ok\",\"label\":\"${long(BackupLimits.MAX_LABEL + 1)}\",\"color\":\"#FF0000\"}," +
                    "{\"kind\":\"sender\",\"value\":\"${long(BackupLimits.MAX_VALUE)}\",\"label\":\"${long(BackupLimits.MAX_LABEL)}\",\"color\":\"#FF0000\"}]," +
                    "\"categories\":[\"${long(25)}\",\"${long(24)}\"]," +
                    "\"pinned\":[\"${long(BackupLimits.MAX_KEY + 1)}\",\"${long(BackupLimits.MAX_KEY)}\"]," +
                    "\"blockedWords\":[\"${long(BackupLimits.MAX_LABEL + 1)}\",\"fine\"]," +
                    "\"notSpam\":[\"${long(BackupLimits.MAX_VALUE + 1)}\"]," +
                    "\"favorites\":[{\"chat\":\"${long(BackupLimits.MAX_KEY + 1)}\",\"date\":1000,\"hash\":\"abcd1234\"}]",
            ),
        )
        assertEquals("", b.appVersion)
        assertEquals(1, b.rules.size)
        assertEquals(listOf(long(24)), b.categories)
        assertEquals(listOf(long(BackupLimits.MAX_KEY)), b.pinned)
        assertEquals(listOf("fine"), b.blockedWords)
        assertTrue(b.notSpam.isEmpty())
        assertTrue(b.favorites.isEmpty())
    }

    @Test
    fun `lists are capped`() {
        val words = (1..BackupLimits.MAX_WORDS + 50).joinToString(",") { "\"w$it\"" }
        val rules = (1..BackupLimits.MAX_RULES + 50).joinToString(",") { "{\"kind\":\"keyword\",\"value\":\"k$it\",\"color\":\"#FF0000\"}" }
        val b = ok(file("\"blockedWords\":[$words],\"colorRules\":[$rules]"))
        assertEquals(BackupLimits.MAX_WORDS, b.blockedWords.size)
        assertEquals(BackupLimits.MAX_RULES, b.rules.size)
    }

    @Test
    fun `moves keep only a real target`() {
        val b = ok(
            file(
                "\"categories\":[\"Work\"]," +
                    "\"categoryMoves\":[" +
                    "{\"chat\":\"111\",\"folder\":\"Banks\"}," +
                    "{\"chat\":\"222\",\"category\":\"work\"}," +
                    "{\"chat\":\"333\",\"category\":\"Missing\"}," +
                    "{\"chat\":\"444\",\"folder\":\"Spam\"}," +
                    "{\"chat\":\"555\",\"folder\":\"All\"}," +
                    "{\"chat\":\"666\",\"folder\":\"Nonsense\"}," +
                    "{\"chat\":\"thread:7\",\"folder\":\"Banks\"}," +
                    "{\"chat\":\"111\",\"folder\":\"Codes\"}," +
                    "{\"folder\":\"Banks\"}]",
            ),
        )
        assertEquals(listOf(BackupMove("111", Folder.Banks, null), BackupMove("222", null, "Work")), b.moves)
    }

    @Test
    fun `categories are cleaned and not repeated`() {
        val b = ok(file("\"categories\":[\"  Work  \",\"WORK\",\"\",\"Home   Town\",5]"))
        assertEquals(listOf("Work", "Home Town"), b.categories)
    }

    @Test
    fun `times must be real`() {
        val b = ok(
            file(
                "\"reminders\":[" +
                    "{\"at\":1800000000000,\"chat\":\"1\",\"messageDate\":1793000000000}," +
                    "{\"at\":\"1800000000000\",\"chat\":\"2\"}," +
                    "{\"at\":-5,\"chat\":\"3\"}," +
                    "{\"at\":99999999999999999,\"chat\":\"4\"}," +
                    "{\"at\":1800000000000,\"chat\":\"5\",\"messageDate\":\"soon\"}]",
            ),
        )
        assertEquals(listOf("1", "5"), b.reminders.map { it.chat })
        assertEquals(listOf(1_793_000_000_000L, null), b.reminders.map { it.messageDateMillis })
    }

    @Test
    fun `favorites need a chat, a date and an eight digit hash`() {
        val b = ok(
            file(
                "\"favorites\":[" +
                    "{\"chat\":\"1\",\"date\":1793000000000,\"hash\":\"ABCDEF01\"}," +
                    "{\"chat\":\"1\",\"date\":1793000000000,\"hash\":\"abcdef01\"}," +
                    "{\"chat\":\"1\",\"date\":1793000000000,\"hash\":\"xyz\"}," +
                    "{\"chat\":\"1\",\"date\":1793000000000}," +
                    "{\"chat\":\"1\",\"hash\":\"abcdef01\"}," +
                    "{\"date\":1793000000000,\"hash\":\"abcdef01\"}]",
            ),
        )
        assertEquals(1, b.favorites.size)
        assertEquals("abcdef01", b.favorites.single().bodyHash)
    }

    @Test
    fun `a byte order mark is tolerated`() {
        ok(0xFEFF.toChar() + file())
    }

    @Test
    fun `the export date is optional`() {
        assertEquals(0L, ok(file()).exportedAtMillis)
        assertEquals(0L, ok(file("\"exportedAt\":\"yesterday\"")).exportedAtMillis)
        assertEquals(java.time.Instant.parse("2026-10-26T06:13:20Z").toEpochMilli(), ok(file("\"exportedAt\":\"2026-10-26T06:13:20Z\"")).exportedAtMillis)
    }

    // Keys and hash

    @Test
    fun `a chat is named by its sender, and a group by its members`() {
        assertEquals("525550123", ChatKeys.of(listOf("+972525550123")))
        assertEquals("525550123", ChatKeys.of(listOf("052-555-0123")))
        assertEquals("leumi", ChatKeys.of(listOf("Leumi")))
        assertEquals("group:525550123,525550124", ChatKeys.of(listOf("0525550124", "0525550123")))
        assertNull(ChatKeys.of(emptyList()))
        assertNull(ChatKeys.of(listOf(" ")))
    }

    @Test
    fun `the hash is short, stable and ignores the edges`() {
        assertEquals(8, BackupHash.of("hello").length)
        assertEquals(BackupHash.of("hello"), BackupHash.of("  hello \n"))
        assertFalse(BackupHash.of("hello") == BackupHash.of("hello!"))
        assertTrue(BackupHash.of("שלום").matches(Regex("[0-9a-f]{8}")))
    }

    @Test
    fun `colour parsing`() {
        assertNull(BackupJson.parseColor(null))
        assertEquals(0xFF123456L, BackupJson.parseColor("#123456"))
    }
}

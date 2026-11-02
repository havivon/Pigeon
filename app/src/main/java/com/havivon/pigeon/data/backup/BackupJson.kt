package com.havivon.pigeon.data.backup

import com.havivon.pigeon.domain.backup.BackupError
import com.havivon.pigeon.domain.backup.BackupFavorite
import com.havivon.pigeon.domain.backup.BackupLimits
import com.havivon.pigeon.domain.backup.BackupMove
import com.havivon.pigeon.domain.backup.BackupParse
import com.havivon.pigeon.domain.backup.BackupReminder
import com.havivon.pigeon.domain.backup.BackupRule
import com.havivon.pigeon.domain.backup.BackupSettings
import com.havivon.pigeon.domain.backup.SettingsBackup
import com.havivon.pigeon.domain.folders.FolderNames
import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppTheme
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.RuleMatch
import java.time.Instant
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * [SettingsBackup] as the versioned JSON file `{"format":"pigeon-settings","version":2,...}` (org.json,
 * which Android already has: no new library).
 *
 * Version 2 adds the colours of the categories (`categoryColors`, `folderColors`); a version 1 file is still read, and has
 * none (an import then leaves the phone's colours of the built-in folders as they are).
 *
 * Writing never includes a message body (see `SettingsBackup`). Reading is defensive, since the file comes
 * from outside: a file over [BackupLimits.MAX_FILE_BYTES], not JSON, of another format or of a newer
 * version is refused with a reason; inside a valid file, fields this version does not know are ignored, and
 * an entry with a wrong type, a too-long string, an unknown enum or a bad colour is dropped (the settings
 * it would have set stay as they are), never half-read. Every list is capped.
 */
object BackupJson {

    // Writing

    fun encode(backup: SettingsBackup): String {
        val root = JSONObject()
        root.put("format", BackupLimits.FORMAT)
        root.put("version", BackupLimits.VERSION)
        if (backup.exportedAtMillis > 0) root.put("exportedAt", Instant.ofEpochMilli(backup.exportedAtMillis).toString())
        root.put("appVersion", backup.appVersion.take(BackupLimits.MAX_VERSION_NAME))

        val s = backup.settings
        root.put(
            "settings",
            JSONObject().apply {
                s.language?.let { put("language", it.name.lowercase(Locale.ROOT)) }
                s.theme?.let { put("theme", it.name.lowercase(Locale.ROOT)) }
                s.appIcon?.let { put("appIcon", it.name.lowercase(Locale.ROOT)) }
                s.lockScreenText?.let { put("lockScreenText", it) }
                s.widgetsShowText?.let { put("widgetsShowText", it) }
                s.blockScreenshots?.let { put("blockScreenshots", it) }
                s.appLock?.let { put("appLock", it) }
                s.autoDeleteCodes?.let { put("autoDeleteCodes", it) }
            },
        )
        root.put(
            "colorRules",
            array(backup.rules) { r ->
                JSONObject().apply {
                    put("label", r.label)
                    when (val m = r.match) {
                        is RuleMatch.Sender -> { put("kind", KIND_SENDER); put("value", m.name) }
                        is RuleMatch.Keyword -> { put("kind", KIND_KEYWORD); put("value", m.word) }
                    }
                    put("color", colorText(r.argb))
                    put("enabled", r.enabled)
                }
            },
        )
        root.put("categories", array(backup.categories) { it })
        // Version 2: the colours (an object per kind, name to "#AARRGGBB"); a folder or category without one is left out
        root.put("categoryColors", JSONObject().apply { backup.categoryColors.forEach { (name, argb) -> put(name, colorText(argb)) } })
        backup.folderColors?.let { colors -> root.put("folderColors", JSONObject().apply { colors.forEach { (folder, argb) -> put(folder.name, colorText(argb)) } }) }
        root.put(
            "categoryMoves",
            array(backup.moves) { m ->
                JSONObject().apply {
                    put("chat", m.key)
                    if (m.folder != null) put("folder", m.folder.name) else put("category", m.category)
                }
            },
        )
        root.put("pinned", array(backup.pinned) { it })
        root.put("locked", array(backup.locked) { it })
        root.put("notSpam", array(backup.notSpam) { it })
        root.put("blockedWords", array(backup.blockedWords) { it })
        root.put(
            "reminders",
            array(backup.reminders) { r ->
                JSONObject().apply {
                    put("at", r.triggerAtMillis)
                    put("chat", r.chat)
                    r.messageDateMillis?.let { put("messageDate", it) }
                }
            },
        )
        root.put(
            "favorites",
            array(backup.favorites) { f ->
                JSONObject().apply {
                    put("chat", f.chat)
                    put("date", f.dateMillis)
                    put("hash", f.bodyHash)
                    f.starredAtMillis?.let { put("starredAt", it) }
                }
            },
        )
        return root.toString(2)
    }

    private fun colorText(argb: Long): String = "#%08X".format(argb and 0xFFFFFFFFL)

    private fun <T> array(items: List<T>, transform: (T) -> Any?): JSONArray =
        JSONArray().also { array -> items.forEach { array.put(transform(it)) } }

    // Reading

    fun parse(bytes: ByteArray): BackupParse {
        if (bytes.size > BackupLimits.MAX_FILE_BYTES) return BackupParse.Failed(BackupError.TooLarge)
        return parse(String(bytes, Charsets.UTF_8))
    }

    fun parse(text: String): BackupParse {
        if (text.length > BackupLimits.MAX_FILE_BYTES) return BackupParse.Failed(BackupError.TooLarge)
        val root = try {
            JSONObject(text.trimStart(BOM))
        } catch (_: JSONException) {
            return BackupParse.Failed(BackupError.NotJson)
        } catch (_: StackOverflowError) {
            // Absurdly deep nesting in a hostile file
            return BackupParse.Failed(BackupError.NotJson)
        }
        if (root.str("format", 100) != BackupLimits.FORMAT) return BackupParse.Failed(BackupError.WrongFormat)
        val version = (root.opt("version") as? Number)?.toLong()
        if (version == null || version < 1) return BackupParse.Failed(BackupError.WrongFormat)
        if (version > BackupLimits.VERSION) return BackupParse.Failed(BackupError.TooNew)

        val categories = readCategories(root)
        return BackupParse.Ok(
            SettingsBackup(
                exportedAtMillis = root.str("exportedAt", 64)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                    ?.takeIf { it in 1..BackupLimits.MAX_MILLIS } ?: 0L,
                appVersion = root.str("appVersion", BackupLimits.MAX_VERSION_NAME).orEmpty(),
                settings = readSettings(root.opt("settings") as? JSONObject),
                rules = readRules(root),
                categories = categories,
                categoryColors = if (version >= 2) readCategoryColors(root, categories) else emptyMap(),
                folderColors = if (version >= 2) readFolderColors(root) else null,
                moves = readMoves(root, categories),
                pinned = root.strings("pinned", BackupLimits.MAX_CHATS, BackupLimits.MAX_KEY),
                locked = root.strings("locked", BackupLimits.MAX_CHATS, BackupLimits.MAX_KEY),
                notSpam = root.strings("notSpam", BackupLimits.MAX_NOT_SPAM, BackupLimits.MAX_VALUE),
                blockedWords = readWords(root),
                reminders = readReminders(root),
                favorites = readFavorites(root),
            ),
        )
    }

    private fun readSettings(o: JSONObject?): BackupSettings {
        if (o == null) return BackupSettings()
        return BackupSettings(
            language = o.enum<AppLanguage>("language"),
            theme = o.enum<AppTheme>("theme"),
            appIcon = o.enum<AppIcon>("appIcon"),
            lockScreenText = o.opt("lockScreenText") as? Boolean,
            widgetsShowText = o.opt("widgetsShowText") as? Boolean,
            blockScreenshots = o.opt("blockScreenshots") as? Boolean,
            appLock = o.opt("appLock") as? Boolean,
            autoDeleteCodes = o.opt("autoDeleteCodes") as? Boolean,
        )
    }

    private fun readRules(root: JSONObject): List<BackupRule> = root.objects("colorRules", BackupLimits.MAX_RULES).mapNotNull { o ->
        val value = o.str("value", BackupLimits.MAX_VALUE)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val match = when (o.str("kind", 20)) {
            KIND_SENDER -> RuleMatch.Sender(value)
            KIND_KEYWORD -> RuleMatch.Keyword(value)
            else -> return@mapNotNull null
        }
        val argb = parseColor(o.str("color", 12)) ?: return@mapNotNull null
        val labelValue = o.opt("label")
        // A label that is present must be usable; a missing one is empty
        val label = if (labelValue == null) "" else o.str("label", BackupLimits.MAX_LABEL) ?: return@mapNotNull null
        BackupRule(label.trim(), match, argb, o.opt("enabled") as? Boolean ?: true)
    }

    /** "#RRGGBB" (opaque) or "#AARRGGBB" as a full ARGB value; null for anything else. */
    internal fun parseColor(text: String?): Long? {
        if (text == null || !COLOR.matches(text)) return null
        val digits = text.substring(1)
        val value = digits.toLong(16)
        return if (digits.length == 6) 0xFF000000L or value else value
    }

    private fun readCategories(root: JSONObject): List<String> {
        val seen = HashSet<String>()
        return root.strings("categories", BackupLimits.MAX_CATEGORIES, BackupLimits.MAX_CATEGORY_NAME * 4)
            .map { FolderNames.clean(it) }
            .filter { it.isNotEmpty() && it.length <= FolderNames.MAX_LENGTH && seen.add(it.lowercase(Locale.ROOT)) }
    }

    /** The colour of each category the file names (by the name as read); a colour that is not "#RRGGBB" or "#AARRGGBB" is dropped. */
    private fun readCategoryColors(root: JSONObject, categories: List<String>): Map<String, Long> {
        val o = root.opt("categoryColors") as? JSONObject ?: return emptyMap()
        val byLowerName = categories.associateBy { it.lowercase(Locale.ROOT) }
        val out = LinkedHashMap<String, Long>()
        for (key in o.keys()) {
            val name = byLowerName[FolderNames.clean(key).lowercase(Locale.ROOT)] ?: continue
            parseColor(o.str(key, 12))?.let { out[name] = it }
        }
        return out
    }

    /** Null when the file does not carry the built-in folders' colours at all (an import then keeps the phone's). */
    private fun readFolderColors(root: JSONObject): Map<Folder, Long>? {
        val o = root.opt("folderColors") as? JSONObject ?: return null
        val out = LinkedHashMap<Folder, Long>()
        for (key in o.keys()) {
            val folder = Folder.entries.firstOrNull { it.name == key && it.colourable } ?: continue
            parseColor(o.str(key, 12))?.let { out[folder] = it }
        }
        return out
    }

    private fun readMoves(root: JSONObject, categories: List<String>): List<BackupMove> {
        val byLowerName = categories.associateBy { it.lowercase(Locale.ROOT) }
        val seen = HashSet<String>()
        return root.objects("categoryMoves", BackupLimits.MAX_MOVES).mapNotNull { o ->
            val key = o.str("chat", BackupLimits.MAX_KEY)?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("thread:") } ?: return@mapNotNull null
            val folderName = o.opt("folder")
            val move = if (folderName != null) {
                val folder = (folderName as? String)?.let { n -> Folder.entries.firstOrNull { it.name == n && it != Folder.All && it != Folder.Spam } }
                    ?: return@mapNotNull null
                BackupMove(key, folder, null)
            } else {
                val name = o.str("category", BackupLimits.MAX_CATEGORY_NAME * 4)?.let { FolderNames.clean(it).lowercase(Locale.ROOT) }
                BackupMove(key, null, name?.let { byLowerName[it] } ?: return@mapNotNull null)
            }
            if (seen.add(key)) move else null
        }
    }

    private fun readWords(root: JSONObject): List<String> {
        val seen = HashSet<String>()
        return root.strings("blockedWords", BackupLimits.MAX_WORDS, BackupLimits.MAX_LABEL)
            .map { it.trim() }
            .filter { it.isNotEmpty() && seen.add(it.lowercase(Locale.ROOT)) }
    }

    private fun readReminders(root: JSONObject): List<BackupReminder> = root.objects("reminders", BackupLimits.MAX_REMINDERS).mapNotNull { o ->
        val at = o.millis("at") ?: return@mapNotNull null
        val chat = o.str("chat", BackupLimits.MAX_KEY)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        BackupReminder(at, chat, o.millis("messageDate"))
    }

    private fun readFavorites(root: JSONObject): List<BackupFavorite> {
        val seen = HashSet<Triple<String, Long, String>>()
        return root.objects("favorites", BackupLimits.MAX_FAVORITES).mapNotNull { o ->
            val chat = o.str("chat", BackupLimits.MAX_KEY)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val date = o.millis("date") ?: return@mapNotNull null
            val hash = o.str("hash", 8)?.lowercase(Locale.ROOT)?.takeIf { HASH.matches(it) } ?: return@mapNotNull null
            if (seen.add(Triple(chat, date, hash))) BackupFavorite(chat, date, hash, o.millis("starredAt")) else null
        }
    }

    // Typed reads: a value of the wrong type is null, never converted (org.json's own `optString` would turn a number into text)

    private fun JSONObject.str(key: String, max: Int): String? = (opt(key) as? String)?.takeIf { it.length <= max }

    private fun JSONObject.millis(key: String): Long? =
        (opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() && it > 0 && it <= BackupLimits.MAX_MILLIS }?.toLong()

    private inline fun <reified E : Enum<E>> JSONObject.enum(key: String): E? {
        val name = str(key, 30) ?: return null
        return enumValues<E>().firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    private fun JSONObject.objects(key: String, max: Int): List<JSONObject> {
        val array = opt(key) as? JSONArray ?: return emptyList()
        return (0 until minOf(array.length(), max)).mapNotNull { array.opt(it) as? JSONObject }
    }

    private fun JSONObject.strings(key: String, max: Int, maxLength: Int): List<String> {
        val array = opt(key) as? JSONArray ?: return emptyList()
        return (0 until minOf(array.length(), max))
            .mapNotNull { (array.opt(it) as? String)?.trim()?.takeIf { s -> s.isNotEmpty() && s.length <= maxLength } }
            .distinct()
    }

    /** A byte order mark some editors put at the start of a UTF-8 file. */
    private val BOM = 0xFEFF.toChar()
    private const val KIND_SENDER = "sender"
    private const val KIND_KEYWORD = "keyword"
    private val COLOR = Regex("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")
    private val HASH = Regex("[0-9a-f]{8}")
}

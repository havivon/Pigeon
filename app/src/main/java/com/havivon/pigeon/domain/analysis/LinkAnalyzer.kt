package com.havivon.pigeon.domain.analysis

/** One link found in a message, with what the user needs to judge it. */
data class LinkInfo(
    /** The link as written in the message, without trailing punctuation. */
    val text: String,
    /** Where [text] sits in the message. */
    val range: IntRange,
    /** The real host the link goes to: lower case, without scheme, user info, port or path. */
    val host: String,
    /** A known link-shortening service, or a short unknown domain shaped like one ([LinkAnalyzer.looksShortened]). */
    val isShortener: Boolean,
    /** The id of the well-known brand this host imitates ("israelpost", "leumi", "paypal"...), or null. */
    val lookalikeOf: String?,
    /** A host with an `xn--` label: an international name that can hide look-alike letters. */
    val isPunycode: Boolean,
    /** "https://paypal.com@evil.example/": what precedes the @ is not the host. */
    val hasUserInfo: Boolean,
) {
    /** Any of the signs that the link pretends to be something it is not. */
    val isLookalike: Boolean get() = lookalikeOf != null || isPunycode || hasUserInfo
}

/**
 * Finds the links in a message and shows where they really go. Everything runs on the device, with
 * a small built-in list of shorteners and of brands that scammers imitate.
 */
object LinkAnalyzer {

    /** The links of [text], in order. Handles links with or without a scheme and with Hebrew text around them. */
    fun findLinks(text: String): List<LinkInfo> {
        val links = ArrayList<LinkInfo>()
        for (m in URL.findAll(text)) {
            val raw = m.value.trimEnd { it in TRAILING }
            if (raw.isEmpty()) continue
            val explicit = EXPLICIT.containsMatchIn(raw)
            val host = hostOf(raw)
            if (host.isEmpty()) continue
            val tld = host.substringAfterLast('.')
            val hasPath = raw.substringAfter("://").any { it == '/' || it == '?' || it == '#' }
            // "Thanks.It" is far more likely a missing space than a link: words that are also TLDs need a path.
            if (!explicit && tld in WORD_TLDS && !hasPath && !isShortener(host)) continue
            links += LinkInfo(
                text = raw,
                range = m.range.first until m.range.first + raw.length,
                host = host,
                isShortener = isShortener(host) || looksShortened(host, raw),
                lookalikeOf = lookalikeOf(host),
                isPunycode = host.split('.').any { it.startsWith("xn--") },
                hasUserInfo = raw.contains("://") && raw.substringAfter("://").substringBefore('/').contains('@'),
            )
        }
        return links
    }

    /** Whether [host] (as returned in [LinkInfo.host]) belongs to a link-shortening service. */
    fun isShortener(host: String): Boolean = SHORTENERS.any { host == it || host.endsWith(".$it") }

    /**
     * Whether a link on an unknown domain is shaped like a shortener's: a domain of 2-4 letters or digits on one
     * of the cheap short TLDs (.me, .ly, .to, .cc, .link, .click...), with a single random-looking path such as
     * "lp6.me/nejcz" or "l5k.me/iihzE_BP5Az". Advertisers and scammers run their own shorteners like these. The
     * well-known short domains (wa.me, t.me, amzn.to...) and the brands' real domains are never flagged.
     */
    fun looksShortened(host: String, link: String): Boolean {
        val labels = host.removePrefix("www.").split('.')
        if (labels.size != 2 || labels[1] !in SHORT_TLDS) return false
        if (labels[0].length !in 2..4 || labels[0].any { !it.isLetterOrDigit() }) return false
        if (host.removePrefix("www.") in WELL_KNOWN_SHORT || BRANDS.any { isGenuine(it, host) }) return false
        val path = link.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#').trimEnd('/')
        return path.length in 4..24 && '/' !in path && path.all { it.isLetterOrDigit() || it == '_' || it == '-' } && looksRandom(path)
    }

    /** A digit, upper and lower case mixed, an underscore or three consonants in a row: "x7Kq9", "nejcz", not "sale". */
    private fun looksRandom(path: String): Boolean {
        if (path.any { it.isDigit() } || '_' in path) return true
        if (path.any { it.isUpperCase() } && path.any { it.isLowerCase() }) return true
        var run = 0
        for (ch in path.lowercase()) {
            run = if (ch in 'a'..'z' && ch !in "aeiouy") run + 1 else 0
            if (run >= 3) return true
        }
        return false
    }

    /** The id of the brand [host] imitates, or null. The brand's own domains are never flagged. */
    fun lookalikeOf(host: String): String? =
        BRANDS.firstOrNull { brand -> !isGenuine(brand, host) && imitates(host, brand) }?.id

    /**
     * The brand an alphanumeric sender id or display name claims to be ("Leumi", "ISRAELPOST", "דואר ישראל"),
     * or null if it names none of the brands in the list.
     */
    fun brandNamed(sender: String): String? {
        val parts = Text.words(sender.replace('-', ' ').replace('.', ' '))
        val compact = parts.joinToString("")
        if (compact.isEmpty()) return null
        return BRANDS.firstOrNull { brand ->
            brand.tokens.any { if (it.length >= SHORT_TOKEN) compact.contains(it) else parts.any { p -> p == it } } ||
                brand.names.any { compact.contains(it) }
        }?.id
    }

    /** Whether [host] is one of the real domains of [brand] (or a subdomain of one). */
    fun isGenuineHost(brand: String, host: String): Boolean = BRANDS.firstOrNull { it.id == brand }?.let { isGenuine(it, host) } ?: false

    private fun isGenuine(brand: Brand, host: String): Boolean = brand.genuine.any { host == it || host.endsWith(".$it") }

    private fun hostOf(raw: String): String {
        var s = raw.substringAfter("://")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfterLast('@').substringBefore(':')
        return s.trimEnd('.').lowercase()
    }

    private fun imitates(host: String, brand: Brand): Boolean {
        val labels = host.split('.').dropLast(1) // the last label is the TLD
        if (labels.isEmpty()) return false
        val joined = glyphVariants(labels.joinToString("") { it.replace("-", "") })
        val perLabel = labels.flatMap { glyphVariants(it.replace("-", "")) }
        val parts = labels.flatMap { it.split('-') }.flatMap { glyphVariants(it) }
        return brand.tokens.any { token ->
            if (token.length < SHORT_TOKEN) {
                // dhl, usps, ups, doar: only as a word of their own ("dhl-parcel", "dhl24"), never inside another word.
                parts.any { p -> p == token || (p.startsWith(token) && p.drop(token.length).all { it.isDigit() }) }
            } else {
                joined.any { it.contains(token) } || perLabel.any { near(it, token) }
            }
        }
    }

    /** Close to [token] by one typo (two for long names). Short names never lose a letter: "lumi" is not "leumi". */
    private fun near(label: String, token: String): Boolean {
        val max = if (token.length >= 9) 2 else 1
        val shortest = if (token.length <= 6) token.length else token.length - max
        if (label.length < shortest || label.length > token.length + max) return false
        return editDistance(label, token) <= max
    }

    /** The label itself, and the readings of digits that stand in for letters: paypa1, amaz0n, netf1ix, arnazon. */
    private fun glyphVariants(label: String): List<String> {
        if (label.none { it in "0135" } && !label.contains("rn") && !label.contains("vv")) return listOf(label)
        fun swap(s: String, one: Char) = s.replace('0', 'o').replace('3', 'e').replace('5', 's').replace('1', one)
            .replace("rn", "m").replace("vv", "w")
        return listOf(label, swap(label, 'l'), swap(label, 'i')).distinct()
    }

    /** Edit distance counting a swap of two neighbouring letters as one edit (optimal string alignment). */
    private fun editDistance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
        }
        return d[a.length][b.length]
    }

    private class Brand(
        val id: String,
        /** Spellings to look for in a host, lower case with no separators. Under [SHORT_TOKEN] letters they must be a word of their own. */
        val tokens: List<String>,
        /** The real domains. A host equal to one of these, or a subdomain of one, is never flagged. */
        val genuine: List<String>,
        /** Hebrew names, used only to recognise a sender that claims to be the brand. */
        val names: List<String> = emptyList(),
    )

    private const val SHORT_TOKEN = 5

    private val BRANDS = listOf(
        Brand(
            "israelpost",
            listOf("israelpost", "israpost", "isrpost", "doarisrael", "doar"),
            listOf("israelpost.co.il", "israelpost.com", "postil.com"),
            listOf("דוארישראל", "ישראלפוסט"),
        ),
        Brand("gov.il", listOf("govil"), listOf("gov.il"), listOf("ממשלה")),
        Brand("leumi", listOf("leumi"), listOf("leumi.co.il", "leumi.com", "bankleumi.co.il", "leumi-card.co.il", "leumicard.co.il"), listOf("לאומי")),
        Brand(
            "hapoalim",
            listOf("hapoalim", "bankpoalim"),
            listOf("bankhapoalim.com", "bankhapoalim.co.il", "hapoalim.co.il", "hapoalim.com", "poalim.co.il"),
            listOf("הפועלים"),
        ),
        // "discount" alone is an everyday word, so only the bank's full name counts.
        Brand("discount", listOf("discountbank", "bankdiscount"), listOf("discountbank.co.il", "discountbank.com"), listOf("דיסקונט")),
        Brand(
            "mizrahi",
            listOf("mizrahi", "tefahot"),
            listOf("mizrahi-tefahot.co.il", "mizrahitefahot.co.il", "mizrahi-tefahot.com", "mizrahi.co.il"),
            listOf("מזרחי"),
        ),
        Brand("isracard", listOf("isracard"), listOf("isracard.co.il", "isracard.com"), listOf("ישראכרט")),
        Brand("cellcom", listOf("cellcom"), listOf("cellcom.co.il", "cellcom.com"), listOf("סלקום")),
        Brand("pelephone", listOf("pelephone"), listOf("pelephone.co.il"), listOf("פלאפון")),
        Brand("paypal", listOf("paypal"), listOf("paypal.com", "paypal.me", "paypal.co.il", "paypalobjects.com"), listOf("פייפאל")),
        Brand(
            "amazon",
            listOf("amazon"),
            listOf(
                "amazon.com", "amazon.co.il", "amazon.co.uk", "amazon.de", "amazon.fr", "amazon.it", "amazon.es", "amazon.ca",
                "amazon.in", "amazon.com.au", "amazonaws.com", "amazon.jobs", "amzn.com", "amzn.to", "a.co",
            ),
            listOf("אמזון"),
        ),
        Brand("netflix", listOf("netflix"), listOf("netflix.com"), listOf("נטפליקס")),
        Brand("usps", listOf("usps"), listOf("usps.com", "usps.gov")),
        Brand("dhl", listOf("dhl"), listOf("dhl.com", "dhl.de", "dhl.co.il", "dhl-usa.com", "dhlparcel.nl")),
        Brand("fedex", listOf("fedex"), listOf("fedex.com")),
        Brand("ups", listOf("ups"), listOf("ups.com")),
    )

    private val SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "cutt.ly", "tiny.cc", "rb.gy", "is.gd", "s.id", "ow.ly", "buff.ly",
        "shorturl.at", "t.ly", "v.gd", "bit.do", "lnkd.in", "tr.im", "adf.ly", "bl.ink", "shorte.st", "clck.ru", "qr.net",
        "tinyurl.is", "rebrand.ly", "short.io", "soo.gd", "x.co", "mcaf.ee", "shorturl.asia", "tiny.one", "u.to", "kutt.it",
        "chilp.it", "cli.re", "link.tl", "po.st", "t2m.io", "urlz.fr", "han.gl", "1url.com", "snip.ly", "2.gp", "zpr.io",
    )

    /** The cheap TLDs that home-made shorteners use ([looksShortened]). */
    private val SHORT_TLDS = setOf(
        "me", "ly", "to", "cc", "link", "click", "gl", "gd", "gy", "ws", "nu", "vc", "sh", "su", "tk", "ml", "ga", "cf", "gq",
        "xyz", "top", "icu", "cyou", "buzz", "cam", "lol", "fun", "vip", "win", "bid", "rest", "life", "live", "site", "online",
    )

    /** Short domains of well-known services, never taken for a home-made shortener. */
    private val WELL_KNOWN_SHORT = setOf("wa.me", "t.me", "m.me", "fb.me", "amzn.to", "lnk.to", "spoti.fi", "apple.co", "g.co", "msft.it")

    /** TLDs that are also ordinary words: "Thanks.It" is a typo, not a link, unless there is a path. */
    private val WORD_TLDS = setOf("is", "it", "in", "at", "no", "to", "me", "us", "be", "so", "my", "am", "as", "or", "do", "by")

    /** Characters a sentence puts right after a link. */
    private val TRAILING = setOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"', '>')

    private val EXPLICIT = Regex("^(?:(?:https?|ftp)://|www\\.)", RegexOption.IGNORE_CASE)

    private val KNOWN_TLDS = listOf(
        "com", "net", "org", "info", "biz", "edu", "gov", "mil", "int", "io", "co", "me", "ly", "gl", "to", "cc", "tv", "ws",
        "gd", "gy", "id", "do", "ai", "nu", "vc", "sh", "ms", "is", "at", "ch", "de", "fr", "es", "it", "nl", "be", "uk", "ie", "se",
        "no", "fi", "dk", "pl", "cz", "sk", "hu", "ro", "bg", "gr", "tr", "ru", "ua", "il", "ps", "jo", "eg", "ae", "sa", "kr",
        "jp", "cn", "hk", "tw", "sg", "in", "pk", "au", "nz", "ca", "us", "mx", "br", "ar", "cl", "za", "ng", "ke", "by", "kz",
        "su", "tk", "ml", "ga", "cf", "gq", "xyz", "top", "online", "site", "shop", "store", "app", "dev", "page", "link",
        "click", "live", "tech", "club", "vip", "win", "bid", "loan", "work", "fun", "icu", "cyou", "buzz", "cam", "rest",
        "monster", "life", "world", "today", "news", "space", "website", "pro", "mobi", "name", "asia", "eu", "lol", "one",
        "ink", "cloud", "digital", "email", "services", "support", "help", "center", "money", "bank", "finance", "gifts",
    ).sortedByDescending { it.length }.joinToString("|")

    private const val LABEL = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"

    /** Path characters stay ASCII, so Hebrew text, emoji and direction marks right after a link end it. */
    private const val PATH = "[a-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]"

    private val URL = Regex(
        "(?<![a-z0-9@._])(?:" +
            "(?:(?:https?|ftp)://(?:[^\\s/@?#]+@)?|www\\.)(?:$LABEL\\.)+(?:[a-z]{2,24}|xn--[a-z0-9-]+)" +
            "|(?:$LABEL\\.)+(?:xn--[a-z0-9-]+|$KNOWN_TLDS)(?![a-z0-9-])" +
            ")(?::\\d{1,5})?(?:[/?#]$PATH*)?",
        RegexOption.IGNORE_CASE,
    )
}

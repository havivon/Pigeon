package com.havivon.pigeon.ui.common

import android.content.Context
import android.icu.text.DateFormat
import android.icu.util.HebrewCalendar
import android.icu.util.ULocale
import android.text.format.DateFormat as AndroidDateFormat
import com.havivon.pigeon.R
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.domain.model.Folder
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The app's current language, as the resources resolve it (the app's own choice, or the phone's). */
fun appLocale(context: Context): Locale = context.resources.configuration.locales[0]

fun dateLabels(context: Context): DateLabels = DateLabels(
    locale = appLocale(context),
    zone = ZoneId.systemDefault(),
    is24Hour = AndroidDateFormat.is24HourFormat(context),
    today = context.getString(R.string.common_today),
    yesterday = context.getString(R.string.common_yesterday),
    tomorrow = context.getString(R.string.common_tomorrow),
)

fun conversationMapper(context: Context, container: AppContainer, labels: DateLabels = dateLabels(context)): ConversationMapper =
    ConversationMapper(labels, container.codeFinder, container.folderClassifier, context.getString(R.string.inbox_locked_snippet))

/**
 * The built-in folders' names in both of the app's languages ("Banks" and "בנקים", "All" and "הכל"): a category
 * of the user's may take none of them, whichever language is on now.
 */
fun reservedFolderNames(context: Context): Set<String> {
    val config = context.resources.configuration
    return listOf(Locale.ENGLISH, Locale("iw")).flatMapTo(LinkedHashSet()) { locale ->
        val localized = context.createConfigurationContext(android.content.res.Configuration(config).apply { setLocale(locale) })
        Folder.entries.map { localized.getString(it.labelRes) }
    }
}

/**
 * The inbox header's date line: today in the Hebrew calendar and in the Gregorian one,
 * "כ׳ בחשוון תשפ״ז · 31.10.2026" or "20 Heshvan 5787 · Oct 31, 2026". Only the Gregorian date if the
 * phone's ICU cannot format the Hebrew one.
 */
fun inboxDateLine(locale: Locale): String {
    val hebrew = locale.language == "iw" || locale.language == "he"
    val today = LocalDate.now()
    val gregorian = DateTimeFormatter.ofPattern(if (hebrew) "d.M.yyyy" else "MMM d, yyyy", locale).format(today)
    val jewish = try {
        val uLocale = ULocale(if (hebrew) "he_IL@calendar=hebrew;numbers=hebr" else "en_US@calendar=hebrew")
        val format = DateFormat.getDateInstance(HebrewCalendar(uLocale), DateFormat.LONG, uLocale)
        format.format(java.util.Date())
    } catch (_: RuntimeException) {
        null
    }
    return listOfNotNull(jewish, gregorian).joinToString(" · ")
}

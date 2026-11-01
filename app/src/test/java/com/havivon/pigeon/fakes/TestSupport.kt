package com.havivon.pigeon.fakes

import com.havivon.pigeon.domain.analysis.AnalysisSeams
import com.havivon.pigeon.ui.common.ConversationMapper
import com.havivon.pigeon.ui.common.DateLabels
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Runs viewModelScope (Dispatchers.Main) on a test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

val Utc: ZoneOffset = ZoneOffset.UTC

/** Sunday 1 November 2026, 12:00 UTC: "now" in every test. */
val Now: Long = millis(2026, 11, 1, 12, 0)

fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long = LocalDateTime.of(y, mo, d, h, mi).toInstant(Utc).toEpochMilli()

fun englishLabels() = DateLabels(Locale.ENGLISH, Utc, is24Hour = true, today = "Today", yesterday = "Yesterday", tomorrow = "Tomorrow")

fun hebrewLabels() = DateLabels(Locale("iw"), Utc, is24Hour = true, today = "היום", yesterday = "אתמול", tomorrow = "מחר")

fun testMapper(labels: DateLabels = englishLabels()) =
    ConversationMapper(labels, AnalysisSeams.codeFinder, AnalysisSeams.folderClassifier, lockedSnippet = "Locked chat")

package com.havivon.pigeon.domain.crash

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The crash report that is written without a network: what it keeps, what it never does, and the dialog that offers it. */
class CrashReportTest {

    private val header = CrashHeader("0.6.4 (31)", "14 (API 34)", "Google Pixel 8", 1_700_000_000_000L)
    private val dir: File = Files.createTempDirectory("crash").toFile()

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun report(error: Throwable, thread: String = "main") = CrashReport.build(header, thread, error)

    @Test
    fun theHeaderHasTheVersionTheAndroidTheDeviceTheTimeAndTheThread() {
        val text = report(IllegalStateException("x"), "main")
        assertTrue(text.contains("App: 0.6.4 (31)"))
        assertTrue(text.contains("Android: 14 (API 34)"))
        assertTrue(text.contains("Device: Google Pixel 8"))
        assertTrue(text.contains("Time: 2023-11-14T22:13:20Z"))
        assertTrue(text.contains("Thread: main"))
    }

    @Test
    fun anExceptionsMessageIsNeverKept() {
        val text = report(IllegalStateException("Cannot send to +972525550123, Dana Cohen: meet at 5"))
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertFalse(text.contains("972525550123"))
        assertFalse(text.contains("Dana"))
        assertFalse(text.contains("meet at"))
    }

    @Test
    fun aClassCastMessageHasOnlyClassNamesSoItStays() {
        val text = report(ClassCastException("com.havivon.pigeon.domain.model.Conversation cannot be cast to java.lang.Void"))
        assertTrue(text.contains("java.lang.ClassCastException: com.havivon.pigeon.domain.model.Conversation cannot be cast to java.lang.Void"))
        val jvm = report(ClassCastException("class a.B cannot be cast to class c.D (a.B and c.D are in unnamed module of loader 'app')"))
        assertTrue(jvm.contains("a.B cannot be cast to class c.D"))
    }

    @Test
    fun aClassCastMessageWithAnythingElseInItIsDropped() {
        val text = report(ClassCastException("Dana Cohen said hi cannot be cast to 0525550123 now"))
        assertFalse(text.contains("Dana"))
        assertFalse(text.contains("0525550123"))
        assertTrue(text.contains("java.lang.ClassCastException"))
    }

    @Test
    fun aNullPointerMessageAboutAMethodStaysButAFreeTextOneDoesNot() {
        val method = report(NullPointerException("Attempt to invoke virtual method 'java.lang.String a.B.name()' on a null object reference"))
        assertTrue(method.contains("Attempt to invoke virtual method 'java.lang.String a.B.name()' on a null object reference"))
        val free = report(NullPointerException("no sender for Dana"))
        assertFalse(free.contains("Dana"))
    }

    @Test
    fun anIndexMessageWithOnlyNumbersStays() {
        val ok = report(IndexOutOfBoundsException("Index 5 out of bounds for length 3"))
        assertTrue(ok.contains("Index 5 out of bounds for length 3"))
        val other = report(IndexOutOfBoundsException("Index 5 out of bounds for Dana"))
        assertFalse(other.contains("Dana"))
    }

    @Test
    fun theStackFramesAreKeptWithClassMethodAndLine() {
        val error = IllegalStateException("secret").apply {
            stackTrace = arrayOf(
                StackTraceElement("com.havivon.pigeon.ui.search.SearchSheetsKt", "MessageSheet", "SearchSheets.kt", 210),
                StackTraceElement("androidx.compose.material3.ModalBottomSheetKt", "invoke", "ModalBottomSheet.kt", 99),
            )
        }
        val text = report(error)
        assertTrue(text.contains("\tat com.havivon.pigeon.ui.search.SearchSheetsKt.MessageSheet(SearchSheets.kt:210)"))
        assertTrue(text.contains("\tat androidx.compose.material3.ModalBottomSheetKt.invoke(ModalBottomSheet.kt:99)"))
        assertFalse(text.contains("secret"))
    }

    @Test
    fun theCausedByChainIsKeptInOrder() {
        val root = ClassCastException("a.B cannot be cast to java.lang.Void")
        val middle = RuntimeException("Dana's number is 0525550123", root)
        val top = IllegalStateException("outer", middle)
        val lines = report(top).lines()
        val chain = lines.filter { it.startsWith("java.lang.IllegalStateException") || it.startsWith("Caused by:") }
        assertEquals(
            listOf("java.lang.IllegalStateException", "Caused by: java.lang.RuntimeException", "Caused by: java.lang.ClassCastException: a.B cannot be cast to java.lang.Void"),
            chain,
        )
        assertFalse(report(top).contains("0525550123"))
    }

    @Test
    fun aCauseThatLoopsBackDoesNotLoopTheReport() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        assertEquals(2, report(b).lines().count { it.startsWith("java.lang.RuntimeException") || it.startsWith("Caused by: java.lang.RuntimeException") })
    }

    @Test
    fun aThreadNameIsReducedToPlainCharacters() {
        val text = report(RuntimeException(), "worker\nDana 0525550123 <x>")
        val line = text.lines().first { it.startsWith("Thread:") }
        assertFalse(line.contains("<"))
        assertTrue(line.length <= "Thread: ".length + 40)
    }

    @Test
    fun theStoreKeepsTheLatestReportAndForgetsItOnClear() {
        val store = CrashStore(File(dir, "crash"))
        assertNull(store.pending())
        assertNull(store.latest())
        store.save("first")
        store.save("second")
        assertEquals("second", store.pending())
        store.clear()
        assertNull(store.latest())
    }

    @Test
    fun aSentReportIsNotOfferedAgainButStaysForTheSettings() {
        val store = CrashStore(File(dir, "crash"))
        store.save("report")
        store.markSent()
        assertNull(store.pending())
        assertEquals("report", store.latest())
        store.save("newer")
        assertEquals("newer", store.pending())
    }

    @Test
    fun theDialogShowsTheReportOfTheLastRunAndNothingWithoutOne() {
        val store = CrashStore(File(dir, "crash"))
        assertNull(CrashPrompt(store).report.value)
        store.save("report")
        assertEquals("report", CrashPrompt(store).report.value)
    }

    @Test
    fun notNowDeletesTheReport() {
        val store = CrashStore(File(dir, "crash"))
        store.save("report")
        val prompt = CrashPrompt(store)
        prompt.dismiss()
        assertNull(prompt.report.value)
        assertNull(store.latest())
        assertNull(CrashPrompt(store).report.value)
    }

    @Test
    fun sendClosesTheDialogForGoodButKeepsTheReportForTheSettings() {
        val store = CrashStore(File(dir, "crash"))
        store.save("report")
        val prompt = CrashPrompt(store)
        assertNotNull(prompt.report.value)
        prompt.sent()
        assertNull(prompt.report.value)
        assertNull(CrashPrompt(store).report.value)
        assertEquals("report", store.latest())
    }
}

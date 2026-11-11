package com.havivon.pigeon.domain.crash

import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What a crash report says about the phone and the build: technical facts only. */
data class CrashHeader(
    val appVersion: String,
    val androidVersion: String,
    val device: String,
    val timeMillis: Long,
)

/**
 * Turns a crash into text that is safe to send: class names and stack frames (class, method, file and line),
 * never an exception's message, because messages can carry what the app was handling (a name, a number, a
 * message). The one exception is a short whitelist of messages that only ever contain class names or numbers.
 */
object CrashReport {
    private const val MAX_CAUSES = 8
    private const val MAX_FRAMES = 60
    private const val MAX_THREAD_NAME = 40

    private val identifier = """[\w.$\[\];/<>]+"""
    private val castMessage = Regex("""^(class )?$identifier cannot be cast to (class )?$identifier( \([\w.$ ,@'\[\]/:;-]*\))?$""")
    private val nullMessage = Regex("""^Attempt to [a-z ]+ '?$identifier[\w.$ ,()\[\];/<>]*'?( on a| of a)? null object reference$""")
    private val numbers = Regex("""-?\d+""")
    private val indexWords = Regex("""\b(index|length|size|out|of|bounds|for)\b""", RegexOption.IGNORE_CASE)

    fun build(header: CrashHeader, threadName: String, error: Throwable): String = buildString {
        appendLine("Pigeon crash report")
        appendLine("App: ${header.appVersion}")
        appendLine("Android: ${header.androidVersion}")
        appendLine("Device: ${header.device}")
        appendLine("Time: ${Instant.ofEpochMilli(header.timeMillis)}")
        appendLine("Thread: ${threadName.filter { it.isLetterOrDigit() || it in " .:_-" }.take(MAX_THREAD_NAME)}")
        appendLine()
        var current: Throwable? = error
        val seen = HashSet<Throwable>()
        var depth = 0
        while (current != null && seen.add(current) && depth < MAX_CAUSES) {
            append(if (depth == 0) "" else "Caused by: ")
            appendLine(describe(current))
            val frames = current.stackTrace
            for (frame in frames.take(MAX_FRAMES)) appendLine("\tat $frame")
            if (frames.size > MAX_FRAMES) appendLine("\t... ${frames.size - MAX_FRAMES} more")
            current = current.cause
            depth++
        }
    }

    /** The class name, and the message only when it is on the whitelist. */
    internal fun describe(t: Throwable): String {
        val message = t.message
        val name = t.javaClass.name
        return if (message != null && safe(t, message)) "$name: $message" else name
    }

    private fun safe(t: Throwable, message: String): Boolean = when {
        message.length > 300 || '\n' in message -> false
        t is ClassCastException -> castMessage.matches(message)
        t is NullPointerException -> nullMessage.matches(message)
        t is IndexOutOfBoundsException -> message.replace(numbers, "").replace(indexWords, "").all { it in "=:,; " }
        else -> false
    }
}

/** The one crash report on the phone, in the app's private storage: [last] not yet offered, [sent] shared once already. */
class CrashStore(private val dir: File) {
    private val last get() = File(dir, "last.txt")
    private val sent get() = File(dir, "sent.txt")

    fun save(text: String) {
        dir.mkdirs()
        sent.delete()
        last.writeText(text)
    }

    /** A report not offered to the user yet. */
    fun pending(): String? = last.takeIf { it.isFile }?.readText()

    /** The latest report, offered or not: what "Send last crash report" in the settings shares. */
    fun latest(): String? = pending() ?: sent.takeIf { it.isFile }?.readText()

    /** The report was handed to the share sheet: it stays for the settings row, but is not offered again at launch. */
    fun markSent() {
        if (last.isFile && !last.renameTo(sent)) {
            sent.writeText(last.readText())
            last.delete()
        }
    }

    fun clear() {
        last.delete()
        sent.delete()
    }
}

/** The dialog shown at launch for a crash report left by the last run. */
class CrashPrompt(private val store: CrashStore) {
    private val _report = MutableStateFlow(store.pending())

    /** The text to offer, or null when there is nothing (or it has been answered). */
    val report: StateFlow<String?> = _report

    /** "Send report": the caller shares the text; the dialog closes and does not come back. */
    fun sent() {
        store.markSent()
        _report.value = null
    }

    /** "Not now": the report is deleted. */
    fun dismiss() {
        store.clear()
        _report.value = null
    }
}

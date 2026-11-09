package com.yaadein.data.crash

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Global exception handler that appends a short, local-only report to `files/crash.log` and then
 * lets Android handle the crash. No third-party SDK, nothing leaves the device unless the user
 * exports the file from Settings → Problem log.
 */
object CrashLog {
    private const val FILE = "crash.log"
    private const val PENDING = "crash.pending"
    private const val MAX_BYTES = 200 * 1024
    private const val MAX_TRACE_LINES = 40

    /** Export errors nest several causes; keep enough lines to reach the innermost one. */
    private const val MAX_NON_FATAL_TRACE_LINES = 120

    fun file(context: Context): File = File(context.filesDir, FILE)

    /** True when a report was written since the user last dismissed or exported one (shown as a home banner). */
    fun hasPendingReport(context: Context): Boolean = File(context.filesDir, PENDING).exists() && file(context).exists()

    fun markSeen(context: Context) { File(context.filesDir, PENDING).delete() }

    fun install(context: Context, versionName: String) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { append(context, versionName, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun append(context: Context, versionName: String, thread: Thread, throwable: Throwable) {
        write(context, header(versionName) + "Thread: ${thread.name}\n" + trace(throwable, MAX_TRACE_LINES))
        File(context.filesDir, PENDING).writeText("1")
    }

    /**
     * Records a handled failure (for example a recap export error) so it can be shared from
     * Settings → Problem log. Does not raise the "app closed unexpectedly" banner.
     */
    fun recordNonFatal(context: Context, what: String, throwable: Throwable) {
        runCatching {
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
            write(context, header(version) + "Handled: $what\n" + trace(throwable, MAX_NON_FATAL_TRACE_LINES))
        }
    }

    private fun header(versionName: String): String =
        "==== ${Instant.now()} ====\nYaadein $versionName · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n"

    private fun trace(throwable: Throwable, maxLines: Int): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString().lines().take(maxLines).joinToString("\n") + "\n\n"
    }

    @Synchronized
    private fun write(context: Context, entry: String) {
        val f = file(context)
        val existing = if (f.exists()) f.readText() else ""
        val combined = (existing + entry).let { if (it.length > MAX_BYTES) it.takeLast(MAX_BYTES) else it }
        f.writeText(combined)
    }

    fun read(context: Context): String = file(context).takeIf { it.exists() }?.readText().orEmpty()

    /**
     * Chooser intent that shares the log as a text attachment. The log lives in `files/`, which the
     * FileProvider does not expose, so a copy is written to `cache/exports/` (a configured root) first.
     * The text is also put in EXTRA_TEXT so apps that ignore attachments still receive it.
     * Returns null when there is nothing to share.
     */
    fun shareIntent(context: Context, chooserTitle: CharSequence): Intent? {
        val text = read(context)
        if (text.isBlank()) return null
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val copy = File(exportDir, EXPORT_NAME).apply { writeText(text) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", copy)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, EXPORT_NAME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text.takeLast(MAX_SHARE_TEXT))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newUri(context.contentResolver, EXPORT_NAME, uri)
        return Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private const val EXPORT_NAME = "yaadein-problem-log.txt"

    /** Binder transactions are limited to about 1 MB; keep the inline text well under that. */
    private const val MAX_SHARE_TEXT = 60 * 1024
    fun clear(context: Context) { file(context).delete(); markSeen(context) }
}

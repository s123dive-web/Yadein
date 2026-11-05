package com.yaadein.data.crash

import android.content.Context
import android.os.Build
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
    private const val MAX_BYTES = 200 * 1024

    fun file(context: Context): File = File(context.filesDir, FILE)

    fun install(context: Context, versionName: String) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { append(context, versionName, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun append(context: Context, versionName: String, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val entry = buildString {
            append("==== ").append(Instant.now()).append(" ====\n")
            append("Yaadein ").append(versionName).append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(") · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Thread: ").append(thread.name).append('\n')
            append(sw.toString().lines().take(40).joinToString("\n")).append("\n\n")
        }
        val f = file(context)
        val existing = if (f.exists()) f.readText() else ""
        val combined = (existing + entry).let { if (it.length > MAX_BYTES) it.takeLast(MAX_BYTES) else it }
        f.writeText(combined)
    }

    fun read(context: Context): String = file(context).takeIf { it.exists() }?.readText().orEmpty()
    fun clear(context: Context) { file(context).delete() }
}

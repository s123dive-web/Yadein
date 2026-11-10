package com.yaadein.feature.recap

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * "Send to WhatsApp": hands a rendered reel straight to WhatsApp (or WhatsApp Business) instead of the share sheet.
 * The app has no INTERNET permission and never talks to WhatsApp itself; it only starts WhatsApp's own share activity
 * with a read grant on the file. The two package names are declared in the app manifest's `<queries>` so they can
 * be resolved on Android 11+.
 */
object WhatsAppShare {
    /** WhatsApp cuts a status at 30 seconds. */
    const val STATUS_MAX_MS = 30_000L

    /** Above this WhatsApp re-compresses the video (visibly) before posting it. */
    const val STATUS_MAX_BYTES = 16L * 1024 * 1024

    private val PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    /** The installed WhatsApp package (consumer first, then Business), or null when neither is installed. */
    fun whatsAppPackage(context: Context): String? = PACKAGES.firstOrNull { pkg ->
        runCatching { context.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull() != null
    }

    /**
     * Opens WhatsApp with [uri] (a content URI this app can grant) attached as a video. Returns false when WhatsApp is
     * not installed so the caller can tell the user; if WhatsApp is installed but refuses the intent, the plain share
     * sheet is shown instead and true is returned.
     */
    fun sendToWhatsApp(context: Context, uri: Uri, chooserTitle: CharSequence): Boolean {
        val pkg = whatsAppPackage(context) ?: return false
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = android.content.ClipData.newRawUri(null, uri)
        return try {
            context.startActivity(Intent(send).setPackage(pkg))
            true
        } catch (_: ActivityNotFoundException) {
            runCatching { context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            true
        }
    }

    /** True when a reel of this length and size posts as a WhatsApp status without being cut or re-compressed. */
    fun fitsStatus(durationMs: Long, sizeBytes: Long): Boolean =
        durationMs in 1..STATUS_MAX_MS && sizeBytes in 1..STATUS_MAX_BYTES

    /** True when WhatsApp will compress the file before posting it (only meaningful once the size is known). */
    fun tooBigForStatus(sizeBytes: Long): Boolean = sizeBytes > STATUS_MAX_BYTES

    private const val MIME = "video/mp4"
}

/** Whether WhatsApp is installed, looked up once per composition. */
@Composable
fun rememberWhatsAppAvailable(): Boolean {
    val context = LocalContext.current
    return remember(context) { WhatsAppShare.whatsAppPackage(context) != null }
}

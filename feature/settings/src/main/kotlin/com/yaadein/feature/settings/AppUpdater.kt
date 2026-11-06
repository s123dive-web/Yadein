package com.yaadein.feature.settings

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** What to do with an APK the user picked in Settings → Update app. Pure, so it is unit-tested. */
sealed interface UpdateDecision {
    data class Install(val newVersionName: String, val newVersionCode: Long) : UpdateDecision
    /** Same or older build: nothing to do. */
    data class AlreadyUpToDate(val installedVersionName: String, val fileVersionName: String, val fileIsOlder: Boolean) : UpdateDecision
    /** Not Yaadein (or the other build type, e.g. the Play release instead of this debug build). */
    data object NotThisApp : UpdateDecision
    /** Signed with a different key: Android would refuse it, and the only way round is uninstalling, which deletes everything. */
    data object DifferentSignature : UpdateDecision
    data object Unreadable : UpdateDecision

    companion object {
        fun decide(installed: AppVersion, candidate: AppVersion?): UpdateDecision = when {
            candidate == null -> Unreadable
            candidate.packageName != installed.packageName -> NotThisApp
            // Unknown signers (some Android versions do not report them for a file) fall through: the system
            // installer compares keys itself and refuses a mismatch without touching any data.
            candidate.signers.isNotEmpty() && installed.signers.isNotEmpty() && candidate.signers != installed.signers -> DifferentSignature
            candidate.versionCode <= installed.versionCode ->
                AlreadyUpToDate(installed.versionName, candidate.versionName, fileIsOlder = candidate.versionCode < installed.versionCode)
            else -> Install(candidate.versionName, candidate.versionCode)
        }
    }
}

/** Package identity of an installed app or an APK file. [signers] are SHA-256 fingerprints of the signing certificates. */
data class AppVersion(val packageName: String, val versionCode: Long, val versionName: String, val signers: Set<String>)

/**
 * Installs a newer Yaadein APK over the current one with [PackageInstaller]. Same package name and same signing
 * key mean Android keeps the app's data (database, encrypted media, keys); only the code is replaced.
 *
 * Debug/sideload builds only: the REQUEST_INSTALL_PACKAGES permission and the status receiver are declared in
 * `app/src/debug/AndroidManifest.xml`, because Play-distributed apps may not update themselves.
 */
class AppUpdater(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val candidateFile: File get() = File(context.cacheDir, "update/candidate.apk")

    fun installed(): AppVersion = versionOf(pm.packageInfo(context.packageName))!!

    /** Copies the picked file into the cache (PackageManager needs a path) and decides what to do with it. */
    suspend fun inspect(uri: Uri): UpdateDecision = withContext(Dispatchers.IO) {
        val file = candidateFile.apply { parentFile?.mkdirs(); delete() }
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it, COPY_BUFFER) } }
        }.isSuccess
        decideOn(file, copied)
    }

    /**
     * A file checked earlier and not yet installed (for example Android closed the app while the user was on the
     * permission screen). Re-judged against the installed version, so an already-installed update is cleared.
     */
    suspend fun resumeCandidate(): UpdateDecision? = withContext(Dispatchers.IO) {
        val file = candidateFile
        if (!file.exists()) null else decideOn(file, copied = true).takeIf { it is UpdateDecision.Install }
    }

    fun clearCandidate() { candidateFile.parentFile?.deleteRecursively() }

    private fun decideOn(file: File, copied: Boolean): UpdateDecision {
        val candidate = if (copied) versionOf(pm.archiveInfo(file.path)) else null
        return UpdateDecision.decide(installed(), candidate).also { if (it !is UpdateDecision.Install) file.delete() }
    }

    /** Android asks once whether this app may install apps ("Install unknown apps"). */
    fun canInstall(): Boolean = pm.canRequestPackageInstalls()

    fun permissionSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Streams the inspected APK into an install session; Android then shows its own "Update this app?" prompt. */
    suspend fun install() = withContext(Dispatchers.IO) {
        val file = candidateFile
        check(file.exists()) { "no update file" }
        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            // Android 12+: once Yaadein has installed itself (it becomes the installer of record), later updates need
            // no "Update this app?" confirmation at all. The first one still asks, which Android requires.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            // Android 14+: keep Yaadein as the update owner so another installer does not take over silently.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) setRequestUpdateOwnership(true)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    input.copyTo(out, COPY_BUFFER)
                    session.fsync(out)
                }
            }
            val status = Intent(UpdateStatusReceiver.ACTION).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, sessionId, status, flags).intentSender)
        }
    }

    private fun versionOf(info: PackageInfo?): AppVersion? {
        info ?: return null
        return AppVersion(info.packageName, PackageInfoCompat.getLongVersionCode(info), info.versionName ?: "?", signersOf(info))
    }

    @Suppress("DEPRECATION")
    private fun signersOf(info: PackageInfo): Set<String> {
        val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            info.signatures
        }
        return certs.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    private fun PackageManager.packageInfo(name: String): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getPackageInfo(name, PackageManager.PackageInfoFlags.of(signingFlags().toLong()))
        else @Suppress("DEPRECATION") getPackageInfo(name, signingFlags())
    }.getOrNull()

    private fun PackageManager.archiveInfo(path: String): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(signingFlags().toLong()))
        else @Suppress("DEPRECATION") getPackageArchiveInfo(path, signingFlags())
    }.getOrNull()
}

/** Install progress reported by the system, observed by the Update screen. */
object UpdateEvents {
    sealed interface Event {
        data object Idle : Event
        data class Failed(val message: String) : Event
        /** Android's "Update this app?" screen, to be started from the Update screen so it opens over Yaadein. */
        data class ConfirmNeeded(val intent: Intent) : Event
    }
    private val _events = MutableStateFlow<Event>(Event.Idle)
    val events: StateFlow<Event> = _events.asStateFlow()
    fun post(event: Event) { _events.value = event }
}

/**
 * Receives the install session's status. Android first answers "pending user action" with its confirmation
 * screen, which is started here; on success the old process is replaced, so no success message arrives.
 */
class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                // Handed to the Update screen, which starts it from the activity so it stays in Yaadein's task
                // (starting it from here needs NEW_TASK, which jumped out of the app).
                UpdateEvents.post(UpdateEvents.Event.ConfirmNeeded(confirm))
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateEvents.post(UpdateEvents.Event.Idle)
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                UpdateEvents.post(UpdateEvents.Event.Failed(message))
            }
        }
    }

    companion object {
        const val ACTION = "com.yaadein.app.UPDATE_STATUS"
    }
}

/**
 * Runs in the new version right after an update (ACTION_MY_PACKAGE_REPLACED): clears the installed APK copy and posts a
 * heads-up "Yaadein updated, tap to open" notification. It also tries to reopen the app, but Android 10+ blocks activity
 * starts from the background (verified on API 34: "Background activity launch blocked"), so the notification is the path.
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        File(context.cacheDir, "update").deleteRecursively()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        runCatching { context.startActivity(launch) }
        runCatching {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            // High importance: shown as a heads-up banner, the quickest way back into the updated app.
            nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL, context.getString(R.string.update_channel), android.app.NotificationManager.IMPORTANCE_HIGH))
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
            val tap = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = androidx.core.app.NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(com.yaadein.core.ui.R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.update_done_title, version))
                .setContentText(context.getString(R.string.update_done_text))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFICATION_ID, n)
        }
    }

    private companion object {
        const val CHANNEL = "app_update_ready"
        const val NOTIFICATION_ID = 4301
    }
}

private const val COPY_BUFFER = 1 shl 20

package com.yaadein.core.ui.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The language Yaadein is shown in, chosen on the welcome screen. Null means "follow the phone".
 *
 * Android 13+ keeps the choice itself (per-app language; the same setting as Settings → Apps → Yaadein → Language) and
 * recreates the activity. Older phones keep the tag in a small preferences file (a language code is not a secret) that
 * [wrap] applies to the activity's base context.
 */
object AppLanguage {
    /** The eight languages the app is translated into; keep in step with `locales_config.xml` and the `values-*` folders. */
    val TAGS = listOf("en", "hi", "mr", "gu", "ta", "te", "bn", "kn")

    /** Each language in its own script: never translated, so a reader can always find theirs. */
    fun endonym(tag: String): String = when (tag) {
        "en" -> "English"
        "hi" -> "हिन्दी"
        "mr" -> "मराठी"
        "gu" -> "ગુજરાતી"
        "ta" -> "தமிழ்"
        "te" -> "తెలుగు"
        "bn" -> "বাংলা"
        "kn" -> "ಕನ್ನಡ"
        else -> tag
    }

    /** The chosen language tag, or null while the app follows the phone's language. */
    fun current(context: Context): String? {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = runCatching { context.getSystemService(LocaleManager::class.java)?.applicationLocales }.getOrNull()
            if (locales == null || locales.isEmpty) null else locales.get(0).language
        } else {
            prefs(context).getString(KEY, null)
        }
        return tag?.takeIf { it in TAGS }
    }

    /** Switches to [tag] (null = follow the phone); the activity is recreated so every screen picks it up. */
    fun set(context: Context, tag: String?) {
        val chosen = tag?.takeIf { it in TAGS }
        if (chosen == current(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                    if (chosen == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(chosen)
            }
        } else {
            prefs(context).edit().apply { if (chosen == null) remove(KEY) else putString(KEY, chosen) }.apply()
            context.findActivity()?.recreate()
        }
    }

    /** For `Activity.attachBaseContext` on phones before Android 13: [base] in the chosen language, or [base] itself. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = prefs(base).getString(KEY, null)?.takeIf { it in TAGS } ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) = context.applicationContext.let { it ?: context }.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    private const val PREFS = "yaadein_ui"
    private const val KEY = "language"
}

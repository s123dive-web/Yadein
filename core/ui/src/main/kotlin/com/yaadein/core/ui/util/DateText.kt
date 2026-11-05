package com.yaadein.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.yaadein.core.ui.R
import com.yaadein.domain.dates.DateLabel
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.MemoryDate
import java.text.NumberFormat
import java.util.Locale

/** The user's current locale, read in a configuration-aware way. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Localised, fuzzy-aware date text: "12 Mar 2024", "December 2024", "Sometime in 2015", "Summer 2015". */
@Composable
fun memoryDateText(date: MemoryDate): String {
    val locale = currentLocale()
    return when (val label = Dates.label(date, locale)) {
        is DateLabel.Exact -> label.text
        is DateLabel.Month -> label.text
        is DateLabel.Year -> stringResource(R.string.date_sometime_in, label.text)
        is DateLabel.Fuzzy -> label.text
    }
}

fun formatBytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
    val nf = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    val kb = 1024.0
    return when {
        bytes >= kb * kb * kb -> "${nf.format(bytes / (kb * kb * kb))} GB"
        bytes >= kb * kb -> "${nf.format(bytes / (kb * kb))} MB"
        bytes >= kb -> "${nf.format(bytes / kb)} KB"
        else -> "$bytes B"
    }
}

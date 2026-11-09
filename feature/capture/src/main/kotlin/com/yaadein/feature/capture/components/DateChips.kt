package com.yaadein.feature.capture.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.core.ui.util.memoryDateText
import com.yaadein.core.ui.util.currentLocale
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.DateKind
import com.yaadein.domain.model.MemoryDate
import com.yaadein.feature.capture.R
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.TextStyle
import com.yaadein.core.ui.R as CoreR

/**
 * "When was this?" — the first field of every capture flow. Chips for Today, Yesterday, Pick a date,
 * plus fuzzy options: month only, year only, custom label ("Summer 2015").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateChips(
    value: MemoryDate,
    onChange: (MemoryDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val spacing = Yaadein.spacing
    var showPicker by remember { mutableStateOf(false) }
    var showMonth by remember { mutableStateOf(false) }
    var showYear by remember { mutableStateOf(false) }
    var showLabel by remember { mutableStateOf(false) }
    val todayDay = today.toEpochDay()
    val yesterdayDay = today.minusDays(1).toEpochDay()
    val isToday = value.kind == DateKind.EXACT && value.startEpochDay == todayDay && value.endEpochDay == null
    val isYesterday = value.kind == DateKind.EXACT && value.startEpochDay == yesterdayDay && value.endEpochDay == null
    val isPicked = value.kind == DateKind.EXACT && !isToday && !isYesterday

    Column(modifier = modifier) {
        Text(stringResource(R.string.date_when), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(spacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            FilterChip(selected = isToday, onClick = { onChange(Dates.exact(today)) }, label = { Text(stringResource(CoreR.string.today)) })
            FilterChip(selected = isYesterday, onClick = { onChange(Dates.exact(today.minusDays(1))) }, label = { Text(stringResource(CoreR.string.yesterday)) })
            FilterChip(
                selected = isPicked,
                onClick = { showPicker = true },
                label = { Text(if (isPicked) memoryDateText(value) else stringResource(R.string.date_pick)) },
                leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
            )
            FilterChip(selected = value.kind == DateKind.MONTH, onClick = { showMonth = true }, label = { Text(if (value.kind == DateKind.MONTH) memoryDateText(value) else stringResource(R.string.date_month_only)) })
            FilterChip(selected = value.kind == DateKind.YEAR, onClick = { showYear = true }, label = { Text(if (value.kind == DateKind.YEAR) Dates.year(value).toString() else stringResource(R.string.date_year_only)) })
            FilterChip(selected = value.kind == DateKind.FUZZY, onClick = { showLabel = true }, label = { Text(if (value.kind == DateKind.FUZZY) (value.label ?: "") else stringResource(R.string.date_custom_label)) })
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Dates.start(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Dates.exact(LocalDate.ofEpochDay(it / 86_400_000L))) }
                    showPicker = false
                }) { Text(stringResource(CoreR.string.action_done)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(CoreR.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
    if (showMonth) {
        MonthYearDialog(
            initial = YearMonth.from(Dates.start(value)),
            onDismiss = { showMonth = false },
            onPick = { onChange(Dates.month(it)); showMonth = false },
            today = today,
        )
    }
    if (showYear) {
        YearDialog(initial = Dates.year(value), onDismiss = { showYear = false }, onPick = { onChange(Dates.year(it)); showYear = false }, today = today)
    }
    if (showLabel) {
        LabelDialog(
            initialLabel = value.label ?: "",
            initialYear = Dates.year(value),
            onDismiss = { showLabel = false },
            onPick = { label, year, month -> onChange(Dates.fuzzy(label, year, month)); showLabel = false },
            today = today,
        )
    }
}

@Composable
private fun MonthYearDialog(initial: YearMonth, onDismiss: () -> Unit, onPick: (YearMonth) -> Unit, today: LocalDate) {
    var year by remember { mutableStateOf(initial.year) }
    var month by remember { mutableStateOf(initial.month) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.date_month_only)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Yaadein.spacing.sm)) {
                YearStepper(year = year, onYear = { year = it }, today = today)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Yaadein.spacing.xs), verticalArrangement = Arrangement.spacedBy(Yaadein.spacing.xs)) {
                    Month.entries.forEach { m ->
                        FilterChip(selected = m == month, onClick = { month = m }, label = { Text(m.getDisplayName(TextStyle.SHORT, currentLocale())) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(YearMonth.of(year, month)) }) { Text(stringResource(CoreR.string.action_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_cancel)) } },
    )
}

@Composable
private fun YearDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit, today: LocalDate) {
    var year by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.date_year_only)) },
        text = { YearStepper(year = year, onYear = { year = it }, today = today) },
        confirmButton = { TextButton(onClick = { onPick(year) }) { Text(stringResource(CoreR.string.action_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_cancel)) } },
    )
}

@Composable
private fun LabelDialog(initialLabel: String, initialYear: Int, onDismiss: () -> Unit, onPick: (String, Int, Int) -> Unit, today: LocalDate) {
    var label by remember { mutableStateOf(initialLabel) }
    var year by remember { mutableStateOf(initialYear) }
    val suggestions = listOf(
        stringResource(R.string.date_label_summer) to 5,
        stringResource(R.string.date_label_monsoon) to 7,
        stringResource(R.string.date_label_winter) to 12,
        stringResource(R.string.date_label_diwali) to 10,
        stringResource(R.string.date_label_early) to 2,
        stringResource(R.string.date_label_late) to 11,
    )
    var anchorMonth by remember { mutableStateOf(1) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.date_custom_label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Yaadein.spacing.sm)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Yaadein.spacing.xs), verticalArrangement = Arrangement.spacedBy(Yaadein.spacing.xs)) {
                    suggestions.forEach { (s, m) ->
                        FilterChip(selected = label.startsWith(s), onClick = { label = s; anchorMonth = m }, label = { Text(s) })
                    }
                }
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.date_label_hint)) }, singleLine = true)
                YearStepper(year = year, onYear = { year = it }, today = today)
            }
        },
        confirmButton = {
            TextButton(enabled = label.isNotBlank(), onClick = {
                val finalLabel = if (label.contains(year.toString())) label.trim() else "${label.trim()} $year"
                onPick(finalLabel, year, anchorMonth)
            }) { Text(stringResource(CoreR.string.action_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_cancel)) } },
    )
}

@Composable
fun YearStepper(year: Int, onYear: (Int) -> Unit, today: LocalDate) {
    val spacing = Yaadein.spacing
    var menu by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        TextButton(onClick = { if (year > 1900) onYear(year - 1) }) { Text("−") }
        TextButton(onClick = { menu = true }) { Text(year.toString(), style = MaterialTheme.typography.titleLarge) }
        TextButton(onClick = { if (year < today.year + 1) onYear(year + 1) }) { Text("+") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            (today.year downTo today.year - 60).forEach { y ->
                DropdownMenuItem(text = { Text(y.toString()) }, onClick = { onYear(y); menu = false })
            }
        }
    }
}

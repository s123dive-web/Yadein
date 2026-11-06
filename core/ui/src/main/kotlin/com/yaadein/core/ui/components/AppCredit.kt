package com.yaadein.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.yaadein.core.ui.R
import com.yaadein.core.ui.theme.Yaadein

/** The maker's credit, a quiet one-line strip shown at the bottom of every screen. */
@Composable
fun AppCredit(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.app_credit),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier.fillMaxWidth().padding(vertical = Yaadein.spacing.xxs),
    )
}

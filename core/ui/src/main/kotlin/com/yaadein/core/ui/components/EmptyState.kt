package com.yaadein.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.yaadein.core.ui.illustrations.Illustration
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein

/** A designed empty state: friendly illustration, headline, one line of help and an optional action. */
@Composable
fun EmptyState(
    illustration: IllustrationKind,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = Yaadein.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xxl, vertical = spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Illustration(kind = illustration, modifier = Modifier.size(Yaadein.sizes.illustration))
        Spacer(Modifier.height(spacing.xl))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(spacing.xl))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

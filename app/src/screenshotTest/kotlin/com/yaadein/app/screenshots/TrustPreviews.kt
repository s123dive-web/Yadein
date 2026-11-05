package com.yaadein.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest
import com.yaadein.core.ui.illustrations.Illustration
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.SansFamily
import com.yaadein.core.ui.theme.SerifFamily
import com.yaadein.feature.settings.BackupScreen
import com.yaadein.feature.settings.BackupStatus
import com.yaadein.feature.settings.FacesScreen
import com.yaadein.feature.settings.FacesUiState
import com.yaadein.feature.settings.PrivacyScreen

private const val PHONE = "spec:width=411dp,height=891dp"

@PreviewTest
@Preview(name = "Backup", device = PHONE, showBackground = true)
@Composable
fun Backup() = ScreenshotTheme { Surface { BackupScreen(status = BackupStatus.Idle, onBack = {}, onExport = {}, onImport = {}, onConfirmRestore = {}, onReset = {}) } }

@PreviewTest
@Preview(name = "Face suggestions", device = PHONE, showBackground = true)
@Composable
fun Faces() = ScreenshotTheme { Surface { FacesScreen(state = FacesUiState(enabled = true, people = Fixtures.people), onBack = {}, onEnabled = {}, onScan = {}, onConfirm = { _, _ -> }, onDismiss = {}) } }

@PreviewTest
@Preview(name = "Privacy", device = PHONE, showBackground = true)
@Composable
fun Privacy() = ScreenshotTheme { Surface { PrivacyScreen(onBack = {}) } }

/** Play Store feature graphic, rendered at exactly 1024 × 500 px (dpi 160 → 1 dp = 1 px). */
@PreviewTest
@Preview(name = "Feature graphic", device = "spec:width=1024dp,height=500dp,dpi=160", showBackground = true)
@Composable
fun FeatureGraphic() = ScreenshotTheme {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3B1A5C), Color(0xFF7A3E9D), Color(0xFFE8A33D)))), contentAlignment = Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 72.dp)) {
            Illustration(kind = IllustrationKind.PHOTO_STACK, modifier = Modifier.size(300.dp))
            Spacer(Modifier.width(48.dp))
            Column {
                Text("Yaadein", fontFamily = SerifFamily, fontSize = 72.sp, color = Color.White, lineHeight = 80.sp)
                Spacer(Modifier.height(8.dp))
                Text("Private memories of the people you love.", fontFamily = SansFamily, fontSize = 28.sp, color = Color.White.copy(alpha = 0.92f), lineHeight = 36.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("100% offline", "Encrypted", "No ads, no accounts").forEach { chip ->
                        Text(chip, fontFamily = SansFamily, fontSize = 18.sp, color = Color(0xFF3B1A5C), modifier = Modifier.background(Color.White.copy(alpha = 0.9f), MaterialTheme.shapes.large).padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}

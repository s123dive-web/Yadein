package com.yaadein.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.yaadein.core.ui.R
import com.yaadein.core.ui.theme.Yaadein

/**
 * Password entry for encrypted files (family packs). No autocorrect or capitalisation, a show/hide toggle, and a second
 * field when the password is being chosen, so what is typed is exactly what protects the file.
 */
@Composable
fun PasswordDialog(
    title: String,
    message: String,
    confirmLabel: String,
    requireConfirm: Boolean,
    onDismiss: () -> Unit,
    minLength: Int = 6,
    onConfirm: (String) -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val tooShort = pw.length < minLength
    val mismatch = requireConfirm && pw2.isNotEmpty() && pw2 != pw
    val ok = !tooShort && (!requireConfirm || pw == pw2)
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)
    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Yaadein.spacing.sm)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, label = { Text(stringResource(R.string.pw_password)) },
                    visualTransformation = transformation, keyboardOptions = keyboard, singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = stringResource(if (visible) R.string.pw_hide else R.string.pw_show))
                        }
                    },
                    isError = pw.isNotEmpty() && tooShort,
                    supportingText = if (pw.isNotEmpty() && tooShort) { { Text(stringResource(R.string.pw_too_short, minLength)) } } else null,
                )
                if (requireConfirm) {
                    OutlinedTextField(
                        value = pw2, onValueChange = { pw2 = it }, label = { Text(stringResource(R.string.pw_again)) },
                        visualTransformation = transformation, keyboardOptions = keyboard, singleLine = true, isError = mismatch,
                        supportingText = if (mismatch) { { Text(stringResource(R.string.pw_mismatch)) } } else null,
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onConfirm(pw) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

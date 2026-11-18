package com.gfocreation.heartrateandzoneannounce

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private const val PREFS_NAME = "hr_zones_prefs"
private const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"

/**
 * Composable modal dialog that forces user to accept a medical/legal disclaimer.
 *
 * onAccept: called when user accepts (checkbox must be checked)
 * onQuit: called when user chooses to quit the app (optional)
 */
@Composable
fun DisclaimerDialog(
    onAccept: () -> Unit,
    onQuit: () -> Unit,
    defaultChecked: Boolean = false
) {
    var checked by remember { mutableStateOf(defaultChecked) }

    AlertDialog(
        onDismissRequest = { /* prevent dismiss by tapping outside: do nothing */ },
        confirmButton = {
            Button(
                onClick = { onAccept() },
                enabled = checked
            ) {
                Text(text = stringResource(R.string.disclaimer_accept_button))
            }
        },
        dismissButton = {
            TextButton(onClick = { onQuit() }) {
                Text(text = stringResource(R.string.disclaimer_quit_button))
            }
        },
        title = { Text(text = stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Surface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .verticalScroll(rememberScrollState())
                ) {

                    Text(text = stringResource(R.string.disclaimer_message))
                    Spacer(modifier = Modifier.height(16.dp))
                    // Checkbox line
                    Column {
                        RowWithCheckbox(checked = checked, onCheckedChange = { checked = it })
                    }
                }
            }
        }
    )
}

@Composable
private fun RowWithCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.disclaimer_valider),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
        )
    }
}

///* --------------------------
//   Persistence helpers
//   -------------------------- */
//
//fun saveDisclaimerAccepted(ctx: Context, accepted: Boolean) {
//    val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
//    prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, accepted).apply()
//}
//
//fun loadDisclaimerAccepted(ctx: Context): Boolean {
//    val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
//    return prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)
//}

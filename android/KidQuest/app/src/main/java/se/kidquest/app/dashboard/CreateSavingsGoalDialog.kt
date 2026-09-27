package se.kidquest.app.dashboard

import se.kidquest.app.i18n.Money
import se.kidquest.app.i18n.tr
import se.kidquest.app.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.CreateSavingsGoalRequest

@Composable
fun CreateSavingsGoalDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.goal_create_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(tr(R.string.goal_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = targetAmount,
                    onValueChange = { targetAmount = it.filter { c -> c.isDigit() }; error = null },
                    label = { Text(tr(R.string.goal_amount_label, Money.symbol())) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it; error = null },
                    label = { Text(tr(R.string.goal_emoji_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        error = tr(R.string.goal_name_missing)
                        return@TextButton
                    }
                    val amount = targetAmount.toIntOrNull() ?: 0
                    if (amount <= 0) {
                        error = tr(R.string.goal_amount_positive)
                        return@TextButton
                    }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                ApiClient.walletApi.createSavingsGoal(
                                    CreateSavingsGoalRequest(
                                        name = name.trim(),
                                        targetAmount = amount,
                                        emoji = emoji.ifBlank { null },
                                    ),
                                )
                            }
                            onSuccess()
                        } catch (e: Exception) {
                            error = ApiErrors.message(e, tr(R.string.goal_create_failed))
                        } finally {
                            loading = false
                        }
                    }
                },
            ) {
                Text(if (loading) tr(R.string.common_creating) else tr(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(R.string.common_cancel))
            }
        },
    )
}

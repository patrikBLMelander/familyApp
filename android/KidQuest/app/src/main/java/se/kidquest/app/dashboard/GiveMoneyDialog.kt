package se.kidquest.app.dashboard

import se.kidquest.app.i18n.Money
import se.kidquest.app.i18n.tr
import se.kidquest.app.R
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.kidquest.app.network.AddAllowanceRequest
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.ApiErrors

private data class Suggestion(val labelRes: Int, val amount: Int) {
    val label: String get() = tr(labelRes)
    /** Sent as the transaction's description, in the parent's language. */
    val description: String get() = tr(labelRes)
}

private val suggestions = listOf(
    Suggestion(R.string.money_monthly, 120),
    Suggestion(R.string.money_weekly, 30),
    Suggestion(R.string.money_reward, 50),
    Suggestion(R.string.money_extra, 20),
)

@Composable
fun GiveMoneyDialog(
    childName: String,
    childId: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.money_give_title, childName), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {

                // Quick-select chips
                Text(
                    text = tr(R.string.money_quick),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { s ->
                        val selected = amount == s.amount.toString() && description == s.description
                        if (selected) {
                            Button(
                                onClick = { amount = s.amount.toString(); description = s.description; error = null },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 14.dp, vertical = 6.dp,
                                ),
                            ) {
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                    Text(s.label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(Money.format(s.amount), fontSize = 11.sp)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { amount = s.amount.toString(); description = s.description; error = null },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 14.dp, vertical = 6.dp,
                                ),
                            ) {
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                    Text(s.label, fontSize = 13.sp)
                                    Text(
                                        Money.format(s.amount),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() }; error = null },
                    label = { Text(tr(R.string.amount_label, Money.symbol())) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; error = null },
                    label = { Text(tr(R.string.money_explanation)) },
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
                    val amountKr = amount.toIntOrNull() ?: 0
                    if (amountKr <= 0) { error = tr(R.string.common_enter_amount); return@TextButton }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            val response = withContext(Dispatchers.IO) {
                                ApiClient.walletApi.addAllowance(
                                    AddAllowanceRequest(
                                        childMemberId = childId,
                                        amount = amountKr,
                                        description = description.ifBlank { tr(R.string.money_default_description) },
                                        savingsGoalAllocations = null,
                                    ),
                                )
                            }
                            if (response.isSuccessful) onSuccess()
                            else error = tr(R.string.money_give_failed)
                        } catch (e: Exception) {
                            error = ApiErrors.message(e, tr(R.string.money_give_failed))
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading,
            ) {
                Text(if (loading) tr(R.string.common_sending) else tr(R.string.money_give))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(R.string.common_cancel)) }
        },
    )
}

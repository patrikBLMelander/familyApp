package se.kidquest.app.dashboard

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

private data class Suggestion(val label: String, val amount: Int, val description: String)

private val suggestions = listOf(
    Suggestion("Månadspeng", 120, "Månadspeng"),
    Suggestion("Veckopeng",   30, "Veckopeng"),
    Suggestion("Belöning",    50, "Belöning"),
    Suggestion("Extra",       20, "Extra"),
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
        title = { Text("Ge pengar till $childName", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {

                // Quick-select chips
                Text(
                    text = "Snabbval",
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
                                    Text("${s.amount} kr", fontSize = 11.sp)
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
                                        "${s.amount} kr",
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
                    label = { Text("Belopp (kr)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; error = null },
                    label = { Text("Förklaring") },
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
                    if (amountKr <= 0) { error = "Ange ett belopp"; return@TextButton }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            val response = withContext(Dispatchers.IO) {
                                ApiClient.walletApi.addAllowance(
                                    AddAllowanceRequest(
                                        childMemberId = childId,
                                        amount = amountKr,
                                        description = description.ifBlank { "Pengar" },
                                        savingsGoalAllocations = null,
                                    ),
                                )
                            }
                            if (response.isSuccessful) onSuccess()
                            else error = "Kunde inte ge pengar"
                        } catch (e: Exception) {
                            error = ApiErrors.message(e, "Kunde inte ge pengar")
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading,
            ) {
                Text(if (loading) "Skickar…" else "Ge pengar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Avbryt") }
        },
    )
}

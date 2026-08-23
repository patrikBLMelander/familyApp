package se.kidquest.app.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import se.kidquest.app.network.AllocateToGoalsRequest
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.SavingsGoalAllocationRequest
import se.kidquest.app.network.SavingsGoalResponse

@Composable
fun AllocateToGoalsDialog(
    currentBalance: Int,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var goals by remember { mutableStateOf<List<SavingsGoalResponse>>(emptyList()) }
    var allocations by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loadingData by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loadingData = true
        error = null
        try {
            goals = withContext(Dispatchers.IO) {
                ApiClient.walletApi.getActiveSavingsGoals()
                    .filter { it.isActive && !it.isCompleted }
            }
        } catch (e: Exception) {
            error = ApiErrors.message(e, "Kunde inte ladda sparmål")
        } finally {
            loadingData = false
        }
    }

    val totalAllocated = allocations.values.sumOf { it.toIntOrNull() ?: 0 }
    val remaining = currentBalance - totalAllocated

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fördela till mål") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (loadingData) {
                    Text("Laddar sparmål…")
                    return@Column
                }
                if (error != null) {
                    Text(text = error!!, color = MaterialTheme.colorScheme.error)
                    return@Column
                }
                Text(
                    text = "Du har $currentBalance kr. Fördela till dina sparmål.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (goals.isEmpty()) {
                    Text(
                        text = "Inga aktiva sparmål. Skapa ett sparmål först.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Column
                }
                goals.forEach { goal ->
                    val label = "${goal.emoji?.let { "$it " } ?: ""}${goal.name} (max ${goal.remainingAmount} kr)"
                    OutlinedTextField(
                        value = allocations[goal.id] ?: "",
                        onValueChange = { v ->
                            allocations = allocations + (goal.id to v.filter { c -> c.isDigit() })
                            error = null
                        },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = "Summa: $totalAllocated kr. Kvar på kontot: $remaining kr.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    if (goals.isEmpty()) {
                        onDismiss()
                        return@TextButton
                    }
                    val list = allocations.mapNotNull { (goalId, value) ->
                        val amt = value.toIntOrNull() ?: 0
                        if (amt > 0) SavingsGoalAllocationRequest(savingsGoalId = goalId, amount = amt) else null
                    }
                    if (list.isEmpty()) {
                        error = "Fördela minst 1 kr"
                        return@TextButton
                    }
                    if (totalAllocated > currentBalance) {
                        error = "Du kan inte fördela mer än du har"
                        return@TextButton
                    }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                ApiClient.walletApi.allocateToGoals(AllocateToGoalsRequest(savingsGoalAllocations = list))
                            }
                            onSuccess()
                        } catch (e: Exception) {
                            error = ApiErrors.message(e, "Kunde inte fördela pengar")
                        } finally {
                            loading = false
                        }
                    }
                },
            ) {
                Text(if (loading) "Sparar…" else "Fördela")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Avbryt")
            }
        },
    )
}

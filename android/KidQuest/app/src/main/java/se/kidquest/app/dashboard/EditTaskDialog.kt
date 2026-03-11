package se.kidquest.app.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import se.kidquest.app.calendar.CalendarRepository
import se.kidquest.app.network.CalendarTaskWithCompletion
import kotlinx.coroutines.launch

@Composable
fun EditTaskDialog(
    task: CalendarTaskWithCompletion,
    onDismiss: () -> Unit,
    onUpdated: (newTitle: String, newXpMultiplier: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(task.event.title) }
    var xpMultiplier by remember { mutableStateOf(task.event.xpPoints?.coerceIn(1, 3) ?: 1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Redigera uppgift") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = { Text("Titel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "XP (mat)", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XpChip(label = "x1", selected = xpMultiplier == 1) { xpMultiplier = 1 }
                    XpChip(label = "x2", selected = xpMultiplier == 2) { xpMultiplier = 2 }
                    XpChip(label = "x3", selected = xpMultiplier == 3) { xpMultiplier = 3 }
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) {
                        error = "Titel krävs"
                        return@TextButton
                    }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            CalendarRepository.updateTaskTitleAndXp(
                                event = task.event,
                                newTitle = title,
                                xpMultiplier = xpMultiplier,
                            )
                            onUpdated(title, xpMultiplier)
                        } catch (e: Exception) {
                            error = e.message ?: "Kunde inte uppdatera uppgiften"
                        } finally {
                            loading = false
                        }
                    }
                },
            ) {
                Text(if (loading) "Sparar…" else "Spara")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Avbryt")
            }
        },
    )
}


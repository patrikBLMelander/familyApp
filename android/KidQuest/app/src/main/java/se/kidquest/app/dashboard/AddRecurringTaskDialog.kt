package se.kidquest.app.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddRecurringTaskDialog(
    childName: String,
    childId: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var xpMultiplier by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Java DayOfWeek: 1 = Monday ... 7 = Sunday
    var selectedWeekdays by remember { mutableStateOf(setOf<Int>()) }

    fun toggleWeekday(day: Int) {
        selectedWeekdays = if (selectedWeekdays.contains(day)) {
            selectedWeekdays - day
        } else {
            selectedWeekdays + day
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Återkommande uppgift – $childName") },
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
                Text(text = "Veckodagar", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    WeekdayChip("M", 1, selectedWeekdays.contains(1), ::toggleWeekday)
                    WeekdayChip("T", 2, selectedWeekdays.contains(2), ::toggleWeekday)
                    WeekdayChip("O", 3, selectedWeekdays.contains(3), ::toggleWeekday)
                    WeekdayChip("T", 4, selectedWeekdays.contains(4), ::toggleWeekday)
                    WeekdayChip("F", 5, selectedWeekdays.contains(5), ::toggleWeekday)
                    WeekdayChip("L", 6, selectedWeekdays.contains(6), ::toggleWeekday)
                    WeekdayChip("S", 7, selectedWeekdays.contains(7), ::toggleWeekday)
                }
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
                    if (selectedWeekdays.isEmpty()) {
                        error = "Välj minst en veckodag"
                        return@TextButton
                    }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            CalendarRepository.createRecurringWeeklyTasks(
                                memberId = childId,
                                title = title,
                                weekdays = selectedWeekdays,
                                xpMultiplier = xpMultiplier,
                            )
                            onSuccess()
                        } catch (e: Exception) {
                            error = e.message ?: "Kunde inte skapa uppgifterna"
                        } finally {
                            loading = false
                        }
                    }
                },
            ) {
                Text(if (loading) "Sparar…" else "Skapa uppgifter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Avbryt")
            }
        },
    )
}

@Composable
private fun WeekdayChip(
    label: String,
    day: Int,
    selected: Boolean,
    onToggle: (Int) -> Unit,
) {
    val colors = if (selected) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    Button(
        onClick = { onToggle(day) },
        modifier = Modifier.height(32.dp),
        colors = colors,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}


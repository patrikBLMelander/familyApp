package se.kidquest.app.dashboard

import se.kidquest.app.i18n.tr
import se.kidquest.app.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import kotlinx.coroutines.launch
import se.kidquest.app.chore.DailyChoreRepository
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.DailyChoreResponse

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddRecurringTaskDialog(
    childName: String,
    childId: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    /** Icke-null öppnar dialogen i redigeringsläge: fälten förifylls och Spara uppdaterar. */
    existing: DailyChoreResponse? = null,
) {
    val editing = existing != null
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var xpMultiplier by remember { mutableStateOf(existing?.xpPoints?.coerceIn(1, 3) ?: 1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Java DayOfWeek: 1 = Monday ... 7 = Sunday
    var selectedWeekdays by remember {
        mutableStateOf(existing?.weekdays?.mapNotNull { DailyChoreRepository.weekdayIndex(it) }?.toSet() ?: setOf())
    }

    fun toggleWeekday(day: Int) {
        selectedWeekdays = if (selectedWeekdays.contains(day)) {
            selectedWeekdays - day
        } else {
            selectedWeekdays + day
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) tr(R.string.recurring_edit_title, childName) else tr(R.string.recurring_new_title, childName)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = { Text(tr(R.string.common_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = tr(R.string.recurring_weekdays), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    WeekdayChip(tr(R.string.weekday_initial_1), 1, selectedWeekdays.contains(1), ::toggleWeekday)
                    WeekdayChip(tr(R.string.weekday_initial_2), 2, selectedWeekdays.contains(2), ::toggleWeekday)
                    WeekdayChip(tr(R.string.weekday_initial_3), 3, selectedWeekdays.contains(3), ::toggleWeekday)
                    WeekdayChip(tr(R.string.weekday_initial_4), 4, selectedWeekdays.contains(4), ::toggleWeekday)
                    WeekdayChip(tr(R.string.weekday_initial_5), 5, selectedWeekdays.contains(5), ::toggleWeekday)
                    WeekdayChip(tr(R.string.weekday_initial_6), 6, selectedWeekdays.contains(6), ::toggleWeekday)
                    WeekdayChip(tr(R.string.weekday_initial_7), 7, selectedWeekdays.contains(7), ::toggleWeekday)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = tr(R.string.common_xp_food), style = MaterialTheme.typography.bodyMedium)
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
                        error = tr(R.string.common_title_required)
                        return@TextButton
                    }
                    if (selectedWeekdays.isEmpty()) {
                        error = tr(R.string.recurring_pick_weekday)
                        return@TextButton
                    }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            if (editing) {
                                DailyChoreRepository.updateChore(
                                    choreId = existing!!.id,
                                    title = title,
                                    weekdays = selectedWeekdays,
                                    xpPoints = xpMultiplier,
                                )
                            } else {
                                DailyChoreRepository.createChore(
                                    memberId = childId,
                                    title = title,
                                    weekdays = selectedWeekdays,
                                    xpPoints = xpMultiplier,
                                )
                            }
                            onSuccess()
                        } catch (e: Exception) {
                            error = ApiErrors.message(
                                e,
                                if (editing) tr(R.string.recurring_save_failed) else tr(R.string.recurring_create_failed),
                            )
                        } finally {
                            loading = false
                        }
                    }
                },
            ) {
                Text(
                    when {
                        loading -> tr(R.string.common_saving)
                        editing -> tr(R.string.recurring_save_changes)
                        else -> tr(R.string.recurring_create)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(R.string.common_cancel))
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


package se.kidquest.app.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import se.kidquest.app.calendar.CalendarRepository
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.CreateFamilyMemberRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AddFamilyMemberDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedAgeRange by remember { mutableStateOf<AgeRange?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lägg till barn") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Namn") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Ålder (valfritt – för färdiga uppgifter)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AgeRangeChip(
                        label = "4–6 år",
                        selected = selectedAgeRange == AgeRange.FOUR_TO_SIX,
                        onClick = {
                            selectedAgeRange = if (selectedAgeRange == AgeRange.FOUR_TO_SIX) null else AgeRange.FOUR_TO_SIX
                        },
                    )
                    AgeRangeChip(
                        label = "7–9 år",
                        selected = selectedAgeRange == AgeRange.SEVEN_TO_NINE,
                        onClick = {
                            selectedAgeRange = if (selectedAgeRange == AgeRange.SEVEN_TO_NINE) null else AgeRange.SEVEN_TO_NINE
                        },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AgeRangeChip(
                        label = "10–12 år",
                        selected = selectedAgeRange == AgeRange.TEN_TO_TWELVE,
                        onClick = {
                            selectedAgeRange = if (selectedAgeRange == AgeRange.TEN_TO_TWELVE) null else AgeRange.TEN_TO_TWELVE
                        },
                    )
                    AgeRangeChip(
                        label = "13+ år",
                        selected = selectedAgeRange == AgeRange.THIRTEEN_PLUS,
                        onClick = {
                            selectedAgeRange = if (selectedAgeRange == AgeRange.THIRTEEN_PLUS) null else AgeRange.THIRTEEN_PLUS
                        },
                    )
                }
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
                    if (name.isBlank()) return@TextButton
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            val member = withContext(Dispatchers.IO) {
                                ApiClient.familyMembersApi.createMember(
                                    CreateFamilyMemberRequest(name = name.trim(), role = "CHILD"),
                                )
                            }
                            selectedAgeRange?.let { range ->
                                createDefaultTasksForAgeRange(
                                    memberId = member.id,
                                    ageRange = range,
                                )
                            }
                            onSuccess()
                        } catch (e: Exception) {
                            error = e.message ?: "Kunde inte lägga till"
                        } finally {
                            loading = false
                        }
                    }
                },
            ) {
                Text(if (loading) "Lägger till…" else "Lägg till")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Avbryt")
            }
        },
    )
}

private enum class AgeRange {
    FOUR_TO_SIX,
    SEVEN_TO_NINE,
    TEN_TO_TWELVE,
    THIRTEEN_PLUS,
}

private suspend fun createDefaultTasksForAgeRange(
    memberId: String,
    ageRange: AgeRange,
) {
    // Java DayOfWeek: 1 = Monday ... 7 = Sunday
    val allWeekdays = setOf(1, 2, 3, 4, 5, 6, 7)
    when (ageRange) {
        AgeRange.FOUR_TO_SIX -> {
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Klä på mig",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Borsta tänder",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Plocka leksaker i mitt rum",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Ställ undan min disk",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Hänga upp jacka & skor",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
        }
        AgeRange.SEVEN_TO_NINE -> {
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Packa skolväskan",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Bädda sängen",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Plocka undan efter mellis",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Kvällsrutin utan tjat",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Hjälpa till med disk/dukning",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
        }
        AgeRange.TEN_TO_TWELVE -> {
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Läx-/pluggstund",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Skräpkoll hemma",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Ordning på rummet",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Hjälpa till med maten",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Skärm efter uppgifter",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
        }
        AgeRange.THIRTEEN_PLUS -> {
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Hålla rummet i ordning",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Ta hand om min tvätt",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Min dagliga hemmasyssla",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Kolla dagens schema & tider",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
            CalendarRepository.createRecurringWeeklyTasks(
                memberId = memberId,
                title = "Kolla ekonomi & sparmål",
                weekdays = allWeekdays,
                xpMultiplier = 1,
            )
        }
    }
}

@Composable
private fun AgeRangeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
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
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        colors = colors,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}


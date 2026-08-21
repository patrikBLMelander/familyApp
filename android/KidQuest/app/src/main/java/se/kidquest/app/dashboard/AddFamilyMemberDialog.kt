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
import androidx.compose.material3.FilterChip
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
import se.kidquest.app.chore.DailyChoreRepository
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.CreateFamilyMemberRequest

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
    // Only CHILD and PARENT are offered. ASSISTANT ("Äldre barn") still exists in
    // the backend for members created before, but is no longer handed out.
    var isParent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lägg till familjemedlem") },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !isParent,
                        onClick = { isParent = false },
                        label = { Text("Barn") },
                    )
                    FilterChip(
                        selected = isParent,
                        onClick = { isParent = true },
                        label = { Text("Förälder") },
                    )
                }
                if (isParent) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Föräldern kopplar sin telefon med QR-koden på deras kort. " +
                            "E-post och lösenord kan sättas i webbappen om de behöver " +
                            "logga in på en ny enhet.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                // Age only drives suggested chores, which a parent has no use for.
                if (!isParent) {
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
                                    CreateFamilyMemberRequest(
                                        name = name.trim(),
                                        role = if (isParent) "PARENT" else "CHILD",
                                    ),
                                )
                            }
                            selectedAgeRange?.takeIf { !isParent }?.let { range ->
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
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Klä på mig",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Borsta tänder",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Plocka leksaker i mitt rum",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Ställ undan min disk",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Hänga upp jacka & skor",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
        }
        AgeRange.SEVEN_TO_NINE -> {
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Packa skolväskan",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Bädda sängen",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Plocka undan efter mellis",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Kvällsrutin utan tjat",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Hjälpa till med disk/dukning",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
        }
        AgeRange.TEN_TO_TWELVE -> {
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Läx-/pluggstund",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Skräpkoll hemma",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Ordning på rummet",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Hjälpa till med maten",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Skärm efter uppgifter",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
        }
        AgeRange.THIRTEEN_PLUS -> {
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Hålla rummet i ordning",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Ta hand om min tvätt",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Min dagliga hemmasyssla",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Kolla dagens schema & tider",
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = "Kolla ekonomi & sparmål",
                weekdays = allWeekdays,
                xpPoints = 1,
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


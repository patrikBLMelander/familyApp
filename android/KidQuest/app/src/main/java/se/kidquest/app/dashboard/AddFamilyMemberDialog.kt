package se.kidquest.app.dashboard

import se.kidquest.app.i18n.tr
import se.kidquest.app.R
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
import se.kidquest.app.network.ApiErrors
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
        title = { Text(tr(R.string.member_add_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(tr(R.string.member_name)) },
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
                        label = { Text(tr(R.string.member_child)) },
                    )
                    FilterChip(
                        selected = isParent,
                        onClick = { isParent = true },
                        label = { Text(tr(R.string.member_parent)) },
                    )
                }
                if (isParent) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = tr(R.string.member_parent_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                // Age only drives suggested chores, which a parent has no use for.
                if (!isParent) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = tr(R.string.member_age),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AgeRangeChip(
                        label = tr(R.string.member_age_4_6),
                        selected = selectedAgeRange == AgeRange.FOUR_TO_SIX,
                        onClick = {
                            selectedAgeRange = if (selectedAgeRange == AgeRange.FOUR_TO_SIX) null else AgeRange.FOUR_TO_SIX
                        },
                    )
                    AgeRangeChip(
                        label = tr(R.string.member_age_7_9),
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
                        label = tr(R.string.member_age_10_12),
                        selected = selectedAgeRange == AgeRange.TEN_TO_TWELVE,
                        onClick = {
                            selectedAgeRange = if (selectedAgeRange == AgeRange.TEN_TO_TWELVE) null else AgeRange.TEN_TO_TWELVE
                        },
                    )
                    AgeRangeChip(
                        label = tr(R.string.member_age_13),
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
                            error = ApiErrors.message(e, tr(R.string.member_add_failed))
                        } finally {
                            loading = false
                        }
                    }
                },
            ) {
                Text(if (loading) tr(R.string.member_adding) else tr(R.string.member_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(R.string.common_cancel))
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
                title = tr(R.string.preset_chore_1),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_2),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_3),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_4),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_5),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
        }
        AgeRange.SEVEN_TO_NINE -> {
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_6),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_7),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_8),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_9),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_10),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
        }
        AgeRange.TEN_TO_TWELVE -> {
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_11),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_12),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_13),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_14),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_15),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
        }
        AgeRange.THIRTEEN_PLUS -> {
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_16),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_17),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_18),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_19),
                weekdays = allWeekdays,
                xpPoints = 1,
            )
            DailyChoreRepository.createChore(
                memberId = memberId,
                title = tr(R.string.preset_chore_20),
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


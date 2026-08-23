package se.kidquest.app.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch
import se.kidquest.app.chore.DailyChoreRepository
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.DailyChoreWithCompletionResponse

private val CHILD_WEEKDAY_ABBREVS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private val CHILD_WEEKDAY_LABELS_SV = listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön")

private fun LocalDate.toChildWeekdayAbbrev(): String = CHILD_WEEKDAY_ABBREVS[dayOfWeek.value - 1]

private fun childCurrentWeekDays(): List<LocalDate> {
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return (0..6).map { monday.plusDays(it.toLong()) }
}

@Composable
fun ChildTasksScreen(
    childName: String,
    childId: String,
    onBack: () -> Unit,
) {
    var tasks by remember { mutableStateOf<List<DailyChoreWithCompletionResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var toggleError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var activeTab by remember { mutableStateOf("today") }
    var showAddChoreDialog by remember { mutableStateOf(false) }
    // The chore awaiting delete confirmation, so a mistyped chore can be removed
    // without a stray tap wiping one that was fine.
    var chorePendingDelete by remember { mutableStateOf<DailyChoreWithCompletionResponse?>(null) }
    var showAddSingleDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(childId, refreshKey) {
        loading = true
        error = null
        try {
            tasks = DailyChoreRepository.fetchChoresForToday(childId)
        } catch (e: Exception) {
            error = ApiErrors.message(e, "Kunde inte ladda uppgifter")
        } finally {
            loading = false
        }
    }

    val today = LocalDate.now()
    val dayLabelFull = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("sv"))
    val dateLabel = "$dayLabelFull ${today.dayOfMonth}/${today.monthValue}"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFFE0E7FF), Color(0xFFE0F2FE))),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Tillbaka",
                        tint = Color(0xFF1E3A5F),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$childName – Sysslor",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1917),
                    )
                    Text(
                        text = dateLabel,
                        fontSize = 13.sp,
                        color = Color(0xFF666666),
                    )
                }
            }

            // ── Tabs ─────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChildTabButton(
                    label = "📝 Idag",
                    selected = activeTab == "today",
                    modifier = Modifier.weight(1f),
                    onClick = { activeTab = "today" },
                )
                ChildTabButton(
                    label = "📅 Vecka",
                    selected = activeTab == "week",
                    modifier = Modifier.weight(1f),
                    onClick = { activeTab = "week" },
                )
            }

            // ── Content ───────────────────────────────────────────────────────
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color(0xFF0C4A6E))
                }

                error != null -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }

                activeTab == "today" -> {
                    if (toggleError != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEDED)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = toggleError!!,
                                    fontSize = 13.sp,
                                    color = Color(0xFFC53030),
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "✕",
                                    fontSize = 14.sp,
                                    color = Color(0xFFC53030),
                                    modifier = Modifier
                                        .clickable { toggleError = null }
                                        .padding(start = 8.dp),
                                )
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (tasks.isEmpty()) {
                            item {
                                ChildSurfaceCard {
                                    Text(
                                        "Inga uppgifter idag.",
                                        fontSize = 14.sp,
                                        color = Color(0xFF666666),
                                        modifier = Modifier.padding(4.dp),
                                    )
                                }
                            }
                        } else {
                            items(tasks, key = { it.chore.id }) { task ->
                                ChildSurfaceCard {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch {
                                                    val choreId = task.chore.id
                                                    val wasCompleted = task.completed
                                                    tasks = tasks.map {
                                                        if (it.chore.id == choreId) it.copy(completed = !wasCompleted) else it
                                                    }
                                                    try {
                                                        DailyChoreRepository.toggleChoreCompletion(
                                                            choreId = choreId,
                                                            isCurrentlyCompleted = wasCompleted,
                                                        )
                                                    } catch (e: Exception) {
                                                        tasks = tasks.map {
                                                            if (it.chore.id == choreId) it.copy(completed = wasCompleted) else it
                                                        }
                                                        if (wasCompleted) {
                                                            toggleError = "Kan inte avmarkera – all mat har redan matats till husdjuret."
                                                        }
                                                    }
                                                }
                                            }
                                            .padding(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        // Circular checkbox
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(if (task.completed) Color(0xFF4ADE80) else Color.Transparent)
                                                .border(
                                                    width = 2.dp,
                                                    color = if (task.completed) Color(0xFF4ADE80) else Color(0xFFCCCCCC),
                                                    shape = CircleShape,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (task.completed) {
                                                Text(
                                                    "✓",
                                                    fontSize = 12.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.chore.title,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (task.completed) Color(0xFF888888) else Color(0xFF1A1A1A),
                                                textDecoration = if (task.completed) TextDecoration.LineThrough
                                                else TextDecoration.None,
                                            )
                                            if (task.chore.xpPoints > 0) {
                                                Text(
                                                    "${task.chore.xpPoints} XP",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF0C4A6E),
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { chorePendingDelete = task },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Ta bort ${task.chore.title}",
                                                tint = Color(0xFF9CA3AF),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Add buttons at bottom
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { showAddSingleDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("+ Idag")
                        }
                        Button(
                            onClick = { showAddChoreDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("🔁 Återkommande")
                        }
                    }
                }

                else -> {
                    // Week view
                    val weekDays = childCurrentWeekDays()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(weekDays, key = { it.toEpochDay() }) { day ->
                            ChildWeekDayCard(day = day, allChores = tasks)
                        }
                    }
                }
            }
        }
    }

    chorePendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { chorePendingDelete = null },
            title = { Text("Ta bort sysslan?") },
            text = {
                Text(
                    "\"${pending.chore.title}\" tas bort för $childName, tillsammans med " +
                        "historiken över när den blivit gjord. Det går inte att ångra.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val choreId = pending.chore.id
                        val previous = tasks
                        // Optimistic: the row disappears at once and comes back if the
                        // call fails, which is how the web version behaves.
                        tasks = tasks.filterNot { it.chore.id == choreId }
                        chorePendingDelete = null
                        scope.launch {
                            try {
                                DailyChoreRepository.deleteChore(choreId)
                                refreshKey++
                            } catch (e: Exception) {
                                tasks = previous
                                toggleError = ApiErrors.message(e, "Kunde inte ta bort sysslan.")
                            }
                        }
                    },
                ) {
                    Text("Ta bort", color = Color(0xFFC53030))
                }
            },
            dismissButton = {
                TextButton(onClick = { chorePendingDelete = null }) { Text("Avbryt") }
            },
        )
    }

    if (showAddChoreDialog) {
        AddRecurringTaskDialog(
            childName = childName,
            childId = childId,
            onDismiss = { showAddChoreDialog = false },
            onSuccess = {
                showAddChoreDialog = false
                refreshKey++
            },
        )
    }

    if (showAddSingleDialog) {
        AddSingleTaskDialog(
            childName = childName,
            childId = childId,
            onDismiss = { showAddSingleDialog = false },
            onSuccess = {
                showAddSingleDialog = false
                refreshKey++
            },
        )
    }
}

// ── Small reusable composables ───────────────────────────────────────────────

@Composable
private fun ChildTabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF0C4A6E) else Color(0xFFBAE6FD),
            contentColor = if (selected) Color.White else Color(0xFF0C4A6E),
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChildSurfaceCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

// ── Week view ────────────────────────────────────────────────────────────────

@Composable
private fun ChildWeekDayCard(
    day: LocalDate,
    allChores: List<DailyChoreWithCompletionResponse>,
) {
    val today = LocalDate.now()
    val isToday = day == today
    val dayIndex = day.dayOfWeek.value - 1 // 0=Mon…6=Sun
    val abbrev = CHILD_WEEKDAY_ABBREVS[dayIndex]
    val dayLabelSv = CHILD_WEEKDAY_LABELS_SV[dayIndex]
    val dateStr = "${day.dayOfMonth}/${day.monthValue}"

    val scheduledChores = allChores.filter { abbrev in it.chore.weekdays }
    val totalChores = scheduledChores.size
    val doneChores = if (isToday) scheduledChores.count { it.completed } else 0
    val allDoneToday = isToday && totalChores > 0 && doneChores == totalChores

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) Color(0xFFEFF6FF) else Color.White.copy(alpha = 0.88f),
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isToday) 4.dp else 1.dp,
        ),
        border = if (isToday) BorderStroke(2.dp, Color(0xFF0C4A6E)) else null,
    ) {
        Column {
            // Day header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isToday) Color(0xFFDBEAFE) else Color(0xFFF7FAFC))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "$dayLabelSv $dateStr",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) Color(0xFF0C4A6E) else Color(0xFF2D3748),
                    )
                    if (isToday) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0C4A6E))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "idag",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Text(
                    text = when {
                        totalChores == 0 -> "–"
                        isToday -> "$doneChores/$totalChores"
                        else -> "$totalChores sysslor"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (allDoneToday) Color(0xFF16A34A) else Color(0xFF718096),
                )
            }

            // Chore list
            if (scheduledChores.isEmpty()) {
                Text(
                    "Inga sysslor",
                    fontSize = 13.sp,
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    scheduledChores.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (isToday) (if (item.completed) "✅" else "⭕") else "·",
                                fontSize = if (isToday) 14.sp else 18.sp,
                                color = if (!isToday) Color(0xFF9CA3AF) else Color.Unspecified,
                                lineHeight = 18.sp,
                            )
                            Text(
                                text = item.chore.title,
                                fontSize = 14.sp,
                                color = if (isToday && item.completed) Color(0xFF9CA3AF)
                                else Color(0xFF374151),
                                textDecoration = if (isToday && item.completed)
                                    TextDecoration.LineThrough
                                else TextDecoration.None,
                                modifier = Modifier.weight(1f),
                            )
                            if (item.chore.xpPoints > 0) {
                                Text(
                                    "${item.chore.xpPoints} XP",
                                    fontSize = 11.sp,
                                    color = Color(0xFFAAAAAA),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

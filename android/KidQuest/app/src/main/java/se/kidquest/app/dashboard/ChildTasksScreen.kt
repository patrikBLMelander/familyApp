package se.kidquest.app.dashboard

import se.kidquest.app.i18n.trp
import se.kidquest.app.i18n.Dates
import se.kidquest.app.i18n.tr
import se.kidquest.app.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import se.kidquest.app.network.DailyChoreResponse
import se.kidquest.app.network.DailyChoreWithCompletionResponse
import se.kidquest.app.theme.LocalSeasonPalette
import se.kidquest.app.theme.SeasonHeaderBar
import se.kidquest.app.session.TokenStore

private val CHILD_WEEKDAY_ABBREVS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

private fun LocalDate.toChildWeekdayAbbrev(): String = CHILD_WEEKDAY_ABBREVS[dayOfWeek.value - 1]

private fun childCurrentWeekDays(): List<LocalDate> {
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return (0..6).map { monday.plusDays(it.toLong()) }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChildTasksScreen(
    childName: String,
    childId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSeasonPalette.current
    // Read from the session rather than from the route: a child's login can never
    // administer chores, whichever way they arrived here. The server refuses them
    // too -- this is so they are not offered a button that answers with an error.
    val isChildSession = TokenStore.getSession()?.isChild == true
    var tasks by remember { mutableStateOf<List<DailyChoreWithCompletionResponse>>(emptyList()) }
    // The week is built from every chore, not today's: a chore that does not fall today
    // would otherwise be missing from all seven days -- and impossible to edit there.
    var allChores by remember { mutableStateOf<List<DailyChoreResponse>>(emptyList()) }
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
    // Långtryck på en syssla öppnar en meny (Redigera/Ta bort). Den som redigeras
    // förifyller redigeringsdialogen.
    var menuForChore by remember { mutableStateOf<DailyChoreWithCompletionResponse?>(null) }
    var choreToEdit by remember { mutableStateOf<DailyChoreWithCompletionResponse?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(childId, refreshKey) {
        loading = true
        error = null
        try {
            tasks = DailyChoreRepository.fetchChoresForToday(childId)
            allChores = DailyChoreRepository.fetchAllChores(childId)
        } catch (e: Exception) {
            error = ApiErrors.message(e, tr(R.string.tasks_load_failed))
        } finally {
            loading = false
        }
    }

    val today = LocalDate.now()
    val dayLabelFull = Dates.weekdayFull(today.dayOfWeek.value)
    val dateLabel = "$dayLabelFull ${today.dayOfMonth}/${today.monthValue}"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.pageBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ──────────────────────────────────────────────────────
            SeasonHeaderBar(
                title = tr(R.string.child_tasks_title, childName),
                subtitle = dateLabel,
                onBack = onBack,
            )

            // ── Tabs ─────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChildTabButton(
                    label = tr(R.string.tasks_tab_today),
                    selected = activeTab == "today",
                    modifier = Modifier.weight(1f),
                    onClick = { activeTab = "today" },
                )
                ChildTabButton(
                    label = tr(R.string.tasks_tab_week),
                    selected = activeTab == "week",
                    modifier = Modifier.weight(1f),
                    onClick = { activeTab = "week" },
                )
            }

            // Above both tabs: a delete from the week view can fail too, and the
            // restored row alone would not say why.
            if (toggleError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.warnBg),
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
                            color = palette.danger,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "✕",
                            fontSize = 14.sp,
                            color = palette.danger,
                            modifier = Modifier
                                .clickable { toggleError = null }
                                .padding(start = 8.dp),
                        )
                    }
                }
            }

            // ── Content ───────────────────────────────────────────────────────
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = palette.accent)
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
                                        tr(R.string.tasks_none_today_dot),
                                        fontSize = 14.sp,
                                        color = palette.inkSoft,
                                        modifier = Modifier.padding(4.dp),
                                    )
                                }
                            }
                        } else {
                            items(tasks, key = { it.chore.id }) { task ->
                              Box {
                                ChildSurfaceCard {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
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
                                                                toggleError = tr(R.string.chore_untick_no_food)
                                                            }
                                                        }
                                                    }
                                                },
                                                // Långtryck = förälderns meny (Redigera/Ta bort). Barn får ingen.
                                                onLongClick = if (!isChildSession) ({ menuForChore = task }) else null,
                                            )
                                            .padding(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        // Circular checkbox
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(if (task.completed) palette.goodInk else Color.Transparent)
                                                .border(
                                                    width = 2.dp,
                                                    color = if (task.completed) palette.goodInk else palette.track,
                                                    shape = CircleShape,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (task.completed) {
                                                Text(
                                                    "✓",
                                                    fontSize = 12.sp,
                                                    color = palette.pageBg,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.chore.title,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (task.completed) palette.inkFaint else palette.ink,
                                                textDecoration = if (task.completed) TextDecoration.LineThrough
                                                else TextDecoration.None,
                                            )
                                            if (task.chore.xpPoints > 0) {
                                                Text(
                                                    "${task.chore.xpPoints} XP",
                                                    fontSize = 12.sp,
                                                    color = palette.accent,
                                                )
                                            }
                                        }
                                    }
                                }
                                DropdownMenu(
                                    expanded = menuForChore?.chore?.id == task.chore.id,
                                    onDismissRequest = { menuForChore = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(tr(R.string.common_edit)) },
                                        onClick = {
                                            choreToEdit = task
                                            menuForChore = null
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(tr(R.string.common_delete), color = palette.danger) },
                                        onClick = {
                                            chorePendingDelete = task
                                            menuForChore = null
                                        },
                                    )
                                }
                              }
                            }
                        }
                    }

                    // Adding a chore is parent administration. A child reaches this
                    // screen from their own dashboard, and could invent chores they
                    // had already done.
                    if (!isChildSession) Row(
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
                            Text(tr(R.string.chore_add_today))
                        }
                        Button(
                            onClick = { showAddChoreDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(tr(R.string.chore_add_recurring))
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
                            ChildWeekDayCard(
                                day = day,
                                allChores = allChores,
                                todaysTasks = tasks,
                                // Same menu as today's list; a chore can show on several
                                // days, so the menu belongs to the chore on this day.
                                canEdit = !isChildSession,
                                onEdit = { choreToEdit = it.withoutCompletion() },
                                onDelete = { chorePendingDelete = it.withoutCompletion() },
                            )
                        }
                    }
                }
            }
        }
    }

    chorePendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { chorePendingDelete = null },
            title = { Text(tr(R.string.chore_delete_title)) },
            text = {
                Text(
                    tr(R.string.chore_delete_body, pending.chore.title, childName),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val choreId = pending.chore.id
                        val previous = tasks
                        // Optimistic: the row disappears at once and comes back if the
                        // call fails, which is how the web version behaves.
                        val previousAll = allChores
                        tasks = tasks.filterNot { it.chore.id == choreId }
                        allChores = allChores.filterNot { it.id == choreId }
                        chorePendingDelete = null
                        scope.launch {
                            try {
                                DailyChoreRepository.deleteChore(choreId)
                                refreshKey++
                            } catch (e: Exception) {
                                tasks = previous
                                allChores = previousAll
                                toggleError = ApiErrors.message(e, tr(R.string.chore_delete_failed))
                            }
                        }
                    },
                ) {
                    Text(tr(R.string.common_delete), color = palette.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { chorePendingDelete = null }) { Text(tr(R.string.common_cancel)) }
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

    choreToEdit?.let { editing ->
        AddRecurringTaskDialog(
            childName = childName,
            childId = childId,
            existing = editing.chore,
            onDismiss = { choreToEdit = null },
            onSuccess = {
                choreToEdit = null
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
    val palette = LocalSeasonPalette.current
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            // Two different roles that a single accent cannot fill: the chosen tab is
            // the accent itself, the other is the accent's tinted pair. Mapping both to
            // the accent made the unselected label invisible against its own background.
            containerColor = if (selected) palette.accent else palette.calBg,
            contentColor = if (selected) palette.onAccent else palette.calInk,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChildSurfaceCard(content: @Composable () -> Unit) {
    val palette = LocalSeasonPalette.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

// ── Week view ────────────────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChildWeekDayCard(
    day: LocalDate,
    allChores: List<DailyChoreResponse>,
    todaysTasks: List<DailyChoreWithCompletionResponse>,
    canEdit: Boolean,
    onEdit: (DailyChoreResponse) -> Unit,
    onDelete: (DailyChoreResponse) -> Unit,
) {
    val palette = LocalSeasonPalette.current
    val today = LocalDate.now()
    val isToday = day == today
    val dayIndex = day.dayOfWeek.value - 1 // 0=Mon…6=Sun
    val abbrev = CHILD_WEEKDAY_ABBREVS[dayIndex]
    val dayLabelSv = Dates.weekdayShort(dayIndex + 1)
    val dateStr = "${day.dayOfMonth}/${day.monthValue}"

    // Completion only exists for today; the other days show what is scheduled.
    val completedToday = todaysTasks.filter { it.completed }.map { it.chore.id }.toSet()
    val scheduledChores = allChores
        .filter { abbrev in it.weekdays }
        .map { DailyChoreWithCompletionResponse(it, completed = isToday && it.id in completedToday, completionId = null) }
    val totalChores = scheduledChores.size
    val doneChores = if (isToday) scheduledChores.count { it.completed } else 0
    var menuForChoreId by remember { mutableStateOf<String?>(null) }
    val allDoneToday = isToday && totalChores > 0 && doneChores == totalChores

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) palette.calBg else palette.surface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isToday) 4.dp else 1.dp,
        ),
        border = if (isToday) BorderStroke(2.dp, palette.accent) else null,
    ) {
        Column {
            // Day header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isToday) palette.calBg else palette.tipBg)
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
                        color = if (isToday) palette.accent else palette.ink,
                    )
                    if (isToday) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.accent)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                tr(R.string.tasks_today_badge),
                                fontSize = 11.sp,
                                color = palette.onAccent,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Text(
                    text = when {
                        totalChores == 0 -> "–"
                        isToday -> "$doneChores/$totalChores"
                        else -> trp(R.plurals.tasks_count, totalChores)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (allDoneToday) palette.goodInk else palette.inkSoft,
                )
            }

            // Chore list
            if (scheduledChores.isEmpty()) {
                Text(
                    tr(R.string.tasks_no_chores),
                    fontSize = 13.sp,
                    color = palette.inkFaint,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    scheduledChores.forEach { item ->
                      Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {},
                                    // Långtryck = förälderns meny, som i dagens lista. Barn får ingen.
                                    onLongClick = if (canEdit) ({ menuForChoreId = item.chore.id }) else null,
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (isToday) (if (item.completed) "✅" else "⭕") else "·",
                                fontSize = if (isToday) 14.sp else 18.sp,
                                color = if (!isToday) palette.inkFaint else Color.Unspecified,
                                lineHeight = 18.sp,
                            )
                            Text(
                                text = item.chore.title,
                                fontSize = 14.sp,
                                color = if (isToday && item.completed) palette.inkFaint
                                else palette.inkSoft,
                                textDecoration = if (isToday && item.completed)
                                    TextDecoration.LineThrough
                                else TextDecoration.None,
                                modifier = Modifier.weight(1f),
                            )
                            if (item.chore.xpPoints > 0) {
                                Text(
                                    "${item.chore.xpPoints} XP",
                                    fontSize = 11.sp,
                                    color = palette.inkFaint,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = menuForChoreId == item.chore.id,
                            onDismissRequest = { menuForChoreId = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text(tr(R.string.common_edit)) },
                                onClick = {
                                    menuForChoreId = null
                                    onEdit(item.chore)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(tr(R.string.common_delete), color = palette.danger) },
                                onClick = {
                                    menuForChoreId = null
                                    onDelete(item.chore)
                                },
                            )
                        }
                      }
                    }
                }
            }
        }
    }
}

/** The week lists chores without today's completion; the edit and delete dialogs take the wrapped shape. */
private fun DailyChoreResponse.withoutCompletion() =
    DailyChoreWithCompletionResponse(chore = this, completed = false, completionId = null)

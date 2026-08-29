package se.kidquest.app.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import se.kidquest.app.chore.DailyChoreRepository
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.DailyChoreWithCompletionResponse
import se.kidquest.app.network.FamilyMemberResponse
import se.kidquest.app.theme.LocalSeasonPalette
import se.kidquest.app.theme.SeasonHeaderBar

private data class MemberWithChores(
    val member: FamilyMemberResponse,
    val chores: List<DailyChoreWithCompletionResponse>,
)

private val WEEKDAY_ABBREVS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private val WEEKDAY_LABELS_SV = listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön")

private fun LocalDate.toWeekdayAbbrev(): String = WEEKDAY_ABBREVS[dayOfWeek.value - 1]

private fun currentWeekDays(): List<LocalDate> {
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return (0..6).map { monday.plusDays(it.toLong()) }
}

@Composable
fun FamilyTasksScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalSeasonPalette.current
    var data by remember { mutableStateOf<List<MemberWithChores>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf("today") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            val members = ApiClient.familyMembersApi.getAllMembers()
                .filter { it.role == "CHILD" || it.role == "ASSISTANT" }
            data = coroutineScope {
                members.map { member ->
                    async {
                        val chores = runCatching {
                            DailyChoreRepository.fetchChoresForToday(member.id)
                        }.getOrElse { emptyList() }
                        MemberWithChores(member, chores)
                    }
                }.map { it.await() }
            }
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
        modifier = modifier
            .fillMaxSize()
            .background(palette.pageBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ──────────────────────────────────────────────────────
            SeasonHeaderBar(
                title = "Familjens uppgifter",
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
                TabButton(
                    label = "📝 Idag",
                    selected = activeTab == "today",
                    modifier = Modifier.weight(1f),
                    onClick = { activeTab = "today" },
                )
                TabButton(
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
                    CircularProgressIndicator(color = palette.accent)
                }

                error != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (activeTab == "today") {
                        if (data.isEmpty()) {
                            item {
                                SurfaceCard {
                                    Text(
                                        "Inga familjemedlemmar hittades.",
                                        fontSize = 14.sp,
                                        color = palette.inkSoft,
                                        modifier = Modifier.padding(4.dp),
                                    )
                                }
                            }
                        } else {
                            items(data, key = { it.member.id }) { row ->
                                TodayMemberCard(
                                    row = row,
                                    onToggle = { choreId, isCompleted ->
                                        scope.launch {
                                            data = data.map { r ->
                                                if (r.member.id != row.member.id) r
                                                else r.copy(chores = r.chores.map { c ->
                                                    if (c.chore.id == choreId) c.copy(completed = !isCompleted) else c
                                                })
                                            }
                                            try {
                                                DailyChoreRepository.toggleChoreCompletion(
                                                    choreId = choreId,
                                                    isCurrentlyCompleted = isCompleted,
                                                )
                                            } catch (_: Exception) {
                                                // revert on error
                                                data = data.map { r ->
                                                    if (r.member.id != row.member.id) r
                                                    else r.copy(chores = r.chores.map { c ->
                                                        if (c.chore.id == choreId) c.copy(completed = isCompleted) else c
                                                    })
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    } else {
                        // Week view — one card per day derived from chore weekdays
                        val weekDays = currentWeekDays()
                        items(weekDays, key = { it.toEpochDay() }) { day ->
                            WeekDayCard(day = day, members = data)
                        }
                    }
                }
            }
        }
    }
}

// ── Small reusable composables ──────────────────────────────────────────────

@Composable
private fun TabButton(
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
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalSeasonPalette.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

// ── Today view ──────────────────────────────────────────────────────────────

@Composable
private fun TodayMemberCard(
    row: MemberWithChores,
    onToggle: (choreId: String, isCompleted: Boolean) -> Unit,
) {
    val palette = LocalSeasonPalette.current
    val done = row.chores.count { it.completed }
    val total = row.chores.size
    val allDone = total > 0 && done == total

    SurfaceCard {
        // Member name + progress badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = row.member.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = palette.ink,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (allDone) palette.goodBg else palette.tipBg)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    text = when {
                        total == 0 -> "Inga uppgifter idag"
                        allDone -> "✓ Allt klart ($total)"
                        else -> "$done / $total gjorda"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (allDone) palette.goodInk else palette.inkSoft,
                )
            }
        }

        if (row.chores.isEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Inga uppgifter idag",
                fontSize = 13.sp,
                color = palette.inkFaint,
            )
        } else {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = palette.cardEdge)
            Spacer(modifier = Modifier.height(4.dp))

            row.chores.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(item.chore.id, item.completed) }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Circular checkbox
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (item.completed) palette.goodInk else Color.Transparent)
                            .border(
                                width = 2.dp,
                                color = if (item.completed) palette.goodInk else palette.track,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.completed) {
                            Text(
                                "✓",
                                fontSize = 11.sp,
                                color = palette.pageBg,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        text = item.chore.title,
                        fontSize = 15.sp,
                        color = if (item.completed) palette.inkFaint else palette.ink,
                        textDecoration = if (item.completed) TextDecoration.LineThrough
                        else TextDecoration.None,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.chore.xpPoints > 0) {
                        Text(
                            "${item.chore.xpPoints} XP",
                            fontSize = 12.sp,
                            color = palette.inkFaint,
                        )
                    }
                }
            }
        }
    }
}

// ── Week view ──────────────────────────────────────────────────────────────

@Composable
private fun WeekDayCard(
    day: LocalDate,
    members: List<MemberWithChores>,
) {
    val palette = LocalSeasonPalette.current
    val today = LocalDate.now()
    val isToday = day == today
    val dayIndex = day.dayOfWeek.value - 1 // 0=Mon…6=Sun
    val abbrev = WEEKDAY_ABBREVS[dayIndex]
    val dayLabelSv = WEEKDAY_LABELS_SV[dayIndex]
    val dateStr = "${day.dayOfMonth}/${day.monthValue}"

    // For each member, pick chores scheduled on this weekday
    val membersThisDay = members.mapNotNull { row ->
        val scheduled = row.chores.filter { abbrev in it.chore.weekdays }
        if (scheduled.isEmpty()) null else row to scheduled
    }

    val totalChores = membersThisDay.sumOf { (_, c) -> c.size }
    val doneChores = if (isToday) membersThisDay.sumOf { (_, c) -> c.count { it.completed } } else 0
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
                                "idag",
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
                        else -> "$totalChores sysslor"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (allDoneToday) palette.goodInk else palette.inkSoft,
                )
            }

            // Chore list
            if (membersThisDay.isEmpty()) {
                Text(
                    "Inga sysslor",
                    fontSize = 13.sp,
                    color = palette.inkFaint,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    membersThisDay.forEach { (row, chores) ->
                        // Member name label
                        Text(
                            text = row.member.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.inkSoft,
                        )
                        chores.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = if (isToday) (if (item.completed) "✅" else "⭕")
                                    else "·",
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
                        }
                    }
                }
            }
        }
    }
}

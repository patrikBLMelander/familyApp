package se.kidquest.app.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.RecurringAllowanceResponse
import se.kidquest.app.network.SaveRecurringAllowanceRequest

private val ink = Color(0xFF1C1917)
private val inkSoft = Color(0xFF57534E)
private val inkFaint = Color(0xFF78716C)
private val cardBg = Color(0xFFFFFBEB)
private val accent = Color(0xFF4C1D95)
private val accentSoft = Color(0xFFF5F3FF)
private val hairline = Color(0xFFE7E5E4)
private val money = Color(0xFF38A169)
private val deepBlue = Color(0xFF0C4A6E)
private val radioOff = Color(0xFFA8A29E)

/** Day of the month is capped at 28 so the date exists in February too -- the server agrees. */
private const val MAX_DAY_OF_MONTH = 28

private val swedish = Locale("sv", "SE")
private val weekdayNames = listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön")

private enum class Kind(val api: String) { WEEKLY("WEEKLY"), MONTHLY("MONTHLY"), LEVEL("LEVEL") }

/**
 * Automatic weekly or monthly allowance for one child.
 *
 * Reached only from the parent's view of a child's wallet. The server refuses a child
 * on both read and write, so hiding it here is about not putting the amounts in front
 * of the person they are about -- the actual lock is on the other side.
 *
 * The three options are one list, not three screens: a parent choosing between them
 * wants to compare, and only the chosen one unfolds its fields.
 */
@Composable
fun RecurringAllowanceScreen(
    childName: String,
    childId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<RecurringAllowanceResponse?>(null) }
    var currentLevel by remember { mutableStateOf<Int?>(null) }
    var confirmDisable by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    // Form state. Amounts stay strings so a half-typed field is not silently a zero.
    var kind by remember { mutableStateOf(Kind.WEEKLY) }
    var amount by remember { mutableStateOf("") }
    var weekday by remember { mutableStateOf(5) }
    var dayOfMonth by remember { mutableStateOf(1) }
    var levels by remember { mutableStateOf(List(5) { "" }) }

    LaunchedEffect(childId, refreshKey) {
        loading = true
        loadError = null
        try {
            val response = withContext(Dispatchers.IO) {
                ApiClient.recurringAllowanceApi.get(childId)
            }
            if (!response.isSuccessful) throw retrofit2.HttpException(response)
            val existing = response.body()
            saved = existing
            if (existing != null) {
                kind = Kind.entries.firstOrNull { it.api == existing.kind } ?: Kind.WEEKLY
                amount = existing.amount?.toString() ?: ""
                weekday = existing.weekday ?: 5
                dayOfMonth = existing.dayOfMonth ?: 1
                levels = listOf(
                    existing.level1, existing.level2, existing.level3,
                    existing.level4, existing.level5,
                ).map { it?.toString() ?: "" }
            }
        } catch (e: Exception) {
            loadError = ApiErrors.message(e, "Kunde inte hämta inställningen")
        } finally {
            loading = false
        }

        // Only used to mark which row the child is on right now. Not worth an error.
        currentLevel = kotlin.runCatching {
            withContext(Dispatchers.IO) { ApiClient.xpApi.getMemberXpProgress(childId) }
                .takeIf { it.isSuccessful }?.body()?.currentLevel
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFE0E7FF), Color(0xFFE0F2FE)))),
    ) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = accent,
            )

            loadError != null -> Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = loadError!!, color = ink)
                Button(onClick = { refreshKey++ }) { Text("Försök igen") }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Tillbaka",
                            tint = ink,
                        )
                    }
                    Text(
                        text = "Utbetalningar till $childName",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = ink,
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OptionCard(
                        selected = kind == Kind.WEEKLY,
                        icon = Icons.Filled.CalendarMonth,
                        title = "Veckopeng",
                        subtitle = "Samma belopp varje vecka",
                        onSelect = { kind = Kind.WEEKLY; error = null },
                    ) {
                        FieldLabel("Belopp")
                        AmountField(
                            label = "Varje vecka",
                            value = amount,
                            onValueChange = { amount = it; error = null },
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        FieldLabel("Vilken dag?")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            weekdayNames.forEachIndexed { index, label ->
                                val day = index + 1
                                DayChip(
                                    label = label,
                                    selected = weekday == day,
                                    onClick = { weekday = day; error = null },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        NextPaymentNote(kind, weekday, dayOfMonth, saved)
                    }

                    OptionCard(
                        selected = kind == Kind.MONTHLY,
                        icon = Icons.Filled.EventRepeat,
                        title = "Månadspeng",
                        subtitle = "Samma belopp varje månad",
                        onSelect = { kind = Kind.MONTHLY; error = null },
                    ) {
                        FieldLabel("Belopp")
                        AmountField(
                            label = "Varje månad",
                            value = amount,
                            onValueChange = { amount = it; error = null },
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        FieldLabel("Vilken dag i månaden?")
                        DayOfMonthField(
                            day = dayOfMonth,
                            onDayChange = { dayOfMonth = it; error = null },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        NextPaymentNote(kind, weekday, dayOfMonth, saved)
                        Text(
                            text = "Går att välja 1–$MAX_DAY_OF_MONTH, så dagen finns varje månad.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = inkFaint,
                        )
                    }

                    OptionCard(
                        selected = kind == Kind.LEVEL,
                        icon = Icons.Filled.BarChart,
                        title = "Månadspeng utifrån avklarade uppgifter",
                        subtitle = "Beloppet beror på vilken nivå $childName når",
                        onSelect = { kind = Kind.LEVEL; error = null },
                    ) {
                        FieldLabel("Belopp per nivå")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            levels.forEachIndexed { index, value ->
                                val level = index + 1
                                val here = currentLevel == level
                                AmountField(
                                    label = if (here) "Nivå $level · här nu" else "Nivå $level",
                                    value = value,
                                    highlighted = here,
                                    onValueChange = { typed ->
                                        levels = levels.toMutableList().also { it[index] = typed }
                                        error = null
                                    },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "$childName får beloppet för den nivå hen nått den 1:a. " +
                                "Nivån nollställs varje månad, precis som djuret.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = inkFaint,
                        )
                    }

                    if (error != null) {
                        Text(
                            text = error!!,
                            fontSize = 13.sp,
                            color = Color(0xFFB91C1C),
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (saving) deepBlue.copy(alpha = 0.6f) else deepBlue)
                            .clickable(enabled = !saving) {
                                val request = buildRequest(kind, amount, weekday, dayOfMonth, levels)
                                if (request == null) {
                                    error = missingFieldMessage(kind, amount, levels)
                                    return@clickable
                                }
                                saving = true
                                error = null
                                scope.launch {
                                    try {
                                        saved = withContext(Dispatchers.IO) {
                                            ApiClient.recurringAllowanceApi.save(childId, request)
                                        }
                                        onBack()
                                    } catch (e: Exception) {
                                        error = ApiErrors.message(e, "Kunde inte spara")
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (saving) "Sparar…" else "Spara",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }

                    if (saved?.active == true) {
                        Text(
                            text = "Stäng av automatisk utbetalning",
                            fontSize = 12.5.sp,
                            color = deepBlue,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !saving) { confirmDisable = true }
                                .padding(top = 6.dp, bottom = 6.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Stäng av?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Inga fler automatiska utbetalningar till $childName. " +
                        "Pengar som redan betalats ut ligger kvar i plånboken, och du " +
                        "kan slå på det igen när du vill.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            try {
                                val response = withContext(Dispatchers.IO) {
                                    ApiClient.recurringAllowanceApi.disable(childId)
                                }
                                if (!response.isSuccessful) throw retrofit2.HttpException(response)
                                confirmDisable = false
                                onBack()
                            } catch (e: Exception) {
                                confirmDisable = false
                                error = ApiErrors.message(e, "Kunde inte stänga av")
                            } finally {
                                saving = false
                            }
                        }
                    },
                ) { Text("Stäng av") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisable = false }) { Text("Avbryt") }
            },
        )
    }
}

// MARK: - Building and validating the request

/** Null when something required is still blank; the caller turns that into a message. */
private fun buildRequest(
    kind: Kind,
    amount: String,
    weekday: Int,
    dayOfMonth: Int,
    levels: List<String>,
): SaveRecurringAllowanceRequest? = when (kind) {
    Kind.WEEKLY -> amount.toIntOrNull()?.takeIf { it > 0 }?.let {
        SaveRecurringAllowanceRequest(kind = kind.api, amount = it, weekday = weekday)
    }

    Kind.MONTHLY -> amount.toIntOrNull()?.takeIf { it > 0 }?.let {
        SaveRecurringAllowanceRequest(kind = kind.api, amount = it, dayOfMonth = dayOfMonth)
    }

    Kind.LEVEL -> {
        val parsed = levels.map { it.toIntOrNull() }
        if (parsed.any { it == null }) {
            null
        } else {
            SaveRecurringAllowanceRequest(
                kind = kind.api,
                // The level kind always pays on the 1st: that is the day the level for
                // the month just ended is final, and the day it resets.
                dayOfMonth = 1,
                level1 = parsed[0], level2 = parsed[1], level3 = parsed[2],
                level4 = parsed[3], level5 = parsed[4],
            )
        }
    }
}

/**
 * Says which field is missing rather than "något saknas". Same wording the server
 * would answer with, but without the round trip.
 */
private fun missingFieldMessage(kind: Kind, amount: String, levels: List<String>): String =
    when (kind) {
        Kind.LEVEL -> {
            val blank = levels.indexOfFirst { it.toIntOrNull() == null }
            "Fyll i ett belopp för nivå ${blank + 1}"
        }

        else -> if (amount.isBlank()) "Fyll i ett belopp" else "Beloppet måste vara större än 0"
    }

// MARK: - Pieces

@Composable
private fun OptionCard(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onSelect: () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(
                width = 1.5.dp,
                color = if (selected) accent else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(enabled = !selected, onClick = onSelect)
            .alpha(if (selected) 1f else 0.72f),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Brush.horizontalGradient(listOf(accent, Color(0xFF1E293B)))),
            )
        }
        Column(
            modifier = Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = if (selected) 16.dp else 14.dp,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accent else inkFaint,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ink,
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        color = inkSoft,
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                Radio(selected)
            }
            if (selected) {
                Spacer(modifier = Modifier.height(16.dp))
                body()
            }
        }
    }
}

@Composable
private fun Radio(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) cardBg else Color.Transparent)
            .border(
                width = if (selected) 6.dp else 2.dp,
                color = if (selected) accent else radioOff,
                shape = CircleShape,
            ),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = inkFaint,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/**
 * An amount that reads as a line of the schedule rather than as a form field: the
 * label on the left, the money on the right, editable in place.
 */
@Composable
private fun AmountField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    highlighted: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (highlighted) Color(0xFFFAF8FF) else Color.White)
            .border(
                width = if (highlighted) 1.5.dp else 1.dp,
                color = if (highlighted) accent else hairline,
                shape = RoundedCornerShape(11.dp),
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.5.sp,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlighted) accent else inkSoft,
        )
        Spacer(modifier = Modifier.size(8.dp))
        BasicTextField(
            value = value,
            onValueChange = { typed -> onValueChange(typed.filter { it.isDigit() }.take(6)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = money,
                textAlign = TextAlign.End,
            ),
            cursorBrush = SolidColor(money),
            keyboardOptions = KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            ),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) {
                        Text(
                            text = "0",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = money.copy(alpha = 0.35f),
                        )
                    }
                    field()
                }
            },
        )
        Text(
            text = " kr",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = money,
        )
    }
}

@Composable
private fun RowScope.DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) accentSoft else Color.White)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent else hairline,
                shape = RoundedCornerShape(11.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accent else inkSoft,
        )
    }
}

@Composable
private fun DayOfMonthField(day: Int, onDayChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White)
                .border(1.dp, hairline, RoundedCornerShape(11.dp))
                .clickable { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Den ${ordinal(day)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ink,
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = "Välj dag",
                tint = inkFaint,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (1..MAX_DAY_OF_MONTH).forEach { candidate ->
                DropdownMenuItem(
                    text = { Text("Den ${ordinal(candidate)}") },
                    onClick = { onDayChange(candidate); open = false },
                )
            }
        }
    }
}

/**
 * "Varje vecka" is a claim; a date is a promise. This turns the choice above into the
 * day it will actually happen.
 */
@Composable
private fun NextPaymentNote(
    kind: Kind,
    weekday: Int,
    dayOfMonth: Int,
    saved: RecurringAllowanceResponse?,
) {
    val today = remember { LocalDate.now() }
    val pending = saved?.takeIf { it.active && it.kind == kind.api }
        ?.nextDueOn
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.takeIf { !it.isAfter(today) }

    val text = when {
        pending != null -> "Nästa utbetalning: idag."
        kind == Kind.WEEKLY -> {
            val date = nextWeekday(weekday, today)
            "Nästa utbetalning: ${date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", swedish))}."
        }

        else -> {
            val date = nextDayOfMonth(dayOfMonth, today)
            "Nästa utbetalning: ${date.format(DateTimeFormatter.ofPattern("d MMMM", swedish))}."
        }
    }
    Text(text = text, fontSize = 12.sp, lineHeight = 17.sp, color = inkFaint)
}

// MARK: - Dates
//
// Deliberately the same rule as the server's firstDueAfter: the next occurrence
// strictly after today, never today itself. A preview that disagreed with the
// schedule would be worse than no preview.

private fun nextWeekday(weekday: Int, today: LocalDate): LocalDate {
    var date = today.plusDays(1)
    while (date.dayOfWeek.value != weekday) date = date.plusDays(1)
    return date
}

private fun nextDayOfMonth(day: Int, today: LocalDate): LocalDate {
    val candidate = today.withDayOfMonth(day.coerceIn(1, MAX_DAY_OF_MONTH))
    return if (candidate.isAfter(today)) candidate else candidate.plusMonths(1)
}

/** Swedish ordinals: 1:a, 2:a, 3:e … 11:e, 12:e … 21:a, 22:a, 23:e. */
private fun ordinal(day: Int): String {
    val suffix = if (day % 10 in 1..2 && day != 11 && day != 12) ":a" else ":e"
    return "$day$suffix"
}

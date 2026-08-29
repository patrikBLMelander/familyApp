package se.kidquest.app.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.RecurringAllowanceResponse
import se.kidquest.app.network.SavingsGoalResponse
import se.kidquest.app.network.WalletBalanceResponse
import se.kidquest.app.network.WalletTransactionResponse
import se.kidquest.app.theme.SeasonPalette
import se.kidquest.app.theme.LocalSeasonPalette

private val textPrimary = Color(0xFF1C1917)
private val textSecondary = Color(0xFF57534E)

/**
 * Whose screen this is, as far as colour is concerned.
 *
 * A child's own wallet -- and a parent previewing it -- keeps the animal's colours,
 * because that is the child's identity. A parent administering the wallet gets the
 * season, like every other screen a parent opens.
 */
private class WalletSkin(val parentView: Boolean, val palette: SeasonPalette) {
    val surface: Color get() = if (parentView) palette.surface else Color.White.copy(alpha = 0.82f)
    val ink: Color get() = if (parentView) palette.ink else textPrimary
    val inkSoft: Color get() = if (parentView) palette.inkSoft else textSecondary
    /** White reads on every animal gradient; on a light season ground it does not. */
    val onBackground: Color get() = if (parentView) palette.ink else Color.White
}

@Composable
fun ChildWalletScreen(
    childName: String,
    childId: String,
    isOwnWallet: Boolean,
    onBack: () -> Unit,
    onOpenRecurringAllowance: () -> Unit = {},
    /** Opened from inside the child's own view rather than from the overview. */
    fromChildView: Boolean = false,
) {
    var balance by remember { mutableStateOf<WalletBalanceResponse?>(null) }
    var transactions by remember { mutableStateOf<List<WalletTransactionResponse>>(emptyList()) }
    var savingsGoals by remember { mutableStateOf<List<SavingsGoalResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showGiveMoneyDialog by remember { mutableStateOf(false) }
    var showExpenseDialog by remember { mutableStateOf(false) }
    var showCreateGoalDialog by remember { mutableStateOf(false) }
    var showAllocateDialog by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var petType by remember { mutableStateOf<String?>(null) }
    var recurring by remember { mutableStateOf<RecurringAllowanceResponse?>(null) }

    LaunchedEffect(childId, isOwnWallet, refreshKey) {
        loading = true
        error = null
        try {
            coroutineScope {
                val balanceDeferred = async {
                    if (isOwnWallet) ApiClient.walletApi.getWalletBalance()
                    else ApiClient.walletApi.getMemberBalance(childId)
                }
                val txDeferred = async {
                    if (isOwnWallet) ApiClient.walletApi.getTransactions(limit = 20)
                    else ApiClient.walletApi.getMemberTransactions(childId, limit = 20)
                }
                val goalsDeferred = async {
                    if (isOwnWallet) ApiClient.walletApi.getSavingsGoals() else emptyList()
                }
                val petDeferred = async {
                    kotlin.runCatching {
                        if (isOwnWallet) ApiClient.petsApi.getCurrentPet()
                        else ApiClient.petsApi.getMemberPet(childId)
                    }.getOrNull()
                }
                // Parent view only. The server refuses a child on this endpoint, so
                // asking for it in the child's own wallet would fail every time.
                val recurringDeferred = async {
                    if (isOwnWallet) null
                    else kotlin.runCatching {
                        ApiClient.recurringAllowanceApi.get(childId)
                            .takeIf { it.isSuccessful }?.body()
                    }.getOrNull()
                }

                balance = balanceDeferred.await()
                transactions = txDeferred.await()
                savingsGoals = goalsDeferred.await()
                val petResp = petDeferred.await()
                petType = if (petResp?.isSuccessful == true) petResp.body()?.petType else null
                recurring = recurringDeferred.await()
            }
        } catch (e: Exception) {
            error = ApiErrors.message(e, "Kunde inte ladda")
        } finally {
            loading = false
        }
    }

    val palette = LocalSeasonPalette.current
    val skin = WalletSkin(parentView = !isOwnWallet && !fromChildView, palette = palette)
    val backgroundBrush =
        if (skin.parentView) SolidColor(palette.pageBg) else walletGradient(petType)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = skin.onBackground,
            )
        } else if (error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = error!!, color = skin.onBackground)
                Button(onClick = { refreshKey++ }) { Text("Försök igen") }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Tillbaka",
                            tint = skin.onBackground,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "$childName – Plånbok",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = skin.onBackground,
                    )
                }

                // Balance card
                balance?.let { b ->
                    WalletCard(skin) {
                        Text(
                            text = "Saldo",
                            fontSize = 14.sp,
                            color = skin.inkSoft,
                        )
                        Text(
                            text = "${b.balance} kr",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = skin.ink,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Giving money is a decision a parent makes from their own
                        // side of the app. Recording a purchase is something you do
                        // standing next to the child, so it belongs on every route in.
                        val canGiveMoney = !isOwnWallet && !fromChildView
                        if (canGiveMoney) {
                            Button(
                                onClick = { showGiveMoneyDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38A169)),
                            ) {
                                Text("Ge pengar")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Button(
                            onClick = { showExpenseDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = b.balance > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canGiveMoney) Color(0xFF2B6CB0) else Color(0xFF38A169),
                            ),
                        ) {
                            Text("Registrera köp")
                        }
                    }
                }

                // Automatic allowance (parent view only). Deliberately not shown to
                // the child: the amounts are a parent's decision, and a level table
                // read as a price list is a promise no one made.
                if (!isOwnWallet) {
                    RecurringAllowanceRow(
                        skin = skin,
                        schedule = recurring,
                        onClick = if (fromChildView) null else onOpenRecurringAllowance,
                    )
                }

                // Savings goals (own wallet only)
                if (isOwnWallet) {
                    WalletCard(skin) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Sparmål",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val hasBalance = (balance?.balance ?: 0) > 0
                                val hasActiveGoals = savingsGoals.any { it.isActive && !it.isCompleted }
                                if (hasBalance && hasActiveGoals) {
                                    TextButton(onClick = { showAllocateDialog = true }) {
                                        Text("Fördela", fontSize = 13.sp)
                                    }
                                }
                                TextButton(onClick = { showCreateGoalDialog = true }) {
                                    Text("+ Nytt mål", fontSize = 13.sp)
                                }
                            }
                        }

                        if (savingsGoals.isEmpty()) {
                            Text(
                                text = "Inga sparmål ännu.",
                                fontSize = 14.sp,
                                color = textSecondary,
                            )
                        } else {
                            val active = savingsGoals.filter { it.isActive && !it.isCompleted }
                            val done = savingsGoals.filter { it.isCompleted || it.isPurchased }.take(3)

                            Spacer(modifier = Modifier.height(4.dp))
                            active.forEach { goal -> SavingsGoalRow(goal = goal, dimmed = false) }
                            done.forEach { goal -> SavingsGoalRow(goal = goal, dimmed = true) }
                        }
                    }
                }

                // Transactions
                WalletCard(skin) {
                    Text(
                        text = "Senaste transaktioner",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = skin.ink,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (transactions.isEmpty()) {
                        Text(
                            text = "Inga transaktioner ännu.",
                            fontSize = 14.sp,
                            color = skin.inkSoft,
                        )
                    } else {
                        transactions.take(20).forEach { t ->
                            TransactionRow(skin, t)
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }

    if (showGiveMoneyDialog) {
        GiveMoneyDialog(
            childName = childName,
            childId = childId,
            onDismiss = { showGiveMoneyDialog = false },
            onSuccess = { showGiveMoneyDialog = false; refreshKey++ },
        )
    }
    if (showExpenseDialog && balance != null) {
        RecordExpenseDialog(
            currentBalance = balance!!.balance,
            onDismiss = { showExpenseDialog = false },
            onSuccess = { showExpenseDialog = false; refreshKey++ },
            memberId = if (isOwnWallet) null else childId,
            childName = if (isOwnWallet) null else childName,
        )
    }
    if (isOwnWallet && showCreateGoalDialog) {
        CreateSavingsGoalDialog(
            onDismiss = { showCreateGoalDialog = false },
            onSuccess = { showCreateGoalDialog = false; refreshKey++ },
        )
    }
    if (isOwnWallet && showAllocateDialog && balance != null) {
        AllocateToGoalsDialog(
            currentBalance = balance!!.balance,
            onDismiss = { showAllocateDialog = false },
            onSuccess = { showAllocateDialog = false; refreshKey++ },
        )
    }
}

// MARK: - Reusable card

@Composable
private fun WalletCard(skin: WalletSkin, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(skin.surface)
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

// MARK: - Automatic allowance row

/**
 * Where the automatic allowance lives: one line in the wallet, because the wallet is
 * where a parent already goes to think about money.
 *
 * The subtitle carries the date rather than the amount. A parent who wants to check
 * that it is on needs to know when; a parent who wants to change the amount is
 * tapping through anyway.
 */
@Composable
private fun RecurringAllowanceRow(
    skin: WalletSkin,
    schedule: RecurringAllowanceResponse?,
    /** Null inside the child's view: the arrangement is worth seeing, not changing there. */
    onClick: (() -> Unit)?,
) {
    val active = schedule?.active == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(skin.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = if (active) Color(0xFF38A169) else Color(0xFFA8A29E),
            modifier = Modifier.width(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Automatisk utbetalning",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = skin.ink,
            )
            Text(
                text = describeSchedule(schedule),
                fontSize = 12.sp,
                color = if (active) skin.inkSoft else skin.palette.inkFaint,
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = skin.palette.inkFaint,
                modifier = Modifier.width(16.dp),
            )
        }
    }
}

private fun describeSchedule(schedule: RecurringAllowanceResponse?): String {
    if (schedule == null || !schedule.active) return "Inte inställt"
    val kind = when (schedule.kind) {
        "WEEKLY" -> "Veckopeng"
        "MONTHLY" -> "Månadspeng"
        else -> "Efter nivå"
    }
    val due = schedule.nextDueOn
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.format(DateTimeFormatter.ofPattern("d MMMM", Locale("sv", "SE")))
    return if (due == null) kind else "$kind · nästa $due"
}

// MARK: - Savings goal row

@Composable
private fun SavingsGoalRow(goal: SavingsGoalResponse, dimmed: Boolean) {
    val alpha = if (dimmed) 0.55f else 1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFE0F2FE).copy(alpha = alpha))
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${goal.emoji?.let { "$it " } ?: ""}${goal.name}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary.copy(alpha = alpha),
                )
                Text(
                    text = when {
                        goal.isPurchased -> "🛒 Köpt"
                        goal.isCompleted -> "✓ Klar"
                        else -> "${goal.currentAmount} / ${goal.targetAmount} kr"
                    },
                    fontSize = 12.sp,
                    color = if (goal.isCompleted && !goal.isPurchased) Color(0xFF22C55E)
                            else textSecondary.copy(alpha = alpha),
                )
            }
            if (!goal.isCompleted && !goal.isPurchased) {
                LinearProgressIndicator(
                    progress = { goal.progressPercentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF48BB78),
                    trackColor = Color.Black.copy(alpha = 0.08f),
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    text = "${goal.remainingAmount} kr kvar",
                    fontSize = 11.sp,
                    color = textSecondary.copy(alpha = alpha),
                )
            }
        }
    }
}

// MARK: - Transaction row

@Composable
private fun TransactionRow(skin: WalletSkin, t: WalletTransactionResponse) {
    val isSavings = t.transactionType == "SAVINGS_ALLOCATION"
    val isExpense = t.amount < 0
    // Money colours mean something, so they stay -- but a dark card needs the lighter
    // end of each hue or the amount disappears into it.
    val onDark = skin.parentView && skin.palette.dark
    val accentColor = when {
        isSavings -> if (onDark) Color(0xFF7FB0F5) else Color(0xFF2563EB)
        isExpense -> if (onDark) Color(0xFFF58A8A) else Color(0xFFEF4444)
        else -> if (onDark) Color(0xFF6FD38F) else Color(0xFF22C55E)
    }
    val sign = if (t.amount >= 0) "+" else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.08f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(52.dp)
                .background(accentColor, RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = t.description ?: localizedType(t.transactionType),
                fontSize = 14.sp,
                color = skin.ink,
            )
            Text(
                text = formatDate(t.createdAt),
                fontSize = 11.sp,
                color = skin.inkSoft,
            )
        }
        Text(
            text = "$sign${t.amount} kr",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = accentColor,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

// MARK: - Helpers

private fun localizedType(type: String) = when (type) {
    "ALLOWANCE" -> "Fickpengar"
    "EXPENSE" -> "Köp"
    "SAVINGS_ALLOCATION" -> "Sparmål"
    else -> type
}

private fun formatDate(iso: String): String = try {
    val dt = OffsetDateTime.parse(iso)
    dt.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("sv", "SE")))
} catch (_: Exception) {
    iso
}

private fun walletGradient(petType: String?): Brush = when (petType?.lowercase()) {
    "dragon" -> Brush.verticalGradient(listOf(Color(0xFF4C1D95), Color(0xFF1E293B)))
    "cat" -> Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF97316)))
    "dog" -> Brush.verticalGradient(listOf(Color(0xFFBBF7D0), Color(0xFF22C55E)))
    "bird" -> Brush.verticalGradient(listOf(Color(0xFFBFDBFE), Color(0xFF2563EB)))
    "rabbit" -> Brush.verticalGradient(listOf(Color(0xFFFCE7F3), Color(0xFFEC4899)))
    "bear" -> Brush.verticalGradient(listOf(Color(0xFFFEF3C7), Color(0xFF92400E)))
    "snake" -> Brush.verticalGradient(listOf(Color(0xFFDCFCE7), Color(0xFF15803D)))
    "panda" -> Brush.verticalGradient(listOf(Color(0xFFE5E7EB), Color(0xFF111827)))
    "slot" -> Brush.verticalGradient(listOf(Color(0xFFE5E7EB), Color(0xFF6B7280)))
    "hydra" -> Brush.verticalGradient(listOf(Color(0xFFC4B5FD), Color(0xFF4C1D95)))
    "unicorn" -> Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF9A8D4)))
    "kapybara" -> Brush.verticalGradient(listOf(Color(0xFFDCFCE7), Color(0xFF22C55E)))
    else -> Brush.verticalGradient(listOf(Color(0xFFE0E7FF), Color(0xFFE0F2FE)))
}

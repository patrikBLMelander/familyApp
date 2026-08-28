package se.kidquest.app.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.kidquest.app.chore.DailyChoreRepository
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.FamilyMemberResponse
import se.kidquest.app.network.SubscriptionStatusResponse
import se.kidquest.app.network.UpdateFamilyMemberRequest
import se.kidquest.app.network.UpdatePasswordRequest
import se.kidquest.app.pet.PetImages
import se.kidquest.app.pet.PetTheme
import se.kidquest.app.pet.PetVisual
import se.kidquest.app.session.PrefsStore
import se.kidquest.app.session.TokenStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdultDashboardScreen(
    modifier: Modifier = Modifier,
    refreshKey: Int = 0,
    showAddMemberHint: Boolean = false,
    onDismissAddMemberHint: () -> Unit = {},
    onLogout: () -> Unit = {},
    onAddFamilyMember: () -> Unit = {},
    onChildPet: (childId: String, childName: String) -> Unit = { _, _ -> },
    onChildWallet: (childId: String, childName: String) -> Unit = { _, _ -> },
    onChildTasks: (childId: String, childName: String) -> Unit = { _, _ -> },
    onChildView: (childId: String, childName: String) -> Unit = { _, _ -> },
    onFamilyTasks: () -> Unit = {},
) {
    var children by remember { mutableStateOf<List<FamilyMemberResponse>>(emptyList()) }
    // refreshKey comes from the caller; this covers reloads the screen triggers
    // itself, such as after a rename or a delete.
    var localRefresh by remember { mutableStateOf(0) }
    var memberPendingRename by remember { mutableStateOf<FamilyMemberResponse?>(null) }
    var memberPendingDelete by remember { mutableStateOf<FamilyMemberResponse?>(null) }
    var memberPendingPassword by remember { mutableStateOf<FamilyMemberResponse?>(null) }
    // Every row's done-state comes from real family data below. Only the dismissal is
    // stored, so the card survives a reinstall, shows as complete for a family who set
    // up on web, and comes back if a parent later deletes their only child.
    val dashboardScope = rememberCoroutineScope()
    var anyChildHasChores by remember { mutableStateOf(false) }
    var onboardingDismissed by remember { mutableStateOf(true) }
    // Null until the parent decides, because the sensible default depends on family
    // data that is not loaded yet. Held here rather than inside the LazyColumn item so
    // scrolling the row out of view does not forget the choice.
    var checklistExpanded by remember { mutableStateOf<Boolean?>(null) }
    var subscription by remember { mutableStateOf<SubscriptionStatusResponse?>(null) }
    var topMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onboardingDismissed = PrefsStore.isOnboardingDismissed()
    }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var inviteChild by remember { mutableStateOf<FamilyMemberResponse?>(null) }
    var childSummaries by remember { mutableStateOf<Map<String, ChildSummary>>(emptyMap()) }
    var adults by remember { mutableStateOf<List<FamilyMemberResponse>>(emptyList()) }
    // Used to mark the signed-in parent and keep them out of their own delete menu.
    val currentMemberId = remember { TokenStore.getSession()?.memberId }

    LaunchedEffect(refreshKey, localRefresh) {
        loading = true
        error = null
        try {
            val all = ApiClient.familyMembersApi.getAllMembers()
            children = all.filter { it.role == "CHILD" || it.role == "ASSISTANT" }
            adults = all.filter { it.role == "PARENT" }
            anyChildHasChores = coroutineScope {
                children.map { child ->
                    async { kotlin.runCatching { DailyChoreRepository.hasAnyChore(child.id) }.getOrDefault(false) }
                }.awaitAll().any { it }
            }
            childSummaries = coroutineScope {
                children.map { child ->
                    async {
                        try {
                            val choresDeferred = async { DailyChoreRepository.fetchChoresForToday(child.id) }
                            val petDeferred = async { kotlin.runCatching { ApiClient.petsApi.getMemberPet(child.id) }.getOrNull() }

                            val chores = choresDeferred.await()
                            val petResp = petDeferred.await()

                            val total = chores.size
                            val done = chores.count { it.completed }
                            val nextTaskTitle = chores.firstOrNull { !it.completed }?.chore?.title
                            val pet = petResp?.takeIf { it.isSuccessful }?.body()

                            child.id to ChildSummary(
                                memberId = child.id,
                                memberName = child.name,
                                todaysDone = done,
                                todaysTotal = total,
                                hasPet = pet != null,
                                petType = pet?.petType,
                                growthStage = pet?.growthStage ?: 1,
                                nextTaskTitle = nextTaskTitle,
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                }.mapNotNull { it.await() }.toMap()
            }
        } catch (e: Exception) {
            error = ApiErrors.message(e, "Kunde inte ladda familjemedlemmar")
        } finally {
            loading = false
        }

        // Deliberately outside the block above: billing is not worth blanking the
        // dashboard over. If this fails the banner simply does not appear, and the
        // server still enforces entitlement on every write.
        subscription = kotlin.runCatching {
            ApiClient.subscriptionApi.getStatus().takeIf { it.isSuccessful }?.body()
        }.getOrNull()
    }

    val backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFFE0E7FF), // ljus lavendel
            Color(0xFFE0F2FE), // ljus blå
        )
    )
    val cardPastel = Color(0xFFFFFBEB)       // mjuk creme
    val textPrimary = Color(0xFF1C1917)
    val textSecondary = Color(0xFF57534E)
    val buttonPastel = Color(0xFFBAE6FD)
    val buttonOnPastel = Color(0xFF0C4A6E)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Min familj", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = textPrimary,
                ),
                actions = {
                    // Signing out is a once-a-year action. As a full outlined button it
                    // was the loudest thing on a screen where everything else had been
                    // quietened deliberately.
                    Box(modifier = Modifier.padding(end = 4.dp)) {
                        IconButton(onClick = { topMenuOpen = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Fler val",
                                tint = textSecondary,
                            )
                        }
                        DropdownMenu(
                            expanded = topMenuOpen,
                            onDismissRequest = { topMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Logga ut") },
                                onClick = {
                                    topMenuOpen = false
                                    onLogout()
                                },
                            )
                        }
                    }
                },
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundBrush)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                return@Scaffold
            }

            val summaryList = children.mapNotNull { childSummaries[it.id] }
            val totalTasksToday = summaryList.sumOf { it.todaysTotal }
            val completedTasksToday = summaryList.sumOf { it.todaysDone }
            val childrenWithPet = summaryList.count { it.hasPet }
            val suggestion = summaryList
                .filter { it.todaysTotal > 0 }
                .minByOrNull { if (it.todaysTotal == 0) 1.0 else it.todaysDone.toDouble() / it.todaysTotal.toDouble() }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                subscription?.let { sub ->
                    item {
                        SubscriptionBanner(status = sub, textSecondary = textSecondary)
                    }
                }

                if (children.isNotEmpty()) {
                    item {
                        FamilyTodayCard(
                            done = completedTasksToday,
                            total = totalTasksToday,
                            childrenWithPet = childrenWithPet,
                            childCount = children.size,
                            cardPastel = cardPastel,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accent = buttonOnPastel,
                            // The card is the way into the family task list. It replaced
                            // a full-width button that sat here competing with the four
                            // buttons inside every child card below it.
                            onClick = onFamilyTasks,
                        )
                    }

                    suggestion?.let { s ->
                        item {
                            SuggestionStrip(
                                childName = s.memberName,
                                message = when {
                                    s.todaysDone >= s.todaysTotal && s.todaysTotal > 0 ->
                                        "har gjort alla sina uppgifter idag – ge extra beröm!"
                                    !s.nextTaskTitle.isNullOrBlank() ->
                                        "behöver påminnas om \"${s.nextTaskTitle}\" för att mata sitt djur."
                                    else -> "behöver påminnas om dagens uppgifter."
                                },
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                            )
                        }
                    }
                }

                if (!onboardingDismissed) {
                    item {
                        val hasChild = children.isNotEmpty()
                        val hasPairedDevice = children.any { it.hasPairedDevice }
                        val hasPet = childSummaries.values.any { it.hasPet }
                        val doneCount =
                            listOf(hasChild, anyChildHasChores, hasPairedDevice, hasPet).count { it }
                        // A family that has not started sees the whole guide. Once any
                        // step is done it folds into one row, so four setup steps stop
                        // taking the screenful that belongs to the children.
                        val expanded = checklistExpanded ?: (doneCount == 0)

                        if (expanded) {
                            GetStartedCard(
                                hasChild = hasChild,
                                hasChores = anyChildHasChores,
                                hasPairedDevice = hasPairedDevice,
                                hasPet = hasPet,
                                cardPastel = cardPastel,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onAddChild = onAddFamilyMember,
                                onAddChores = {
                                    children.firstOrNull()?.let { onChildTasks(it.id, it.name) }
                                },
                                onPairDevice = { children.firstOrNull()?.let { inviteChild = it } },
                                // The child view, not the pet screen. The pet screen is
                                // read-only for a parent -- it says the child has not chosen
                                // an egg and offers no way to do it. The child view is where
                                // the egg picker lives.
                                onSeePet = {
                                    children.firstOrNull()?.let { onChildView(it.id, it.name) }
                                },
                                onCollapse = { checklistExpanded = false },
                                onDismiss = {
                                    onboardingDismissed = true
                                    dashboardScope.launch { PrefsStore.setOnboardingDismissed(true) }
                                },
                            )
                        } else {
                            GetStartedStrip(
                                doneCount = doneCount,
                                // Pairing is last on purpose: it is the skippable step,
                                // so it should never be what a parent is told to do next.
                                nextLabel = when {
                                    !hasChild -> "lägg till ett barn"
                                    !anyChildHasChores -> "lägg till dagliga sysslor"
                                    !hasPet -> "välj ett ägg"
                                    !hasPairedDevice -> "koppla barnets telefon"
                                    else -> null
                                },
                                cardPastel = cardPastel,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                accent = buttonOnPastel,
                                onExpand = { checklistExpanded = true },
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Mina barn",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }

                if (children.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardPastel),
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "Inga barn i familjen ännu",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = textPrimary,
                                )
                                Text(
                                    text = "Lägg till ditt första barn nedan.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textSecondary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                } else {
                    items(children, key = { it.id }) { child ->
                        val summary = childSummaries[child.id]
                        ChildCard(
                            name = child.name,
                            hasPairedDevice = child.hasPairedDevice,
                            cardPastel = cardPastel,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            buttonPastel = buttonPastel,
                            buttonOnPastel = buttonOnPastel,
                            summary = summary,
                            onPetClick = { onChildPet(child.id, child.name) },
                            onWalletClick = { onChildWallet(child.id, child.name) },
                            onTasksClick = { onChildTasks(child.id, child.name) },
                            onChildViewClick = { onChildView(child.id, child.name) },
                            onRenameClick = { memberPendingRename = child },
                            onDeleteClick = { memberPendingDelete = child },
                            onInviteClick = { inviteChild = child },
                        )
                    }
                }

                if (adults.isNotEmpty()) {
                    item {
                        Text(
                            text = "Vuxna",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(adults, key = { it.id }) { adult ->
                        AdultRow(
                            name = adult.name,
                            isCurrentUser = adult.id == currentMemberId,
                            hasPairedDevice = adult.hasPairedDevice,
                            cardPastel = cardPastel,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            buttonPastel = buttonPastel,
                            buttonOnPastel = buttonOnPastel,
                            onRenameClick = { memberPendingRename = adult },
                            onPasswordClick = { memberPendingPassword = adult },
                            onDeleteClick = { memberPendingDelete = adult },
                            onInviteClick = { inviteChild = adult },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            memberPendingRename?.let { member ->
                RenameMemberDialog(
                    member = member,
                    onDismiss = { memberPendingRename = null },
                    onRenamed = {
                        memberPendingRename = null
                        localRefresh++
                    },
                )
            }

            memberPendingPassword?.let { member ->
                ChangePasswordDialog(
                    member = member,
                    isSelf = member.id == currentMemberId,
                    onDismiss = { memberPendingPassword = null },
                    onChanged = { memberPendingPassword = null },
                )
            }

            memberPendingDelete?.let { member ->
                DeleteMemberDialog(
                    member = member,
                    onDismiss = { memberPendingDelete = null },
                    onDeleted = {
                        memberPendingDelete = null
                        localRefresh++
                    },
                )
            }

            if (showAddMemberHint && children.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Steg 1: Lägg till ditt första barn",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tryck på \"Lägg till familjemedlem\" här nedanför så hjälper vi dig komma igång.",
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecondary,
                        )
                    }
                }
            }

            // Outlined rather than filled: it stays pinned and always reachable, but
            // a second sky-blue slab down here read as important as the one action
            // inside each child's card, which is the thing a parent opens daily.
            OutlinedButton(
                onClick = {
                    onDismissAddMemberHint()
                    onAddFamilyMember()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, Color(0xFFA5B4FC)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF3730A3)),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Lägg till familjemedlem", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    inviteChild?.let { child ->
        ChildInviteDialog(
            child = child,
            onDismiss = { inviteChild = null },
        )
    }
}

private data class ChildSummary(
    val memberId: String,
    val memberName: String,
    val todaysDone: Int,
    val todaysTotal: Int,
    val hasPet: Boolean,
    /** Drives the card's portrait and its colour. Null until an egg has been chosen. */
    val petType: String?,
    val growthStage: Int,
    val nextTaskTitle: String?,
)
// A streakDays field used to live here, hardcoded to 0, behind a `> 0` check in the
// card -- so the streak line it fed could never appear. Dropped rather than kept as
// dead weight; it comes back the day the backend actually reports a streak.

/** Diameter of the ring drawn around a child's pet portrait. */
private val PORTRAIT_SIZE = 84.dp
private val PORTRAIT_PET_SIZE = 72.dp
private val PORTRAIT_STROKE = 4.dp

/**
 * Swedish possessive.
 *
 * A name already ending in s, x or z takes no extra s, so the old `"${name}s Sysslor"`
 * produced "Nilss Sysslor" for a perfectly ordinary Swedish name.
 */
private fun possessive(name: String): String {
    val trimmed = name.trim()
    return when (trimmed.lastOrNull()?.lowercaseChar()) {
        's', 'x', 'z' -> trimmed
        else -> "${trimmed}s"
    }
}

/**
 * A child's pet, ringed by how much of today's list is done.
 *
 * The ring replaces the sentence "Idag: 3 av 5 uppgifter gjorda". A parent with three
 * children reads three rings at a glance; three sentences have to be read one at a
 * time. The exact figure stays in the badge for whoever wants it.
 *
 * The portrait draws through PetVisual, so it is the same art over the same seasonal
 * background the child sees on their own screen and the two cannot drift apart.
 */
@Composable
private fun ChildPetPortrait(
    petType: String?,
    growthStage: Int,
    done: Int,
    total: Int,
    accent: Color,
    cardPastel: Color,
    textPrimary: Color,
    childName: String,
) {
    val fraction = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    Box(modifier = Modifier.size(PORTRAIT_SIZE)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = PORTRAIT_STROKE.toPx()
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            // The track is always drawn, so a child with nothing done still reads as
            // "0 of 4" rather than as a card that failed to load.
            drawArc(
                // 0.15 vanished against a deep accent like dragon's violet, which left
                // the ring reading as a floating arc rather than a share of a circle.
                color = accent.copy(alpha = 0.22f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (fraction > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        if (petType != null) {
            PetVisual(
                petType = petType,
                growthStage = growthStage,
                modifier = Modifier
                    .size(PORTRAIT_PET_SIZE)
                    .align(Alignment.Center),
                contentDescription = "${possessive(childName)} djur",
                // Half the diameter, so the frame is a circle rather than a rounded square.
                cornerRadius = 36,
                alignment = Alignment.BottomCenter,
                // The 8.dp default is tuned for the full-screen pet; at 72.dp it eats
                // most of the animal.
                petPadding = 2.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(PORTRAIT_PET_SIZE)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Pets,
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        if (total > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(8.dp))
                    .background(cardPastel)
                    .border(1.5.dp, accent, RoundedCornerShape(8.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "$done/$total",
                    fontSize = 10.5.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                )
            }
        }
    }
}

/**
 * One child, as the thing the screen is actually about.
 *
 * What changed and why:
 *
 * - The pet is here. It is the app's whole proposition and it used to appear nowhere
 *   in the parent's view, so a parent saw a name, three lines of grey text and four
 *   buttons -- a database row with actions.
 * - Today's progress is a ring rather than a sentence, and the species carries its own
 *   colour from PetTheme along the card's top edge, so each child's card matches the
 *   screen that child sees.
 * - One primary action. Four buttons of equal weight meant no primary action at all;
 *   the chore list is what a parent opens daily, so it is the filled one.
 * - "Bjud in till appen" left the card. It is done once per child, and it held
 *   permanent full-width space for the rest of the child's life in the app. It is in
 *   the overflow menu, and appears as a row here only while the phone is unpaired.
 */
@Composable
private fun ChildCard(
    name: String,
    hasPairedDevice: Boolean,
    cardPastel: Color,
    textPrimary: Color,
    textSecondary: Color,
    buttonPastel: Color,
    buttonOnPastel: Color,
    summary: ChildSummary? = null,
    onPetClick: () -> Unit,
    onWalletClick: () -> Unit,
    onTasksClick: () -> Unit,
    onInviteClick: () -> Unit,
    onChildViewClick: () -> Unit = {},
    onRenameClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    val palette = PetTheme.forPet(summary?.petType)
    val speciesName = PetImages.speciesName(summary?.petType)
    val allDoneToday = summary != null && summary.todaysTotal > 0 &&
        summary.todaysDone >= summary.todaysTotal

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardPastel.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        // A sliver of the child's own screen. The gradient is the same one that fills
        // that screen behind the pet, which is what ties the two together.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(PetTheme.edge(summary?.petType)),
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChildPetPortrait(
                    petType = summary?.petType,
                    growthStage = summary?.growthStage ?: 1,
                    done = summary?.todaysDone ?: 0,
                    total = summary?.todaysTotal ?: 0,
                    accent = palette.accent,
                    cardPastel = cardPastel,
                    textPrimary = textPrimary,
                    childName = name,
                )
                Spacer(modifier = Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = when {
                            summary == null -> "Kunde inte läsa dagens sysslor"
                            speciesName != null -> "$speciesName · nivå ${summary.growthStage}"
                            else -> "Inget djur valt ännu"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary,
                    )
                    // A chip only when there is something worth saying. The ring already
                    // covers every state in between.
                    if (allDoneToday) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFDCFCE7))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF166534),
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = "Allt klart idag",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF166534),
                            )
                        }
                    } else if (summary != null && summary.todaysTotal == 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Inga sysslor planerade idag",
                            style = MaterialTheme.typography.labelSmall,
                            color = textSecondary,
                        )
                    }
                }
                // Kept out of the name row so it stays a full 48.dp touch target
                // without stretching the text beside it.
                Box(modifier = Modifier.align(Alignment.Top)) {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Fler val för $name",
                            tint = textSecondary,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Visa som barn") },
                            onClick = {
                                menuOpen = false
                                onChildViewClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Bjud in till appen") },
                            onClick = {
                                menuOpen = false
                                onInviteClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Byt namn") },
                            onClick = {
                                menuOpen = false
                                onRenameClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Ta bort", color = Color(0xFFC53030)) },
                            onClick = {
                                menuOpen = false
                                onDeleteClick()
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Only while it is outstanding. Once the phone is paired this row is gone
            // for good, rather than becoming a button nobody will press again.
            if (!hasPairedDevice) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFF7ED))
                        .clickable(onClick = onInviteClick)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Ingen telefon kopplad ännu",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF92400E),
                    )
                    Text(
                        text = "Bjud in",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB45309),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            FilledTonalButton(
                onClick = onTasksClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = buttonPastel,
                    contentColor = buttonOnPastel,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Checklist,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(9.dp))
                Text("${possessive(name)} sysslor", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quiet on purpose. Both are worth reaching, neither is a daily errand.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onPetClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(11.dp),
                    border = BorderStroke(1.dp, Color(0xFFE7E5E4)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White.copy(alpha = 0.55f),
                        contentColor = Color(0xFF44403C),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Pets,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(7.dp))
                    Text("Djur", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onWalletClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(11.dp),
                    border = BorderStroke(1.dp, Color(0xFFE7E5E4)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White.copy(alpha = 0.55f),
                        contentColor = Color(0xFF44403C),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Wallet,
                        contentDescription = null,
                        tint = Color(0xFF78716C),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(7.dp))
                    Text("Plånbok", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}


@Composable
private fun RenameMemberDialog(
    member: FamilyMemberResponse,
    onDismiss: () -> Unit,
    onRenamed: () -> Unit,
) {
    var name by remember { mutableStateOf(member.name) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Byt namn") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Namn") },
                    singleLine = true,
                    enabled = !saving,
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && name.isNotBlank() && name.trim() != member.name,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                ApiClient.familyMembersApi.updateMember(
                                    memberId = member.id,
                                    body = UpdateFamilyMemberRequest(name = name.trim()),
                                )
                            }
                            onRenamed()
                        } catch (e: Exception) {
                            error = ApiErrors.message(e, "Kunde inte byta namn.")
                        } finally {
                            saving = false
                        }
                    }
                },
            ) {
                Text(if (saving) "Sparar…" else "Spara")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Avbryt") }
        },
    )
}

/**
 * Deleting a member takes their chores, completions, XP, pet history and wallet
 * records with them, and none of it can be restored. The name has to be typed to
 * confirm -- a plain "are you sure" is too easy to tap past for something this
 * final, especially with the option sitting in a menu next to "Byt namn".
 */
@Composable
private fun DeleteMemberDialog(
    member: FamilyMemberResponse,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val confirmed = typed.trim().equals(member.name.trim(), ignoreCase = true)

    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("Ta bort ${member.name}?") },
        text = {
            Column {
                Text(
                    "Det här tar bort ${member.name} ur familjen, tillsammans med alla " +
                        "sysslor, XP, djur och plånbokshistorik. Det går inte att ångra.",
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Skriv ${member.name} för att bekräfta:",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    enabled = !deleting,
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmed && !deleting,
                onClick = {
                    deleting = true
                    error = null
                    scope.launch {
                        try {
                            val response = withContext(Dispatchers.IO) {
                                ApiClient.familyMembersApi.deleteMember(member.id)
                            }
                            if (!response.isSuccessful) {
                                throw IllegalStateException("HTTP ${response.code()}")
                            }
                            onDeleted()
                        } catch (e: Exception) {
                            error = ApiErrors.message(e, "Kunde inte ta bort medlemmen.")
                        } finally {
                            deleting = false
                        }
                    }
                },
            ) {
                Text(
                    text = if (deleting) "Tar bort…" else "Ta bort",
                    color = if (confirmed) Color(0xFFC53030) else Color(0xFF9CA3AF),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) { Text("Avbryt") }
        },
    )
}


/**
 * A parent, as a row rather than a card.
 *
 * Adults have nothing to do here daily -- no pet, no chore progress, no wallet -- but
 * their card was built to the same recipe as a child's, so a family of three children
 * and two parents read as five equally important things. Everything an adult row
 * offers is once-in-a-while work, so all of it lives in the overflow.
 *
 * The signed-in parent gets no delete option -- removing your own account from the
 * device you are holding is never what you meant, and there is no way back.
 */
@Composable
private fun AdultRow(
    name: String,
    isCurrentUser: Boolean,
    hasPairedDevice: Boolean,
    cardPastel: Color,
    textPrimary: Color,
    textSecondary: Color,
    buttonPastel: Color,
    buttonOnPastel: Color,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onInviteClick: () -> Unit,
    onPasswordClick: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = cardPastel.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E7FF)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.trim().take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF3730A3),
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                )
                Text(
                    text = if (hasPairedDevice) "Förälder" else "Förälder · ingen telefon kopplad",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary,
                )
            }
            // Replaces "(du)" appended to the name, which grew the one string a parent
            // scans for.
            if (isCurrentUser) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(buttonPastel)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "Du",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = buttonOnPastel,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Fler val för $name",
                        tint = textSecondary,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Byt namn") },
                        onClick = {
                            menuOpen = false
                            onRenameClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isCurrentUser) "Byt lösenord" else "Sätt nytt lösenord") },
                        onClick = {
                            menuOpen = false
                            onPasswordClick()
                        },
                    )
                    if (!isCurrentUser) {
                        DropdownMenuItem(
                            text = { Text("Koppla telefon") },
                            onClick = {
                                menuOpen = false
                                onInviteClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Ta bort", color = Color(0xFFC53030)) },
                            onClick = {
                                menuOpen = false
                                onDeleteClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The family's day, as one figure.
 *
 * This replaced a card of the same weight as every other card, carrying two sentences
 * of bodySmall. The screen had no dominant element at all -- five card groups all
 * shouting at the same volume -- so nothing read as the point of it.
 *
 * The card is the way into the family task list, which is what let the full-width
 * "Familjens uppgifter idag" button go away.
 */
@Composable
private fun FamilyTodayCard(
    done: Int,
    total: Int,
    childrenWithPet: Int,
    childCount: Int,
    cardPastel: Color,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    onClick: () -> Unit,
) {
    val fraction = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardPastel),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "IDAG I FAMILJEN",
                    modifier = Modifier.weight(1f),
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = Color(0xFF78716C),
                )
                Text(
                    text = "Alla uppgifter",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = done.toString(),
                    fontSize = 40.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Spacer(modifier = Modifier.size(7.dp))
                Text(
                    text = if (total > 0) "av $total uppgifter" else "inga uppgifter planerade",
                    modifier = Modifier.padding(bottom = 5.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = textSecondary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Drawn rather than a LinearProgressIndicator so the fill can carry the
            // app's own background pastels at full strength.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFE7E5E4)),
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF6366F1), Color(0xFF0EA5E9)),
                                )
                            ),
                    )
                }
            }
            if (childCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "$childrenWithPet av $childCount barn har ett aktivt djur",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary,
                )
            }
        }
    }
}

/**
 * Today's nudge, at a volume below the figure above it.
 *
 * Same information the "Förslag idag" card carried, without the card: it was drawn to
 * the same recipe as everything else on the screen, which is how a suggestion came to
 * look as important as the family's whole day.
 */
@Composable
private fun SuggestionStrip(
    childName: String,
    message: String,
    textPrimary: Color,
    textSecondary: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF7ED))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = null,
            tint = Color(0xFFB45309),
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.size(9.dp))
        Text(
            // The name leads and carries the weight, because that is what a parent is
            // scanning for.
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = textPrimary)) {
                    append(childName)
                }
                append(" ")
                append(message)
            },
            style = MaterialTheme.typography.bodySmall,
            color = textSecondary,
        )
    }
}

/**
 * The setup guide, folded to one row.
 *
 * Expanded it is four steps with subtitles -- around 320.dp, which is most of a phone
 * screen -- and it kept that space long after it stopped being the thing a parent came
 * for. Tapping the row opens the full card again; "Dölj" inside that card is still the
 * only way to be rid of it permanently.
 */
@Composable
private fun GetStartedStrip(
    doneCount: Int,
    nextLabel: String?,
    cardPastel: Color,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    onExpand: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardPastel)
            .clickable(onClick = onExpand)
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (index < doneCount) accent else Color(0xFFD6D3D1)),
                )
            }
        }
        Spacer(modifier = Modifier.size(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Kom igång — $doneCount av 4 klara",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
            )
            nextLabel?.let {
                Text(
                    text = "Nästa: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondary,
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Visa alla steg",
            tint = Color(0xFFA8A29E),
            modifier = Modifier.size(18.dp),
        )
    }
}


/**
 * Sets a member's password.
 *
 * This is the entire account recovery story until email reset exists: a parent who
 * cannot log in has no other route back, and the alternative is editing the database
 * by hand. So the other parent does it for them.
 *
 * Existing sessions are left alone. Whoever is locked out has none, and signing the
 * other parent's phone out would achieve nothing.
 */
@Composable
private fun ChangePasswordDialog(
    member: FamilyMemberResponse,
    isSelf: Boolean,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Trimmed here as well as server-side, so the length shown to the user is the
    // length that gets hashed. See FamilyService.
    val trimmed = password.trim()
    val tooShort = trimmed.length < 6
    val mismatch = confirm.isNotEmpty() && confirm.trim() != trimmed
    val canSave = !saving && !tooShort && !mismatch && confirm.isNotEmpty()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (isSelf) "Byt ditt lösenord" else "Nytt lösenord för ${member.name}") },
        text = {
            Column {
                if (!isSelf) {
                    Text(
                        text = "${member.name} kan logga in med det nya lösenordet direkt. " +
                            "Kom överens om vad det ska vara och låt hen byta det sedan.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Nytt lösenord") },
                    singleLine = true,
                    enabled = !saving,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text("Upprepa lösenordet") },
                    singleLine = true,
                    enabled = !saving,
                    isError = mismatch,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(modifier = Modifier.height(8.dp))
                val hint = when {
                    mismatch -> "Lösenorden är inte lika."
                    password.isNotEmpty() && tooShort -> "Minst 6 tecken."
                    else -> null
                }
                hint?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFC53030))
                }
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                ApiClient.familyMembersApi.updatePassword(
                                    memberId = member.id,
                                    body = UpdatePasswordRequest(password = trimmed),
                                )
                            }
                            onChanged()
                        } catch (e: Exception) {
                            error = ApiErrors.message(e, "Kunde inte spara lösenordet.")
                        } finally {
                            saving = false
                        }
                    }
                },
            ) {
                Text(if (saving) "Sparar…" else "Spara")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Avbryt") }
        },
    )
}


/**
 * The get-started card.
 *
 * A checklist that opens the real dialogs, not a tour. Coach marks would need anchor
 * coordinates that break under rotation, large fonts and tablets, and would have to be
 * maintained twice -- once in Compose and once in SwiftUI. A card that launches dialogs
 * the app already ships has no positioning logic at all.
 *
 * Nothing here is a step counter. Each row's state is read from the family itself, so
 * the card cannot disagree with reality.
 */
@Composable
private fun GetStartedCard(
    hasChild: Boolean,
    hasChores: Boolean,
    hasPairedDevice: Boolean,
    hasPet: Boolean,
    cardPastel: Color,
    textPrimary: Color,
    textSecondary: Color,
    onAddChild: () -> Unit,
    onAddChores: () -> Unit,
    onPairDevice: () -> Unit,
    onSeePet: () -> Unit,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Pairing is skippable on purpose: plenty of younger children have no phone, and a
    // checklist that cannot be completed is worse than none. It is not counted here.
    val required = listOf(hasChild, hasChores)
    val doneCount = required.count { it } + listOf(hasPairedDevice, hasPet).count { it }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardPastel),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        // Tapping the heading folds the guide back to a single row.
                        // "Dölj" beside it is permanent; this is not.
                        .clickable(onClick = onCollapse),
                ) {
                    Text(
                        text = "Kom igång",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                    )
                    Text(
                        text = "$doneCount av 4 klara",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary,
                    )
                }
                TextButton(onClick = onDismiss) { Text("Dölj") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            GetStartedRow(
                done = hasChild,
                title = "Lägg till ett barn",
                subtitle = "Inget fungerar förrän det finns ett barn i familjen.",
                actionLabel = "Lägg till",
                onClick = onAddChild,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
            )
            GetStartedRow(
                done = hasChores,
                // Enabled only once a child exists, since the chores belong to one.
                enabled = hasChild,
                title = "Lägg till dagliga sysslor",
                subtitle = "Välj ålder när du lägger till barnet och du får förslag direkt.",
                actionLabel = "Lägg till",
                onClick = onAddChores,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
            )
            GetStartedRow(
                done = hasPairedDevice,
                enabled = hasChild,
                title = "Koppla barnets telefon",
                subtitle = "Hoppa över det här om barnet inte har någon egen telefon — du kan visa barnets vy från ditt eget konto.",
                actionLabel = "Visa kod",
                onClick = onPairDevice,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
            )
            GetStartedRow(
                done = hasPet,
                enabled = hasChild,
                title = "Välj ett ägg",
                subtitle = "Barnet får ett djur att ta hand om — det är hela poängen.",
                actionLabel = "Öppna",
                onClick = onSeePet,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                isLast = true,
            )
        }
    }
}

@Composable
private fun GetStartedRow(
    done: Boolean,
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    enabled: Boolean = true,
    isLast: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp, end = 12.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (done) Color(0xFF4ADE80) else Color.Transparent)
                .border(
                    width = 2.dp,
                    color = if (done) Color(0xFF4ADE80) else Color(0xFFCBD5E1),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Text("✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (done) FontWeight.Normal else FontWeight.Medium,
                color = if (done) textSecondary else textPrimary,
            )
            if (!done) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary,
                )
            }
        }
        if (!done) {
            TextButton(onClick = onClick, enabled = enabled) { Text(actionLabel) }
        }
    }
    if (!isLast) {
        HorizontalDivider(color = textSecondary.copy(alpha = 0.15f))
    }
}

/**
 * The redesigned dashboard, without a login.
 *
 * Reaching the real screen means signing in as a parent with children who have pets
 * and chores, which is a slow way to check whether a card is 4.dp too tall. Everything
 * here is layout only -- no API, no session -- so Android Studio renders it directly.
 */
@Preview(name = "Föräldravy", widthDp = 390, heightDp = 1000, showBackground = true)
@Composable
private fun AdultDashboardPreview() {
    val cardPastel = Color(0xFFFFFBEB)
    val textPrimary = Color(0xFF1C1917)
    val textSecondary = Color(0xFF57534E)
    val buttonPastel = Color(0xFFBAE6FD)
    val buttonOnPastel = Color(0xFF0C4A6E)

    val ella = ChildSummary(
        memberId = "1",
        memberName = "Ella",
        todaysDone = 3,
        todaysTotal = 5,
        hasPet = true,
        petType = "dragon",
        growthStage = 3,
        nextTaskTitle = "Duka bordet",
    )
    val oskar = ChildSummary(
        memberId = "2",
        memberName = "Oskar",
        todaysDone = 5,
        todaysTotal = 5,
        hasPet = true,
        petType = "cat",
        growthStage = 2,
        nextTaskTitle = null,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFFE0E7FF), Color(0xFFE0F2FE)))
            )
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FamilyTodayCard(
            done = 8,
            total = 10,
            childrenWithPet = 2,
            childCount = 2,
            cardPastel = cardPastel,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accent = buttonOnPastel,
            onClick = {},
        )
        SuggestionStrip(
            childName = "Ella",
            message = "behöver påminnas om \"Duka bordet\" för att mata sitt djur.",
            textPrimary = textPrimary,
            textSecondary = textSecondary,
        )
        GetStartedStrip(
            doneCount = 2,
            nextLabel = "koppla barnets telefon",
            cardPastel = cardPastel,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accent = buttonOnPastel,
            onExpand = {},
        )
        Text(
            text = "Mina barn",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        ChildCard(
            name = "Ella",
            hasPairedDevice = true,
            cardPastel = cardPastel,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            buttonPastel = buttonPastel,
            buttonOnPastel = buttonOnPastel,
            summary = ella,
            onPetClick = {},
            onWalletClick = {},
            onTasksClick = {},
            onInviteClick = {},
        )
        // The unpaired case, which is the only time the invite row appears.
        ChildCard(
            name = "Oskar",
            hasPairedDevice = false,
            cardPastel = cardPastel,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            buttonPastel = buttonPastel,
            buttonOnPastel = buttonOnPastel,
            summary = oskar,
            onPetClick = {},
            onWalletClick = {},
            onTasksClick = {},
            onInviteClick = {},
        )
        Text(
            text = "Vuxna",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        AdultRow(
            name = "Patrik",
            isCurrentUser = true,
            hasPairedDevice = true,
            cardPastel = cardPastel,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            buttonPastel = buttonPastel,
            buttonOnPastel = buttonOnPastel,
            onRenameClick = {},
            onDeleteClick = {},
            onInviteClick = {},
        )
        AdultRow(
            name = "Jessica",
            isCurrentUser = false,
            hasPairedDevice = false,
            cardPastel = cardPastel,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            buttonPastel = buttonPastel,
            buttonOnPastel = buttonOnPastel,
            onRenameClick = {},
            onDeleteClick = {},
            onInviteClick = {},
        )
    }
}

/** How close to the end of the trial before the dashboard mentions it. */
private const val TRIAL_NAG_DAYS = 30L

/**
 * Says something about billing only when there is something worth saying.
 *
 * The dashboard was deliberately rebuilt around a single dominant element, so a
 * permanent billing strip would undo that work. A family comfortably inside their
 * trial, one that is paying, and one that has been comped all see nothing at all.
 *
 * Nothing here decides access. The server does that, and it applies the same answer
 * to every write regardless of what this banner happens to show.
 */
@Composable
private fun SubscriptionBanner(
    status: SubscriptionStatusResponse,
    textSecondary: Color,
) {
    // A comped family is never nagged, whatever the trial clock says -- they were
    // given free access on purpose.
    if (status.comped) return

    val urgent = status.status == "EXPIRED"
    val headline: String
    val detail: String

    when {
        status.status == "EXPIRED" -> {
            headline = "Provperioden har gått ut"
            detail = "Förnya för att lägga till sysslor och familjemedlemmar igen. Barnens sysslor och djur fungerar som vanligt."
        }
        status.status == "GRACE" -> {
            headline = "Betalningen gick inte igenom"
            detail = "Google försöker igen. Appen fungerar som vanligt under tiden."
        }
        status.inTrial && status.trialDaysRemaining <= TRIAL_NAG_DAYS -> {
            headline = when (status.trialDaysRemaining) {
                0L -> "Provperioden slutar idag"
                1L -> "1 dag kvar av provperioden"
                else -> "${status.trialDaysRemaining} dagar kvar av provperioden"
            }
            detail = "Sedan kostar KidQuest 29 kr per månad för hela familjen."
        }
        else -> return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (urgent) Color(0xFFFEF2F2) else Color(0xFFFFF7ED))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (urgent) Icons.Default.ErrorOutline else Icons.Default.Schedule,
            contentDescription = null,
            tint = if (urgent) Color(0xFF991B1B) else Color(0xFFB45309),
            modifier = Modifier
                .padding(top = 1.dp)
                .size(17.dp),
        )
        Spacer(modifier = Modifier.size(9.dp))
        Column {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (urgent) Color(0xFF991B1B) else Color(0xFF92400E),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary,
            )
        }
    }
}

/**
 * The banner's three states, since none of them can be reached by running the app:
 * the trial has months left, nobody is paying yet, and a comped family shows nothing.
 */
@Preview(name = "Prenumerationsbanner", widthDp = 390, showBackground = true)
@Composable
private fun SubscriptionBannerPreview() {
    fun status(status: String, days: Long, inTrial: Boolean) = SubscriptionStatusResponse(
        status = status,
        entitled = status != "EXPIRED",
        trialEndsAt = null,
        trialDaysRemaining = days,
        inTrial = inTrial,
        currentPeriodEnd = null,
        platform = "ANDROID",
        cancelAtPeriodEnd = false,
        comped = false,
    )

    Column(
        modifier = Modifier
            .background(Brush.verticalGradient(listOf(Color(0xFFE0E7FF), Color(0xFFE0F2FE))))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SubscriptionBanner(status("TRIAL", 12, true), Color(0xFF57534E))
        SubscriptionBanner(status("TRIAL", 1, true), Color(0xFF57534E))
        SubscriptionBanner(status("TRIAL", 0, true), Color(0xFF57534E))
        SubscriptionBanner(status("GRACE", 0, false), Color(0xFF57534E))
        SubscriptionBanner(status("EXPIRED", 0, false), Color(0xFF57534E))
    }
}

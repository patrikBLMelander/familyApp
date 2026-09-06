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
import androidx.compose.material.icons.filled.CalendarMonth
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
import se.kidquest.app.network.RecurringAllowanceResponse
import se.kidquest.app.network.FamilyMemberResponse
import se.kidquest.app.network.SubscriptionStatusResponse
import se.kidquest.app.network.UpdateFamilyMemberRequest
import se.kidquest.app.network.UpdatePasswordRequest
import se.kidquest.app.billing.BillingConfig
import se.kidquest.app.pet.PetImages
import se.kidquest.app.pet.PetTheme
import se.kidquest.app.pet.PetVisual
import se.kidquest.app.session.PrefsStore
import se.kidquest.app.session.TokenStore
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Switch
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import se.kidquest.app.theme.LocalSeasonPalette
import se.kidquest.app.theme.SeasonPalette
import se.kidquest.app.ui.theme.KidQuestTheme

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
    onOpenPaywall: () -> Unit = {},
    onFamilyDeleted: () -> Unit = {},
    /** Null while nobody has chosen, in which case the phone decides. */
    darkMode: Boolean? = null,
    onSetDarkMode: (Boolean) -> Unit = {},
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
    // Barnlåsets kod. Sätts i barnvyns banderoll där behovet uppstår, ändras härifrån.
    var parentPin by remember { mutableStateOf<String?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }
    val pinScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { parentPin = TokenStore.parentPin() }
    // The family already named itself at registration. Falling back to "Min familj"
    // rather than blank, because the header is drawn before this arrives.
    var familyName by remember { mutableStateOf<String?>(null) }
    var confirmingFamilyDeletion by remember { mutableStateOf(false) }

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
                            val allowanceDeferred = async {
                                kotlin.runCatching {
                                    ApiClient.recurringAllowanceApi.get(child.id)
                                        .takeIf { it.isSuccessful }?.body()
                                }.getOrNull()
                            }

                            val chores = choresDeferred.await()
                            val petResp = petDeferred.await()
                            val allowance = allowanceDeferred.await()?.takeIf { it.active }

                            val total = chores.size
                            val done = chores.count { it.completed }
                            val pet = petResp?.takeIf { it.isSuccessful }?.body()

                            child.id to ChildSummary(
                                memberId = child.id,
                                memberName = child.name,
                                todaysDone = done,
                                todaysTotal = total,
                                hasPet = pet != null,
                                petType = pet?.petType,
                                growthStage = pet?.growthStage ?: 1,
                                allowanceNote = allowance?.let { describeAllowance(it) },
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

        familyName = kotlin.runCatching {
            adults.firstOrNull()?.familyId?.let { id ->
                ApiClient.familyApi.getFamily(id).takeIf { it.isSuccessful }?.body()?.name
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    // The old lavender-to-sky gradient competed with the animals, which are the only
    // thing the children care about. The season carries the colour now and the pets
    // are the brightest thing on the screen again.
    val palette = LocalSeasonPalette.current
    val cardPastel = palette.surface
    val textPrimary = palette.ink
    val textSecondary = palette.inkSoft
    val buttonPastel = palette.accent
    val buttonOnPastel = palette.onAccent
    val listState = rememberLazyListState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
    ) { innerPadding ->
      Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.pageBg)
                .padding(innerPadding),
        ) {
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.pageBg)
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
                    modifier = Modifier.padding(24.dp),
                )
                return@Scaffold
            }

            val summaryList = children.mapNotNull { childSummaries[it.id] }
            val totalTasksToday = summaryList.sumOf { it.todaysTotal }
            val completedTasksToday = summaryList.sumOf { it.todaysDone }
            val childrenWithPet = summaryList.count { it.hasPet }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                item {
                    SeasonHeader(
                        done = completedTasksToday,
                        total = totalTasksToday,
                        palette = palette,
                        onClick = onFamilyTasks,
                    )
                }

                subscription?.let { sub ->
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            SubscriptionBanner(
                                status = sub,
                                textSecondary = textSecondary,
                                // Only a route worth offering once there is something on the
                                // other end of it. No key, no paywall, no dead tap.
                                onClick = if (BillingConfig.isConfigured) onOpenPaywall else null,
                            )
                        }
                    }
                }

                if (!onboardingDismissed) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                                    accent = palette.accent,
                                    onExpand = { checklistExpanded = true },
                                )
                            }
                        }
                    }
                }

                if (children.isEmpty()) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                    }
                } else {
                    items(children, key = { it.id }) { child ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                }

                if (adults.isNotEmpty()) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                text = "Vuxna",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(adults, key = { it.id }) { adult ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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

                if (showAddMemberHint && children.isEmpty()) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = palette.surface),
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
                    }
                }

                // Last in the list rather than pinned to the bottom of the screen.
                // Adding a family member happens a handful of times ever, and until now
                // it held 52dp of every screenful for the rest of the app's life.
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        OutlinedButton(
                            onClick = {
                                onDismissAddMemberHint()
                                onAddFamilyMember()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(top = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, palette.accent),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accent),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Lägg till familjemedlem", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (confirmingFamilyDeletion) {
                DeleteFamilyDialog(
                    onDismiss = { confirmingFamilyDeletion = false },
                    onDeleted = {
                        confirmingFamilyDeletion = false
                        onFamilyDeleted()
                    },
                )
            }

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


        }

        // Drawn over the list rather than above it, so the photograph can run up
        // behind it and the name and the menu never scroll out of reach. The menu is
        // the only way to Prenumeration and Logga ut.
        DashboardTopBar(
            familyName = familyName ?: "Min familj",
            palette = palette,
            // Fully faded in by the time the photograph's own title would have left.
            collapsed = collapseFraction(listState),
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            menuOpen = topMenuOpen,
            onMenuOpenChange = { topMenuOpen = it },
            darkMode = darkMode,
            onSetDarkMode = onSetDarkMode,
            showSubscription = BillingConfig.isConfigured,
            onOpenPaywall = onOpenPaywall,
            onLogout = onLogout,
            hasParentPin = parentPin != null,
            onChangePin = { showPinDialog = true },
            onDeleteFamily = { confirmingFamilyDeletion = true },
        )

        if (showPinDialog) {
            ParentPinDialog(
                purpose = PinPurpose.CHANGE,
                season = palette,
                onDismiss = { showPinDialog = false },
                onPinChosen = { nyKod ->
                    showPinDialog = false
                    parentPin = nyKod
                    pinScope.launch { TokenStore.setParentPin(nyKod) }
                },
            )
        }
      }
    }

    inviteChild?.let { child ->
        ChildInviteDialog(
            child = child,
            onDismiss = { inviteChild = null },
        )
    }
}

/** Height of the seasonal band. The bar overlaps its top 56dp. */
private val HEADER_HEIGHT = 196.dp
private val TOP_BAR_HEIGHT = 56.dp

/**
 * How far the header has scrolled away, 0f to 1f.
 *
 * Read as derived state so scrolling recomposes the bar and nothing else.
 */
@Composable
private fun collapseFraction(listState: LazyListState): Float {
    val travel = with(LocalDensity.current) { (HEADER_HEIGHT - TOP_BAR_HEIGHT).toPx() }
    val fraction by remember(travel) {
        derivedStateOf {
            when {
                listState.firstVisibleItemIndex > 0 -> 1f
                travel <= 0f -> 1f
                else -> (listState.firstVisibleItemScrollOffset / travel).coerceIn(0f, 1f)
            }
        }
    }
    return fraction
}

/**
 * The season as a colour field, with the family's day on top of it.
 *
 * The seasonal artwork went here first, at full size, and it read as clutter: the
 * paintings are detailed, and detail directly above a list of children competes with
 * them. What survived is the colour, which is what carried the season anyway. The
 * paintings stay where they started, behind each pet portrait, at the size that suits
 * them -- so the season still appears twice on the screen.
 *
 * A blurred photograph would have been the other answer, but Modifier.blur needs
 * API 31 and this app runs from 24.
 */
@Composable
private fun SeasonHeader(
    done: Int,
    total: Int,
    palette: SeasonPalette,
    onClick: () -> Unit,
) {
    val fraction = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .background(
                Brush.verticalGradient(
                    0f to palette.headerTop,
                    0.52f to palette.headerMid,
                    1f to palette.headerBottom,
                ),
            )
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "IDAG I FAMILJEN",
                    modifier = Modifier.weight(1f),
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = Color.White.copy(alpha = 0.84f),
                )
                Text(
                    text = "Alla uppgifter",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = done.toString(),
                    fontSize = 40.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.size(7.dp))
                Text(
                    text = if (total > 0) "av $total uppgifter" else "inga uppgifter planerade",
                    modifier = Modifier.padding(bottom = 5.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Spacer(modifier = Modifier.height(9.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.34f)),
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White),
                    )
                }
            }
        }
    }
}

/**
 * The family's name and the overflow menu, laid over the header.
 *
 * It starts transparent with white text on the photograph and fades into a solid bar
 * as the photograph scrolls away. Pinned rather than scrolling, because this menu is
 * the only route to Prenumeration, Logga ut and deleting the family.
 */
@Composable
private fun DashboardTopBar(
    familyName: String,
    palette: SeasonPalette,
    collapsed: Float,
    modifier: Modifier = Modifier,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    darkMode: Boolean?,
    onSetDarkMode: (Boolean) -> Unit,
    showSubscription: Boolean,
    onOpenPaywall: () -> Unit,
    onLogout: () -> Unit,
    onDeleteFamily: () -> Unit,
    /** Menypunkten finns bara när en kod är satt; se kommentaren vid den. */
    hasParentPin: Boolean,
    onChangePin: () -> Unit,
) {
    // White on the photograph, the season's ink once the bar is solid.
    val titleColour = lerp(Color.White, palette.ink, collapsed)
    val iconColour = lerp(Color.White, palette.inkSoft, collapsed)
    val systemDark = isSystemInDarkTheme()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TOP_BAR_HEIGHT)
            .background(palette.pageBg.copy(alpha = collapsed))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = familyName,
            modifier = Modifier.weight(1f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = titleColour,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box {
            IconButton(onClick = { onMenuOpenChange(true) }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Fler val",
                    tint = iconColour,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onMenuOpenChange(false) },
            ) {
                // Deliberately a switch rather than a system setting only. A parent who
                // reads this in bed wants it dark whatever the phone is doing, and until
                // they touch it the phone still decides.
                DropdownMenuItem(
                    text = { Text("Mörkt läge") },
                    trailingIcon = {
                        Switch(
                            checked = darkMode ?: systemDark,
                            onCheckedChange = { onSetDarkMode(it) },
                        )
                    },
                    onClick = { onSetDarkMode(!(darkMode ?: systemDark)) },
                )
                HorizontalDivider()
                // The banner only appears in the last 30 days of the trial, so without
                // this a parent who decides in week two has no way to pay. It is also
                // the only route to the paywall for anyone testing a purchase.
                if (showSubscription) {
                    DropdownMenuItem(
                        text = { Text("Prenumeration") },
                        onClick = {
                            onMenuOpenChange(false)
                            onOpenPaywall()
                        },
                    )
                }
                // Bara när en kod finns. Att SÄTTA den hör hemma i barnvyns banderoll,
                // där behovet uppstår -- ingen öppnar en meny för att leta efter ett lås
                // de inte vet finns. Att ÄNDRA den hör hemma här: den som ändrar vet
                // redan att koden existerar.
                if (hasParentPin) {
                    DropdownMenuItem(
                        text = { Text("Barnlåsets kod") },
                        onClick = {
                            onMenuOpenChange(false)
                            onChangePin()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Logga ut") },
                    onClick = {
                        onMenuOpenChange(false)
                        onLogout()
                    },
                )
                HorizontalDivider()
                // Both stores require this to be reachable in the app, and Apple
                // enforces it. Last in the menu and in red, because it is the one
                // entry here that cannot be undone.
                DropdownMenuItem(
                    text = { Text("Ta bort familjen", color = palette.danger) },
                    onClick = {
                        onMenuOpenChange(false)
                        onDeleteFamily()
                    },
                )
            }
        }
    }
}

/**
 * The eight palettes, side by side.
 *
 * Seven of them cannot be reached by running the app: the season comes from the clock,
 * so autumn arrives on 1 September whether or not anyone has looked at it. These exist
 * so a season can be judged before it happens rather than after a family reports it.
 */
@Preview(name = "Vår", widthDp = 820, heightDp = 470, showBackground = true)
@Composable
private fun SeasonSpringPreview() = SeasonPair("spring")

@Preview(name = "Sommar", widthDp = 820, heightDp = 470, showBackground = true)
@Composable
private fun SeasonSummerPreview() = SeasonPair("summer")

@Preview(name = "Höst", widthDp = 820, heightDp = 470, showBackground = true)
@Composable
private fun SeasonAutumnPreview() = SeasonPair("autumn")

@Preview(name = "Vinter", widthDp = 820, heightDp = 470, showBackground = true)
@Composable
private fun SeasonWinterPreview() = SeasonPair("winter")

/** Light and dark of one season together, which is the comparison that matters. */
@Composable
private fun SeasonPair(season: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf(false, true).forEach { dark ->
            Box(modifier = Modifier.weight(1f)) {
                KidQuestTheme(dark = dark, season = season) {
                    SeasonSample()
                }
            }
        }
    }
}

/**
 * Enough of the screen to judge a palette: the band, the chips, the unpaired-phone row
 * and the primary action -- every place a colour has to hold text.
 */
@Composable
private fun SeasonSample() {
    val palette = LocalSeasonPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.pageBg),
    ) {
        SeasonHeader(done = 8, total = 10, palette = palette, onClick = {})
        Box(modifier = Modifier.padding(12.dp)) {
            ChildCard(
                name = "Ella",
                hasPairedDevice = false,
                cardPastel = palette.surface,
                textPrimary = palette.ink,
                textSecondary = palette.inkSoft,
                buttonPastel = palette.accent,
                buttonOnPastel = palette.onAccent,
                summary = ChildSummary(
                    memberId = "1",
                    memberName = "Ella",
                    todaysDone = 5,
                    todaysTotal = 5,
                    hasPet = true,
                    petType = "dragon",
                    growthStage = 3,
                    allowanceNote = "50 kr varje fredag",
                ),
                onPetClick = {},
                onWalletClick = {},
                onTasksClick = {},
                onInviteClick = {},
            )
        }
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
    /** "50 kr varje fredag", or null when no automatic allowance is running. */
    val allowanceNote: String?,
)

/**
 * The standing arrangement in one line, from the parent's side of it.
 *
 * The level kind deliberately names no figure: the amount is not decided until the
 * month ends, and a number here would read as a promise.
 */
private fun describeAllowance(schedule: RecurringAllowanceResponse): String {
    val day = schedule.dayOfMonth ?: 1
    val ordinal = if (day % 10 in 1..2 && day != 11 && day != 12) "$day:a" else "$day:e"
    return when (schedule.kind) {
        "WEEKLY" -> {
            val weekday = when (schedule.weekday) {
                1 -> "måndag"; 2 -> "tisdag"; 3 -> "onsdag"; 4 -> "torsdag"
                5 -> "fredag"; 6 -> "lördag"; else -> "söndag"
            }
            "${schedule.amount ?: 0} kr varje $weekday"
        }
        "MONTHLY" -> "${schedule.amount ?: 0} kr den $ordinal"
        else -> "Efter nivå den $ordinal"
    }
}
// A streakDays field used to live here, hardcoded to 0, behind a `> 0` check in the
// card -- so the streak line it fed could never appear. Dropped rather than kept as
// dead weight; it comes back the day the backend actually reports a streak.
//
// nextTaskTitle went the same way with the suggestion strip: the strip named the
// child furthest from done and said what was left, directly above the cards that
// already show both. Two readings of one fact, competing for the same fold.

/**
 * One shape for every small label on a child's card, so the colour is the only thing
 * that varies and it varies for a reason.
 */
@Composable
private fun StatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    background: Color,
    ink: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        )
    }
}

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
    val season = LocalSeasonPalette.current
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
                    // The chip under the name always answers the one question this
                    // screen exists for: is this child done today. It used to appear
                    // only when they were, and the slot underneath it carried the
                    // allowance in the same success green -- so a child with chores
                    // left wore a green badge about pocket money while the child who
                    // WAS done wore an identical green badge about being done. The
                    // same colour making opposite claims, and wrong on the only card
                    // where it mattered.
                    if (summary != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        when {
                            allDoneToday -> StatusChip(
                                icon = Icons.Default.Check,
                                text = "Allt klart idag",
                                background = season.goodBg,
                                ink = season.goodInk,
                            )
                            summary.todaysTotal == 0 -> Text(
                                text = "Inga sysslor planerade idag",
                                style = MaterialTheme.typography.labelSmall,
                                color = textSecondary,
                            )
                            else -> StatusChip(
                                icon = Icons.Default.Schedule,
                                text = "${summary.todaysTotal - summary.todaysDone} kvar idag",
                                background = season.warnBg,
                                ink = season.warnStrong,
                            )
                        }
                    }
                    // Setting it up belongs in the wallet. Seeing that it is on belongs
                    // where a parent already looks every day, so nobody has to remember
                    // what they chose back in the summer.
                    // Quiet on purpose: a standing arrangement, not anything about
                    // today. Green here was borrowing a claim it had no right to.
                    summary?.allowanceNote?.let { note ->
                        Spacer(modifier = Modifier.height(6.dp))
                        StatusChip(
                            icon = Icons.Default.CalendarMonth,
                            text = note,
                            background = season.tipBg,
                            ink = season.inkSoft,
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
                            text = { Text("Ta bort", color = season.danger) },
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
                        .background(season.warnBg)
                        .clickable(onClick = onInviteClick)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Ingen telefon kopplad ännu",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = season.warnInk,
                    )
                    Text(
                        text = "Bjud in",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = season.warnStrong,
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
                    border = BorderStroke(1.dp, season.outlineEdge),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = season.outlineBg,
                        contentColor = season.outlineInk,
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
                    border = BorderStroke(1.dp, season.outlineEdge),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = season.outlineBg,
                        contentColor = season.outlineInk,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Wallet,
                        contentDescription = null,
                        tint = season.inkFaint,
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
    val season = LocalSeasonPalette.current
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
                    color = if (confirmed) season.danger else season.inkFaint,
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
    val season = LocalSeasonPalette.current
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
                    .background(season.calBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.trim().take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = season.calInk,
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
                            text = { Text("Ta bort", color = season.danger) },
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
    val season = LocalSeasonPalette.current
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
                        .background(if (index < doneCount) accent else season.track),
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
            tint = season.inkFaint,
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
    val season = LocalSeasonPalette.current
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
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = season.danger)
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
    val season = LocalSeasonPalette.current
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
                .background(if (done) season.goodInk else Color.Transparent)
                .border(
                    width = 2.dp,
                    color = if (done) season.goodInk else season.track,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Text("✓", fontSize = 12.sp, color = season.pageBg, fontWeight = FontWeight.Bold)
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
    val palette = LocalSeasonPalette.current
    val cardPastel = palette.surface
    val textPrimary = palette.ink
    val textSecondary = palette.inkSoft
    val buttonPastel = palette.accent
    val buttonOnPastel = palette.onAccent

    val ella = ChildSummary(
        memberId = "1",
        memberName = "Ella",
        todaysDone = 3,
        todaysTotal = 5,
        hasPet = true,
        petType = "dragon",
        growthStage = 3,
        allowanceNote = "Peng efter nivå den 1:a",
    )
    val oskar = ChildSummary(
        memberId = "2",
        memberName = "Oskar",
        todaysDone = 5,
        todaysTotal = 5,
        hasPet = true,
        petType = "cat",
        growthStage = 2,
        allowanceNote = null,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.pageBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SeasonHeader(done = 8, total = 10, palette = palette, onClick = {})
        GetStartedStrip(
            doneCount = 2,
            nextLabel = "koppla barnets telefon",
            cardPastel = cardPastel,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accent = palette.accent,
            onExpand = {},
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
    onClick: (() -> Unit)? = null,
) {
    val season = LocalSeasonPalette.current
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
            .background(if (urgent) season.warnBg else season.warnBg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (urgent) Icons.Default.ErrorOutline else Icons.Default.Schedule,
            contentDescription = null,
            tint = if (urgent) season.danger else season.warnStrong,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(17.dp),
        )
        Spacer(modifier = Modifier.size(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (urgent) season.danger else season.warnInk,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary,
            )
        }
        if (onClick != null) {
            Spacer(modifier = Modifier.size(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (urgent) season.danger else season.warnStrong,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(17.dp),
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
            .background(LocalSeasonPalette.current.pageBg)
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

/** What a parent has to type to confirm. Deliberately not the family's own name. */
private const val DELETE_FAMILY_CONFIRMATION = "TA BORT"

/**
 * Deletes the family and everything in it.
 *
 * Required in the app by both stores, and the one action here with no way back. So it
 * says exactly what goes rather than "are you sure", names the other people it affects,
 * and asks for a typed word rather than a tap — the same bar as removing a single
 * member, for something far larger.
 *
 * The word is fixed rather than the family's name: a family called "Melander" is easy
 * to type by reflex while reading something else, and the point of the friction is to
 * interrupt exactly that.
 *
 * Not gated by entitlement. A family must always be able to leave, paid up or not.
 */
@Composable
private fun DeleteFamilyDialog(
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    val season = LocalSeasonPalette.current
    val familyId = remember { TokenStore.getSession()?.familyId }
    var typed by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val confirmed = typed.trim().equals(DELETE_FAMILY_CONFIRMATION, ignoreCase = true)

    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("Ta bort familjen?") },
        text = {
            Column {
                Text(
                    "Det här tar bort hela familjen och allt som hör till den: alla barn " +
                        "och vuxna, sysslor, XP, djur, plånböcker och sparmål. Även för de " +
                        "andra i familjen.",
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Det går inte att ångra, och ingenting sparas.",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Har du en prenumeration behöver du avsluta den separat i Google Play " +
                        "— den försvinner inte med kontot.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Skriv $DELETE_FAMILY_CONFIRMATION för att bekräfta:",
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
                enabled = confirmed && !deleting && familyId != null,
                onClick = {
                    deleting = true
                    error = null
                    scope.launch {
                        try {
                            val id = familyId ?: throw IllegalStateException("Ingen familj i sessionen")
                            val response = withContext(Dispatchers.IO) {
                                ApiClient.familyApi.deleteFamily(id)
                            }
                            if (!response.isSuccessful) {
                                throw IllegalStateException("HTTP ${response.code()}")
                            }
                            onDeleted()
                        } catch (e: Exception) {
                            error = ApiErrors.message(e, "Kunde inte ta bort familjen.")
                        } finally {
                            deleting = false
                        }
                    }
                },
            ) {
                Text(
                    text = if (deleting) "Tar bort…" else "Ta bort allt",
                    color = if (confirmed) season.danger else season.inkFaint,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) { Text("Avbryt") }
        },
    )
}

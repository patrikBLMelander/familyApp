package se.kidquest.app.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import se.kidquest.app.network.UpdateFamilyMemberRequest
import se.kidquest.app.network.UpdatePasswordRequest
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
                            val hasPet = petResp?.isSuccessful == true && petResp.body() != null

                            child.id to ChildSummary(
                                memberId = child.id,
                                memberName = child.name,
                                todaysDone = done,
                                todaysTotal = total,
                                hasPet = hasPet,
                                streakDays = 0,
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
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text("Logga ut")
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
                if (summaryList.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Idag i familjen",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (totalTasksToday > 0) {
                                        "Barnen har gjort $completedTasksToday av $totalTasksToday uppgifter idag."
                                    } else {
                                        "Inga uppgifter planerade idag ännu."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textSecondary,
                                )
                                if (childrenWithPet > 0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$childrenWithPet av ${children.size} barn har ett aktivt djur just nu.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textSecondary,
                                    )
                                }
                            }
                        }
                    }

                    suggestion?.let { s ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Förslag idag",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val message = if (s.todaysDone >= s.todaysTotal && s.todaysTotal > 0) {
                                        "Ge extra beröm – ${s.memberName} har gjort alla sina uppgifter idag!"
                                    } else if (!s.nextTaskTitle.isNullOrBlank()) {
                                        "Påminn ${s.memberName} om \"${s.nextTaskTitle}\" för att mata sitt djur."
                                    } else {
                                        "Påminn ${s.memberName} om dagens uppgifter."
                                    }
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    FilledTonalButton(
                        onClick = onFamilyTasks,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = buttonPastel,
                            contentColor = buttonOnPastel,
                        ),
                    ) {
                        Icon(Icons.Default.Pets, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Familjens uppgifter idag")
                    }
                }

                if (!onboardingDismissed) {
                    item {
                        GetStartedCard(
                            hasChild = children.isNotEmpty(),
                            hasChores = anyChildHasChores,
                            hasPairedDevice = children.any { it.hasPairedDevice },
                            hasPet = childSummaries.values.any { it.hasPet },
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
                            onDismiss = {
                                onboardingDismissed = true
                                dashboardScope.launch { PrefsStore.setOnboardingDismissed(true) }
                            },
                        )
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
                            cardPastel = cardPastel,
                            textPrimary = textPrimary,
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
                        AdultCard(
                            name = adult.name,
                            isCurrentUser = adult.id == currentMemberId,
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

            FilledTonalButton(
                onClick = {
                    onDismissAddMemberHint()
                    onAddFamilyMember()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = buttonPastel,
                    contentColor = buttonOnPastel,
                ),
            ) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Lägg till familjemedlem")
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
    val streakDays: Int,
    val nextTaskTitle: String?,
)

@Composable
private fun ChildCard(
    name: String,
    cardPastel: Color,
    textPrimary: Color,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardPastel.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Fler val för $name",
                            tint = textPrimary,
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
            Spacer(modifier = Modifier.height(16.dp))
            summary?.let { s ->
                Text(
                    text = if (s.todaysTotal > 0) {
                        "Idag: ${s.todaysDone} av ${s.todaysTotal} uppgifter gjorda"
                    } else {
                        "Idag: inga uppgifter planerade"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = textPrimary,
                )
                Text(
                    text = if (s.hasPet) "Djur: aktivt den här månaden" else "Djur: inget ägg valt ännu",
                    style = MaterialTheme.typography.bodySmall,
                    color = textPrimary,
                )
                if (s.streakDays > 0) {
                    Text(
                        text = "Streak: ${s.streakDays} dagar i rad",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = onPetClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = buttonPastel,
                        contentColor = buttonOnPastel,
                    ),
                ) {
                    Icon(Icons.Default.Pets, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Djur")
                }
                FilledTonalButton(
                    onClick = onWalletClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = buttonPastel,
                        contentColor = buttonOnPastel,
                    ),
                ) {
                    Icon(Icons.Default.Wallet, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Plånbok")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onTasksClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = buttonPastel,
                    contentColor = buttonOnPastel,
                ),
            ) {
                Text("${name}s Sysslor")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onInviteClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = buttonOnPastel,
                ),
            ) {
                Text("Bjud in till appen")
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
 * A parent or other adult. Deliberately plainer than ChildCard: adults have no pet,
 * no chore progress and no wallet to show, so the card is identity plus the two
 * things you might do to it.
 *
 * The signed-in parent gets no delete option -- removing your own account from the
 * device you are holding is never what you meant, and there is no way back.
 */
@Composable
private fun AdultCard(
    name: String,
    isCurrentUser: Boolean,
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
            .clip(RoundedCornerShape(16.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardPastel.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCurrentUser) "$name (du)" else name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                    )
                    Text(
                        text = "Förälder",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Fler val för $name",
                            tint = textPrimary,
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
            if (!isCurrentUser) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onInviteClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = buttonPastel,
                        contentColor = buttonOnPastel,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Koppla telefon")
                }
            }
        }
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
                Column(modifier = Modifier.weight(1f)) {
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

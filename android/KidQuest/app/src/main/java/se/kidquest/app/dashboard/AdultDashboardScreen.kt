package se.kidquest.app.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.kidquest.app.chore.DailyChoreRepository
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.FamilyMemberResponse
import java.time.LocalDate

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
    onFamilyTasks: () -> Unit = {},
) {
    var children by remember { mutableStateOf<List<FamilyMemberResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var inviteChild by remember { mutableStateOf<FamilyMemberResponse?>(null) }
    var childSummaries by remember { mutableStateOf<Map<String, ChildSummary>>(emptyMap()) }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        try {
            val all = ApiClient.familyMembersApi.getAllMembers()
            children = all.filter { it.role == "CHILD" || it.role == "ASSISTANT" }
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
            error = e.message ?: "Kunde inte ladda familjemedlemmar"
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
                            onInviteClick = { inviteChild = child },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                            text = "Tryck på \"Lägg till barn\" här nedanför så hjälper vi dig komma igång.",
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
                Text("Lägg till barn")
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
) {
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
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
            )
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

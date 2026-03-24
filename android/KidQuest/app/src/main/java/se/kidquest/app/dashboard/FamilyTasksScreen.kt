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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.kidquest.app.chore.DailyChoreRepository
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.DailyChoreWithCompletionResponse
import se.kidquest.app.network.FamilyMemberResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private data class MemberWithChores(
    val member: FamilyMemberResponse,
    val chores: List<DailyChoreWithCompletionResponse>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyTasksScreen(
    onBack: () -> Unit,
) {
    var data by remember { mutableStateOf<List<MemberWithChores>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
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
                        val chores = kotlin.runCatching {
                            DailyChoreRepository.fetchChoresForToday(member.id)
                        }.getOrElse { emptyList() }
                        MemberWithChores(member, chores)
                    }
                }.map { it.await() }
            }
        } catch (e: Exception) {
            error = e.message ?: "Kunde inte ladda uppgifter"
        } finally {
            loading = false
        }
    }

    val today = LocalDate.now()
    val dayName = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("sv"))
    val dateLabel = "$dayName ${today.dayOfMonth}/${today.monthValue}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Uppgifter idag", fontWeight = FontWeight.Bold)
                        Text(dateLabel, fontSize = 12.sp, color = Color(0xFF666666))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Tillbaka",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                data.isEmpty() -> Text(
                    text = "Inga familjemedlemmar hittades.",
                    color = Color(0xFF666666),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                ) {
                    items(data, key = { it.member.id }) { row ->
                        MemberChoresCard(
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
                                        // revert
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
            }
        }
    }
}

@Composable
private fun MemberChoresCard(
    row: MemberWithChores,
    onToggle: (choreId: String, isCompleted: Boolean) -> Unit,
) {
    val done = row.chores.count { it.completed }
    val total = row.chores.size
    val allDone = total > 0 && done == total

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allDone) Color(0xFFECFDF5) else Color(0xFFFFFBEB),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = row.member.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        total == 0 -> "Inga uppgifter"
                        allDone -> "✓ Allt klart ($total)"
                        else -> "$done / $total gjorda"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (allDone) Color(0xFF2D7A2D) else Color(0xFF555555),
                    fontWeight = FontWeight.Medium,
                )
            }

            if (row.chores.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                row.chores.forEach { choreItem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = choreItem.completed,
                            onCheckedChange = { onToggle(choreItem.chore.id, choreItem.completed) },
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = choreItem.chore.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (choreItem.completed) Color(0xFF888888) else Color(0xFF1A1A1A),
                            )
                        }
                        if (choreItem.chore.xpPoints > 0) {
                            Text(
                                text = "${choreItem.chore.xpPoints} XP",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF888888),
                            )
                        }
                    }
                }
            }
        }
    }
}

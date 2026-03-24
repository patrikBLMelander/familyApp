package se.kidquest.app.dashboard

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.kidquest.app.chore.DailyChoreRepository
import se.kidquest.app.network.DailyChoreWithCompletionResponse
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildTasksScreen(
    childName: String,
    childId: String,
    onBack: () -> Unit,
) {
    var tasks by remember { mutableStateOf<List<DailyChoreWithCompletionResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var showAddChoreDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(childId, refreshKey) {
        loading = true
        error = null
        try {
            tasks = DailyChoreRepository.fetchChoresForToday(childId)
        } catch (e: Exception) {
            error = e.message ?: "Kunde inte ladda uppgifter"
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$childName – Att göra", fontWeight = FontWeight.Bold) },
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
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            if (error != null) {
                Text(text = error!!, color = MaterialTheme.colorScheme.error)
            }

            if (tasks.isEmpty()) {
                Text(
                    text = "Inga uppgifter idag.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tasks, key = { it.chore.id }) { task ->
                        TaskRow(
                            task = task,
                            onToggle = {
                                scope.launch {
                                    // Optimistisk uppdatering: vänd completed direkt i UI
                                    val currentCompleted = task.completed
                                    val choreId = task.chore.id
                                    tasks = tasks.map {
                                        if (it.chore.id == choreId) it.copy(completed = !currentCompleted) else it
                                    }
                                    try {
                                        DailyChoreRepository.toggleChoreCompletion(
                                            choreId = choreId,
                                            isCurrentlyCompleted = currentCompleted,
                                        )
                                    } catch (e: Exception) {
                                        // Revertera om något går fel
                                        tasks = tasks.map {
                                            if (it.chore.id == choreId) it.copy(completed = currentCompleted) else it
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showAddChoreDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Lägg till återkommande syssla")
            }
        }
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
}

@Composable
private fun TaskRow(
    task: DailyChoreWithCompletionResponse,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = { onToggle() },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = task.chore.title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (task.chore.xpPoints > 0) {
                    Text(
                        text = "${task.chore.xpPoints} mat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}


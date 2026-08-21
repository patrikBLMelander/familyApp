package se.kidquest.app.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.kidquest.app.chore.DailyChoreRepository
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.DailyChoreWithCompletionResponse
import se.kidquest.app.network.FeedPetRequest
import se.kidquest.app.network.PetResponse
import se.kidquest.app.network.SelectEggRequest
import se.kidquest.app.network.WalletBalanceResponse
import se.kidquest.app.network.XpProgressResponse
import se.kidquest.app.pet.PetFoodUtils
import se.kidquest.app.pet.PetNameUtils
import se.kidquest.app.pet.PetImages
import se.kidquest.app.pet.PetVisual
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDashboardScreen(
    childName: String,
    childId: String,
    onBack: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenWallet: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pet by remember { mutableStateOf<PetResponse?>(null) }
    var xp by remember { mutableStateOf<XpProgressResponse?>(null) }
    var balance by remember { mutableStateOf<WalletBalanceResponse?>(null) }
    var todaysTasks by remember { mutableStateOf<List<DailyChoreWithCompletionResponse>>(emptyList()) }
    var collectedFoodCount by remember { mutableStateOf(0) }
    var isFeeding by remember { mutableStateOf(false) }
    var showSelectEggDialog by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var isInitialLoad by remember { mutableStateOf(true) }
    var lastFedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val taskToggleScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val taskToggleMutex = remember { Mutex() }
    var refreshDebounceJob by remember { mutableStateOf<Job?>(null) }
    val todayString = LocalDate.now().toString()

    LaunchedEffect(childId, refreshKey) {
        if (isInitialLoad) {
            loading = true
        }
        error = null
        try {
            coroutineScope {
                val petDeferred = async { kotlin.runCatching { ApiClient.petsApi.getCurrentPet() }.getOrNull() }
                val xpDeferred = async { kotlin.runCatching { ApiClient.xpApi.getCurrentProgress() }.getOrNull() }
                val walletDeferred = async { kotlin.runCatching { ApiClient.walletApi.getWalletBalance() }.getOrNull() }
                val foodDeferred = async { kotlin.runCatching { ApiClient.petsApi.getCollectedFood() }.getOrNull() }
                val tasksDeferred = async { DailyChoreRepository.fetchChoresForToday(childId) }

                val petResp = petDeferred.await()
                val xpResp = xpDeferred.await()
                val walletResp = walletDeferred.await()
                val foodResp = foodDeferred.await()
                todaysTasks = tasksDeferred.await()

                pet = if (petResp?.isSuccessful == true) petResp.body() else null
                xp = if (xpResp?.isSuccessful == true) xpResp.body() else null
                balance = walletResp
                collectedFoodCount = foodResp?.totalCount ?: 0
            }
        } catch (e: Exception) {
            error = e.message ?: "Kunde inte ladda barnvyn"
        } finally {
            if (isInitialLoad) {
                loading = false
                isInitialLoad = false
            }
        }
    }

    LaunchedEffect(showConfetti) {
        if (showConfetti) {
            delay(2500)
            showConfetti = false
        }
    }

    var hasAutoOpenedEggDialog by remember { mutableStateOf(false) }
    LaunchedEffect(loading, pet, error) {
        if (!loading && error == null && pet == null && !hasAutoOpenedEggDialog) {
            hasAutoOpenedEggDialog = true
            showSelectEggDialog = true
        }
    }

    val backgroundBrush = when (pet?.petType?.lowercase()) {
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
        else -> Brush.verticalGradient(listOf(Color(0xFFE0E7FF), Color(0xFFE0F2FE))) // ljus pastell när inget djur valt
    }

    // Kortfärger i djurets tema – ljusa toner så korten syns tydligt men känns sammanhängande
    val (cardColor, cardColorInner) = when (pet?.petType?.lowercase()) {
        "dragon" -> Color(0xFFEDE9FE) to Color(0xFFDDD6FE)   // ljus lila
        "cat" -> Color(0xFFFFFBEB) to Color(0xFFFEF3C7)     // ljus amber
        "dog" -> Color(0xFFDCFCE7) to Color(0xFFBBF7D0)     // ljus grön
        "bird" -> Color(0xFFEFF6FF) to Color(0xFFDBEAFE)   // ljus blå
        "rabbit" -> Color(0xFFFDF2F8) to Color(0xFFFCE7F3)  // ljus rosa
        "bear" -> Color(0xFFFFFBEB) to Color(0xFFFEF3C7)   // ljus amber
        "snake" -> Color(0xFFDCFCE7) to Color(0xFFBBF7D0)   // ljus grön
        "panda" -> Color(0xFFF3F4F6) to Color(0xFFE5E7EB)   // ljus grå
        "slot" -> Color(0xFFF3F4F6) to Color(0xFFE5E7EB)
        "hydra" -> Color(0xFFEDE9FE) to Color(0xFFDDD6FE)
        "unicorn" -> Color(0xFFFDF2F8) to Color(0xFFFCE7F3)
        "kapybara" -> Color(0xFFDCFCE7) to Color(0xFFBBF7D0)
        else -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.surfaceVariant
    }

    // Textfärger på kort – mörka så att texten alltid är läsbar mot ljusa kort
    val cardTextPrimary = Color(0xFF1C1917)
    val cardTextSecondary = Color(0xFF57534E)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(childName, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
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
                .background(backgroundBrush)
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (error != null) {
                Text(text = error!!, color = MaterialTheme.colorScheme.error)
                return@Scaffold
            }

            // Pet + XP-kort – stor bild, humör och cirkel-progress
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (pet != null) {
                        val petName = pet!!.name ?: PetNameUtils.getPetNameSwedish(pet!!.petType)
                        val hasFedToday = lastFedDate == todayString
                        val isHungry = !hasFedToday
                        val moodEmoji = if (isHungry) "🥺" else "😊"
                        val moodText = if (isHungry) {
                            "Jag är hungrig... kan du ge mig mat?"
                        } else {
                            "Mmm! Tack för maten idag, $childName!"
                        }

                        // Pratbubbla över bilden – pastellfärger som syns bra mot alla djur
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = moodText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = cardTextPrimary,
                                modifier = Modifier
                                    .background(
                                        color = if (isHungry) {
                                            Color(0xFFFFE4D6) // mjuk persiko/beige – hungrig
                                        } else {
                                            Color(0xFFD1FAE5) // mjuk mint – nöjd
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Stor pet-bild med cirkulär XP-progress och humörikon i mitten
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PetVisual(
                                petType = pet!!.petType,
                                growthStage = pet!!.growthStage,
                                contentDescription = petName,
                                modifier = Modifier.fillMaxSize(),
                            )

                            xp?.let { progress ->
                                val xpThresholds = listOf(0, 10, 35, 70, 125)
                                val maxLevel = xpThresholds.lastIndex
                                val safeLevel = progress.currentLevel.coerceIn(1, maxLevel)
                                val currentIndex = safeLevel - 1
                                val currentThreshold = xpThresholds[currentIndex]
                                val nextThreshold = xpThresholds.getOrNull(safeLevel) ?: xpThresholds.last()
                                val range = (nextThreshold - currentThreshold).coerceAtLeast(1)
                                val percentage = (progress.xpInCurrentLevel.toFloat() / range.toFloat()).coerceIn(0f, 1f)

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .offset(y = 32.dp), // mindre cirkel som hänger ned lite under bilden
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { percentage },
                                            modifier = Modifier.fillMaxSize(),
                                            strokeWidth = 6.dp,
                                        )
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text = moodEmoji,
                                                style = MaterialTheme.typography.titleLarge,
                                            )
                                            Text(
                                                text = progress.currentLevel.toString(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = cardTextPrimary,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = petName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = cardTextPrimary,
                        )
                    } else {
                        Text(text = "Inget djur denna månad", color = cardTextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showSelectEggDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                        ) {
                            Text("Välj ägg")
                        }
                    }
                    // Ingen extra rak progress-bar – allt visas i den runda indikatorn
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mat att ge – visuell pott + mata djuret
            val foodEmoji = PetFoodUtils.getPetFoodEmoji(pet?.petType)
            val foodName = PetFoodUtils.getPetFoodName(pet?.petType)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = cardColor.copy(alpha = 0.92f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Titelrad
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "🍽️ Mat att ge",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = cardTextPrimary,
                        )
                        Text(
                            text = "$collectedFoodCount $foodName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (collectedFoodCount > 0) {
                                cardTextPrimary
                            } else {
                                cardTextSecondary
                            },
                        )
                    }

                    // Skål och emojis – helt egen sektion utan överlapp
                    if (collectedFoodCount == 0) {
                        Text(
                            text = "Ingen mat ännu... Utför uppgifter för att samla $foodName!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cardTextSecondary,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Skål sedd uppifrån – oval med tjock kant
                            Box(
                                modifier = Modifier
                                    .width(220.dp)
                                    .height(70.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                // Yttre kant på skålen
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(50.dp),
                                        ),
                                )
                                // Skålens insida
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.78f)
                                        .fillMaxHeight(0.65f)
                                        .background(
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                                            shape = RoundedCornerShape(50.dp),
                                        ),
                                )
                                // Maten i skålen
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    repeat(kotlin.math.min(collectedFoodCount, 10)) {
                                        Text(
                                            text = foodEmoji,
                                            style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.padding(horizontal = 2.dp),
                                        )
                                    }
                                    if (collectedFoodCount > 10) {
                                        Text(
                                            text = "+${collectedFoodCount - 10}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = cardTextSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                if (collectedFoodCount < 1 || isFeeding) return@Button
                                isFeeding = true
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            ApiClient.petsApi.feedPet(FeedPetRequest(xpAmount = 1))
                                        }
                                            lastFedDate = todayString
                                        refreshKey++
                                    } finally {
                                        isFeeding = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = collectedFoodCount >= 1 && !isFeeding,
                        ) {
                            Text(if (isFeeding) "..." else "Mata 1 $foodName")
                        }
                        Button(
                            onClick = {
                                if (collectedFoodCount == 0 || isFeeding) return@Button
                                isFeeding = true
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            ApiClient.petsApi.feedPet(FeedPetRequest(xpAmount = collectedFoodCount))
                                        }
                                        lastFedDate = todayString
                                        refreshKey++
                                    } finally {
                                        isFeeding = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = collectedFoodCount > 0 && !isFeeding,
                        ) {
                            Text(if (isFeeding) "Ger mat..." else "Mata allt ($collectedFoodCount)")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dagens uppgifter – synliga direkt under djuret
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = cardColor.copy(alpha = 0.92f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(text = "Dagens uppgifter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cardTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (todaysTasks.isEmpty()) {
                        Text(
                            text = "Inga uppgifter idag.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cardTextSecondary,
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 260.dp),
                        ) {
                            items(todaysTasks, key = { it.chore.id }) { task ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = cardColorInner.copy(alpha = 0.98f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = task.completed,
                                            onCheckedChange = {
                                                taskToggleScope.launch {
                                                    taskToggleMutex.withLock {
                                                        val currentCompleted = task.completed
                                                        val choreId = task.chore.id

                                                        todaysTasks = todaysTasks.map {
                                                            if (it.chore.id == choreId) {
                                                                it.copy(completed = !currentCompleted)
                                                            } else {
                                                                it
                                                            }
                                                        }

                                                        try {
                                                            DailyChoreRepository.toggleChoreCompletion(
                                                                choreId = choreId,
                                                                isCurrentlyCompleted = currentCompleted,
                                                            )
                                                            // Debounce: en omladdning ~400 ms efter senaste toggle så att snabba klick inte avbryter LaunchedEffect upprepade gånger
                                                            refreshDebounceJob?.cancel()
                                                            refreshDebounceJob = taskToggleScope.launch {
                                                                delay(400)
                                                                refreshKey++
                                                            }
                                                        } catch (e: Exception) {
                                                            todaysTasks = todaysTasks.map {
                                                                if (it.chore.id == choreId) {
                                                                    it.copy(completed = currentCompleted)
                                                                } else {
                                                                    it
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Color(0xFFFFCC00),
                                                checkmarkColor = Color(0xFF5B21B6),
                                                uncheckedColor = Color(0xFFFFE082),
                                            ),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = task.chore.title,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = cardTextPrimary,
                                            )
                                            if (task.chore.xpPoints > 0) {
                                                Text(
                                                    text = "${task.chore.xpPoints} mat",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = cardTextSecondary,
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

            Spacer(modifier = Modifier.height(16.dp))

            // Plånbokskort
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = cardColor.copy(alpha = 0.92f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(text = "Plånbok", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cardTextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (balance != null) {
                        Text(
                            text = "Saldo: ${balance!!.balance} kr",
                            style = MaterialTheme.typography.bodyLarge,
                            color = cardTextPrimary,
                        )
                    } else {
                        Text(
                            text = "Saldo okänt.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cardTextSecondary,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onOpenWallet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                    ) {
                        Text("Öppna plånbok")
                    }
                }
            }
        }
    }

    if (showSelectEggDialog) {
        SelectEggDialog(
            onDismiss = { showSelectEggDialog = false },
            onEggSelected = { updatedPet ->
                pet = updatedPet
                showSelectEggDialog = false
                showConfetti = true
            },
        )
    }

    if (showConfetti) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "🎉",
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    text = "Grattis! Ditt nya djur är här!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(10) {
                        Text(text = "🎊", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectEggDialog(
    onDismiss: () -> Unit,
    onEggSelected: (PetResponse) -> Unit,
) {
    var eggTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedEgg by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isNaming by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    var isHatching by remember { mutableStateOf(false) }
    var hatchingStage by remember { mutableStateOf(1) }
    var showCelebrate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            eggTypes = withContext(Dispatchers.IO) {
                ApiClient.petsApi.getAvailableEggTypes()
            }
            if (eggTypes.isNotEmpty()) {
                selectedEgg = eggTypes.first()
            }
        } catch (e: Exception) {
            error = e.message ?: "Kunde inte hämta äggtyper"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(isHatching, selectedEgg) {
        if (isHatching && selectedEgg != null) {
            showCelebrate = false
            for (stage in 1..5) {
                hatchingStage = stage
                delay(2000)
            }
            showCelebrate = true
            delay(2000)
            showCelebrate = false
            isHatching = false
            isNaming = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isHatching) Color.White else MaterialTheme.colorScheme.surface,
        title = {
            Text(
                when {
                    isHatching -> "Ägget kläcks"
                    isNaming -> "Namnge ditt djur"
                    else -> "Välj ägg"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (loading) {
                    Text("Laddar äggtyper…")
                } else if (error != null) {
                    Text(text = error!!, color = MaterialTheme.colorScheme.error)
                } else if (isHatching) {
                    val egg = selectedEgg
                    val eggStageDrawable = PetImages.eggDrawable(LocalContext.current, egg, hatchingStage)
                    if (showCelebrate) {
                        Text(
                            text = "Grattis! Ditt ägg har kläckts!",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            text = "Ägget kläcks...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (eggStageDrawable != null) {
                        Image(
                            painter = painterResource(id = eggStageDrawable),
                            contentDescription = "Kläckande ägg",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                } else if (!isNaming) {
                    Text("Välj ett ägg för ditt nya djur.")
                    Spacer(modifier = Modifier.height(12.dp))
                    eggTypes.forEach { egg ->
                        val isSelected = egg == selectedEgg
                        val eggDrawable = PetImages.eggDrawable(LocalContext.current, egg)
                        val label = getEggLabel(egg)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .clickable {
                                        selectedEgg = egg
                                        showHint = false
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (eggDrawable != null) {
                                    Image(
                                        painter = painterResource(id = eggDrawable),
                                        contentDescription = label,
                                        modifier = Modifier.size(80.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                } else {
                                    Text(
                                        text = "🥚",
                                        style = MaterialTheme.typography.headlineLarge,
                                    )
                                }
                                if (isSelected) {
                                    TextButton(onClick = { showHint = !showHint }) {
                                        Text(if (showHint) "Göm hint" else "Visa hint")
                                    }
                                    if (showHint) {
                                        val hint = getEggHint(egg)
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        ) {
                                            Text(
                                                text = hint,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(8.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    val egg = selectedEgg
                    if (egg != null) {
                        PetVisual(
                            petType = PetImages.petTypeForEgg(egg),
                            growthStage = 1,
                            contentDescription = "Nytt djur",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Text(
                        text = "Vad ska ditt djur heta?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Namn på djuret (valfritt)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val egg = selectedEgg ?: return@TextButton
                    if (!isNaming && !isHatching) {
                        // Starta kläckningssekvens
                        isHatching = true
                        hatchingStage = 1
                        showHint = false
                        return@TextButton
                    }
                    if (isHatching) return@TextButton
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            val pet = withContext(Dispatchers.IO) {
                                ApiClient.petsApi.selectEgg(
                                    SelectEggRequest(
                                        eggType = egg,
                                        name = name.ifBlank { null },
                                    ),
                                )
                            }
                            onEggSelected(pet)
                        } catch (e: Exception) {
                            error = e.message ?: "Kunde inte välja ägg"
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !loading && !saving && selectedEgg != null && !isHatching,
            ) {
                Text(
                    when {
                        saving -> "Väljer…"
                        isHatching -> "Ägget kläcks…"
                        !isNaming -> "Välj"
                        else -> "Spara namn"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Välj senare")
            }
        },
    )
}

private fun getEggLabel(eggType: String): String =
    when (eggType.lowercase()) {
        "blue_egg" -> "Blått ägg"
        "green_egg" -> "Grönt ägg"
        "red_egg" -> "Rött ägg"
        "yellow_egg" -> "Gult ägg"
        "purple_egg" -> "Lila ägg"
        "orange_egg" -> "Orange ägg"
        "brown_egg" -> "Brunt ägg"
        "black_egg" -> "Mörkt ägg"
        "gray_egg" -> "Grått ägg"
        "teal_egg" -> "Turkost ägg"
        "pink_egg" -> "Rosa ägg"
        "cyan_egg" -> "Blågrönt ägg"
        else -> eggType
    }

private fun getEggHint(eggType: String): String =
    when (eggType.lowercase()) {
        "blue_egg" -> "Jag älskar att flyga högt bland molnen."
        "green_egg" -> "Jag spinner nöjt när jag får ligga i solen."
        "red_egg" -> "Jag hämtar gärna bollen om du kastar den."
        "yellow_egg" -> "Jag kvittrar gärna när dagen börjar."
        "purple_egg" -> "Jag hoppar fram och gnager gärna på morötter."
        "orange_egg" -> "Jag tar gärna en lång vintersömn med magen full."
        "brown_egg" -> "Jag gillar att slingra mig på varma stenar."
        "black_egg" -> "Jag tycker om att smyga runt i skuggan."
        "gray_egg" -> "Jag rör mig långsamt men kramas gärna länge."
        "teal_egg" -> "Jag trivs där det finns mycket vatten och mystik."
        "pink_egg" -> "Jag gillar glitter, regnbågar och magi."
        "cyan_egg" -> "Jag älskar att plaska runt med kompisar."
        else -> "Jag längtar efter att få träffa dig."
    }


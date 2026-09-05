package se.kidquest.app.dashboard

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import se.kidquest.app.chore.DailyChoreRepository
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.DailyChoreResponse
import se.kidquest.app.network.DailyChoreWithCompletionResponse
import se.kidquest.app.network.FeedPetRequest
import se.kidquest.app.network.PetResponse
import se.kidquest.app.network.SelectEggRequest
import se.kidquest.app.network.WalletBalanceResponse
import se.kidquest.app.network.XpProgressResponse
import se.kidquest.app.pet.PetFoodUtils
import se.kidquest.app.pet.PetImages
import se.kidquest.app.pet.PetNameUtils
import se.kidquest.app.pet.PetTheme
import se.kidquest.app.pet.PetVisual
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInRoot
import se.kidquest.app.network.PetHistoryResponse
import se.kidquest.app.theme.LocalSeasonPalette
import se.kidquest.app.theme.SeasonPalette
import se.kidquest.app.theme.SeasonTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * The child's own view.
 *
 * Reached two ways, and the difference is only which endpoints it calls. A child on
 * their own phone is authenticated as themselves, so the self-scoped endpoints apply.
 * A parent viewing or helping is authenticated as the parent, so every call has to
 * name the child explicitly -- otherwise "feed" would pour the food into the parent's
 * own pet, which is what /pets/feed does when it resolves the member from the token.
 *
 * @param actingAsParent true when a parent is looking at, or acting for, this child.
 */
fun ChildDashboardScreen(
    childName: String,
    childId: String,
    onBack: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenWallet: () -> Unit,
    actingAsParent: Boolean = false,
    onExitChildView: (() -> Unit)? = null,
    onSwitchChild: (() -> Unit)? = null,
    /**
     * Icke-null renderar de här värdena i stället för att anropa nätet.
     *
     * Finns för att skärmen ska gå att fotografera. Bandet som växer när dagen är klar
     * och samlingens läsläge nås bara genom att trycka, och de två lägena är precis
     * de som en skärmbild behövs för. Sätts bara av harnesket i MainActivity.
     */
    fixture: ChildDashboardFixture? = null,
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
    // Distinct from "no pet": the request itself failed, so we must not claim the
    // child has not chosen an egg.
    var petLoadFailed by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var isInitialLoad by remember { mutableStateOf(true) }
    var lastFedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val taskToggleScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val taskToggleMutex = remember { Mutex() }
    var refreshDebounceJob by remember { mutableStateOf<Job?>(null) }
    val todayString = LocalDate.now().toString()
    // Barnets tidigare djur, och vilket som visas. Nil betyder dagens.
    var petHistory by remember { mutableStateOf<List<PetHistoryResponse>>(emptyList()) }
    var viewingPast by remember { mutableStateOf<PetHistoryResponse?>(null) }

    // Matningens synliga del. Barnen som testade sa att man inte ser att man matar och
    // inte ser progressen -- bägge stämde: maten försvann ur en siffra och nivån var en
    // textsträng. Sekvensen bor i FeedAnimation, tillståndet här.
    val feedAnim = remember { FeedAnimation() }
    // Bandets och matlungans läge i rotens koordinater. Maten ska flyga från räknaren
    // till djuret, och lungans y flyttar sig när "Allt klart idag!" lägger till en rad,
    // så den kan inte hårdkodas.
    // Båda sparas i rotens pixlar och dras ifrån varandra först vid renderingen.
    // onGloballyPositioned garanterar ingen ordning mellan förälder och barn, så om
    // brickorna mättes först vore scenOrigin noll och deras läge räknat mot roten i
    // stället för mot scenen. Att spara råa lägen och räkna differensen där den används
    // gör ordningen betydelselös.
    var scenOrigin by remember { mutableStateOf(Offset.Zero) }
    var foodTilesRoot by remember { mutableStateOf<Offset?>(null) }
    // positionInRoot() ger pixlar medan bandets maxWidth ger dp, och bären placeras med
    // Modifier.offset i dp. Omräkningen sker vid renderingen, på ett ställe.
    val density = LocalDensity.current
    // Nivån som firas, fångad när höjningen sker. Att läsa xp?.currentLevel + 1 medan
    // fanfaren står kvar hade visat fel siffra så fort omladdningen kommit in.
    var celebrateLevel by remember { mutableStateOf(0) }

    LaunchedEffect(childId, refreshKey) {
        if (fixture != null) {
            pet = fixture.pet
            xp = fixture.xp
            balance = fixture.balance
            todaysTasks = fixture.tasks
            collectedFoodCount = fixture.foodCount
            petHistory = fixture.history
            viewingPast = if (fixture.viewingPast) fixture.history.firstOrNull() else null
            loading = false
            isInitialLoad = false
            return@LaunchedEffect
        }
        if (isInitialLoad) {
            loading = true
        }
        error = null
        petLoadFailed = false
        try {
            coroutineScope {
                val petDeferred = async {
                    kotlin.runCatching {
                        if (actingAsParent) ApiClient.petsApi.getMemberPet(childId)
                        else ApiClient.petsApi.getCurrentPet()
                    }
                }
                val xpDeferred = async {
                    kotlin.runCatching {
                        if (actingAsParent) ApiClient.xpApi.getMemberXpProgress(childId)
                        else ApiClient.xpApi.getCurrentProgress()
                    }.getOrNull()
                }
                val walletDeferred = async {
                    kotlin.runCatching {
                        if (actingAsParent) ApiClient.walletApi.getMemberBalance(childId)
                        else ApiClient.walletApi.getWalletBalance()
                    }.getOrNull()
                }
                val foodDeferred = async {
                    kotlin.runCatching {
                        if (actingAsParent) ApiClient.petsApi.getMemberCollectedFood(childId)
                        else ApiClient.petsApi.getCollectedFood()
                    }.getOrNull()
                }
                val tasksDeferred = async { DailyChoreRepository.fetchChoresForToday(childId) }

                val petResult = petDeferred.await()
                val petResp = petResult.getOrNull()
                val xpResp = xpDeferred.await()
                val walletResp = walletDeferred.await()
                val foodResp = foodDeferred.await()
                todaysTasks = tasksDeferred.await()

                pet = if (petResp?.isSuccessful == true) petResp.body() else null

                // A 404 genuinely means "this child has no pet this month", which is what
                // opens the egg picker. Anything else - a 500, a timeout, a dropped
                // connection - must not be reported as "you haven't chosen an egg", or a
                // child who already has a pet is told to pick another one and then hits
                // "Pet already selected for this month" when they try.
                petLoadFailed = when {
                    petResult.isFailure -> true
                    petResp == null -> true
                    petResp.isSuccessful -> false
                    petResp.code() == 404 -> false
                    else -> true
                }
                if (petLoadFailed) {
                    val reason = petResult.exceptionOrNull()?.message
                        ?: petResp?.let { "HTTP ${it.code()}" }
                        ?: "okänt fel"
                    Log.w("ChildDashboard", "Kunde inte hämta djur för $childId: $reason")
                }
                xp = if (xpResp?.isSuccessful == true) xpResp.body() else null
                balance = walletResp
                collectedFoodCount = foodResp?.totalCount ?: 0
            }
        } catch (e: Exception) {
            error = ApiErrors.message(e, "Kunde inte ladda barnvyn")
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

    LaunchedEffect(childId) {
        if (fixture != null) return@LaunchedEffect
        petHistory = withContext(Dispatchers.IO) {
            kotlin.runCatching {
                if (actingAsParent) ApiClient.petsApi.getMemberPetHistory(childId)
                else ApiClient.petsApi.getPetHistory()
            }.getOrDefault(emptyList())
        }.sortedWith(compareByDescending<PetHistoryResponse> { it.year }.thenByDescending { it.month })
    }

    // Matningen och bockningen låg förut inne i knapparnas onClick. De ligger här nu
    // eftersom layouten inte längre äger dem -- beteendet är oförändrat, inklusive
    // mutexen som hindrar två bockningar från att trampa på varandra och debouncen som
    // gör att snabba klick inte avbryter omladdningen om och om igen.
    val feed: (Int) -> Unit = { amount ->
        if (amount > 0 && !isFeeding) {
            isFeeding = true
            // Sparade för att kunna läggas tillbaka om anropet misslyckas.
            val xpBefore = xp
            val petBefore = pet
            val foodBefore = collectedFoodCount

            // Vilket bär som tar barnet över tröskeln. xpForNextLevel är hur många XP
            // som fattas, alltså korsar det (xpForNextLevel - 1):te bäret. Noll betyder
            // högsta nivån, där det inte finns någon höjning att spela.
            val needed = xpBefore?.xpForNextLevel ?: 0
            val crossing = if (needed in 1..amount) needed - 1 else null
            val emoji = PetFoodUtils.getPetFoodEmoji(pet?.petType)

            coroutineScope.launch {
                // I en fixtur finns ingen session att mata mot. Animationen körs ändå,
                // och ingen omladdning sker efteråt -- den skulle lägga tillbaka
                // fixturens ursprungliga siffror och radera det man just tittade på.
                if (fixture != null) {
                    feedAnim.run(
                        amount = amount,
                        emoji = emoji,
                        crossingBerry = crossing,
                        onBerryLifted = {
                            collectedFoodCount = (collectedFoodCount - 1).coerceAtLeast(0)
                        },
                        onBerryLanded = { xp = xp?.plusOneXp() },
                        onLevelUp = {
                            celebrateLevel = ((xpBefore?.currentLevel ?: 1) + 1).coerceAtMost(5)
                            xp = xp?.let { it.copy(currentLevel = celebrateLevel) }
                            pet = pet?.let {
                                it.copy(growthStage = (it.growthStage + 1).coerceAtMost(5))
                            }
                        },
                    )
                    isFeeding = false
                    return@launch
                }

                // Anropet går iväg nu, men skärmen väntar inte in det. toggleTask några
                // rader ner har varit optimistisk hela tiden; matningen laddade om och
                // stod stilla under nätverkstiden, vilket är halva skälet att barnen
                // sa att det inte händer något.
                val call = async(Dispatchers.IO) {
                    kotlin.runCatching {
                        if (actingAsParent) {
                            ApiClient.petsApi.feedMemberPet(childId, FeedPetRequest(xpAmount = amount))
                        } else {
                            ApiClient.petsApi.feedPet(FeedPetRequest(xpAmount = amount))
                        }
                    }
                }

                feedAnim.run(
                    amount = amount,
                    emoji = emoji,
                    crossingBerry = crossing,
                    onBerryLifted = {
                        collectedFoodCount = (collectedFoodCount - 1).coerceAtLeast(0)
                    },
                    onBerryLanded = { xp = xp?.plusOneXp() },
                    onLevelUp = {
                        celebrateLevel = ((xpBefore?.currentLevel ?: 1) + 1).coerceAtMost(5)
                        // Namnraden visar nivån, och utan det här sa den "NIVÅ 3" i
                        // 2,4 sekunder medan banderollen sa "Nivå 4!". Omladdningen
                        // rättar den ändå, men motsägelsen syntes hela firandet.
                        xp = xp?.let { it.copy(currentLevel = celebrateLevel) }
                        // Stadiet är nivån (calculateGrowthStage mappar 1:1), så det går
                        // att visa direkt. Bytet sker under blänket och läser därför som
                        // att djuret växer och inte som att en bild ersattes.
                        pet = pet?.let { it.copy(growthStage = (it.growthStage + 1).coerceAtMost(5)) }
                    },
                )

                val result = call.await()
                if (result.isSuccess) {
                    lastFedDate = todayString
                    refreshKey++
                } else {
                    // Maten kommer tillbaka. Ett barn som ser bären flyga och sedan
                    // ligga kvar i räknaren har fått veta att det inte gick.
                    feedAnim.reset()
                    collectedFoodCount = foodBefore
                    xp = xpBefore
                    pet = petBefore
                    error = ApiErrors.message(
                        result.exceptionOrNull() ?: Exception("okänt fel"),
                        "Kunde inte ge maten",
                    )
                }
                isFeeding = false
            }
        }
    }

    /**
     * Ett enda stycke mat, från ett tryck på en bricka i remsan.
     *
     * Blockerar med flit inte på isFeeding. Ett barn som trycker fyra gånger snabbt ska
     * se fyra frön i luften, inte vänta ut det första -- flera samtidigt är hela poängen
     * med att maten ligger som stycken.
     */
    val feedOne: () -> Unit = {
        val xp0 = xp
        val petNow = pet
        if (collectedFoodCount > 0 && xp0 != null && feedAnim.levelUp <= 0f) {
            // xpForNextLevel räknar bara det som redan LANDAT. Maten som är i luften har
            // inte räknats än, så den måste dras av för att veta om just det här stycket
            // är det som korsar tröskeln. Utan avdraget skulle fyra snabba tryck strax
            // under gränsen fira fyra gånger.
            val crosses = (xp0.xpForNextLevel - feedAnim.inFlight) == 1
            val emoji = PetFoodUtils.getPetFoodEmoji(petNow?.petType)
            val levelAtTap = xp0.currentLevel

            coroutineScope.launch {
                val call = if (fixture != null) null else async(Dispatchers.IO) {
                    kotlin.runCatching {
                        if (actingAsParent) {
                            ApiClient.petsApi.feedMemberPet(childId, FeedPetRequest(xpAmount = 1))
                        } else {
                            ApiClient.petsApi.feedPet(FeedPetRequest(xpAmount = 1))
                        }
                    }
                }

                feedAnim.one(
                    emoji = emoji,
                    crosses = crosses,
                    onLifted = {
                        collectedFoodCount = (collectedFoodCount - 1).coerceAtLeast(0)
                    },
                    onLanded = { xp = xp?.plusOneXp() },
                    onLevelUp = {
                        celebrateLevel = (levelAtTap + 1).coerceAtMost(5)
                        xp = xp?.let { it.copy(currentLevel = celebrateLevel) }
                        pet = pet?.let { it.copy(growthStage = (it.growthStage + 1).coerceAtMost(5)) }
                    },
                )

                val result = call?.await() ?: return@launch
                if (result.isSuccess) {
                    lastFedDate = todayString
                    // Bara när ingenting är kvar i luften. Fem snabba tryck skulle
                    // annars ladda om fem gånger och slå sönder sin egen animation.
                    if (feedAnim.inFlight == 0) refreshKey++
                } else {
                    // Ingen aritmetisk återställning här. Med flera stycken i luften
                    // samtidigt går det inte att veta vad som ska läggas tillbaka --
                    // servern är sanningen, så vi hämtar den.
                    error = ApiErrors.message(
                        result.exceptionOrNull() ?: Exception("okänt fel"),
                        "Kunde inte ge maten",
                    )
                    refreshKey++
                }
            }
        }
    }

    val toggleTask: (DailyChoreWithCompletionResponse) -> Unit = { task ->
        taskToggleScope.launch {
            taskToggleMutex.withLock {
                val wasCompleted = task.completed
                val choreId = task.chore.id
                // Optimistiskt: bocken vänder direkt, innan servern svarat.
                todaysTasks = todaysTasks.map {
                    if (it.chore.id == choreId) it.copy(completed = !wasCompleted) else it
                }
                try {
                    DailyChoreRepository.toggleChoreCompletion(
                        choreId = choreId,
                        isCurrentlyCompleted = wasCompleted,
                    )
                    refreshDebounceJob?.cancel()
                    refreshDebounceJob = taskToggleScope.launch {
                        delay(400)
                        refreshKey++
                    }
                } catch (e: Exception) {
                    todaysTasks = todaysTasks.map {
                        if (it.chore.id == choreId) it.copy(completed = wasCompleted) else it
                    }
                }
            }
        }
    }

    var hasAutoOpenedEggDialog by remember { mutableStateOf(false) }
    LaunchedEffect(loading, pet, error, petLoadFailed) {
        if (fixture != null) return@LaunchedEffect
        if (!loading && error == null && !petLoadFailed && pet == null && !hasAutoOpenedEggDialog) {
            hasAutoOpenedEggDialog = true
            showSelectEggDialog = true
        }
    }

    // Every species colour lives in PetTheme, shared with the parent dashboard. It
    // used to be spelled out here as two `when` blocks, which is how shark and lion
    // ended up missing from both and falling through to the neutral pastel.
    // Årstidens ljusa palett. Mörkt läge är föräldrarnas inställning -- de sitter i
    // appen på kvällen -- och barnets skärm ska vara ljus och ha en årstid i den.
    // Förut stod här PetTheme.background(), en gradient per djurtyp som var helt utanför
    // temat resten av appen fick.
    val season = remember { SeasonTheme.current(dark = false) }
    val context = LocalContext.current

    val doneCount = todaysTasks.count { it.completed }
    val allDone = todaysTasks.isNotEmpty() && doneCount == todaysTasks.size
    val shownPet = viewingPast?.let { it.petType to it.finalGrowthStage }
        ?: pet?.let { it.petType to it.growthStage }
    // Bandet är kort så länge det finns arbete kvar och växer när dagen är klar:
    // belöningen kommer efter arbetet, inte före.
    val bandHeight by animateDpAsState(
        targetValue = if (allDone && viewingPast == null) 400.dp else 258.dp,
        animationSpec = tween(durationMillis = 450),
        label = "band",
    )

    CompositionLocalProvider(LocalSeasonPalette provides season) {
        Box(modifier = Modifier.fillMaxSize().background(season.pageBg)) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                return@Box
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                // ---- Scenen: bandet och matremsan ----
                // De två ligger i en gemensam behållare för att maten flyger MELLAN dem
                // -- från en bricka i remsan upp till djuret i bandet. Bandet klipper
                // sitt eget innehåll, så ett frö som bara låg i bandet hade kapats vid
                // underkanten. Flyglagret ligger därför i scenen, medan konfettin blir
                // kvar inne i bandet där den ska klippas.
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { scenOrigin = it.positionInRoot() },
                ) {
                    val bigPet = allDone && viewingPast == null
                    val petScale = if (bigPet) 0.82f else 0.52f
                    // ContentScale.Fit centrerar konsten i sin box, så boxens mitt är
                    // konstens mitt -- det är dit maten ska. Bandet ligger överst i
                    // scenen, så y räknas från scenens topp utan förskjutning.
                    val petBoxW = maxWidth.value * petScale
                    val petBoxH = bandHeight.value * petScale
                    val petCenter = Offset(
                        x = if (bigPet) maxWidth.value / 2f else maxWidth.value - petBoxW / 2f,
                        y = bandHeight.value - petBoxH / 2f,
                    )

                    Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bandHeight)
                            // Konfettin faller nedåt och ritades utanför bandet, ner
                            // över uppgiftskortet -- det läste som en bugg och inte som
                            // ett firande.
                            .clipToBounds(),
                    ) {
                    if (shownPet != null) {
                        PetVisual(
                            petType = shownPet.first,
                            growthStage = shownPet.second,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 0,
                            scale = petScale,
                            alignment = if (bigPet) {
                                Alignment.BottomCenter
                            } else {
                                Alignment.BottomEnd
                            },
                            // Bara djuret pulsar. Skalar man hela PetVisual zoomar
                            // landskapet med.
                            petScaleMultiplier = feedAnim.petPulse,
                        )
                    } else {
                        PetImages.seasonalBackgroundDrawable(context)?.let { bg ->
                            Image(
                                painter = painterResource(id = bg),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }

                    // Läsbarhet: nedre kanten tonar in i sidans färg, toppen mörknar så
                    // att raden med knappar syns mot vilken årstid som helst.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.34f),
                                    0.22f to Color.Transparent,
                                    0.72f to Color.Transparent,
                                    0.92f to season.pageBg.copy(alpha = 0.55f),
                                    1f to season.pageBg,
                                )
                            )
                    )

                    // Djursamlingen och plånboken.
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            // enableEdgeToEdge ritar innehållet ända upp, så utan det här
                            // hamnar cirklarna ovanpå klockan.
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (petHistory.isNotEmpty() && pet != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PetCircle(
                                    petType = pet!!.petType,
                                    stage = pet!!.growthStage,
                                    active = viewingPast == null,
                                    season = season,
                                ) { viewingPast = null }
                                petHistory.take(5).forEach { past ->
                                    PetCircle(
                                        petType = past.petType,
                                        stage = past.finalGrowthStage,
                                        active = viewingPast?.id == past.id,
                                        season = season,
                                    ) { viewingPast = past }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        // Saldot, och vägen till plånboken. Ett tryck och inte ett kort:
                        // bandet ska inte konkurrera med listan om uppmärksamheten.
                        Surface(
                            onClick = onOpenWallet,
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.92f),
                        ) {
                            Text(
                                text = balance?.let { "${it.balance} kr ›" } ?: "Plånbok ›",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = season.accent,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }

                    val labelShadow = Shadow(
                        color = Color.Black.copy(alpha = 0.55f),
                        offset = Offset(0f, 1f),
                        blurRadius = 8f,
                    )
                    // Namn, nivå och matantal.
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val past = viewingPast
                        if (past != null) {
                            Text(
                                text = monthLabel(past.year, past.month).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(shadow = labelShadow),
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                            Text(
                                text = PetNameUtils.getPetNameSwedish(past.petType),
                                style = MaterialTheme.typography.titleLarge.copy(shadow = labelShadow),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        } else if (pet != null) {
                            val displayName = pet!!.name?.takeIf { it.isNotBlank() }
                                ?: PetNameUtils.getPetNameSwedish(pet!!.petType)
                            Text(
                                text = "$displayName · NIVÅ ${xp?.currentLevel ?: 1}".uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(shadow = labelShadow),
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.92f),
                            )
                            // Mätaren. Ett barn på 17 av 25 xp såg förut exakt samma
                            // text som ett barn på 1 av 25 -- nivån var en sträng och
                            // xpInCurrentLevel låg oanvänd i modellen.
                            val xp0 = xp
                            if (xp0 != null) {
                                XpMeter(
                                    xpInLevel = xp0.xpInCurrentLevel,
                                    span = xpSpanFor(xp0.xpInCurrentLevel, xp0.xpForNextLevel),
                                    level = xp0.currentLevel,
                                    modifier = Modifier.width(172.dp),
                                )
                            }
                            if (allDone) {
                                Text(
                                    text = "Allt klart idag!",
                                    style = MaterialTheme.typography.headlineSmall.copy(shadow = labelShadow),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            // Matlungan låg här förut och sa "5 mat att ge". Remsan
                            // under bandet visar samma sak och går dessutom att trycka
                            // på, så två platser hade sagt samma sak och bara den ena
                            // gjort något.
                        }
                    }

                    // ---- Firandet, klippt till bandet ----
                    // Konfettin faller och ska sluta vid bandets kant. Maten flyger
                    // uppåt och ska inte -- därför ligger de i olika lager.
                    if (feedAnim.levelUp > 0f) {
                        LevelUpFlash(feedAnim.levelUp)
                        ConfettiBurst(
                            progress = feedAnim.levelUp,
                            origin = petCenter,
                            season = season,
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(top = 54.dp, start = 14.dp, end = 14.dp),
                        ) {
                            LevelUpBanner(
                                level = celebrateLevel,
                                petName = pet?.name?.takeIf { it.isNotBlank() }
                                    ?: PetNameUtils.getPetNameSwedish(pet?.petType),
                                progress = feedAnim.levelUp,
                                season = season,
                            )
                        }
                    }
                    } // bandet

                    // ---- Matremsan ----
                    // Ett tidigare djur går inte att mata, och en misslyckad hämtning
                    // får inte visa en tom remsa som om barnet vore utan mat.
                    val shownPetForFood = pet
                    if (viewingPast == null && shownPetForFood == null && !petLoadFailed && !loading) {
                        // Utan djur finns ingen mat att ge, men det ska finnas en väg
                        // till ägget -- och den låg förut bara bakom en tom uppgiftslista.
                        ChooseEggStrip(
                            season = season,
                            onSelectEgg = { showSelectEggDialog = true },
                            modifier = Modifier.padding(start = 13.dp, end = 13.dp, top = 10.dp),
                        )
                    }
                    if (viewingPast == null && shownPetForFood != null && !petLoadFailed) {
                        FoodStrip(
                            foodCount = collectedFoodCount,
                            emoji = PetFoodUtils.getPetFoodEmoji(shownPetForFood.petType),
                            petName = shownPetForFood.name?.takeIf { it.isNotBlank() }
                                ?: PetNameUtils.getPetNameSwedish(shownPetForFood.petType),
                            // Under firandet ligger allt stilla: att mata in i en
                            // pågående nivåhöjning gör två saker samtidigt av det som
                            // ska vara ett ögonblick.
                            enabled = feedAnim.levelUp <= 0f,
                            season = season,
                            onFeedOne = { feedOne() },
                            onFeedAll = { feed(collectedFoodCount) },
                            onTilesPositioned = { foodTilesRoot = it },
                            modifier = Modifier.padding(start = 13.dp, end = 13.dp, top = 10.dp),
                        )
                    }
                    } // scenens kolumn

                    // ---- Maten i luften ----
                    // Ligger i scenen och inte i bandet: den startar i remsan och landar
                    // i djuret, alltså över gränsen mellan de två ytorna.
                    val tiles = foodTilesRoot
                    val flyFrom = tiles?.let {
                        with(density) {
                            Offset(
                                x = (it.x - scenOrigin.x).toDp().value,
                                y = (it.y - scenOrigin.y).toDp().value,
                            )
                        }
                    }
                    if (flyFrom != null) {
                        feedAnim.berries.forEach { berry ->
                            // key på id:t: utan den återanvänder Compose samma
                            // BerryInFlight för nästa bär, och animationen börjar
                            // aldrig om från noll.
                            key(berry.id) {
                                BerryInFlight(
                                    emoji = berry.emoji,
                                    from = flyFrom,
                                    to = petCenter,
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (actingAsParent) {
                        ActingAsParentBanner(
                            childName = childName,
                            onSwitchChild = onSwitchChild,
                            onExit = onExitChildView,
                        )
                    }

                    val error0 = error
                    if (error0 != null) {
                        ErrorCard(message = error0, season = season) { refreshKey++ }
                    } else if (viewingPast != null) {
                        BackToNowCard(season = season) { viewingPast = null }
                    } else {
                        TasksSection(
                            tasks = todaysTasks,
                            doneCount = doneCount,
                            allDone = allDone,
                            hasPet = pet != null,
                            petLoadFailed = petLoadFailed,
                            season = season,
                            onToggle = { toggleTask(it) },
                        )
                    }

                    if (!actingAsParent) {
                        // Långt ner och litet. Det är vägen ut, inte något ett barn ska
                        // trycka på av misstag mitt i sin lista.
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Logga ut $childName",
                                style = MaterialTheme.typography.labelLarge,
                                color = season.inkFaint,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }


    if (showSelectEggDialog) {
        SelectEggDialog(
            actingAsParent = actingAsParent,
            childId = childId,
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
    // Whose egg this is. Without it a parent choosing in the child view creates a pet
    // for themselves, because select-egg resolves the member from the device token.
    actingAsParent: Boolean = false,
    childId: String = "",
) {
    var eggTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedEgg by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isNaming by remember { mutableStateOf(false) }
    // Set once the pet is persisted. The hatching animation plays afterwards and
    // hands this to onEggSelected, so nothing is celebrated before it is saved.
    var savedPet by remember { mutableStateOf<PetResponse?>(null) }
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
            error = ApiErrors.message(e, "Kunde inte hämta äggtyper")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(isHatching) {
        val pet = savedPet
        if (isHatching && pet != null) {
            showCelebrate = false
            for (stage in 1..5) {
                hatchingStage = stage
                delay(2000)
            }
            showCelebrate = true
            delay(2000)
            onEggSelected(pet)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isHatching && !saving) onDismiss() },
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
                    val namingEggDrawable = PetImages.eggDrawable(LocalContext.current, egg)
                    if (namingEggDrawable != null) {
                        Image(
                            painter = painterResource(id = namingEggDrawable),
                            contentDescription = "Ditt ägg",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentScale = ContentScale.Fit,
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
                    if (isHatching) return@TextButton
                    if (!isNaming) {
                        // Ask for the name first. The egg is not persisted yet, so
                        // backing out here loses nothing the child was promised.
                        isNaming = true
                        showHint = false
                        return@TextButton
                    }
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            val pet = withContext(Dispatchers.IO) {
                                if (actingAsParent) {
                                    ApiClient.petsApi.selectEggForMember(
                                        memberId = childId,
                                        body = SelectEggRequest(
                                            eggType = egg,
                                            name = name.ifBlank { null },
                                        ),
                                    )
                                } else ApiClient.petsApi.selectEgg(
                                    SelectEggRequest(
                                        eggType = egg,
                                        name = name.ifBlank { null },
                                    ),
                                )
                            }
                            // Saved. Only now does the egg get to hatch.
                            savedPet = pet
                            isNaming = false
                            hatchingStage = 1
                            isHatching = true
                        } catch (e: Exception) {
                            error = ApiErrors.message(e, "Kunde inte välja ägg")
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
                        else -> "Spara och kläck"
                    },
                )
            }
        },
        dismissButton = {
            if (!isHatching && !saving) {
                TextButton(onClick = onDismiss) {
                    Text("Välj senare")
                }
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
        "golden_egg" -> "Gyllene ägg"
        "white_egg" -> "Vitt ägg"
        // Aldrig eggType. Föll ett ägg igenom stod det "golden_egg" med understreck
        // mitt i äggväljaren, vilket är precis vad som hände när lejonet och hajen
        // lades till på servern och namnen inte följde med. En reserv som lyder är
        // bättre än en som avslöjar en identifierare.
        else -> "Ägg"
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
        "golden_egg" -> "Jag ryter så att alla hör att jag vaknat."
        "white_egg" -> "Jag simmar snabbast av alla där det är djupt."
        else -> "Jag längtar efter att få träffa dig."
    }


// MARK: - Byggstenar för barnets dag

/**
 * Ett djur i samlingen. Dagens först, sedan tidigare månader.
 *
 * Visas bara när det finns något att växla mellan -- en enda cirkel är ingen växling,
 * bara en prick.
 */
@Composable
private fun PetCircle(
    petType: String,
    stage: Int,
    active: Boolean,
    season: SeasonPalette,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val drawable = PetImages.petDrawable(context, petType, stage)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f))
            .border(
                width = if (active) 2.5.dp else 1.5.dp,
                color = if (active) season.warnStrong else Color.White.copy(alpha = 0.9f),
                shape = CircleShape,
            )
            .alpha(if (active) 1f else 0.72f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (drawable != null) {
            Image(
                painter = painterResource(id = drawable),
                contentDescription = PetNameUtils.getPetNameSwedish(petType),
                modifier = Modifier.fillMaxSize().padding(2.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/**
 * Dagens uppgifter -- det som är störst på skärmen.
 *
 * Ordningen är vald efter vilken fråga ett barn öppnar appen med på en tisdagmorgon:
 * vad ska jag göra nu? Därför ligger listan på vitt så den går att läsa, medan djuret
 * bor i bandet ovanför.
 */
@Composable
private fun TasksSection(
    tasks: List<DailyChoreWithCompletionResponse>,
    doneCount: Int,
    allDone: Boolean,
    hasPet: Boolean,
    petLoadFailed: Boolean,
    season: SeasonPalette,
    onToggle: (DailyChoreWithCompletionResponse) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Dagens uppgifter",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = season.ink,
                modifier = Modifier.weight(1f),
            )
            if (tasks.isNotEmpty()) {
                Text(
                    text = "$doneCount / ${tasks.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (allDone) season.goodInk else season.inkSoft,
                )
            }
        }

        if (tasks.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = season.surface),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when {
                        petLoadFailed -> {
                            Text(
                                "Kunde inte läsa djuret",
                                fontWeight = FontWeight.SemiBold,
                                color = season.ink,
                            )
                            Text(
                                "Försök igen om en stund. Inget har gått förlorat.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = season.inkSoft,
                            )
                        }
                        !hasPet -> {
                            Text(
                                "Välj ett ägg först",
                                fontWeight = FontWeight.SemiBold,
                                color = season.ink,
                            )
                            Text(
                                "Knappen ligger i remsan ovanför.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = season.inkSoft,
                            )
                        }
                        else -> {
                            Text(
                                "Inga uppgifter idag",
                                fontWeight = FontWeight.SemiBold,
                                color = season.ink,
                            )
                            Text(
                                "Njut av dagen!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = season.inkSoft,
                            )
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = season.surface),
                border = BorderStroke(1.dp, season.cardEdge),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                    tasks.forEachIndexed { index, task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(task) }
                                .padding(vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (task.completed) season.goodInk else season.pageBg)
                                    .border(
                                        width = if (task.completed) 0.dp else 2.dp,
                                        color = season.cardEdge,
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (task.completed) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = task.chore.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (task.completed) season.inkFaint else season.ink,
                                textDecoration = if (task.completed) {
                                    TextDecoration.LineThrough
                                } else {
                                    null
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (task.chore.xpPoints > 0) {
                                Text(
                                    text = "+${task.chore.xpPoints} mat",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (task.completed) season.inkFaint else season.tipStrong,
                                )
                            }
                        }
                        if (index < tasks.size - 1) {
                            HorizontalDivider(color = season.cardEdge.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

/** Ett tidigare djur går inte att mata, och skärmen säger det i stället för att erbjuda en knapp som inte gör något. */
@Composable
private fun BackToNowCard(season: SeasonPalette, onBack: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = season.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Det här djuret är färdigväxt",
                fontWeight = FontWeight.SemiBold,
                color = season.ink,
            )
            Text(
                "Du kan inte mata det längre, men det stannar i din samling.",
                style = MaterialTheme.typography.bodyMedium,
                color = season.inkSoft,
            )
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = season.accent,
                    contentColor = season.onAccent,
                ),
            ) {
                Text("Tillbaka till nu", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, season: SeasonPalette, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = season.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(message, color = season.danger)
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = season.accent,
                    contentColor = season.onAccent,
                ),
            ) {
                Text("Försök igen")
            }
        }
    }
}

/**
 * Den gula banderollen är bärande, inte dekoration: en förälder som lägger ifrån sig
 * telefonen mitt i och tar upp den igen har inget annat sätt att se vems skärm det är.
 * Den bär också vägen ut, sedan skärmen slutade ha en egen rubrikrad.
 */
@Composable
private fun ActingAsParentBanner(
    childName: String,
    onSwitchChild: (() -> Unit)?,
    onExit: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Du ser ${possessiveSwedish(childName)} vy",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF78350F),
                modifier = Modifier.weight(1f),
            )
            if (onSwitchChild != null) {
                TextButton(onClick = onSwitchChild) {
                    Text("Byt barn", color = Color(0xFF78350F), fontWeight = FontWeight.SemiBold)
                }
            }
            if (onExit != null) {
                TextButton(onClick = onExit) {
                    Text("Tillbaka", color = Color(0xFF78350F), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Svensk genitiv: "Signes vy", men "Lukas vy" -- namn på s, x eller z får inget extra s. */
private fun possessiveSwedish(name: String): String {
    val last = name.lowercase().lastOrNull() ?: return name
    return if (last in "sxz") name else name + "s"
}

private fun monthLabel(year: Int, month: Int): String {
    val names = listOf(
        "januari", "februari", "mars", "april", "maj", "juni",
        "juli", "augusti", "september", "oktober", "november", "december",
    )
    return "${names[month.coerceIn(1, 12) - 1]} $year"
}

/**
 * Provvärden för [ChildDashboardScreen], så att skärmen går att fotografera.
 *
 * Samma siffror som iOS-fixturen: Signe en vardag i september, med uppgifterna ur den
 * färdiga listan för 7-9 år. Poängen är att de två plattformarna går att jämföra sida
 * vid sida -- skiljer de sig ska det bero på layouten, inte på olika data.
 */
data class ChildDashboardFixture(
    val pet: PetResponse?,
    val xp: XpProgressResponse?,
    val balance: WalletBalanceResponse?,
    val tasks: List<DailyChoreWithCompletionResponse>,
    val foodCount: Int,
    val history: List<PetHistoryResponse>,
    val viewingPast: Boolean = false,
) {
    companion object {
        /**
         * @param nearLevelUp tre XP från tröskeln med fem mat i räknaren, så att
         *   nivåhöjningen går att se utan en riktig session. Höjningen spelas bara som
         *   följd av en matning och aldrig ur inläst tillstånd, vilket är rätt men också
         *   betyder att den annars inte kan fotograferas.
         */
        /**
         * @param noPet barnet har sysslor men inget djur -- läget varje nytt barn börjar
         *   i, eftersom fem standardsysslor skapas när barnet läggs till. Det var just
         *   den kombinationen som gömde "Välj ägg", och den gick inte att titta på förut.
         */
        fun signe(
            allDone: Boolean = false,
            viewingPast: Boolean = false,
            nearLevelUp: Boolean = false,
            noPet: Boolean = false,
        ): ChildDashboardFixture {
            fun chore(id: String, title: String, food: Int, done: Boolean) =
                DailyChoreWithCompletionResponse(
                    chore = DailyChoreResponse(
                        id = id,
                        memberId = "child-1",
                        title = title,
                        weekdays = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"),
                        xpPoints = food,
                        isActive = true,
                    ),
                    completed = done,
                    completionId = if (done) "c-$id" else null,
                )

            return ChildDashboardFixture(
                pet = if (noPet) null else PetResponse(
                    id = "p1", memberId = "child-1", year = 2026, month = 9,
                    selectedEggType = "yellow_egg", petType = "bird", name = "Kvitter",
                    // Stadiet är nivån (calculateGrowthStage mappar 1:1), så en
                    // fixtur med stadie 4 och nivå 3 beskriver ett tillstånd som inte
                    // kan uppstå.
                    growthStage = 3, hatchedAt = null,
                    createdAt = "2026-09-01T08:00:00Z", updatedAt = "2026-09-01T08:00:00Z",
                ),
                xp = XpProgressResponse(
                    id = "x1", memberId = "child-1", year = 2026, month = 9,
                    // xpForNextLevel är hur många XP som FATTAS, inte tröskeln --
                    // servern returnerar tröskel minus currentXp. Fixturen hade 70,
                    // alltså tröskeln, vilket gav mätaren spannet 77 i stället för 35.
                    // Felet syntes inte förrän något faktiskt läste fältet.
                    currentXp = if (nearLevelUp) 67 else 42,
                    currentLevel = 3,
                    totalTasksCompleted = 28,
                    xpForNextLevel = if (nearLevelUp) 3 else 28,
                    xpInCurrentLevel = if (nearLevelUp) 32 else 7,
                ),
                balance = WalletBalanceResponse(id = "w1", memberId = "child-1", balance = 85),
                tasks = listOf(
                    chore("1", "Bädda sängen", 1, true),
                    chore("2", "Packa skolväskan", 1, true),
                    chore("3", "Plocka undan efter mellis", 1, allDone),
                    chore("4", "Kvällsrutin utan tjat", 2, allDone),
                    chore("5", "Hjälpa till med disken", 1, allDone),
                ),
                foodCount = if (nearLevelUp) 5 else if (allDone) 5 else 2,
                history = listOf(
                    PetHistoryResponse(
                        id = "h1", memberId = "child-1", year = 2026, month = 8,
                        selectedEggType = "blue_egg", petType = "dragon", finalGrowthStage = 5,
                    ),
                    PetHistoryResponse(
                        id = "h2", memberId = "child-1", year = 2026, month = 7,
                        selectedEggType = "pink_egg", petType = "unicorn", finalGrowthStage = 4,
                    ),
                ),
                viewingPast = viewingPast,
            )
        }
    }
}

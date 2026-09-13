package se.kidquest.app.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.kidquest.app.network.AdventureResponse
import se.kidquest.app.network.AdventureStateResponse
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.FrameRequest
import se.kidquest.app.network.InventoryItemResponse
import se.kidquest.app.network.LootCatalogItemResponse
import se.kidquest.app.network.LootResponse
import se.kidquest.app.network.SceneItemRequest
import se.kidquest.app.network.StartAdventureRequest
import se.kidquest.app.pet.PetImages
import se.kidquest.app.theme.LocalSeasonPalette
import se.kidquest.app.theme.SeasonHeaderBar
import se.kidquest.app.theme.SeasonPalette

/** A place a pet can be sent. `key` is stored server-side; `drawable` is the scene art in
 *  res/drawable (scene_<key>). */
private data class AdventureScene(val key: String, val label: String, val drawable: String)

private val SCENES = listOf(
    AdventureScene("glade", "Gläntan", "scene_glade"),
    AdventureScene("forest", "Skogen", "scene_forest"),
    AdventureScene("snow", "Snöstigen", "scene_snow"),
    AdventureScene("mountain", "Berget", "scene_mountain"),
    AdventureScene("cave", "Grottan", "scene_cave"),
    AdventureScene("reef", "Korallrevet", "scene_reef"),
)

@Composable
fun AdventuresScreen(
    childName: String,
    childId: String,
    onBack: () -> Unit,
    actingAsParent: Boolean = false,
) {
    val season = LocalSeasonPalette.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<AdventureStateResponse?>(null) }
    var loadedAtMillis by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var loot by remember { mutableStateOf<LootResponse?>(null) }
    var inventory by remember { mutableStateOf<List<InventoryItemResponse>>(emptyList()) }
    var catalog by remember { mutableStateOf<Map<String, LootCatalogItemResponse>>(emptyMap()) }
    var equippedFrame by remember { mutableStateOf<String?>(null) }
    var equippedSceneItem by remember { mutableStateOf<String?>(null) }
    // Increments each second so the countdowns recompose; the remaining time itself is
    // computed from the server's secondsRemaining minus wall-clock elapsed since load.
    var tick by remember { mutableStateOf(0L) }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        try {
            val loaded = withContext(Dispatchers.IO) {
                if (actingAsParent) ApiClient.adventuresApi.getStateForMember(childId)
                else ApiClient.adventuresApi.getState()
            }
            state = loaded
            loadedAtMillis = System.currentTimeMillis()
            inventory = withContext(Dispatchers.IO) {
                if (actingAsParent) ApiClient.adventuresApi.getInventoryForMember(childId)
                else ApiClient.adventuresApi.getInventory()
            }
            catalog = withContext(Dispatchers.IO) {
                ApiClient.adventuresApi.getLootCatalog().associateBy { it.id }
            }
            val pet = withContext(Dispatchers.IO) {
                val resp = if (actingAsParent) ApiClient.petsApi.getMemberPet(childId)
                else ApiClient.petsApi.getCurrentPet()
                if (resp.isSuccessful) resp.body() else null
            }
            equippedFrame = pet?.equippedFrame
            equippedSceneItem = pet?.equippedSceneItem
        } catch (e: Exception) {
            error = ApiErrors.message(e, "Kunde inte hämta äventyr")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    fun remainingSecs(adv: AdventureResponse): Long {
        // Reading tick here registers a snapshot read, so every countdown recomposes each
        // second; the value itself is server's secondsRemaining minus wall-clock elapsed.
        val elapsed = tick.let { (System.currentTimeMillis() - loadedAtMillis) / 1000 }
        return (adv.secondsRemaining - elapsed).coerceAtLeast(0)
    }

    fun startAdventure(scene: String) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                withContext(Dispatchers.IO) {
                    val body = StartAdventureRequest(scene)
                    if (actingAsParent) ApiClient.adventuresApi.startForMember(childId, body)
                    else ApiClient.adventuresApi.start(body)
                }
                // Gå direkt tillbaka till bandet så barnet genast ser djuret på äventyr.
                onBack()
            } catch (e: Exception) {
                error = ApiErrors.message(e, "Kunde inte skicka iväg djuret")
            } finally {
                busy = false
            }
        }
    }

    fun claimAdventure(id: String) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                loot = withContext(Dispatchers.IO) {
                    if (actingAsParent) ApiClient.adventuresApi.claimForMember(childId, id)
                    else ApiClient.adventuresApi.claim(id)
                }
                refreshKey++
            } catch (e: Exception) {
                error = ApiErrors.message(e, "Kunde inte hämta belöningen")
            } finally {
                busy = false
            }
        }
    }

    fun equipFrame(frameId: String?) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                val updated = withContext(Dispatchers.IO) {
                    val body = FrameRequest(frameId)
                    if (actingAsParent) ApiClient.petsApi.setFrameForMember(childId, body)
                    else ApiClient.petsApi.setFrame(body)
                }
                equippedFrame = updated.equippedFrame
            } catch (e: Exception) {
                error = ApiErrors.message(e, "Kunde inte byta ram")
            } finally {
                busy = false
            }
        }
    }

    fun equipSceneItem(itemId: String?) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                val updated = withContext(Dispatchers.IO) {
                    val body = SceneItemRequest(itemId)
                    if (actingAsParent) ApiClient.petsApi.setSceneItemForMember(childId, body)
                    else ApiClient.petsApi.setSceneItem(body)
                }
                equippedSceneItem = updated.equippedSceneItem
            } catch (e: Exception) {
                error = ApiErrors.message(e, "Kunde inte byta dekoration")
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(season.pageBg)) {
        SeasonHeaderBar(title = "Äventyr", subtitle = childName, onBack = onBack)

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val current = state

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (error != null) {
                Text(text = error!!, color = MaterialTheme.colorScheme.error)
            }

            val balance = current?.ticketBalance ?: 0L
            TicketBadge(balance = balance, season = season)

            val ongoing = current?.adventures?.filter { it.status == "ONGOING" } ?: emptyList()

            // Send-out section, only while there is a ticket to spend.
            if (balance > 0) {
                Text(
                    text = "Skicka på äventyr",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = season.ink,
                )
                SceneList(enabled = !busy, season = season, onPick = { startAdventure(it.key) })
            } else if (ongoing.isEmpty()) {
                Text(
                    text = "Klara fler nivåer för att få en äventyrsbiljett.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = season.inkFaint,
                )
            }

            if (ongoing.isNotEmpty()) {
                Text(
                    text = "På äventyr",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = season.ink,
                    modifier = Modifier.padding(top = 4.dp),
                )
                ongoing.forEach { adv ->
                    OngoingAdventureCard(
                        adventure = adv,
                        remainingSecs = remainingSecs(adv),
                        busy = busy,
                        season = season,
                        onClaim = { claimAdventure(adv.id) },
                    )
                }
            }

            val ownedIds = inventory.map { it.itemId }
            // Split owned cosmetics by catalog type. Items missing from the catalog (older art)
            // fall back to frames, matching the pre-scene-item behaviour.
            val frameIds = ownedIds.filter { catalog[it]?.type != "SCENE_ITEM" }
            val sceneItemIds = ownedIds.filter { catalog[it]?.type == "SCENE_ITEM" }

            FramesSection(
                frameIds = frameIds,
                equippedFrame = equippedFrame,
                busy = busy,
                season = season,
                onEquip = { equipFrame(it) },
            )

            DecorationsSection(
                sceneItemIds = sceneItemIds,
                catalog = catalog,
                equippedSceneItem = equippedSceneItem,
                busy = busy,
                season = season,
                onEquip = { equipSceneItem(it) },
            )
        }
    }

    val revealed = loot
    if (revealed != null) {
        LootDialog(loot = revealed, season = season, onDismiss = { loot = null })
    }
}

@Composable
private fun TicketBadge(balance: Long, season: SeasonPalette) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(season.tipBg)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("🎟️", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (balance == 1L) "1 biljett" else "$balance biljetter",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = season.tipStrong,
        )
    }
}

/** Stora, inbjudande scenkort i en kolumn. Varje kort fyller bredden, namnet ligger över
 *  bilden och ett "Skicka"-märke gör tryckhandlingen tydlig. Ett tryck skickar iväg djuret
 *  direkt. */
@Composable
private fun SceneList(
    enabled: Boolean,
    season: SeasonPalette,
    onPick: (AdventureScene) -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SCENES.forEach { scene ->
            val drawable = remember(scene.drawable) {
                val id = context.resources.getIdentifier(scene.drawable, "drawable", context.packageName)
                if (id != 0) id else null
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(season.surface)
                    .clickable(enabled = enabled) { onPick(scene) },
            ) {
                if (drawable != null) {
                    Image(
                        painter = painterResource(id = drawable),
                        contentDescription = scene.label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                // Mörk toning nedtill så namnet syns mot vilken scen som helst.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.6f),
                            )
                        )
                )
                Text(
                    text = scene.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("🗺️", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "Skicka",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun OngoingAdventureCard(
    adventure: AdventureResponse,
    remainingSecs: Long,
    busy: Boolean,
    season: SeasonPalette,
    onClaim: () -> Unit,
) {
    val ready = remainingSecs <= 0
    val scene = SCENES.firstOrNull { it.key == adventure.scene }
    val context = LocalContext.current
    val sceneDrawable = scene?.let {
        val id = context.resources.getIdentifier(it.drawable, "drawable", context.packageName)
        if (id != 0) id else null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(if (ready) season.goodBg else season.surface)
            .border(
                1.5.dp,
                if (ready) season.goodInk.copy(alpha = 0.4f) else season.cardEdge,
                RoundedCornerShape(15.dp),
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (sceneDrawable != null) {
            Image(
                painter = painterResource(id = sceneDrawable),
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text("🗺️", style = MaterialTheme.typography.headlineSmall)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scene?.label ?: "Äventyr",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = season.ink,
            )
            Text(
                text = if (ready) "Djuret är hemma!" else "Hemma om ${formatRemaining(remainingSecs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (ready) season.goodInk else season.inkFaint,
            )
        }
        if (ready) {
            TextButton(onClick = onClaim, enabled = !busy) {
                Text("Hämta", fontWeight = FontWeight.Bold, color = season.goodInk)
            }
        }
    }
}

@Composable
internal fun LootDialog(loot: LootResponse, season: SeasonPalette, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // The chest opens through five stages, then the reward fades in -- the same beat as
    // the egg hatching. Nothing is dismissible until it has opened.
    var stage by remember { mutableStateOf(1) }
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        for (s in 1..5) {
            stage = s
            delay(320)
        }
        delay(200)
        revealed = true
    }
    val chest = remember(stage) {
        val id = context.resources.getIdentifier("chest_stage$stage", "drawable", context.packageName)
        if (id != 0) id else null
    }
    val (emoji, message) = when (loot.type) {
        "EGG" -> "🥚" to "Du hittade ett nytt ägg! Det väntar i äggväljaren."
        "FRAME" -> "🖼️" to "En ny ram till din scen!"
        "SCENE_ITEM" -> "✨" to "En ny dekoration till din scen!"
        else -> "🍎" to (if (loot.quantity == 1) "Du hittade 1 mat till ditt djur!"
        else "Du hittade ${loot.quantity} mat till ditt djur!")
    }
    // Den faktiska konsten för lootet: ett ägg visar hur det ser ut, så nyfikenheten
    // byggs inför nästa månadsskifte. Faller tillbaka på emojin när konst saknas.
    val rewardDrawable = remember(loot.type, loot.ref) {
        when (loot.type) {
            "EGG" -> PetImages.eggDrawable(context, loot.ref)
            "FRAME", "SCENE_ITEM" ->
                context.resources.getIdentifier(loot.ref, "drawable", context.packageName)
                    .takeIf { it != 0 }
            else -> null
        }
    }
    AlertDialog(
        onDismissRequest = { if (revealed) onDismiss() },
        confirmButton = {
            if (revealed) {
                TextButton(onClick = onDismiss) { Text("Toppen!", fontWeight = FontWeight.Bold) }
            }
        },
        title = { Text(if (revealed) "Titta vad du fick!" else "Öppnar kistan…") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (chest != null) {
                    Image(
                        painter = painterResource(id = chest),
                        contentDescription = null,
                        modifier = Modifier.size(150.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text("🧰", style = MaterialTheme.typography.displaySmall)
                }
                if (revealed) {
                    if (rewardDrawable != null) {
                        Image(
                            painter = painterResource(id = rewardDrawable),
                            contentDescription = null,
                            modifier = Modifier.size(96.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(emoji, style = MaterialTheme.typography.displaySmall)
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = season.ink,
                    )
                }
            }
        },
    )
}

/** Owned frames, with an "Ingen ram" option to clear. Equipping one shows on the scene
 *  this month and follows the pet into the collection at month-end. */
@Composable
private fun FramesSection(
    frameIds: List<String>,
    equippedFrame: String?,
    busy: Boolean,
    season: SeasonPalette,
    onEquip: (String?) -> Unit,
) {
    if (frameIds.isEmpty()) return
    Text(
        text = "Dina ramar",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = season.ink,
        modifier = Modifier.padding(top = 4.dp),
    )
    val options: List<String?> = listOf(null) + frameIds
    options.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { frameId ->
                Box(modifier = Modifier.weight(1f)) {
                    FrameTile(
                        frameId = frameId,
                        selected = frameId == equippedFrame,
                        enabled = !busy,
                        season = season,
                        onClick = { onEquip(frameId) },
                    )
                }
            }
            repeat(3 - row.size) { Box(modifier = Modifier.weight(1f)) {} }
        }
    }
}

/** Owned scene decorations, with an "Ingen" option to clear. Like frames, an equipped
 *  decoration shows on this month's scene. */
@Composable
private fun DecorationsSection(
    sceneItemIds: List<String>,
    catalog: Map<String, LootCatalogItemResponse>,
    equippedSceneItem: String?,
    busy: Boolean,
    season: SeasonPalette,
    onEquip: (String?) -> Unit,
) {
    if (sceneItemIds.isEmpty()) return
    Text(
        text = "Dina dekorationer",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = season.ink,
        modifier = Modifier.padding(top = 4.dp),
    )
    val options: List<String?> = listOf(null) + sceneItemIds
    options.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { itemId ->
                Box(modifier = Modifier.weight(1f)) {
                    DecorationTile(
                        itemId = itemId,
                        label = itemId?.let { catalog[it]?.name },
                        selected = itemId == equippedSceneItem,
                        enabled = !busy,
                        season = season,
                        onClick = { onEquip(itemId) },
                    )
                }
            }
            repeat(3 - row.size) { Box(modifier = Modifier.weight(1f)) {} }
        }
    }
}

@Composable
private fun DecorationTile(
    itemId: String?,
    label: String?,
    selected: Boolean,
    enabled: Boolean,
    season: SeasonPalette,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val drawable = itemId?.let {
        val id = context.resources.getIdentifier(it, "drawable", context.packageName)
        if (id != 0) id else null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) season.tipBg else season.surface)
            .border(
                width = if (selected) 2.5.dp else 1.5.dp,
                color = if (selected) season.accent else season.cardEdge,
                shape = RoundedCornerShape(15.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (itemId == null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Ingen",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = season.inkFaint,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            if (drawable != null) {
                Image(
                    painter = painterResource(id = drawable),
                    contentDescription = label,
                    modifier = Modifier.size(52.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✨", style = MaterialTheme.typography.headlineSmall)
                }
            }
            Text(
                text = label ?: "Dekoration",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = season.ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FrameTile(
    frameId: String?,
    selected: Boolean,
    enabled: Boolean,
    season: SeasonPalette,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val drawable = frameId?.let {
        val id = context.resources.getIdentifier(it, "drawable", context.packageName)
        if (id != 0) id else null
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) season.tipBg else season.surface)
            .border(
                width = if (selected) 2.5.dp else 1.5.dp,
                color = if (selected) season.accent else season.cardEdge,
                shape = RoundedCornerShape(15.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (frameId == null) {
            Text(
                text = "Ingen ram",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = season.inkFaint,
                textAlign = TextAlign.Center,
            )
        } else if (drawable != null) {
            Image(
                painter = painterResource(id = drawable),
                contentDescription = "Ram",
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text("🖼️", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/** mm:ss for short waits, "X min" once it is more than a couple of minutes. */
private fun formatRemaining(secs: Long): String {
    val m = secs / 60
    val s = secs % 60
    return if (m >= 3) "$m min" else "%d:%02d".format(m, s)
}

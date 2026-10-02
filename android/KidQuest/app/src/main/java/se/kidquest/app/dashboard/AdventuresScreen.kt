package se.kidquest.app.dashboard

import se.kidquest.app.i18n.trp
import se.kidquest.app.i18n.tr
import se.kidquest.app.R
import androidx.compose.foundation.Image
import se.kidquest.app.network.XpProgressResponse
import se.kidquest.app.network.PetResponse
import se.kidquest.app.network.EggOption
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.Icons
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
private data class AdventureScene(val key: String, val labelRes: Int, val drawable: String) {
    val label: String get() = tr(labelRes)
}

private val SCENES = listOf(
    AdventureScene("glade", R.string.adventure_scene_glade, "scene_glade"),
    AdventureScene("forest", R.string.adventure_scene_forest, "scene_forest"),
    AdventureScene("snow", R.string.adventure_scene_snow, "scene_snow"),
    AdventureScene("mountain", R.string.adventure_scene_mountain, "scene_mountain"),
    AdventureScene("cave", R.string.adventure_scene_cave, "scene_cave"),
    AdventureScene("reef", R.string.adventure_scene_reef, "scene_reef"),
)

@Composable
fun AdventuresScreen(
    childName: String,
    childId: String,
    onBack: () -> Unit,
    actingAsParent: Boolean = false,
    /** Opens the child's chores, from the "no tickets" card. Null falls back to onBack. */
    onOpenChores: (() -> Unit)? = null,
    /** Debug harness only: this state instead of the network. */
    fixture: AdventureStateResponse? = null,
    /** Debug harness only: the pet, XP and eggs the "no tickets" card reads. */
    fixtureExtras: AdventuresExtras? = null,
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
    // What the "no tickets" card shows: the pet waiting, how far to the next ticket, and
    // how many rare eggs are still out there. Any of them may stay null; the card copes.
    var pet by remember { mutableStateOf<PetResponse?>(null) }
    var xp by remember { mutableStateOf<XpProgressResponse?>(null) }
    var eggs by remember { mutableStateOf<List<EggOption>?>(null) }
    // Increments each second so the countdowns recompose; the remaining time itself is
    // computed from the server's secondsRemaining minus wall-clock elapsed since load.
    var tick by remember { mutableStateOf(0L) }

    LaunchedEffect(refreshKey) {
        if (fixture != null) {
            state = fixture
            pet = fixtureExtras?.pet
            xp = fixtureExtras?.xp
            eggs = fixtureExtras?.eggs
            loadedAtMillis = System.currentTimeMillis()
            loading = false
            return@LaunchedEffect
        }
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
            val loadedPet = withContext(Dispatchers.IO) {
                val resp = if (actingAsParent) ApiClient.petsApi.getMemberPet(childId)
                else ApiClient.petsApi.getCurrentPet()
                if (resp.isSuccessful) resp.body() else null
            }
            pet = loadedPet
            equippedFrame = loadedPet?.equippedFrame
            equippedSceneItem = loadedPet?.equippedSceneItem
            // Only needed for the "no tickets" card; a failure here must not break the screen.
            if (loaded.ticketBalance == 0L) {
                xp = runCatching {
                    withContext(Dispatchers.IO) {
                        val resp = if (actingAsParent) ApiClient.xpApi.getMemberXpProgress(childId)
                        else ApiClient.xpApi.getCurrentProgress()
                        if (resp.isSuccessful) resp.body() else null
                    }
                }.getOrNull()
                eggs = runCatching {
                    withContext(Dispatchers.IO) {
                        if (actingAsParent) ApiClient.petsApi.getEggsForMember(childId)
                        else ApiClient.petsApi.getEggs()
                    }
                }.getOrNull()
            }
        } catch (e: Exception) {
            error = ApiErrors.message(e, tr(R.string.adv_load_failed))
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
                error = ApiErrors.message(e, tr(R.string.adv_send_failed))
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
                error = ApiErrors.message(e, tr(R.string.adv_reward_failed))
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
                error = ApiErrors.message(e, tr(R.string.adv_frame_failed))
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
                error = ApiErrors.message(e, tr(R.string.adv_deco_failed))
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(season.pageBg)) {
        SeasonHeaderBar(title = tr(R.string.adv_title), subtitle = childName, onBack = onBack)

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
                    text = tr(R.string.adv_send_on),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = season.ink,
                )
                SceneList(enabled = !busy, season = season, onPick = { startAdventure(it.key) })
            } else if (ongoing.isEmpty()) {
                NoTicketsCard(
                    pet = pet,
                    xp = xp,
                    season = season,
                    onOpenChores = onOpenChores ?: onBack,
                )
                Text(
                    text = tr(R.string.adv_waiting_scenes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = season.ink,
                    modifier = Modifier.padding(top = 4.dp),
                )
                SceneList(enabled = false, season = season, locked = true, onPick = {})
                val rareLeft = eggs?.count { !it.unlocked && it.rarity != "COMMON" } ?: 0
                if (rareLeft > 0) RareEggsTeaser(count = rareLeft, season = season)
            }

            if (ongoing.isNotEmpty()) {
                Text(
                    text = tr(R.string.adv_ongoing),
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
            text = trp(R.plurals.adv_tickets, balance.toInt()),
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
    /** A teaser of what a ticket opens: dimmed, a lock instead of "Send", not tappable. */
    locked: Boolean = false,
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
                    .alpha(if (locked) 0.6f else 1f)
                    .clickable(enabled = enabled) { onPick(scene) },
            ) {
                if (drawable != null) {
                    Image(
                        painter = painterResource(id = drawable),
                        contentDescription = scene.label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        colorFilter = if (locked) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.35f) }) else null,
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
                    if (locked) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = Color.Black.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        Text("🗺️", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        text = if (locked) tr(R.string.adv_needs_ticket_badge) else tr(R.string.adv_send),
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
                text = scene?.label ?: tr(R.string.adv_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = season.ink,
            )
            Text(
                text = if (ready) tr(R.string.adv_home) else tr(R.string.adv_home_in, formatRemaining(remainingSecs)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (ready) season.goodInk else season.inkFaint,
            )
        }
        if (ready) {
            TextButton(onClick = onClaim, enabled = !busy) {
                Text(tr(R.string.adv_collect), fontWeight = FontWeight.Bold, color = season.goodInk)
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
        "EGG" -> "🥚" to tr(R.string.adv_loot_egg)
        "FRAME" -> "🖼️" to tr(R.string.adv_loot_frame)
        "SCENE_ITEM" -> "✨" to tr(R.string.adv_loot_deco)
        else -> "🍎" to trp(R.plurals.adv_loot_food, loot.quantity)
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
                TextButton(onClick = onDismiss) { Text(tr(R.string.adv_awesome), fontWeight = FontWeight.Bold) }
            }
        },
        title = { Text(if (revealed) tr(R.string.adv_look) else tr(R.string.adv_opening)) },
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
        text = tr(R.string.adv_your_frames),
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
        text = tr(R.string.adv_your_decos),
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
                    text = tr(R.string.adv_none),
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
                text = label ?: tr(R.string.adv_decoration),
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
                text = tr(R.string.adv_no_frame),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = season.inkFaint,
                textAlign = TextAlign.Center,
            )
        } else if (drawable != null) {
            Image(
                painter = painterResource(id = drawable),
                contentDescription = tr(R.string.adv_frame),
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
    return if (m >= 3) tr(R.string.adv_minutes, m.toInt()) else "%d:%02d".format(m, s)
}

/**
 * Store-screenshot state: three tickets to spend, one adventure on its way home and one
 * ready to open. No inventory, since the loot catalogue's names come from the server.
 */
/** What the "no tickets" card reads, bundled for the debug harness. */
data class AdventuresExtras(
    val pet: PetResponse?,
    val xp: XpProgressResponse?,
    val eggs: List<EggOption>?,
)

/** XP still needed for the next ticket, and how far along the current stretch is (0..1). */
private fun nextTicketProgress(xp: XpProgressResponse?): Pair<Int, Float>? {
    if (xp == null) return null
    return if (xp.currentLevel < 5) {
        // Every level-up pays a ticket.
        val span = xp.xpInCurrentLevel + xp.xpForNextLevel
        if (span <= 0) null else xp.xpForNextLevel to xp.xpInCurrentLevel.toFloat() / span
    } else {
        // After the top level, every star (XP_PER_STAR = 50) pays a ticket.
        val left = xp.xpToNextStar.coerceIn(0, XP_PER_STAR)
        left to (XP_PER_STAR - left).toFloat() / XP_PER_STAR
    }
}

private const val XP_PER_STAR = 50
private val TICKET_GOLD = Color(0xFFFACC15)

/**
 * What a child with no ticket sees: the pet ready to go with a packed backpack, how
 * little is left to the next ticket, and the way to earn it. A goal, not a dead end.
 */
@Composable
private fun NoTicketsCard(
    pet: PetResponse?,
    xp: XpProgressResponse?,
    season: SeasonPalette,
    onOpenChores: () -> Unit,
) {
    val context = LocalContext.current
    val petImage = remember(pet?.petType, pet?.growthStage) {
        pet?.let { PetImages.petDrawable(context, it.petType, it.growthStage) }
    }
    val progress = nextTicketProgress(xp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(season.tipBg)
            .border(1.dp, season.badgeEdge, RoundedCornerShape(22.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy((-14).dp),
        ) {
            if (petImage != null) {
                Image(
                    painter = painterResource(id = petImage),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Image(
                painter = painterResource(id = R.drawable.adventure_backpack),
                contentDescription = null,
                modifier = Modifier.size(130.dp),
                contentScale = ContentScale.Fit,
            )
        }
        val name = pet?.name?.takeIf { it.isNotBlank() }
        Text(
            text = if (name != null) tr(R.string.adv_packed_named, name) else tr(R.string.adv_packed),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = season.ink,
            textAlign = TextAlign.Center,
        )
        if (progress != null) {
            val (left, fraction) = progress
            Text(
                text = trp(R.plurals.adv_xp_to_ticket, left, left),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = season.tipStrong,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(season.track),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                        .height(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(TICKET_GOLD),
                )
            }
        } else {
            Text(
                text = tr(R.string.adv_need_ticket),
                style = MaterialTheme.typography.bodyMedium,
                color = season.inkSoft,
                textAlign = TextAlign.Center,
            )
        }
        Button(
            onClick = onOpenChores,
            colors = ButtonDefaults.buttonColors(containerColor = season.accent, contentColor = season.onAccent),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text(tr(R.string.adv_do_chores), fontWeight = FontWeight.Bold)
        }
    }
}

/** "4 rare eggs left to discover", with a row of question-mark eggs. */
@Composable
private fun RareEggsTeaser(count: Int, season: SeasonPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(season.surface)
            .border(1.dp, season.cardEdge, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            repeat(minOf(count, 3)) {
                Box(
                    modifier = Modifier
                        .size(width = 26.dp, height = 32.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(season.track)
                        .border(2.dp, season.surface, RoundedCornerShape(percent = 50)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("?", fontWeight = FontWeight.Bold, color = season.inkSoft)
                }
            }
        }
        Text(
            text = trp(R.plurals.adv_rare_eggs_left, count, count),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = season.ink,
        )
    }
}

object AdventuresFixture {
    fun ella() = AdventureStateResponse(
        ticketBalance = 3,
        adventures = listOf(
            AdventureResponse(
                id = "a1", scene = "forest", status = "ONGOING", durationSecs = 3600,
                secondsRemaining = 1420, ready = false, lootType = null, lootRef = null, lootQty = null,
                startedAt = "2026-09-27T09:00:00Z",
            ),
            AdventureResponse(
                id = "a2", scene = "reef", status = "ONGOING", durationSecs = 1800,
                secondsRemaining = 0, ready = true, lootType = null, lootRef = null, lootQty = null,
                startedAt = "2026-09-27T08:00:00Z",
            ),
        ),
    )

    /** No tickets, nothing on its way: the state the "no tickets" card is for. */
    fun empty() = AdventureStateResponse(ticketBalance = 0, adventures = emptyList())

    /** Kvitter at level 3, 28 of 35 XP into the level (7 left), five rare eggs to find. */
    fun emptyExtras() = AdventuresExtras(
        pet = PetResponse(
            id = "p1", memberId = "child-1", year = 2026, month = 10,
            selectedEggType = "yellow_egg", petType = "bird", name = "Kvitter",
            growthStage = 3, hatchedAt = null,
            createdAt = "2026-10-01T08:00:00Z", updatedAt = "2026-10-01T08:00:00Z",
        ),
        xp = XpProgressResponse(
            id = "x1", memberId = "child-1", year = 2026, month = 10,
            currentXp = 63, currentLevel = 3, totalTasksCompleted = 30,
            xpForNextLevel = 7, xpInCurrentLevel = 28,
        ),
        eggs = listOf("orange_egg", "black_egg", "cyan_egg", "gray_egg", "silver_egg").map {
            EggOption(eggType = it, petType = "?", rarity = "RARE", unlocked = false, collected = false)
        } + EggOption(eggType = "green_egg", petType = "cat", rarity = "COMMON", unlocked = true, collected = false),
    )
}

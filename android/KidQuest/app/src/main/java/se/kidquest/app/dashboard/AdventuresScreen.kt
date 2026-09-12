package se.kidquest.app.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import se.kidquest.app.network.LootResponse
import se.kidquest.app.network.StartAdventureRequest
import se.kidquest.app.theme.LocalSeasonPalette
import se.kidquest.app.theme.SeasonHeaderBar
import se.kidquest.app.theme.SeasonPalette

/** A place a pet can be sent. Placeholder scenes until scene art exists; the key is what
 *  the server stores and what a future `scene_<key>` backdrop will key off. */
private data class AdventureScene(val key: String, val label: String, val emoji: String)

private val SCENES = listOf(
    AdventureScene("forest", "Skogen", "🌲"),
    AdventureScene("cave", "Grottan", "🪨"),
    AdventureScene("beach", "Stranden", "🏖️"),
    AdventureScene("mountain", "Berget", "⛰️"),
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
                refreshKey++
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
                SceneRow(enabled = !busy, season = season, onPick = { startAdventure(it.key) })
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

@Composable
private fun SceneRow(
    enabled: Boolean,
    season: SeasonPalette,
    onPick: (AdventureScene) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SCENES.forEach { scene ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(15.dp))
                    .background(season.surface)
                    .border(1.5.dp, season.cardEdge, RoundedCornerShape(15.dp))
                    .clickable(enabled = enabled) { onPick(scene) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(scene.emoji, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = scene.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = season.ink,
                    textAlign = TextAlign.Center,
                )
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
        Text(scene?.emoji ?: "🗺️", style = MaterialTheme.typography.headlineSmall)
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
private fun LootDialog(loot: LootResponse, season: SeasonPalette, onDismiss: () -> Unit) {
    val (emoji, message) = when (loot.type) {
        "EGG" -> "🥚" to "Du hittade ett nytt ägg! Det väntar i äggväljaren."
        "FRAME" -> "🖼️" to "En ny ram till din scen!"
        else -> "🍎" to (if (loot.quantity == 1) "Du hittade 1 mat till ditt djur!"
        else "Du hittade ${loot.quantity} mat till ditt djur!")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Toppen!", fontWeight = FontWeight.Bold) }
        },
        title = { Text("Djuret är hemma!") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(emoji, style = MaterialTheme.typography.displaySmall)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = season.ink,
                )
            }
        },
    )
}

/** mm:ss for short waits, "X min" once it is more than a couple of minutes. */
private fun formatRemaining(secs: Long): String {
    val m = secs / 60
    val s = secs % 60
    return if (m >= 3) "$m min" else "%d:%02d".format(m, s)
}

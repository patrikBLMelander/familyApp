package se.kidquest.app.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import se.kidquest.app.network.EggOption
import se.kidquest.app.network.PetHistoryResponse
import se.kidquest.app.pet.PetImages
import se.kidquest.app.pet.PetNameUtils
import se.kidquest.app.theme.SeasonPalette

/**
 * Äggväljaren som en samlingstavla.
 *
 * Var en rak lista med fjorton fullbreda kort under varandra. Två saker gjorde att den
 * behövde göras om: den var lång att bläddra igenom, och när dubbletter uteslöts krympte
 * den varje månad utan att något förklarade vart äggen tog vägen.
 *
 * Tavlan svarar på båda. De arter barnet redan samlat ligger kvar på sina platser -- som
 * djur, inte som ägg, med månaden de kom under sig -- och går inte att välja. Resten är
 * ägg. Rutan säger fortfarande "detta är allt som finns", men som en samling att fylla
 * och inte som ett lager som töms: ett barn med tre djur ser tre det har, inte elva det
 * saknar.
 *
 * Hinten flyttade hit ur ett dolt "tryck igen" och ligger i en fast rad hos anroparen.
 */
/** Ordning och svenska rubriker per sällsynthetstier. Hela väljaren grupperas på detta,
 *  så en vunnen sällsynthet hamnar i sin egen tier och inte bland de vanliga. */
private val RARITY_TIERS = listOf(
    "COMMON" to "Vanliga",
    "RARE" to "Sällsynta",
    "LEGENDARY" to "Legendariska",
    "MYTHIC" to "Mytiska",
)

@Composable
fun EggCollectionBoard(
    eggs: List<EggOption>,
    history: List<PetHistoryResponse>,
    selectedEgg: String?,
    season: SeasonPalette,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tre zoner. Valbara = upplåsta och inte redan samlade. Oupptäckta = ännu inte
    // upplåsta, visade som mystery utan att avslöja djur eller sällsynthet -- gåtan är
    // poängen, och vägen dit är äventyr. Samlade ritas ur historiken, som bär månaden.
    val selectable = eggs.filter { it.unlocked && !it.collected }
    val mystery = eggs.filter { !it.unlocked }
    val taken = remember(history) {
        history.sortedWith(
            compareByDescending<PetHistoryResponse> { it.year }.thenByDescending { it.month }
        )
    }

    // Vanliga rader och inte LazyVerticalGrid. Väljarens innehåll ligger i en Column med
    // verticalScroll, och ett lazy-rutnät därinne får obegränsad höjd -- alltså noll, och
    // tavlan ritades inte alls. Med fjorton rutor köper lathet ingenting.
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Hela väljaren grupperas per sällsynthetstier. Inom varje tier ligger de valbara
        // (upplåsta, tryckbara) äggen först och de oupptäckta ("?") efter -- så en vunnen
        // sällsynthet hamnar under "Sällsynta", inte bland de vanliga.
        RARITY_TIERS.forEach { (key, label) ->
            val sel = selectable.filter { it.rarity == key }
            val myst = mystery.filter { it.rarity == key }
            if (sel.isNotEmpty() || myst.isNotEmpty()) {
                ZoneDivider(label, season)
                val cells: List<EggOption?> = sel + List(myst.size) { null }
                cells.chunked(3).forEach { row ->
                    TileRow(row.size) { i ->
                        val cell = row[i]
                        if (cell != null) {
                            EggTile(
                                egg = cell.eggType,
                                selected = cell.eggType == selectedEgg,
                                ordinal = selectable.indexOf(cell) + 1,
                                total = selectable.size,
                                season = season,
                                onClick = { onSelect(cell.eggType) },
                            )
                        } else {
                            MysteryTile(season)
                        }
                    }
                }
            }
        }

        if (mystery.isNotEmpty()) {
            Text(
                text = "Skicka djuret på äventyr för att hitta fler.",
                style = MaterialTheme.typography.labelSmall,
                color = season.inkFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }

        if (taken.isNotEmpty()) {
            ZoneDivider("${taken.size} av ${eggs.size} samlade", season)
            taken.chunked(3).forEach { row ->
                TileRow(row.size) { i -> CollectedTile(row[i], season) }
            }
        }
    }
}

/** Den vaga avdelaren mellan zonerna: en hårfin linje och vad som följer. */
@Composable
private fun ZoneDivider(label: String, season: SeasonPalette) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(season.cardEdge))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = season.inkFaint,
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(season.cardEdge))
    }
}

/**
 * En rad med tre lika breda platser.
 *
 * Utfyllnaden finns för att en ofull sista rad annars gör de kvarvarande rutorna bredare
 * än de andra, vilket läser som att de vore viktigare.
 */
@Composable
private fun TileRow(count: Int, tile: @Composable (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            Box(modifier = Modifier.weight(1f)) { tile(i) }
        }
        repeat(3 - count) {
            Box(modifier = Modifier.weight(1f)) {}
        }
    }
}

/** En plats som redan är fylld. Visar djuret och månaden det kom. */
@Composable
private fun CollectedTile(entry: PetHistoryResponse, season: SeasonPalette) {
    val context = LocalContext.current
    val drawable = PetImages.petDrawable(context, entry.petType, entry.finalGrowthStage)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(season.goodBg)
            .border(1.5.dp, season.goodInk.copy(alpha = 0.35f), RoundedCornerShape(15.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Ramen djuret bar den månaden lägger sig runt porträttet -- en fyrkantig ram i
        // en fyrkantig ruta. Saknas ram (eller dess bild) visas djuret som förut.
        val frameDrawable = entry.frame?.let { name ->
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (id != 0) id else null
        }
        Box(
            modifier = Modifier.size(if (frameDrawable != null) 66.dp else 52.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (drawable != null) {
                Image(
                    painter = painterResource(id = drawable),
                    contentDescription = PetNameUtils.getPetNameSwedish(entry.petType),
                    modifier = Modifier.size(if (frameDrawable != null) 40.dp else 52.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("🐾", style = MaterialTheme.typography.headlineSmall)
            }
            if (frameDrawable != null) {
                Image(
                    painter = painterResource(id = frameDrawable),
                    contentDescription = null,
                    modifier = Modifier.size(66.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Text(
            text = PetNameUtils.getPetNameSwedish(entry.petType),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = season.goodInk,
            textAlign = TextAlign.Center,
        )
        Text(
            text = collectionMonthName(entry.month).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = season.goodInk.copy(alpha = 0.75f),
        )
    }
}

/**
 * Ett ägg som går att välja.
 *
 * Utan namn under. Äggkonsten ritas per art -- filerna heter `<art>_egg_stage1` -- medan
 * namnen kom från serverns identifierare, som är färger. De två gled isär när djuren
 * byttes ut: "Rött ägg" var beige med ett blått tassavtryck, "Brunt ägg" hade mörkgröna
 * fjäll, "Vitt ägg" var blått. Nio av fjorton sa emot sin egen bild.
 *
 * Att i stället döpa om dem hade gjort namnen sanna men fortfarande överflödiga -- ett ägg
 * som ser prickigt ut behöver inte texten "Prickigt ägg" under sig. Gåtan ligger i
 * ledtråden nedanför väljaren, och den stämmer.
 *
 * `ordinal` finns bara för skärmläsare. Fjorton rutor som alla heter "Ägg" går inte att
 * navigera mellan; "Ägg 3 av 12" gör det, utan att avslöja något ögat inte redan ser.
 */
@Composable
private fun EggTile(
    egg: String,
    selected: Boolean,
    ordinal: Int,
    total: Int,
    season: SeasonPalette,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val drawable = PetImages.eggDrawable(context, egg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) season.tipBg else season.surface)
            .border(
                width = if (selected) 2.5.dp else 1.5.dp,
                // Årstidens färg och inte MaterialTheme.primary: resten av barnvyn är
                // höst eller vinter, och väljaren var det enda stället som var blå.
                color = if (selected) season.accent else season.cardEdge,
                shape = RoundedCornerShape(15.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Ägget får de bildpunkter texten hade. Det är nu rutans enda innehåll, och
        // skillnaden mellan äggen är det enda barnet har att välja på.
        if (drawable != null) {
            Image(
                painter = painterResource(id = drawable),
                contentDescription = "Ägg $ordinal av $total",
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Text("🥚", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

/**
 * En oupptäckt plats. Ett tonat ägg med frågetecken tills `mystery_egg`-konsten finns --
 * den byts in automatiskt så fort en drawable med det namnet läggs till. Går inte att
 * trycka på, och avslöjar varken djur eller sällsynthet: gåtan är hela poängen.
 */
@Composable
private fun MysteryTile(season: SeasonPalette) {
    val context = LocalContext.current
    val drawable = remember {
        val id = context.resources.getIdentifier("mystery_egg", "drawable", context.packageName)
        if (id != 0) id else null
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(season.surface)
            .border(1.5.dp, season.cardEdge, RoundedCornerShape(15.dp))
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (drawable != null) {
            Image(
                painter = painterResource(id = drawable),
                contentDescription = "Oupptäckt ägg",
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "🥚",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.alpha(0.35f),
                )
                Text(
                    text = "?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = season.inkFaint,
                )
            }
        }
    }
}

/** Månadens namn, för raden under ett samlat djur. */
fun collectionMonthName(month: Int): String = when (month) {
    1 -> "januari"; 2 -> "februari"; 3 -> "mars"; 4 -> "april"
    5 -> "maj"; 6 -> "juni"; 7 -> "juli"; 8 -> "augusti"
    9 -> "september"; 10 -> "oktober"; 11 -> "november"; 12 -> "december"
    else -> ""
}

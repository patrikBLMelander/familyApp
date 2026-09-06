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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import se.kidquest.app.network.PetHistoryResponse
import se.kidquest.app.pet.EggNames
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
@Composable
fun EggCollectionBoard(
    eggTypes: List<String>,
    history: List<PetHistoryResponse>,
    selectedEgg: String?,
    season: SeasonPalette,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Ägget är nyckeln, inte arten. Historiken bär selectedEggType, så uppslagningen
    // behöver ingen kopia av backendens EGG_TO_PET_MAP -- och en kopia av den kartan är
    // precis vad som gjorde att lejonet och hajen saknade namn i somras.
    val collected = remember(history) {
        history.associateBy { it.selectedEggType.lowercase() }
    }

    // Valbara ägg först, samlade djur sist. Blandade låg de samlade som luckor mitt i
    // det barnet faktiskt ska välja bland, och bröt läsrytmen för ingenting -- de går
    // inte att välja. Samlingen är något att titta på efteråt, inte något att skanna
    // förbi under tiden.
    val available = eggTypes.filterNot { collected.containsKey(it.lowercase()) }
    val taken = eggTypes.mapNotNull { collected[it.lowercase()] }

    // Vanliga rader och inte LazyVerticalGrid. Väljarens innehåll ligger i en Column med
    // verticalScroll, och ett lazy-rutnät därinne får obegränsad höjd -- alltså noll, och
    // tavlan ritades inte alls. Med fjorton rutor köper lathet ingenting.
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        available.chunked(3).forEach { row ->
            TileRow(row.size) { i ->
                val egg = row[i]
                EggTile(
                    egg = egg,
                    selected = egg == selectedEgg,
                    season = season,
                    onClick = { onSelect(egg) },
                )
            }
        }

        if (taken.isNotEmpty()) {
            // Den vaga avdelaren. En hårfin linje och en rad som säger vad som följer --
            // tillräckligt för att ögat ska förstå att listan tar slut här, utan att bli
            // en rubrik som konkurrerar med äggen ovanför.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(season.cardEdge)
                )
                Text(
                    text = "${taken.size} av ${eggTypes.size} samlade",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = season.inkFaint,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(season.cardEdge)
                )
            }

            taken.chunked(3).forEach { row ->
                TileRow(row.size) { i -> CollectedTile(row[i], season) }
            }
        }
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
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(season.goodBg)
            .border(1.5.dp, season.goodInk.copy(alpha = 0.35f), RoundedCornerShape(15.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (drawable != null) {
            Image(
                painter = painterResource(id = drawable),
                contentDescription = PetNameUtils.getPetNameSwedish(entry.petType),
                modifier = Modifier.size(52.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text("🐾", style = MaterialTheme.typography.headlineSmall)
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

/** Ett ägg som går att välja. */
@Composable
private fun EggTile(
    egg: String,
    selected: Boolean,
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
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (drawable != null) {
            Image(
                painter = painterResource(id = drawable),
                contentDescription = EggNames.label(egg),
                modifier = Modifier.size(52.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Text("🥚", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Text(
            text = EggNames.label(egg),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) season.accent else season.inkSoft,
            textAlign = TextAlign.Center,
        )
    }
}

/** Månadens namn, för raden under ett samlat djur. */
fun collectionMonthName(month: Int): String = when (month) {
    1 -> "januari"; 2 -> "februari"; 3 -> "mars"; 4 -> "april"
    5 -> "maj"; 6 -> "juni"; 7 -> "juli"; 8 -> "augusti"
    9 -> "september"; 10 -> "oktober"; 11 -> "november"; 12 -> "december"
    else -> ""
}

package se.kidquest.app.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import se.kidquest.app.network.XpProgressResponse
import se.kidquest.app.theme.SeasonPalette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Det synliga svaret på matningen: mätaren, maten som flyger och nivåhöjningen.
 *
 * Barnen som testade sa tre saker -- att man inte ser att man matar, att man inte ser
 * progressen på nivån, och att det därför inte känns som att något händer. Alla tre
 * beskriver samma sak: en handling utan konsekvens på skärmen. Maten försvann ur en
 * siffra, XP:n gick någonstans osynligt, och när djuret väl växte sa ingen något.
 */

/**
 * Hur många XP den nuvarande nivån spänner över.
 *
 * Trösklarna ({0, 10, 35, 70, 125} i MemberXpProgress) är medvetet ojämna, så en mätare
 * mot totalen kryper knappt i början av nivå 4. Spannet räknas därför ur DTO:n i stället
 * för ur en kopia av trösklarna -- xpInCurrentLevel är hur långt in i nivån barnet är och
 * xpForNextLevel är hur mycket som fattas, så summan är nivåns egen längd. Det betyder
 * också att en ändring av trösklarna på servern följer med hit utan att någon rör den här
 * filen.
 *
 * Noll betyder högsta nivån, där det inte finns någon nästa nivå att fylla mot.
 */
fun xpSpanFor(xpInCurrentLevel: Int, xpForNextLevel: Int): Int =
    xpInCurrentLevel + xpForNextLevel

/**
 * Mätaren under djurets namn.
 *
 * Den finns hela tiden och inte bara när något händer: ett barn som öppnar appen på
 * morgonen ska kunna se hur nära nästa stadie djuret är utan att först mata det.
 */
@Composable
fun XpMeter(
    xpInLevel: Int,
    span: Int,
    level: Int,
    modifier: Modifier = Modifier,
) {
    val labelShadow = Shadow(
        color = Color.Black.copy(alpha = 0.55f),
        offset = Offset(0f, 1f),
        blurRadius = 8f,
    )
    val maxed = span <= 0
    val target = if (maxed) 1f else (xpInLevel.toFloat() / span).coerceIn(0f, 1f)
    // Samma fjäder som mätaren i förälderns vy, så de två inte rör sig olika.
    val filled by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
        label = "xpFill",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(50))
                // Mörkt spår och inte vitt. Bandet är en årstidsmålning, och ett vitt
                // spår på 32 % försvann rakt in i vattenfallet -- man såg den fyllda
                // delen men inte hur mycket som återstod, vilket är halva informationen.
                // Mörkt håller mot både ett ljust vattenfall och en vintermörk skog.
                .background(Color.Black.copy(alpha = 0.34f))
                .border(1.dp, Color.White.copy(alpha = 0.42f), RoundedCornerShape(50)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(filled)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
                    .background(
                        // Ljusare än accenten. Mot ett mörkt spår behöver fyllningen
                        // lysa, inte matcha knappen.
                        Brush.horizontalGradient(
                            listOf(Color(0xFFD9793F), Color(0xFFF5B063)),
                        )
                    ),
            )
        }
        Text(
            text = when {
                maxed -> "Största stadiet!"
                // Full mätare betyder att tröskeln just passerats. Nivån är då redan
                // uppräknad, och "35 / 35 xp till nivå 5" hade varit fel i båda leden.
                xpInLevel >= span -> "Nivå $level nådd!"
                else -> "$xpInLevel / $span xp till nivå ${level + 1}"
            },
            style = MaterialTheme.typography.labelSmall.copy(shadow = labelShadow),
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}

/** Ett stycke mat på väg från räknaren till djuret. Id:t skiljer två identiska bär. */
data class FlyingBerry(val id: Long, val emoji: String)

/**
 * Ett bär som flyger, studsar in i djuret och försvinner.
 *
 * Banan är en båge och inte en rak linje. En rak linje läser som att något teleporteras;
 * en båge läser som att någon kastar.
 *
 * Bäret rapporterar inte när det landat. [FeedAnimation] äger tiden och tar bort bäret
 * efter [BERRY_FLIGHT_MS], och båda använder samma konstant -- annars kunde XP:n räknas
 * upp innan bäret nått fram, eller bäret ligga kvar efter att det räknats.
 */
@Composable
fun BerryInFlight(
    emoji: String,
    from: Offset,
    to: Offset,
) {
    val progress = remember { Animatable(0f) }
    // Varje bär får sin egen lilla avvikelse, annars ser fyra bär ut som ett bär i
    // fyra exemplar längs exakt samma bana.
    val drift = remember { Random.nextFloat() * 34f - 17f }
    val spin = remember { 220f + Random.nextFloat() * 200f }

    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            tween(durationMillis = BERRY_FLIGHT_MS, easing = FastOutSlowInEasing),
        )
    }

    val t = progress.value
    // Lyftet är en sinusbåge: noll i båda ändarna, störst på halva vägen.
    val lift = sin(t * Math.PI).toFloat() * 52f
    val x = from.x + (to.x - from.x) * t + drift * sin(t * Math.PI).toFloat()
    val y = from.y + (to.y - from.y) * t - lift

    Text(
        text = emoji,
        // 20sp försvann mot höstmålningen. Bandet är ett detaljerat landskap och maten
        // ska läsas i förbifarten av ett barn, inte letas efter -- större, och med en
        // skugga så den lyfter från bakgrunden i stället för att smälta in i den.
        fontSize = 30.sp,
        style = MaterialTheme.typography.bodyLarge.copy(
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.5f),
                offset = Offset(0f, 2f),
                blurRadius = 10f,
            ),
        ),
        modifier = Modifier
            .offset(x = Dp(x), y = Dp(y))
            .graphicsLayer {
                rotationZ = t * spin
                val s = when {
                    t < 0.18f -> 0.6f + (t / 0.18f) * 0.6f
                    t > 0.82f -> 1.2f - ((t - 0.82f) / 0.18f) * 0.85f
                    else -> 1.2f
                }
                scaleX = s
                scaleY = s
                alpha = when {
                    t < 0.12f -> t / 0.12f
                    t > 0.88f -> 1f - (t - 0.88f) / 0.12f
                    else -> 1f
                }
            },
    )
}

/**
 * Vitblänket som täcker stadiebytet.
 *
 * Djurets konst byts under blänket, så bytet läser som en förvandling i stället för som
 * att en bild ersattes med en annan. Det är billigare än att lägga två PetVisual ovanpå
 * varandra och korsblanda dem, och det ser bättre ut.
 */
@Composable
fun LevelUpFlash(progress: Float) {
    if (progress <= 0f) return
    // Toppar tidigt och tonar långsamt ut: blänket ska hinna dölja bytet men inte
    // stå kvar och skymma djuret man just fick se växa.
    val alpha = when {
        progress < 0.22f -> progress / 0.22f
        else -> 1f - (progress - 0.22f) / 0.78f
    }.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color(0xFFFFF0DC).copy(alpha = 0f),
                    ),
                    radius = 620f,
                )
            ),
    )
}

/** "Nivå 4! Frasse växte" -- det appen aldrig sa förut. */
@Composable
fun LevelUpBanner(level: Int, petName: String, progress: Float, season: SeasonPalette) {
    if (progress <= 0f) return
    val slide = when {
        progress < 0.10f -> EaseOutBack.transform(progress / 0.10f)
        else -> 1f
    }
    val alpha = when {
        progress < 0.09f -> progress / 0.09f
        progress > 0.86f -> 1f - (progress - 0.86f) / 0.14f
        else -> 1f
    }.coerceIn(0f, 1f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = season.accent,
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = Dp(-16f + 16f * slide))
            .alpha(alpha),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp, horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Nivå $level!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = season.onAccent,
            )
            Text(
                text = "$petName växte",
                style = MaterialTheme.typography.labelMedium,
                color = season.onAccent.copy(alpha = 0.92f),
            )
        }
    }
}

/** En partikel i konfettin. Sparas en gång så banan inte ritas om vid varje bildruta. */
private data class Confetto(
    val angle: Float,
    val force: Float,
    val spin: Float,
    val delay: Float,
    val color: Color,
    val width: Float,
    val height: Float,
)

/**
 * Konfetti ur djurets mitt.
 *
 * En animation och tjugosex partiklar, inte tjugosex animationer: varje partikels läge
 * räknas ur samma framsteg. Det håller det billigt nog för en telefon som ett barn
 * använder, vilket ofta är en gammal telefon.
 */
@Composable
fun ConfettiBurst(progress: Float, origin: Offset, season: SeasonPalette) {
    if (progress <= 0f) return
    val partiklar = remember {
        val farger = listOf(
            Color(0xFFA0451E), Color(0xFFD9793F), Color(0xFFC08760),
            Color(0xFF4A5B26), Color(0xFFF0C070),
        )
        List(26) { i ->
            Confetto(
                angle = (Math.PI * 2 * i / 26).toFloat() + Random.nextFloat() * 0.3f,
                force = 70f + Random.nextFloat() * 80f,
                spin = Random.nextFloat() * 720f - 360f,
                delay = i * 0.012f,
                color = farger[i % farger.size],
                width = 5f + Random.nextFloat() * 3f,
                height = 8f + Random.nextFloat() * 4f,
            )
        }
    }

    partiklar.forEach { p ->
        val t = ((progress - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
        if (t <= 0f) return@forEach
        // Ut och sedan ned: kraften bär utåt, tyngden tar över.
        val x = origin.x + cos(p.angle) * p.force * t
        val y = origin.y + sin(p.angle) * p.force * t - 24f * t + 150f * t * t
        Box(
            modifier = Modifier
                .offset(x = Dp(x), y = Dp(y))
                .graphicsLayer {
                    rotationZ = p.spin * t
                    alpha = if (t > 0.7f) 1f - (t - 0.7f) / 0.3f else 1f
                }
                .size(width = Dp(p.width), height = Dp(p.height))
                .clip(RoundedCornerShape(1.dp))
                .background(p.color),
        )
    }
}

/** Hur länge ett bär är i luften. Delad med [FeedAnimation], som äger tiden. */
const val BERRY_FLIGHT_MS = 620

/** Mellanrummet mellan två bär som lyfter. */
private const val BERRY_STAGGER_MS = 130L

/** Hur länge fanfaren, blänket och konfettin varar. */
private const val LEVEL_UP_MS = 2400

/** Pausen mellan att mätaren slår i taket och att höjningen börjar. */
private const val LEVEL_UP_DELAY_MS = 240L

/**
 * Ökar mätaren med ett XP utan att gå till servern.
 *
 * Bara mätarens fält rörs, aldrig nivån: när xpForNextLevel når noll står mätaren kvar
 * full tills omladdningen kommer med serverns siffror för den nya nivån. Att gissa nästa
 * nivås spann hade krävt en kopia av XP_THRESHOLDS här, och en kopia av en konstant är
 * en konstant som glider.
 *
 * På högsta nivån är båda fälten noll och mätaren har inget att fylla mot, så bara
 * totalen räknas upp.
 */
fun XpProgressResponse.plusOneXp(): XpProgressResponse {
    // Noll spann är högsta nivån: det finns inget att fylla mot.
    if (xpSpanFor(xpInCurrentLevel, xpForNextLevel) == 0) {
        return copy(currentXp = currentXp + 1)
    }
    // Redan full. Att fortsätta öka xpInCurrentLevel här hade ökat spannet med, eftersom
    // spannet ÄR summan av de två -- mätaren visade "37 / 37" i stället för att stå kvar
    // på "35 / 35" när barnet matade förbi tröskeln. Totalen räknas ändå upp, för den
    // används inte av mätaren.
    if (xpForNextLevel == 0) {
        return copy(currentXp = currentXp + 1)
    }
    return copy(
        currentXp = currentXp + 1,
        xpInCurrentLevel = xpInCurrentLevel + 1,
        xpForNextLevel = xpForNextLevel - 1,
    )
}

/**
 * Sekvensen från tryck till växt djur.
 *
 * Håller vad som är i luften och hur långt firandet kommit. Datan äger den inte -- det
 * gör skärmen, som får tillbaka tre anrop: när ett bär lyfter (matsiffran ska ner), när
 * det landar (XP:n ska upp) och när ett av dem tog barnet över tröskeln.
 *
 * Höjningen spelas bara som en direkt följd av en matning, aldrig ur inläst tillstånd.
 * Det är skillnaden mellan att fira för att barnet just gjorde något och att fira för att
 * skärmen råkade ritas om -- och det senare skulle hända vid varje omladdning.
 */
class FeedAnimation {
    /** Bären som just nu är i luften. */
    var berries by mutableStateOf<List<FlyingBerry>>(emptyList())
        private set

    /** 0 när inget firande pågår, annars 0..1 genom blänk, fanfar och konfetti. */
    var levelUp by mutableStateOf(0f)
        private set

    /** Djurets skala. Puls vid varje tugga, ett större lyft vid höjningen. */
    var petPulse by mutableStateOf(1f)
        private set

    private var nextId = 0L

    /**
     * @param crossingBerry index på det bär som tar barnet över tröskeln, eller null om
     *   ingen höjning sker. Räknas i skärmen ur xpForNextLevel, som är hur många XP som
     *   fattas -- alltså är det (xpForNextLevel - 1):te bäret som korsar.
     */
    suspend fun run(
        amount: Int,
        emoji: String,
        crossingBerry: Int?,
        onBerryLifted: () -> Unit,
        onBerryLanded: () -> Unit,
        onLevelUp: () -> Unit,
    ) = coroutineScope {
        repeat(amount) { i ->
            launch {
                delay(i * BERRY_STAGGER_MS)
                one(
                    emoji = emoji,
                    crosses = i == crossingBerry,
                    onLifted = onBerryLifted,
                    onLanded = onBerryLanded,
                    onLevelUp = onLevelUp,
                )
            }
        }
    }

    /**
     * Ett enda stycke mat, hela vägen.
     *
     * Både "Mata alla" och ett tryck på en enskild bricka går genom den här, så de två
     * kan inte se olika ut. Flera samtidiga anrop är meningen och inte ett problem: ett
     * barn som trycker fyra gånger snabbt ska se fyra frön i luften, inte vänta ut det
     * första.
     */
    suspend fun one(
        emoji: String,
        crosses: Boolean,
        onLifted: () -> Unit,
        onLanded: () -> Unit,
        onLevelUp: () -> Unit,
    ) {
        val berry = FlyingBerry(nextId++, emoji)
        berries = berries + berry
        onLifted()

        delay(BERRY_FLIGHT_MS.toLong())
        berries = berries.filterNot { it.id == berry.id }
        onLanded()
        chew()

        if (crosses) {
            delay(LEVEL_UP_DELAY_MS)
            onLevelUp()
            celebrate()
        }
    }

    /** Hur många stycken mat som är i luften just nu. */
    val inFlight: Int get() = berries.size

    /** Den lilla studsen när ett bär landar. Hoptryckt och tillbaka. */
    private suspend fun chew() {
        val a = Animatable(1f)
        a.animateTo(1.12f, tween(110, easing = FastOutSlowInEasing)) { petPulse = value }
        a.animateTo(1f, tween(190, easing = FastOutSlowInEasing)) { petPulse = value }
    }

    /**
     * Höjningen. Djuret dyker, växer förbi sin nya storlek och sätter sig -- samma
     * timing som blänket, så konstbytet under blänket läser som tillväxt.
     */
    private suspend fun celebrate() = coroutineScope {
        launch {
            val a = Animatable(1f)
            a.animateTo(0.88f, tween(180, easing = FastOutSlowInEasing)) { petPulse = value }
            a.animateTo(1.24f, tween(420, easing = EaseOutBack)) { petPulse = value }
            a.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) { petPulse = value }
        }
        val a = Animatable(0f)
        a.animateTo(1f, tween(LEVEL_UP_MS, easing = LinearEasing)) { levelUp = value }
        levelUp = 0f
    }

    /** Efter ett avbrutet flöde ska ingenting hänga kvar på skärmen. */
    fun reset() {
        berries = emptyList()
        levelUp = 0f
        petPulse = 1f
    }
}

/**
 * Matremsan: maten som saker, mellan djuret och uppgifterna.
 *
 * Låg först som en stor knapp under listan. Ett barn med tio sysslor såg då aldrig
 * djuret och knappen i samma skärmbild -- bandet är 258dp och varje rad omkring 46, så
 * knappen hamnade runt 800dp ner på en skärm med drygt 850 användbara. Det gjorde hela
 * den synliga matningen meningslös för just de barnen: de tryckte och såg ingenting,
 * vilket är precis klagomålet arbetet skulle lösa.
 *
 * Remsan gör också maten till stycken i stället för en siffra. Ett tryck på en bricka
 * ger ett, [onFeedAll] ger allt. Vilket barnen väljer är i sig svaret på frågan om maten
 * ska ges styckvis -- de svarar med tummen i stället för i en enkät.
 */
@Composable
fun FoodStrip(
    foodCount: Int,
    emoji: String,
    petName: String,
    enabled: Boolean,
    season: SeasonPalette,
    onFeedOne: () -> Unit,
    onFeedAll: () -> Unit,
    onTilesPositioned: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(season.surface)
            .border(1.dp, season.cardEdge, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .onGloballyPositioned { c ->
                    val p = c.positionInRoot()
                    onTilesPositioned(
                        Offset(p.x + c.size.width / 2f, p.y + c.size.height / 2f)
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (foodCount == 0) {
                Text(
                    text = "Tomt — bocka av en syssla",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = season.inkFaint,
                )
            } else {
                // Taket är fem. Knappen tar sin bredd först, och sex brickor a 38dp
                // plus mellanrum får inte plats bredvid den på en 360dp-skärm -- de
                // radbryter, och en remsa som blir två rader är inte längre en remsa.
                val visible = minOf(foodCount, FOOD_TILE_CAP)
                repeat(visible) { i ->
                    val isOverflow = i == FOOD_TILE_CAP - 1 && foodCount > FOOD_TILE_CAP
                    FoodTile(
                        label = if (isOverflow) "+${foodCount - (FOOD_TILE_CAP - 1)}" else emoji,
                        isCount = isOverflow,
                        enabled = enabled,
                        season = season,
                        onClick = onFeedOne,
                    )
                }
            }
        }

        // Knappen finns bara när det finns något att ge. Tom remsa sa förut både
        // "Tomt -- bocka av en syssla" och "Mata Kvitter" i grått, alltså två
        // meddelanden om samma sak där det ena såg ut som ett erbjudande. Raden håller
        // sin höjd ändå tack vare heightIn ovan, så remsan hoppar inte.
        if (foodCount > 0) {
            Button(
                onClick = onFeedAll,
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = season.accent,
                    contentColor = season.onAccent,
                    disabledContainerColor = season.outlineBg,
                    disabledContentColor = season.inkFaint,
                ),
            ) {
                Text(
                    text = if (foodCount > 1) "Mata alla" else "Mata $petName",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Hur många brickor som ryms bredvid knappen innan de radbryter. */
private const val FOOD_TILE_CAP = 5

/**
 * En bricka mat.
 *
 * Ytan är 48dp medan brickan syns vara 38. Riktlinjen för en träffyta är 48, och den
 * gäller mer här än på de flesta ställen: den som trycker är sex år, och trycker hen fel
 * finns ingen ångerknapp -- maten är borta.
 */
@Composable
private fun FoodTile(
    label: String,
    isCount: Boolean,
    enabled: Boolean,
    season: SeasonPalette,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (enabled) season.tipBg else season.outlineBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = if (isCount) 13.sp else 19.sp,
                fontWeight = if (isCount) FontWeight.Bold else FontWeight.Normal,
                color = if (isCount) season.tipStrong else Color.Unspecified,
            )
        }
    }
}

/**
 * Vägen till äggvalet, på matremsans plats när det inte finns något djur att mata.
 *
 * "Välj ägg"-knappen bodde bara i uppgiftskortets tomma läge, alltså bakom villkoret att
 * barnet saknar sysslor. Ett barn som läggs till får fem standardsysslor på köpet, så
 * listan är aldrig tom och knappen syntes aldrig. På Android räddades det av att
 * dialogen öppnades av sig själv en gång -- men stängde man den fanns ingen väg tillbaka
 * förrän appen startades om, och på iOS fanns ingen väg alls.
 *
 * Här ligger den i stället där djurets sak hör hemma, synlig hela tiden.
 */
@Composable
fun ChooseEggStrip(
    season: SeasonPalette,
    onSelectEgg: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(season.surface)
            .border(1.dp, season.cardEdge, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Inget djur än",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = season.inkSoft,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).wrapContentHeight(),
        )
        Button(
            onClick = onSelectEgg,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = season.accent,
                contentColor = season.onAccent,
            ),
        ) {
            Text(
                text = "Välj ägg",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

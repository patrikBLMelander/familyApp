package se.kidquest.app.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import se.kidquest.app.network.PetHistoryResponse
import se.kidquest.app.pet.PetImages
import se.kidquest.app.pet.PetNameUtils
import se.kidquest.app.theme.SeasonPalette

/**
 * Månaden som tog slut, och djuret som ska sparas.
 *
 * [tasks] är noll när XP-historiken inte gick att hämta. Raden utelämnas då hellre än
 * att visa en nolla -- "0 sysslor avbockade" är en anklagelse, inte en sammanfattning.
 */
data class MonthFarewellData(
    val entry: PetHistoryResponse,
    val petName: String,
    val level: Int,
    val tasks: Int,
)

/**
 * Avskedet vid månadsskiftet.
 *
 * `XpService.monthlyReset()` flyttar djuret till pet_history klockan noll den första och
 * raderar det. Fram till nu innebar det att en månads avbockade sysslor slutade i att
 * djuret var borta nästa gång barnet tittade -- samma tystnad som nivåhöjningen hade
 * innan, fast större, eftersom det är hela månaden som avslutas.
 *
 * Lagret ritar sin egen samlingsrad överst i stället för att sikta på barnvyns riktiga.
 * Den riktiga ligger under ett mörkläggande lager och skulle behöva mätas genom det;
 * ett eget exemplar på samma plats gör flykten självständig och kan inte hamna fel för
 * att något under råkade flytta sig.
 *
 * Spelas en gång per barn och månad, aldrig ur inläst tillstånd -- samma regel som
 * nivåhöjningen, av samma skäl.
 */
@Composable
fun MonthFarewell(
    data: MonthFarewellData,
    season: SeasonPalette,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val drawable = PetImages.petDrawable(context, data.entry.petType, data.entry.finalGrowthStage)
    val background = PetImages.seasonalBackgroundDrawable(context)

    // 0 = djuret står stilla i mitten, 1 = det har landat i ringen.
    val flight = remember { Animatable(0f) }
    var landed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val labelShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 10f)

    Box(modifier = Modifier.fillMaxSize()) {
        if (background != null) {
            Image(
                painter = painterResource(id = background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.62f),
                        0.45f to Color.Black.copy(alpha = 0.42f),
                        1f to Color.Black.copy(alpha = 0.78f),
                    )
                )
        )

        // Samlingsraden, dit djuret ska. Ett eget exemplar; se klassens kommentar.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 14.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (landed) 0.92f else 0.24f))
                    .border(
                        width = if (landed) 2.5.dp else 1.5.dp,
                        color = if (landed) season.warnStrong else Color.White.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (landed && drawable != null) {
                    Image(
                        painter = painterResource(id = drawable),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Djuret. Flyger uppåt vänster när knappen trycks, och krymper till
            // ringens storlek på vägen.
            val t = flight.value
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .graphicsLayer {
                        val lyft = kotlin.math.sin(t * Math.PI).toFloat()
                        translationX = -t * 480f - lyft * 40f
                        translationY = -t * 900f - lyft * 60f
                        val s = 1f - t * 0.86f
                        scaleX = s
                        scaleY = s
                        rotationZ = -t * 400f
                        alpha = if (t > 0.86f) (1f - t) / 0.14f else 1f
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (drawable != null) {
                    Image(
                        painter = painterResource(id = drawable),
                        contentDescription = data.petName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            Text(
                text = "${monthLabelOf(data.entry.month)} är slut".uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(shadow = labelShadow),
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.82f),
            )
            Text(
                text = "${data.petName} blev ${stageWord(data.entry.finalGrowthStage)}",
                style = MaterialTheme.typography.headlineSmall.copy(shadow = labelShadow),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            // Fem streck, ett per stadie. Visar hur långt det kom utan att någon
            // behöver läsa en siffra.
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(5) { i ->
                    Box(
                        modifier = Modifier
                            .size(width = 28.dp, height = 5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (i < data.entry.finalGrowthStage) {
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFD9793F), Color(0xFFF5B063))
                                    )
                                } else {
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.28f),
                                            Color.White.copy(alpha = 0.28f),
                                        )
                                    )
                                }
                            )
                    )
                }
            }

            Text(
                text = buildString {
                    append("Nivå ${data.entry.finalGrowthStage} av 5")
                    if (data.tasks > 0) append(" · ${data.tasks} sysslor avbockade")
                },
                style = MaterialTheme.typography.bodyMedium.copy(shadow = labelShadow),
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 4.dp),
            )

            Button(
                onClick = {
                    if (!saving) saving = true
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 0.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = season.accent,
                    contentColor = season.onAccent,
                    disabledContainerColor = season.accent.copy(alpha = 0.7f),
                    disabledContentColor = season.onAccent.copy(alpha = 0.8f),
                ),
            ) {
                Text(
                    text = "Spara ${data.petName} i samlingen",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Du kan alltid titta på den igen",
                style = MaterialTheme.typography.labelMedium.copy(shadow = labelShadow),
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }

    LaunchedEffect(saving) {
        if (!saving) return@LaunchedEffect
        flight.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
        landed = true
        // Ett andetag med djuret på plats i ringen innan väljaren tar över. Utan den
        // hinner ingen se vart det tog vägen, vilket är hela poängen med flykten.
        delay(700)
        onSaved()
    }
}

/** "Fullvuxen" när det nådde toppen, annars något mildare. */
private fun stageWord(stage: Int): String = when (stage) {
    5 -> "fullvuxen"
    4 -> "nästan fullvuxen"
    3 -> "stor"
    2 -> "lite större"
    else -> "en liten unge"
}

private fun monthLabelOf(month: Int): String =
    collectionMonthName(month).replaceFirstChar { it.uppercase() }

package se.kidquest.app.pet

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * Debug-only gallery of every pet drawable over a chosen seasonal background.
 *
 * Exists to eyeball the exported art and to make gaps in it obvious rather than
 * silently blank. Reached from a link on the login screen that only renders in a
 * debuggable build.
 *
 * Two lookups run side by side on purpose. The audit resolves the exact resource
 * name, so a missing file is reported even when PetImages would quietly substitute
 * a lower stage. The image itself comes from PetImages, so what you see is what the
 * real screens draw. Cells where those two disagree are labelled as a fallback —
 * unicorn stage 5 is the one that does today.
 */

private val SPECIES = listOf(
    "bear", "bird", "cat", "dog", "dragon", "hydra", "kapybara",
    "lion", "panda", "rabbit", "shark", "slot", "snake", "unicorn",
)

private enum class Season(val label: String, val key: String) {
    SPRING("Vår", "spring"),
    SUMMER("Sommar", "summer"),
    AUTUMN("Höst", "autumn"),
    WINTER("Vinter", "winter"),
}

private enum class Variant(val label: String) {
    STANDALONE("Djur"),
    EGG("Ägg"),
}

private fun currentSeason(): Season =
    Season.entries.first { it.key == PetImages.currentSeason() }

private fun resNameFor(variant: Variant, species: String, stage: Int): String =
    when (variant) {
        Variant.STANDALONE -> "${species}_stage$stage"
        Variant.EGG -> "${species}_egg_stage$stage"
    }

/** Exact-name audit: 0 means that specific file is absent, fallbacks ignored. */
private fun Context.exactDrawable(variant: Variant, species: String, stage: Int): Int =
    resources.getIdentifier(resNameFor(variant, species, stage), "drawable", packageName)

/** What the real screens would actually draw here, fallbacks included. */
private fun Context.renderedDrawable(variant: Variant, species: String, stage: Int): Int =
    when (variant) {
        Variant.STANDALONE -> PetImages.petDrawable(this, species, stage)
        Variant.EGG -> PetImages.eggDrawable(this, species, stage)
    } ?: 0

@Composable
fun PetGalleryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    var season by remember { mutableStateOf(currentSeason()) }
    var variant by remember { mutableStateOf(Variant.STANDALONE) }
    var sliderScale by remember { mutableStateOf(DEFAULT_PET_SCALE) }
    var useMap by remember { mutableStateOf(true) }
    var bottomAligned by remember { mutableStateOf(false) }

    val backgroundId = PetImages.seasonalBackgroundDrawable(context, season.key) ?: 0

    val missing = remember(variant) {
        SPECIES.flatMap { sp -> (1..5).map { sp to it } }
            .filter { (sp, stage) -> context.exactDrawable(variant, sp, stage) == 0 }
            .map { (sp, stage) -> resNameFor(variant, sp, stage) }
    }
    val total = SPECIES.size * 5

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F5))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Bildgalleri",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onBack) { Text("Stäng") }
        }

        Text(
            text = "${total - missing.size} av $total bilder finns",
            style = MaterialTheme.typography.bodyMedium,
            color = if (missing.isEmpty()) Color(0xFF15703A) else Color(0xFFA8500A),
        )

        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Saknas: ${missing.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA8500A),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text("Variant", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Variant.entries.forEach { v ->
                FilterChip(
                    selected = variant == v,
                    onClick = { variant = v },
                    label = { Text(v.label) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text("Årstid", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Season.entries.forEach { s ->
                FilterChip(
                    selected = season == s,
                    onClick = { season = s },
                    label = { Text(if (s == currentSeason()) "${s.label} (nu)" else s.label) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = if (useMap) {
                "Storlek: från PET_SCALE_OVERRIDES"
            } else {
                "Storlek ${"%.2f".format(sliderScale)}  ·  paste i PET_SCALE_OVERRIDES"
            },
            style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = useMap,
                onClick = { useMap = true },
                label = { Text("Kartan") },
            )
            FilterChip(
                selected = !useMap,
                onClick = { useMap = false },
                label = { Text("Reglaget") },
            )
        }
        Slider(
            value = sliderScale,
            onValueChange = {
                sliderScale = it
                useMap = false
            },
            valueRange = 0.4f..1f,
            steps = 11,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !bottomAligned,
                onClick = { bottomAligned = false },
                label = { Text("Centrerad") },
            )
            FilterChip(
                selected = bottomAligned,
                onClick = { bottomAligned = true },
                label = { Text("Mot marken") },
            )
        }

        if (backgroundId == 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Bakgrunden season_${season.key} saknas.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA81E1E),
            )
        }

        Spacer(Modifier.height(20.dp))

        SPECIES.forEach { species ->
            Text(
                text = "${PetNameUtils.getPetNameSwedish(species)}  ·  $species",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                (1..5).forEach { stage ->
                    StageCell(
                        resName = resNameFor(variant, species, stage),
                        exactId = context.exactDrawable(variant, species, stage),
                        renderedId = context.renderedDrawable(variant, species, stage),
                        backgroundId = backgroundId,
                        stage = stage,
                        scale = if (useMap) petScaleFor(species, stage) else sliderScale,
                        showScale = useMap,
                        alignment = if (bottomAligned) Alignment.BottomCenter else Alignment.Center,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StageCell(
    resName: String,
    exactId: Int,
    renderedId: Int,
    backgroundId: Int,
    stage: Int,
    scale: Float,
    showScale: Boolean,
    alignment: Alignment,
) {
    val isFallback = exactId == 0 && renderedId != 0

    Column(
        modifier = Modifier.width(160.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f)
                .clip(RoundedCornerShape(10.dp))
                .background(if (renderedId == 0) Color(0xFFFAE7E7) else Color.White),
            contentAlignment = alignment,
        ) {
            if (backgroundId != 0 && renderedId != 0) {
                Image(
                    painter = painterResource(id = backgroundId),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (renderedId != 0) {
                Image(
                    painter = painterResource(id = renderedId),
                    contentDescription = resName,
                    modifier = Modifier
                        .fillMaxSize(scale.coerceIn(0.1f, 1f))
                        .padding(6.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = "saknas\n$resName",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA81E1E),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(6.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val scaled = showScale && scale != DEFAULT_PET_SCALE
        Text(
            text = buildString {
                append("steg $stage")
                if (isFallback) append(" · reserv")
                if (scaled) append(" · %.2f".format(scale))
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
                isFallback -> Color(0xFFA8500A)
                scaled -> Color(0xFF15703A)
                else -> Color(0xFF57534E)
            },
        )
    }
}

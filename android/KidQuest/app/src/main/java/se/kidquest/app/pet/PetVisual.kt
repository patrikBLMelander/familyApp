package se.kidquest.app.pet

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Size correction per pet, as a fraction of the box the pet is drawn in.
 *
 * The art is not cropped consistently. Measured across the standalone set, the
 * animal occupies anywhere from 47% to 100% of its canvas height, and that fraction
 * varies *between stages of the same species*. Since ContentScale.Fit scales the
 * canvas rather than the animal, an edge-to-edge crop renders roughly twice the
 * height of a sibling drawn with margin — so a pet appears to balloon when it
 * levels up. A factor here pulls the outlier back in line.
 *
 * Keys are checked most specific first: "<species>_stage<n>", then "<species>",
 * then DEFAULT_PET_SCALE. 1.0 fills the box.
 *
 * Values below bring each outlier down to roughly its own siblings' subject height.
 * Tune them with the scale slider in the debug gallery.
 */
private val PET_SCALE_OVERRIDES: Map<String, Float> = mapOf(
    // Edge-to-edge crops whose stage-siblings are drawn with margin. Values are the
    // measured ratio between the outlier and its own siblings' median subject height.
    "snake_stage2" to 0.57f,
    "slot_stage2" to 0.63f,
    "hydra_stage1" to 0.57f,
    "dragon_stage2" to 0.80f,
    // Every shark stage is cropped tight, so correct the whole species.
    "shark" to 0.80f,

    // Same defect, measured but not yet seen in the app - uncomment when it bothers
    // you. Higher stages show up rarely, which is why they surface late.
    // "hydra_stage5" to 0.57f,
    // "unicorn_stage4" to 0.61f,
    // "kapybara_stage3" to 0.75f,
    // "snake_stage5" to 0.78f,
)

/** Applies when neither the stage nor the species has an override. */
const val DEFAULT_PET_SCALE = 1f

fun petScaleFor(petType: String?, growthStage: Int): Float {
    val species = petType?.lowercase() ?: return DEFAULT_PET_SCALE
    val stage = growthStage.coerceIn(1, 5)
    return PET_SCALE_OVERRIDES["${species}_stage$stage"]
        ?: PET_SCALE_OVERRIDES[species]
        ?: DEFAULT_PET_SCALE
}

/**
 * A pet drawn as transparent standalone art over the current seasonal background.
 *
 * This is the single place that composes the two layers, so the dashboard, the pet
 * screen and the debug gallery cannot drift apart. Mirrors the web layering: the
 * background is cropped to fill, the pet is fitted inside it.
 *
 * @param scale fraction of the box the pet occupies; see PET_SCALE_OVERRIDES.
 * @param alignment where the pet sits once scaled. Bottom reads as standing on the
 *   ground, which suits a landscape background; Center keeps it floating mid-frame.
 */
@Composable
fun PetVisual(
    petType: String?,
    growthStage: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    season: String = PetImages.currentSeason(),
    cornerRadius: Int = 16,
    scale: Float = petScaleFor(petType, growthStage),
    alignment: Alignment = Alignment.Center,
) {
    val context = LocalContext.current
    val petId = PetImages.petDrawable(context, petType, growthStage)
    val backgroundId = PetImages.seasonalBackgroundDrawable(context, season)

    Box(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius.dp)),
        contentAlignment = alignment,
    ) {
        if (backgroundId != null) {
            Image(
                painter = painterResource(id = backgroundId),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (petId != null) {
            Image(
                painter = painterResource(id = petId),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize(scale.coerceIn(0.1f, 1f))
                    .padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(text = "🐾", style = MaterialTheme.typography.displayLarge)
        }
    }
}

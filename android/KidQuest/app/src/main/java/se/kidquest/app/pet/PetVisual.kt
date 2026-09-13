package se.kidquest.app.pet

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
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

/** How far the frame is scaled past the band so its solid border reaches the edge and the
 *  art's soft outer glow-cloud clips away. Tune with the frame art. */
private const val FRAME_OVERSCAN = 1.12f

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
 * @param petPadding inset around the scaled art. The default suits a large frame;
 *   a small portrait needs less or the animal all but disappears.
 * @param alignment where the pet sits once scaled. Bottom reads as standing on the
 *   ground, which suits a landscape background; Center keeps it floating mid-frame.
 * @param petScaleMultiplier a transient scale on the animal only, for a chew bounce or
 *   the growth pulse when it levels up. It has to live here rather than on the caller's
 *   Modifier: this composable draws the background and the animal in one box, so scaling
 *   the box scales the landscape too -- the pet would appear to zoom the world. Anchored
 *   at bottom centre so the animal grows up from the ground it stands on. 1f is
 *   unchanged, which is what every caller that does not animate gets.
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
    petPadding: Dp = 8.dp,
    petScaleMultiplier: Float = 1f,
    frameDrawableName: String? = null,
    sceneItemDrawableName: String? = null,
    sceneItemAtTop: Boolean = true,
    backgroundDrawableName: String? = null,
) {
    val context = LocalContext.current
    val petId = PetImages.petDrawable(context, petType, growthStage)
    // An explicit background (an adventure scene) wins over the seasonal one; the pet is
    // then drawn standing in wherever it has gone.
    val backgroundId = backgroundDrawableName?.let { drawableId(context, it) }
        ?: PetImages.seasonalBackgroundDrawable(context, season)
    val sceneItemId = sceneItemDrawableName?.let { drawableId(context, it) }
    val frameId = frameDrawableName?.let { drawableId(context, it) }

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
        // Scene decoration sits between the background and the pet, anchored to the sky or
        // the ground. A small accent -- roughly a third of the scene -- not a full overlay,
        // so it never swallows the background.
        if (sceneItemId != null) {
            Box(
                modifier = Modifier.matchParentSize().padding(10.dp),
                contentAlignment = if (sceneItemAtTop) Alignment.TopCenter else Alignment.BottomCenter,
            ) {
                Image(
                    painter = painterResource(id = sceneItemId),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.34f)
                        .fillMaxHeight(0.34f),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        if (petId != null) {
            Image(
                painter = painterResource(id = petId),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize(scale.coerceIn(0.1f, 1f))
                    .padding(petPadding)
                    .graphicsLayer {
                        scaleX = petScaleMultiplier
                        scaleY = petScaleMultiplier
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(text = "🐾", style = MaterialTheme.typography.displayLarge)
        }
        // The frame surrounds the whole scene, drawn last over everything. It is overscanned
        // slightly and the box clips the overflow: the generated frame art carries a soft
        // outer glow-cloud beyond its border, which otherwise hazes the band edges and makes
        // the border read as floating inward. Scaling past the edge pushes the solid border
        // to the edge and clips the cloud (and a sliver of the corner ornaments) away.
        if (frameId != null) {
            Image(
                painter = painterResource(id = frameId),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = FRAME_OVERSCAN
                        scaleY = FRAME_OVERSCAN
                    },
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

private fun drawableId(context: android.content.Context, name: String): Int? =
    context.resources.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 }

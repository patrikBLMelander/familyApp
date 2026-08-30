package se.kidquest.app.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.PetResponse
import se.kidquest.app.network.XpProgressResponse
import se.kidquest.app.pet.PetNameUtils
import se.kidquest.app.pet.PetVisual
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import se.kidquest.app.theme.SeasonPalette
import se.kidquest.app.theme.LocalSeasonPalette

@Composable
fun ChildPetScreen(
    childName: String,
    childId: String,
    onBack: () -> Unit,
) {
    var pet by remember { mutableStateOf<PetResponse?>(null) }
    var xp by remember { mutableStateOf<XpProgressResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var noPet by remember { mutableStateOf(false) }
    var showGiveFoodDialog by remember { mutableStateOf(false) }

    LaunchedEffect(childId) {
        loading = true
        noPet = false
        pet = null
        xp = null
        try {
            coroutineScope {
                val petDeferred = async { ApiClient.petsApi.getMemberPet(childId) }
                val xpDeferred = async {
                    kotlin.runCatching { ApiClient.xpApi.getMemberXpProgress(childId) }.getOrNull()
                }
                val petResp = petDeferred.await()
                val xpResp = xpDeferred.await()

                pet = if (petResp.isSuccessful) petResp.body() else null
                noPet = !petResp.isSuccessful || pet == null
                xp = if (xpResp?.isSuccessful == true) xpResp.body() else null
            }
        } catch (_: Exception) {
            noPet = true
            pet = null
        } finally {
            loading = false
        }
    }

    val palette = LocalSeasonPalette.current
    val backgroundBrush = petGradient(pet?.petType, palette)
    val cardColor = petCardColor(pet?.petType)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Tillbaka",
                            tint = Color.White,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = childName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (noPet) {
                    NoPetCard(cardColor = cardColor)
                } else {
                    pet?.let { p ->
                        PetCard(pet = p, xp = xp, cardColor = cardColor)

                        Spacer(modifier = Modifier.height(16.dp))

                        GiveFoodButton(
                            onClick = { showGiveFoodDialog = true },
                            petType = p.petType,
                        )
                    }
                }
            }
        }
    }

    if (showGiveFoodDialog) {
        GiveFoodDialog(
            childName = childName,
            childId = childId,
            onDismiss = { showGiveFoodDialog = false },
            onSuccess = { showGiveFoodDialog = false },
        )
    }
}

@Composable
private fun PetCard(
    pet: PetResponse,
    xp: XpProgressResponse?,
    cardColor: Color,
) {
    val petName = pet.name ?: PetNameUtils.getPetNameSwedish(pet.petType)
    val level = xp?.currentLevel ?: pet.growthStage
    val totalXp = xp?.currentXp ?: 0

    val xpThresholds = listOf(0, 10, 35, 70, 125)
    val safeLevel = level.coerceIn(1, xpThresholds.size - 1)
    val currentThreshold = xpThresholds[safeLevel - 1]
    val nextThreshold = xpThresholds[safeLevel]
    val range = (nextThreshold - currentThreshold).coerceAtLeast(1)
    val xpInLevel = xp?.xpInCurrentLevel ?: 0
    val progress = (xpInLevel.toFloat() / range).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PetVisual(
                petType = pet.petType,
                growthStage = pet.growthStage,
                contentDescription = petName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )

            Text(
                text = petName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1917),
            )

            // Level + total XP row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFACC15),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Nivå $level",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1C1917),
                )
                Text(
                    text = "·",
                    color = Color(0xFF57534E),
                )
                Text(
                    text = "$totalXp XP totalt",
                    fontSize = 15.sp,
                    color = Color(0xFF57534E),
                )
            }

            // Progress bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = Color(0xFF7C3AED),
                    trackColor = Color.Black.copy(alpha = 0.1f),
                    strokeCap = StrokeCap.Round,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$xpInLevel / $range XP till nästa nivå",
                        fontSize = 12.sp,
                        color = Color(0xFF57534E),
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF57534E),
                    )
                }
            }
        }
    }
}

@Composable
private fun GiveFoodButton(onClick: () -> Unit, petType: String) {
    val buttonColor = when (petType.lowercase()) {
        "dragon", "hydra" -> Color(0xFF6D28D9)
        "cat", "unicorn" -> Color(0xFFD97706)
        "dog", "snake", "kapybara" -> Color(0xFF15803D)
        "bird" -> Color(0xFF2563EB)
        "rabbit" -> Color(0xFFBE185D)
        "bear" -> Color(0xFF92400E)
        "panda", "slot" -> Color(0xFF374151)
        else -> Color(0xFF6D28D9)
    }

    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
    ) {
        Text(
            text = "Ge extra mat",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun NoPetCard(cardColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.85f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "🥚", fontSize = 52.sp)
            Text(
                text = "Inget djur denna månad",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1917),
            )
            Text(
                text = "Barnet har inte valt ett ägg än.",
                fontSize = 14.sp,
                color = Color(0xFF57534E),
            )
        }
    }
}

/**
 * The animal's own colours, which every label on this screen is drawn in white
 * against.
 *
 * @param palette used only for the fallback. A child who has not chosen an egg has no
 *   species colour to borrow, and the old stand-in was a pale lavender -- white text
 *   on it could not be read at all, which is the state every child is in until they
 *   pick. The season's band is dark at both ends, which is what the white here
 *   already assumes.
 */
private fun petGradient(petType: String?, palette: SeasonPalette): Brush = when (petType?.lowercase()) {
    "dragon" -> Brush.verticalGradient(listOf(Color(0xFF4C1D95), Color(0xFF1E293B)))
    "cat" -> Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF97316)))
    "dog" -> Brush.verticalGradient(listOf(Color(0xFFBBF7D0), Color(0xFF22C55E)))
    "bird" -> Brush.verticalGradient(listOf(Color(0xFFBFDBFE), Color(0xFF2563EB)))
    "rabbit" -> Brush.verticalGradient(listOf(Color(0xFFFCE7F3), Color(0xFFEC4899)))
    "bear" -> Brush.verticalGradient(listOf(Color(0xFFFEF3C7), Color(0xFF92400E)))
    "snake" -> Brush.verticalGradient(listOf(Color(0xFFDCFCE7), Color(0xFF15803D)))
    "panda" -> Brush.verticalGradient(listOf(Color(0xFFE5E7EB), Color(0xFF111827)))
    "slot" -> Brush.verticalGradient(listOf(Color(0xFFE5E7EB), Color(0xFF6B7280)))
    "hydra" -> Brush.verticalGradient(listOf(Color(0xFFC4B5FD), Color(0xFF4C1D95)))
    "unicorn" -> Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF9A8D4)))
    "kapybara" -> Brush.verticalGradient(listOf(Color(0xFFDCFCE7), Color(0xFF22C55E)))
    else -> Brush.verticalGradient(listOf(palette.headerTop, palette.headerBottom))
}

private fun petCardColor(petType: String?): Color = when (petType?.lowercase()) {
    "dragon", "hydra" -> Color(0xFFEDE9FE)
    "cat", "unicorn" -> Color(0xFFFFFBEB)
    "dog", "kapybara" -> Color(0xFFF0FDF4)
    "bird" -> Color(0xFFEFF6FF)
    "rabbit" -> Color(0xFFFDF2F8)
    "bear" -> Color(0xFFFFFBEB)
    "snake" -> Color(0xFFF0FDF4)
    "panda", "slot" -> Color(0xFFF9FAFB)
    else -> Color(0xFFF5F3FF)
}

package se.kidquest.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import se.kidquest.app.R

@Composable
fun WelcomeScreen(
    modifier: Modifier = Modifier,
    onParentClick: () -> Unit = {},
    onChildInviteClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
) {
    val backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFFE0E7FF),
            Color(0xFFE0F2FE),
        )
    )
    val cardColor = Color(0xFFFFFBEB)
    val textPrimary = Color(0xFF1C1917)
    val textSecondary = Color(0xFF57534E)
    val buttonColor = Color(0xFFBAE6FD)
    val buttonOnColor = Color(0xFF0C4A6E)

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.onboarding_hero_family),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentScale = ContentScale.Fit,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Gör tråkiga sysslor till roliga uppdrag",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Varje månad ett hemligt ägg, mata djuret med vardagsuppdrag och levla upp – plus belöningar som motiverar.",
            style = MaterialTheme.typography.bodyMedium,
            color = textSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ValueCard(
                modifier = Modifier.weight(1f),
                imageRes = R.drawable.onboarding_card_pet_xp,
                title = "Hemligt ägg varje månad",
                subtitle = "Barnen får ett nytt ägg med ett djur i – en överraskning vilket djur det blir.",
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
            )
            ValueCard(
                modifier = Modifier.weight(1f),
                imageRes = R.drawable.onboarding_card_family_overview,
                title = "Uppdrag matar djuret",
                subtitle = "Under månaden gör barnet uppgifter i vardagen för att mata och levla upp djuret.",
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
            )
            ValueCard(
                modifier = Modifier.weight(1f),
                imageRes = R.drawable.onboarding_card_rewards_savings,
                title = "Belöningar som motiverar",
                subtitle = "Koppla uppdrag till veckopeng eller små mål – om du vill.",
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onParentClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = buttonOnColor,
            ),
        ) {
            Text("Jag är förälder")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onChildInviteClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonOnColor),
        ) {
            Text("Jag är barn och har en kod")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onLoginClick) {
            Text("Logga in", color = textSecondary)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ValueCard(
    modifier: Modifier = Modifier,
    imageRes: Int,
    title: String,
    subtitle: String,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

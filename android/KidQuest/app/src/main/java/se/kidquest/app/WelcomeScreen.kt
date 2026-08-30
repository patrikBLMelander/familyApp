package se.kidquest.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.kidquest.app.theme.LocalSeasonPalette
import se.kidquest.app.theme.SeasonHeaderBar

@Composable
fun WelcomeScreen(
    modifier: Modifier = Modifier,
    onParentClick: () -> Unit = {},
    onChildInviteClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
) {
    val palette = LocalSeasonPalette.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.pageBg),
    ) {
        // The same band as every screen behind the login, so the first thing a family
        // sees is already the app they are about to be in.
        SeasonHeaderBar(
            title = "KidQuest",
            subtitle = "Gör tråkiga sysslor till roliga uppdrag",
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.onboarding_hero_family),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surface),
                contentScale = ContentScale.Crop,
            )

            // Three side-by-side cards put the longest of these titles on three lines and
            // left the row ragged. Read down instead of across: each promise gets a full
            // line to itself and the block costs less height than the cards did.
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ValueBullet(
                    title = "Hemligt ägg varje månad",
                    body = "Barnen får ett nytt djur — de vet inte vilket.",
                )
                ValueBullet(
                    title = "Uppdrag matar djuret",
                    body = "Vardagssysslor ger XP, och XP får djuret att växa.",
                )
                ValueBullet(
                    title = "Belöningar som motiverar",
                    body = "Koppla till veckopeng eller små mål, om du vill.",
                )
            }

            // Three weights, so the screen says which one is the way in: filled, outlined,
            // then a plain word. All three used to look roughly equally clickable.
            Button(
                onClick = onParentClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                ),
            ) {
                Text("Skapa en ny familj", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onChildInviteClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, palette.accent),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accent),
            ) {
                Text("Jag har en inbjudningskod", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
            }

            TextButton(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Logga in",
                    fontSize = 14.sp,
                    color = palette.accent,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ValueBullet(title: String, body: String) {
    val palette = LocalSeasonPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                // Nudged down to sit on the title's baseline row rather than its box top.
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(palette.accent),
        )
        Column {
            Text(
                text = title,
                fontSize = 14.5.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.ink,
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = body,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = palette.inkSoft,
            )
        }
    }
}

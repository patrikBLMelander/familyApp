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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import se.kidquest.app.theme.SeasonPalette

/** Vad kodrutan används till just nu. */
enum class PinPurpose {
    /** Föräldern sätter en kod för första gången. */
    SET,

    /** Någon vill lämna barnläget och måste skriva koden. */
    UNLOCK,

    /** Föräldern ändrar eller tar bort en kod som redan finns. */
    CHANGE,
}

/** Hur många fel som får göras innan en paus. */
private const val MAX_ATTEMPTS = 5

/** Hur länge pausen varar. */
private const val LOCKOUT_SECONDS = 30

/**
 * Kodrutan för att lämna barnläget.
 *
 * Koden skyddar mot ett barn som trycker "Tillbaka" av nyfikenhet, inte mot en angripare.
 * Det avgör ambitionsnivån: fyra siffror, en paus efter fem fel, och en väg ut som är
 * utloggning i stället för en lösenordskontroll.
 *
 * Utloggning som reservväg är inte en genväg för barnet. Den som loggar ut hamnar på
 * inloggningsskärmen, inte i föräldravyn -- och för att komma tillbaka in behövs
 * lösenordet, som barnet inte har. Alternativet hade varit att verifiera lösenordet här,
 * men sessionen sparar ingen e-postadress, så det hade betytt att skriva både adress och
 * lösenord på en skärm vars hela poäng är fyra siffror.
 */
@Composable
fun ParentPinDialog(
    purpose: PinPurpose,
    season: SeasonPalette,
    childName: String? = null,
    onDismiss: () -> Unit,
    /** Vid SET och CHANGE: den nya koden, eller null när den tagits bort. */
    onPinChosen: (String?) -> Unit = {},
    /** Vid UNLOCK: koden stämde. */
    onUnlocked: () -> Unit = {},
    /** Vid UNLOCK: kontrollerar en inmatning. */
    verify: (String) -> Boolean = { false },
    /** Vid UNLOCK: vägen ut för den som glömt. */
    onSignOut: () -> Unit = {},
) {
    var entered by remember { mutableStateOf("") }
    // Vid SET och CHANGE skrivs koden två gånger; det här är den första.
    var firstEntry by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }
    var lockedFor by remember { mutableStateOf(0) }

    val settingNew = purpose != PinPurpose.UNLOCK
    val confirming = firstEntry != null

    LaunchedEffect(lockedFor) {
        if (lockedFor > 0) {
            delay(1000)
            lockedFor -= 1
        }
    }

    fun submit() {
        val code = entered
        entered = ""
        if (settingNew) {
            val first = firstEntry
            if (first == null) {
                firstEntry = code
                error = null
            } else if (first != code) {
                firstEntry = null
                error = "Koderna var olika. Försök igen."
            } else {
                onPinChosen(code)
            }
            return
        }
        if (verify(code)) {
            onUnlocked()
            return
        }
        attempts += 1
        if (attempts >= MAX_ATTEMPTS) {
            attempts = 0
            lockedFor = LOCKOUT_SECONDS
            error = "För många försök."
        } else {
            error = "Fel kod."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = season.surface,
        title = {
            Text(
                text = when {
                    confirming -> "En gång till"
                    purpose == PinPurpose.UNLOCK -> "Skriv koden"
                    purpose == PinPurpose.CHANGE -> "Välj en ny kod"
                    else -> "Välj en kod"
                },
                fontWeight = FontWeight.Bold,
                color = season.ink,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = when {
                        lockedFor > 0 -> "Vänta $lockedFor sekunder."
                        confirming -> "Så att den inte blev fel."
                        purpose == PinPurpose.UNLOCK ->
                            childName?.let { "För att lämna ${possessiveSwedish(it)} vy." }
                                ?: "För att lämna barnläget."
                        else -> "Fyra siffror. Den behövs för att komma tillbaka hit."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = season.inkSoft,
                    textAlign = TextAlign.Center,
                )

                val err = error
                Text(
                    text = err ?: "",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = season.danger,
                    modifier = Modifier.heightIn(min = 20.dp),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (i < entered.length) season.accent else Color.Transparent
                                )
                                .border(
                                    width = 1.8.dp,
                                    color = if (err != null) season.danger
                                    else if (i < entered.length) season.accent
                                    else season.cardEdge,
                                    shape = RoundedCornerShape(50),
                                )
                        )
                    }
                }

                Keypad(
                    enabled = lockedFor == 0,
                    season = season,
                    onDigit = { d ->
                        if (entered.length < 4) {
                            error = null
                            entered += d
                            if (entered.length == 4) submit()
                        }
                    },
                    onBackspace = { entered = entered.dropLast(1) },
                )
            }
        },
        confirmButton = {
            if (purpose == PinPurpose.CHANGE) {
                TextButton(onClick = { onPinChosen(null) }) {
                    Text("Ta bort koden", color = season.danger, fontWeight = FontWeight.SemiBold)
                }
            } else if (purpose == PinPurpose.UNLOCK) {
                TextButton(onClick = onSignOut) {
                    Text("Glömt? Logga ut", color = season.inkSoft, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Avbryt", color = season.inkSoft, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/** Nollan i mitten på nedersta raden, som på en telefon. */
@Composable
private fun Keypad(
    enabled: Boolean,
    season: SeasonPalette,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { label ->
                    Box(
                        modifier = Modifier
                            .size(width = 76.dp, height = 52.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .then(
                                if (label.isEmpty()) Modifier
                                else Modifier
                                    .background(season.outlineBg)
                                    .border(1.dp, season.cardEdge, RoundedCornerShape(13.dp))
                                    .clickable(enabled = enabled) {
                                        if (label == "⌫") onBackspace() else onDigit(label[0])
                                    }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (label.isNotEmpty()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (enabled) season.ink else season.inkFaint,
                            )
                        }
                    }
                }
            }
        }
    }
}

package se.kidquest.app.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith
import se.kidquest.app.billing.BillingConfig
import se.kidquest.app.billing.LegalLinks
import se.kidquest.app.pet.PetVisual

/**
 * Where a parent decides whether to keep paying for this.
 *
 * The copy is deliberately a person talking rather than a feature comparison. KidQuest
 * is one parent's app, built for his own children, and that is the most persuasive true
 * thing about it -- more than any list of features would be. What it is not is a request
 * for a donation: after the trial a parent genuinely loses the ability to manage chores,
 * so the price, the renewal and how to cancel are all stated plainly rather than softened.
 * Google rejects paywalls that blur those terms, and rightly.
 *
 * The price shown is the store's own formatted string, never a hardcoded one. A
 * hardcoded "29 kr" would be wrong for anyone billed in another currency and is exactly
 * the kind of thing that gets a submission rejected.
 */
@Composable
fun PaywallScreen(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onPurchased: () -> Unit = {},
) {
    val context = LocalContext.current
    var monthly by remember { mutableStateOf<Package?>(null) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!BillingConfig.isConfigured || !Purchases.isConfigured) {
            loading = false
            message = "Prenumerationen är inte tillgänglig just nu. Försök igen senare."
            return@LaunchedEffect
        }
        Purchases.sharedInstance.getOfferingsWith(
            onError = {
                loading = false
                message = "Kunde inte hämta priset. Kontrollera din uppkoppling."
            },
            onSuccess = { offerings ->
                monthly = offerings.current?.monthly
                loading = false
                if (monthly == null) {
                    message = "Prenumerationen är inte tillgänglig just nu."
                }
            },
        )
    }

    // The store's formatted price, so currency and separators are whatever is correct
    // where the parent actually is.
    val price = monthly?.product?.price?.formatted

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFFE0E7FF), Color(0xFFE0F2FE)))
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Stäng",
                    tint = TEXT_SECONDARY,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            PetTrio()

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Tack för att ni använder KidQuest",
                fontSize = 23.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                color = TEXT_PRIMARY,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Jag är en pappa som byggde KidQuest till mina egna barn. Det började " +
                    "som ett sätt att slippa tjata om tandborstning varje morgon. Nu används " +
                    "appen hemma hos er också, och det betyder mycket för mig.",
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = TEXT_SECONDARY,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Jag utvecklar appen själv, på kvällar och helger. Servern och allt runt " +
                    "omkring kostar pengar varje månad, och 29 kronor per familj är vad som gör " +
                    "att jag kan fortsätta.",
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = TEXT_SECONDARY,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "— Patrik",
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = TEXT_PRIMARY,
            )

            Spacer(modifier = Modifier.height(20.dp))

            ContinuesAsBefore()

            Spacer(modifier = Modifier.height(18.dp))

            // The price must never be guessed, so there is no fallback string -- and the
            // whole block goes rather than leaving an empty row behind, which is what it
            // did when the store first failed to answer.
            price?.let {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = it,
                        fontSize = 26.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = ACCENT,
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "per månad, för hela familjen",
                        modifier = Modifier.padding(bottom = 2.dp),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TEXT_SECONDARY,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Förnyas automatiskt tills du avslutar. Du avslutar när du vill i " +
                        "Google Play och behåller tiden du redan betalat för.",
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = MUTED,
                )
            }

            message?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF991B1B),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val activity = context.findActivity()
                    val pkg = monthly
                    if (activity == null || pkg == null) {
                        message = "Kunde inte starta köpet. Försök igen."
                        return@Button
                    }
                    working = true
                    message = null
                    Purchases.sharedInstance.purchaseWith(
                        purchaseParams = PurchaseParams.Builder(activity, pkg).build(),
                        onError = { error, userCancelled ->
                            working = false
                            // A parent who backed out has not hit a problem, so say nothing.
                            if (!userCancelled) {
                                message = "Köpet gick inte igenom: ${error.message}"
                            }
                        },
                        onSuccess = { _, _ ->
                            working = false
                            onPurchased()
                        },
                    )
                },
                enabled = !loading && !working && monthly != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ACCENT,
                    contentColor = Color.White,
                ),
            ) {
                when {
                    loading || working -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    price != null -> Text(
                        text = "Fortsätt för $price/mån",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    else -> Text(
                        text = "Fortsätt",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Google requires a restore path: a parent who reinstalls, or switches
                // phone, must be able to get back what they already paid for.
                FooterLink("Återställ köp") {
                    if (!Purchases.isConfigured) return@FooterLink
                    working = true
                    Purchases.sharedInstance.restorePurchasesWith(
                        onError = {
                            working = false
                            message = "Kunde inte återställa köp: ${it.message}"
                        },
                        onSuccess = {
                            working = false
                            onPurchased()
                        },
                    )
                }
                Dot()
                FooterLink("Villkor") { context.open(LegalLinks.TERMS) }
                Dot()
                FooterLink("Integritetspolicy") { context.open(LegalLinks.PRIVACY) }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private val TEXT_PRIMARY = Color(0xFF1C1917)
private val TEXT_SECONDARY = Color(0xFF57534E)
private val MUTED = Color(0xFF78716C)
private val ACCENT = Color(0xFF0C4A6E)
private val CARD = Color(0xFFFFFBEB)

/**
 * Three of the animals, drawn through PetVisual so they sit on the current season's
 * background like everywhere else in the app.
 *
 * Fixed species rather than the family's own pets: this screen has no child in scope,
 * and fetching three more things to decorate a paywall is not worth the latency.
 */
@Composable
private fun PetTrio() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PetCircle("cat", 2, 64.dp)
        Spacer(modifier = Modifier.size((-12).dp))
        PetCircle("dragon", 3, 76.dp)
        Spacer(modifier = Modifier.size((-12).dp))
        PetCircle("lion", 4, 64.dp)
    }
}

@Composable
private fun PetCircle(petType: String, stage: Int, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(CARD)
            .padding(2.5.dp),
    ) {
        PetVisual(
            petType = petType,
            growthStage = stage,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = (size.value / 2).toInt(),
            alignment = Alignment.BottomCenter,
            petPadding = 2.dp,
        )
    }
}

/** What an expired trial does *not* take away. Reassurance, not a feature list. */
@Composable
private fun ContinuesAsBefore() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CARD)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = "DET HÄR FORTSÄTTER SOM VANLIGT",
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = MUTED,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(
                "Obegränsat antal barn och sysslor",
                "Ett nytt djur att ta hand om varje månad",
                "Plånbok med sparmål",
                "Hela familjen, på alla telefoner",
            ).forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF166534),
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.size(9.dp))
                    Text(
                        text = line,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = TEXT_PRIMARY,
                    )
                }
            }
        }
    }
}

@Composable
private fun FooterLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        fontSize = 12.sp,
        color = ACCENT,
        textDecoration = TextDecoration.Underline,
    )
}

@Composable
private fun Dot() {
    Text(text = "·", fontSize = 12.sp, color = Color(0xFFA8A29E))
}

private fun Context.open(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/** Compose gives a Context; Play billing needs the Activity behind it. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Preview(name = "Betalvägg", widthDp = 390, heightDp = 900, showBackground = true)
@Composable
private fun PaywallPreview() {
    // No RevenueCat in a preview, so the price and the button's label are absent --
    // which is also exactly what a parent would see if the store never answered.
    PaywallScreen()
}

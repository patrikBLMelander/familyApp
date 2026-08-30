package se.kidquest.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.kidquest.app.billing.Billing
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.LinkDeviceByTokenRequest
import se.kidquest.app.session.TokenStore
import se.kidquest.app.theme.LocalSeasonPalette
import se.kidquest.app.theme.SeasonHeaderBar

@Composable
fun ChildInviteLoginScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onLoginAsChild: (childId: String, childName: String) -> Unit,
    onLoginAsAdult: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var inviteCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun performLink(token: String) {
        if (token.isBlank()) return
        loading = true
        status = null
        scope.launch {
            try {
                val deviceToken = UUID.randomUUID().toString()
                val member = withContext(Dispatchers.IO) {
                    ApiClient.familyMembersApi.linkDeviceByInviteToken(
                        LinkDeviceByTokenRequest(
                            inviteToken = token,
                            deviceToken = deviceToken,
                        ),
                    )
                }
                TokenStore.setSession(
                    deviceToken = deviceToken,
                    memberId = member.id,
                    memberName = member.name,
                    role = member.role,
                    familyId = member.familyId,
                )
                Billing.identify(member.familyId)
                // A code pairs whoever it was issued for, and a second parent is paired
                // exactly like a child. Sending everyone to the child dashboard put a
                // parent into their own children's view -- the stored session had the
                // right role all along, so relaunching corrected it, which is what made
                // this look like a display glitch rather than routing.
                if (member.role.equals("CHILD", ignoreCase = true)) {
                    onLoginAsChild(member.id, member.name)
                } else {
                    onLoginAsAdult()
                }
            } catch (e: Exception) {
                status = ApiErrors.message(e, "Kunde inte koppla enheten. Kontrollera koden.")
            } finally {
                loading = false
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (!raw.isNullOrBlank()) {
            val token = when {
                raw.contains("/invite/") -> raw.substringAfterLast("/invite/").trim().substringBefore("?").trim()
                else -> raw.trim()
            }
            if (token.isNotBlank()) performLink(token)
        }
    }

    val palette = LocalSeasonPalette.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.pageBg),
    ) {
        // The same band as the welcome and login screens either side of this one: a
        // child taps through from one of them, and the app should not change colour
        // under them on the way.
        SeasonHeaderBar(
            title = "Koppla din enhet",
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Be någon i familjen visa koden eller QR-koden – eller skanna den här nedan.",
                fontSize = 14.sp,
                lineHeight = 19.sp,
                color = palette.inkSoft,
            )

            // Scanning is the way in this screen is built around -- a child with a phone
            // pointed at a parent's screen never has to read a code at all -- so it takes
            // the filled weight the sibling screens give their primary action.
            Button(
                onClick = {
                    scanLauncher.launch(
                        ScanOptions().apply {
                            setPrompt("Skanna inbjudningskoden")
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        }
                    )
                },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                ),
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Skanna QR-kod", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            // A rule through the words, so typing reads as the alternative to scanning
            // rather than as the next step after it.
            OrDivider(text = "eller skriv in koden")

            EntryTextField(
                label = "Inbjudningskod",
                value = inviteCode,
                onValueChange = { inviteCode = it.trim(); status = null },
            )

            OutlinedButton(
                onClick = {
                    if (inviteCode.isBlank()) {
                        status = "Ange en kod"
                        return@OutlinedButton
                    }
                    performLink(inviteCode)
                },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, palette.accent),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accent),
            ) {
                Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (loading) "Kopplar…" else "Koppla denna enhet",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            status?.let { msg ->
                Text(
                    text = msg,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.danger,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // The way out stays at the foot as a word, matching the login screen's footer:
        // the arrow in the band is the same route, but it is a small target for a child
        // who has just failed to scan and is looking for a way back, not up.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 22.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(palette.cardEdge),
            )
            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Tillbaka", fontSize = 14.sp, color = palette.accent)
            }
        }
    }
}

@Composable
private fun OrDivider(text: String) {
    val palette = LocalSeasonPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(palette.cardEdge),
        )
        Text(text = text, fontSize = 13.sp, color = palette.inkSoft)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(palette.cardEdge),
        )
    }
}

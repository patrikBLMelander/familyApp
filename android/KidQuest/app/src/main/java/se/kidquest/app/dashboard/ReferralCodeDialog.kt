package se.kidquest.app.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.RedeemReferralRequest
import se.kidquest.app.theme.SeasonPalette

/**
 * Lets a family enter a referral code, tying their account to the affiliate who gave it.
 *
 * First-touch on the server, so entering a second code once attributed does nothing --
 * the family belongs to whoever's code came first. The code is the only input; the family
 * is derived from the device token by the auth interceptor.
 */
@Composable
fun ReferralCodeDialog(season: SeasonPalette, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (code.isBlank() || submitting) return
        submitting = true
        error = null
        scope.launch {
            try {
                val response = ApiClient.affiliatesApi.redeem(RedeemReferralRequest(code.trim()))
                if (response.isSuccessful) {
                    done = true
                } else {
                    error = if (response.code() == 400) {
                        "Koden känns inte igen. Dubbelkolla stavningen."
                    } else {
                        "Något gick fel (${response.code()})."
                    }
                }
            } catch (e: Exception) {
                error = ApiErrors.message(e, "Kunde inte registrera koden")
            } finally {
                submitting = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(if (done) "Tack!" else "Värvningskod") },
        text = {
            if (done) {
                Text("Din kod är registrerad. Tack för att du stödjer den som tipsade dig om KidQuest!")
            } else {
                Column {
                    Text("Har du fått en kod av någon? Skriv in den så kopplas ditt konto till dem.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = {
                            code = it.uppercase().trim()
                            error = null
                        },
                        singleLine = true,
                        label = { Text("Kod") },
                        isError = error != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(error!!, color = season.danger)
                    }
                }
            }
        },
        confirmButton = {
            if (done) {
                TextButton(onClick = onDismiss) { Text("Klar") }
            } else {
                TextButton(enabled = code.isNotBlank() && !submitting, onClick = { submit() }) {
                    Text(if (submitting) "…" else "Registrera")
                }
            }
        },
        dismissButton = {
            if (!done) {
                TextButton(onClick = onDismiss, enabled = !submitting) { Text("Avbryt") }
            }
        },
    )
}

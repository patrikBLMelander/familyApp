package se.kidquest.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import se.kidquest.app.R
import se.kidquest.app.i18n.AppLanguage
import se.kidquest.app.i18n.L10n
import se.kidquest.app.i18n.Money
import se.kidquest.app.i18n.tr
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.UpdateCurrencyRequest

/** Language names are always written in their own language, so anyone can find theirs. */
private val LANGUAGE_NAMES = listOf(
    "sv" to "Svenska",
    "en" to "English",
    "de" to "Deutsch",
    "es" to "Español",
)

/** Picks the app language, or "follow the phone" (null). Saved to [memberId] too. */
@Composable
fun LanguageDialog(memberId: String?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(L10n.chosenLanguage()) }
    var saving by remember { mutableStateOf(false) }
    val options = listOf<Pair<String?, String>>(null to tr(R.string.language_follow_phone)) + LANGUAGE_NAMES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.language_title)) },
        text = {
            Column {
                options.forEach { (code, name) ->
                    ChoiceRow(label = name, selected = selected == code) { selected = code }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    if (selected == L10n.chosenLanguage()) {
                        onDismiss()
                        return@TextButton
                    }
                    saving = true
                    scope.launch { AppLanguage.choose(memberId, selected) }
                },
            ) { Text(if (saving) tr(R.string.common_saving) else tr(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(tr(R.string.common_cancel)) }
        },
    )
}

/** Parents only: the family's currency for every wallet amount. */
@Composable
fun CurrencyDialog(familyId: String?, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(Money.familyCurrency) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.currency_title)) },
        text = {
            Column {
                Text(
                    tr(R.string.currency_explain),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Money.SUPPORTED.forEach { code ->
                    ChoiceRow(label = "$code · ${Money.symbol(code)}", selected = selected == code) { selected = code }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && familyId != null,
                onClick = {
                    val id = familyId ?: return@TextButton
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            val family = ApiClient.familyApi.updateCurrency(id, UpdateCurrencyRequest(selected))
                            Money.remember(family.currency ?: selected)
                            onSaved(family.currency ?: selected)
                        } catch (e: Exception) {
                            error = ApiErrors.message(e, tr(R.string.currency_save_failed))
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) tr(R.string.common_saving) else tr(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(tr(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

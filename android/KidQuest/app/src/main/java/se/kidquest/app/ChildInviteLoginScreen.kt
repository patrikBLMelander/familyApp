package se.kidquest.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.LinkDeviceByTokenRequest
import se.kidquest.app.session.TokenStore
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun ChildInviteLoginScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onLoginAsChild: (childId: String, childName: String) -> Unit,
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
                )
                onLoginAsChild(member.id, member.name)
            } catch (e: Exception) {
                status = e.message ?: "Kunde inte koppla enheten. Kontrollera koden."
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

    val backgroundBrush = Brush.verticalGradient(
        listOf(Color(0xFFE0E7FF), Color(0xFFE0F2FE))
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
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Koppla din enhet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Be mamma eller pappa visa koden eller QR-koden – eller skanna här nedan.",
            style = MaterialTheme.typography.bodyMedium,
            color = textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
        OutlinedButton(
            onClick = {
                scanLauncher.launch(
                    ScanOptions().apply {
                        setPrompt("Skanna inbjudningskoden")
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    }
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !loading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonOnColor),
        ) {
            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text("Skanna QR-kod")
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "eller skriv in koden",
            style = MaterialTheme.typography.bodySmall,
            color = textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it.trim(); status = null },
            label = { Text("Inbjudningskod") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = buttonOnColor,
                unfocusedBorderColor = textSecondary.copy(alpha = 0.5f),
                focusedLabelColor = buttonOnColor,
                unfocusedLabelColor = textSecondary,
                cursorColor = buttonOnColor,
                focusedTextColor = textPrimary,
                unfocusedTextColor = textPrimary,
            ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (inviteCode.isBlank()) {
                    status = "Ange en kod"
                    return@Button
                }
                performLink(inviteCode)
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = buttonOnColor,
            ),
        ) {
            Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text(if (loading) "Kopplar…" else "Koppla denna enhet")
        }

        status?.let { msg ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        TextButton(onClick = onBack) {
            Text("Tillbaka", color = textSecondary)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}


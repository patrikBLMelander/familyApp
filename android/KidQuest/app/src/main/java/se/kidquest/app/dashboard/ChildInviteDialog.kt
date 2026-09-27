package se.kidquest.app.dashboard

import se.kidquest.app.i18n.tr
import se.kidquest.app.R
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.FamilyMemberResponse

@Composable
fun ChildInviteDialog(
    child: FamilyMemberResponse,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var inviteToken by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(child.id) {
        try {
            val response = ApiClient.familyMembersApi.generateInviteToken(child.id)
            inviteToken = response.token
        } catch (e: Exception) {
            error = ApiErrors.message(e, tr(R.string.invite_failed))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.invite_title, child.name)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    error != null -> {
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    inviteToken == null -> {
                        Text(tr(R.string.invite_generating))
                    }
                    else -> {
                        Text(
                            text = tr(R.string.invite_instructions, child.name),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        inviteToken?.let { token ->
                            QrCodeImage(content = token, sizeDp = 200)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Text(
                            text = inviteToken!!,
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(inviteToken!!))
                                copied = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (copied) tr(R.string.invite_copied) else tr(R.string.invite_copy))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(R.string.common_close))
            }
        },
    )
}

@Composable
private fun QrCodeImage(
    content: String,
    sizeDp: Int,
) {
    var bitmap by remember(content) { mutableStateOf<Bitmap?>(null) }
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.dp.roundToPx() }

    LaunchedEffect(content, sizePx) {
        bitmap = withContext(Dispatchers.Default) {
            try {
                BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            } catch (_: Exception) {
                null
            }
        }
    }

    bitmap?.asImageBitmap()?.let { imageBitmap ->
        Image(
            bitmap = imageBitmap,
            contentDescription = tr(R.string.invite_qr_cd),
            modifier = Modifier.size(sizeDp.dp),
        )
    }
}


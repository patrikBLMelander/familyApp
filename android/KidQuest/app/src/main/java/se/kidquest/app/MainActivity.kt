package se.kidquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.EmailLoginRequest
import se.kidquest.app.network.RegisterFamilyRequest
import se.kidquest.app.dashboard.AddFamilyMemberDialog
import se.kidquest.app.dashboard.AdultDashboardScreen
import se.kidquest.app.dashboard.ChildDashboardScreen
import se.kidquest.app.dashboard.ChildPetScreen
import se.kidquest.app.dashboard.ChildTasksScreen
import se.kidquest.app.dashboard.ChildWalletScreen
import se.kidquest.app.dashboard.FamilyTasksScreen
import se.kidquest.app.session.TokenStore
import se.kidquest.app.ui.theme.KidQuestTheme
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private sealed class AppScreen {
    data object Loading : AppScreen()
    data object Welcome : AppScreen()
    data object Register : AppScreen()
    data object Auth : AppScreen()
    data object Home : AppScreen()
    data object ChildInviteLogin : AppScreen()
    data class ChildDashboard(val childId: String, val childName: String) : AppScreen()
    data class ChildPet(val childId: String, val childName: String) : AppScreen()
    data class ChildWallet(val childId: String, val childName: String, val isOwnWallet: Boolean) : AppScreen()
    data class ChildTasks(val childId: String, val childName: String) : AppScreen()
    data object FamilyTasks : AppScreen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TokenStore.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            KidQuestTheme {
                var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Loading) }
                var showAddMemberDialog by remember { mutableStateOf(false) }
                var dashboardRefreshKey by remember { mutableStateOf(0) }
                // När barn öppnar uppgifter/plånbok från sin dashboard: Tillbaka ska gå tillbaka till barnets startsida
                var returnToChildDashboard by remember { mutableStateOf<Pair<String, String>?>(null) }
                var showAddMemberHint by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    TokenStore.load()
                    // A device token alone does not say who it belongs to. Routing on
                    // its presence alone sent children straight into the adult
                    // dashboard, where they could add and delete tasks and open the
                    // family wallet.
                    var session = TokenStore.getSession()

                    if (session != null && session.isIncomplete) {
                        // Paired before the role was stored locally: resolve it once
                        // rather than making the family re-pair the device.
                        session = runCatching {
                            withContext(Dispatchers.IO) {
                                ApiClient.familyMembersApi.getMemberByDeviceToken(session!!.deviceToken)
                            }
                        }.getOrNull()?.let { member ->
                            TokenStore.setSession(
                                deviceToken = session!!.deviceToken,
                                memberId = member.id,
                                memberName = member.name,
                                role = member.role,
                            )
                            TokenStore.getSession()
                        }
                    }

                    currentScreen = when {
                        session == null -> AppScreen.Welcome
                        // Still unknown after the lookup: the token is stale or the
                        // member is gone. Sending them to the adult view would be the
                        // original bug, so start over instead.
                        session.isIncomplete -> {
                            TokenStore.clearToken()
                            AppScreen.Welcome
                        }
                        session.isChild -> AppScreen.ChildDashboard(
                            childId = session.memberId!!,
                            childName = session.memberName ?: "Barn",
                        )
                        else -> AppScreen.Home
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (val screen = currentScreen) {
                        AppScreen.Loading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        AppScreen.Welcome -> WelcomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            onParentClick = { currentScreen = AppScreen.Register },
                            onChildInviteClick = { currentScreen = AppScreen.ChildInviteLogin },
                            onLoginClick = { currentScreen = AppScreen.Auth },
                        )
                        AppScreen.Register -> RegisterScreen(
                            modifier = Modifier.padding(innerPadding),
                            onRegisterSuccess = {
                                showAddMemberHint = true
                                currentScreen = AppScreen.Home
                            },
                            onBackToLogin = { currentScreen = AppScreen.Auth },
                        )
                        AppScreen.Auth -> AuthScreen(
                            modifier = Modifier.padding(innerPadding),
                            onLoginSuccess = { currentScreen = AppScreen.Home },
                            onChildInviteLogin = { currentScreen = AppScreen.ChildInviteLogin },
                        )
                        AppScreen.Home -> AdultDashboardScreen(
                            modifier = Modifier.padding(innerPadding),
                            refreshKey = dashboardRefreshKey,
                            showAddMemberHint = showAddMemberHint,
                            onDismissAddMemberHint = { showAddMemberHint = false },
                            onLogout = {
                                scope.launch {
                                    TokenStore.clearToken()
                                    currentScreen = AppScreen.Auth
                                }
                            },
                            onAddFamilyMember = {
                                showAddMemberHint = false
                                showAddMemberDialog = true
                            },
                            onChildPet = { id, name ->
                                returnToChildDashboard = null
                                currentScreen = AppScreen.ChildPet(id, name)
                            },
                            onChildWallet = { id, name ->
                                returnToChildDashboard = null
                                currentScreen = AppScreen.ChildWallet(id, name, isOwnWallet = false)
                            },
                            onChildTasks = { id, name ->
                                returnToChildDashboard = null
                                currentScreen = AppScreen.ChildTasks(id, name)
                            },
                            onFamilyTasks = {
                                currentScreen = AppScreen.FamilyTasks
                            },
                        )
                        AppScreen.FamilyTasks -> FamilyTasksScreen(
                            onBack = { currentScreen = AppScreen.Home },
                        )
                        AppScreen.ChildInviteLogin -> ChildInviteLoginScreen(
                            modifier = Modifier.padding(innerPadding),
                            onBack = { currentScreen = AppScreen.Welcome },
                            onLoginAsChild = { childId, childName ->
                                currentScreen = AppScreen.ChildDashboard(childId, childName)
                            },
                        )
                        is AppScreen.ChildDashboard -> ChildDashboardScreen(
                            childName = screen.childName,
                            childId = screen.childId,
                            onBack = {
                                scope.launch {
                                    TokenStore.clearToken()
                                    currentScreen = AppScreen.Auth
                                }
                            },
                            onOpenTasks = {
                                returnToChildDashboard = screen.childId to screen.childName
                                currentScreen = AppScreen.ChildTasks(screen.childId, screen.childName)
                            },
                            onOpenWallet = {
                                returnToChildDashboard = screen.childId to screen.childName
                                currentScreen = AppScreen.ChildWallet(screen.childId, screen.childName, isOwnWallet = true)
                            },
                        )
                        is AppScreen.ChildPet -> ChildPetScreen(
                            childName = screen.childName,
                            childId = screen.childId,
                            onBack = {
                                returnToChildDashboard = null
                                currentScreen = AppScreen.Home
                            },
                        )
                        is AppScreen.ChildWallet -> ChildWalletScreen(
                            childName = screen.childName,
                            childId = screen.childId,
                            isOwnWallet = screen.isOwnWallet,
                            onBack = {
                                val toDashboard = returnToChildDashboard
                                returnToChildDashboard = null
                                currentScreen = if (toDashboard != null) {
                                    AppScreen.ChildDashboard(toDashboard.first, toDashboard.second)
                                } else AppScreen.Home
                            },
                        )
                        is AppScreen.ChildTasks -> ChildTasksScreen(
                            childName = screen.childName,
                            childId = screen.childId,
                            onBack = {
                                val toDashboard = returnToChildDashboard
                                returnToChildDashboard = null
                                currentScreen = if (toDashboard != null) {
                                    AppScreen.ChildDashboard(toDashboard.first, toDashboard.second)
                                } else AppScreen.Home
                            },
                        )
                    }
                }

                if (showAddMemberDialog) {
                    AddFamilyMemberDialog(
                        onDismiss = { showAddMemberDialog = false },
                        onSuccess = {
                            showAddMemberDialog = false
                            dashboardRefreshKey++
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    onLoginSuccess: () -> Unit = {},
    onChildInviteLogin: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    val emailState = remember { mutableStateOf("") }
    val passwordState = remember { mutableStateOf("") }
    val statusState = remember { mutableStateOf("Inte inloggad") }
    val loadingState = remember { mutableStateOf(false) }

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
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Logga in",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Förälder eller vårdnadshavare",
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
                OutlinedTextField(
                    value = emailState.value,
                    onValueChange = { emailState.value = it },
                    label = { Text("E-post") },
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
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = passwordState.value,
                    onValueChange = { passwordState.value = it },
                    label = { Text("Lösenord") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        scope.launch {
                            loadingState.value = true
                            statusState.value = "Loggar in..."
                            try {
                                val api = ApiClient.authApi
                                val response = api.loginByEmail(
                                    EmailLoginRequest(
                                        email = emailState.value,
                                        password = passwordState.value,
                                    ),
                                )
                                TokenStore.setSession(
                                    deviceToken = response.deviceToken,
                                    memberId = response.member.id,
                                    memberName = response.member.name,
                                    role = response.member.role,
                                )
                                onLoginSuccess()
                            } catch (e: Exception) {
                                statusState.value = "Fel: ${e.message}"
                            } finally {
                                loadingState.value = false
                            }
                        }
                    },
                    enabled = !loadingState.value,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonOnColor,
                    ),
                ) {
                    Text(text = if (loadingState.value) "Loggar in..." else "Logga in")
                }
                if (statusState.value != "Loggar in..." && statusState.value != "Inte inloggad") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = statusState.value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Barn i familjen?", style = MaterialTheme.typography.bodyMedium, color = textSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onChildInviteLogin) {
            Text("Jag är barn och har en kod", color = buttonOnColor)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun RegisterScreen(
    modifier: Modifier = Modifier,
    onRegisterSuccess: () -> Unit = {},
    onBackToLogin: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    val familyNameState = remember { mutableStateOf("") }
    val parentNameState = remember { mutableStateOf("") }
    val emailState = remember { mutableStateOf("") }
    val passwordState = remember { mutableStateOf("") }
    val statusState = remember { mutableStateOf<String?>(null) }
    val loadingState = remember { mutableStateOf(false) }

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
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Skapa familj",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Registrera dig som förälder och bjud in dina barn.",
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
                OutlinedTextField(
                    value = familyNameState.value,
                    onValueChange = { familyNameState.value = it },
                    label = { Text("Familjens namn") },
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
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = parentNameState.value,
                    onValueChange = { parentNameState.value = it },
                    label = { Text("Ditt namn") },
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
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = emailState.value,
                    onValueChange = { emailState.value = it },
                    label = { Text("E-post") },
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
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = passwordState.value,
                    onValueChange = { passwordState.value = it },
                    label = { Text("Lösenord") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (familyNameState.value.isBlank() || parentNameState.value.isBlank() || emailState.value.isBlank() || passwordState.value.isBlank()) {
                            statusState.value = "Fyll i alla fält."
                            return@Button
                        }
                        scope.launch {
                            loadingState.value = true
                            statusState.value = null
                            try {
                                val api = ApiClient.authApi
                                val response = api.registerFamily(
                                    RegisterFamilyRequest(
                                        familyName = familyNameState.value.trim(),
                                        adminName = parentNameState.value.trim(),
                                        adminEmail = emailState.value.trim(),
                                        password = passwordState.value,
                                    ),
                                )
                                TokenStore.setSession(
                                    deviceToken = response.deviceToken,
                                    memberId = response.admin.id,
                                    memberName = response.admin.name,
                                    role = response.admin.role,
                                )
                                onRegisterSuccess()
                            } catch (e: Exception) {
                                statusState.value = "Fel: ${e.message}"
                            } finally {
                                loadingState.value = false
                            }
                        }
                    },
                    enabled = !loadingState.value,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonOnColor,
                    ),
                ) {
                    Text(text = if (loadingState.value) "Skapar familj..." else "Skapa familj")
                }
                statusState.value?.let { msg ->
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

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBackToLogin) {
            Text("Har redan konto? Logga in", color = textSecondary)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(text = "Välkommen till KidQuest!")
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onLogout) {
            Text("Logga ut")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    KidQuestTheme {
        AuthScreen()
    }
}
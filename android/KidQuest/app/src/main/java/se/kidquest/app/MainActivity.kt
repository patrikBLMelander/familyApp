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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.kidquest.app.billing.Billing
import se.kidquest.app.dashboard.AddFamilyMemberDialog
import se.kidquest.app.dashboard.AdultDashboardScreen
import se.kidquest.app.dashboard.ChildDashboardScreen
import se.kidquest.app.dashboard.ChildPetScreen
import se.kidquest.app.dashboard.ChildTasksScreen
import se.kidquest.app.dashboard.ChildWalletScreen
import se.kidquest.app.dashboard.FamilyTasksScreen
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.PasswordResetRequest
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.EmailLoginRequest
import se.kidquest.app.network.RegisterFamilyRequest
import se.kidquest.app.paywall.PaywallScreen
import se.kidquest.app.session.PrefsStore
import se.kidquest.app.session.TokenStore
import se.kidquest.app.ui.theme.KidQuestTheme

private sealed class AppScreen {
    data object Loading : AppScreen()
    data object Welcome : AppScreen()
    data object Register : AppScreen()
    data object Auth : AppScreen()
    data object Home : AppScreen()
    data object ChildInviteLogin : AppScreen()
    data class ChildDashboard(val childId: String, val childName: String) : AppScreen()

    /**
     * A parent looking at, or acting for, a child. Separate from ChildDashboard so the
     * screen knows to call the member-scoped endpoints: authenticated as the parent,
     * "feed" has to name the child or it feeds the parent's own pet.
     */
    data class ChildViewAsParent(val childId: String, val childName: String) : AppScreen()
    data class ChildPet(val childId: String, val childName: String) : AppScreen()
    data class ChildWallet(val childId: String, val childName: String, val isOwnWallet: Boolean) : AppScreen()
    data class ChildTasks(val childId: String, val childName: String) : AppScreen()
    data object FamilyTasks : AppScreen()
    data object Paywall : AppScreen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TokenStore.init(applicationContext)
        PrefsStore.init(applicationContext)
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

                    Billing.identify(session?.familyId)

                    currentScreen = when {
                        session == null -> AppScreen.Welcome
                        // Still unknown after the lookup: the token is stale or the
                        // member is gone. Sending them to the adult view would be the
                        // original bug, so start over instead.
                        session.isIncomplete -> {
                            TokenStore.clearToken()
                                    Billing.forget()
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
                                    Billing.forget()
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
                            onChildView = { id, name ->
                                currentScreen = AppScreen.ChildViewAsParent(id, name)
                            },
                            onFamilyTasks = {
                                currentScreen = AppScreen.FamilyTasks
                            },
                            onOpenPaywall = { currentScreen = AppScreen.Paywall },
                            onFamilyDeleted = {
                                scope.launch {
                                    TokenStore.clearToken()
                                    Billing.forget()
                                    // Welcome rather than Auth: the account is gone, so
                                    // a login screen would be inviting them to sign in to
                                    // something that no longer exists.
                                    currentScreen = AppScreen.Welcome
                                }
                            },
                        )
                        AppScreen.FamilyTasks -> FamilyTasksScreen(
                            onBack = { currentScreen = AppScreen.Home },
                        )
                        AppScreen.Paywall -> PaywallScreen(
                            modifier = Modifier.padding(innerPadding),
                            onDismiss = { currentScreen = AppScreen.Home },
                            onPurchased = {
                                // Entitlement is the server's answer, and it arrives by
                                // webhook rather than from the SDK. Refreshing is the most
                                // that can be done here; if RevenueCat has not delivered
                                // yet the banner lingers for a moment, which is honest.
                                dashboardRefreshKey++
                                currentScreen = AppScreen.Home
                            },
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
                                    Billing.forget()
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
                        is AppScreen.ChildViewAsParent -> ChildDashboardScreen(
                            childName = screen.childName,
                            childId = screen.childId,
                            actingAsParent = true,
                            // No token juggling: the parent stays signed in throughout,
                            // which is the entire point of this route.
                            onExitChildView = { currentScreen = AppScreen.Home },
                            onSwitchChild = { currentScreen = AppScreen.Home },
                            onBack = { currentScreen = AppScreen.Home },
                            onOpenTasks = {
                                returnToChildDashboard = screen.childId to screen.childName
                                currentScreen = AppScreen.ChildTasks(screen.childId, screen.childName)
                            },
                            onOpenWallet = {
                                returnToChildDashboard = screen.childId to screen.childName
                                currentScreen = AppScreen.ChildWallet(screen.childId, screen.childName, isOwnWallet = false)
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
    val showForgotPassword = remember { mutableStateOf(false) }
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
                                    familyId = response.member.familyId,
                                )
                                Billing.identify(response.member.familyId)
                                onLoginSuccess()
                            } catch (e: Exception) {
                                statusState.value = ApiErrors.message(e, "Kunde inte logga in.")
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

                // Until this existed, a parent who forgot their password was locked out
                // for good: another parent could set a new one, which does nothing at
                // all for a single-parent family.
                TextButton(
                    onClick = { showForgotPassword.value = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Glömt lösenordet?",
                        color = buttonOnColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (showForgotPassword.value) {
                    ForgotPasswordDialog(onDismiss = { showForgotPassword.value = false })
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
                                    familyId = response.family.id,
                                )
                                Billing.identify(response.family.id)
                                onRegisterSuccess()
                            } catch (e: Exception) {
                                statusState.value = ApiErrors.message(e, "Kunde inte skapa kontot.")
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
/**
 * Asks for a reset link.
 *
 * Says the same thing whether or not the address has an account, because the server
 * does. Anything else would turn this dialog into a way of finding out which families
 * use KidQuest -- and the answer would be a list of parents.
 */
@Composable
private fun ForgotPasswordDialog(onDismiss: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(if (sent) "Kolla din mejl" else "Glömt lösenordet?") },
        text = {
            if (sent) {
                Text(
                    "Om adressen finns hos oss har vi skickat en länk dit. Den gäller i " +
                        "en timme. Titta i skräpposten om den inte dyker upp.",
                )
            } else {
                Column {
                    Text("Skriv din e-postadress så skickar vi en länk för att välja ett nytt lösenord.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("E-post") },
                        singleLine = true,
                        enabled = !sending,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                    error?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text("Klart") }
            } else {
                TextButton(
                    enabled = email.isNotBlank() && !sending,
                    onClick = {
                        sending = true
                        error = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    ApiClient.authApi.requestPasswordReset(
                                        PasswordResetRequest(email.trim()),
                                    )
                                }
                                // Deliberately not checking the response body: a failure
                                // to send must look exactly like a success, or this
                                // becomes an account-enumeration tool.
                                sent = true
                            } catch (e: Exception) {
                                error = ApiErrors.message(e, "Kunde inte skicka just nu.")
                            } finally {
                                sending = false
                            }
                        }
                    },
                ) {
                    Text(if (sending) "Skickar…" else "Skicka länk")
                }
            }
        },
        dismissButton = {
            if (!sent) {
                TextButton(onClick = onDismiss, enabled = !sending) { Text("Avbryt") }
            }
        },
    )
}

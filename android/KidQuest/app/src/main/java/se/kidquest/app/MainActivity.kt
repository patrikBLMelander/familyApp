package se.kidquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import se.kidquest.app.dashboard.RecurringAllowanceScreen
import se.kidquest.app.dashboard.FamilyTasksScreen
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.PasswordResetRequest
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.EmailLoginRequest
import se.kidquest.app.network.RegisterFamilyRequest
import se.kidquest.app.paywall.PaywallScreen
import se.kidquest.app.session.PrefsStore
import se.kidquest.app.theme.LocalSeasonPalette
import se.kidquest.app.theme.SeasonHeaderBar
import se.kidquest.app.session.TokenStore
import se.kidquest.app.ui.theme.KidQuestTheme
import androidx.activity.compose.BackHandler
import android.content.pm.ApplicationInfo

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
    data class ChildWallet(
        val childId: String,
        val childName: String,
        val isOwnWallet: Boolean,
        /** Reached from inside the child's own view, where nothing may be changed. */
        val fromChildView: Boolean = false,
    ) : AppScreen()
    data class ChildTasks(val childId: String, val childName: String) : AppScreen()
    data class RecurringAllowance(val childId: String, val childName: String) : AppScreen()
    data object FamilyTasks : AppScreen()
    data object Paywall : AppScreen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TokenStore.init(applicationContext)
        PrefsStore.init(applicationContext)
        enableEdgeToEdge()
        // Debug-only: render one screen without touching the session, so a screen
        // that sits behind a login can be photographed for review at all. The
        // alternative is signing the family out to look at their own welcome screen,
        // which costs a real login to undo.
        //   adb shell am start -n se.kidquest.app/.MainActivity --es kq_screen welcome
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val forcedScreen = if (debuggable) intent?.getStringExtra("kq_screen") else null

        setContent {
            // Hoisted above the theme because the theme is what consumes it. Null until
            // a parent picks a side, which is what lets a fresh install follow the phone.
            var darkMode by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(Unit) { darkMode = PrefsStore.darkMode() }
            val themeScope = rememberCoroutineScope()

            KidQuestTheme(dark = darkMode) {
                var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Loading) }
                var showAddMemberDialog by remember { mutableStateOf(false) }
                var dashboardRefreshKey by remember { mutableStateOf(0) }
                // När barn öppnar uppgifter/plånbok från sin dashboard: Tillbaka ska gå tillbaka till barnets startsida
                var returnToChildDashboard by remember { mutableStateOf<Pair<String, String>?>(null) }
                var showAddMemberHint by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    when (forcedScreen) {
                        "welcome" -> { currentScreen = AppScreen.Welcome; return@LaunchedEffect }
                        "auth" -> { currentScreen = AppScreen.Auth; return@LaunchedEffect }
                        "register" -> { currentScreen = AppScreen.Register; return@LaunchedEffect }
                        "childinvite" -> { currentScreen = AppScreen.ChildInviteLogin; return@LaunchedEffect }
                    }

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
                    // One answer to "where does back go", shared by the arrow drawn on
                    // the screen and the phone's own back gesture. The gesture used to be
                    // unhandled, so it left the app from any depth instead of stepping
                    // back one screen.
                    //
                    // Null marks a root, where Android expects back to leave. The child's
                    // dashboard is one on purpose: its own back affordance signs the child
                    // out, and wiring the gesture to that would have a child logging
                    // themselves out by reflex -- needing a parent and a fresh code to undo.
                    val backAction: (() -> Unit)? = when (val s = currentScreen) {
                        AppScreen.Loading, AppScreen.Welcome, AppScreen.Home -> null
                        is AppScreen.ChildDashboard -> null

                        AppScreen.Register, AppScreen.Auth, AppScreen.ChildInviteLogin ->
                            ({ currentScreen = AppScreen.Welcome })

                        AppScreen.FamilyTasks, AppScreen.Paywall ->
                            ({ currentScreen = AppScreen.Home })

                        is AppScreen.ChildViewAsParent ->
                            ({ currentScreen = AppScreen.Home })

                        is AppScreen.ChildPet -> ({
                            returnToChildDashboard = null
                            currentScreen = AppScreen.Home
                        })

                        is AppScreen.RecurringAllowance -> ({
                            currentScreen = AppScreen.ChildWallet(
                                s.childId,
                                s.childName,
                                isOwnWallet = false,
                            )
                        })

                        // Both were opened either from the parent's overview or from a
                        // child's own dashboard, and have to return to whichever it was.
                        is AppScreen.ChildWallet, is AppScreen.ChildTasks -> ({
                            val toDashboard = returnToChildDashboard
                            returnToChildDashboard = null
                            currentScreen = if (toDashboard != null) {
                                AppScreen.ChildDashboard(toDashboard.first, toDashboard.second)
                            } else AppScreen.Home
                        })
                    }

                    BackHandler(enabled = backAction != null) { backAction?.invoke() }

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
                            onBack = { backAction?.invoke() },
                        )
                        AppScreen.Auth -> AuthScreen(
                            modifier = Modifier.padding(innerPadding),
                            onLoginSuccess = { currentScreen = AppScreen.Home },
                            onChildInviteLogin = { currentScreen = AppScreen.ChildInviteLogin },
                            onBack = { backAction?.invoke() },
                        )
                        AppScreen.Home -> AdultDashboardScreen(
                            modifier = Modifier.padding(innerPadding),
                            refreshKey = dashboardRefreshKey,
                            // The switch lives in the dashboard's overflow menu, but
                            // the value has to sit above the theme to be able to change it.
                            darkMode = darkMode,
                            onSetDarkMode = { on ->
                                darkMode = on
                                themeScope.launch { PrefsStore.setDarkMode(on) }
                            },
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
                            modifier = Modifier.padding(innerPadding),
                            onBack = { backAction?.invoke() },
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
                            onBack = { backAction?.invoke() },
                            onLoginAsChild = { childId, childName ->
                                currentScreen = AppScreen.ChildDashboard(childId, childName)
                            },
                            onLoginAsAdult = { currentScreen = AppScreen.Home },
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
                            onBack = { backAction?.invoke() },
                            onOpenTasks = {
                                returnToChildDashboard = screen.childId to screen.childName
                                currentScreen = AppScreen.ChildTasks(screen.childId, screen.childName)
                            },
                            onOpenWallet = {
                                returnToChildDashboard = screen.childId to screen.childName
                                currentScreen = AppScreen.ChildWallet(
                                    screen.childId,
                                    screen.childName,
                                    isOwnWallet = false,
                                    fromChildView = true,
                                )
                            },
                        )
                        is AppScreen.ChildPet -> ChildPetScreen(
                            childName = screen.childName,
                            childId = screen.childId,
                            onBack = { backAction?.invoke() },
                        )
                        is AppScreen.ChildWallet -> ChildWalletScreen(
                            childName = screen.childName,
                            childId = screen.childId,
                            isOwnWallet = screen.isOwnWallet,
                            fromChildView = screen.fromChildView,
                            onBack = { backAction?.invoke() },
                            onOpenRecurringAllowance = {
                                currentScreen = AppScreen.RecurringAllowance(
                                    screen.childId,
                                    screen.childName,
                                )
                            },
                        )
                        is AppScreen.RecurringAllowance -> RecurringAllowanceScreen(
                            childName = screen.childName,
                            childId = screen.childId,
                            // Back to the wallet it was opened from, so a parent who
                            // just set an amount sees the row say so.
                            onBack = { backAction?.invoke() },
                        )
                        is AppScreen.ChildTasks -> ChildTasksScreen(
                            modifier = Modifier.padding(innerPadding),
                            childName = screen.childName,
                            childId = screen.childId,
                            onBack = { backAction?.invoke() },
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
    onBack: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val palette = LocalSeasonPalette.current

    val emailState = remember { mutableStateOf("") }
    val passwordState = remember { mutableStateOf("") }
    val statusState = remember { mutableStateOf("Inte inloggad") }
    val showForgotPassword = remember { mutableStateOf(false) }
    val loadingState = remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.pageBg),
    ) {
        SeasonHeaderBar(
            title = "Logga in",
            subtitle = "Förälder eller vårdnadshavare",
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
            EntryTextField(
                label = "E-post",
                value = emailState.value,
                onValueChange = { emailState.value = it },
                placeholder = "namn@exempel.se",
            )
            EntryTextField(
                label = "Lösenord",
                value = passwordState.value,
                onValueChange = { passwordState.value = it },
                isPassword = true,
            )

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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                ),
            ) {
                Text(
                    text = if (loadingState.value) "Loggar in..." else "Logga in",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
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
                    fontSize = 14.sp,
                    color = palette.accent,
                )
            }

            if (showForgotPassword.value) {
                ForgotPasswordDialog(onDismiss = { showForgotPassword.value = false })
            }
            if (statusState.value != "Loggar in..." && statusState.value != "Inte inloggad") {
                Text(
                    text = statusState.value,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.danger,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Signing in as a child is a different kind of sign-in, not the next step of
        // this form -- so it sits below a rule at the foot of the screen instead of
        // floating in the empty half under the password field.
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
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Barn i familjen?",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 13.5.sp,
                color = palette.inkSoft,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onChildInviteLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, palette.accent),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accent),
            ) {
                Text(
                    text = "Jag är barn och har en kod",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun RegisterScreen(
    modifier: Modifier = Modifier,
    onRegisterSuccess: () -> Unit = {},
    onBackToLogin: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val palette = LocalSeasonPalette.current

    val familyNameState = remember { mutableStateOf("") }
    val parentNameState = remember { mutableStateOf("") }
    val emailState = remember { mutableStateOf("") }
    val passwordState = remember { mutableStateOf("") }
    val statusState = remember { mutableStateOf<String?>(null) }
    val loadingState = remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.pageBg),
    ) {
        SeasonHeaderBar(
            title = "Skapa familj",
            subtitle = "Registrera dig som förälder och bjud in dina barn",
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Four unlabelled boxes in a column were four identical boxes: a parent who
            // put their own name where the family's belongs had nothing to notice it by.
            EntryTextField(
                label = "Familjens namn",
                value = familyNameState.value,
                onValueChange = { familyNameState.value = it },
            )
            EntryTextField(
                label = "Ditt namn",
                value = parentNameState.value,
                onValueChange = { parentNameState.value = it },
            )
            EntryTextField(
                label = "E-post",
                value = emailState.value,
                onValueChange = { emailState.value = it },
                placeholder = "namn@exempel.se",
            )
            EntryTextField(
                label = "Lösenord",
                value = passwordState.value,
                onValueChange = { passwordState.value = it },
                placeholder = "minst 6 tecken",
                isPassword = true,
            )

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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                ),
            ) {
                Text(
                    text = if (loadingState.value) "Skapar familj..." else "Skapa familj",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            statusState.value?.let { msg ->
                Text(
                    text = msg,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.danger,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // The way out for someone who is registering by mistake belongs at the foot of
        // the screen, not between the form and the empty half below it.
        TextButton(
            onClick = onBackToLogin,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 22.dp),
        ) {
            Text(text = "Har redan konto? ", fontSize = 13.5.sp, color = palette.inkSoft)
            Text(
                text = "Logga in",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.accent,
            )
        }
    }
}

/**
 * One field of an entry form: a label that stays put, and the box under it.
 *
 * Material's floating label is the same word doing both jobs, so the moment a parent
 * starts typing the only thing telling them what the field was is gone -- which is
 * exactly when they need it, halfway through four boxes that otherwise look alike.
 */
@Composable
private fun EntryTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isPassword: Boolean = false,
) {
    val palette = LocalSeasonPalette.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.24.sp,
            color = palette.inkFaint,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
            placeholder = if (placeholder != null) {
                { Text(text = placeholder, fontSize = 15.sp, color = palette.inkFaint) }
            } else {
                null
            },
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = if (isPassword) {
                KeyboardOptions(keyboardType = KeyboardType.Password)
            } else {
                KeyboardOptions.Default
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = palette.surface,
                unfocusedContainerColor = palette.surface,
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.cardEdge,
                cursorColor = palette.accent,
                focusedTextColor = palette.ink,
                unfocusedTextColor = palette.ink,
            ),
        )
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

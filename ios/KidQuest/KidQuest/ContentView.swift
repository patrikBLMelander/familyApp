//
//  ContentView.swift
//  KidQuest
//
//  Created by Patrik Melander on 2026-03-03.
//

import SwiftUI

enum AppScreen: Equatable {
    case loading
    case welcome
    case register
    case auth
    case home
    case childInviteLogin
    case paywall
    case familyTasks
    // Barnens detaljflöden (implementeras senare)
    case childDashboard(childId: String, childName: String)
    case childPet(childId: String, childName: String)
    case childWallet(childId: String, childName: String, isOwnWallet: Bool)
    case childTasks(childId: String, childName: String)
}

struct ContentView: View {
    @State private var currentScreen: AppScreen = .loading
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        NavigationStack {
            Group {
                switch currentScreen {
                case .loading:
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)

                case .welcome:
                    WelcomeView(
                        onParentTap: { currentScreen = .register },
                        onChildInviteTap: { currentScreen = .childInviteLogin },
                        onLoginTap: { currentScreen = .auth }
                    )

                case .register:
                    RegisterView(
                        onRegisterSuccess: {
                            // Den som registrerar familjen är alltid dess förälder.
                            currentScreen = .home
                        },
                        onBackToLogin: {
                            currentScreen = .auth
                        },
                        // Bakåtpilen går dit skärmen kom ifrån, välkomstskärmen, och
                        // inte till inloggningen som länken i botten gör.
                        onBack: {
                            currentScreen = .welcome
                        }
                    )

                case .auth:
                    AuthView(
                        onLoginSuccess: {
                            // Bara föräldrar och assistenter kan logga in med e-post,
                            // så inloggning härifrån leder alltid till föräldravyn.
                            currentScreen = .home
                        },
                        onChildInviteLogin: {
                            currentScreen = .childInviteLogin
                        },
                        // Välkomstskärmen är appens rot, och det är dit bakåt leder
                        // även när man kom hit genom att logga ut: den har alla tre
                        // vägarna in, inloggningen bara en.
                        onBack: {
                            currentScreen = .welcome
                        }
                    )

                case .paywall:
                    PaywallView(
                        // No price to show yet: iOS has no store wired in, and a price
                        // must never be invented. The screen says so itself rather than
                        // guessing a number.
                        formattedMonthlyPrice: nil,
                        onPurchase: nil,
                        onRestore: nil,
                        onDismiss: { currentScreen = .home }
                    )

                case .home:
                    AdultDashboardView(
                        onLogout: {
                            TokenStoreIOS.shared.clearToken()
                            currentScreen = .auth
                        },
                        onChildPet: { id, name in
                            currentScreen = .childPet(childId: id, childName: name)
                        },
                        onChildWallet: { id, name in
                            currentScreen = .childWallet(childId: id, childName: name, isOwnWallet: false)
                        },
                        onChildTasks: { id, name in
                            currentScreen = .childTasks(childId: id, childName: name)
                        },
                        // Without this the band on the overview is inert:
                        // SeasonHeaderBand does not draw "Alla uppgifter ›" at all
                        // when onTap is nil.
                        onFamilyTasks: { currentScreen = .familyTasks },
                        onOpenSubscription: { currentScreen = .paywall }
                    )

                case .familyTasks:
                    // Only the parent's view offers the way here, and children are
                    // never routed to it at start-up. The screen offers nothing to add
                    // or remove, only ticking off, which everyone may do.
                    FamilyTasksView(onBack: { currentScreen = .home })

                case .childInviteLogin:
                    ChildInviteLoginView(
                        onBack: { currentScreen = .welcome },

                        onLoginAsChild: { childId, childName in
                            currentScreen = .childDashboard(childId: childId, childName: childName)
                        },
                        onLoginAsAdult: {
                            currentScreen = .home
                        }
                    )

                case let .childDashboard(childId, childName):
                    ChildDashboardView(
                        childId: childId,
                        childName: childName,
                        onBack: {
                            TokenStoreIOS.shared.clearToken()
                            currentScreen = .auth
                        },
                        onOpenTasks: {
                            currentScreen = .childTasks(childId: childId, childName: childName)
                        },
                        onOpenWallet: {
                            currentScreen = .childWallet(childId: childId, childName: childName, isOwnWallet: true)
                        }
                    )

                case let .childPet(childId, childName):
                    ChildPetView(
                        childId: childId,
                        childName: childName,
                        onBack: { currentScreen = .home }
                    )

                case let .childWallet(childId, childName, isOwnWallet):
                    ChildWalletView(
                        childName: childName,
                        childId: childId,
                        isOwnWallet: isOwnWallet,
                        onBack: {
                            currentScreen = .home
                        }
                    )

                case let .childTasks(childId, childName):
                    ChildTasksView(
                        childName: childName,
                        childId: childId,
                        // Barn når samma skärm från sin egen dashboard. Att alltid gå
                        // till .home härifrån hade lämnat dem i föräldravyn -- precis
                        // det routeOnStartup finns för att förhindra.
                        onBack: {
                            if TokenStoreIOS.shared.getSession()?.isChild == true {
                                currentScreen = .childDashboard(childId: childId, childName: childName)
                            } else {
                                currentScreen = .home
                            }
                        }
                    )
                }
            }
        }
        // Injiceras här, en gång, i stället för i varje skärm. Utan det här faller
        // hela appen tillbaka på standardvärdet i SeasonTheme -- sommar, ljust -- och
        // årstiden syntes bara i debug-riggen.
        .environment(\.seasonPalette, SeasonTheme.current(dark: colorScheme == .dark))
        .task {
            guard currentScreen == .loading else { return }
            await routeOnStartup()
        }
    }

    /// Väljer startskärm utifrån vem sessionen tillhör.
    ///
    /// En device-token säger i sig inte vem den tillhör. Att routa enbart på att den
    /// finns skickade barn rakt in i föräldravyn, där de kunde lägga till och ta bort
    /// uppgifter och öppna familjens plånbok. Därför routar vi på rollen.
    private func routeOnStartup() async {
        TokenStoreIOS.shared.load()
        var session = TokenStoreIOS.shared.getSession()

        if let stored = session, stored.isIncomplete {
            // Kopplad innan rollen sparades lokalt: slå upp den en gång istället för
            // att tvinga familjen att koppla om enheten.
            session = nil
            if let member = try? await AuthService.memberByDeviceToken(stored.deviceToken) {
                TokenStoreIOS.shared.setSession(
                    deviceToken: stored.deviceToken,
                    memberId: member.id,
                    memberName: member.name,
                    role: member.role,
                    familyId: member.familyId
                )
                session = TokenStoreIOS.shared.getSession()
            }
            // Misslyckas uppslagningen behåller vi token i lagringen: ett tillfälligt
            // nätverksfel ska inte kosta familjen en ny koppling, vi försöker igen
            // nästa gång appen startar.
        }

        guard let session else {
            currentScreen = .welcome
            return
        }

        if session.isIncomplete {
            // Fortfarande okänd efter uppslagningen: token är gammal eller medlemmen
            // borta. Att skicka dem till föräldravyn vore precis den ursprungliga
            // buggen, så vi börjar om istället.
            TokenStoreIOS.shared.clearToken()
            currentScreen = .welcome
        } else if session.isChild {
            // isIncomplete har redan garanterat att memberId finns; ?? håller oss
            // borta från en force unwrap.
            currentScreen = .childDashboard(
                childId: session.memberId ?? "",
                childName: session.memberName ?? "Barn"
            )
        } else {
            currentScreen = .home
        }
    }
}

/// Det första en ny familj ser: vad appen är, och tre vägar in i den.
///
/// Dressed in the seasonal palette like every screen behind the login. It used to open
/// in lavender and cream and then turn green one screen later, which read as two
/// different apps rather than one.
struct WelcomeView: View {
    var onParentTap: () -> Void = {}
    var onChildInviteTap: () -> Void = {}
    var onLoginTap: () -> Void = {}

    @Environment(\.seasonPalette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            // The shared bar rather than a bespoke title block: this screen has no back
            // control, and the band is what carries the season on every other screen.
            SeasonHeaderBar(
                title: "KidQuest",
                subtitle: "Gör tråkiga sysslor till roliga uppdrag"
            )

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    hero
                    points
                    actions
                }
                .padding(.horizontal, 16)
                .padding(.top, 18)
                .padding(.bottom, 24)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.pageBg.ignoresSafeArea())
        // The bar draws the title, so the navigation bar would only be an empty strip
        // of a second colour above it.
        .toolbar(.hidden, for: .navigationBar)
    }

    /// Samma illustration som tidigare, nu som ett band över hela bredden.
    ///
    /// scaledToFill inside a fixed frame, so the artwork spans 320pt and 430pt alike
    /// and is cropped rather than letterboxed.
    private var hero: some View {
        Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: 150)
            .overlay {
                Image("onboarding_hero_family")
                    .resizable()
                    .scaledToFill()
            }
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .accessibilityHidden(true)
    }

    /// Tre punkter i en lodrät lista.
    ///
    /// They were three cards side by side. At 390pt that left roughly 100pt per card:
    /// every title broke over three lines, the cards ended up unequal heights, and the
    /// block took a third of the screen. Stacked, each point gets one line of body and
    /// the whole section is shorter than one of the old cards.
    private var points: some View {
        VStack(alignment: .leading, spacing: 14) {
            point(
                title: "Hemligt ägg varje månad",
                detail: "Barnen får ett nytt djur — de vet inte vilket."
            )
            point(
                title: "Uppdrag matar djuret",
                detail: "Vardagssysslor ger XP, och XP får djuret att växa."
            )
            point(
                title: "Belöningar som motiverar",
                detail: "Koppla till veckopeng eller små mål, om du vill."
            )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func point(title: String, detail: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Circle()
                .fill(palette.accent)
                .frame(width: 8, height: 8)
                // Sits the dot on the title's mid-x-height rather than its top edge.
                .padding(.top, 6)

            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.system(size: 14.5, weight: .semibold))
                    .foregroundStyle(palette.ink)
                Text(detail)
                    .font(.system(size: 13))
                    .foregroundStyle(palette.inkSoft)
                    // Multi-line text in an HStack is otherwise free to truncate.
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .combine)
    }

    /// Tre vägar in, i fallande vikt.
    ///
    /// They used to be a filled pale-blue button, an outlined one and a text link —
    /// three different weights, but none of them clearly the one to press. Creating the
    /// family is what this screen exists for, the invite code is the second way in, and
    /// an account that already exists is the rare case.
    private var actions: some View {
        VStack(spacing: 12) {
            FilledActionButton(title: "Skapa en ny familj", action: onParentTap)
            OutlinedActionButton(title: "Jag har en inbjudningskod", action: onChildInviteTap)
            QuietActionButton(title: "Logga in", action: onLoginTap)
        }
        .padding(.top, 2)
    }
}

struct AuthView: View {
    var onLoginSuccess: () -> Void = {}
    var onChildInviteLogin: () -> Void = {}
    /// Nil när det inte finns någon skärm att gå tillbaka till.
    ///
    /// Reaching this screen by logging out leaves nothing behind it, and the bar then
    /// draws no chevron rather than one that goes nowhere.
    var onBack: (() -> Void)?

    /// Fyller formuläret åt debug-riggen. Bara `fixture()` sätter den.
    ///
    /// An empty form photographs as if the labels had never been tested: the whole
    /// point of them is that the field's name survives being typed over.
    var prefill: Prefill?

    struct Prefill {
        let email: String
        let password: String
    }

    @State private var email: String = ""
    @State private var password: String = ""
    @State private var status: String = "Inte inloggad"
    @State private var isLoading: Bool = false
    @State private var isShowingForgotPassword: Bool = false

    @Environment(\.seasonPalette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            SeasonHeaderBar(
                title: "Logga in",
                subtitle: "Förälder eller vårdnadshavare",
                onBack: onBack
            )

            ScrollView {
                VStack(spacing: 16) {
                    LabeledField(
                        label: "E‑post",
                        placeholder: "namn@exempel.se",
                        text: $email
                    )

                    LabeledField(
                        label: "Lösenord",
                        placeholder: "••••••••",
                        text: $password,
                        isSecure: true
                    )

                    FilledActionButton(
                        title: isLoading ? "Loggar in..." : "Logga in",
                        isEnabled: !isLoading
                    ) {
                        Task { await performLogin() }
                    }
                    .padding(.top, 2)

                    // Utan den här var en förälder som glömt sitt lösenord utelåst för
                    // gott: en annan förälder kunde sätta ett nytt, vilket inte hjälper
                    // en familj med bara en vuxen.
                    QuietActionButton(title: "Glömt lösenordet?") {
                        isShowingForgotPassword = true
                    }

                    if let statusMessage {
                        FormMessage(message: statusMessage)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 22)
                .padding(.bottom, 24)
            }

            childRoute
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.pageBg.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            guard let prefill, email.isEmpty, password.isEmpty else { return }
            email = prefill.email
            password = prefill.password
        }
        .sheet(isPresented: $isShowingForgotPassword) {
            ForgotPasswordSheet(initialEmail: email)
                // Handed over rather than inherited: the sheet is hosted in its own
                // presentation, outside this view's hierarchy, and the palette is
                // injected once at ContentView's root. Without this the sheet falls
                // back to SeasonTheme's default and is the wrong season.
                .environment(\.seasonPalette, palette)
                .presentationDetents([.medium])
                .presentationDragIndicator(.visible)
        }
    }

    /// Barnvägen, förankrad i botten bakom en avskiljare.
    ///
    /// It used to float in the middle of an otherwise empty lower half, directly under
    /// the form, where it read as the form's next step. It is not: it is a different
    /// kind of sign-in, with no email and no password, which is what the rule and the
    /// distance say.
    private var childRoute: some View {
        VStack(spacing: 0) {
            Rectangle()
                .fill(palette.cardEdge)
                .frame(height: 1)
                .padding(.bottom, 16)

            Text("Barn i familjen?")
                .font(.system(size: 13.5))
                .foregroundStyle(palette.inkSoft)
                .padding(.bottom, 10)

            OutlinedActionButton(
                title: "Jag är barn och har en kod",
                action: onChildInviteLogin
            )
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 22)
    }

    /// De två vilolägena är inte fel och visas inte.
    ///
    /// Same predicate the screen has always used, moved out of the body so the row it
    /// feeds is one view rather than an inline condition.
    private var statusMessage: String? {
        guard status != "Loggar in..." && status != "Inte inloggad" else { return nil }
        return status
    }

    private func performLogin() async {
        guard !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !password.isEmpty
        else {
            status = "Fyll i e‑post och lösenord."
            return
        }

        isLoading = true
        status = "Loggar in..."

        do {
            _ = try await AuthService.loginByEmail(
                email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                password: password
            )
            isLoading = false
            status = "Inloggad"
            onLoginSuccess()
        } catch {
            isLoading = false
            status = "Fel: \(error.localizedDescription)"
        }
    }
}

struct RegisterView: View {
    var onRegisterSuccess: () -> Void = {}
    var onBackToLogin: () -> Void = {}
    /// Se AuthView.onBack — samma skäl.
    var onBack: (() -> Void)?

    /// Fyller formuläret åt debug-riggen. Bara `fixture()` sätter den.
    ///
    /// Four unlabelled boxes was worst here: family name and your own name filled in
    /// the wrong order could not be noticed. A photograph of an empty form cannot show
    /// that the labels fix it.
    var prefill: Prefill?

    struct Prefill {
        let familyName: String
        let parentName: String
        let email: String
        let password: String
    }

    @State private var familyName: String = ""
    @State private var parentName: String = ""
    @State private var email: String = ""
    @State private var password: String = ""
    @State private var status: String?
    @State private var isLoading: Bool = false

    @Environment(\.seasonPalette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            SeasonHeaderBar(
                title: "Skapa familj",
                subtitle: "Registrera dig som förälder och bjud in dina barn",
                onBack: onBack
            )

            ScrollView {
                VStack(spacing: 14) {
                    LabeledField(
                        label: "Familjens namn",
                        placeholder: "T.ex. Melander",
                        text: $familyName
                    )

                    LabeledField(
                        label: "Ditt namn",
                        placeholder: "T.ex. Patrik",
                        text: $parentName
                    )

                    LabeledField(
                        label: "E‑post",
                        placeholder: "namn@exempel.se",
                        text: $email
                    )

                    LabeledField(
                        label: "Lösenord",
                        placeholder: "minst 6 tecken",
                        text: $password,
                        isSecure: true
                    )

                    FilledActionButton(
                        title: isLoading ? "Skapar familj..." : "Skapa familj",
                        isEnabled: !isLoading
                    ) {
                        Task { await performRegister() }
                    }
                    .padding(.top, 2)

                    if let status {
                        FormMessage(message: status)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 20)
                .padding(.bottom, 24)
            }

            loginRoute
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.pageBg.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            guard let prefill, familyName.isEmpty, parentName.isEmpty else { return }
            familyName = prefill.familyName
            parentName = prefill.parentName
            email = prefill.email
            password = prefill.password
        }
    }

    /// Förankrad i botten i stället för direkt under knappen, där den konkurrerade
    /// med den om samma blick.
    private var loginRoute: some View {
        Button(action: onBackToLogin) {
            HStack(spacing: 4) {
                Text("Har redan konto?")
                    .foregroundStyle(palette.inkSoft)
                Text("Logga in")
                    .fontWeight(.semibold)
                    .foregroundStyle(palette.accent)
            }
            .font(.system(size: 13.5))
            .frame(maxWidth: .infinity)
            .frame(height: 32)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
        .padding(.bottom, 22)
    }

    private func performRegister() async {
        guard !familyName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !parentName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !password.isEmpty
        else {
            status = "Fyll i alla fält."
            return
        }

        isLoading = true
        status = nil

        do {
            _ = try await AuthService.register(
                familyName: familyName.trimmingCharacters(in: .whitespacesAndNewlines),
                adminName: parentName.trimmingCharacters(in: .whitespacesAndNewlines),
                email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                password: password
            )
            isLoading = false
            onRegisterSuccess()
        } catch {
            isLoading = false
            status = "Fel: \(error.localizedDescription)"
        }
    }
}

// MARK: - Entry screen building blocks

/// Ett fält med sitt namn kvar ovanför sig.
///
/// The entry forms were placeholder-only: the word that told you what the field was
/// vanished at the first keystroke. On register that left four identical rounded
/// boxes, where the family's name and your own filled in the wrong order was
/// impossible to notice afterwards.
private struct LabeledField: View {
    @Environment(\.seasonPalette) private var palette

    let label: String
    let placeholder: String
    @Binding var text: String
    var isSecure: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .kerning(0.24)
                .foregroundStyle(palette.inkFaint)

            field
                .font(.system(size: 15))
                .foregroundStyle(palette.ink)
                .tint(palette.accent)
                .padding(.horizontal, 14)
                .frame(height: 50)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(palette.cardEdge, lineWidth: 1)
                )
                // The visible name is the label above; without this VoiceOver falls
                // back to reading the prompt, which for a password is eight bullets.
                .accessibilityLabel(label)
        }
    }

    /// The prompt carries the palette's faint ink rather than the system placeholder
    /// grey, which is tuned for the system background and not for a seasonal one.
    @ViewBuilder
    private var field: some View {
        if isSecure {
            SecureField("", text: $text, prompt: promptText)
        } else {
            TextField("", text: $text, prompt: promptText)
        }
    }

    private var promptText: Text {
        Text(placeholder).foregroundStyle(palette.inkFaint)
    }
}

/// Den tunga knappen: fylld med årstidens accent.
private struct FilledActionButton: View {
    @Environment(\.seasonPalette) private var palette

    let title: String
    var isEnabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 16, weight: .semibold))
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .foregroundStyle(palette.onAccent)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(palette.accent)
                )
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1 : 0.6)
    }
}

/// Den andra vägen: kontur i accentfärgen, samma vikt som "Lägg till familjemedlem"
/// på föräldravyn.
private struct OutlinedActionButton: View {
    @Environment(\.seasonPalette) private var palette

    let title: String
    var isEnabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 15.5, weight: .semibold))
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .foregroundStyle(palette.accent)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(palette.accent, lineWidth: 1.5)
                )
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1 : 0.6)
    }
}

/// Den tysta vägen: bara text.
private struct QuietActionButton: View {
    @Environment(\.seasonPalette) private var palette

    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 14))
                .foregroundStyle(palette.accent)
                .frame(maxWidth: .infinity)
                // 44pt is the smallest thing a thumb should be asked to hit, and a
                // line of 14pt text is half that on its own.
                .frame(height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Ett fel ur formuläret, sagt högt under knappen.
private struct FormMessage: View {
    @Environment(\.seasonPalette) private var palette

    let message: String

    var body: some View {
        Text(message)
            .font(.footnote)
            .multilineTextAlignment(.center)
            .foregroundStyle(palette.danger)
            .frame(maxWidth: .infinity)
    }
}

/// Begär en länk för att välja ett nytt lösenord.
///
/// Says the same thing whether or not the address has an account, because the server
/// does: it answers 200 either way and will not tell the app which it was. Anything
/// else here would turn the form into a way of finding out which families use
/// KidQuest, and the answer would be a list of parents.
///
/// The link in the mail opens a web page, so the app's job ends at asking for it --
/// there is no token to type in here.
private struct ForgotPasswordSheet: View {
    @Environment(\.seasonPalette) private var palette
    @Environment(\.dismiss) private var dismiss

    /// Det som redan står i inloggningsformuläret, så att adressen inte behöver
    /// skrivas två gånger.
    var initialEmail: String = ""

    @State private var email: String = ""
    @State private var isSending: Bool = false
    @State private var didSend: Bool = false
    @State private var errorMessage: String?

    var body: some View {
        VStack(spacing: 0) {
            // The sheet keeps its own title bar and rewrites it in place, rather than
            // closing and leaving the parent to guess whether anything was sent.
            SeasonHeaderBar(title: didSend ? "Kolla din mejl" : "Glömt lösenordet?")

            ScrollView {
                VStack(spacing: 16) {
                    if didSend {
                        confirmation
                    } else {
                        form
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 22)
                .padding(.bottom, 24)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.pageBg.ignoresSafeArea())
        .onAppear {
            guard email.isEmpty else { return }
            email = initialEmail
        }
    }

    private var form: some View {
        VStack(spacing: 16) {
            explanation("Skriv din e‑postadress så skickar vi en länk för att välja ett nytt lösenord.")

            LabeledField(
                label: "E‑post",
                placeholder: "namn@exempel.se",
                text: $email
            )
            .keyboardType(.emailAddress)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()

            FilledActionButton(
                title: isSending ? "Skickar…" : "Skicka länk",
                isEnabled: canSend
            ) {
                Task { await submit() }
            }
            .padding(.top, 2)

            QuietActionButton(title: "Avbryt") {
                guard !isSending else { return }
                dismiss()
            }

            if let errorMessage {
                FormMessage(message: errorMessage)
            }
        }
    }

    /// Samma text oavsett utfall -- se doc-kommentaren ovanför vyn.
    private var confirmation: some View {
        VStack(spacing: 16) {
            explanation(
                "Om adressen finns hos oss har vi skickat en länk dit. Den gäller i en timme. "
                + "Titta i skräpposten om den inte dyker upp."
            )

            FilledActionButton(title: "Klart") {
                dismiss()
            }
            .padding(.top, 2)
        }
    }

    private func explanation(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 14))
            .foregroundStyle(palette.inkSoft)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
    }

    private var canSend: Bool {
        !isSending && !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func submit() async {
        guard canSend else { return }

        isSending = true
        errorMessage = nil

        do {
            try await AuthService.requestPasswordReset(
                email: email.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            // Bara transportfel når hit -- AuthService sväljer statuskoden med flit,
            // så ett "skickat" ser likadant ut för varje adress.
            didSend = true
        } catch {
            errorMessage = ApiErrors.message(error, fallback: "Kunde inte skicka just nu.")
        }

        isSending = false
    }
}

/// Den tredje vägen in: en kod från en förälder kopplar den här enheten.
///
/// Dressed in the seasonal palette like the two screens it sits between. It kept the
/// old lavender-and-cream look after those were rebuilt, so the entry flow turned
/// green on the welcome screen and back to lavender one tap later -- the same break
/// that reskinning WelcomeView was meant to remove, moved one screen down.
struct ChildInviteLoginView: View {
    var onBack: () -> Void = {}
    var onLoginAsChild: (String, String) -> Void = { _, _ in }
    /// A code can belong to a second parent as easily as to a child, and where it
    /// lands has to follow the member's role rather than the screen they typed it on.
    var onLoginAsAdult: () -> Void = {}

    /// Fyller koden åt debug-riggen. Bara `fixture()` sätter den.
    ///
    /// Se AuthView.prefill -- samma skäl: ett tomt fält kan inte visa att etiketten
    /// står kvar när koden väl är itryckt.
    var prefill: Prefill?

    struct Prefill {
        let inviteCode: String
    }

    @State private var inviteCode: String = ""
    @State private var status: String?
    @State private var isLoading: Bool = false

    @Environment(\.seasonPalette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            SeasonHeaderBar(
                title: "Koppla din enhet",
                subtitle: "Be någon i familjen visa koden eller QR‑koden",
                onBack: onBack
            )

            ScrollView {
                VStack(spacing: 16) {
                    // Kvar som platshållare tills skanningen finns: knappen visar att
                    // vägen är planerad, men den går inte att trycka på.
                    // TODO: Lägg till riktig QR-skanning med AVFoundation.
                    OutlinedActionButton(title: "Skanna QR‑kod", isEnabled: false) {}

                    Text("eller skriv in koden")
                        .font(.system(size: 13.5))
                        .foregroundStyle(palette.inkSoft)

                    LabeledField(
                        label: "Inbjudningskod",
                        placeholder: "Koden du fått",
                        text: $inviteCode
                    )

                    FilledActionButton(
                        title: isLoading ? "Kopplar…" : "Koppla denna enhet",
                        isEnabled: !isLoading
                    ) {
                        Task { await performLink() }
                    }
                    .padding(.top, 2)

                    if let status {
                        FormMessage(message: status)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 22)
                .padding(.bottom, 24)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.pageBg.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            guard let prefill, inviteCode.isEmpty else { return }
            inviteCode = prefill.inviteCode
        }
    }

    private func performLink() async {
        let trimmed = inviteCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            status = "Ange en kod"
            return
        }

        isLoading = true
        status = nil

        do {
            let member = try await FamilyRepository.linkDeviceByInviteToken(inviteToken: trimmed)
            isLoading = false
            // The same bug Android shipped and fixed: routing on where the code was
            // entered rather than on whose it is put a paired parent in the child's
            // view, and a paired child in the adult dashboard.
            if member.role == "CHILD" || member.role == "ASSISTANT" {
                onLoginAsChild(member.id, member.name)
            } else {
                onLoginAsAdult()
            }
        } catch {
            isLoading = false
            status = "Kunde inte koppla enheten. Kontrollera koden."
        }
    }
}

#if DEBUG
extension WelcomeView {

    /// Skärmen som den ser ut för en ny familj, så den kan fotograferas.
    ///
    /// The iOS simulator hands over a screenshot but takes no input, so a screen behind
    /// a tap cannot be reached to be looked at at all. This is the way in — see
    /// ScreenHarness in KidQuestApp.swift.
    static func fixture() -> WelcomeView {
        WelcomeView()
    }
}

extension AuthView {

    /// Ifylld, och med bakåtpilen framme.
    ///
    /// Both on purpose: the chevron only exists when there is somewhere to go back to,
    /// and an empty form cannot show the one thing the labels are for — that the
    /// field's name is still above it once the placeholder has been typed over.
    static func fixture() -> AuthView {
        AuthView(
            onBack: {},
            prefill: Prefill(email: "patrik@exempel.se", password: "hemligt123")
        )
    }
}

extension RegisterView {

    /// Ifylld i rätt ordning, vilket är hela poängen: familjens namn och ditt eget är
    /// två olika fält, och utan etiketter fanns inget som sa vilket som var vilket.
    static func fixture() -> RegisterView {
        RegisterView(
            onBack: {},
            prefill: Prefill(
                familyName: "Melander",
                parentName: "Patrik",
                email: "patrik@exempel.se",
                password: "hemligt123"
            )
        )
    }
}

extension ChildInviteLoginView {

    /// Med en kod ifylld: ett tomt fält kan inte visa det etiketten finns för, att
    /// fältets namn står kvar när koden väl är itryckt.
    static func fixture() -> ChildInviteLoginView {
        ChildInviteLoginView(prefill: Prefill(inviteCode: "8F3K-2QX7"))
    }
}

#Preview("Välkomst") {
    WelcomeView.fixture()
        .environment(\.seasonPalette, SeasonTheme.current(dark: false))
}

#Preview("Logga in mörk") {
    AuthView.fixture()
        .environment(\.seasonPalette, SeasonTheme.current(dark: true))
        .preferredColorScheme(.dark)
}

#Preview("Registrera") {
    RegisterView.fixture()
        .environment(\.seasonPalette, SeasonTheme.current(dark: false))
}

#Preview("Koppla enhet") {
    ChildInviteLoginView.fixture()
        .environment(\.seasonPalette, SeasonTheme.current(dark: false))
}

#Preview("Glömt lösenord") {
    ForgotPasswordSheet(initialEmail: "patrik@exempel.se")
        .environment(\.seasonPalette, SeasonTheme.current(dark: false))
}
#endif

#Preview {
    ContentView()
}

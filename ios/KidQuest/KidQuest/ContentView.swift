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
                        }
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
                        }
                    )

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

struct WelcomeView: View {
    var onParentTap: () -> Void = {}
    var onChildInviteTap: () -> Void = {}
    var onLoginTap: () -> Void = {}

    private var backgroundGradient: LinearGradient {
        LinearGradient(
            colors: [
                Color(red: 224 / 255, green: 231 / 255, blue: 1.0),
                Color(red: 224 / 255, green: 242 / 255, blue: 1.0),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }

    private let cardColor = Color(red: 1.0, green: 251 / 255, blue: 235 / 255)
    private let textPrimary = Color(red: 28 / 255, green: 25 / 255, blue: 23 / 255)
    private let textSecondary = Color(red: 87 / 255, green: 83 / 255, blue: 78 / 255)
    private let buttonColor = Color(red: 186 / 255, green: 230 / 255, blue: 253 / 255)
    private let buttonOnColor = Color(red: 12 / 255, green: 74 / 255, blue: 110 / 255)

    var body: some View {
        ScrollView {
            VStack(alignment: .center, spacing: 0) {
                Spacer(minLength: 24)

                Image("onboarding_hero_family")
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: .infinity)
                    .frame(height: 180)

                Spacer(minLength: 24)

                Text("Gör tråkiga sysslor till roliga uppdrag")
                    .font(.title2.weight(.bold))
                    .multilineTextAlignment(.center)
                    .foregroundColor(textPrimary)

                Spacer().frame(height: 8)

                Text("Varje månad ett hemligt ägg, mata djuret med vardagsuppdrag och levla upp – plus belöningar som motiverar.")
                    .font(.body)
                    .multilineTextAlignment(.center)
                    .foregroundColor(textSecondary)

                Spacer().frame(height: 24)

                HStack(spacing: 12) {
                    ValueCard(
                        imageName: "onboarding_card_pet_xp",
                        title: "Hemligt ägg varje månad",
                        subtitle: "Barnen får ett nytt ägg med ett djur i – en överraskning vilket djur det blir.",
                        cardColor: cardColor,
                        textPrimary: textPrimary,
                        textSecondary: textSecondary
                    )

                    ValueCard(
                        imageName: "onboarding_card_family_overview",
                        title: "Uppdrag matar djuret",
                        subtitle: "Under månaden gör barnet uppgifter i vardagen för att mata och levla upp djuret.",
                        cardColor: cardColor,
                        textPrimary: textPrimary,
                        textSecondary: textSecondary
                    )

                    ValueCard(
                        imageName: "onboarding_card_rewards_savings",
                        title: "Belöningar som motiverar",
                        subtitle: "Koppla uppdrag till veckopeng eller små mål – om du vill.",
                        cardColor: cardColor,
                        textPrimary: textPrimary,
                        textSecondary: textSecondary
                    )
                }

                Spacer().frame(height: 32)

                Button(action: onParentTap) {
                    Text("Jag är förälder")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(buttonColor)
                .foregroundColor(buttonOnColor)
                .controlSize(.large)

                Spacer().frame(height: 12)

                Button(action: onChildInviteTap) {
                    Text("Jag är barn och har en kod")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(buttonOnColor)
                .controlSize(.large)

                Spacer().frame(height: 8)

                Button(action: onLoginTap) {
                    Text("Logga in")
                        .foregroundColor(textSecondary)
                }
                .buttonStyle(.plain)

                Spacer().frame(height: 32)
            }
            .padding(24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            backgroundGradient
                .ignoresSafeArea()
        )
    }
}

struct AuthView: View {
    var onLoginSuccess: () -> Void = {}
    var onChildInviteLogin: () -> Void = {}

    @State private var email: String = ""
    @State private var password: String = ""
    @State private var status: String = "Inte inloggad"
    @State private var isLoading: Bool = false

    private var backgroundGradient: LinearGradient {
        LinearGradient(
            colors: [
                Color(red: 224 / 255, green: 231 / 255, blue: 1.0),
                Color(red: 224 / 255, green: 242 / 255, blue: 1.0),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }

    private let cardColor = Color(red: 1.0, green: 251 / 255, blue: 235 / 255)
    private let textPrimary = Color(red: 28 / 255, green: 25 / 255, blue: 23 / 255)
    private let textSecondary = Color(red: 87 / 255, green: 83 / 255, blue: 78 / 255)
    private let buttonColor = Color(red: 186 / 255, green: 230 / 255, blue: 253 / 255)
    private let buttonOnColor = Color(red: 12 / 255, green: 74 / 255, blue: 110 / 255)

    var body: some View {
        ScrollView {
            VStack(alignment: .center) {
                Spacer().frame(height: 32)

                Text("Logga in")
                    .font(.title2.weight(.bold))
                    .foregroundColor(textPrimary)
                    .multilineTextAlignment(.center)

                Spacer().frame(height: 8)

                Text("Förälder eller vårdnadshavare")
                    .font(.body)
                    .foregroundColor(textSecondary)
                    .multilineTextAlignment(.center)

                Spacer().frame(height: 24)

                VStack {
                    VStack(alignment: .center, spacing: 16) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("E‑post")
                                .font(.caption)
                                .foregroundColor(textSecondary)
                            TextField("", text: $email)
                                .foregroundColor(textPrimary)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 10)
                                .background(
                                    RoundedRectangle(cornerRadius: 12)
                                        .strokeBorder(buttonOnColor.opacity(0.4), lineWidth: 1)
                                        .background(Color.white.cornerRadius(12))
                                )
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text("Lösenord")
                                .font(.caption)
                                .foregroundColor(textSecondary)
                            SecureField("", text: $password)
                                .foregroundColor(textPrimary)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 10)
                                .background(
                                    RoundedRectangle(cornerRadius: 12)
                                        .strokeBorder(buttonOnColor.opacity(0.4), lineWidth: 1)
                                        .background(Color.white.cornerRadius(12))
                                )
                        }

                        Button {
                            Task {
                                await performLogin()
                            }
                        } label: {
                            Text(isLoading ? "Loggar in..." : "Logga in")
                                .fontWeight(.semibold)
                                .frame(maxWidth: .infinity)
                                .frame(height: 48)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(buttonColor)
                        .foregroundColor(buttonOnColor)
                        .disabled(isLoading)

                        if status != "Loggar in..." && status != "Inte inloggad" {
                            Text(status)
                                .font(.footnote)
                                .foregroundColor(.red)
                                .multilineTextAlignment(.center)
                                .padding(.top, 4)
                        }
                    }
                    .padding(20)
                }
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(cardColor)
                )
                .padding(.horizontal, 16)

                Spacer().frame(height: 24)

                Text("Barn i familjen?")
                    .font(.body)
                    .foregroundColor(textSecondary)

                Spacer().frame(height: 8)

                Button(action: onChildInviteLogin) {
                    Text("Jag är barn och har en kod")
                        .foregroundColor(buttonOnColor)
                }
                .buttonStyle(.plain)

                Spacer().frame(height: 24)
            }
            .padding(.horizontal, 20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            backgroundGradient
                .ignoresSafeArea()
        )
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

    @State private var familyName: String = ""
    @State private var parentName: String = ""
    @State private var email: String = ""
    @State private var password: String = ""
    @State private var status: String?
    @State private var isLoading: Bool = false

    private var backgroundGradient: LinearGradient {
        LinearGradient(
            colors: [
                Color(red: 224 / 255, green: 231 / 255, blue: 1.0),
                Color(red: 224 / 255, green: 242 / 255, blue: 1.0),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }

    private let cardColor = Color(red: 1.0, green: 251 / 255, blue: 235 / 255)
    private let textPrimary = Color(red: 28 / 255, green: 25 / 255, blue: 23 / 255)
    private let textSecondary = Color(red: 87 / 255, green: 83 / 255, blue: 78 / 255)
    private let buttonColor = Color(red: 186 / 255, green: 230 / 255, blue: 253 / 255)
    private let buttonOnColor = Color(red: 12 / 255, green: 74 / 255, blue: 110 / 255)

    var body: some View {
        ScrollView {
            VStack {
                Spacer().frame(height: 32)

                Text("Skapa familj")
                    .font(.title2.weight(.bold))
                    .foregroundColor(textPrimary)
                    .multilineTextAlignment(.center)

                Spacer().frame(height: 8)

                Text("Registrera dig som förälder och bjud in dina barn.")
                    .font(.body)
                    .foregroundColor(textSecondary)
                    .multilineTextAlignment(.center)

                Spacer().frame(height: 24)

                VStack(alignment: .center, spacing: 16) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Familjens namn")
                            .font(.caption)
                            .foregroundColor(textSecondary)
                        TextField("", text: $familyName)
                            .foregroundColor(textPrimary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .strokeBorder(buttonOnColor.opacity(0.4), lineWidth: 1)
                                    .background(Color.white.cornerRadius(12))
                            )
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Ditt namn")
                            .font(.caption)
                            .foregroundColor(textSecondary)
                        TextField("", text: $parentName)
                            .foregroundColor(textPrimary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .strokeBorder(buttonOnColor.opacity(0.4), lineWidth: 1)
                                    .background(Color.white.cornerRadius(12))
                            )
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("E‑post")
                            .font(.caption)
                            .foregroundColor(textSecondary)
                        TextField("", text: $email)
                            .foregroundColor(textPrimary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .strokeBorder(buttonOnColor.opacity(0.4), lineWidth: 1)
                                    .background(Color.white.cornerRadius(12))
                            )
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Lösenord")
                            .font(.caption)
                            .foregroundColor(textSecondary)
                        SecureField("", text: $password)
                            .foregroundColor(textPrimary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .strokeBorder(buttonOnColor.opacity(0.4), lineWidth: 1)
                                    .background(Color.white.cornerRadius(12))
                            )
                    }

                    Button {
                        Task {
                            await performRegister()
                        }
                    } label: {
                        Text(isLoading ? "Skapar familj..." : "Skapa familj")
                            .fontWeight(.semibold)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(buttonColor)
                    .foregroundColor(buttonOnColor)
                    .disabled(isLoading)

                    if let status {
                        Text(status)
                            .font(.footnote)
                            .foregroundColor(.red)
                            .multilineTextAlignment(.center)
                            .padding(.top, 4)
                    }
                }
                .padding(20)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(cardColor)
                )
                .padding(.horizontal, 16)

                Spacer().frame(height: 16)

                Button(action: onBackToLogin) {
                    Text("Har redan konto? Logga in")
                        .foregroundColor(textSecondary)
                }
                .buttonStyle(.plain)

                Spacer().frame(height: 24)
            }
            .padding(.horizontal, 20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            backgroundGradient
                .ignoresSafeArea()
        )
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

struct ChildInviteLoginView: View {
    var onBack: () -> Void = {}
    var onLoginAsChild: (String, String) -> Void = { _, _ in }
    /// A code can belong to a second parent as easily as to a child, and where it
    /// lands has to follow the member's role rather than the screen they typed it on.
    var onLoginAsAdult: () -> Void = {}

    @State private var inviteCode: String = ""
    @State private var status: String?
    @State private var isLoading: Bool = false

    private var backgroundGradient: LinearGradient {
        LinearGradient(
            colors: [
                Color(red: 224 / 255, green: 231 / 255, blue: 1.0),
                Color(red: 224 / 255, green: 242 / 255, blue: 1.0),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }

    private let cardColor = Color(red: 1.0, green: 251 / 255, blue: 235 / 255)
    private let textPrimary = Color(red: 28 / 255, green: 25 / 255, blue: 23 / 255)
    private let textSecondary = Color(red: 87 / 255, green: 83 / 255, blue: 78 / 255)
    private let buttonColor = Color(red: 186 / 255, green: 230 / 255, blue: 253 / 255)
    private let buttonOnColor = Color(red: 12 / 255, green: 74 / 255, blue: 110 / 255)

    var body: some View {
        ScrollView {
            VStack(alignment: .center) {
                Spacer().frame(height: 24)

                Text("Koppla din enhet")
                    .font(.title2.weight(.bold))
                    .foregroundColor(textPrimary)
                    .multilineTextAlignment(.center)

                Spacer().frame(height: 8)

                Text("Be mamma eller pappa visa koden eller QR-koden – eller skanna här nedan.")
                    .font(.body)
                    .foregroundColor(textSecondary)
                    .multilineTextAlignment(.center)

                Spacer().frame(height: 24)

                VStack(alignment: .center, spacing: 16) {
                    Button {
                        // TODO: Lägg till riktig QR-skanning med AVFoundation om vi vill.
                    } label: {
                        HStack {
                            Image(systemName: "qrcode.viewfinder")
                            Text("Skanna QR-kod")
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                    }
                    .buttonStyle(.bordered)
                    .tint(buttonOnColor)
                    .disabled(true) // placeholder tills vi implementerar skanning

                    Text("eller skriv in koden")
                        .font(.footnote)
                        .foregroundColor(textSecondary)

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Inbjudningskod")
                            .font(.caption)
                            .foregroundColor(textSecondary)
                        TextField("", text: $inviteCode)
                            .foregroundColor(textPrimary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .strokeBorder(buttonOnColor.opacity(0.4), lineWidth: 1)
                                    .background(Color.white.cornerRadius(12))
                            )
                    }

                    Button {
                        Task {
                            await performLink()
                        }
                    } label: {
                        HStack {
                            Image(systemName: "iphone")
                            Text(isLoading ? "Kopplar…" : "Koppla denna enhet")
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(buttonColor)
                    .foregroundColor(buttonOnColor)
                    .disabled(isLoading)

                    if let status {
                        Text(status)
                            .font(.footnote)
                            .foregroundColor(.red)
                            .multilineTextAlignment(.center)
                    }
                }
                .padding(20)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(cardColor)
                )
                .padding(.horizontal, 16)

                Spacer().frame(height: 20)

                Button(action: onBack) {
                    Text("Tillbaka")
                        .foregroundColor(textSecondary)
                }
                .buttonStyle(.plain)

                Spacer().frame(height: 24)
            }
            .padding(.horizontal, 20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            backgroundGradient
                .ignoresSafeArea()
        )
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

private struct ValueCard: View {
    let imageName: String
    let title: String
    let subtitle: String
    let cardColor: Color
    let textPrimary: Color
    let textSecondary: Color

    var body: some View {
        VStack(spacing: 8) {
            // Wide illustrations (1.83), not icons. A square frame with scaledToFit
            // letterboxed them, so span the card and crop to a header strip.
            Color.clear
                .aspectRatio(1.83, contentMode: .fit)
                .overlay(
                    Image(imageName)
                        .resizable()
                        .scaledToFill()
                )
                .clipShape(RoundedRectangle(cornerRadius: 10))

            Text(title)
                .font(.subheadline.weight(.semibold))
                .multilineTextAlignment(.center)
                .foregroundColor(textPrimary)

            Text(subtitle)
                .font(.caption)
                .multilineTextAlignment(.center)
                .foregroundColor(textSecondary)
        }
        .padding(12)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(cardColor)
        )
    }
}

#Preview {
    ContentView()
}

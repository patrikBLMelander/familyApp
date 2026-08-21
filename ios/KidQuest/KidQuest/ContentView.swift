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
                            // När backend finns kopplar vi in riktig token-hantering här.
                            currentScreen = .home
                        },
                        onBackToLogin: {
                            currentScreen = .auth
                        }
                    )

                case .auth:
                    AuthView(
                        onLoginSuccess: {
                            // När backend finns kopplar vi in riktig token-hantering här.
                            currentScreen = .home
                        },
                        onChildInviteLogin: {
                            currentScreen = .childInviteLogin
                        }
                    )

                case .home:
                    AdultDashboardView(
                        onLogout: {
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
                        }
                    )

                case let .childDashboard(childId, childName):
                    ChildDashboardView(
                        childId: childId,
                        childName: childName,
                        onBack: {
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
                    Text("Dagens uppgifter för \(childName) (\(childId))")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
        }
        .task {
            if currentScreen == .loading {
                if TokenStoreIOS.shared.getToken() != nil {
                    currentScreen = .home
                } else {
                    currentScreen = .welcome
                }
            }
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
            try await AuthRepository.login(email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                                           password: password)
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
            try await AuthRepository.registerFamily(
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
            onLoginAsChild(member.id, member.name)
        } catch {
            isLoading = false
            status = "Kunde inte koppla enheten. Kontrollera koden."
        }
    }
}

// MARK: - Adult dashboard (förälder)

private struct AdultChildSummary: Identifiable {
    let id: String
    let name: String
    let todaysDone: Int
    let todaysTotal: Int
    let hasPet: Bool
    let streakDays: Int
    let nextTaskTitle: String?
}

struct AdultDashboardView: View {
    var onLogout: () -> Void = {}
    var onAddFamilyMember: () -> Void = {}
    var onChildPet: (String, String) -> Void = { _, _ in }
    var onChildWallet: (String, String) -> Void = { _, _ in }
    var onChildTasks: (String, String) -> Void = { _, _ in }

    @State private var children: [AdultChildSummary] = []
    @State private var isLoading: Bool = true
    @State private var errorMessage: String?
    @State private var inviteChild: AdultChildSummary?

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

    private let cardPastel = Color(red: 1.0, green: 251 / 255, blue: 235 / 255)
    private let textPrimary = Color(red: 28 / 255, green: 25 / 255, blue: 23 / 255)
    private let textSecondary = Color(red: 87 / 255, green: 83 / 255, blue: 78 / 255)
    private let buttonPastel = Color(red: 186 / 255, green: 230 / 255, blue: 253 / 255)
    private let buttonOnPastel = Color(red: 12 / 255, green: 74 / 255, blue: 110 / 255)

    var body: some View {
        NavigationStack {
            ZStack {
                backgroundGradient
                    .ignoresSafeArea()
                if isLoading {
                    ProgressView()
                } else {
                    VStack(spacing: 0) {
                        // Top bar
                        HStack {
                            Text("Min familj")
                                .font(.title2.weight(.bold))
                                .foregroundColor(textPrimary)

                            Spacer()

                            Button(action: onLogout) {
                                Text("Logga ut")
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 16)

                        ScrollView {
                            VStack(spacing: 16) {
                                if let errorMessage {
                                    Text(errorMessage)
                                        .foregroundColor(.red)
                                        .padding(.horizontal, 16)
                                }

                                summaryCards
                                Text("Mina barn")
                                    .font(.title2.weight(.semibold))
                                    .foregroundColor(textPrimary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.horizontal, 16)

                                if children.isEmpty {
                                    emptyChildrenCard
                                } else {
                                    VStack(spacing: 12) {
                                        ForEach(children) { child in
                                            AdultChildCardView(
                                                child: child,
                                                cardPastel: cardPastel,
                                                textPrimary: textPrimary,
                                                buttonPastel: buttonPastel,
                                                buttonOnPastel: buttonOnPastel,
                                                onPetClick: { onChildPet(child.id, child.name) },
                                                onWalletClick: { onChildWallet(child.id, child.name) },
                                                onTasksClick: { onChildTasks(child.id, child.name) },
                                                onInviteClick: { inviteChild = child }
                                            )
                                            .padding(.horizontal, 16)
                                        }
                                    }
                                }

                                addChildButton
                                    .padding(.horizontal, 16)

                                Spacer(minLength: 24)
                            }
                            .padding(.top, 8)
                        }
                    }
                }
            }
        }
        .task {
            await loadChildren()
        }
        .sheet(item: $inviteChild) { child in
            ChildInviteSheet(
                childName: child.name,
                memberId: child.id
            )
        }
    }

    private var summaryCards: some View {
        let totalTasksToday = children.map { $0.todaysTotal }.reduce(0, +)
        let completedTasksToday = children.map { $0.todaysDone }.reduce(0, +)
        let childrenWithPet = children.filter { $0.hasPet }.count

        return VStack(spacing: 8) {
            // Översikt idag
            VStack(alignment: .leading, spacing: 4) {
                Text("Idag i familjen")
                    .font(.headline)
                    .foregroundColor(textPrimary)
                Text(
                    totalTasksToday > 0
                        ? "Barnen har gjort \(completedTasksToday) av \(totalTasksToday) uppgifter idag."
                        : "Inga uppgifter planerade idag ännu."
                )
                .font(.subheadline)
                .foregroundColor(textSecondary)

                if childrenWithPet > 0 {
                    Text("\(childrenWithPet) av \(children.count) barn har ett aktivt djur just nu.")
                        .font(.subheadline)
                        .foregroundColor(textSecondary)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Color.white.opacity(0.9))
            )
            .padding(.horizontal, 16)

            // Förslag idag
            if let suggestion = children
                .filter({ $0.todaysTotal > 0 })
                .min(by: { lhs, rhs in
                    let lRatio = lhs.todaysTotal == 0 ? 1.0 : Double(lhs.todaysDone) / Double(lhs.todaysTotal)
                    let rRatio = rhs.todaysTotal == 0 ? 1.0 : Double(rhs.todaysDone) / Double(rhs.todaysTotal)
                    return lRatio < rRatio
                }) {

                VStack(alignment: .leading, spacing: 4) {
                    Text("Förslag idag")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(textPrimary)

                    let message = suggestion.todaysDone >= suggestion.todaysTotal && suggestion.todaysTotal > 0
                        ? "Ge extra beröm – \(suggestion.name) har gjort alla sina uppgifter idag!"
                        : (
                            (suggestion.nextTaskTitle?.isEmpty == false)
                            ? "Påminn \(suggestion.name) om \"\(suggestion.nextTaskTitle ?? "")\" för att mata sitt djur."
                            : "Påminn \(suggestion.name) om dagens uppgifter."
                        )

                    Text(message)
                        .font(.subheadline)
                        .foregroundColor(textSecondary)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(cardPastel)
                )
                .padding(.horizontal, 16)
            }
        }
    }

    private var emptyChildrenCard: some View {
        VStack(alignment: .center, spacing: 8) {
            Text("Inga barn i familjen ännu")
                .font(.body.weight(.semibold))
                .foregroundColor(textPrimary)
            Text("Lägg till ditt första barn nedan.")
                .font(.body)
                .foregroundColor(textSecondary)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(cardPastel)
        )
        .padding(.horizontal, 16)
    }

    private var addChildButton: some View {
        Button {
            onAddFamilyMember()
        } label: {
            HStack {
                Image(systemName: "person.badge.plus")
                Text("Lägg till barn")
            }
            .font(.headline)
            .frame(maxWidth: .infinity)
            .frame(height: 56)
        }
        .buttonStyle(.borderedProminent)
        .tint(buttonPastel)
        .foregroundColor(buttonOnPastel)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func loadChildren() async {
        isLoading = true
        errorMessage = nil
        do {
            let members = try await FamilyRepository.fetchChildren()
            let order = members.enumerated().reduce(into: [String: Int]()) { $0[$1.element.id] = $1.offset }

            var updated = await withTaskGroup(of: AdultChildSummary.self) { group -> [AdultChildSummary] in
                for member in members {
                    group.addTask {
                        async let petTask: PetResponseDTO? = try? ApiClient.shared.send(
                            PetResponseDTO.self,
                            path: "pets/members/\(member.id)/current",
                            method: "GET"
                        )
                        async let tasksTask = CalendarRepositoryIOS.fetchTasksForToday(memberId: member.id)

                        let pet = await petTask
                        let todaysTasks = (try? await tasksTask) ?? []
                        let done = todaysTasks.filter { $0.completed }.count
                        let nextTitle = todaysTasks.first(where: { !$0.completed })?.event.title

                        return AdultChildSummary(
                            id: member.id,
                            name: member.name,
                            todaysDone: done,
                            todaysTotal: todaysTasks.count,
                            hasPet: pet != nil,
                            streakDays: 0,
                            nextTaskTitle: nextTitle
                        )
                    }
                }
                var results: [AdultChildSummary] = []
                for await summary in group { results.append(summary) }
                return results
            }

            updated.sort { (order[$0.id] ?? 0) < (order[$1.id] ?? 0) }
            children = updated
            isLoading = false
        } catch {
            errorMessage = "Kunde inte ladda familjemedlemmar."
            isLoading = false
        }
    }
}

private struct AdultChildCardView: View {
    let child: AdultChildSummary
    let cardPastel: Color
    let textPrimary: Color
    let buttonPastel: Color
    let buttonOnPastel: Color
    var onPetClick: () -> Void
    var onWalletClick: () -> Void
    var onTasksClick: () -> Void
    var onInviteClick: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(child.name)
                .font(.headline)
                .foregroundColor(textPrimary)

            VStack(alignment: .leading, spacing: 4) {
                Text(
                    child.todaysTotal > 0
                        ? "Idag: \(child.todaysDone) av \(child.todaysTotal) uppgifter gjorda"
                        : "Idag: inga uppgifter planerade"
                )
                .foregroundColor(textPrimary)

                Text(child.hasPet ? "Djur: aktivt den här månaden" : "Djur: inget ägg valt ännu")
                    .foregroundColor(textPrimary)

                if child.streakDays > 0 {
                    Text("Streak: \(child.streakDays) dagar i rad")
                        .foregroundColor(.blue)
                }
            }
            .font(.subheadline)

            HStack(spacing: 12) {
                Button(action: onPetClick) {
                    Label("Djur", systemImage: "pawprint.fill")
                        .frame(maxWidth: .infinity)
                }
                Button(action: onWalletClick) {
                    Label("Plånbok", systemImage: "wallet.pass.fill")
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(buttonPastel)
            .foregroundColor(buttonOnPastel)

            Button(action: onTasksClick) {
                Text("Att göra idag")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(buttonPastel)
            .foregroundColor(buttonOnPastel)

            Button(action: onInviteClick) {
                Text("Bjud in till appen")
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        .tint(buttonOnPastel)
        .foregroundColor(textPrimary)
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(cardPastel.opacity(0.95))
        )
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

import SwiftUI

struct ChildPetView: View {
    let childId: String
    let childName: String
    var onBack: () -> Void = {}

    @State private var isLoading = true
    @State private var error: String?
    @State private var pet: PetResponseDTO?
    @State private var xp: XpProgressResponseDTO?
    @State private var showGiveFood = false

    private let cardTextPrimary = Color(red: 28 / 255, green: 25 / 255, blue: 23 / 255)
    private let cardTextSecondary = Color(red: 87 / 255, green: 83 / 255, blue: 78 / 255)

    var body: some View {
        ZStack {
            backgroundGradient(for: pet?.petType)
                .ignoresSafeArea()

            if isLoading {
                ProgressView()
            } else if let error {
                VStack(spacing: 12) {
                    Text(error)
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                    Button("Försök igen") {
                        Task { await load() }
                    }
                }
                .padding()
            } else {
                ScrollView {
                    VStack(spacing: 16) {
                        header
                        if let pet {
                            petCard(pet: pet, xp: xp)
                            giveFoodButton
                        } else {
                            noPetCard
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
        }
        .task {
            await load()
        }
        .sheet(isPresented: $showGiveFood) {
            if let pet {
                GiveFoodSheet(
                    childName: childName,
                    petName: pet.name ?? PetNameUtilsIOS.getPetNameSwedish(pet.petType),
                    childId: childId,
                    onDismiss: { showGiveFood = false },
                    onSuccess: {
                        showGiveFood = false
                        Task { await load(showLoadingSpinner: false) }
                    }
                )
            }
        }
    }

    // MARK: - Subviews

    private var header: some View {
        HStack {
            Button(action: onBack) {
                HStack(spacing: 4) {
                    Image(systemName: "chevron.backward")
                    Text("Tillbaka")
                }
            }
            .foregroundColor(.white)

            Spacer()

            Text(childName)
                .font(.title2.weight(.bold))
                .foregroundColor(.white)
        }
        .padding(.top, 16)
    }

    private func petCard(pet: PetResponseDTO, xp: XpProgressResponseDTO?) -> some View {
        let petName = pet.name ?? PetNameUtilsIOS.getPetNameSwedish(pet.petType)

        let xpThresholds = [0, 10, 35, 70, 125]
        let level = xp?.currentLevel ?? pet.growthStage
        let safeLevel = max(1, min(xpThresholds.count - 1, level))
        let currentThreshold = xpThresholds[safeLevel - 1]
        let nextThreshold = xpThresholds[min(safeLevel, xpThresholds.count - 1)]
        let range = max(1, nextThreshold - currentThreshold)
        let xpInLevel = xp?.xpInCurrentLevel ?? 0
        let progress: CGFloat = CGFloat(min(max(0, xpInLevel), range)) / CGFloat(range)
        let totalXp = xp?.currentXp ?? 0

        return VStack(spacing: 12) {
            if let imageName = PetImagesIOS.integratedImageName(for: pet.petType, growthStage: pet.growthStage),
               let uiImage = UIImage(named: imageName) {
                Image(uiImage: uiImage)
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: .infinity)
                    .frame(height: 240)
            } else {
                Text("🐾")
                    .font(.system(size: 80))
                    .frame(height: 240)
            }

            Text(petName)
                .font(.title2.weight(.bold))
                .foregroundColor(cardTextPrimary)

            HStack(spacing: 8) {
                Image(systemName: "star.fill")
                    .foregroundColor(.yellow)
                    .font(.subheadline)
                Text("Nivå \(level)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(cardTextPrimary)
                Text("·")
                    .foregroundColor(cardTextSecondary)
                Text("\(totalXp) XP totalt")
                    .font(.subheadline)
                    .foregroundColor(cardTextSecondary)
            }

            VStack(spacing: 4) {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(Color.gray.opacity(0.2))
                            .frame(height: 10)
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(Color.purple)
                            .frame(width: geo.size.width * progress, height: 10)
                    }
                }
                .frame(height: 10)

                HStack {
                    Text("\(xpInLevel) / \(range) XP till nästa nivå")
                        .font(.caption)
                        .foregroundColor(cardTextSecondary)
                    Spacer()
                    Text("\(Int(progress * 100))%")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(cardTextSecondary)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity)
        .background(.white.opacity(0.75), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private var giveFoodButton: some View {
        Button {
            showGiveFood = true
        } label: {
            Label("Ge extra mat", systemImage: "plus.circle.fill")
                .font(.headline)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(Color.white.opacity(0.3))
                )
        }
    }

    private var noPetCard: some View {
        VStack(spacing: 8) {
            Text("🥚")
                .font(.system(size: 64))
            Text("Inget djur denna månad")
                .font(.headline)
                .foregroundColor(cardTextPrimary)
            Text("\(childName) har inte valt ett ägg än.")
                .font(.subheadline)
                .foregroundColor(cardTextSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(.white.opacity(0.75), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    // MARK: - Helpers

    private func load(showLoadingSpinner: Bool = true) async {
        if showLoadingSpinner { isLoading = true }
        error = nil
        do {
            pet = try await PetRepository.fetchPetForMember(memberId: childId)
        } catch ApiError.httpError(404, _) {
            pet = nil
        } catch {
            self.error = "Kunde inte hämta djuret. Försök igen."
            isLoading = false
            return
        }
        xp = try? await ApiClient.shared.send(
            XpProgressResponseDTO.self,
            path: "xp/members/\(childId)/current",
            method: "GET"
        )
        isLoading = false
    }

    // MARK: - Background gradient (same as ChildDashboardView)

    private func backgroundGradient(for petType: String?) -> LinearGradient {
        switch petType?.lowercased() {
        case "dragon":
            return LinearGradient(
                colors: [Color(red: 0x4C/255, green: 0x1D/255, blue: 0x95/255),
                         Color(red: 0x1E/255, green: 0x29/255, blue: 0x3B/255)],
                startPoint: .top, endPoint: .bottom)
        case "cat":
            return LinearGradient(
                colors: [Color(red: 0xFD/255, green: 0xE6/255, blue: 0x8A/255),
                         Color(red: 0xF9/255, green: 0x73/255, blue: 0x16/255)],
                startPoint: .top, endPoint: .bottom)
        case "dog":
            return LinearGradient(
                colors: [Color(red: 0xBB/255, green: 0xF7/255, blue: 0xD0/255),
                         Color(red: 0x22/255, green: 0xC5/255, blue: 0x5E/255)],
                startPoint: .top, endPoint: .bottom)
        case "bird":
            return LinearGradient(
                colors: [Color(red: 0xBF/255, green: 0xDB/255, blue: 0xFE/255),
                         Color(red: 0x25/255, green: 0x63/255, blue: 0xEB/255)],
                startPoint: .top, endPoint: .bottom)
        case "rabbit":
            return LinearGradient(
                colors: [Color(red: 0xFC/255, green: 0xE7/255, blue: 0xF3/255),
                         Color(red: 0xEC/255, green: 0x48/255, blue: 0x99/255)],
                startPoint: .top, endPoint: .bottom)
        case "bear":
            return LinearGradient(
                colors: [Color(red: 0xFE/255, green: 0xF3/255, blue: 0xC7/255),
                         Color(red: 0x92/255, green: 0x40/255, blue: 0x0E/255)],
                startPoint: .top, endPoint: .bottom)
        case "snake":
            return LinearGradient(
                colors: [Color(red: 0xDC/255, green: 0xFC/255, blue: 0xE7/255),
                         Color(red: 0x15/255, green: 0x80/255, blue: 0x3D/255)],
                startPoint: .top, endPoint: .bottom)
        case "panda":
            return LinearGradient(
                colors: [Color(red: 0xE5/255, green: 0xE7/255, blue: 0xEB/255),
                         Color(red: 0x11/255, green: 0x18/255, blue: 0x27/255)],
                startPoint: .top, endPoint: .bottom)
        case "slot":
            return LinearGradient(
                colors: [Color(red: 0xE5/255, green: 0xE7/255, blue: 0xEB/255),
                         Color(red: 0x6B/255, green: 0x72/255, blue: 0x80/255)],
                startPoint: .top, endPoint: .bottom)
        case "hydra":
            return LinearGradient(
                colors: [Color(red: 0xC4/255, green: 0xB5/255, blue: 0xFD/255),
                         Color(red: 0x4C/255, green: 0x1D/255, blue: 0x95/255)],
                startPoint: .top, endPoint: .bottom)
        case "unicorn":
            return LinearGradient(
                colors: [Color(red: 0xFD/255, green: 0xE6/255, blue: 0x8A/255),
                         Color(red: 0xF9/255, green: 0xA8/255, blue: 0xD4/255)],
                startPoint: .top, endPoint: .bottom)
        case "kapybara":
            return LinearGradient(
                colors: [Color(red: 0xDC/255, green: 0xFC/255, blue: 0xE7/255),
                         Color(red: 0x22/255, green: 0xC5/255, blue: 0x5E/255)],
                startPoint: .top, endPoint: .bottom)
        default:
            return LinearGradient(
                colors: [Color(red: 224/255, green: 231/255, blue: 1.0),
                         Color(red: 224/255, green: 242/255, blue: 1.0)],
                startPoint: .top, endPoint: .bottom)
        }
    }
}

// MARK: - Give Food Sheet

private struct GiveFoodSheet: View {
    let childName: String
    let petName: String
    let childId: String
    var onDismiss: () -> Void = {}
    var onSuccess: () -> Void = {}

    @State private var xpAmount: Int = 1
    @State private var isLoading = false
    @State private var errorMessage: String?

    private let quickAmounts = [1, 2, 3, 5]

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Text("Ge extra mat till \(petName)")
                    .font(.headline)
                    .multilineTextAlignment(.center)

                Text("Välj hur mycket bonusmat du vill ge \(childName)s djur (max 100 XP).")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)

                // Quick-select chips
                HStack(spacing: 10) {
                    ForEach(quickAmounts, id: \.self) { amount in
                        let isSelected = xpAmount == amount
                        Button {
                            xpAmount = amount
                        } label: {
                            Text("\(amount) XP")
                                .font(.subheadline.weight(.semibold))
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(
                                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                                        .fill(isSelected ? Color.purple : Color(.systemGray5))
                                )
                                .foregroundColor(isSelected ? .white : .primary)
                        }
                    }
                }

                // Stepper
                Stepper("Mängd: \(xpAmount) XP", value: $xpAmount, in: 1...100)
                    .padding(.horizontal)

                if let errorMessage {
                    Text(errorMessage)
                        .font(.subheadline)
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                }

                Spacer()

                Button {
                    Task { await give() }
                } label: {
                    if isLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    } else {
                        Text("Ge \(xpAmount) XP mat")
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.vertical, 14)
                .background(Color.purple, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .disabled(isLoading)
                .padding(.horizontal)
            }
            .padding(.top, 24)
            .padding(.horizontal, 16)
            .navigationTitle("Ge mat")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Avbryt", action: onDismiss)
                }
            }
        }
    }

    private func give() async {
        isLoading = true
        errorMessage = nil
        do {
            try await PetRepository.awardBonusXp(memberId: childId, xpPoints: xpAmount)
            onSuccess()
        } catch ApiError.httpError(let code, let data) {
            let body = data.flatMap { String(data: $0, encoding: .utf8) } ?? ""
            errorMessage = "Fel \(code): \(body)"
        } catch {
            errorMessage = "Något gick fel. Försök igen."
        }
        isLoading = false
    }
}

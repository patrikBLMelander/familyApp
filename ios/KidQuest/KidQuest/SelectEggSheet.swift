import SwiftUI

struct SelectEggSheet: View {
    /// Vem ägget väljs åt. Nil = den som håller telefonen.
    ///
    /// `pets/select-egg` resolves the member from the device token, which is right for
    /// a child on their own phone and wrong for a parent in "Visa som barn": it would
    /// create a pet for the PARENT and leave the child without one. When this is set
    /// the member-scoped route is used instead.
    var memberId: String?
    /// Vad barnet redan samlat. Tavlan visar de platserna som djur, inte som ägg.
    var history: [PetHistoryResponseDTO] = []
    var onDismiss: () -> Void = {}
    var onEggSelected: (PetResponseDTO) -> Void = { _ in }

    /// Tre steg: välj ägg -> ägget kläcks -> namnge djuret (nu syns djuret). Namnet väljs
    /// efter kläckningen, för ett barn kan inte döpa något det inte sett än.
    private enum Phase { case board, hatching, naming }

    @State private var eggs: [EggCollectionItemDTO] = []
    @State private var selectedEgg: String?
    @State private var petName: String = ""
    @State private var loading: Bool = true
    @State private var saving: Bool = false
    @State private var errorMessage: String?
    @State private var phase: Phase = .board
    @State private var hatchStage: Int = 1

    private var palette: SeasonPalette { SeasonTheme.current(dark: false) }

    /// Djuret bakom det valda ägget -- servern ger petType per ägg, så kläckning och
    /// namnsteg kan visa rätt djur innan pet:en ens skapats.
    private var selectedPetType: String? { eggs.first { $0.eggType == selectedEgg }?.petType }

    var body: some View {
        NavigationStack {
            Group {
                if loading {
                    ProgressView("Laddar ägg…")
                } else if let errorMessage {
                    VStack(spacing: 12) {
                        Text(errorMessage).foregroundColor(.red)
                        Button("Stäng") { onDismiss() }
                    }
                } else {
                    switch phase {
                    case .board: boardContent
                    case .hatching: hatchingContent
                    case .naming: namingContent
                    }
                }
            }
            .padding()
            .navigationTitle(phaseTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if phase == .board {
                        Button("Välj senare") { onDismiss() }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    switch phase {
                    case .board:
                        Button("Välj") { startHatching() }
                            .disabled(selectedEgg == nil)
                    case .hatching:
                        EmptyView()
                    case .naming:
                        Button(saving ? "Sparar…" : "Spara") { Task { await save() } }
                            .disabled(saving)
                    }
                }
            }
        }
        .task { await loadEggTypes() }
    }

    private var phaseTitle: String {
        switch phase {
        case .board: return "Välj ägg"
        case .hatching: return "Ägget kläcks"
        case .naming: return "Namnge ditt djur"
        }
    }

    private var boardContent: some View {
        VStack(alignment: .leading, spacing: 12) {
            ScrollView {
                EggCollectionBoard(
                    eggs: eggs,
                    history: history,
                    selectedEgg: selectedEgg,
                    palette: palette,
                    onSelect: { selectedEgg = $0 }
                )
                .padding(.bottom, 4)
            }
            // Hinten i en fast rad. Namnet frågas INTE här -- först efter kläckningen, när
            // djuret syns.
            Text(selectedEgg.map { EggNames.hint(for: $0) }
                 ?? "Tryck på ett ägg för att höra vad som viskar därinne.")
                .font(.footnote)
                .italic(selectedEgg != nil)
                .foregroundStyle(selectedEgg != nil ? palette.tipInk : palette.inkFaint)
                .frame(maxWidth: .infinity, minHeight: 40, alignment: .leading)
        }
    }

    private var hatchingContent: some View {
        VStack(spacing: 14) {
            Spacer()
            Text("Ägget kläcks…").font(.body).foregroundStyle(palette.ink)
            if let egg = selectedEgg,
               let name = PetImagesIOS.eggImageName(for: egg, stage: hatchStage),
               let img = UIImage(named: name) {
                Image(uiImage: img).resizable().scaledToFit().frame(height: 180)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private var namingContent: some View {
        VStack(spacing: 14) {
            if let pt = selectedPetType,
               let name = PetImagesIOS.petImageName(for: pt, growthStage: 1),
               let img = UIImage(named: name) {
                Image(uiImage: img).resizable().scaledToFit().frame(height: 180)
            }
            Text(selectedPetType.map { "Det blev en \(PetNameUtilsIOS.getPetNameSwedish($0))! Vad ska den heta?" }
                 ?? "Vad ska ditt djur heta?")
                .font(.body)
                .multilineTextAlignment(.center)
                .foregroundStyle(palette.ink)
            TextField("Namn på djuret (valfritt)", text: $petName)
                .textFieldStyle(.roundedBorder)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private func startHatching() {
        guard selectedEgg != nil else { return }
        phase = .hatching
        hatchStage = 1
        Task {
            for s in 1...5 {
                await MainActor.run { hatchStage = s }
                try? await Task.sleep(for: .seconds(0.65))
            }
            try? await Task.sleep(for: .seconds(0.25))
            await MainActor.run { phase = .naming }
        }
    }

    private func loadEggTypes() async {
        loading = true
        errorMessage = nil
        do {
            let loaded = try await AdventureRepository.eggs(memberId: memberId)
            await MainActor.run {
                self.eggs = loaded
                self.selectedEgg = loaded.first(where: { $0.unlocked && !$0.collected })?.eggType
                self.loading = false
            }
        } catch {
            await MainActor.run {
                self.errorMessage = "Kunde inte hämta äggtyper."
                self.loading = false
            }
        }
    }

    private func save() async {
        guard let egg = selectedEgg else { return }
        saving = true
        let trimmedName = petName.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            let pet: PetResponseDTO
            if let memberId {
                pet = try await MemberScopedRepository.selectEgg(
                    memberId: memberId,
                    eggType: egg,
                    name: trimmedName
                )
            } else {
                pet = try await ChildDashboardRepository.selectEgg(eggType: egg, name: trimmedName)
            }
            await MainActor.run {
                saving = false
                onEggSelected(pet)
                onDismiss()
            }
        } catch {
            await MainActor.run {
                saving = false
                errorMessage = ApiErrors.message(error, fallback: "Kunde inte välja ägg.")
            }
        }
    }
}

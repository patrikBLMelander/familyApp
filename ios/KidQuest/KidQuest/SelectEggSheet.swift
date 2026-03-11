import SwiftUI

struct SelectEggSheet: View {
    var onDismiss: () -> Void = {}
    var onEggSelected: (PetResponseDTO) -> Void = { _ in }

    @State private var eggTypes: [String] = []
    @State private var selectedEgg: String?
    @State private var petName: String = ""
    @State private var loading: Bool = true
    @State private var saving: Bool = false
    @State private var errorMessage: String?
    @State private var showHint: Bool = false

    var body: some View {
        NavigationStack {
            Group {
                if loading {
                    ProgressView("Laddar ägg…")
                } else if let errorMessage {
                    VStack(spacing: 12) {
                        Text(errorMessage)
                            .foregroundColor(.red)
                        Button("Stäng") { onDismiss() }
                    }
                } else {
                    content
                }
            }
            .padding()
            .navigationTitle("Välj ägg")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Avbryt") { onDismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(saving ? "Väljer…" : "Välj") {
                        Task { await save() }
                    }
                    .disabled(saving || selectedEgg == nil)
                }
            }
        }
        .task {
            await loadEggTypes()
        }
    }

    private var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Välj ett ägg för ditt nya djur.")
                    .font(.body)

                ForEach(eggTypes, id: \.self) { egg in
                    eggCard(eggType: egg)
                }

                if let selectedEgg {
                    Divider().padding(.vertical, 8)
                    Text("Vad ska ditt djur heta? (valfritt)")
                        .font(.subheadline)
                    TextField("Namn på djuret", text: $petName)
                        .textFieldStyle(.roundedBorder)
                    if showHint {
                        Text(eggHint(for: selectedEgg))
                            .font(.footnote)
                            .foregroundColor(.secondary)
                            .padding(.top, 4)
                    }
                }
            }
        }
    }

    private func eggCard(eggType: String) -> some View {
        let isSelected = eggType == selectedEgg
        let label = eggLabel(for: eggType)

        return Button {
            if selectedEgg == eggType {
                showHint.toggle()
            } else {
                selectedEgg = eggType
                showHint = false
            }
        } label: {
            HStack(spacing: 12) {
                if let name = PetImagesIOS.eggImageName(for: eggType),
                   let uiImage = UIImage(named: name) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 56, height: 56)
                } else {
                    Text("🥚")
                        .font(.largeTitle)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text(label)
                        .font(.headline)
                    if isSelected && !showHint {
                        Text("Tryck igen för att visa hint")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                }
                Spacer()
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.blue)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(isSelected ? Color.blue.opacity(0.12) : Color.gray.opacity(0.08))
            )
        }
        .buttonStyle(.plain)
    }

    private func loadEggTypes() async {
        loading = true
        errorMessage = nil
        do {
            let eggs = try await ApiClient.shared.send([String].self,
                                                       path: "pets/available-eggs",
                                                       method: "GET")
            await MainActor.run {
                self.eggTypes = eggs
                self.selectedEgg = eggs.first
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
        do {
            let pet = try await ChildDashboardRepository.selectEgg(eggType: egg, name: petName.trimmingCharacters(in: .whitespacesAndNewlines))
            await MainActor.run {
                saving = false
                onEggSelected(pet)
                onDismiss()
            }
        } catch {
            await MainActor.run {
                saving = false
                errorMessage = "Kunde inte välja ägg."
            }
        }
    }

    private func eggLabel(for eggType: String) -> String {
        switch eggType.lowercased() {
        case "blue_egg": return "Blått ägg"
        case "green_egg": return "Grönt ägg"
        case "red_egg": return "Rött ägg"
        case "yellow_egg": return "Gult ägg"
        case "purple_egg": return "Lila ägg"
        case "orange_egg": return "Orange ägg"
        case "brown_egg": return "Brunt ägg"
        case "black_egg": return "Mörkt ägg"
        case "gray_egg": return "Grått ägg"
        case "teal_egg": return "Turkost ägg"
        case "pink_egg": return "Rosa ägg"
        case "cyan_egg": return "Blågrönt ägg"
        default: return eggType
        }
    }

    private func eggHint(for eggType: String) -> String {
        switch eggType.lowercased() {
        case "blue_egg": return "Jag älskar att flyga högt bland molnen."
        case "green_egg": return "Jag spinner nöjt när jag får ligga i solen."
        case "red_egg": return "Jag hämtar gärna bollen om du kastar den."
        case "yellow_egg": return "Jag kvittrar gärna när dagen börjar."
        case "purple_egg": return "Jag hoppar fram och gnager gärna på morötter."
        case "orange_egg": return "Jag tar gärna en lång vintersömn med magen full."
        case "brown_egg": return "Jag gillar att slingra mig på varma stenar."
        case "black_egg": return "Jag tycker om att smyga runt i skuggan."
        case "gray_egg": return "Jag rör mig långsamt men kramas gärna länge."
        case "teal_egg": return "Jag trivs där det finns mycket vatten och mystik."
        case "pink_egg": return "Jag gillar glitter, regnbågar och magi."
        case "cyan_egg": return "Jag älskar att plaska runt med kompisar."
        default: return "Jag längtar efter att få träffa dig."
        }
    }
}


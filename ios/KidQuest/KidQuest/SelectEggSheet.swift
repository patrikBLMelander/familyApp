import SwiftUI

struct SelectEggSheet: View {
    /// Vem ägget väljs åt. Nil = den som håller telefonen.
    ///
    /// `pets/select-egg` resolves the member from the device token, which is right for
    /// a child on their own phone and wrong for a parent in "Visa som barn": it would
    /// create a pet for the PARENT and leave the child without one. When this is set
    /// the member-scoped route is used instead. Optional with a default so the child's
    /// own dashboard keeps calling this sheet exactly as before.
    var memberId: String?
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
                        Text(EggNames.hint(for: selectedEgg))
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
        let label = EggNames.label(for: eggType)

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


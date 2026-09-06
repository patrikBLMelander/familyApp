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
    /// Vad barnet redan samlat. Tavlan visar de platserna som djur, inte som ägg.
    var history: [PetHistoryResponseDTO] = []
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

    private var palette: SeasonPalette { SeasonTheme.current(dark: false) }

    private var content: some View {
        VStack(alignment: .leading, spacing: 12) {
            ScrollView {
                EggCollectionBoard(
                    eggTypes: eggTypes,
                    history: history,
                    selectedEgg: selectedEgg,
                    palette: palette,
                    onSelect: { selectedEgg = $0 }
                )
                .padding(.bottom, 4)
            }

            // Hinten i en fast rad i stället för bakom "tryck igen". Den knappen fanns
            // bara på det valda ägget och upptäcktes därför nästan aldrig, vilket gjorde
            // att hälften av väljarens innehåll aldrig lästes.
            Text(selectedEgg.map { EggNames.hint(for: $0) }
                 ?? "Tryck på ett ägg för att höra vad som viskar därinne.")
                .font(.footnote)
                .italic(selectedEgg != nil)
                .foregroundStyle(selectedEgg != nil ? palette.tipInk : palette.inkFaint)
                .frame(maxWidth: .infinity, minHeight: 40, alignment: .leading)

            TextField("Ge det ett namn (valfritt)", text: $petName)
                .textFieldStyle(.roundedBorder)
        }
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


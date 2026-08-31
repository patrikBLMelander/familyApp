import SwiftUI

/// Lägg till en familjemedlem.
///
/// Utan den här kan en ny familj inte lägga till sitt första barn, vilket är hela appen.
/// Knappen har funnits på översikten hela tiden, men ingenting var kopplat till den.
///
/// Följer Android: bara Barn och Förälder erbjuds. ASSISTANT ("Äldre barn") finns kvar
/// i backend för medlemmar som skapades förr, men delas inte ut längre.
struct AddFamilyMemberSheet: View {

    /// Anropas efter en lyckad skapelse, så att översikten kan hämta om sig.
    var onCreated: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.seasonPalette) private var palette

    @State private var name: String = ""
    @State private var isParent: Bool = false
    @State private var ageRange: AgeRange?
    @State private var isSaving: Bool = false
    @State private var errorMessage: String?

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isSaving
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    nameField
                    roleChips
                    if isParent {
                        parentNote
                    } else {
                        ageSection
                    }
                    if let errorMessage {
                        Text(errorMessage)
                            .font(.footnote)
                            .foregroundStyle(palette.danger)
                    }
                }
                .padding(20)
            }
            .background(palette.pageBg.ignoresSafeArea())
            // Kort med flit: "Lägg till familjemedlem" ryms inte i inline-läget och
            // kapades till "Lägg till familjem...".
            .navigationTitle("Ny familjemedlem")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Avbryt") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isSaving ? "Lägger till…" : "Lägg till") {
                        Task { await save() }
                    }
                    .disabled(!canSave)
                }
            }
        }
    }

    private var nameField: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Namn")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.ink)
            TextField("Barnets eller den vuxnas namn", text: $name)
                .textInputAutocapitalization(.words)
                .autocorrectionDisabled()
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(palette.cardEdge, lineWidth: 1)
                )
                .foregroundStyle(palette.ink)
                .onChange(of: name) { _, _ in errorMessage = nil }
        }
    }

    private var roleChips: some View {
        HStack(spacing: 10) {
            chip(label: "Barn", selected: !isParent) { isParent = false }
            chip(label: "Förälder", selected: isParent) { isParent = true }
            Spacer(minLength: 0)
        }
    }

    private var parentNote: some View {
        Text(
            "Föräldern kopplar sin telefon med QR-koden på deras kort. E-post och lösenord "
            + "kan sättas i webbappen om de behöver logga in på en ny enhet."
        )
        .font(.footnote)
        .foregroundStyle(palette.inkSoft)
        .fixedSize(horizontal: false, vertical: true)
    }

    /// Åldern styr bara vilka färdiga sysslor som skapas, vilket en förälder inte har
    /// någon nytta av -- därför visas den bara för barn.
    private var ageSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Ålder (valfritt – för färdiga uppgifter)")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.ink)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ForEach(AgeRange.allCases) { range in
                    chip(label: range.label, selected: ageRange == range) {
                        ageRange = (ageRange == range) ? nil : range
                    }
                }
            }
        }
    }

    private func chip(label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 11)
                .padding(.horizontal, 16)
                .background(
                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                        .fill(selected ? palette.accent : palette.outlineBg)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                        .stroke(selected ? .clear : palette.outlineEdge, lineWidth: 1)
                )
                .foregroundStyle(selected ? palette.onAccent : palette.outlineInk)
        }
        .buttonStyle(.plain)
    }

    private func save() async {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        isSaving = true
        errorMessage = nil
        do {
            let member = try await FamilyRepository.createMember(
                name: trimmed,
                role: isParent ? "PARENT" : "CHILD"
            )
            // Sysslorna skapas efter medlemmen och en i taget. Skulle någon av dem falla
            // står medlemmen kvar -- en familj utan färdiga sysslor är ett mindre problem
            // än ett barn som inte blev skapat.
            if !isParent, let ageRange {
                try? await createDefaultChores(memberId: member.id, ageRange: ageRange)
            }
            await MainActor.run {
                isSaving = false
                onCreated()
                dismiss()
            }
        } catch {
            await MainActor.run {
                isSaving = false
                errorMessage = ApiErrors.message(error, fallback: "Kunde inte lägga till")
            }
        }
    }

    private func createDefaultChores(memberId: String, ageRange: AgeRange) async throws {
        // Backendens veckodagar: 1 = måndag ... 7 = söndag.
        let allWeekdays = ["1", "2", "3", "4", "5", "6", "7"]
        for title in ageRange.defaultChores {
            try await DailyChoreRepositoryIOS.createChore(
                memberId: memberId,
                title: title,
                weekdays: allWeekdays,
                xpPoints: 1
            )
        }
    }
}

/// Samma fyra spann och samma sysslor som Android, ord för ord. En familj som byter
/// telefon ska inte mötas av en annan uppsättning.
private enum AgeRange: String, CaseIterable, Identifiable {
    case fourToSix
    case sevenToNine
    case tenToTwelve
    case thirteenPlus

    var id: String { rawValue }

    var label: String {
        switch self {
        case .fourToSix: "4–6 år"
        case .sevenToNine: "7–9 år"
        case .tenToTwelve: "10–12 år"
        case .thirteenPlus: "13+ år"
        }
    }

    var defaultChores: [String] {
        switch self {
        case .fourToSix:
            [
                "Klä på mig",
                "Borsta tänder",
                "Plocka leksaker i mitt rum",
                "Ställ undan min disk",
                "Hänga upp jacka & skor",
            ]
        case .sevenToNine:
            [
                "Packa skolväskan",
                "Bädda sängen",
                "Plocka undan efter mellis",
                "Kvällsrutin utan tjat",
                "Hjälpa till med disk/dukning",
            ]
        case .tenToTwelve:
            [
                "Läx-/pluggstund",
                "Skräpkoll hemma",
                "Ordning på rummet",
                "Hjälpa till med maten",
                "Skärm efter uppgifter",
            ]
        case .thirteenPlus:
            [
                "Hålla rummet i ordning",
                "Ta hand om min tvätt",
                "Min dagliga hemmasyssla",
                "Kolla dagens schema & tider",
                "Kolla ekonomi & sparmål",
            ]
        }
    }
}

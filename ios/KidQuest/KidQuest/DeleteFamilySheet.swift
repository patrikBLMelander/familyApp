import SwiftUI

/// Ta bort familjen och allt som hör till den.
///
/// Båda butikerna kräver en väg hit inifrån appen, och Apple avvisar utan den. Den
/// låg inte i iOS-appen alls: menyposten fanns i koden men var dold, eftersom
/// ingenting kopplade den.
///
/// Bekräftelsen är ett skrivfält och inte bara en knapp, precis som på Android. Det
/// här är det enda i appen som tar bort andras data också -- den andra föräldern och
/// varje barn förlorar allt samtidigt -- och ett felaktigt tryck går inte att ångra.
struct DeleteFamilySheet: View {

    /// Anropas när familjen faktiskt är borttagen. Anroparen får rensa sessionen och
    /// skicka användaren tillbaka till välkomstskärmen.
    var onDeleted: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.seasonPalette) private var palette

    @State private var typed: String = ""
    @State private var isDeleting = false
    @State private var errorMessage: String?

    /// Samma ord som Android, så att en förälder som sett den ena skärmen känner igen
    /// den andra.
    private static let confirmation = "TA BORT"

    private var isConfirmed: Bool {
        typed.trimmingCharacters(in: .whitespacesAndNewlines)
            .caseInsensitiveCompare(Self.confirmation) == .orderedSame
    }

    private var familyId: String? {
        TokenStoreIOS.shared.getSession()?.familyId
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(
                        "Det här tar bort hela familjen och allt som hör till den: alla barn "
                        + "och vuxna, sysslor, XP, djur, plånböcker och sparmål. Även för de "
                        + "andra i familjen."
                    )
                    .foregroundStyle(palette.ink)
                    .fixedSize(horizontal: false, vertical: true)

                    Text("Det går inte att ångra, och ingenting sparas.")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(palette.danger)
                        .fixedSize(horizontal: false, vertical: true)

                    Text(
                        "Har du en prenumeration behöver du avsluta den separat i App Store "
                        + "— den försvinner inte med kontot."
                    )
                    .font(.footnote)
                    .foregroundStyle(palette.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)

                    Divider().overlay(palette.cardEdge)

                    Text("Skriv \(Self.confirmation) för att bekräfta:")
                        .font(.footnote)
                        .foregroundStyle(palette.inkSoft)

                    TextField("", text: $typed)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .disabled(isDeleting)
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

                    if familyId == nil {
                        Text("Ingen familj i sessionen. Logga in igen och försök om.")
                            .font(.footnote)
                            .foregroundStyle(palette.danger)
                    }

                    if let errorMessage {
                        Text(errorMessage)
                            .font(.footnote)
                            .foregroundStyle(palette.danger)
                    }

                    deleteButton
                }
                .padding(20)
            }
            .background(palette.pageBg.ignoresSafeArea())
            .navigationTitle("Ta bort familjen?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Avbryt") { dismiss() }
                        .disabled(isDeleting)
                }
            }
            .interactiveDismissDisabled(isDeleting)
        }
    }

    private var deleteButton: some View {
        let enabled = isConfirmed && !isDeleting && familyId != nil
        return Button {
            Task { await deleteFamily() }
        } label: {
            Text(isDeleting ? "Tar bort…" : "Ta bort allt")
                .font(.body.weight(.semibold))
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .foregroundStyle(enabled ? Color.white : palette.inkFaint)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(enabled ? palette.danger : palette.outlineBg)
                )
                // outlineBg är samma färg som pageBg i alla fyra ljusa paletter, så
                // utan kontur har den avstängda knappen ingen form alls -- bara grå
                // text som svävar på sidan. Samma fel som fanns på Android.
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(enabled ? .clear : palette.outlineEdge, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    private func deleteFamily() async {
        guard let familyId else { return }
        isDeleting = true
        errorMessage = nil
        do {
            try await FamilyRepository.deleteFamily(familyId: familyId)
            await MainActor.run {
                isDeleting = false
                onDeleted()
                dismiss()
            }
        } catch {
            await MainActor.run {
                isDeleting = false
                errorMessage = ApiErrors.message(error, fallback: "Kunde inte ta bort familjen.")
            }
        }
    }
}

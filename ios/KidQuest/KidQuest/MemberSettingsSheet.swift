import SwiftUI

/// Vem inställningarna gäller. `sheet(item:)` behöver en Identifiable, och det här
/// bär samtidigt det som avgör vilka val som ska visas.
struct MemberSettingsTarget: Identifiable {
    let id: String
    let name: String
    let role: String
    /// Den inloggade får inte ta bort sig själv -- då står familjen utan den som
    /// kunde ha bjudit in någon igen.
    let isCurrentUser: Bool

    var isChild: Bool { role.uppercased() == "CHILD" }
}

/// Byt namn, sätt lösenord, ta bort medlem.
///
/// Alla tre fanns bara på Android. Lösenordet erbjuds inte för barn: de har inget,
/// de loggar in med en QR-kod från en förälder, och ett fält som sätter något som
/// aldrig används vore vilseledande snarare än ofullständigt.
struct MemberSettingsSheet: View {

    let target: MemberSettingsTarget

    /// Anropas när något faktiskt ändrades, så att översikten kan hämta om sig.
    var onChanged: () -> Void
    /// Skild från [onChanged] eftersom en radering kan behöva mer än en omhämtning --
    /// tar man bort sig själv finns inte längre någon vy att hämta till.
    var onDeleted: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.seasonPalette) private var palette

    @State private var name: String = ""
    @State private var password: String = ""
    @State private var passwordRepeat: String = ""
    @State private var isSavingName = false
    @State private var isSavingPassword = false
    @State private var isDeleting = false
    @State private var showDeleteConfirm = false
    @State private var errorMessage: String?
    @State private var statusMessage: String?

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var canSaveName: Bool {
        !trimmedName.isEmpty && trimmedName != target.name && !isSavingName
    }

    private var passwordProblem: String? {
        if password.isEmpty && passwordRepeat.isEmpty { return nil }
        if password.count < 6 { return "Lösenordet måste vara minst 6 tecken." }
        if password != passwordRepeat { return "Lösenorden är inte lika." }
        return nil
    }

    private var canSavePassword: Bool {
        !password.isEmpty && passwordProblem == nil && !isSavingPassword
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    nameSection
                    if !target.isChild {
                        passwordSection
                    }
                    if !target.isCurrentUser {
                        deleteSection
                    }
                    if let statusMessage {
                        Text(statusMessage)
                            .font(.footnote)
                            .foregroundStyle(palette.goodInk)
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
            .navigationTitle(target.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Klar") { dismiss() }
                }
            }
            .alert("Ta bort \(target.name)?", isPresented: $showDeleteConfirm) {
                Button("Avbryt", role: .cancel) {}
                Button("Ta bort", role: .destructive) {
                    Task { await deleteMember() }
                }
            } message: {
                Text(
                    "Sysslor, avklaranden, XP, djur och plånbokshistorik försvinner med "
                    + "medlemmen. Det går inte att ångra."
                )
            }
        }
        .onAppear { name = target.name }
    }

    // MARK: - Namn

    private var nameSection: some View {
        section(title: "Namn") {
            field(placeholder: "Namn", text: $name)
            actionButton(
                title: isSavingName ? "Sparar…" : "Spara namn",
                enabled: canSaveName
            ) {
                Task { await saveName() }
            }
        }
    }

    // MARK: - Lösenord

    private var passwordSection: some View {
        section(title: "Lösenord") {
            Text(
                target.isCurrentUser
                ? "Sätt ett nytt lösenord för ditt eget konto."
                : "En förälder kan sätta lösenordet åt en annan vuxen. Det är vägen "
                  + "tillbaka in för den som låst ute sig."
            )
            .font(.footnote)
            .foregroundStyle(palette.inkSoft)
            .fixedSize(horizontal: false, vertical: true)

            secureField(placeholder: "Nytt lösenord", text: $password)
            secureField(placeholder: "Upprepa lösenord", text: $passwordRepeat)

            if let passwordProblem {
                Text(passwordProblem)
                    .font(.caption)
                    .foregroundStyle(palette.danger)
            }

            actionButton(
                title: isSavingPassword ? "Sparar…" : "Spara lösenord",
                enabled: canSavePassword
            ) {
                Task { await savePassword() }
            }
        }
    }

    // MARK: - Radera

    private var deleteSection: some View {
        section(title: "Ta bort") {
            Text("Tar bort \(target.name) och allt som hör till dem. Går inte att ångra.")
                .font(.footnote)
                .foregroundStyle(palette.inkSoft)
                .fixedSize(horizontal: false, vertical: true)

            Button {
                showDeleteConfirm = true
            } label: {
                Text(isDeleting ? "Tar bort…" : "Ta bort \(target.name)")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .foregroundStyle(palette.danger)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.outlineBg)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(palette.danger.opacity(0.5), lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
            .disabled(isDeleting)
        }
    }

    // MARK: - Byggstenar

    private func section<Content: View>(
        title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.ink)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous).fill(palette.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(palette.cardEdge, lineWidth: 1)
        )
    }

    private func field(placeholder: String, text: Binding<String>) -> some View {
        TextField(placeholder, text: text)
            .textInputAutocapitalization(.words)
            .autocorrectionDisabled()
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.pageBg)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(palette.cardEdge, lineWidth: 1)
            )
            .foregroundStyle(palette.ink)
    }

    private func secureField(placeholder: String, text: Binding<String>) -> some View {
        SecureField(placeholder, text: text)
            .textContentType(.newPassword)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.pageBg)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(palette.cardEdge, lineWidth: 1)
            )
            .foregroundStyle(palette.ink)
    }

    private func actionButton(
        title: String,
        enabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(.body.weight(.semibold))
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .foregroundStyle(enabled ? palette.onAccent : palette.inkFaint)
                // Ligger inne i ett kort, inte på sidan, så outlineBg syns mot den vita
                // ytan. Konturen finns ändå med för att formen inte ska försvinna om
                // kortet någon gång får sidans färg.
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(enabled ? palette.accent : palette.outlineBg)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(enabled ? .clear : palette.outlineEdge, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    // MARK: - Anrop

    private func saveName() async {
        isSavingName = true
        errorMessage = nil
        statusMessage = nil
        do {
            _ = try await FamilyRepository.updateMemberName(memberId: target.id, name: trimmedName)
            await MainActor.run {
                isSavingName = false
                statusMessage = "Namnet är sparat."
                onChanged()
            }
        } catch {
            await MainActor.run {
                isSavingName = false
                errorMessage = ApiErrors.message(error, fallback: "Kunde inte spara namnet")
            }
        }
    }

    private func savePassword() async {
        isSavingPassword = true
        errorMessage = nil
        statusMessage = nil
        do {
            try await FamilyRepository.updatePassword(memberId: target.id, password: password)
            await MainActor.run {
                isSavingPassword = false
                password = ""
                passwordRepeat = ""
                statusMessage = "Lösenordet är satt."
            }
        } catch {
            await MainActor.run {
                isSavingPassword = false
                errorMessage = ApiErrors.message(error, fallback: "Kunde inte sätta lösenordet")
            }
        }
    }

    private func deleteMember() async {
        isDeleting = true
        errorMessage = nil
        do {
            try await FamilyRepository.deleteMember(memberId: target.id)
            await MainActor.run {
                isDeleting = false
                onDeleted()
                dismiss()
            }
        } catch {
            await MainActor.run {
                isDeleting = false
                errorMessage = ApiErrors.message(error, fallback: "Kunde inte ta bort")
            }
        }
    }
}

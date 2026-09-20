import SwiftUI

/// Lets a family enter a referral code, tying their account to the affiliate who gave it.
///
/// First-touch on the server, so a second code once attributed does nothing. The code is
/// the only input; the family comes from the device token in ApiClient.
struct ReferralCodeSheet: View {
    @Environment(\.dismiss) private var dismiss

    @State private var code = ""
    @State private var submitting = false
    @State private var errorMessage: String?
    @State private var done = false

    private struct RedeemRequest: Encodable { let code: String }

    var body: some View {
        NavigationStack {
            Form {
                if done {
                    Section {
                        Text("Din kod är registrerad. Tack för att du stödjer den som tipsade dig om KidQuest!")
                    }
                } else {
                    Section {
                        TextField("Kod", text: $code)
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                            .onChange(of: code) { errorMessage = nil }
                    } header: {
                        Text("Värvningskod")
                    } footer: {
                        Text("Har du fått en kod av någon? Skriv in den så kopplas ditt konto till dem.")
                    }
                    if let errorMessage {
                        Section {
                            Text(errorMessage).foregroundStyle(.red)
                        }
                    }
                }
            }
            .navigationTitle(done ? "Tack!" : "Värvningskod")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(done ? "Klar" : "Avbryt") { dismiss() }
                }
                if !done {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Registrera") { Task { await submit() } }
                            .disabled(code.trimmingCharacters(in: .whitespaces).isEmpty || submitting)
                    }
                }
            }
        }
    }

    private func submit() async {
        let trimmed = code.trimmingCharacters(in: .whitespaces).uppercased()
        guard !trimmed.isEmpty else { return }
        submitting = true
        errorMessage = nil
        do {
            try await ApiClient.shared.sendWithoutResponse(
                path: "affiliates/redeem",
                method: "POST",
                body: RedeemRequest(code: trimmed)
            )
            done = true
        } catch ApiError.httpError(let status, _) where status == 400 {
            errorMessage = "Koden känns inte igen. Dubbelkolla stavningen."
        } catch {
            errorMessage = "Kunde inte registrera koden. Försök igen."
        }
        submitting = false
    }
}

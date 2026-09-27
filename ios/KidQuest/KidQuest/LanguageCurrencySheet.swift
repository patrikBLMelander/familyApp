import SwiftUI

/// Språk för den som är inloggad, och familjens valuta (bara föräldrar).
///
/// Språket sparas både lokalt (byts direkt, se `AppLanguage`) och på servern,
/// så att felmeddelanden och e-post följer samma val och valet följer med till en ny telefon.
struct LanguageCurrencySheet: View {
    @Environment(\.dismiss) private var dismiss

    /// Valuta får bara föräldrar ändra -- samma regel som servern.
    let canChangeCurrency: Bool

    @State private var language: String? = AppLanguage.preference
    @State private var currency: String = Money.currency
    @State private var errorMessage: String?
    @State private var savingCurrency = false

    /// `null` betyder "följ telefonen", så nyckeln skickas alltid (syntetiserad kod utelämnar den).
    private struct LanguageRequest: Encodable {
        let language: String?
        enum CodingKeys: String, CodingKey { case language }
        func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(language, forKey: .language)
        }
    }
    private struct CurrencyRequest: Encodable { let currency: String }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker(selection: $language) {
                        Text("Följ telefonen").tag(String?.none)
                        ForEach(AppLanguage.supported, id: \.self) { code in
                            Text(verbatim: AppLanguage.displayName(code)).tag(Optional(code))
                        }
                    } label: {
                        Text("Språk")
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                    .onChange(of: language) { _, newValue in
                        Task { await saveLanguage(newValue) }
                    }
                } header: {
                    Text("Språk")
                }

                if canChangeCurrency {
                    Section {
                        Picker(selection: $currency) {
                            ForEach(Money.supportedCurrencies, id: \.self) { code in
                                Text(verbatim: Money.displayName(code)).tag(code)
                            }
                        } label: {
                            Text("Valuta")
                        }
                        .disabled(savingCurrency)
                        .onChange(of: currency) { oldValue, newValue in
                            guard oldValue != newValue else { return }
                            Task { await saveCurrency(newValue, revertTo: oldValue) }
                        }
                    } header: {
                        Text("Valuta")
                    } footer: {
                        Text("Gäller hela familjens plånböcker. Beloppen räknas inte om.")
                    }
                }

                if let errorMessage {
                    Section {
                        Text(errorMessage).foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Språk och valuta")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Klar") { dismiss() }
                }
            }
        }
    }

    /// Servern först: språkbytet bygger om hela gränssnittet, och det här arket med det.
    /// Misslyckas sparningen gäller det lokala valet ändå; servern får veta nästa gång.
    private func saveLanguage(_ code: String?) async {
        if let memberId = TokenStoreIOS.shared.getSession()?.memberId {
            try? await ApiClient.shared.sendWithoutResponse(
                path: "family-members/\(memberId)/language",
                method: "PATCH",
                body: LanguageRequest(language: code)
            )
        }
        AppLanguage.setPreference(code)
    }

    private func saveCurrency(_ code: String, revertTo previous: String) async {
        guard let familyId = TokenStoreIOS.shared.getSession()?.familyId else { return }
        savingCurrency = true
        errorMessage = nil
        do {
            try await ApiClient.shared.sendWithoutResponse(
                path: "families/\(familyId)/currency",
                method: "PATCH",
                body: CurrencyRequest(currency: code)
            )
            Money.currency = code
        } catch {
            currency = previous
            errorMessage = ApiErrors.message(error, fallback: String(localized: "Kunde inte byta valuta."))
        }
        savingCurrency = false
    }
}

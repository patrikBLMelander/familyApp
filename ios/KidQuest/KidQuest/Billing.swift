import Foundation
import RevenueCat

/// Köpidentiteten, på ett ställe.
///
/// App User ID är **familjens** id, aldrig en medlems. Ett hushåll köper en gång: en
/// förälder prenumererar, och barnens device-tokens -- som inte har något butikskonto
/// alls -- täcks av samma köp. Med ett medlems-id hade samma familj sålts appen en gång
/// per person.
///
/// Det stämmer också med backend, där `family_subscription` har familjens id som nyckel,
/// så en webhook som bär App User ID pekar direkt på raden den ska uppdatera.
///
/// Speglar Androids `Billing`, med en skillnad: identify och forget anropas inifrån
/// [TokenStoreIOS] i stället för från varje inloggningsställe. Android gör det på
/// anropsplatserna, och det finns sju av dem här -- ett bortglömt hade gett en familj
/// någon annans köp.
enum Billing {

    /// Anropas en gång vid appstart, före allt annat.
    static func configure() {
        guard BillingConfig.isConfigured else {
            // Ingen nyckel: hela betalvägen ska vara osynlig, inte trasig.
            return
        }
        guard !Purchases.isConfigured else { return }
        #if DEBUG
        Purchases.logLevel = .debug
        #else
        Purchases.logLevel = .warn
        #endif
        Purchases.configure(withAPIKey: BillingConfig.revenueCatPublicKey)
    }

    /// Binder köp till en familj. Anropas efter varje inloggning som ger en.
    ///
    /// Går att anropa om med samma id. Fel loggas hellre än visas: en familj som inte
    /// når RevenueCat ska ändå kunna använda appen, eftersom servern avgör
    /// berättigandet oavsett.
    static func identify(familyId: String?) {
        guard Purchases.isConfigured,
              let familyId,
              !familyId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else { return }
        Task {
            do {
                _ = try await Purchases.shared.logIn(familyId)
            } catch {
                print("[Billing] kunde inte identifiera familj \(familyId): \(error.localizedDescription)")
            }
        }
    }

    /// Kopplar loss enheten från en familj vid utloggning, så att en efterföljande
    /// inloggning på samma telefon -- en annan förälder, eller ett barn som kopplar via
    /// QR -- inte ärver den förra familjens köp.
    static func forget() {
        guard Purchases.isConfigured else { return }
        Task {
            do {
                _ = try await Purchases.shared.logOut()
            } catch {
                print("[Billing] kunde inte logga ut ur RevenueCat: \(error.localizedDescription)")
            }
        }
    }
}

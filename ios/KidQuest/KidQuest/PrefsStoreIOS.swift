import Foundation

/// Små lokala inställningar, skilda från sessionen så att en utloggning inte rensar dem.
///
/// UserDefaults duger här och bara här: det som ligger inne är en bock, inte en
/// personuppgift och inte en nyckel. Sessionen ligger i nyckelringen av skäl som
/// [KeychainSessionStore] går igenom.
///
/// Motsvarar Androids PrefsStore. Allt annat om kom igång-guiden räknas fram från
/// familjens faktiska data vid varje laddning, vilket är det som gör att den överlever
/// en ominstallation, är färdig direkt för en familj som registrerat sig på webben, och
/// korrekt kommer tillbaka om en förälder senare tar bort sitt enda barn.
enum PrefsStoreIOS {

    private static let onboardingDismissedKey = "kidquest_onboarding_dismissed"

    static var isOnboardingDismissed: Bool {
        get { UserDefaults.standard.bool(forKey: onboardingDismissedKey) }
        set { UserDefaults.standard.set(newValue, forKey: onboardingDismissedKey) }
    }
}

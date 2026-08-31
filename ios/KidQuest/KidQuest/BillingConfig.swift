import Foundation

/// RevenueCat-konfiguration för iOS.
///
/// Den publika SDK-nyckeln ligger med flit i källkod. RevenueCats klientnycklar är
/// gjorda för att följa med in i appbinären -- vem som helst kan läsa ut den ur ett
/// paket, och den ger ingenting utöver vad appen själv kan göra. Hemligheten värd att
/// skydda är webhookens signeringsnyckel, som bor i backend och aldrig kommer nära
/// den här filen.
///
/// Motsvarar Androids `BillingConfig`, men nyckeln är en annan: App Store-nyckeln
/// börjar på `appl_`, Play-nyckeln på `goog_`. **De kan inte se varandras köp** --
/// Play-nyckeln vet ingenting om ett App Store-köp och tvärtom.
enum BillingConfig {

    /// Tom tills appen finns i RevenueCat. Så länge den är tom är hela betalvägen
    /// osynlig i stället för en knapp som inte leder någonstans -- se [isConfigured].
    ///
    /// Hämtas i RevenueCat under API keys, den för App Store. En nyckel som börjar på
    /// `test_` hör till Test Store och kan inte se riktiga köp.
    static let revenueCatPublicKey = ""

    /// Entitlement-identifieraren i RevenueCat. Samma sträng som Android använder, för
    /// det är samma entitlement som säljs -- ett hushåll, en prenumeration.
    ///
    /// Det här styr vad som *visas*. Vad som är *tillåtet* avgör servern, som svarar
    /// 402 på skrivningar när en familj inte längre är berättigad. Se SubscriptionService.
    static let entitlementPro = "pro"

    static var isConfigured: Bool {
        !revenueCatPublicKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

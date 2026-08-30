import Foundation

/// Gör om ett misslyckat anrop till något en förälder kan agera på.
///
/// `error.localizedDescription` för en `ApiError` är bara "The operation couldn't be
/// completed", vilket slängde bort anledningen som backend redan hade skickat med.
/// Testarna såg "Fel: ..." på inloggningsskärmen medan servern rakt av hade sagt att
/// det inte fanns något konto med den e-postadressen.
///
/// Backend svarar med {"error": "..."} via GlobalExceptionHandler, så det riktiga
/// meddelandet finns alltid att läsa.
enum ApiErrors {

    /// Backend-meddelandena är engelska och skrivna för utvecklare. Det här är de som
    /// en användare faktiskt kan råka ut för, formulerade så att nästa steg blir tydligt.
    private static let translations: [String: String] = [
        "No account found with this email":
            "Det finns inget konto med den e-postadressen. Har du skapat ett konto än?",
        "Invalid password":
            "Fel lösenord. Försök igen.",
        "Email is required":
            "Fyll i din e-postadress.",
        "Password is required":
            "Fyll i ditt lösenord.",
        "Password not set for this account. Please set a password first.":
            "Kontot har inget lösenord än. Sätt ett lösenord i webbappen först.",
        "Email login is only available for parent or assistant users":
            "Bara föräldrar loggar in med e-post. Barn använder QR-koden eller en kod.",
        "Password must be at least 6 characters long":
            "Lösenordet måste vara minst 6 tecken.",
        "Invalid device token":
            "Inloggningen gäller inte längre. Be en förälder visa en ny kod.",
        "Family member not found for device token":
            "Inloggningen gäller inte längre. Be en förälder visa en ny kod.",
        "Invalid invite token":
            "Koden stämmer inte. Be en förälder visa en ny kod.",
        "Invite token has expired":
            "Koden har gått ut. Be en förälder visa en ny kod.",
        "Device token already in use":
            "Den här telefonen är redan kopplad till någon annan i familjen.",
        "Pet already selected for this month":
            "Ett djur är redan valt den här månaden.",
        "No unfed food available":
            "Det finns ingen mat att ge just nu.",
        "Subscription required for this action":
            "Provperioden har gått ut. Barnens sysslor och djur fungerar som vanligt, "
            + "men för att lägga till eller ändra behöver familjen en prenumeration.",
    ]

    /// - Parameter fallback: visas när felet inte bär på något användbart, så att varje
    ///   anropsställe kan vara specifikt om vad det försökte göra.
    static func message(_ error: Error, fallback: String) -> String {
        // Ingen uppkoppling alls är värt att säga rakt ut istället för att skylla på inmatningen.
        if error is URLError {
            return "Kunde inte nå servern. Kontrollera din uppkoppling."
        }

        if let apiError = error as? ApiError, case let .httpError(status, data) = apiError {
            if let server = serverMessage(from: data) {
                return translations[server] ?? server
            }
            // Inget läsbart i bodyn: skilj åtminstone på "ditt fel" och "vårt fel",
            // vilket en naken statuskod inte gör.
            switch status {
            case 401, 403:
                return "Du har inte behörighet till det här."
            // Servern nekade på grund av utebliven betalning, inte behörighet. Bannern
            // på dashboarden är redan uppe och leder till betalväggen, så det här
            // behöver bara förklara, inte navigera.
            case 402:
                return "Provperioden har gått ut. Förnya för att lägga till eller ändra."
            case 404:
                return fallback
            case 500...599:
                return "Något gick fel hos servern. Försök igen om en stund."
            default:
                return fallback
            }
        }

        return fallback
    }

    private static func serverMessage(from data: Data?) -> String? {
        guard let data, !data.isEmpty else { return nil }
        // En body som inte är JSON (en felsida från en proxy, säg) är inte värd att fallera på.
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let message = json["error"] as? String
        else {
            return nil
        }
        let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

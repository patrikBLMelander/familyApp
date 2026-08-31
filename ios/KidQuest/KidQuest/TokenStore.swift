import Foundation

/// Vem som är inloggad på den här enheten.
///
/// Rollen sparas tillsammans med token eftersom uppstarten måste routa på den: en
/// device-token säger i sig inte om den tillhör en förälder eller ett barn, och att
/// skicka ett barn till föräldravyn ger dem uppgiftshantering och familjens plånbok.
/// Att spara den lokalt gör dessutom att uppstarten slipper ett nätverksanrop.
struct Session: Equatable, Codable {
    let deviceToken: String
    let memberId: String?
    let memberName: String?
    let role: String?
    let familyId: String?

    var isChild: Bool {
        role?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() == "CHILD"
    }

    /// Sant för sessioner som sparades innan rollen lagrades lokalt; behöver en uppslagning.
    var isIncomplete: Bool {
        isBlank(role) || isBlank(memberId)
    }

    private func isBlank(_ value: String?) -> Bool {
        guard let value else { return true }
        return value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

/// iOS-version av Androids TokenStore: håller sessionen i nyckelringen.
///
/// Låg tidigare i UserDefaults, som säkerhetskopieras till iCloud. Se
/// [KeychainSessionStore] för varför det inte duger för en bärarnyckel.
final class TokenStoreIOS {
    static let shared = TokenStoreIOS()

    private let defaults = UserDefaults.standard

    /// Så här skrevs sessionen innan den flyttade till nyckelringen. Läses en gång vid
    /// start så att en redan inloggad familj följer med över uppgraderingen i stället
    /// för att kastas ut till välkomstskärmen, och raderas sedan. Att behålla läsaren
    /// för alltid hade betytt att klartexten fick ligga kvar för alltid.
    private let legacyTokenKey = "kidquest_device_token"
    private let legacyMemberIdKey = "kidquest_member_id"
    private let legacyMemberNameKey = "kidquest_member_name"
    private let legacyMemberRoleKey = "kidquest_member_role"
    private let legacyFamilyIdKey = "kidquest_family_id"

    /// Nyckelringsposter överlever att appen raderas; UserDefaults gör det inte.
    /// Skillnaden är vad som gör den här flaggan meningsfull: saknas den är det en ny
    /// installation, och då ska en kvarvarande session från förra installationen bort.
    ///
    /// Utan det hade "radera appen och installera om" inte loggat ut någon, vilket dels
    /// avviker från Android, dels är fel person inloggad den dagen telefonen går vidare
    /// till ett annat barn.
    private let installMarkerKey = "kidquest_keychain_install_marker"

    private init() {}

    private(set) var current: Session?

    func load() {
        defer { Billing.identify(familyId: current?.familyId) }
        if !defaults.bool(forKey: installMarkerKey) {
            // Ny installation: allt i nyckelringen är rester från en tidigare.
            KeychainSessionStore.delete()
            current = migrateLegacySession()
            defaults.set(true, forKey: installMarkerKey)
            return
        }

        guard let data = KeychainSessionStore.read() else {
            current = migrateLegacySession()
            return
        }

        guard let session = try? JSONDecoder().decode(Session.self, from: data) else {
            // Posten finns men går inte att tolka. Den kommer aldrig att gå att tolka.
            KeychainSessionStore.delete()
            current = nil
            return
        }
        current = session
    }

    func getSession() -> Session? {
        current
    }

    func getToken() -> String? {
        current?.deviceToken
    }

    func setSession(
        deviceToken: String,
        memberId: String?,
        memberName: String?,
        role: String?,
        familyId: String? = nil
    ) {
        let session = Session(
            deviceToken: deviceToken,
            memberId: memberId,
            memberName: memberName,
            role: role,
            familyId: familyId
        )
        persist(session)
        current = session
        Billing.identify(familyId: familyId)
    }

    /// Finns kvar för anropsställen som bara har en token; slänger samtidigt gammal identitet.
    func setToken(_ token: String) {
        setSession(deviceToken: token, memberId: nil, memberName: nil, role: nil, familyId: nil)
    }

    func clearToken() {
        KeychainSessionStore.delete()
        clearLegacyKeys()
        current = nil
        // Annars ärver nästa inloggning på samma telefon den förra familjens köp.
        Billing.forget()
    }

    private func persist(_ session: Session) {
        if let data = try? JSONEncoder().encode(session) {
            KeychainSessionStore.write(data)
        }
        // Sätts här och inte bara i [load] så att ordningen inte spelar roll. Skulle en
        // session någon gång sparas innan appen hunnit köra sin första load -- ett nytt
        // anropsställe, en ändrad uppstart -- hade nästa start annars sett den som en
        // rest från en tidigare installation och kastat den.
        defaults.set(true, forKey: installMarkerKey)
        // Alltid, även om skrivningen ovan misslyckades: att lämna kvar en läsbar
        // bärarnyckel är värre än en extra inloggning.
        clearLegacyKeys()
    }

    /// Skriver om en session från UserDefaults till nyckelringen och raderar originalet.
    ///
    /// Raderingen är villkorslös, även när nyckelringsskrivningen misslyckades. Efter
    /// den första lyckade starten kör den här koden aldrig igen, så en kvarlämnad
    /// klartexttoken hade blivit kvar för gott.
    private func migrateLegacySession() -> Session? {
        guard let token = defaults.string(forKey: legacyTokenKey) else {
            clearLegacyKeys()
            return nil
        }
        let session = Session(
            deviceToken: token,
            memberId: defaults.string(forKey: legacyMemberIdKey),
            memberName: defaults.string(forKey: legacyMemberNameKey),
            role: defaults.string(forKey: legacyMemberRoleKey),
            familyId: defaults.string(forKey: legacyFamilyIdKey)
        )
        persist(session)
        return session
    }

    // MARK: - Endast för tester

    /// Återställer till läget "appen har aldrig körts på den här enheten".
    ///
    /// Finns för att migreringen ska gå att testa på riktigt i stället för att
    /// resoneras om. Den är den enda delen som gör verklig skada om den är fel: den
    /// hade loggat ut varje installerad enhet samtidigt, och barn kan inte logga in
    /// själva -- de behöver en förälder med en QR-kod.
    func resetForTesting() {
        KeychainSessionStore.delete()
        clearLegacyKeys()
        defaults.removeObject(forKey: installMarkerKey)
        current = nil
    }

    /// Skriver en session på det sätt appen gjorde före nyckelringen.
    func writeLegacyPlaintextSessionForTesting(_ session: Session) {
        defaults.set(session.deviceToken, forKey: legacyTokenKey)
        write(session.memberId, forKey: legacyMemberIdKey)
        write(session.memberName, forKey: legacyMemberNameKey)
        write(session.role, forKey: legacyMemberRoleKey)
        write(session.familyId, forKey: legacyFamilyIdKey)
        current = nil
    }

    /// Sant om något av de gamla klartextfälten ligger kvar i UserDefaults.
    var hasLegacyPlaintextForTesting: Bool {
        [legacyTokenKey, legacyMemberIdKey, legacyMemberNameKey,
         legacyMemberRoleKey, legacyFamilyIdKey].contains { defaults.object(forKey: $0) != nil }
    }

    /// Markerar att appen redan har körts, utan att röra sessionen.
    func markInstalledForTesting() {
        defaults.set(true, forKey: installMarkerKey)
    }

    /// Glömmer den cachade sessionen utan att röra det som ligger på disk, så att
    /// [load] kan testas som om appen just startats.
    func forgetCachedSessionForTesting() {
        current = nil
    }

    private func write(_ value: String?, forKey key: String) {
        if let value {
            defaults.set(value, forKey: key)
        } else {
            defaults.removeObject(forKey: key)
        }
    }

    private func clearLegacyKeys() {
        for key in [
            legacyTokenKey, legacyMemberIdKey, legacyMemberNameKey,
            legacyMemberRoleKey, legacyFamilyIdKey,
        ] {
            defaults.removeObject(forKey: key)
        }
    }
}

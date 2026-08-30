import Foundation
import Security

/// Den enda platsen i appen som pratar med nyckelringen.
///
/// En device-token är en bärarnyckel: den som har strängen *är* familjemedlemmen,
/// utan lösenord att också ta sig förbi. För ett barn är den hela inloggningen, för de
/// har inget lösenord alls. I UserDefaults låg den i en property list som
/// säkerhetskopieras till iCloud, och därmed kunde följa med av enheten.
///
/// Nyckelringen löser det på två sätt samtidigt. Posten skrivs med
/// `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`: `ThisDeviceOnly` gör att den
/// varken hamnar i en säkerhetskopia eller följer med till en ny telefon, och
/// `AfterFirstUnlock` gör att appen ändå kommer åt den efter en omstart utan att
/// användaren måste låsa upp först.
///
/// Posten är inte heller synkroniserbar (`kSecAttrSynchronizable` är false som
/// standard), så iCloud-nyckelringen rör den inte.
enum KeychainSessionStore {

    /// Medvetet frikopplat från bundle-id:t, så att ett byte av det inte gör en
    /// inloggad familj utloggad.
    private static let service = "se.kidquest.session"
    private static let account = "current"

    private static var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    /// Nil när det inte finns någon post, eller när den inte gick att läsa.
    static func read() -> Data? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess else { return nil }
        return item as? Data
    }

    /// @return false om skrivningen misslyckades. Anroparen får inte tolka det som att
    ///   den lyckades -- att tro att en session är sparad när den inte är det ger en
    ///   utloggning vid nästa start utan någon förklaring.
    @discardableResult
    static func write(_ data: Data) -> Bool {
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]

        let updateStatus = SecItemUpdate(baseQuery as CFDictionary, attributes as CFDictionary)
        if updateStatus == errSecSuccess { return true }
        guard updateStatus == errSecItemNotFound else { return false }

        var insert = baseQuery
        insert.merge(attributes) { _, new in new }
        return SecItemAdd(insert as CFDictionary, nil) == errSecSuccess
    }

    static func delete() {
        SecItemDelete(baseQuery as CFDictionary)
    }
}

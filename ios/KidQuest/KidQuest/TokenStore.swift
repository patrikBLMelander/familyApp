import Foundation

/// Vem som är inloggad på den här enheten.
///
/// Rollen sparas tillsammans med token eftersom uppstarten måste routa på den: en
/// device-token säger i sig inte om den tillhör en förälder eller ett barn, och att
/// skicka ett barn till föräldravyn ger dem uppgiftshantering och familjens plånbok.
/// Att spara den lokalt gör dessutom att uppstarten slipper ett nätverksanrop.
struct Session: Equatable {
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

/// iOS-version av Androids TokenStore: håller sessionen i UserDefaults.
final class TokenStoreIOS {
    static let shared = TokenStoreIOS()

    private let defaults = UserDefaults.standard
    private let tokenKey = "kidquest_device_token"
    private let memberIdKey = "kidquest_member_id"
    private let memberNameKey = "kidquest_member_name"
    private let memberRoleKey = "kidquest_member_role"
    private let familyIdKey = "kidquest_family_id"

    private init() {}

    private(set) var current: Session?

    func load() {
        guard let token = defaults.string(forKey: tokenKey) else {
            current = nil
            return
        }
        current = Session(
            deviceToken: token,
            memberId: defaults.string(forKey: memberIdKey),
            memberName: defaults.string(forKey: memberNameKey),
            role: defaults.string(forKey: memberRoleKey),
            familyId: defaults.string(forKey: familyIdKey)
        )
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
        defaults.set(deviceToken, forKey: tokenKey)
        write(memberId, forKey: memberIdKey)
        write(memberName, forKey: memberNameKey)
        write(role, forKey: memberRoleKey)
        write(familyId, forKey: familyIdKey)

        current = Session(
            deviceToken: deviceToken,
            memberId: memberId,
            memberName: memberName,
            role: role,
            familyId: familyId
        )
    }

    /// Finns kvar för anropsställen som bara har en token; slänger samtidigt gammal identitet.
    func setToken(_ token: String) {
        setSession(deviceToken: token, memberId: nil, memberName: nil, role: nil, familyId: nil)
    }

    func clearToken() {
        for key in [tokenKey, memberIdKey, memberNameKey, memberRoleKey, familyIdKey] {
            defaults.removeObject(forKey: key)
        }
        current = nil
    }

    private func write(_ value: String?, forKey key: String) {
        if let value {
            defaults.set(value, forKey: key)
        } else {
            defaults.removeObject(forKey: key)
        }
    }
}

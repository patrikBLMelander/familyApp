import Foundation

/// Enkel iOS-version av TokenStore som lagrar device-token i UserDefaults.
final class TokenStoreIOS {
    static let shared = TokenStoreIOS()

    private let defaults = UserDefaults.standard
    private let key = "kidquest_device_token"

    private init() {}

    private(set) var currentToken: String? {
        didSet {
            if let token = currentToken {
                defaults.set(token, forKey: key)
            } else {
                defaults.removeObject(forKey: key)
            }
        }
    }

    func load() {
        currentToken = defaults.string(forKey: key)
    }

    func getToken() -> String? {
        currentToken
    }

    func setToken(_ token: String) {
        currentToken = token
    }

    func clearToken() {
        currentToken = nil
    }
}


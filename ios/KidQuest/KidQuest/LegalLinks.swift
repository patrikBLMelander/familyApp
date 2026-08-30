import Foundation

/// De två dokument en prenumerationsapp måste länka till.
///
/// Both live in the web app rather than being duplicated per platform, so there is one
/// version of the text to keep current -- and so a correction does not need a new
/// release on two stores. Same two URLs as Android's `LegalLinks`, deliberately.
enum LegalLinks {

    private static let webBase = "https://familyapp-frontend-production.up.railway.app"

    /// Optional rather than force-unwrapped: a typo in the base string should make a
    /// link disappear, not crash the paywall a parent is standing on.
    static let privacy = URL(string: "\(webBase)/privacy")
    static let terms = URL(string: "\(webBase)/villkor")
}

import Foundation

/// Familjens betalningsstatus, så som servern ser den.
///
/// `entitled` is the ONLY field to gate on. Everything else describes what to *say* --
/// the server enforces the same answer on every write regardless, so an app that got
/// this wrong could show the wrong banner but never hand out access it should not.
///
/// Ported field for field from Android's `SubscriptionStatusResponse`, so the two apps
/// cannot end up disagreeing about what the same JSON means.
struct SubscriptionStatusDTO: Decodable, Equatable {
    /// TRIAL, ACTIVE, GRACE, CANCELED, EXPIRED eller COMPED.
    let status: String
    /// Det enda fältet som avgör åtkomst.
    let entitled: Bool
    let trialEndsAt: String?
    let trialDaysRemaining: Int
    let inTrial: Bool
    let currentPeriodEnd: String?
    let platform: String?
    let cancelAtPeriodEnd: Bool
    /// Gratis åtkomst som getts för hand. Tjata aldrig på en compad familj.
    let comped: Bool

    private enum CodingKeys: String, CodingKey {
        case status, entitled, trialEndsAt, trialDaysRemaining, inTrial
        case currentPeriodEnd, platform, cancelAtPeriodEnd, comped
    }

    /// Decoded field by field with defaults rather than by the synthesised initialiser.
    /// A backend that later adds a status value or stops sending a field it considers
    /// obsolete would otherwise fail the whole decode -- and the caller's fallback for
    /// "no status" is to show nothing at all, which would silently remove the trial
    /// banner from every phone at once. Missing fields degrade to the safe reading:
    /// not entitled, not in a trial, not comped.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        status = try container.decodeIfPresent(String.self, forKey: .status) ?? "UNKNOWN"
        entitled = try container.decodeIfPresent(Bool.self, forKey: .entitled) ?? false
        trialEndsAt = try container.decodeIfPresent(String.self, forKey: .trialEndsAt)
        trialDaysRemaining = try container.decodeIfPresent(Int.self, forKey: .trialDaysRemaining) ?? 0
        inTrial = try container.decodeIfPresent(Bool.self, forKey: .inTrial) ?? false
        currentPeriodEnd = try container.decodeIfPresent(String.self, forKey: .currentPeriodEnd)
        platform = try container.decodeIfPresent(String.self, forKey: .platform)
        cancelAtPeriodEnd = try container.decodeIfPresent(Bool.self, forKey: .cancelAtPeriodEnd) ?? false
        comped = try container.decodeIfPresent(Bool.self, forKey: .comped) ?? false
    }

    /// Memberwise initialiser for fixtures and tests. Kept outside `#if DEBUG` so the
    /// type has the same shape in both configurations.
    init(
        status: String,
        entitled: Bool,
        trialEndsAt: String? = nil,
        trialDaysRemaining: Int = 0,
        inTrial: Bool = false,
        currentPeriodEnd: String? = nil,
        platform: String? = "IOS",
        cancelAtPeriodEnd: Bool = false,
        comped: Bool = false
    ) {
        self.status = status
        self.entitled = entitled
        self.trialEndsAt = trialEndsAt
        self.trialDaysRemaining = trialDaysRemaining
        self.inTrial = inTrial
        self.currentPeriodEnd = currentPeriodEnd
        self.platform = platform
        self.cancelAtPeriodEnd = cancelAtPeriodEnd
        self.comped = comped
    }
}

/// Hämtar familjens betalningsstatus. Serverdelen av prenumerationen -- inga köp här.
///
/// Deliberately server-facing only. iOS has no StoreKit or RevenueCat integration yet,
/// and the server is what decides entitlement in any case: the eight parent-administration
/// endpoints answer 402 when a family is no longer entitled, and nothing a child does is
/// ever refused. This call exists to know what to *say*, not what to allow.
enum SubscriptionService {

    static func fetchStatus() async throws -> SubscriptionStatusDTO {
        try await ApiClient.shared.send(
            SubscriptionStatusDTO.self,
            path: "subscription/status",
            method: "GET"
        )
    }

    /// Samma anrop, men sväljer felet.
    ///
    /// For the banner: a billing strip must never be the reason a parent's dashboard
    /// shows an error. No status means no banner, which is the same thing a comfortable
    /// family sees anyway.
    static func fetchStatusOrNil() async -> SubscriptionStatusDTO? {
        try? await fetchStatus()
    }
}

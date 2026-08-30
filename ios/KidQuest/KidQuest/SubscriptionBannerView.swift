import SwiftUI

/// Hur nära provperiodens slut innan dashboarden nämner den.
///
/// Same 30 days as Android's `TRIAL_NAG_DAYS`. Shared number, so a family that opens
/// the app on two phones is told the same thing on both.
private let trialNagDays = 30

/// Säger något om betalningen bara när det finns något värt att säga.
///
/// The dashboard was deliberately built around a single dominant element, so a permanent
/// billing strip would undo that work. A family comfortably inside their trial, one that
/// is paying, and one that has been comped all see nothing at all.
///
/// Nothing here decides access. The server does that, and it applies the same answer to
/// every write regardless of what this banner happens to show -- `entitled` is the only
/// field anything is ever gated on, and this view does not gate anything.
struct SubscriptionBannerView: View {
    let status: SubscriptionStatusDTO
    /// Nil hides the chevron and makes the row inert. Android gates the paywall entry
    /// on whether billing is configured at all -- no key, no paywall, no dead tap -- and
    /// iOS has no store integration yet, so the same reasoning applies here.
    var onTap: (() -> Void)?

    @Environment(\.seasonPalette) private var palette

    var body: some View {
        if let message = Self.message(for: status) {
            row(message)
        }
    }

    @ViewBuilder
    private func row(_ message: Message) -> some View {
        let tint = message.urgent ? palette.danger : palette.warnStrong
        let content = HStack(alignment: .top, spacing: 9) {
            Image(systemName: message.urgent ? "exclamationmark.circle" : "clock")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(tint)
                .padding(.top, 1)

            VStack(alignment: .leading, spacing: 2) {
                Text(message.headline)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(message.urgent ? palette.danger : palette.warnInk)
                Text(message.detail)
                    .font(.footnote)
                    .foregroundStyle(palette.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if onTap != nil {
                Image(systemName: "chevron.right")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(tint)
                    .padding(.top, 2)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.warnBg, in: RoundedRectangle(cornerRadius: 12))

        if let onTap {
            Button(action: onTap) { content }
                .buttonStyle(.plain)
                .accessibilityHint("Öppnar prenumerationen")
        } else {
            content
        }
    }

    // MARK: - What to say, if anything

    private struct Message {
        let headline: String
        let detail: String
        let urgent: Bool
    }

    /// Returnerar nil när bannern inte ska visas alls.
    ///
    /// Static and pure so the rule can be read in one place, and so a future test can
    /// check it without building a view.
    private static func message(for status: SubscriptionStatusDTO) -> Message? {
        // A comped family is never nagged, whatever the trial clock says -- they were
        // given free access on purpose.
        if status.comped { return nil }

        switch status.status {
        case "EXPIRED":
            return Message(
                headline: "Provperioden har gått ut",
                detail: "Förnya för att lägga till sysslor och familjemedlemmar igen. "
                    + "Barnens sysslor och djur fungerar som vanligt.",
                urgent: true
            )
        case "GRACE":
            return Message(
                headline: "Betalningen gick inte igenom",
                // Android says "Google försöker igen" here. The retry on this platform is
                // the App Store's, so the sentence names the store the parent would
                // actually go to.
                detail: "App Store försöker igen. Appen fungerar som vanligt under tiden.",
                urgent: false
            )
        default:
            guard status.inTrial, status.trialDaysRemaining <= trialNagDays else { return nil }
            let headline: String
            switch status.trialDaysRemaining {
            case ...0: headline = "Provperioden slutar idag"
            case 1: headline = "1 dag kvar av provperioden"
            default: headline = "\(status.trialDaysRemaining) dagar kvar av provperioden"
            }
            return Message(
                headline: headline,
                detail: "Sedan kostar KidQuest 29 kr per månad för hela familjen.",
                urgent: false
            )
        }
    }
}

// MARK: - Self-loading wrapper

/// Bannern som hämtar sin egen status.
///
/// One line for the dashboard to adopt, rather than a status property threaded through
/// a screen that has nothing else to do with billing. It occupies no height until the
/// call comes back, and none at all if the call fails or there is nothing to say, so
/// dropping it into a layout cannot move anything for a family that is paying.
struct SubscriptionBannerLoader: View {
    var onTap: (() -> Void)?
    /// Non-nil renders this instead of calling the network. Only the fixtures set it.
    var preloaded: SubscriptionStatusDTO?

    @State private var status: SubscriptionStatusDTO?

    var body: some View {
        Group {
            if let status {
                SubscriptionBannerView(status: status, onTap: onTap)
            }
        }
        .task {
            guard preloaded == nil else {
                status = preloaded
                return
            }
            status = await SubscriptionService.fetchStatusOrNil()
        }
    }
}

// MARK: - Fixture

#if DEBUG
extension SubscriptionBannerView {

    /// De tillstånd bannern kan hamna i, som inget riktigt konto kan nå på beställning.
    ///
    /// Reaching EXPIRED for real means waiting out a trial on a live family, and GRACE
    /// means making a real card fail. The simulator takes no touch input either, so a
    /// state that cannot be reached cannot be looked at at all without this.
    enum FixtureState: String, CaseIterable {
        /// Well inside the trial but within the nag window: the banner does show.
        case comfortableTrial
        /// One day left. The wording changes at 1 and at 0, so both are worth seeing.
        case nearlyExpiredTrial
        case lastDayOfTrial
        case grace
        case expired
        /// A comped family. Renders nothing at all, which is the point of including it.
        case comped
        /// A paying family, well outside any trial. Also renders nothing.
        case active

        var status: SubscriptionStatusDTO {
            switch self {
            case .comfortableTrial:
                return SubscriptionStatusDTO(
                    status: "TRIAL", entitled: true, trialDaysRemaining: 12, inTrial: true
                )
            case .nearlyExpiredTrial:
                return SubscriptionStatusDTO(
                    status: "TRIAL", entitled: true, trialDaysRemaining: 1, inTrial: true
                )
            case .lastDayOfTrial:
                return SubscriptionStatusDTO(
                    status: "TRIAL", entitled: true, trialDaysRemaining: 0, inTrial: true
                )
            case .grace:
                return SubscriptionStatusDTO(status: "GRACE", entitled: true)
            case .expired:
                return SubscriptionStatusDTO(status: "EXPIRED", entitled: false)
            case .comped:
                return SubscriptionStatusDTO(status: "COMPED", entitled: true, comped: true)
            case .active:
                return SubscriptionStatusDTO(status: "ACTIVE", entitled: true)
            }
        }
    }

    static func fixture(_ state: FixtureState = .comfortableTrial) -> SubscriptionBannerView {
        SubscriptionBannerView(status: state.status, onTap: {})
    }
}

/// Alla tillstånd på en skärm, i den ordning en familj möter dem.
///
/// One photograph rather than seven launches, and it is the only way to see that the
/// comped and active rows really do take no space.
struct SubscriptionBannerGallery: View {
    @Environment(\.seasonPalette) private var palette

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                ForEach(SubscriptionBannerView.FixtureState.allCases, id: \.self) { state in
                    Text(state.rawValue)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(palette.inkFaint)
                    SubscriptionBannerView.fixture(state)
                }
            }
            .padding(16)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.pageBg.ignoresSafeArea())
    }
}

#Preview("Prenumerationsbanner") {
    SubscriptionBannerGallery()
        .environment(\.seasonPalette, SeasonTheme.current(dark: false))
}

#Preview("Prenumerationsbanner mörk") {
    SubscriptionBannerGallery()
        .environment(\.seasonPalette, SeasonTheme.current(dark: true))
        .preferredColorScheme(.dark)
}
#endif

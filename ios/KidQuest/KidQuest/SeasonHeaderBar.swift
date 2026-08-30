import SwiftUI

/// The band across the top of every screen that is not the dashboard: back, a title,
/// and one line of context under it.
///
/// A port of Android's SeasonHeaderBar, and shared here for the reason it was made
/// shared there: the family's list and one child's list are the same screen at two
/// distances, and a bespoke header row per screen is exactly how those two drifted
/// apart before. New screens take this rather than rebuilding the row.
///
/// It carries the same gradient as the dashboard's band, so the season is the first
/// thing on screen wherever you are. There is no collapse-on-scroll here: the
/// dashboard's band is 196pt of colour worth reclaiming, this is a title bar.
struct SeasonHeaderBar<Actions: View>: View {
    @Environment(\.seasonPalette) private var palette

    private let title: String
    private let subtitle: String?
    private let onBack: (() -> Void)?
    private let actions: Actions

    init(
        title: String,
        subtitle: String? = nil,
        onBack: (() -> Void)? = nil,
        @ViewBuilder actions: () -> Actions
    ) {
        self.title = title
        self.subtitle = subtitle
        self.onBack = onBack
        self.actions = actions()
    }

    var body: some View {
        HStack(spacing: 0) {
            if let onBack {
                Button(action: onBack) {
                    // A chevron, not Android's arrow: this is the glyph iOS uses for
                    // back, and it is what a thumb on the left edge is looking for.
                    Image(systemName: "chevron.backward")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Tillbaka")
            } else {
                Spacer().frame(width: 16)
            }

            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .truncationMode(.tail)

                if let subtitle, !subtitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(Color.white.opacity(0.82))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            actions
        }
        .padding(.trailing, 8)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .background(
            // Through the safe area rather than up to it, so the status bar sits on the
            // band instead of on whatever colour happens to be behind it.
            LinearGradient(
                colors: [palette.headerTop, palette.headerBottom],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea(edges: .top)
        )
    }
}

extension SeasonHeaderBar where Actions == EmptyView {
    /// The common case: a bar with nothing on its trailing side.
    init(title: String, subtitle: String? = nil, onBack: (() -> Void)? = nil) {
        self.init(title: title, subtitle: subtitle, onBack: onBack) { EmptyView() }
    }
}

import SwiftUI

/// Which half of a chore screen is showing: the day, or the week it recurs over.
///
/// Shared rather than nested in one screen, because both chore screens have exactly
/// these two tabs and one of them is reached from the other.
enum ChoreTab: Hashable {
    case today
    case week
}

/// The Idag/Vecka pair that sits under the header bar on both chore screens.
///
/// One implementation on purpose. The child's list and the family's list sit next to
/// each other in the app, and a control copied into each is how those two would drift
/// apart a fix at a time.
struct ChoreTabPicker: View {
    @Environment(\.seasonPalette) private var palette

    let selected: ChoreTab
    let onSelect: (ChoreTab) -> Void

    var body: some View {
        HStack(spacing: 8) {
            button(.today, title: "Idag", icon: "checklist")
            button(.week, title: "Vecka", icon: "calendar")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    private func button(_ value: ChoreTab, title: String, icon: String) -> some View {
        let isSelected = selected == value
        return Button {
            onSelect(value)
        } label: {
            HStack(spacing: 6) {
                // Android puts 📝 and 📅 in these labels. An emoji is a piece of
                // someone else's type design dropped into a control: it keeps its own
                // colour, ignores the weight of the text beside it and cannot be
                // tinted when the tab is selected. An SF Symbol is the same picture
                // drawn in the app's own ink.
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .semibold))
                Text(title)
                    .font(.subheadline.weight(.semibold))
            }
            .frame(maxWidth: .infinity)
            .frame(height: 40)
            // Two roles a single accent cannot fill: the chosen tab is the accent, the
            // other is the accent's tinted pair.
            .foregroundStyle(isSelected ? palette.onAccent : palette.calInk)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(isSelected ? palette.accent : palette.calBg)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : [.isButton])
    }
}

import SwiftUI
import UIKit

/// A pet drawn as transparent standalone art over the current seasonal background.
///
/// This is the single place that composes the two layers, so the dashboard and the pet
/// screen cannot drift apart. Mirrors the Android PetVisual and the web layering: the
/// background is cropped to fill, the pet is fitted inside it.
///
/// - Parameters:
///   - scale: fraction of the box the pet occupies. Defaults to the per-stage
///     correction in `PetImagesIOS.petScale(for:growthStage:)`.
///   - alignment: where the pet sits once scaled. `.bottom` reads as standing on the
///     ground, which suits a landscape background; `.center` keeps it mid-frame.
struct PetVisual: View {
    let petType: String?
    let growthStage: Int
    var season: String = PetImagesIOS.currentSeason()
    var cornerRadius: CGFloat = 16
    var scale: CGFloat? = nil
    var alignment: Alignment = .center

    var body: some View {
        let effectiveScale = min(
            max(scale ?? PetImagesIOS.petScale(for: petType, growthStage: growthStage), 0.1),
            1.0
        )

        GeometryReader { geo in
            ZStack(alignment: alignment) {
                if let backgroundName = PetImagesIOS.seasonalBackgroundName(season),
                   let background = UIImage(named: backgroundName) {
                    Image(uiImage: background)
                        .resizable()
                        .scaledToFill()
                        .frame(width: geo.size.width, height: geo.size.height)
                        .clipped()
                }

                if let petName = PetImagesIOS.petImageName(for: petType, growthStage: growthStage),
                   let pet = UIImage(named: petName) {
                    Image(uiImage: pet)
                        .resizable()
                        .scaledToFit()
                        .frame(
                            width: geo.size.width * effectiveScale,
                            height: geo.size.height * effectiveScale
                        )
                        .padding(4)
                } else {
                    Text("🐾")
                        .font(.system(size: 80))
                }
            }
            .frame(width: geo.size.width, height: geo.size.height, alignment: alignment)
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
    }
}

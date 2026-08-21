import SwiftUI
import UIKit

enum PetNameUtilsIOS {
    private static let names: [String: String] = [
        "dragon": "Drake",
        "cat": "Katt",
        "dog": "Hund",
        "bird": "Fågel",
        "rabbit": "Kanin",
        "bear": "Björn",
        "snake": "Orm",
        "panda": "Panda",
        "slot": "Sengångare",
        "hydra": "Hydra",
        "unicorn": "Enhörning",
        "kapybara": "Kapybara",
        "shark": "Haj",
        "lion": "Lejon",
    ]

    static func getPetNameSwedish(_ petType: String?) -> String {
        guard let key = petType?.lowercased() else { return "Djur" }
        return names[key] ?? petType ?? "Djur"
    }
}

enum PetFoodUtilsIOS {
    private static let emojis: [String: String] = [
        "dragon": "🔥",
        "cat": "🐟",
        "dog": "🦴",
        "bird": "🌾",
        "rabbit": "🥕",
        "bear": "🍯",
        "snake": "🥚",
        "panda": "🎋",
        "slot": "🍃",
        "hydra": "💧",
        "unicorn": "✨",
        "kapybara": "🌿",
        "shark": "🐠",
        "lion": "🥩",
    ]

    private static let foodNames: [String: String] = [
        "dragon": "eldbär",
        "cat": "fisk",
        "dog": "ben",
        "bird": "frön",
        "rabbit": "morötter",
        "bear": "honung",
        "snake": "ägg",
        "panda": "bambu",
        "slot": "löv",
        "hydra": "vattendroppar",
        "unicorn": "stjärnfrukter",
        "kapybara": "gräs",
        "shark": "fiskar",
        "lion": "kött",
    ]

    static func emoji(for petType: String?) -> String {
        emojis[petType?.lowercased() ?? ""] ?? "🍎"
    }

    static func name(for petType: String?) -> String {
        foodNames[petType?.lowercased() ?? ""] ?? "mat"
    }
}

/// Resolves pet artwork by asset name rather than a hand-written switch per species.
///
/// Adding a species means dropping `<species>_stage1..5` and `<species>_egg_stage1..5`
/// into Assets.xcassets and adding one line to `eggToPet`.
///
/// The integrated variant (pet with a background baked in) is gone. Pets are drawn as
/// transparent standalone art over a seasonal background, matching the web app since
/// d190a65 and the Android app's PetImages — see PetVisual.
enum PetImagesIOS {

    private static let minStage = 1
    private static let maxStage = 5

    /// Mirrors EGG_TO_PET_MAP in the backend's PetService.
    private static let eggToPet: [String: String] = [
        "blue_egg": "dragon",
        "green_egg": "cat",
        "red_egg": "dog",
        "yellow_egg": "bird",
        "purple_egg": "rabbit",
        "orange_egg": "bear",
        "brown_egg": "snake",
        "black_egg": "panda",
        "gray_egg": "slot",
        "teal_egg": "hydra",
        "pink_egg": "unicorn",
        "cyan_egg": "kapybara",
        "white_egg": "shark",
        "golden_egg": "lion",
    ]

    /// "dragon" for "blue_egg". Also accepts a petType directly, since the API returns both.
    static func petType(forEgg eggType: String?) -> String? {
        guard let key = eggType?.lowercased() else { return nil }
        if let mapped = eggToPet[key] { return mapped }
        return eggToPet.values.contains(key) ? key : nil
    }

    static func eggImageName(for eggType: String?, stage: Int = 1) -> String? {
        guard let species = petType(forEgg: eggType) else { return nil }
        let safeStage = min(max(stage, minStage), maxStage)
        let name = "\(species)_egg_stage\(safeStage)"
        return UIImage(named: name) != nil ? name : nil
    }

    /// Standalone transparent pet art.
    ///
    /// Falls back to the nearest lower stage when one is missing, so a gap in the art
    /// shows the previous stage instead of an empty screen. unicorn_stage5 relies on
    /// this today — it does not exist in the source art.
    static func petImageName(for petType: String?, growthStage: Int) -> String? {
        guard let species = petType?.lowercased() else { return nil }
        let requested = min(max(growthStage, minStage), maxStage)
        for stage in stride(from: requested, through: minStage, by: -1) {
            let name = "\(species)_stage\(stage)"
            if UIImage(named: name) != nil { return name }
        }
        return nil
    }

    static func petImageName(forEgg eggType: String?, growthStage: Int) -> String? {
        petImageName(for: petType(forEgg: eggType), growthStage: growthStage)
    }

    /// Mar–May spring, Jun–Aug summer, Sep–Nov autumn, otherwise winter — as on web.
    static func currentSeason() -> String {
        switch Calendar.current.component(.month, from: Date()) {
        case 3...5: return "spring"
        case 6...8: return "summer"
        case 9...11: return "autumn"
        default: return "winter"
        }
    }

    static func seasonalBackgroundName(_ season: String = currentSeason()) -> String? {
        let name = "season_\(season)"
        return UIImage(named: name) != nil ? name : nil
    }

    /// Size correction per pet, as a fraction of the box the pet is drawn in.
    ///
    /// The art is not cropped consistently: measured across the standalone set, the
    /// animal occupies 47% to 100% of its canvas height, and that fraction varies
    /// between stages of the same species. Because the canvas is scaled to fit rather
    /// than the animal, an edge-to-edge crop renders roughly twice the height of a
    /// sibling drawn with margin — so a pet appears to balloon when it levels up.
    ///
    /// Keys are checked most specific first: "<species>_stage<n>", then "<species>".
    /// Kept in step with PET_SCALE_OVERRIDES in the Android PetVisual.kt.
    private static let petScaleOverrides: [String: CGFloat] = [
        "snake_stage2": 0.57,
        "slot_stage2": 0.63,
        "hydra_stage1": 0.57,
        "dragon_stage2": 0.80,
        "shark": 0.80,

        // Same defect, measured but not yet seen in the app — uncomment when it
        // bothers you. Higher stages show up rarely, which is why they surface late.
        // "hydra_stage5": 0.57,
        // "unicorn_stage4": 0.61,
        // "kapybara_stage3": 0.75,
        // "snake_stage5": 0.78,
    ]

    static let defaultPetScale: CGFloat = 1.0

    static func petScale(for petType: String?, growthStage: Int) -> CGFloat {
        guard let species = petType?.lowercased() else { return defaultPetScale }
        let stage = min(max(growthStage, minStage), maxStage)
        return petScaleOverrides["\(species)_stage\(stage)"]
            ?? petScaleOverrides[species]
            ?? defaultPetScale
    }
}

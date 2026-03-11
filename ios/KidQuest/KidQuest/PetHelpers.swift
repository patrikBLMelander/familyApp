import SwiftUI

enum PetNameUtilsIOS {
    static func getPetNameSwedish(_ petType: String?) -> String {
        switch petType?.lowercased() {
        case "dragon": return "Drake"
        case "cat": return "Katt"
        case "dog": return "Hund"
        case "bird": return "Fågel"
        case "rabbit": return "Kanin"
        case "bear": return "Björn"
        case "snake": return "Orm"
        case "panda": return "Panda"
        case "slot": return "Sengångare"
        case "hydra": return "Hydra"
        case "unicorn": return "Enhörning"
        case "kapybara": return "Kapybara"
        default: return petType ?? "Djur"
        }
    }
}

enum PetFoodUtilsIOS {
    static func emoji(for petType: String?) -> String {
        switch petType?.lowercased() {
        case "dragon": return "🔥"
        case "cat": return "🐟"
        case "dog": return "🦴"
        case "bird": return "🌾"
        case "rabbit": return "🥕"
        case "bear": return "🍯"
        case "snake": return "🥚"
        case "panda": return "🎋"
        case "slot": return "🍃"
        case "hydra": return "💧"
        case "unicorn": return "✨"
        case "kapybara": return "🌿"
        default: return "🍎"
        }
    }

    static func name(for petType: String?) -> String {
        switch petType?.lowercased() {
        case "dragon": return "eldbär"
        case "cat": return "fisk"
        case "dog": return "ben"
        case "bird": return "frön"
        case "rabbit": return "morötter"
        case "bear": return "honung"
        case "snake": return "ägg"
        case "panda": return "bambu"
        case "slot": return "löv"
        case "hydra": return "vattendroppar"
        case "unicorn": return "stjärnfrukter"
        case "kapybara": return "gräs"
        default: return "mat"
        }
    }
}

enum PetImagesIOS {
    private static func eggToPetType(_ key: String) -> String? {
        switch key {
        case "blue_egg": return "dragon"
        case "green_egg": return "cat"
        case "red_egg": return "dog"
        case "yellow_egg": return "bird"
        case "purple_egg": return "rabbit"
        case "orange_egg": return "bear"
        case "brown_egg": return "snake"
        case "black_egg": return "panda"
        case "gray_egg": return "slot"
        case "teal_egg": return "hydra"
        case "pink_egg": return "unicorn"
        case "cyan_egg": return "kapybara"
        // Fallbacks
        case "dragon","cat","dog","bird","rabbit","bear","snake","panda","slot","hydra","unicorn","kapybara":
            return key
        default:
            return nil
        }
    }

    static func eggImageName(for eggType: String?, stage: Int = 1) -> String? {
        guard let key = eggType?.lowercased(), let petType = eggToPetType(key) else { return nil }
        let s = max(1, min(5, stage))
        switch petType {
        case "dragon": return "dragon_egg_stage\(s)"
        case "cat": return "cat_egg_stage\(s)"
        case "dog": return "dog_egg_stage\(s)"
        case "bird": return "bird_egg_stage\(s)"
        case "rabbit": return "rabbit_egg_stage\(s)"
        case "bear": return "bear_egg_stage\(s)"
        case "snake": return "snake_egg_stage\(s)"
        case "panda": return "panda_egg_stage\(s)"
        case "slot": return "slot_egg_stage\(s)"
        case "hydra": return "hydra_egg_stage\(s)"
        case "unicorn": return "unicorn_egg_stage\(s)"
        case "kapybara": return "kapybara_egg_stage\(s)"
        default: return nil
        }
    }

    static func integratedImageName(for petType: String?, growthStage: Int) -> String? {
        let stage = max(1, min(5, growthStage))
        guard let key = petType?.lowercased() else { return nil }

        let base: String
        switch key {
        case "dragon": base = "dragon_integrated_stage"
        case "cat": base = "cat_integrated_stage"
        case "dog": base = "dog_integrated_stage"
        case "bird": base = "bird_integrated_stage"
        case "rabbit": base = "rabbit_integrated_stage"
        case "bear": base = "bear_integrated_stage"
        case "snake": base = "snake_integrated_stage"
        case "panda": base = "panda_integrated_stage"
        case "slot": base = "slot_integrated_stage"
        case "hydra": base = "hydra_integrated_stage"
        case "unicorn": base = "unicorn_integrated_stage"
        case "kapybara": base = "kapybara_integrated_stage"
        default: return nil
        }
        return "\(base)\(stage)"
    }
}


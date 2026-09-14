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
        "koala": "Koala",
        "meerkat": "Surikat",
        "penguin": "Pingvin",
        "spider": "Spindel",
        "kangaroo": "Känguru",
        "scorpion": "Skorpion",
        "octopus": "Bläckfisk",
        "snowleopard": "Snöleopard",
        "tiger": "Tiger",
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
        "koala": "🍃",
        "meerkat": "🦗",
        "penguin": "🐟",
        "spider": "🪰",
        "kangaroo": "🌿",
        "scorpion": "🦗",
        "octopus": "🦐",
        "snowleopard": "🥩",
        "tiger": "🍖",
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
        "koala": "löv",
        "meerkat": "insekter",
        "penguin": "fisk",
        "spider": "flugor",
        "kangaroo": "gräs",
        "scorpion": "syrsor",
        "octopus": "räkor",
        "snowleopard": "kött",
        "tiger": "kött",
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
        "silver_egg": "koala",
        "sand_egg": "meerkat",
        "ice_egg": "penguin",
        "amber_egg": "spider",
        "clay_egg": "kangaroo",
        "ember_egg": "scorpion",
        "indigo_egg": "octopus",
        "frost_egg": "snowleopard",
        "tiger_egg": "tiger",
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

    // MARK: - Adventures + rarity art (nil when the asset is not in the catalog yet)

    /// A frame's loot_item id is also its asset name (frame_forest, …).
    static func frameImageName(_ frameId: String?) -> String? {
        guard let frameId, UIImage(named: frameId) != nil else { return nil }
        return frameId
    }

    static func sceneImageName(_ key: String) -> String? {
        let name = "scene_\(key)"
        return UIImage(named: name) != nil ? name : nil
    }

    /// A scene decoration's loot_item id is also its asset name (item_kite, …).
    static func sceneItemImageName(_ itemId: String?) -> String? {
        guard let itemId, UIImage(named: itemId) != nil else { return nil }
        return itemId
    }

    static func chestStageImageName(_ stage: Int) -> String? {
        let name = "chest_stage\(min(max(stage, minStage), maxStage))"
        return UIImage(named: name) != nil ? name : nil
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

/// Per-species colour, mirroring the Android PetTheme and frontend petTheme.ts.
///
/// `accent` is the reason this is a table rather than just a pair of gradient stops.
/// Neither end of a species gradient is reliably usable on its own: dragon's pale end
/// is nearly black, unicorn's saturated end is a pastel pink. `accent` is the member
/// picked to stay visible as a 4pt stroke or a 1.5pt border on a light card, which is
/// what the parent dashboard draws its progress rings with. It is deliberately NOT a
/// text colour — several accents sit near 2.5:1 against cream.
///
/// The same values are still written out a second time inside ChildDashboardView and
/// ChildWalletView, which is how shark and lion came to be missing from both. Those
/// two should come here; this table already has all fourteen.
enum PetThemeIOS {

    struct Palette {
        /// Pale end of the species gradient — the top of the child's own screen.
        let from: Color
        /// Saturated end.
        let to: Color
        /// Legible as a stroke or a border on a light card. Not for text.
        let accent: Color
    }

    private static let palettes: [String: Palette] = [
        "dragon": palette(0x4C1D95, 0x1E293B, 0x4C1D95),
        "cat": palette(0xFDE68A, 0xF97316, 0xF97316),
        "dog": palette(0xBBF7D0, 0x22C55E, 0x16A34A),
        "bird": palette(0xBFDBFE, 0x2563EB, 0x2563EB),
        "rabbit": palette(0xFCE7F3, 0xEC4899, 0xDB2777),
        "bear": palette(0xFEF3C7, 0x92400E, 0x92400E),
        "snake": palette(0xDCFCE7, 0x15803D, 0x15803D),
        "panda": palette(0xE5E7EB, 0x111827, 0x111827),
        "slot": palette(0xE5E7EB, 0x6B7280, 0x4B5563),
        "hydra": palette(0xC4B5FD, 0x4C1D95, 0x4C1D95),
        // Both ends of unicorn are pastel, so the accent steps down the same hue ramp
        // to a strength that still reads as a ring.
        "unicorn": palette(0xFDE68A, 0xF9A8D4, 0xDB2777),
        "kapybara": palette(0xDCFCE7, 0x22C55E, 0x16A34A),
        "shark": palette(0xBAE6FD, 0x0369A1, 0x0369A1),
        "lion": palette(0xFEF3C7, 0xD97706, 0xD97706),
        "koala": palette(0xE6EBE1, 0x6B8E5A, 0x556B2F),
        "meerkat": palette(0xEAD9B8, 0xB07A3E, 0x8A5A2E),
        "penguin": palette(0xCDE3F0, 0x3F5A70, 0x2E4257),
        "spider": palette(0xF7DFC0, 0xC96A1E, 0x7A3B12),
        "kangaroo": palette(0xF6E3C8, 0xB06A3A, 0x8A4E28),
        "scorpion": palette(0xF6D9B0, 0xE0791B, 0x5A3212),
        "octopus": palette(0xDCC9F0, 0x7C5CC0, 0x3F2A78),
        "snowleopard": palette(0xE8EAEE, 0x64748B, 0x334155),
        "tiger": palette(0xFCE3B0, 0xE07B1E, 0x5A3410),
    ]

    /// For a child who has not chosen an egg yet: the app's own background pastels.
    private static let neutral = palette(0xE0E7FF, 0xE0F2FE, 0x0C4A6E)

    /// Accepts a petType or an eggType; the API hands out both.
    static func forPet(_ petType: String?) -> Palette {
        guard let species = PetImagesIOS.petType(forEgg: petType) else { return neutral }
        return palettes[species] ?? neutral
    }

    /// The sliver along the top of a card, so it reads as a piece of that child's screen.
    static func edge(_ petType: String?) -> LinearGradient {
        let p = forPet(petType)
        return LinearGradient(colors: [p.from, p.to], startPoint: .leading, endPoint: .trailing)
    }

    /// The table is written as six-digit RGB, like the web palette it comes from,
    /// while `Color(hex:)` reads ARGB. Passing these unqualified would give every
    /// animal an alpha of zero — compiles, renders nothing.
    private static func palette(_ from: UInt32, _ to: UInt32, _ accent: UInt32) -> Palette {
        Palette(
            from: Color(hex: 0xFF00_0000 | from),
            to: Color(hex: 0xFF00_0000 | to),
            accent: Color(hex: 0xFF00_0000 | accent)
        )
    }
}

/// Vad äggen viskar innan de kläcks.
///
/// Bodde i SelectEggSheet och var privat där, vilket gjorde att ingenting kunde
/// kontrollera att listan var komplett. Den var det inte: lejonet och hajen lades till på
/// servern och ledtrådarna följde aldrig med. Här ligger de bredvid artnamnen, och
/// EggNamesTests ser till att alla fjorton täcks.
///
/// Här fanns också label(), ett namn per ägg. Namnen kom från serverns identifierare,
/// som är färger, medan konsten ritas per art -- så "Rött ägg" var beige med ett blått
/// tassavtryck. Rutorna visar nu bara ägget, och ledtråden är den enda texten kvar.
enum EggNames {

    static func hint(for eggType: String) -> String {
        switch eggType.lowercased() {
        case "blue_egg": return "Jag älskar att flyga högt bland molnen."
        case "green_egg": return "Jag spinner nöjt när jag får ligga i solen."
        case "red_egg": return "Jag hämtar gärna bollen om du kastar den."
        case "yellow_egg": return "Jag kvittrar gärna när dagen börjar."
        case "purple_egg": return "Jag hoppar fram och gnager gärna på morötter."
        case "orange_egg": return "Jag tar gärna en lång vintersömn med magen full."
        case "brown_egg": return "Jag gillar att slingra mig på varma stenar."
        case "black_egg": return "Jag tycker om att smyga runt i skuggan."
        case "gray_egg": return "Jag rör mig långsamt men kramas gärna länge."
        case "teal_egg": return "Jag trivs där det finns mycket vatten och mystik."
        case "pink_egg": return "Jag gillar glitter, regnbågar och magi."
        case "cyan_egg": return "Jag älskar att plaska runt med kompisar."
        case "golden_egg": return "Jag ryter så att alla hör att jag vaknat."
        case "white_egg": return "Jag simmar snabbast av alla där det är djupt."
        case "silver_egg": return "Jag sover gärna högt uppe bland eukalyptuslöven."
        case "sand_egg": return "Jag står på bakbenen och spanar efter faror."
        case "ice_egg": return "Jag vaggar fram på isen och dyker gärna efter fisk."
        case "amber_egg": return "Jag spinner nät och fångar flugor på morgonen."
        case "clay_egg": return "Jag hoppar långt och bär min unge i fickan."
        case "ember_egg": return "Jag har en stark svans och gömmer mig gärna bland stenar."
        case "indigo_egg": return "Jag har åtta armar och gömmer mig gärna bland koraller."
        case "frost_egg": return "Jag smyger tyst över snöiga berg och gömmer mig i dimman."
        case "tiger_egg": return "Jag har ränder och smyger tyst genom den höga gräsdjungeln."
        default: return "Jag längtar efter att få träffa dig."
        }
    }
}

/// Månadens namn, för raden under ett samlat djur i äggväljaren.
func kqMonthName(_ month: Int) -> String {
    let n = ["januari", "februari", "mars", "april", "maj", "juni",
             "juli", "augusti", "september", "oktober", "november", "december"]
    guard month >= 1, month <= 12 else { return "" }
    return n[month - 1]
}

import Combine
import Foundation

/// Vilket språk appen körs på, och förälderns/barnets eget val.
///
/// iOS väljer språk när appen startar och byter inte medan den kör. Att be användaren
/// "starta om appen" höll inte: att gå till hemskärmen och tillbaka startar inte om den,
/// så valet syntes aldrig. Ett eget val pekar därför om textuppslagen direkt (se
/// `activate`) och ritar om gränssnittet via `LanguageState`.
/// Ett språk telefonen har men appen saknar (t.ex. franska) landar på engelska, eftersom
/// projektets development region är `en`.
enum AppLanguage {
    static let supported = ["sv", "en", "de", "es"]

    private static let preferenceKey = "kq.appLanguage"
    private static let appleLanguagesKey = "AppleLanguages"

    /// Språket gränssnittet faktiskt visas på just nu.
    static var current: String {
        if let activeLanguage { return activeLanguage }
        let picked = Bundle.main.preferredLocalizations.first ?? "en"
        let code = String(picked.prefix(2))
        return supported.contains(code) ? code : "en"
    }

    /// Språket vars .lproj alla textuppslag går mot, eller nil när iOS eget val gäller.
    /// Satt från start när användaren valt ett språk, och när språket byts medan appen kör.
    nonisolated(unsafe) private(set) static var activeLanguage: String?
    nonisolated(unsafe) fileprivate static var activeBundle: Bundle?

    /// Anropas en gång vid start, före första vyn: ett eget val gäller direkt, även om
    /// iOS vid starten valt ett annat språk ur `AppleLanguages`.
    static func activateAtLaunch() {
        if let preference { activate(preference) }
    }

    /// Pekar om alla textuppslag (Text, String(localized:), NSLocalizedString) till
    /// språkets .lproj. `Bundle.main` får en underklass som bara ändrar uppslaget --
    /// det enda sättet att byta språk utan omstart, eftersom iOS låser språket vid start.
    private static func activate(_ code: String) {
        installOverrideOnce
        activeLanguage = code
        activeBundle = Bundle.main.path(forResource: code, ofType: "lproj").flatMap(Bundle.init(path:))
    }

    private static let installOverrideOnce: Void = {
        object_setClass(Bundle.main, LanguageOverrideBundle.self)
    }()

    /// Telefonens eget språk, för "Följ telefonen" medan appen kör. `Locale.preferredLanguages`
    /// duger inte här: den innehåller appens egen `AppleLanguages` från starten.
    private static var deviceLanguage: String {
        let global = UserDefaults.standard.persistentDomain(forName: UserDefaults.globalDomain)?["AppleLanguages"] as? [String]
        for tag in global ?? Locale.preferredLanguages {
            let code = String(tag.prefix(2))
            if supported.contains(code) { return code }
        }
        return "en"
    }

    /// Locale för datum, veckodagar, månader och pengar -- samma språk som texten.
    static var locale: Locale {
        Locale(identifier: current)
    }

    /// Gregoriansk kalender med appens språk, måndag först som i resten av appen.
    static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = locale
        return calendar
    }

    /// Användarens eget val, eller nil för "följ telefonen".
    static var preference: String? {
        let value = UserDefaults.standard.string(forKey: preferenceKey)
        return value.flatMap { supported.contains($0) ? $0 : nil }
    }

    /// Sparar valet och byter språk direkt. `AppleLanguages` skrivs också, så att iOS egna
    /// texter (systemdialoger, tangentbord) följer med från nästa start.
    static func setPreference(_ code: String?) {
        let defaults = UserDefaults.standard
        if let code, supported.contains(code) {
            defaults.set(code, forKey: preferenceKey)
            defaults.set([code], forKey: appleLanguagesKey)
            activate(code)
        } else {
            defaults.removeObject(forKey: preferenceKey)
            defaults.removeObject(forKey: appleLanguagesKey)
            activate(deviceLanguage)
        }
        LanguageState.shared.revision += 1
    }

    /// Vid inloggning/koppling: ett språk som sparats på servern (t.ex. från en annan
    /// telefon) vinner och gäller från nästa start. Inget sparat språk rör inte det lokala valet.
    static func adoptFromServer(_ code: String?) {
        guard let code, supported.contains(code), code != preference else { return }
        setPreference(code)
    }


    /// Språket i eget namn, för väljaren.
    static func displayName(_ code: String) -> String {
        switch code {
        case "sv": return "Svenska"
        case "en": return "English"
        case "de": return "Deutsch"
        case "es": return "Español"
        default: return code
        }
    }
}

/// Byter bara textuppslaget; allt annat i Bundle.main är orört.
private final class LanguageOverrideBundle: Bundle, @unchecked Sendable {
    override func localizedString(forKey key: String, value: String?, table tableName: String?) -> String {
        guard let bundle = AppLanguage.activeBundle else {
            return super.localizedString(forKey: key, value: value, table: tableName)
        }
        return bundle.localizedString(forKey: key, value: value, table: tableName)
    }

    /// String(localized:) väljer språk härifrån i stället för via localizedString(forKey:).
    override var preferredLocalizations: [String] {
        guard let language = AppLanguage.activeLanguage else { return super.preferredLocalizations }
        return [language]
    }
}

extension String {
    /// Appens egen String(localized:), som alla anrop i modulen träffar före Foundations:
    /// Foundations version går inte via Bundle.localizedString och väljer språk ur
    /// Locale.current, som iOS låser vid start. Här skickas det valda språkets .lproj med,
    /// så att ett språkbyte slår igenom direkt. Strängkatalogens extrahering påverkas inte.
    init(localized value: String.LocalizationValue, comment: StaticString? = nil) {
        self.init(
            localized: value,
            table: nil,
            bundle: AppLanguage.activeBundle ?? .main,
            locale: AppLanguage.locale,
            comment: comment
        )
    }
}

/// Ökar när språket byts; roten lyssnar och ritar om hela gränssnittet på det nya språket.
final class LanguageState: ObservableObject {
    static let shared = LanguageState()
    @Published var revision = 0
}

// MARK: - Språkberoende hjälpare

/// Genitiv: "Ellas" / "Ella's" / "Ellas" (de) -- och bara namnet på spanska, där
/// översättningen själv säger "de Ella". Namn på s, x eller z får inget extra s.
func kqPossessive(_ name: String) -> String {
    let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
    guard let last = trimmed.lowercased().last else { return trimmed }
    switch AppLanguage.current {
    case "en":
        return last == "s" ? "\(trimmed)’" : "\(trimmed)’s"
    case "de":
        return "sßxz".contains(last) ? "\(trimmed)’" : "\(trimmed)s"
    case "es":
        return trimmed
    default:
        return "sxz".contains(last) ? trimmed : "\(trimmed)s"
    }
}

/// Månadens namn (1–12) i appens språk, fristående form: "september", "September", "septiembre".
func kqMonthName(_ month: Int) -> String {
    let symbols = AppLanguage.calendar.standaloneMonthSymbols
    guard month >= 1, month <= symbols.count else { return "" }
    return symbols[month - 1]
}

/// Veckodagar måndag först (index 0 = måndag), i appens språk.
enum KQWeekdays {
    private static func mondayFirst(_ symbols: [String]) -> [String] {
        // Foundation börjar på söndag.
        Array(symbols[1...]) + [symbols[0]]
    }

    /// "måndag", "Monday", "Montag", "lunes".
    static var full: [String] { mondayFirst(AppLanguage.calendar.standaloneWeekdaySymbols) }
    /// "mån", "Mon", "Mo.", "lun".
    static var short: [String] {
        mondayFirst(AppLanguage.calendar.shortStandaloneWeekdaySymbols).map { symbol in
            let trimmed = symbol.trimmingCharacters(in: CharacterSet(charactersIn: "."))
            return trimmed.prefix(1).uppercased() + trimmed.dropFirst()
        }
    }
    /// "M", "T", "O" ...
    static var initial: [String] { mondayFirst(AppLanguage.calendar.veryShortStandaloneWeekdaySymbols) }

    /// Veckodagen i plural/återkommande form: "måndagar", "Mondays", "montags", "los lunes".
    static func recurring(_ index: Int) -> String {
        let name = full.indices.contains(index) ? full[index] : ""
        switch AppLanguage.current {
        case "en": return name + "s"
        case "de": return name.lowercased() + "s"
        case "es":
            // lunes–viernes har samma form i plural; sábado/domingo får -s.
            return "los " + (name.hasSuffix("s") ? name : name + "s")
        default: return name + "ar"
        }
    }
}

/// Datum formaterat från en mall ("EEEEdMMMM", "dMMM") i appens språk.
func kqFormatDate(_ date: Date, template: String) -> String {
    let formatter = DateFormatter()
    formatter.locale = AppLanguage.locale
    formatter.calendar = AppLanguage.calendar
    formatter.setLocalizedDateFormatFromTemplate(template)
    return formatter.string(from: date)
}

/// Ordningstal för en dag i månaden: "1:a" / "1st" / "1." / "1".
func kqOrdinal(_ day: Int) -> String {
    switch AppLanguage.current {
    case "en":
        let suffix: String
        switch (day % 10, day % 100) {
        case (1, let t) where t != 11: suffix = "st"
        case (2, let t) where t != 12: suffix = "nd"
        case (3, let t) where t != 13: suffix = "rd"
        default: suffix = "th"
        }
        return "\(day)\(suffix)"
    case "de":
        return "\(day)."
    case "es":
        return "\(day)"
    default:
        let suffix = (day % 10 == 1 || day % 10 == 2) && day != 11 && day != 12 ? ":a" : ":e"
        return "\(day)\(suffix)"
    }
}

// MARK: - Pengar

/// Familjens valuta och hur ett belopp skrivs. Beloppen är hela enheter.
enum Money {
    static let supportedCurrencies = ["SEK", "EUR", "USD", "GBP", "NOK", "DKK", "CHF"]
    private static let currencyKey = "kq.familyCurrency"

    /// Senast kända valuta för familjen; SEK tills servern sagt något annat.
    static var currency: String {
        get { UserDefaults.standard.string(forKey: currencyKey) ?? "SEK" }
        set {
            guard supportedCurrencies.contains(newValue) else { return }
            UserDefaults.standard.set(newValue, forKey: currencyKey)
        }
    }

    /// Uppdatera från ett serversvar (nil/okänd kod ignoreras).
    static func remember(_ code: String?) {
        guard let code, supportedCurrencies.contains(code) else { return }
        currency = code
    }

    /// "120 kr", "120 €", "€120", "$120".
    static func format(_ amount: Int, currency code: String? = nil) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = AppLanguage.locale
        formatter.currencyCode = code ?? currency
        formatter.maximumFractionDigits = 0
        formatter.minimumFractionDigits = 0
        if (code ?? currency) == "SEK" || (code ?? currency) == "NOK" || (code ?? currency) == "DKK" {
            // Kronor skrivs "kr" oavsett språk; "SEK 120" känns fel för en barnplånbok.
            formatter.currencySymbol = "kr"
        }
        return formatter.string(from: NSNumber(value: amount)) ?? "\(amount)"
    }

    /// Om språket skriver symbolen före beloppet ("$50", "£50") i stället för efter ("50 kr", "50 €").
    static var symbolLeads: Bool {
        guard let first = format(1).first else { return false }
        return !first.isNumber
    }

    /// Valutans symbol ensam, för inmatningsfält ("kr", "€", "$").
    static var symbol: String {
        let code = currency
        if code == "SEK" || code == "NOK" || code == "DKK" { return "kr" }
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = AppLanguage.locale
        formatter.currencyCode = code
        return formatter.currencySymbol ?? code
    }

    /// Valutans namn i appens språk, för väljaren: "svenska kronor", "Euro".
    static func displayName(_ code: String) -> String {
        AppLanguage.locale.localizedString(forCurrencyCode: code).map { "\($0) (\(code))" } ?? code
    }
}

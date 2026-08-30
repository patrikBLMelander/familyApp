import Foundation

// MARK: - DTOs

/// The full standing arrangement for one child, as the server returns it.
///
/// `ApiModels.RecurringAllowanceResponseDTO` already carries the handful of fields the
/// dashboard's one-line chip needs. This one carries the rest -- the five level amounts
/// and `nextDueOn` -- because the screen that edits the arrangement has to round-trip
/// every field it is allowed to change, and a form that silently drops the levels on
/// save would be worse than no form.
struct RecurringAllowanceDetailDTO: Decodable, Equatable {
    let memberId: String
    /// WEEKLY, MONTHLY or LEVEL.
    let kind: String
    let amount: Int?
    /// 1 = Monday ... 7 = Sunday, the same numbering as java.time on the server.
    let weekday: Int?
    let dayOfMonth: Int?
    let level1: Int?
    let level2: Int?
    let level3: Int?
    let level4: Int?
    let level5: Int?
    let active: Bool
    /// ISO date, "2026-09-01". Kept as the string the server sent: it is a calendar
    /// day, not an instant, and turning it into a Date on arrival would drag a time
    /// zone into a value that has none.
    let nextDueOn: String?
}

struct SaveRecurringAllowanceRequestDTO: Encodable {
    let kind: String
    var amount: Int?
    var weekday: Int?
    var dayOfMonth: Int?
    var level1: Int?
    var level2: Int?
    var level3: Int?
    var level4: Int?
    var level5: Int?
}

// MARK: - Repository

/// Read, write and switch off the automatic allowance.
///
/// Parent-only on all three calls, and the server is the lock: it refuses a child on
/// read as well as on write. Hiding the row in the child's wallet is about not putting
/// the amounts in front of the person they are about.
enum RecurringAllowanceRepository {

    private static func path(_ memberId: String) -> String {
        "wallet/members/\(memberId)/recurring-allowance"
    }

    /// The arrangement, or nil when there is none.
    ///
    /// The server answers 204 with no body when nothing is set up, and decoding an
    /// empty body throws. That throw means "nothing set up" and nothing else, so it is
    /// caught here -- while an ApiError (401, 403, a server fault) is rethrown, because
    /// a screen that cannot tell "no allowance" from "you were refused" will happily
    /// offer to create one that will not save.
    static func fetch(memberId: String) async throws -> RecurringAllowanceDetailDTO? {
        do {
            return try await ApiClient.shared.send(
                RecurringAllowanceDetailDTO.self,
                path: path(memberId),
                method: "GET"
            )
        } catch is DecodingError {
            return nil
        }
    }

    static func save(
        memberId: String,
        request: SaveRecurringAllowanceRequestDTO
    ) async throws -> RecurringAllowanceDetailDTO {
        try await ApiClient.shared.send(
            RecurringAllowanceDetailDTO.self,
            path: path(memberId),
            method: "PUT",
            body: request
        )
    }

    static func disable(memberId: String) async throws {
        try await ApiClient.shared.sendWithoutResponse(path: path(memberId), method: "DELETE")
    }

    /// The child's level right now, used only to mark which row of the level table they
    /// are standing on. Never worth an error of its own, so it answers nil on failure.
    static func currentLevel(memberId: String) async -> Int? {
        let progress = try? await ApiClient.shared.send(
            XpProgressResponseDTO.self,
            path: "xp/members/\(memberId)/current",
            method: "GET"
        )
        return progress?.currentLevel
    }
}

// MARK: - Dates

/// The schedule's arithmetic, in one place because two screens read it: the wallet's
/// summary line and the editor's "next payment" preview.
enum AllowanceDates {

    /// The day of the month is capped at 28 so the date exists in February too. The
    /// server agrees, and a picker that offered the 31st would be offering a month
    /// that sometimes silently skips.
    static let maxDayOfMonth = 28

    static let swedish = Locale(identifier: "sv_SE")

    /// Gregorian rather than `Calendar.current`: the schedule is defined in ordinary
    /// calendar days on the server, and a phone set to another calendar would otherwise
    /// preview a different date than the one that will actually pay out.
    private static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = swedish
        return calendar
    }

    /// Foundation counts weekdays from Sunday, java.time from Monday. Everything the
    /// server sends and receives uses the ISO numbering, so the conversion happens
    /// here and nowhere else.
    private static func isoWeekday(of date: Date) -> Int {
        let foundationWeekday = calendar.component(.weekday, from: date)
        return (foundationWeekday + 5) % 7 + 1
    }

    // The next occurrence STRICTLY after today, never today itself -- deliberately the
    // same rule as the server's firstDueAfter. A preview that disagreed with the
    // schedule would be worse than no preview.

    static func nextWeekday(_ target: Int, after today: Date = Date()) -> Date {
        let start = calendar.startOfDay(for: today)
        for offset in 1...7 {
            guard let candidate = calendar.date(byAdding: .day, value: offset, to: start) else {
                continue
            }
            if isoWeekday(of: candidate) == target { return candidate }
        }
        return start
    }

    static func nextDayOfMonth(_ day: Int, after today: Date = Date()) -> Date {
        let start = calendar.startOfDay(for: today)
        var components = calendar.dateComponents([.year, .month], from: start)
        components.day = min(max(day, 1), maxDayOfMonth)
        guard let candidate = calendar.date(from: components) else { return start }
        if candidate > start { return candidate }
        return calendar.date(byAdding: .month, value: 1, to: candidate) ?? candidate
    }

    /// Parses the server's `nextDueOn` -- a plain calendar day, so no time zone is read
    /// out of it beyond the one the rest of the app counts days in.
    static func parseIsoDate(_ iso: String?) -> Date? {
        guard let iso else { return nil }
        let parser = DateFormatter()
        // POSIX so a phone on a non-Gregorian locale still reads "2026-09-01" literally.
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.calendar = calendar
        parser.timeZone = calendar.timeZone
        parser.dateFormat = "yyyy-MM-dd"
        return parser.date(from: iso)
    }

    static func format(_ date: Date, _ pattern: String) -> String {
        let formatter = DateFormatter()
        formatter.locale = swedish
        formatter.calendar = calendar
        formatter.dateFormat = pattern
        return formatter.string(from: date)
    }

    /// Swedish ordinals: 1:a, 2:a, 3:e ... 11:e, 12:e ... 21:a, 22:a, 23:e.
    static func ordinal(_ day: Int) -> String {
        let suffix = (day % 10 == 1 || day % 10 == 2) && day != 11 && day != 12 ? ":a" : ":e"
        return "\(day)\(suffix)"
    }

    /// The wallet's one-line summary: "Månadspeng · nästa 1 september".
    ///
    /// The subtitle carries the date rather than the amount. A parent checking that it
    /// is on needs to know when; a parent who wants to change the amount is tapping
    /// through anyway.
    static func describe(_ schedule: RecurringAllowanceDetailDTO?) -> String {
        guard let schedule, schedule.active else { return "Inte inställt" }
        let kind: String
        switch schedule.kind {
        case "WEEKLY": kind = "Veckopeng"
        case "MONTHLY": kind = "Månadspeng"
        default: kind = "Efter nivå"
        }
        guard let due = parseIsoDate(schedule.nextDueOn) else { return kind }
        return "\(kind) · nästa \(format(due, "d MMMM"))"
    }
}

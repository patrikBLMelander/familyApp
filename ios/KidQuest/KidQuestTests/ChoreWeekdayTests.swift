import Foundation
import Testing
@testable import KidQuest

/// Fäster kontraktet mot backend för veckodagar.
///
/// Bakgrunden är en riktig bugg: kom igång-flödets färdiga sysslor skickade `"1"..."7"`
/// i stället för `MON...SUN`. Backend validerar ingenting -- `DailyChoreService` gör
/// `String.join(",", weekdays)` och sparar vad den får -- så anropet lyckades, sysslorna
/// skapades, och de matchade sedan aldrig någon dag. En förälder såg ett barn utan
/// sysslor och ingen felmeddelande någonstans.
///
/// Det som gör buggen värd ett test är att den inte gick att upptäcka genom att titta:
/// både klient och server var nöjda. Bara jämförelsen mot serverns egen formel avslöjar
/// den.
@Suite struct ChoreWeekdayTests {

    /// Serverns formel är `date.getDayOfWeek().name().substring(0, 3)` på en engelsk
    /// DayOfWeek, alltså exakt dessa sju strängar i den här ordningen.
    @Test func codesMatchWhatTheEndpointExpects() {
        #expect(ChoreWeekday.all.map(\.code) == ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"])
    }

    /// Måndag först, som en svensk vecka läses -- inte som Calendar numrerar sina dagar.
    @Test func weekStartsOnMonday() {
        #expect(ChoreWeekday.all.first?.code == "MON")
        #expect(ChoreWeekday.all.first?.index == 0)
        #expect(ChoreWeekday.all.map(\.index) == Array(0...6))
    }

    /// Varje åldersspann ska ge faktiska sysslor. Ett tomt spann såg i appen ut precis
    /// som den trasiga veckodagskoden: barnet tillagt, ingenting att göra.
    @Test func everyAgeRangeSuggestsChores() {
        for range in AgeRange.allCases {
            #expect(!range.defaultChores.isEmpty, "\(range.label) föreslår inga sysslor")
            #expect(
                range.defaultChores.allSatisfy { !$0.trimmingCharacters(in: .whitespaces).isEmpty },
                "\(range.label) har en tom titel"
            )
        }
    }

    /// Samma uppsättning som Android, som är den appen familjerna använder idag. En
    /// familj som byter telefon ska inte mötas av andra sysslor.
    @Test func ageRangesMatchAndroid() {
        #expect(AgeRange.allCases.count == 4)
        #expect(AgeRange.allCases.map(\.label) == ["4–6 år", "7–9 år", "10–12 år", "13+ år"])
        #expect(AgeRange.thirteenPlus.defaultChores.count == 5)
        #expect(AgeRange.thirteenPlus.defaultChores.first == "Hålla rummet i ordning")
    }
}

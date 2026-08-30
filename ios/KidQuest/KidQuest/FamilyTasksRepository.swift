import Foundation

// MARK: - Family tasks

/// Every child's chores in one read, for the parent's view of the family's day.
///
/// It lives here and not in the view for the same reason `AdultDashboardRepository`
/// does: the screen should not have to know that one child's day is two endpoints, nor
/// that the family is a third. Keeping it out here is also what lets the screen be fed
/// a `Family` fixture and photographed without a network.
enum FamilyTasksRepository {

    /// One child, their day, and the week they recur over.
    struct ChildChores: Identifiable {
        let id: String
        let name: String
        /// Carries the completions, and is the only list that can be ticked.
        var today: [DailyChoreWithCompletionResponseDTO]
        /// The full schedule, every weekday — what the Vecka tab draws.
        let all: [DailyChoreResponseDTO]
        /// True when this child's row could not be read. The section says so rather
        /// than showing an empty list, which would read as "nothing to do today".
        let loadFailed: Bool

        var done: Int { today.filter(\.completed).count }
        var total: Int { today.count }
        var allDone: Bool { total > 0 && done == total }
    }

    struct Family {
        var children: [ChildChores]

        var doneToday: Int { children.reduce(0) { $0 + $1.done } }
        var totalToday: Int { children.reduce(0) { $0 + $1.total } }
    }

    /// Every child in the family with their chores, read in parallel.
    ///
    /// Only the member list is allowed to fail the whole call. A single child's chores
    /// failing is marked on that child and the rest of the family still renders — the
    /// same bargain the adult dashboard strikes, and the reason a flaky row cannot cost
    /// a parent the whole screen.
    static func fetchFamilyChores(date: Date = Date()) async throws -> Family {
        let members = try await FamilyRepository.fetchChildren()

        let byId = await withTaskGroup(of: ChildChores.self) { group -> [String: ChildChores] in
            for member in members {
                group.addTask { await chores(for: member, date: date) }
            }
            var result: [String: ChildChores] = [:]
            for await child in group { result[child.id] = child }
            return result
        }

        // The server's order is the family's order. A task group yields in the order
        // answers arrive, so the list is rebuilt from the member list rather than from
        // whichever child's backend call happened to be quickest.
        return Family(children: members.compactMap { byId[$0.id] })
    }

    private static func chores(for member: FamilyMemberResponseDTO, date: Date) async -> ChildChores {
        do {
            let chores = try await DailyChoreRepositoryIOS.fetchChores(memberId: member.id, date: date)
            return ChildChores(
                id: member.id,
                name: member.name,
                today: chores.today,
                all: chores.all,
                loadFailed: false
            )
        } catch {
            return ChildChores(id: member.id, name: member.name, today: [], all: [], loadFailed: true)
        }
    }
}

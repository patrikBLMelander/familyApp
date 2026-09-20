import SwiftUI

/// Create or edit a recurring chore. Shared by the child dashboard (create) and the
/// tasks list (edit via swipe). When `existing` is set the fields are prefilled and
/// saving PATCHes that chore; otherwise it creates a new one for `childId`.
struct ChoreEditorSheet: View {
    let childId: String
    var existing: DailyChoreResponseDTO? = nil
    var onDismiss: () -> Void
    var onSuccess: () -> Void

    @State private var title: String
    @State private var selectedWeekdays: Set<String>
    @State private var xpPoints: Int
    @State private var isLoading = false
    @State private var error: String?

    private let allWeekdays: [(String, String)] = [
        ("MON", "M"), ("TUE", "T"), ("WED", "O"), ("THU", "T"), ("FRI", "F"), ("SAT", "L"), ("SUN", "S")
    ]

    init(childId: String, existing: DailyChoreResponseDTO? = nil, onDismiss: @escaping () -> Void, onSuccess: @escaping () -> Void) {
        self.childId = childId
        self.existing = existing
        self.onDismiss = onDismiss
        self.onSuccess = onSuccess
        _title = State(initialValue: existing?.title ?? "")
        _selectedWeekdays = State(initialValue: Set(existing?.weekdays ?? []))
        _xpPoints = State(initialValue: min(max(existing?.xpPoints ?? 1, 1), 3))
    }

    private var editing: Bool { existing != nil }

    var body: some View {
        NavigationView {
            Form {
                Section("Titel") {
                    TextField("Titel", text: $title)
                }
                Section("Veckodagar") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 7), spacing: 8) {
                        ForEach(allWeekdays, id: \.0) { day, label in
                            Button {
                                if selectedWeekdays.contains(day) {
                                    selectedWeekdays.remove(day)
                                } else {
                                    selectedWeekdays.insert(day)
                                }
                            } label: {
                                Text(label)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 6)
                                    .background(selectedWeekdays.contains(day) ? Color.accentColor : Color(.systemGray5))
                                    .foregroundColor(selectedWeekdays.contains(day) ? .white : .primary)
                                    .cornerRadius(6)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.vertical, 4)
                }
                Section("XP (mat)") {
                    Picker("XP", selection: $xpPoints) {
                        Text("x1").tag(1)
                        Text("x2").tag(2)
                        Text("x3").tag(3)
                    }
                    .pickerStyle(.segmented)
                }
                if let error {
                    Section {
                        Text(error).foregroundColor(.red)
                    }
                }
            }
            .navigationTitle(editing ? "Redigera syssla" : "Ny återkommande syssla")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Avbryt") { onDismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isLoading ? "Sparar…" : "Spara") { save() }
                        .disabled(isLoading)
                }
            }
        }
    }

    private func save() {
        guard !title.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Titel krävs"; return
        }
        guard !selectedWeekdays.isEmpty else {
            error = "Välj minst en veckodag"; return
        }
        isLoading = true
        error = nil
        let ordered = allWeekdays.map(\.0).filter { selectedWeekdays.contains($0) }
        let cleanTitle = title.trimmingCharacters(in: .whitespaces)
        Task {
            do {
                if let existing {
                    try await DailyChoreRepositoryIOS.updateChore(
                        choreId: existing.id, title: cleanTitle, weekdays: ordered, xpPoints: xpPoints)
                } else {
                    try await DailyChoreRepositoryIOS.createChore(
                        memberId: childId, title: cleanTitle, weekdays: ordered, xpPoints: xpPoints)
                }
                onSuccess()
            } catch {
                self.error = editing ? "Kunde inte spara ändringarna." : "Kunde inte skapa sysslan."
            }
            isLoading = false
        }
    }
}

import { useState, useEffect } from "react";
import { FamilyMemberResponse } from "../../../shared/api/familyMembers";
import { getNextWeekday, getDateTwoYearsLater, formatDateForAPI, WEEKDAY_NAMES } from "../utils/weekdayUtils";
import { createCalendarEvent } from "../../../shared/api/calendar";

export type SimplifiedTaskFormProps = {
  members: FamilyMemberResponse[];
  currentUserId?: string | null;
  onSave: () => void;
  onCancel: () => void;
};

/**
 * Simplified form for creating recurring tasks.
 * Only requires: Title, Weekdays, Person, XP.
 * Creates separate recurring events for each selected weekday.
 */
export function SimplifiedTaskForm({ members, currentUserId, onSave, onCancel }: SimplifiedTaskFormProps) {
  const [title, setTitle] = useState("");
  const [selectedWeekdays, setSelectedWeekdays] = useState<Set<number>>(new Set());
  const [selectedMemberIds, setSelectedMemberIds] = useState<Set<string>>(new Set());
  const [xpPoints, setXpPoints] = useState("1");
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Auto-select current user if available
  useEffect(() => {
    if (currentUserId && selectedMemberIds.size === 0) {
      setSelectedMemberIds(new Set([currentUserId]));
    }
  }, [currentUserId, selectedMemberIds.size]);

  const toggleWeekday = (weekday: number) => {
    setSelectedWeekdays((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(weekday)) {
        newSet.delete(weekday);
      } else {
        newSet.add(weekday);
      }
      return newSet;
    });
  };

  const toggleMember = (memberId: string) => {
    setSelectedMemberIds((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(memberId)) {
        newSet.delete(memberId);
      } else {
        newSet.add(memberId);
      }
      return newSet;
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    // Validation
    if (!title.trim()) {
      setError("Titel är obligatorisk");
      return;
    }

    if (selectedWeekdays.size === 0) {
      setError("Välj minst en veckodag");
      return;
    }

    if (selectedMemberIds.size === 0) {
      setError("Välj minst en person");
      return;
    }

    const xp = parseInt(xpPoints, 10);
    if (isNaN(xp) || xp < 0) {
      setError("XP-poäng måste vara ett positivt tal");
      return;
    }

    setIsSaving(true);

    try {
      // Create a separate recurring event for each selected weekday
      // For each member
      const createPromises: Promise<void>[] = [];

      for (const memberId of selectedMemberIds) {
        for (const weekday of selectedWeekdays) {
          // Calculate next occurrence of this weekday
          const nextDate = getNextWeekday(weekday);
          const endDate = getDateTwoYearsLater(nextDate);

          // Format dates for API (YYYY-MM-DDTHH:mm)
          const startDateTime = `${formatDateForAPI(nextDate)}T00:00`;
          const recurringEndDate = formatDateForAPI(endDate);

          // Create recurring event
          createPromises.push(
            createCalendarEvent(
              title.trim(),
              startDateTime,
              null, // endDateTime - not needed for all-day events
              true, // isAllDay
              undefined, // description
              undefined, // categoryId
              undefined, // location
              [memberId], // participantIds
              "WEEKLY", // recurringType
              1, // recurringInterval
              recurringEndDate, // recurringEndDate
              null, // recurringEndCount
              true, // isTask
              xp, // xpPoints
              true // isRequired
            ).then(() => {}) // Convert to Promise<void>
          );
        }
      }

      // Wait for all events to be created
      await Promise.all(createPromises);

      // Reset form
      setTitle("");
      setSelectedWeekdays(new Set());
      setSelectedMemberIds(currentUserId ? new Set([currentUserId]) : new Set());
      setXpPoints("1");

      // Call onSave callback
      onSave();
    } catch (err) {
      console.error("Error creating tasks:", err);
      setError("Kunde inte skapa uppgifterna. Försök igen.");
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <section className="card">
      <h3 style={{ marginTop: 0 }}>Ny återkommande uppgift</h3>
      <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
        {error && <p className="error-text">{error}</p>}

        <div>
          <label htmlFor="title" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
            Titel *
          </label>
          <input
            id="title"
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            style={{ width: "100%", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
            placeholder="T.ex. Städa rummet"
          />
        </div>

        <div>
          <label style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>
            Veckodagar *
          </label>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "8px" }}>
            {/* JavaScript Date.getDay(): 0 = Sunday, 1 = Monday, ..., 6 = Saturday */}
            {/* Display in order: Monday (1) through Sunday (0) */}
            {[1, 2, 3, 4, 5, 6, 0].map((weekday) => {
              const isSelected = selectedWeekdays.has(weekday);
              return (
                <button
                  key={weekday}
                  type="button"
                  onClick={() => toggleWeekday(weekday)}
                  style={{
                    padding: "8px 16px",
                    borderRadius: "6px",
                    border: `2px solid ${isSelected ? "#4CAF50" : "#ddd"}`,
                    backgroundColor: isSelected ? "#E8F5E9" : "white",
                    color: isSelected ? "#2E7D32" : "#333",
                    cursor: "pointer",
                    fontWeight: isSelected ? 600 : 400,
                    fontSize: "0.9rem",
                  }}
                >
                  {WEEKDAY_NAMES[weekday === 0 ? 0 : weekday]}
                </button>
              );
            })}
          </div>
        </div>

        <div>
          <label style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>
            Person *
          </label>
          <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
            {members.map((member) => (
              <label key={member.id} style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <input
                  type="checkbox"
                  checked={selectedMemberIds.has(member.id)}
                  onChange={() => toggleMember(member.id)}
                />
                <span>{member.name}</span>
              </label>
            ))}
          </div>
        </div>

        <div>
          <label htmlFor="xpPoints" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
            XP-poäng *
          </label>
          <input
            id="xpPoints"
            type="number"
            min="0"
            value={xpPoints}
            onChange={(e) => setXpPoints(e.target.value)}
            required
            style={{ width: "100%", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
          />
        </div>

        <div style={{ display: "flex", gap: "8px", marginTop: "12px", justifyContent: "space-between" }}>
          <div style={{ display: "flex", gap: "8px" }}>
            <button type="submit" className="button-primary" disabled={isSaving}>
              {isSaving ? "Sparar..." : "Skapa uppgift"}
            </button>
            <button type="button" onClick={onCancel} className="todo-action-button" disabled={isSaving}>
              Avbryt
            </button>
          </div>
        </div>
      </form>
    </section>
  );
}

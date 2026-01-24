import { CalendarEventResponse } from "../../../shared/api/calendar";
import { getEventsForDay } from "../utils/eventFilters";

type DayActionMenuProps = {
  date: Date;
  events: CalendarEventResponse[];
  onClose: () => void;
  onCreateEvent: (date: Date) => void;
  onEditEvent: (event: CalendarEventResponse) => void;
  onDeleteEvent: (eventId: string) => Promise<void>;
  onGoToRollingView: (date: Date) => void;
  currentUserRole: "CHILD" | "ASSISTANT" | "PARENT" | null;
  currentUserId: string | null;
};

export function DayActionMenu({
  date,
  events,
  onClose,
  onCreateEvent,
  onEditEvent,
  onDeleteEvent,
  onGoToRollingView,
  currentUserRole,
  currentUserId,
}: DayActionMenuProps) {
  // Get events for this specific day
  const dayEvents = getEventsForDay(events, date);
  
  // Filter to only show non-task events (calendar events, not "dagens att göra")
  const calendarEvents = dayEvents.filter(event => !event.isTask);
  
  // Filter events based on edit/delete permissions
  // PARENT and ASSISTANT can edit/delete all events, CHILD cannot edit/delete
  const editableEvents = calendarEvents.filter(event => {
    return currentUserRole === "PARENT" || currentUserRole === "ASSISTANT";
  });
  
  // Sort events: non-tasks first, then by start time
  const sortedEvents = [...editableEvents].sort((a, b) => {
    // Non-tasks before tasks
    if (a.isTask !== b.isTask) {
      return a.isTask ? 1 : -1;
    }
    // Then by start time
    return new Date(a.startDateTime).getTime() - new Date(b.startDateTime).getTime();
  });

  const dateStr = date.toLocaleDateString("sv-SE", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  });

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  return (
    <div
      onClick={handleBackdropClick}
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: "rgba(0, 0, 0, 0.5)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 1000,
      }}
    >
      <section
        className="card"
        onClick={(e) => e.stopPropagation()}
        style={{
          maxWidth: "400px",
          width: "90%",
          maxHeight: "80vh",
          overflow: "auto",
        }}
      >
        <div style={{ marginBottom: "16px" }}>
          <h3 style={{ marginTop: 0, marginBottom: "4px", fontSize: "1.1rem", fontWeight: 600 }}>
            {dateStr}
          </h3>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
          {/* Create new event button */}
          <button
            type="button"
            className="button-primary"
            onClick={() => {
              onCreateEvent(date);
              onClose();
            }}
            style={{ width: "100%", padding: "12px", fontSize: "0.95rem", textAlign: "center" }}
          >
            <span style={{ marginRight: "8px", color: "#fff" }}>+</span>
            Skapa nytt event
          </button>

          {/* Edit existing events */}
          {sortedEvents.length > 0 && (
            <>
              <div
                style={{
                  height: "1px",
                  backgroundColor: "#ddd",
                  margin: "8px 0",
                }}
              />
              {sortedEvents.map((event) => (
                <div
                  key={event.id}
                  style={{
                    display: "flex",
                    gap: "8px",
                    alignItems: "stretch",
                  }}
                >
                  <button
                    type="button"
                    className="button-secondary"
                    onClick={() => {
                      onEditEvent(event);
                      onClose();
                    }}
                    style={{
                      flex: 1,
                      padding: "12px",
                      fontSize: "0.95rem",
                      textAlign: "center",
                    }}
                  >
                    <span style={{ marginRight: "8px", color: "#666" }}>✎</span>
                    Redigera {event.title}
                  </button>
                  <button
                    type="button"
                    className="button-secondary"
                    onClick={async () => {
                      await onDeleteEvent(event.id);
                      onClose();
                    }}
                    title="Ta bort event"
                    style={{
                      padding: "12px",
                      fontSize: "1.2rem",
                      minWidth: "48px",
                      opacity: 0.6,
                      borderColor: "rgba(200, 100, 100, 0.2)",
                      color: "#999",
                      fontWeight: 300,
                      lineHeight: 1,
                    }}
                  >
                    ×
                  </button>
                </div>
              ))}
            </>
          )}

          {/* Go to rolling view button */}
          <div
            style={{
              height: "1px",
              backgroundColor: "#ddd",
              margin: "8px 0",
            }}
          />
          <button
            type="button"
            className="button-secondary"
            onClick={() => {
              onGoToRollingView(date);
              onClose();
            }}
            style={{ width: "100%", padding: "12px", fontSize: "0.95rem", textAlign: "center" }}
          >
            <span style={{ marginRight: "8px", color: "#666" }}>→</span>
            Gå till rullande vy
          </button>

          {/* Close button */}
          <button
            type="button"
            className="button-secondary"
            onClick={onClose}
            style={{ width: "100%", padding: "12px", fontSize: "0.95rem", marginTop: "8px", textAlign: "center" }}
          >
            Avbryt
          </button>
        </div>
      </section>
    </div>
  );
}

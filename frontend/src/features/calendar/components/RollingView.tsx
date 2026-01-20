import { CalendarEventResponse, CalendarEventCategoryResponse, CalendarTaskWithCompletionResponse } from "../../../shared/api/calendar";
import { FamilyMemberResponse } from "../../../shared/api/familyMembers";
import { formatDateTimeRange } from "../utils/dateFormatters";
import { MAX_RECURRING_DAYS } from "../constants";

type RollingViewProps = {
  showTasksOnly: boolean;
  showAllMembers: boolean;
  selectedDate: Date;
  setSelectedDate: (date: Date) => void;
  showQuickAdd: boolean;
  setShowQuickAdd: (show: boolean) => void;
  quickAddTitle: string;
  setQuickAddTitle: (title: string) => void;
  handleQuickAdd: (title: string) => Promise<void>;
  tasksWithCompletion: CalendarTaskWithCompletionResponse[];
  tasksByMember: Map<string, CalendarTaskWithCompletionResponse[]>;
  members: FamilyMemberResponse[];
  currentMemberId: string | null;
  events: CalendarEventResponse[];
  categories: CalendarEventCategoryResponse[];
  handleToggleTask: (eventId: string, memberId?: string) => void;
  handleDeleteEvent: (eventId: string) => Promise<void>;
  setEditingEvent: (event: CalendarEventResponse | null) => void;
};

// Helper function to get all dates for a multi-day all-day event
function getAllDayEventDates(event: CalendarEventResponse): string[] {
  if (!event.isAllDay) return [];
  
  const startDateStr = event.startDateTime.substring(0, 10);
  if (!event.endDateTime) {
    // Single day event
    return [startDateStr];
  }
  
  // Multi-day event - get all dates from start to end (inclusive)
  const endDateStr = event.endDateTime.substring(0, 10);
  const dates: string[] = [];
  const startDate = new Date(startDateStr + "T00:00:00");
  const endDate = new Date(endDateStr + "T00:00:00");
  
  // Safety check: if end is before start, just return start date
  if (endDate < startDate) {
    return [startDateStr];
  }
  
  // Safety limit to prevent performance issues
  let dayCount = 0;
  
  // Iterate through all dates from start to end (inclusive)
  const currentDate = new Date(startDate);
  while (currentDate <= endDate && dayCount < MAX_RECURRING_DAYS) {
    const year = currentDate.getFullYear();
    const month = String(currentDate.getMonth() + 1).padStart(2, "0");
    const day = String(currentDate.getDate()).padStart(2, "0");
    dates.push(`${year}-${month}-${day}`);
    currentDate.setDate(currentDate.getDate() + 1);
    dayCount++;
  }
  
  return dates;
}

export function RollingView({
  showTasksOnly,
  showAllMembers,
  selectedDate,
  setSelectedDate,
  showQuickAdd,
  setShowQuickAdd,
  quickAddTitle,
  setQuickAddTitle,
  handleQuickAdd,
  tasksWithCompletion,
  tasksByMember,
  members,
  currentMemberId,
  events,
  categories,
  handleToggleTask,
  handleDeleteEvent,
  setEditingEvent,
}: RollingViewProps) {
  // Filter events based on task/event toggle
  const now = new Date();
  const todayStr = now.toISOString().split("T")[0]; // YYYY-MM-DD format
  
  // Filter by task/event type based on showTasksOnly
  let filteredEvents = events.filter(event => {
    if (showTasksOnly) {
      if (!event.isTask) return false;
      // If showTasksOnly is true and showAllMembers is false, filter by current member
      if (!showAllMembers && currentMemberId) {
        return event.participantIds.includes(currentMemberId);
      }
      return true;
    } else {
      return !event.isTask; // Show only non-task events
    }
  });
  
  // For rolling view, also filter by date (today or future)
  filteredEvents = filteredEvents.filter(event => {
    const isFutureEvent = event.isAllDay
      ? getAllDayEventDates(event).some(dateStr => dateStr >= todayStr)
      : new Date(event.startDateTime) >= now;
    return isFutureEvent;
  });

  // Group events by date
  const eventsByDate = filteredEvents.reduce((acc, event) => {
    if (event.isAllDay) {
      // For all-day events, get all dates the event spans
      const dates = getAllDayEventDates(event);
      dates.forEach(dateKey => {
        if (!acc[dateKey]) {
          acc[dateKey] = [];
        }
        acc[dateKey].push(event);
      });
    } else {
      // For regular events, parse the datetime
      const date = new Date(event.startDateTime);
      const dateKey = date.toISOString().split("T")[0];
      if (!acc[dateKey]) {
        acc[dateKey] = [];
      }
      acc[dateKey].push(event);
    }
    return acc;
  }, {} as Record<string, CalendarEventResponse[]>);

  const sortedDates = Object.keys(eventsByDate).sort();

  const handleQuickAddTask = async () => {
    await handleQuickAdd(quickAddTitle);
  };

  return (
    <>
      {showTasksOnly ? (
        // Show tasks view
        <>
          {/* Date navigation buttons */}
          <div style={{ display: "flex", gap: "8px", marginBottom: "12px", justifyContent: "center" }}>
            <button
              type="button"
              className="button-secondary"
              onClick={() => {
                const newDate = new Date(selectedDate);
                newDate.setDate(newDate.getDate() - 1);
                setSelectedDate(newDate);
              }}
              style={{ fontSize: "0.9rem", padding: "8px 16px" }}
            >
              {(() => {
                const prevDate = new Date(selectedDate);
                prevDate.setDate(prevDate.getDate() - 1);
                return prevDate.toLocaleDateString("sv-SE", { day: "numeric", month: "short" });
              })()}
            </button>
            {selectedDate.toDateString() !== new Date().toDateString() && (
              <button
                type="button"
                className="button-secondary"
                onClick={() => setSelectedDate(new Date())}
                style={{ fontSize: "0.9rem", padding: "8px 16px" }}
              >
                Idag
              </button>
            )}
            <button
              type="button"
              className="button-secondary"
              onClick={() => {
                const newDate = new Date(selectedDate);
                newDate.setDate(newDate.getDate() + 1);
                setSelectedDate(newDate);
              }}
              style={{ fontSize: "0.9rem", padding: "8px 16px" }}
            >
              {(() => {
                const nextDate = new Date(selectedDate);
                nextDate.setDate(nextDate.getDate() + 1);
                return nextDate.toLocaleDateString("sv-SE", { day: "numeric", month: "short" });
              })()}
            </button>
          </div>
          {/* Quick Add Input */}
          {showQuickAdd && (
            <section className="card" style={{ marginBottom: "12px" }}>
              <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                <input
                  type="text"
                  value={quickAddTitle}
                  onChange={(e) => setQuickAddTitle(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      void handleQuickAddTask();
                    } else if (e.key === "Escape") {
                      setShowQuickAdd(false);
                      setQuickAddTitle("");
                    }
                  }}
                  placeholder="Skriv titel..."
                  autoFocus
                  style={{
                    flex: 1,
                    padding: "8px 12px",
                    borderRadius: "6px",
                    border: "1px solid #ddd",
                    fontSize: "0.9rem",
                  }}
                />
                <button
                  type="button"
                  className="button-primary"
                  onClick={() => void handleQuickAddTask()}
                  style={{ fontSize: "0.9rem", padding: "8px 16px" }}
                >
                  Lägg till
                </button>
                <button
                  type="button"
                  className="button-secondary"
                  onClick={() => {
                    setShowQuickAdd(false);
                    setQuickAddTitle("");
                  }}
                  style={{ fontSize: "0.9rem", padding: "8px 16px" }}
                >
                  Avbryt
                </button>
              </div>
            </section>
          )}

          {showAllMembers ? (
            // Show tasks grouped by member
            (() => {
              const hasTasks = Array.from(tasksByMember.values()).some(tasks => tasks.length > 0);
              return !hasTasks && !showQuickAdd ? (
                <section className="card">
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "12px" }}>
                    <div style={{ flex: 1 }}>
                      <h3 style={{ marginTop: 0, marginBottom: "4px", fontSize: "1rem", fontWeight: 600 }}>
                        {selectedDate.toDateString() === new Date().toDateString() ? "Dagens Att Göra" : "Att Göra"}
                      </h3>
                      <p style={{ margin: 0, fontSize: "0.9rem", color: "#6b6b6b" }}>
                        {selectedDate.toLocaleDateString("sv-SE", {
                          weekday: "long",
                          year: "numeric",
                          month: "long",
                          day: "numeric",
                        })}
                      </p>
                    </div>
                  </div>
                  <p className="placeholder-text" style={{ margin: 0 }}>
                    Inga sysslor för någon familjemedlem.
                  </p>
                </section>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
                  {members.map((member) => {
                    const memberTasks = tasksByMember.get(member.id) || [];
                    if (memberTasks.length === 0 && !showQuickAdd) return null;
                    
                    return (
                      <section key={member.id} className="card">
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "12px" }}>
                          <div style={{ flex: 1 }}>
                            <h3 style={{ marginTop: 0, marginBottom: "4px", fontSize: "1rem", fontWeight: 600 }}>
                              {member.name}
                            </h3>
                          </div>
                        </div>
                        {memberTasks.length === 0 ? (
                          <p className="placeholder-text" style={{ margin: 0, fontSize: "0.9rem" }}>
                            Inga sysslor
                          </p>
                        ) : (
                          <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
                            {memberTasks.map((task) => {
                              const bgColor = task.completed 
                                ? "#f0fff4" // Green for completed
                                : (task.event.isRequired ? "#f7fafc" : "#fff7ed"); // Gray for required, orange for extra
                              const borderColor = task.completed 
                                ? "#48bb78" // Green border for completed
                                : (task.event.isRequired ? "#e2e8f0" : "#fed7aa"); // Gray for required, orange for extra
                              
                              return (
                                <li
                                  key={task.event.id}
                                  style={{
                                    background: bgColor,
                                    borderColor: borderColor,
                                    borderRadius: "8px",
                                    padding: "12px",
                                    marginBottom: "8px",
                                    border: `1px solid ${borderColor}`,
                                  }}
                                >
                                  <label style={{ display: "flex", alignItems: "center", gap: "12px", cursor: "pointer" }}>
                                    <input
                                      type="checkbox"
                                      checked={task.completed}
                                      onChange={() => handleToggleTask(task.event.id, member.id)}
                                      style={{ cursor: "pointer", width: "18px", height: "18px" }}
                                    />
                                    <div style={{ flex: 1 }}>
                                      <span 
                                        style={{ 
                                          color: "#2d3748",
                                          textDecoration: task.completed ? "line-through" : "none",
                                          fontWeight: task.completed ? 600 : 500
                                        }}
                                      >
                                        {task.event.title}
                                      </span>
                                      {task.event.xpPoints && task.event.xpPoints > 0 && (
                                        <span style={{
                                          fontSize: "0.75rem",
                                          padding: "2px 8px",
                                          borderRadius: "12px",
                                          background: "rgba(184, 230, 184, 0.3)",
                                          color: "#2d5a2d",
                                          fontWeight: 600,
                                          marginLeft: "8px"
                                        }}>
                                          +{task.event.xpPoints} XP
                                        </span>
                                      )}
                                      {task.event.description && (
                                        <p style={{ 
                                          margin: "4px 0 0", 
                                          fontSize: "0.9rem", 
                                          color: "#6b6b6b",
                                          fontStyle: task.completed ? "italic" : "normal"
                                        }}>
                                          {task.event.description}
                                        </p>
                                      )}
                                    </div>
                                  </label>
                                </li>
                              );
                            })}
                          </ul>
                        )}
                      </section>
                    );
                  })}
                </div>
              );
            })()
          ) : (
            // Show tasks for current member only
            tasksWithCompletion.length === 0 && !showQuickAdd ? (
              <section className="card">
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "12px" }}>
                  <div style={{ flex: 1 }}>
                    <h3 style={{ marginTop: 0, marginBottom: "4px", fontSize: "1rem", fontWeight: 600 }}>
                      {selectedDate.toDateString() === new Date().toDateString() ? "Dagens Att Göra" : "Att Göra"}
                    </h3>
                    <p style={{ margin: 0, fontSize: "0.9rem", color: "#6b6b6b" }}>
                      {selectedDate.toLocaleDateString("sv-SE", {
                        weekday: "long",
                        year: "numeric",
                        month: "long",
                        day: "numeric",
                      })}
                    </p>
                  </div>
                  <button
                    type="button"
                    className="button-primary"
                    onClick={() => setShowQuickAdd(true)}
                    style={{ fontSize: "0.9rem", padding: "8px 16px" }}
                  >
                    Add
                  </button>
                </div>
                <p className="placeholder-text" style={{ margin: 0 }}>
                  {selectedDate.toDateString() === new Date().toDateString() 
                    ? "Inga dagens att göra. Skapa ditt första task!"
                    : `Inga sysslor för ${selectedDate.toLocaleDateString("sv-SE", { weekday: "long", day: "numeric", month: "long" })}.`}
                </p>
              </section>
            ) : (
              <section className="card">
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "12px" }}>
                  <div style={{ flex: 1 }}>
                    <h3 style={{ marginTop: 0, marginBottom: "4px", fontSize: "1rem", fontWeight: 600 }}>
                      {selectedDate.toDateString() === new Date().toDateString() ? "Dagens Att Göra" : "Att Göra"}
                    </h3>
                    <p style={{ margin: 0, fontSize: "0.9rem", color: "#6b6b6b" }}>
                      {selectedDate.toLocaleDateString("sv-SE", {
                        weekday: "long",
                        year: "numeric",
                        month: "long",
                        day: "numeric",
                      })}
                    </p>
                  </div>
                  {!showQuickAdd && (
                    <button
                      type="button"
                      className="button-primary"
                      onClick={() => setShowQuickAdd(true)}
                      style={{ fontSize: "0.9rem", padding: "8px 16px" }}
                    >
                      Add
                    </button>
                  )}
                </div>
                <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
                {tasksWithCompletion.map((task) => {
                  const bgColor = task.completed 
                    ? "#f0fff4" // Green for completed
                    : (task.event.isRequired ? "#f7fafc" : "#fff7ed"); // Gray for required, orange for extra
                  const borderColor = task.completed 
                    ? "#48bb78" // Green border for completed
                    : (task.event.isRequired ? "#e2e8f0" : "#fed7aa"); // Gray for required, orange for extra
                  
                  return (
                    <li
                      key={task.event.id}
                      style={{
                        background: bgColor,
                        borderColor: borderColor,
                        borderRadius: "8px",
                        padding: "12px",
                        marginBottom: "8px",
                        border: `1px solid ${borderColor}`,
                      }}
                    >
                      <label style={{ display: "flex", alignItems: "center", gap: "12px", cursor: "pointer" }}>
                        <input
                          type="checkbox"
                          checked={task.completed}
                          onChange={() => handleToggleTask(task.event.id)}
                          style={{ cursor: "pointer", width: "18px", height: "18px" }}
                        />
                        <div style={{ flex: 1 }}>
                          <span 
                            style={{ 
                              color: "#2d3748",
                              textDecoration: task.completed ? "line-through" : "none",
                              fontWeight: task.completed ? 600 : 500
                            }}
                          >
                            {task.event.title}
                          </span>
                          {task.event.xpPoints && task.event.xpPoints > 0 && (
                            <span style={{
                              fontSize: "0.75rem",
                              padding: "2px 8px",
                              borderRadius: "12px",
                              background: "rgba(184, 230, 184, 0.3)",
                              color: "#2d5a2d",
                              fontWeight: 600,
                              marginLeft: "8px"
                            }}>
                              +{task.event.xpPoints} XP
                            </span>
                          )}
                          {task.event.description && (
                            <p style={{ 
                              margin: "4px 0 0", 
                              fontSize: "0.9rem", 
                              color: "#6b6b6b",
                              fontStyle: task.completed ? "italic" : "normal"
                            }}>
                              {task.event.description}
                            </p>
                          )}
                        </div>
                      </label>
                    </li>
                  );
                })}
                </ul>
              </section>
            )
          )}
        </>
      ) : (
        // Show events view
        sortedDates.length === 0 ? (
          <section className="card">
            <p className="placeholder-text">
              Inga kommande events. Skapa ditt första event!
            </p>
          </section>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
          {sortedDates.map((dateKey) => {
            const dateEvents = eventsByDate[dateKey];
            const date = new Date(dateKey);
            const dateStr = date.toLocaleDateString("sv-SE", {
              weekday: "long",
              year: "numeric",
              month: "long",
              day: "numeric",
            });

            return (
              <section key={dateKey} className="card">
                <h3 style={{ marginTop: 0, marginBottom: "12px", fontSize: "1rem", fontWeight: 600 }}>
                  {dateStr}
                </h3>
                <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
                  {dateEvents.map((event) => {
                    const category = categories.find((c) => c.id === event.categoryId);
                    const participantNames = event.participantIds
                      .map((id) => members.find((m) => m.id === id)?.name)
                      .filter(Boolean)
                      .join(", ");

                    return (
                      <li
                        key={event.id}
                        style={{
                          padding: "12px",
                          marginBottom: "8px",
                          background: category?.color
                            ? `${category.color}20`
                            : "rgba(240, 240, 240, 0.5)",
                          borderLeft: `4px solid ${category?.color || "#b8e6b8"}`,
                          borderRadius: "8px",
                        }}
                      >
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                          <div style={{ flex: 1 }}>
                            <div style={{ fontWeight: 600, marginBottom: "4px" }}>{event.title}</div>
                            <div style={{ fontSize: "0.85rem", color: "#6b6b6b", marginBottom: "4px" }}>
                              {event.isAllDay ? (
                                // For all-day events, show date range if multi-day, otherwise just the date
                                (() => {
                                  const startDate = new Date(event.startDateTime.substring(0, 10));
                                  const startDateStr = startDate.toLocaleDateString("sv-SE", {
                                    year: "numeric",
                                    month: "long",
                                    day: "numeric",
                                  });
                                  
                                  if (event.endDateTime) {
                                    const endDate = new Date(event.endDateTime.substring(0, 10));
                                    const endDateStr = endDate.toLocaleDateString("sv-SE", {
                                      year: "numeric",
                                      month: "long",
                                      day: "numeric",
                                    });
                                    
                                    // Check if same day
                                    if (event.startDateTime.substring(0, 10) === event.endDateTime.substring(0, 10)) {
                                      return `${startDateStr} - Heldag`;
                                    }
                                    
                                    // Multi-day: show range
                                    return `${startDateStr} - ${endDateStr} - Heldag`;
                                  }
                                  
                                  return `${startDateStr} - Heldag`;
                                })()
                              ) : (
                                formatDateTimeRange(event.startDateTime, event.endDateTime, false)
                              )}
                            </div>
                            {event.description && (
                              <div style={{ fontSize: "0.9rem", color: "#6b6b6b", marginBottom: "4px" }}>
                                {event.description}
                              </div>
                            )}
                            {event.location && (
                              <div style={{ fontSize: "0.85rem", color: "#6b6b6b", marginBottom: "4px" }}>
                                📍 {event.location}
                              </div>
                            )}
                            {participantNames && (
                              <div style={{ fontSize: "0.85rem", color: "#6b6b6b" }}>
                                👥 {participantNames}
                              </div>
                            )}
                          </div>
                          <div style={{ display: "flex", gap: "8px" }}>
                            <button
                              type="button"
                              className="todo-action-button"
                              onClick={() => setEditingEvent(event)}
                              style={{ fontSize: "0.8rem", padding: "4px 8px" }}
                            >
                              Redigera
                            </button>
                            <button
                              type="button"
                              className="todo-action-button-danger"
                              onClick={() => void handleDeleteEvent(event.id)}
                              style={{ fontSize: "0.8rem", padding: "4px 8px", borderRadius: "8px" }}
                            >
                              Ta bort
                            </button>
                          </div>
                        </div>
                      </li>
                    );
                  })}
                </ul>
              </section>
            );
          })}
        </div>
        )
      )}
    </>
  );
}

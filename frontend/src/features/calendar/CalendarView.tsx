import { useEffect, useState } from "react";
import {
  fetchCalendarEvents,
  createCalendarEvent,
  updateCalendarEvent,
  deleteCalendarEvent,
  CalendarEventResponse,
  fetchCalendarCategories,
  createCalendarCategory,
  updateCalendarCategory,
  deleteCalendarCategory,
  CalendarEventCategoryResponse,
} from "../../shared/api/calendar";
import { fetchAllFamilyMembers, FamilyMemberResponse } from "../../shared/api/familyMembers";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "dailytasks" | "dailytasksadmin" | "familymembers";

type CalendarViewProps = {
  onNavigate?: (view: ViewKey) => void;
};

type CalendarViewType = "rolling" | "week" | "month";

export function CalendarView({ onNavigate }: CalendarViewProps) {
  const [events, setEvents] = useState<CalendarEventResponse[]>([]);
  const [categories, setCategories] = useState<CalendarEventCategoryResponse[]>([]);
  const [members, setMembers] = useState<FamilyMemberResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingEvent, setEditingEvent] = useState<CalendarEventResponse | null>(null);
  const [viewType, setViewType] = useState<CalendarViewType>("rolling");
  const [currentWeek, setCurrentWeek] = useState(new Date());
  const [currentMonth, setCurrentMonth] = useState(new Date());
  const [showCategoryManager, setShowCategoryManager] = useState(false);

  useEffect(() => {
    void loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const [eventsData, categoriesData, membersData] = await Promise.all([
        fetchCalendarEvents(),
        fetchCalendarCategories(),
        fetchAllFamilyMembers(),
      ]);
      setEvents(eventsData);
      setCategories(categoriesData);
      setMembers(membersData);
    } catch (e) {
      setError("Kunde inte hämta kalenderdata.");
      console.error("Error loading calendar data:", e);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateEvent = async (eventData: {
    title: string;
    startDateTime: string; // Changed from Date to string
    endDateTime: string | null; // Changed from Date to string
    isAllDay: boolean;
    description?: string;
    categoryId?: string;
    location?: string;
    participantIds?: string[];
    recurringType?: "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY" | null;
    recurringInterval?: number | null;
    recurringEndDate?: string | null; // Changed from Date to string
    recurringEndCount?: number | null;
  }) => {
    try {
      await createCalendarEvent(
        eventData.title,
        eventData.startDateTime,
        eventData.endDateTime,
        eventData.isAllDay,
        eventData.description,
        eventData.categoryId,
        eventData.location,
        eventData.participantIds,
        eventData.recurringType,
        eventData.recurringInterval,
        eventData.recurringEndDate,
        eventData.recurringEndCount
      );
      await loadData();
      setShowCreateForm(false);
    } catch (e) {
      setError("Kunde inte skapa event.");
      console.error("Error creating event:", e);
    }
  };

  const handleUpdateEvent = async (
    eventId: string,
    eventData: {
      title: string;
      startDateTime: string; // Changed from Date to string
      endDateTime: string | null; // Changed from Date to string
      isAllDay: boolean;
      description?: string;
      categoryId?: string;
      location?: string;
      participantIds?: string[];
      recurringType?: "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY" | null;
      recurringInterval?: number | null;
      recurringEndDate?: string | null; // Changed from Date to string
      recurringEndCount?: number | null;
    }
  ) => {
    try {
      await updateCalendarEvent(
        eventId,
        eventData.title,
        eventData.startDateTime,
        eventData.endDateTime,
        eventData.isAllDay,
        eventData.description,
        eventData.categoryId,
        eventData.location,
        eventData.participantIds,
        eventData.recurringType,
        eventData.recurringInterval,
        eventData.recurringEndDate,
        eventData.recurringEndCount
      );
      await loadData();
      setEditingEvent(null);
    } catch (e) {
      setError("Kunde inte uppdatera event.");
      console.error("Error updating event:", e);
    }
  };

  const handleDeleteEvent = async (eventId: string) => {
    if (!confirm("Är du säker på att du vill ta bort detta event?")) {
      return;
    }
    try {
      await deleteCalendarEvent(eventId);
      await loadData();
    } catch (e) {
      setError("Kunde inte ta bort event.");
      console.error("Error deleting event:", e);
    }
  };

  const formatDateTime = (dateTimeString: string, isAllDay: boolean): string => {
    const date = new Date(dateTimeString);
    if (isAllDay) {
      return date.toLocaleDateString("sv-SE", { year: "numeric", month: "long", day: "numeric" });
    }
    return date.toLocaleString("sv-SE", {
      year: "numeric",
      month: "long",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  const formatDateTimeRange = (
    startDateTime: string,
    endDateTime: string | null,
    isAllDay: boolean
  ): string => {
    if (isAllDay) {
      return formatDateTime(startDateTime, true);
    }

    const start = new Date(startDateTime);
    const startDate = start.toISOString().split("T")[0];
    
    if (!endDateTime) {
      // No end time, just show start
      return formatDateTime(startDateTime, false);
    }

    const end = new Date(endDateTime);
    const endDate = end.toISOString().split("T")[0];

    // Check if same day
    if (startDate === endDate) {
      // Same day: "13 januari 16:00 - 17:00" (or with year if not current year)
      const now = new Date();
      const startYear = start.getFullYear();
      const currentYear = now.getFullYear();
      
      const datePart = startYear === currentYear
        ? start.toLocaleDateString("sv-SE", { month: "long", day: "numeric" })
        : start.toLocaleDateString("sv-SE", { year: "numeric", month: "long", day: "numeric" });
      
      const startTime = start.toLocaleTimeString("sv-SE", { hour: "2-digit", minute: "2-digit" });
      const endTime = end.toLocaleTimeString("sv-SE", { hour: "2-digit", minute: "2-digit" });
      
      return `${datePart} ${startTime} - ${endTime}`;
    } else {
      // Different days: show both full dates
      return `${formatDateTime(startDateTime, false)} - ${formatDateTime(endDateTime, false)}`;
    }
  };

  // Filter events based on view type
  const now = new Date();
  const filteredEvents = viewType === "rolling" 
    ? events.filter(event => new Date(event.startDateTime) >= now)
    : events;

  // Helper function to get all dates for a multi-day all-day event
  const getAllDayEventDates = (event: CalendarEventResponse): string[] => {
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
    
    // Safety limit: max 365 days to prevent performance issues
    const maxDays = 365;
    let dayCount = 0;
    
    // Iterate through all dates from start to end (inclusive)
    const currentDate = new Date(startDate);
    while (currentDate <= endDate && dayCount < maxDays) {
      const year = currentDate.getFullYear();
      const month = String(currentDate.getMonth() + 1).padStart(2, "0");
      const day = String(currentDate.getDate()).padStart(2, "0");
      dates.push(`${year}-${month}-${day}`);
      currentDate.setDate(currentDate.getDate() + 1);
      dayCount++;
    }
    
    return dates;
  };

  // Group events by date
  // For all-day events, extract date directly from string to avoid timezone issues
  // For multi-day all-day events, add the event to all dates it spans
  // For regular events, parse the datetime string
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

  return (
    <div className="calendar-view">
      <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "16px" }}>
        {onNavigate && (
          <button
            type="button"
            className="back-button"
            onClick={() => {
              // If form is open, close it; otherwise go to dashboard
              if (showCreateForm || editingEvent) {
                setShowCreateForm(false);
                setEditingEvent(null);
              } else {
                onNavigate("dashboard");
              }
            }}
            aria-label="Tillbaka"
          >
            ←
          </button>
        )}
        <h2 className="view-title" style={{ margin: 0, flex: 1 }}>Schema</h2>
        {!showCreateForm && !editingEvent && (
          <div style={{ display: "flex", gap: "8px" }}>
            <button
              type="button"
              className="todo-action-button"
              onClick={() => setShowCategoryManager(true)}
              style={{ fontSize: "0.85rem", padding: "8px 12px" }}
            >
              Kategorier
            </button>
            <button
              type="button"
              className="button-primary"
              onClick={() => setShowCreateForm(true)}
            >
              + Nytt event
            </button>
          </div>
        )}
      </div>

      {/* View type toggle - only show when not in form */}
      {!showCreateForm && !editingEvent && (
        <div style={{ 
          display: "flex", 
          gap: "4px", 
          marginBottom: "16px",
          padding: "4px",
          background: "rgba(255, 255, 255, 0.6)",
          borderRadius: "8px",
          border: "1px solid rgba(220, 210, 200, 0.3)"
        }}>
        <button
          type="button"
          onClick={() => setViewType("rolling")}
          style={{
            flex: 1,
            padding: "8px 12px",
            borderRadius: "6px",
            border: "none",
            background: viewType === "rolling" ? "#b8e6b8" : "transparent",
            color: viewType === "rolling" ? "#2d5a2d" : "#6b6b6b",
            fontWeight: viewType === "rolling" ? 600 : 400,
            fontSize: "0.85rem",
            cursor: "pointer",
            transition: "all 0.2s ease"
          }}
        >
          Rullande
        </button>
        <button
          type="button"
          onClick={() => setViewType("week")}
          style={{
            flex: 1,
            padding: "8px 12px",
            borderRadius: "6px",
            border: "none",
            background: viewType === "week" ? "#b8e6b8" : "transparent",
            color: viewType === "week" ? "#2d5a2d" : "#6b6b6b",
            fontWeight: viewType === "week" ? 600 : 400,
            fontSize: "0.85rem",
            cursor: "pointer",
            transition: "all 0.2s ease"
          }}
        >
          Vecka
        </button>
        <button
          type="button"
          onClick={() => setViewType("month")}
          style={{
            flex: 1,
            padding: "8px 12px",
            borderRadius: "6px",
            border: "none",
            background: viewType === "month" ? "#b8e6b8" : "transparent",
            color: viewType === "month" ? "#2d5a2d" : "#6b6b6b",
            fontWeight: viewType === "month" ? 600 : 400,
            fontSize: "0.85rem",
            cursor: "pointer",
            transition: "all 0.2s ease"
          }}
        >
          Månad
        </button>
      </div>
      )}

      {error && <p className="error-text">{error}</p>}

      {loading && (
        <section className="card">
          <p>Laddar...</p>
        </section>
      )}

      {!loading && !showCreateForm && !editingEvent && (
        <>
          {viewType === "rolling" && (
            <>
              {sortedDates.length === 0 ? (
                <section className="card">
                  <p className="placeholder-text">Inga kommande events. Skapa ditt första event!</p>
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
                                  className="delete-button"
                                  onClick={() => void handleDeleteEvent(event.id)}
                                  style={{ fontSize: "0.8rem", padding: "4px 8px" }}
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
          )}
          </>)}

          {viewType === "week" && (
            <WeekView
              events={events}
              categories={categories}
              members={members}
              currentWeek={currentWeek}
              onWeekChange={setCurrentWeek}
              onEventClick={(event) => setEditingEvent(event)}
              onEventDelete={handleDeleteEvent}
            />
          )}

          {viewType === "month" && (
            <MonthView
              events={events}
              categories={categories}
              members={members}
              currentMonth={currentMonth}
              onMonthChange={setCurrentMonth}
              onEventClick={(event) => setEditingEvent(event)}
              onEventDelete={handleDeleteEvent}
            />
          )}
        </>
      )}

      {(showCreateForm || editingEvent) && (
        <EventForm
          event={editingEvent}
          categories={categories}
          members={members}
          onSave={(eventData) => {
            if (editingEvent) {
              void handleUpdateEvent(editingEvent.id, eventData);
            } else {
              void handleCreateEvent(eventData);
            }
          }}
          onCancel={() => {
            setShowCreateForm(false);
            setEditingEvent(null);
          }}
        />
      )}

      {showCategoryManager && (
        <CategoryManager
          categories={categories}
          onClose={() => setShowCategoryManager(false)}
          onUpdate={async () => {
            // Reload categories
            const categoriesData = await fetchCalendarCategories();
            setCategories(categoriesData);
          }}
        />
      )}
    </div>
  );
}

type EventFormProps = {
  event?: CalendarEventResponse | null;
  categories: CalendarEventCategoryResponse[];
  members: FamilyMemberResponse[];
  onSave: (eventData: {
    title: string;
    startDateTime: string; // Changed from Date to string
    endDateTime: string | null; // Changed from Date to string
    isAllDay: boolean;
    description?: string;
    categoryId?: string;
    location?: string;
    participantIds?: string[];
    recurringType?: "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY" | null;
    recurringInterval?: number | null;
    recurringEndDate?: string | null; // Changed from Date to string
    recurringEndCount?: number | null;
  }) => void;
  onCancel: () => void;
};

function EventForm({ event, categories, members, onSave, onCancel }: EventFormProps) {
  const [title, setTitle] = useState(event?.title || "");
  const [description, setDescription] = useState(event?.description || "");
  const [showCategoryDropdown, setShowCategoryDropdown] = useState(false);
  
  // Calculate default end date (1 hour after start) for new events
  // Works with datetime-local format (YYYY-MM-DDTHH:mm)
  const getDefaultEndDate = (startDateStr: string): string => {
    if (!startDateStr) return "";
    // Parse the datetime-local string and add 1 hour
    const [datePart, timePart] = startDateStr.split("T");
    if (!timePart) return "";
    const [hours, minutes] = timePart.split(":").map(Number);
    let newHours = hours + 1;
    let newMinutes = minutes;
    if (newHours >= 24) {
      newHours = 0;
      // For simplicity, we don't handle day rollover here
    }
    return `${datePart}T${String(newHours).padStart(2, "0")}:${String(newMinutes).padStart(2, "0")}`;
  };
  
  // Convert ISO datetime string to datetime-local format (YYYY-MM-DDTHH:mm)
  // or date format (YYYY-MM-DD) for all-day events
  const isoToLocalDateTime = (isoString: string, isAllDay: boolean): string => {
    if (isAllDay) {
      // For all-day events, just extract the date part (YYYY-MM-DD)
      if (isoString.length >= 10) {
        return isoString.substring(0, 10);
      }
      // Fallback: parse and extract date
      const date = new Date(isoString);
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, "0");
      const day = String(date.getDate()).padStart(2, "0");
      return `${year}-${month}-${day}`;
    } else {
      // For regular events, convert to YYYY-MM-DDTHH:mm format
      // If it's already in the right format, return it
      if (isoString.length === 16 && isoString[10] === "T") {
        return isoString;
      }
      // Otherwise parse and convert
      const date = new Date(isoString);
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, "0");
      const day = String(date.getDate()).padStart(2, "0");
      const hours = String(date.getHours()).padStart(2, "0");
      const minutes = String(date.getMinutes()).padStart(2, "0");
      return `${year}-${month}-${day}T${hours}:${minutes}`;
    }
  };
  
  const [startDate, setStartDate] = useState(
    event ? isoToLocalDateTime(event.startDateTime, event.isAllDay) : ""
  );
  const [endDate, setEndDate] = useState(
    event?.endDateTime 
      ? isoToLocalDateTime(event.endDateTime, event.isAllDay)
      : startDate && !event?.isAllDay ? getDefaultEndDate(startDate) : ""
  );
  const [isAllDay, setIsAllDay] = useState(event?.isAllDay || false);
  const [location, setLocation] = useState(event?.location || "");
  const [categoryId, setCategoryId] = useState(event?.categoryId || "");
  const [participantIds, setParticipantIds] = useState<Set<string>>(
    new Set(event?.participantIds || [])
  );
  const [isRecurring, setIsRecurring] = useState(!!event?.recurringType);
  const [recurringType, setRecurringType] = useState<"DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY" | "">(
    (event?.recurringType as "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY") || ""
  );
  const [recurringInterval, setRecurringInterval] = useState(
    event?.recurringInterval?.toString() || "1"
  );
  const [recurringEndDate, setRecurringEndDate] = useState(
    event?.recurringEndDate ? new Date(event.recurringEndDate).toISOString().split("T")[0] : ""
  );
  const [recurringEndCount, setRecurringEndCount] = useState(
    event?.recurringEndCount?.toString() || ""
  );
  const [recurringEndType, setRecurringEndType] = useState<"never" | "date" | "count">(
    event?.recurringEndDate ? "date" : event?.recurringEndCount ? "count" : "never"
  );
  const [showRecurringTypeDropdown, setShowRecurringTypeDropdown] = useState(false);

  // Update end date when start date changes (only for new events, not when editing)
  const handleStartDateChange = (newStartDate: string) => {
    setStartDate(newStartDate);
    // Only auto-update end date if this is a new event (not editing)
    if (!event && newStartDate && !isAllDay) {
      setEndDate(getDefaultEndDate(newStartDate));
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !startDate) {
      return;
    }

    // Validate recurring fields
    if (isRecurring && !recurringType) {
      alert("Välj en typ för återkommande event.");
      return;
    }

    // For all-day events, the input type is "date" which gives us YYYY-MM-DD
    // We need to convert it to YYYY-MM-DDTHH:mm format (use 00:00 for start, 23:59 for end)
    let startDateTimeStr: string;
    let endDateTimeStr: string | null = null;
    
    if (isAllDay) {
      // All-day: convert date (YYYY-MM-DD) to datetime (YYYY-MM-DDTHH:mm)
      startDateTimeStr = `${startDate}T00:00`;
      if (endDate) {
        endDateTimeStr = `${endDate}T23:59`;
      }
    } else {
      // Regular event: already in YYYY-MM-DDTHH:mm format from datetime-local input
      startDateTimeStr = startDate;
      endDateTimeStr = endDate || null;
    }

    onSave({
      title: title.trim(),
      description: description.trim() || undefined,
      startDateTime: startDateTimeStr,
      endDateTime: endDateTimeStr,
      isAllDay,
      location: location.trim() || undefined,
      categoryId: categoryId || undefined,
      participantIds: Array.from(participantIds),
      recurringType: isRecurring && recurringType ? (recurringType as "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY") : null,
      recurringInterval: isRecurring && recurringType && recurringInterval ? parseInt(recurringInterval, 10) : null,
      recurringEndDate: isRecurring && recurringType && recurringEndType === "date" && recurringEndDate ? recurringEndDate : null,
      recurringEndCount: isRecurring && recurringType && recurringEndType === "count" && recurringEndCount ? parseInt(recurringEndCount, 10) : null,
    });
  };

  const toggleParticipant = (memberId: string) => {
    setParticipantIds((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(memberId)) {
        newSet.delete(memberId);
      } else {
        newSet.add(memberId);
      }
      return newSet;
    });
  };

  return (
    <section className="card">
      <h3 style={{ marginTop: 0 }}>{event ? "Redigera event" : "Nytt event"}</h3>
      <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
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
          />
        </div>

        <div>
          <label htmlFor="description" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
            Beskrivning
          </label>
          <textarea
            id="description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            style={{ width: "100%", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
          />
        </div>

        <div>
          <label style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
            <input
              type="checkbox"
              checked={isAllDay}
              onChange={(e) => {
                setIsAllDay(e.target.checked);
                // When switching to all-day, clear end date if it was set
                // When switching from all-day, set default end date if not set
                if (e.target.checked) {
                  // Switching to all-day - keep end date if it exists, but user can clear it
                } else {
                  // Switching from all-day - set default end date if not set
                  if (!endDate && startDate) {
                    setEndDate(getDefaultEndDate(startDate));
                  }
                }
              }}
            />
            <span>Hela dagen</span>
          </label>
        </div>

        <div>
          <label htmlFor="startDate" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
            Startdatum/tid *
          </label>
          <input
            id="startDate"
            type={isAllDay ? "date" : "datetime-local"}
            value={startDate}
            onChange={(e) => handleStartDateChange(e.target.value)}
            required
            style={{ width: "100%", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
          />
        </div>

        <div>
          <label htmlFor="endDate" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
            Slutdatum/tid
          </label>
          <input
            id="endDate"
            type={isAllDay ? "date" : "datetime-local"}
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            style={{ width: "100%", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
          />
        </div>

        <div>
          <label style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
            <input
              type="checkbox"
              checked={isRecurring}
              onChange={(e) => {
                setIsRecurring(e.target.checked);
                if (!e.target.checked) {
                  setRecurringType("");
                  setRecurringInterval("1");
                  setRecurringEndDate("");
                  setRecurringEndCount("");
                  setRecurringEndType("never");
                }
              }}
            />
            <span>Återkommande</span>
          </label>
        </div>

        {isRecurring && (
          <>
            <div style={{ position: "relative" }}>
              <label htmlFor="recurringType" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
                Typ
              </label>
              <button
                type="button"
                id="recurringType"
                onClick={() => setShowRecurringTypeDropdown(!showRecurringTypeDropdown)}
                style={{
                  width: "100%",
                  padding: "12px",
                  borderRadius: "6px",
                  border: "1px solid #ddd",
                  fontSize: "1rem",
                  backgroundColor: "white",
                  minHeight: "44px",
                  textAlign: "left",
                  cursor: "pointer",
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                }}
              >
                <span>
                  {recurringType === "DAILY" && "Dagligen"}
                  {recurringType === "WEEKLY" && "Veckovis"}
                  {recurringType === "MONTHLY" && "Månadsvis"}
                  {recurringType === "YEARLY" && "Årligen"}
                  {!recurringType && "Välj typ"}
                </span>
                <span style={{ fontSize: "0.8rem", color: "#6b6b6b" }}>▼</span>
              </button>
              {showRecurringTypeDropdown && (
                <>
                  <div
                    style={{
                      position: "fixed",
                      inset: 0,
                      zIndex: 100,
                    }}
                    onClick={() => setShowRecurringTypeDropdown(false)}
                  />
                  <div
                    style={{
                      position: "absolute",
                      top: "100%",
                      left: 0,
                      right: 0,
                      marginTop: "4px",
                      backgroundColor: "white",
                      border: "1px solid #ddd",
                      borderRadius: "6px",
                      boxShadow: "0 4px 12px rgba(0, 0, 0, 0.15)",
                      zIndex: 101,
                      maxHeight: "200px",
                      overflowY: "auto",
                    }}
                  >
                    <button
                      type="button"
                      onClick={() => {
                        setRecurringType("DAILY");
                        setShowRecurringTypeDropdown(false);
                      }}
                      style={{
                        width: "100%",
                        padding: "12px",
                        textAlign: "left",
                        border: "none",
                        backgroundColor: recurringType === "DAILY" ? "rgba(184, 230, 184, 0.2)" : "transparent",
                        cursor: "pointer",
                        fontSize: "1rem",
                        borderBottom: "1px solid #f0f0f0",
                      }}
                    >
                      Dagligen
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setRecurringType("WEEKLY");
                        setShowRecurringTypeDropdown(false);
                      }}
                      style={{
                        width: "100%",
                        padding: "12px",
                        textAlign: "left",
                        border: "none",
                        backgroundColor: recurringType === "WEEKLY" ? "rgba(184, 230, 184, 0.2)" : "transparent",
                        cursor: "pointer",
                        fontSize: "1rem",
                        borderBottom: "1px solid #f0f0f0",
                      }}
                    >
                      Veckovis
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setRecurringType("MONTHLY");
                        setShowRecurringTypeDropdown(false);
                      }}
                      style={{
                        width: "100%",
                        padding: "12px",
                        textAlign: "left",
                        border: "none",
                        backgroundColor: recurringType === "MONTHLY" ? "rgba(184, 230, 184, 0.2)" : "transparent",
                        cursor: "pointer",
                        fontSize: "1rem",
                        borderBottom: "1px solid #f0f0f0",
                      }}
                    >
                      Månadsvis
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setRecurringType("YEARLY");
                        setShowRecurringTypeDropdown(false);
                      }}
                      style={{
                        width: "100%",
                        padding: "12px",
                        textAlign: "left",
                        border: "none",
                        backgroundColor: recurringType === "YEARLY" ? "rgba(184, 230, 184, 0.2)" : "transparent",
                        cursor: "pointer",
                        fontSize: "1rem",
                      }}
                    >
                      Årligen
                    </button>
                  </div>
                </>
              )}
            </div>

            {recurringType && (
              <div>
                <label htmlFor="recurringInterval" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
                  Varje
                </label>
                <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                  <input
                    id="recurringInterval"
                    type="number"
                    min="1"
                    value={recurringInterval}
                    onChange={(e) => setRecurringInterval(e.target.value)}
                    required
                    style={{ width: "60px", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
                  />
                  <span>
                    {recurringType === "DAILY" && "dag(ar)"}
                    {recurringType === "WEEKLY" && "vecka(or)"}
                    {recurringType === "MONTHLY" && "månad(er)"}
                    {recurringType === "YEARLY" && "år"}
                  </span>
                </div>
              </div>
            )}

            {recurringType && (
              <div>
                <label style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>
                  Slutar
                </label>
                <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                  <label style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                    <input
                      type="radio"
                      name="recurringEndType"
                      checked={recurringEndType === "never"}
                      onChange={() => setRecurringEndType("never")}
                    />
                    <span>Aldrig</span>
                  </label>
                  <label style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                    <input
                      type="radio"
                      name="recurringEndType"
                      checked={recurringEndType === "date"}
                      onChange={() => setRecurringEndType("date")}
                    />
                    <span>På datum:</span>
                    <input
                      type="date"
                      value={recurringEndDate}
                      onChange={(e) => setRecurringEndDate(e.target.value)}
                      disabled={recurringEndType !== "date"}
                      style={{ padding: "4px", borderRadius: "4px", border: "1px solid #ddd" }}
                    />
                  </label>
                  <label style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                    <input
                      type="radio"
                      name="recurringEndType"
                      checked={recurringEndType === "count"}
                      onChange={() => setRecurringEndType("count")}
                    />
                    <span>Efter</span>
                    <input
                      type="number"
                      min="1"
                      value={recurringEndCount}
                      onChange={(e) => setRecurringEndCount(e.target.value)}
                      disabled={recurringEndType !== "count"}
                      style={{ width: "60px", padding: "4px", borderRadius: "4px", border: "1px solid #ddd" }}
                    />
                    <span>förekomster</span>
                  </label>
                </div>
              </div>
            )}
          </>
        )}

        <div>
          <label htmlFor="location" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
            Plats
          </label>
          <input
            id="location"
            type="text"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            style={{ width: "100%", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
          />
        </div>

        <div style={{ position: "relative" }}>
          <label htmlFor="category" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
            Kategori
          </label>
          <button
            type="button"
            id="category"
            onClick={() => setShowCategoryDropdown(!showCategoryDropdown)}
            style={{
              width: "100%",
              padding: "12px",
              borderRadius: "6px",
              border: "1px solid #ddd",
              fontSize: "1rem",
              backgroundColor: "white",
              minHeight: "44px",
              textAlign: "left",
              cursor: "pointer",
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
            }}
          >
            <span>
              {categoryId
                ? categories.find((c) => c.id === categoryId)?.name || "Ingen kategori"
                : "Ingen kategori"}
            </span>
            <span style={{ fontSize: "0.8rem", color: "#6b6b6b" }}>▼</span>
          </button>
          {showCategoryDropdown && (
            <>
              <div
                style={{
                  position: "fixed",
                  inset: 0,
                  zIndex: 100,
                }}
                onClick={() => setShowCategoryDropdown(false)}
              />
              <div
                style={{
                  position: "absolute",
                  top: "100%",
                  left: 0,
                  right: 0,
                  marginTop: "4px",
                  backgroundColor: "white",
                  border: "1px solid #ddd",
                  borderRadius: "6px",
                  boxShadow: "0 4px 12px rgba(0, 0, 0, 0.15)",
                  zIndex: 101,
                  maxHeight: "200px",
                  overflowY: "auto",
                }}
              >
                <button
                  type="button"
                  onClick={() => {
                    setCategoryId("");
                    setShowCategoryDropdown(false);
                  }}
                  style={{
                    width: "100%",
                    padding: "12px",
                    textAlign: "left",
                    border: "none",
                    backgroundColor: categoryId === "" ? "rgba(184, 230, 184, 0.2)" : "transparent",
                    cursor: "pointer",
                    fontSize: "1rem",
                    borderBottom: "1px solid #f0f0f0",
                  }}
                >
                  Ingen kategori
                </button>
                {categories.map((cat) => (
                  <button
                    key={cat.id}
                    type="button"
                    onClick={() => {
                      setCategoryId(cat.id);
                      setShowCategoryDropdown(false);
                    }}
                    style={{
                      width: "100%",
                      padding: "12px",
                      textAlign: "left",
                      border: "none",
                      backgroundColor: categoryId === cat.id ? "rgba(184, 230, 184, 0.2)" : "transparent",
                      cursor: "pointer",
                      fontSize: "1rem",
                      borderBottom: categories.indexOf(cat) < categories.length - 1 ? "1px solid #f0f0f0" : "none",
                    }}
                  >
                    {cat.name}
                  </button>
                ))}
              </div>
            </>
          )}
        </div>

        <div>
          <label style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>
            Deltagare
          </label>
          <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
            {members.map((member) => (
              <label key={member.id} style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <input
                  type="checkbox"
                  checked={participantIds.has(member.id)}
                  onChange={() => toggleParticipant(member.id)}
                />
                <span>{member.name}</span>
              </label>
            ))}
          </div>
        </div>

        <div style={{ display: "flex", gap: "8px", marginTop: "12px" }}>
          <button type="submit" className="button-primary">
            {event ? "Spara ändringar" : "Skapa event"}
          </button>
          <button type="button" onClick={onCancel} className="todo-action-button">
            Avbryt
          </button>
        </div>
      </form>
    </section>
  );
}

// Week View Component
type WeekViewProps = {
  events: CalendarEventResponse[];
  categories: CalendarEventCategoryResponse[];
  members: FamilyMemberResponse[];
  currentWeek: Date;
  onWeekChange: (date: Date) => void;
  onEventClick: (event: CalendarEventResponse) => void;
  onEventDelete: (eventId: string) => void;
};

function WeekView({ events, categories, members, currentWeek, onWeekChange, onEventClick, onEventDelete }: WeekViewProps) {
  // Get start of week (Monday)
  const getWeekStart = (date: Date): Date => {
    const d = new Date(date);
    const day = d.getDay();
    const diff = d.getDate() - day + (day === 0 ? -6 : 1); // Adjust when day is Sunday
    return new Date(d.setDate(diff));
  };

  const weekStart = getWeekStart(currentWeek);
  const weekDays: Date[] = [];
  for (let i = 0; i < 7; i++) {
    const day = new Date(weekStart);
    day.setDate(weekStart.getDate() + i);
    weekDays.push(day);
  }

  const hours = Array.from({ length: 24 }, (_, i) => i);

  // Get events for a specific day and hour range
  const getEventsForDay = (day: Date): CalendarEventResponse[] => {
    const dayStart = new Date(day);
    dayStart.setHours(0, 0, 0, 0);
    const dayEnd = new Date(day);
    dayEnd.setHours(23, 59, 59, 999);
    const dayDateStr = day.toISOString().split("T")[0];

    return events.filter(event => {
      if (event.isAllDay) {
        // For all-day events, check if this day falls within the event's date range
        const eventStartDateStr = event.startDateTime.substring(0, 10);
        if (!event.endDateTime) {
          // Single day event
          return eventStartDateStr === dayDateStr;
        }
        // Multi-day event - check if day is within range (inclusive)
        const eventEndDateStr = event.endDateTime.substring(0, 10);
        return dayDateStr >= eventStartDateStr && dayDateStr <= eventEndDateStr;
      }
      const eventStart = new Date(event.startDateTime);
      return eventStart >= dayStart && eventStart <= dayEnd;
    });
  };

  const getEventsForHour = (day: Date, hour: number): CalendarEventResponse[] => {
    const hourStart = new Date(day);
    hourStart.setHours(hour, 0, 0, 0);
    const hourEnd = new Date(day);
    hourEnd.setHours(hour, 59, 59, 999);

    return events.filter(event => {
      // Skip all-day events - they should only appear in the all-day section
      if (event.isAllDay) {
        return false;
      }
      const eventStart = new Date(event.startDateTime);
      return eventStart >= hourStart && eventStart <= hourEnd;
    });
  };

  const formatTime = (date: Date): string => {
    return date.toLocaleTimeString("sv-SE", { hour: "2-digit", minute: "2-digit" });
  };

  const navigateWeek = (direction: "prev" | "next") => {
    const newDate = new Date(currentWeek);
    newDate.setDate(newDate.getDate() + (direction === "next" ? 7 : -7));
    onWeekChange(newDate);
  };

  const weekRange = `${weekDays[0].toLocaleDateString("sv-SE", { day: "numeric", month: "short" })} - ${weekDays[6].toLocaleDateString("sv-SE", { day: "numeric", month: "short", year: "numeric" })}`;

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "12px" }}>
        <button
          type="button"
          onClick={() => navigateWeek("prev")}
          style={{ padding: "8px 12px", border: "1px solid #ddd", borderRadius: "6px", background: "white", cursor: "pointer" }}
        >
          ←
        </button>
        <div style={{ textAlign: "center", fontWeight: 600 }}>
          Vecka {weekRange}
        </div>
        <button
          type="button"
          onClick={() => navigateWeek("next")}
          style={{ padding: "8px 12px", border: "1px solid #ddd", borderRadius: "6px", background: "white", cursor: "pointer" }}
        >
          →
        </button>
      </div>

      {/* All-day events - show in a grid matching the week layout */}
      {(() => {
        const hasAllDayEvents = weekDays.some(day => getEventsForDay(day).filter(e => e.isAllDay).length > 0);
        if (!hasAllDayEvents) return null;
        
        return (
          <div style={{ 
            marginBottom: "12px", 
            border: "1px solid #ddd", 
            borderRadius: "8px", 
            overflow: "hidden",
            background: "white"
          }}>
            <div style={{ 
              display: "flex", 
              borderBottom: "1px solid #ddd",
              background: "#f5f5f5"
            }}>
              <div style={{ 
                width: "45px", 
                flexShrink: 0, 
                padding: "6px 2px",
                fontSize: "0.7rem",
                color: "#6b6b6b",
                fontWeight: 600,
                borderRight: "1px solid #ddd"
              }}>
                Heldag
              </div>
              {weekDays.map((day, dayIndex) => {
                const isToday = day.toDateString() === new Date().toDateString();
                const dayEvents = getEventsForDay(day).filter(e => e.isAllDay);
                return (
                  <div 
                    key={day.toISOString()} 
                    style={{ 
                      flex: 1, 
                      minWidth: 0, 
                      borderRight: dayIndex < 6 ? "1px solid #ddd" : "none",
                      padding: "4px 2px",
                      background: isToday ? "#b8e6b820" : "transparent"
                    }}
                  >
                    {dayEvents.map(event => {
                      const category = categories.find(c => c.id === event.categoryId);
                      return (
                        <div
                          key={event.id}
                          onClick={() => onEventClick(event)}
                          style={{
                            padding: "4px 6px",
                            marginBottom: "2px",
                            background: category?.color || "#b8e6b8",
                            borderRadius: "4px",
                            fontSize: "0.75rem",
                            color: "#2d5a2d",
                            fontWeight: 500,
                            cursor: "pointer",
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                            whiteSpace: "nowrap"
                          }}
                          title={event.title}
                        >
                          {event.title}
                        </div>
                      );
                    })}
                  </div>
                );
              })}
            </div>
          </div>
        );
      })()}

      {/* Hourly grid */}
      <div style={{ display: "flex", border: "1px solid #ddd", borderRadius: "8px", width: "100%", overflow: "hidden" }}>
        {/* Time column */}
        <div style={{ width: "45px", flexShrink: 0, borderRight: "1px solid #ddd" }}>
          {hours.map(hour => (
            <div
              key={hour}
              style={{
                height: "60px",
                padding: "4px 2px",
                fontSize: "0.7rem",
                color: "#6b6b6b",
                borderBottom: "1px solid #f0f0f0",
                display: "flex",
                alignItems: "flex-start"
              }}
            >
              {hour}:00
            </div>
          ))}
        </div>

        {/* Days columns */}
        {weekDays.map((day, dayIndex) => {
          const isToday = day.toDateString() === new Date().toDateString();
          return (
            <div key={day.toISOString()} style={{ flex: 1, minWidth: 0, borderRight: dayIndex < 6 ? "1px solid #ddd" : "none" }}>
              {/* Day header */}
              <div
                style={{
                  padding: "6px 2px",
                  textAlign: "center",
                  borderBottom: "1px solid #ddd",
                  background: isToday ? "#b8e6b820" : "transparent",
                  fontWeight: isToday ? 600 : 400
                }}
              >
                <div style={{ fontSize: "0.65rem", color: "#6b6b6b" }}>
                  {day.toLocaleDateString("sv-SE", { weekday: "short" })}
                </div>
                <div style={{ fontSize: "0.85rem", color: isToday ? "#2d5a2d" : "#3a3a3a" }}>
                  {day.getDate()}
                </div>
              </div>

              {/* Hour slots */}
              {hours.map(hour => {
                const hourEvents = getEventsForHour(day, hour);
                
                // Group events that start in this hour and calculate their positions
                // to handle overlapping events
                const eventsInHour = hourEvents
                  .map(event => {
                    const category = categories.find(c => c.id === event.categoryId);
                    const startTime = new Date(event.startDateTime);
                    const endTime = event.endDateTime ? new Date(event.endDateTime) : new Date(startTime.getTime() + 60 * 60 * 1000);
                    const eventStartHour = startTime.getHours();
                    const eventStartMin = startTime.getMinutes();
                    const eventEndHour = endTime.getHours();
                    const eventEndMin = endTime.getMinutes();
                    const durationMinutes = (eventEndHour * 60 + eventEndMin) - (eventStartHour * 60 + eventStartMin);
                    const heightPercent = (durationMinutes / 60) * 100;
                    
                    return {
                      event,
                      category,
                      startTime,
                      endTime,
                      eventStartHour,
                      eventStartMin,
                      eventEndHour,
                      eventEndMin,
                      durationMinutes,
                      heightPercent
                    };
                  })
                  .filter(e => e.eventStartHour === hour);
                
                // Calculate overlapping groups and assign positions
                const eventGroups: Array<Array<typeof eventsInHour[0]>> = [];
                eventsInHour.forEach(eventData => {
                  let placed = false;
                  for (const group of eventGroups) {
                    // Check if this event overlaps with any event in this group
                    const overlaps = group.some(existing => {
                      return !(eventData.endTime <= existing.startTime || eventData.startTime >= existing.endTime);
                    });
                    if (!overlaps) {
                      group.push(eventData);
                      placed = true;
                      break;
                    }
                  }
                  if (!placed) {
                    eventGroups.push([eventData]);
                  }
                });
                
                return (
                  <div
                    key={hour}
                    style={{
                      height: "60px",
                      borderBottom: "1px solid #f0f0f0",
                      position: "relative",
                      padding: "2px"
                    }}
                  >
                    {eventGroups.map((group, groupIndex) => {
                      const groupWidth = 100 / eventGroups.length;
                      const groupLeft = (groupIndex * 100) / eventGroups.length;
                      
                      return group.map((eventData, eventIndex) => {
                        const eventWidth = groupWidth / group.length;
                        const eventLeft = groupLeft + (eventIndex * eventWidth);
                        
                        return (
                          <div
                            key={eventData.event.id}
                            onClick={() => onEventClick(eventData.event)}
                            style={{
                              position: "absolute",
                              top: `${(eventData.eventStartMin / 60) * 100}%`,
                              left: `${eventLeft}%`,
                              width: `${eventWidth}%`,
                              height: `${Math.max(eventData.heightPercent, 20)}%`,
                              background: eventData.category?.color || "#b8e6b8",
                              borderRadius: "4px",
                              padding: "2px 4px",
                              fontSize: "0.7rem",
                              color: "#2d5a2d",
                              fontWeight: 500,
                              cursor: "pointer",
                              overflow: "hidden",
                              zIndex: 10,
                              marginLeft: eventIndex > 0 ? "1px" : "0",
                              marginRight: eventIndex < group.length - 1 ? "1px" : "0"
                            }}
                          >
                            {eventData.event.title}
                          </div>
                        );
                      });
                    })}
                  </div>
                );
              })}
            </div>
          );
        })}
      </div>
    </div>
  );
}

// Month View Component
type MonthViewProps = {
  events: CalendarEventResponse[];
  categories: CalendarEventCategoryResponse[];
  members: FamilyMemberResponse[];
  currentMonth: Date;
  onMonthChange: (date: Date) => void;
  onEventClick: (event: CalendarEventResponse) => void;
  onEventDelete: (eventId: string) => void;
};

function MonthView({ events, categories, members, currentMonth, onMonthChange, onEventClick, onEventDelete }: MonthViewProps) {
  const year = currentMonth.getFullYear();
  const month = currentMonth.getMonth();

  // Get first day of month and how many days
  const firstDay = new Date(year, month, 1);
  const lastDay = new Date(year, month + 1, 0);
  const daysInMonth = lastDay.getDate();
  const startingDayOfWeek = (firstDay.getDay() + 6) % 7; // Monday = 0

  // Get events for a specific day
  const getEventsForDay = (day: number): CalendarEventResponse[] => {
    const date = new Date(year, month, day);
    const dayStart = new Date(date);
    dayStart.setHours(0, 0, 0, 0);
    const dayEnd = new Date(date);
    dayEnd.setHours(23, 59, 59, 999);
    
    // Format day as YYYY-MM-DD for comparison with all-day events
    const dayDateStr = `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;

    return events.filter(event => {
      if (event.isAllDay) {
        // For all-day events, check if this day falls within the event's date range
        const eventStartDateStr = event.startDateTime.substring(0, 10);
        if (!event.endDateTime) {
          // Single day event
          return eventStartDateStr === dayDateStr;
        }
        // Multi-day event - check if day is within range (inclusive)
        const eventEndDateStr = event.endDateTime.substring(0, 10);
        return dayDateStr >= eventStartDateStr && dayDateStr <= eventEndDateStr;
      }
      const eventStart = new Date(event.startDateTime);
      return eventStart >= dayStart && eventStart <= dayEnd;
    });
  };

  const navigateMonth = (direction: "prev" | "next") => {
    const newDate = new Date(currentMonth);
    newDate.setMonth(newDate.getMonth() + (direction === "next" ? 1 : -1));
    onMonthChange(newDate);
  };

  const monthName = currentMonth.toLocaleDateString("sv-SE", { month: "long", year: "numeric" });
  const weekDays = ["Mån", "Tis", "Ons", "Tors", "Fre", "Lör", "Sön"];

  const today = new Date();
  const isToday = (day: number): boolean => {
    return year === today.getFullYear() && month === today.getMonth() && day === today.getDate();
  };

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "12px" }}>
        <button
          type="button"
          onClick={() => navigateMonth("prev")}
          style={{ padding: "8px 12px", border: "1px solid #ddd", borderRadius: "6px", background: "white", cursor: "pointer" }}
        >
          ←
        </button>
        <div style={{ textAlign: "center", fontWeight: 600, textTransform: "capitalize" }}>
          {monthName}
        </div>
        <button
          type="button"
          onClick={() => navigateMonth("next")}
          style={{ padding: "8px 12px", border: "1px solid #ddd", borderRadius: "6px", background: "white", cursor: "pointer" }}
        >
          →
        </button>
      </div>

      <div style={{ border: "1px solid #ddd", borderRadius: "8px", overflow: "hidden" }}>
        {/* Weekday headers */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", background: "#f5f5f5" }}>
          {weekDays.map(day => (
            <div
              key={day}
              style={{
                padding: "8px",
                textAlign: "center",
                fontSize: "0.75rem",
                fontWeight: 600,
                color: "#6b6b6b",
                borderRight: "1px solid #ddd"
              }}
            >
              {day}
            </div>
          ))}
        </div>

        {/* Calendar grid */}
        {/* Calculate number of rows needed (including empty cells) */}
        {(() => {
          const totalCells = startingDayOfWeek + daysInMonth;
          const numRows = Math.ceil(totalCells / 7);
          const rowHeight = 70; // Fixed height per row in pixels
          return (
            <div style={{ 
              display: "grid", 
              gridTemplateColumns: "repeat(7, 1fr)",
              gridTemplateRows: `repeat(${numRows}, ${rowHeight}px)`,
              height: `${numRows * rowHeight}px`
            }}>
          {/* Empty cells for days before month starts */}
          {Array.from({ length: startingDayOfWeek }).map((_, i) => (
            <div
              key={`empty-${i}`}
              style={{
                borderRight: "1px solid #ddd",
                borderBottom: "1px solid #ddd",
                background: "#fafafa",
                overflow: "hidden"
              }}
            />
          ))}

          {/* Days of month */}
          {Array.from({ length: daysInMonth }).map((_, i) => {
            const day = i + 1;
            const dayEvents = getEventsForDay(day);
            const todayClass = isToday(day);

            return (
              <div
                key={day}
                style={{
                  borderRight: (startingDayOfWeek + day) % 7 !== 0 ? "1px solid #ddd" : "none",
                  borderBottom: "1px solid #ddd",
                  padding: "2px",
                  background: todayClass ? "#b8e6b820" : "white",
                  position: "relative",
                  display: "flex",
                  flexDirection: "column",
                  overflow: "hidden",
                  height: "100%"
                }}
              >
                <div
                  style={{
                    fontSize: "0.65rem",
                    fontWeight: todayClass ? 600 : 400,
                    color: todayClass ? "#2d5a2d" : "#3a3a3a",
                    marginBottom: "2px",
                    lineHeight: "1.1",
                    flexShrink: 0,
                    whiteSpace: "nowrap",
                    overflow: "hidden"
                  }}
                >
                  {day}
                </div>
                <div style={{ 
                  display: "flex", 
                  flexDirection: "column", 
                  gap: "1px", 
                  flex: 1, 
                  overflow: "hidden",
                  minHeight: 0,
                  width: "100%"
                }}>
                  {dayEvents.slice(0, 3).map(event => {
                    const category = categories.find(c => c.id === event.categoryId);
                    // Truncate long titles
                    const truncatedTitle = event.title.length > 15 
                      ? event.title.substring(0, 15) + "..." 
                      : event.title;
                    return (
                      <div
                        key={event.id}
                        onClick={() => onEventClick(event)}
                        style={{
                          padding: "1px 2px",
                          background: category?.color || "#b8e6b8",
                          borderRadius: "2px",
                          fontSize: "0.5rem",
                          color: "#2d5a2d",
                          fontWeight: 500,
                          cursor: "pointer",
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          whiteSpace: "nowrap",
                          lineHeight: "1.1",
                          flexShrink: 0,
                          width: "100%",
                          maxWidth: "100%"
                        }}
                        title={event.title} // Show full title on hover
                      >
                        {truncatedTitle}
                      </div>
                    );
                  })}
                  {dayEvents.length > 3 && (
                    <div style={{ 
                      fontSize: "0.5rem", 
                      color: "#6b6b6b", 
                      padding: "1px 2px", 
                      lineHeight: "1.1",
                      flexShrink: 0,
                      whiteSpace: "nowrap"
                    }}>
                      +{dayEvents.length - 3}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
            </div>
          );
        })()}
      </div>
    </div>
  );
}

// Category Manager Component
type CategoryManagerProps = {
  categories: CalendarEventCategoryResponse[];
  onClose: () => void;
  onUpdate: () => Promise<void>;
};

const CATEGORY_COLORS = [
  { value: "#b8e6b8", label: "Grön" },
  { value: "#b8d8f0", label: "Blå" },
  { value: "#f5c2d1", label: "Rosa" },
  { value: "#d8c8f0", label: "Lila" },
  { value: "#f5d8a8", label: "Orange" },
  { value: "#ffb8d8", label: "Rosa (ljus)" },
  { value: "#d8b8ff", label: "Lila (ljus)" },
  { value: "#b8d8ff", label: "Blå (ljus)" },
];

function CategoryManager({ categories, onClose, onUpdate }: CategoryManagerProps) {
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingCategory, setEditingCategory] = useState<CalendarEventCategoryResponse | null>(null);
  const [categoryName, setCategoryName] = useState("");
  const [categoryColor, setCategoryColor] = useState(CATEGORY_COLORS[0].value);
  const [error, setError] = useState<string | null>(null);

  const handleCreateCategory = async () => {
    if (!categoryName.trim()) {
      setError("Kategorinamn krävs.");
      return;
    }

    try {
      await createCalendarCategory(categoryName.trim(), categoryColor);
      await onUpdate();
      setCategoryName("");
      setCategoryColor(CATEGORY_COLORS[0].value);
      setShowCreateForm(false);
      setError(null);
    } catch (e) {
      setError("Kunde inte skapa kategori.");
      console.error("Error creating category:", e);
    }
  };

  const handleUpdateCategory = async () => {
    if (!editingCategory || !categoryName.trim()) {
      setError("Kategorinamn krävs.");
      return;
    }

    try {
      await updateCalendarCategory(editingCategory.id, categoryName.trim(), categoryColor);
      await onUpdate();
      setEditingCategory(null);
      setCategoryName("");
      setCategoryColor(CATEGORY_COLORS[0].value);
      setError(null);
    } catch (e) {
      setError("Kunde inte uppdatera kategori.");
      console.error("Error updating category:", e);
    }
  };

  const handleDeleteCategory = async (categoryId: string) => {
    if (!confirm("Är du säker på att du vill ta bort denna kategori? Events som använder denna kategori kommer inte längre ha en kategori.")) {
      return;
    }

    try {
      await deleteCalendarCategory(categoryId);
      await onUpdate();
    } catch (e) {
      setError("Kunde inte ta bort kategori.");
      console.error("Error deleting category:", e);
    }
  };

  const startEdit = (category: CalendarEventCategoryResponse) => {
    setEditingCategory(category);
    setCategoryName(category.name);
    setCategoryColor(category.color);
    setShowCreateForm(false);
    setError(null);
  };

  const cancelEdit = () => {
    setEditingCategory(null);
    setShowCreateForm(false);
    setCategoryName("");
    setCategoryColor(CATEGORY_COLORS[0].value);
    setError(null);
  };

  return (
    <section className="card">
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "16px" }}>
        <h3 style={{ margin: 0 }}>Hantera kategorier</h3>
        <button
          type="button"
          className="todo-action-button"
          onClick={onClose}
        >
          Stäng
        </button>
      </div>

      {error && <p className="error-text">{error}</p>}

      {!showCreateForm && !editingCategory && (
        <>
          <div style={{ display: "flex", flexDirection: "column", gap: "8px", marginBottom: "16px" }}>
            {categories.map((category) => (
              <div
                key={category.id}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "12px",
                  padding: "12px",
                  background: "rgba(240, 240, 240, 0.5)",
                  borderRadius: "8px",
                  border: `2px solid ${category.color}`
                }}
              >
                <div
                  style={{
                    width: "24px",
                    height: "24px",
                    borderRadius: "6px",
                    background: category.color,
                    flexShrink: 0
                  }}
                />
                <div style={{ flex: 1, fontWeight: 500 }}>{category.name}</div>
                <button
                  type="button"
                  className="todo-action-button"
                  onClick={() => startEdit(category)}
                  style={{ fontSize: "0.8rem", padding: "4px 8px" }}
                >
                  Redigera
                </button>
                <button
                  type="button"
                  className="delete-button"
                  onClick={() => void handleDeleteCategory(category.id)}
                  style={{ fontSize: "0.8rem", padding: "4px 8px" }}
                >
                  Ta bort
                </button>
              </div>
            ))}
            {categories.length === 0 && (
              <p className="placeholder-text" style={{ textAlign: "center", padding: "16px" }}>
                Inga kategorier än. Skapa din första kategori!
              </p>
            )}
          </div>

          <button
            type="button"
            className="button-primary"
            onClick={() => {
              setShowCreateForm(true);
              setError(null);
            }}
          >
            + Ny kategori
          </button>
        </>
      )}

      {(showCreateForm || editingCategory) && (
        <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
          <h4 style={{ margin: 0 }}>{editingCategory ? "Redigera kategori" : "Ny kategori"}</h4>

          <div>
            <label htmlFor="categoryName" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
              Namn *
            </label>
            <input
              id="categoryName"
              type="text"
              value={categoryName}
              onChange={(e) => setCategoryName(e.target.value)}
              placeholder="T.ex. Skola, Idrott, Familj"
              required
              style={{ width: "100%", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
            />
          </div>

          <div>
            <label style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>
              Färg
            </label>
            <div style={{ display: "flex", flexWrap: "wrap", gap: "8px" }}>
              {CATEGORY_COLORS.map((color) => (
                <button
                  key={color.value}
                  type="button"
                  onClick={() => setCategoryColor(color.value)}
                  style={{
                    width: "44px",
                    height: "44px",
                    borderRadius: "8px",
                    border: categoryColor === color.value ? "3px solid #2d5a2d" : "2px solid #ddd",
                    background: color.value,
                    cursor: "pointer",
                    boxShadow: categoryColor === color.value ? "0 2px 8px rgba(0, 0, 0, 0.2)" : "0 1px 3px rgba(0, 0, 0, 0.1)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontSize: "1.2rem"
                  }}
                  title={color.label}
                >
                  {categoryColor === color.value && "✓"}
                </button>
              ))}
            </div>
          </div>

          <div style={{ display: "flex", gap: "8px", marginTop: "8px" }}>
            <button
              type="button"
              className="button-primary"
              onClick={editingCategory ? handleUpdateCategory : handleCreateCategory}
            >
              {editingCategory ? "Spara ändringar" : "Skapa kategori"}
            </button>
            <button
              type="button"
              className="todo-action-button"
              onClick={cancelEdit}
            >
              Avbryt
            </button>
          </div>
        </div>
      )}
    </section>
  );
}


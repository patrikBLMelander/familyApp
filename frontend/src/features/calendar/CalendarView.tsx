import { useEffect, useState } from "react";
import {
  fetchCalendarEvents,
  createCalendarEvent,
  updateCalendarEvent,
  deleteCalendarEvent,
  CalendarEventResponse,
  fetchCalendarCategories,
  CalendarEventCategoryResponse,
  fetchTasksForToday,
  fetchTasksForDate,
  toggleTaskCompletion,
  CalendarTaskWithCompletionResponse,
  getTaskCompletionsForMember,
} from "../../shared/api/calendar";
import { fetchAllFamilyMembers, FamilyMemberResponse, getMemberByDeviceToken } from "../../shared/api/familyMembers";
import { EventForm } from "./components/EventForm";
import { WeekView } from "./components/WeekView";
import { MonthView } from "./components/MonthView";
import { CategoryManager } from "./components/CategoryManager";
import { formatDateTime, formatDateTimeRange } from "./utils/dateFormatters";
import { EventFormData } from "./types/eventForm";
import { MAX_RECURRING_DAYS } from "./constants";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers";

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
  const [showTasksOnly, setShowTasksOnly] = useState(false);
  const [showAllMembers, setShowAllMembers] = useState(false);
  const [tasksWithCompletion, setTasksWithCompletion] = useState<CalendarTaskWithCompletionResponse[]>([]);
  const [tasksByMember, setTasksByMember] = useState<Map<string, CalendarTaskWithCompletionResponse[]>>(new Map());
  const [currentMemberId, setCurrentMemberId] = useState<string | null>(null);
  const [selectedDate, setSelectedDate] = useState<Date>(new Date());
  const [showQuickAdd, setShowQuickAdd] = useState(false);
  const [quickAddTitle, setQuickAddTitle] = useState("");
  const [initialStartDate, setInitialStartDate] = useState<string | null>(null);
  const [currentUserRole, setCurrentUserRole] = useState<"CHILD" | "ASSISTANT" | "PARENT" | null>(null);

  useEffect(() => {
    void loadData();
    void loadCurrentMember();
  }, []);

  // Reload events when view type changes to optimize data fetching
  useEffect(() => {
    void loadData();
  }, [viewType, currentWeek, currentMonth]);

  useEffect(() => {
    if (showTasksOnly && viewType === "rolling") {
      if (showAllMembers) {
        void loadTasksForAllMembers();
      } else if (currentMemberId) {
        void loadTasks();
      }
    }
  }, [showTasksOnly, viewType, currentMemberId, selectedDate, showAllMembers]);

  const loadCurrentMember = async () => {
    const deviceToken = localStorage.getItem("deviceToken");
    if (deviceToken) {
      try {
        const member = await getMemberByDeviceToken(deviceToken);
        setCurrentMemberId(member.id);
        setCurrentUserRole(member.role);
      } catch (e) {
        console.error("Error loading current member:", e);
      }
    }
  };

  const loadTasks = async () => {
    if (!currentMemberId) return;
    try {
      const tasks = await fetchTasksForDate(currentMemberId, selectedDate);
      // Sort tasks: required first, then by title
      const sortedTasks = [...tasks].sort((a, b) => {
        if (a.event.isRequired !== b.event.isRequired) {
          return a.event.isRequired ? -1 : 1; // Required first
        }
        return a.event.title.localeCompare(b.event.title);
      });
      setTasksWithCompletion(sortedTasks);
    } catch (e) {
      console.error("Error loading tasks:", e);
      setTasksWithCompletion([]);
    }
  };

  const loadTasksForAllMembers = async () => {
    try {
      const allTasks = new Map<string, CalendarTaskWithCompletionResponse[]>();
      
      // Optimize: Fetch events once for the date (shared across all members)
      const dateStart = new Date(selectedDate);
      dateStart.setHours(0, 0, 0, 0);
      const dateEnd = new Date(selectedDate);
      dateEnd.setHours(23, 59, 59, 999);
      
      // Fetch events and completions in parallel
      const [events, ...completionPromises] = await Promise.all([
        fetchCalendarEvents(dateStart, dateEnd),
        ...members.map(member => getTaskCompletionsForMember(member.id))
      ]);
      
      // Get all completions
      const allCompletions = await Promise.all(completionPromises);
      const dateStr = selectedDate.toISOString().split("T")[0]; // YYYY-MM-DD
      
      // Process tasks for each member in parallel
      const memberTasksPromises = members.map(async (member, index) => {
        try {
          // Filter to only tasks where member is a participant
          const taskEvents = events.filter(event => 
            event.isTask && event.participantIds.includes(member.id)
          );
          
          // Get completions for this member for the selected date
          const memberCompletions = allCompletions[index];
          const completionMap = new Map<string, CalendarEventTaskCompletionResponse>();
          memberCompletions.forEach(completion => {
            if (completion.occurrenceDate === dateStr) {
              completionMap.set(completion.eventId, completion);
            }
          });
          
          // Map events to tasks with completion status
          const tasks = taskEvents.map(event => ({
            event,
            completed: completionMap.has(event.id),
          }));
          
          // Sort tasks: required first, then by title
          const sortedTasks = [...tasks].sort((a, b) => {
            if (a.event.isRequired !== b.event.isRequired) {
              return a.event.isRequired ? -1 : 1; // Required first
            }
            return a.event.title.localeCompare(b.event.title);
          });
          
          return { memberId: member.id, tasks: sortedTasks };
        } catch (e) {
          console.error(`Error processing tasks for member ${member.id}:`, e);
          return { memberId: member.id, tasks: [] };
        }
      });
      
      // Wait for all members to be processed
      const memberTasksResults = await Promise.all(memberTasksPromises);
      
      // Build the map
      memberTasksResults.forEach(({ memberId, tasks }) => {
        if (tasks.length > 0) {
          allTasks.set(memberId, tasks);
        }
      });
      
      setTasksByMember(allTasks);
    } catch (e) {
      console.error("Error loading tasks for all members:", e);
      setTasksByMember(new Map());
    }
  };

  const handleToggleTask = async (eventId: string, memberId?: string) => {
    const targetMemberId = memberId || currentMemberId;
    if (!targetMemberId) return;
    
    // Optimistic update
    if (showAllMembers) {
      setTasksByMember((prev) => {
        const newMap = new Map(prev);
        const memberTasks = newMap.get(targetMemberId) || [];
        newMap.set(
          targetMemberId,
          memberTasks.map((task) =>
            task.event.id === eventId
              ? { ...task, completed: !task.completed }
              : task
          )
        );
        return newMap;
      });
    } else {
      setTasksWithCompletion((prev) =>
        prev.map((task) =>
          task.event.id === eventId
            ? { ...task, completed: !task.completed }
            : task
        )
      );
    }

    try {
      await toggleTaskCompletion(eventId, targetMemberId, selectedDate);
      // Reload tasks to get updated state
      if (showAllMembers) {
        await loadTasksForAllMembers();
      } else {
        await loadTasks();
      }
    } catch (e) {
      console.error("Error toggling task:", e);
      // Reload on error to revert
      if (showAllMembers) {
        await loadTasksForAllMembers();
      } else {
        await loadTasks();
      }
    }
  };

  const handleQuickAddTask = async () => {
    if (!currentMemberId || !quickAddTitle.trim()) return;

    try {
      // Format date as YYYY-MM-DD
      const year = selectedDate.getFullYear();
      const month = String(selectedDate.getMonth() + 1).padStart(2, "0");
      const day = String(selectedDate.getDate()).padStart(2, "0");
      const dateStr = `${year}-${month}-${day}`;
      
      // Create all-day task with defaults
      await createCalendarEvent(
        quickAddTitle.trim(),
        `${dateStr}T00:00`, // startDateTime
        null, // endDateTime (null for all-day)
        true, // isAllDay
        undefined, // description
        undefined, // categoryId
        undefined, // location
        [currentMemberId], // participantIds
        null, // recurringType
        null, // recurringInterval
        null, // recurringEndDate
        null, // recurringEndCount
        true, // isTask
        1, // xpPoints
        true // isRequired
      );
      
      // Clear input and reload tasks
      setQuickAddTitle("");
      setShowQuickAdd(false);
      if (showAllMembers) {
        await loadTasksForAllMembers();
      } else {
        await loadTasks();
      }
    } catch (e) {
      console.error("Error creating quick task:", e);
      setError("Kunde inte skapa task.");
    }
  };

  const loadData = async () => {
    try {
      setLoading(true);
      
      // Optimize: For rolling view, only fetch events from today up to 30 days ahead
      // For week/month view, fetch a wider range to allow navigation
      let startDate: Date | undefined;
      let endDate: Date | undefined;
      
      if (viewType === "rolling") {
        // Rolling view: today to 30 days ahead
        startDate = new Date();
        startDate.setHours(0, 0, 0, 0);
        endDate = new Date();
        endDate.setDate(endDate.getDate() + 30);
        endDate.setHours(23, 59, 59, 999);
      } else if (viewType === "week") {
        // Week view: 7 days before current week to 7 days after (3 weeks total)
        const weekStart = new Date(currentWeek);
        const day = weekStart.getDay();
        const diff = weekStart.getDate() - day + (day === 0 ? -6 : 1);
        weekStart.setDate(diff);
        weekStart.setHours(0, 0, 0, 0);
        
        startDate = new Date(weekStart);
        startDate.setDate(startDate.getDate() - 7);
        
        endDate = new Date(weekStart);
        endDate.setDate(endDate.getDate() + 14); // 7 days before + 7 days week + 7 days after
        endDate.setHours(23, 59, 59, 999);
      } else if (viewType === "month") {
        // Month view: first day of current month to last day (with some buffer)
        const year = currentMonth.getFullYear();
        const month = currentMonth.getMonth();
        startDate = new Date(year, month, 1);
        startDate.setHours(0, 0, 0, 0);
        
        endDate = new Date(year, month + 1, 0); // Last day of month
        endDate.setHours(23, 59, 59, 999);
      } else {
        // Default: if viewType is not set yet, use rolling view logic (today to 30 days)
        startDate = new Date();
        startDate.setHours(0, 0, 0, 0);
        endDate = new Date();
        endDate.setDate(endDate.getDate() + 30);
        endDate.setHours(23, 59, 59, 999);
      }
      
      const [eventsData, categoriesData, membersData] = await Promise.all([
        fetchCalendarEvents(startDate, endDate),
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

  const handleCreateEvent = async (eventData: EventFormData) => {
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
        eventData.recurringEndCount,
        eventData.isTask,
        eventData.xpPoints,
        eventData.isRequired
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
    eventData: EventFormData
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
        eventData.recurringEndCount,
        eventData.isTask,
        eventData.xpPoints,
        eventData.isRequired
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
      setEditingEvent(null);
      setShowCreateForm(false);
    } catch (e) {
      setError("Kunde inte ta bort event.");
      console.error("Error deleting event:", e);
    }
  };


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
  };

  // Filter events based on view type and task/event toggle
  const now = new Date();
  const todayStr = now.toISOString().split("T")[0]; // YYYY-MM-DD format
  
  // Filter by task/event type based on showTasksOnly (applies to all views)
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
  if (viewType === "rolling") {
    filteredEvents = filteredEvents.filter(event => {
      const isFutureEvent = event.isAllDay
        ? getAllDayEventDates(event).some(dateStr => dateStr >= todayStr)
        : new Date(event.startDateTime) >= now;
      return isFutureEvent;
    });
  }

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
        <h2 className="view-title" style={{ margin: 0, flex: 1 }}>Kalender</h2>
        {!showCreateForm && !editingEvent && (
          <div style={{ display: "flex", gap: "8px" }}>
            {currentUserRole === "PARENT" && (
            <button
              type="button"
              className="todo-action-button"
              onClick={() => setShowCategoryManager(true)}
              style={{ fontSize: "0.85rem", padding: "8px 12px" }}
            >
              Kategorier
            </button>
            )}
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

      {/* View type toggle and filters - only show when not in form */}
      {!showCreateForm && !editingEvent && (
        <div style={{ 
          display: "flex", 
          flexDirection: "column",
          gap: "8px",
          marginBottom: "16px"
        }}>
          <div style={{ 
            display: "flex", 
            gap: "4px", 
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
          {/* Task/Event toggle - show in all views */}
          <div style={{ 
            display: "flex", 
            gap: "4px", 
            padding: "4px",
            background: "rgba(255, 255, 255, 0.6)",
            borderRadius: "8px",
            border: "1px solid rgba(220, 210, 200, 0.3)"
          }}>
            <button
              type="button"
              onClick={() => setShowTasksOnly(false)}
              style={{
                flex: 1,
                padding: "8px 12px",
                borderRadius: "6px",
                border: "none",
                background: !showTasksOnly ? "#b8e6b8" : "transparent",
                color: !showTasksOnly ? "#2d5a2d" : "#6b6b6b",
                fontWeight: !showTasksOnly ? 600 : 400,
                fontSize: "0.85rem",
                cursor: "pointer",
                transition: "all 0.2s ease"
              }}
            >
              Schema
            </button>
            <button
              type="button"
              onClick={() => setShowTasksOnly(true)}
              style={{
                flex: 1,
                padding: "8px 12px",
                borderRadius: "6px",
                border: "none",
                background: showTasksOnly ? "#b8e6b8" : "transparent",
                color: showTasksOnly ? "#2d5a2d" : "#6b6b6b",
                fontWeight: showTasksOnly ? 600 : 400,
                fontSize: "0.85rem",
                cursor: "pointer",
                transition: "all 0.2s ease"
              }}
            >
              Dagens Att Göra
            </button>
          </div>
          {showTasksOnly && (
            <div
              style={{
                display: "flex",
                gap: "4px",
                padding: "4px",
                background: "#f5f5f5",
                borderRadius: "8px",
                marginBottom: "12px"
              }}
            >
              <button
                type="button"
                onClick={() => setShowAllMembers(false)}
                style={{
                  flex: 1,
                  padding: "8px 12px",
                  borderRadius: "6px",
                  border: "none",
                  background: !showAllMembers ? "#b8e6b8" : "transparent",
                  color: !showAllMembers ? "#2d5a2d" : "#6b6b6b",
                  fontWeight: !showAllMembers ? 600 : 400,
                  fontSize: "0.85rem",
                  cursor: "pointer",
                  transition: "all 0.2s ease"
                }}
              >
                Endast mig
              </button>
              <button
                type="button"
                onClick={() => setShowAllMembers(true)}
                style={{
                  flex: 1,
                  padding: "8px 12px",
                  borderRadius: "6px",
                  border: "none",
                  background: showAllMembers ? "#b8e6b8" : "transparent",
                  color: showAllMembers ? "#2d5a2d" : "#6b6b6b",
                  fontWeight: showAllMembers ? 600 : 400,
                  fontSize: "0.85rem",
                  cursor: "pointer",
                  transition: "all 0.2s ease"
                }}
              >
                Alla familjemedlemmar
              </button>
            </div>
          )}
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
                                              onChange={() => void handleToggleTask(task.event.id, member.id)}
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
                                  onChange={() => void handleToggleTask(task.event.id)}
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
          )}

          {viewType === "week" && (
            <WeekView
              events={filteredEvents}
              categories={categories}
              members={members}
              currentWeek={currentWeek}
              onWeekChange={setCurrentWeek}
              onEventClick={(event) => setEditingEvent(event)}
              onEventDelete={handleDeleteEvent}
              onDayClick={(date, hour) => {
                // Format date and hour as YYYY-MM-DDTHH:mm
                const year = date.getFullYear();
                const month = String(date.getMonth() + 1).padStart(2, "0");
                const day = String(date.getDate()).padStart(2, "0");
                const hourStr = hour !== undefined ? String(hour).padStart(2, "0") : "00";
                const dateStr = `${year}-${month}-${day}T${hourStr}:00`;
                setInitialStartDate(dateStr);
                setEditingEvent(null);
                setShowCreateForm(true);
              }}
              showTasksOnly={showTasksOnly}
            />
          )}

          {viewType === "month" && (
            <MonthView
              events={filteredEvents}
              categories={categories}
              members={members}
              currentMonth={currentMonth}
              onMonthChange={setCurrentMonth}
              onEventClick={(event) => setEditingEvent(event)}
              onEventDelete={handleDeleteEvent}
              onDayClick={(date) => {
                // Format date as YYYY-MM-DD (all-day event)
                const year = date.getFullYear();
                const month = String(date.getMonth() + 1).padStart(2, "0");
                const day = String(date.getDate()).padStart(2, "0");
                const dateStr = `${year}-${month}-${day}`;
                setInitialStartDate(dateStr);
                setEditingEvent(null);
                setShowCreateForm(true);
              }}
            />
          )}
        </>
      )}

      {(showCreateForm || editingEvent) && (
        <EventForm
          event={editingEvent}
          initialStartDate={initialStartDate}
          categories={categories}
          members={members}
          currentUserRole={currentUserRole}
          currentUserId={currentMemberId}
          onSave={(eventData) => {
            if (editingEvent) {
              void handleUpdateEvent(editingEvent.id, eventData);
            } else {
              void handleCreateEvent(eventData);
            }
          }}
          onDelete={editingEvent ? () => void handleDeleteEvent(editingEvent.id) : undefined}
          onCancel={() => {
            setShowCreateForm(false);
            setEditingEvent(null);
            setInitialStartDate(null);
          }}
        />
      )}

      {showCategoryManager && currentUserRole === "PARENT" && (
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

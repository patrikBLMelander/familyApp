import { useEffect, useState, useMemo } from "react";
import {
  CalendarEventResponse,
} from "../../shared/api/calendar";
import { EventForm } from "./components/EventForm";
import { WeekView } from "./components/WeekView";
import { MonthView } from "./components/MonthView";
import { CategoryManager } from "./components/CategoryManager";
import { RollingView } from "./components/RollingView";
import { useCalendarData } from "./hooks/useCalendarData";
import { useCalendarEvents } from "./hooks/useCalendarEvents";
import { formatDateForEventForm } from "./utils/dateFormatters";
import { CALENDAR_VIEW_TYPES, CalendarViewType } from "./constants";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers";

type CalendarContainerProps = {
  onNavigate?: (view: ViewKey) => void;
};

export function CalendarContainer({ onNavigate }: CalendarContainerProps) {
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingEvent, setEditingEvent] = useState<CalendarEventResponse | null>(null);
  const [viewType, setViewType] = useState<CalendarViewType>(CALENDAR_VIEW_TYPES.ROLLING);
  const [currentWeek, setCurrentWeek] = useState(new Date());
  const [currentMonth, setCurrentMonth] = useState(new Date());
  const [showCategoryManager, setShowCategoryManager] = useState(false);
  const [showTasksOnly, setShowTasksOnly] = useState(false);
  const [showAllMembers, setShowAllMembers] = useState(false);
  const [currentMemberId, setCurrentMemberId] = useState<string | null>(null);
  const [selectedDate, setSelectedDate] = useState<Date>(new Date());
  const [showQuickAdd, setShowQuickAdd] = useState(false);
  const [quickAddTitle, setQuickAddTitle] = useState("");
  const [initialStartDate, setInitialStartDate] = useState<string | null>(null);

  // Use data hook
  const {
    events,
    categories,
    members,
    loading,
    error,
    tasksWithCompletion,
    tasksByMember,
    currentUserRole,
    loadData,
    loadCategories,
    loadTasks,
    loadTasksForAllMembers,
    loadCurrentMember,
    handleToggleTask,
    setError,
  } = useCalendarData(
    viewType,
    currentWeek,
    currentMonth,
    selectedDate,
    showTasksOnly,
    showAllMembers,
    currentMemberId
  );

  // Use events hook
  const {
    handleCreateEvent,
    handleUpdateEvent,
    handleDeleteEvent,
    handleQuickAdd,
  } = useCalendarEvents(
    loadData,
    setError,
    setShowCreateForm,
    setEditingEvent,
    currentMemberId,
    selectedDate,
    showAllMembers,
    () => loadTasks(currentMemberId, selectedDate),
    () => loadTasksForAllMembers(members, selectedDate),
    setQuickAddTitle,
    setShowQuickAdd
  );

  // Load current member on mount
  useEffect(() => {
    const loadMember = async () => {
      const memberId = await loadCurrentMember();
      if (memberId) {
        setCurrentMemberId(memberId);
      }
    };
    void loadMember();
  }, [loadCurrentMember]);

  // Load data on mount and when view type changes
  useEffect(() => {
    void loadData();
  }, [viewType, currentWeek, currentMonth, loadData]);

  // Load tasks when needed
  useEffect(() => {
    if (showTasksOnly && viewType === CALENDAR_VIEW_TYPES.ROLLING) {
      if (showAllMembers) {
        void loadTasksForAllMembers(members, selectedDate);
      } else if (currentMemberId) {
        void loadTasks(currentMemberId, selectedDate);
      }
    }
  }, [showTasksOnly, viewType, currentMemberId, selectedDate, showAllMembers, members, loadTasks, loadTasksForAllMembers]);

  // Filter events for week/month views (rolling view handles its own filtering)
  const filteredEvents = useMemo(() => {
    return events.filter(event => {
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
  }, [events, showTasksOnly, showAllMembers, currentMemberId]);

  // Wrapper for handleToggleTask to match expected signature
  const handleToggleTaskWrapper = useMemo(() => {
    return (eventId: string, memberId?: string) => {
      void handleToggleTask(eventId, memberId || currentMemberId, selectedDate, showAllMembers);
    };
  }, [handleToggleTask, currentMemberId, selectedDate, showAllMembers]);

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
              onClick={() => setViewType(CALENDAR_VIEW_TYPES.ROLLING)}
              style={{
                flex: 1,
                padding: "8px 12px",
                borderRadius: "6px",
                border: "none",
                background: viewType === CALENDAR_VIEW_TYPES.ROLLING ? "#b8e6b8" : "transparent",
                color: viewType === CALENDAR_VIEW_TYPES.ROLLING ? "#2d5a2d" : "#6b6b6b",
                fontWeight: viewType === CALENDAR_VIEW_TYPES.ROLLING ? 600 : 400,
                fontSize: "0.85rem",
                cursor: "pointer",
                transition: "all 0.2s ease"
              }}
            >
              Rullande
            </button>
            <button
              type="button"
              onClick={() => setViewType(CALENDAR_VIEW_TYPES.WEEK)}
              style={{
                flex: 1,
                padding: "8px 12px",
                borderRadius: "6px",
                border: "none",
                background: viewType === CALENDAR_VIEW_TYPES.WEEK ? "#b8e6b8" : "transparent",
                color: viewType === CALENDAR_VIEW_TYPES.WEEK ? "#2d5a2d" : "#6b6b6b",
                fontWeight: viewType === CALENDAR_VIEW_TYPES.WEEK ? 600 : 400,
                fontSize: "0.85rem",
                cursor: "pointer",
                transition: "all 0.2s ease"
              }}
            >
              Vecka
            </button>
            <button
              type="button"
              onClick={() => setViewType(CALENDAR_VIEW_TYPES.MONTH)}
              style={{
                flex: 1,
                padding: "8px 12px",
                borderRadius: "6px",
                border: "none",
                background: viewType === CALENDAR_VIEW_TYPES.MONTH ? "#b8e6b8" : "transparent",
                color: viewType === CALENDAR_VIEW_TYPES.MONTH ? "#2d5a2d" : "#6b6b6b",
                fontWeight: viewType === CALENDAR_VIEW_TYPES.MONTH ? 600 : 400,
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
          {viewType === CALENDAR_VIEW_TYPES.ROLLING && (
            <RollingView
              showTasksOnly={showTasksOnly}
              showAllMembers={showAllMembers}
              selectedDate={selectedDate}
              setSelectedDate={setSelectedDate}
              showQuickAdd={showQuickAdd}
              setShowQuickAdd={setShowQuickAdd}
              quickAddTitle={quickAddTitle}
              setQuickAddTitle={setQuickAddTitle}
              handleQuickAdd={handleQuickAdd}
              tasksWithCompletion={tasksWithCompletion}
              tasksByMember={tasksByMember}
              members={members}
              currentMemberId={currentMemberId}
              events={events}
              categories={categories}
              handleToggleTask={handleToggleTaskWrapper}
              handleDeleteEvent={handleDeleteEvent}
              setEditingEvent={setEditingEvent}
            />
          )}

          {viewType === CALENDAR_VIEW_TYPES.WEEK && (
            <WeekView
              events={filteredEvents}
              categories={categories}
              members={members}
              currentWeek={currentWeek}
              onWeekChange={setCurrentWeek}
              onEventClick={(event) => setEditingEvent(event)}
              onEventDelete={handleDeleteEvent}
              onDayClick={(date, hour) => {
                const dateStr = formatDateForEventForm(date, hour);
                setInitialStartDate(dateStr);
                setEditingEvent(null);
                setShowCreateForm(true);
              }}
              showTasksOnly={showTasksOnly}
            />
          )}

          {viewType === CALENDAR_VIEW_TYPES.MONTH && (
            <MonthView
              events={filteredEvents}
              categories={categories}
              members={members}
              currentMonth={currentMonth}
              onMonthChange={setCurrentMonth}
              onEventClick={(event) => setEditingEvent(event)}
              onEventDelete={handleDeleteEvent}
              onDayClick={(date) => {
                const dateStr = formatDateForEventForm(date);
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
            // Reload categories only (without affecting loading state)
            await loadCategories();
          }}
        />
      )}
    </div>
  );
}

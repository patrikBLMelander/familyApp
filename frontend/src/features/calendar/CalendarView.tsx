import { useEffect, useState } from "react";
import {
  CalendarEventResponse,
  CalendarEventCategoryResponse,
} from "../../shared/api/calendar";
import { FamilyMemberResponse } from "../../shared/api/familyMembers";
import { EventForm } from "./components/EventForm";
import { WeekView } from "./components/WeekView";
import { MonthView } from "./components/MonthView";
import { CategoryManager } from "./components/CategoryManager";
import { RollingView } from "./components/RollingView";
import { useCalendarData } from "./hooks/useCalendarData";
import { useCalendarEvents } from "./hooks/useCalendarEvents";
import { EventFormData } from "./types/eventForm";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers";

type CalendarViewProps = {
  onNavigate?: (view: ViewKey) => void;
};

type CalendarViewType = "rolling" | "week" | "month";

export function CalendarView({ onNavigate }: CalendarViewProps) {
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingEvent, setEditingEvent] = useState<CalendarEventResponse | null>(null);
  const [viewType, setViewType] = useState<CalendarViewType>("rolling");
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

  // Load current member and data on mount
  useEffect(() => {
    const loadMember = async () => {
      const memberId = await loadCurrentMember();
      if (memberId) {
        setCurrentMemberId(memberId);
      }
    };
    void loadMember();
    void loadData();
  }, [loadCurrentMember, loadData]);

  // Reload events when view type changes to optimize data fetching
  useEffect(() => {
    void loadData();
  }, [viewType, currentWeek, currentMonth, loadData]);

  // Load tasks when needed
  useEffect(() => {
    if (showTasksOnly && viewType === "rolling") {
      if (showAllMembers) {
        void loadTasksForAllMembers(members, selectedDate);
      } else if (currentMemberId) {
        void loadTasks(currentMemberId, selectedDate);
      }
    }
  }, [showTasksOnly, viewType, currentMemberId, selectedDate, showAllMembers, members, loadTasks, loadTasksForAllMembers]);

  // Filter events for week/month views (rolling view handles its own filtering)
  const filteredEvents = events.filter(event => {
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

  // Wrapper for handleToggleTask to match expected signature
  const handleToggleTaskWrapper = (eventId: string, memberId?: string) => {
    void handleToggleTask(eventId, memberId || currentMemberId, selectedDate, showAllMembers);
  };

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
            await loadData();
          }}
        />
      )}
    </div>
  );
}

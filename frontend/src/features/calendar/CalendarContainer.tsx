import { useEffect, useState, useMemo, useRef } from "react";
import {
  CalendarEventResponse,
} from "../../shared/api/calendar";
import { EventForm } from "./components/EventForm";
import { WeekView } from "./components/WeekView";
import { MonthView } from "./components/MonthView";
import { CategoryManager } from "./components/CategoryManager";
import { RollingView } from "./components/RollingView";
import { CalendarHeader } from "./components/CalendarHeader";
import { CalendarViewSelector } from "./components/CalendarViewSelector";
import { CalendarFilters } from "./components/CalendarFilters";
import { DayActionMenu } from "./components/DayActionMenu";
import { useCalendarData } from "./hooks/useCalendarData";
import { useCalendarEvents } from "./hooks/useCalendarEvents";
import { formatDateForEventForm } from "./utils/dateFormatters";
import { CALENDAR_VIEW_TYPES, CalendarViewType } from "./constants";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers";

type CalendarContainerProps = {
  onNavigate?: (view: ViewKey) => void;
};

/**
 * Main container component for the calendar feature.
 * Manages all state, data fetching, and coordinates between different calendar views.
 * 
 * Handles:
 * - View type switching (rolling, week, month)
 * - Event CRUD operations
 * - Task completion
 * - Category management
 * - Filtering (tasks/events, members)
 * 
 * @param onNavigate - Optional callback for navigation to other views
 */
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
  const [dayActionMenuDate, setDayActionMenuDate] = useState<Date | null>(null);
  const [scrollToDate, setScrollToDate] = useState<Date | null>(null);

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
    loadMoreEvents,
  } = useCalendarData(
    viewType,
    currentWeek,
    currentMonth,
    selectedDate,
    showTasksOnly,
    showAllMembers,
    currentMemberId
  );

  // Use ref to maintain stable reference to members array
  const membersRef = useRef(members);
  useEffect(() => {
    membersRef.current = members;
  }, [members]);

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
        // Use ref to avoid unnecessary re-runs when members array reference changes
        void loadTasksForAllMembers(membersRef.current, selectedDate);
      } else if (currentMemberId) {
        void loadTasks(currentMemberId, selectedDate);
      }
    }
  }, [showTasksOnly, viewType, currentMemberId, selectedDate, showAllMembers, loadTasks, loadTasksForAllMembers]);

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

  const handleBackClick = () => {
    if (showCreateForm || editingEvent) {
      setShowCreateForm(false);
      setEditingEvent(null);
    } else if (onNavigate) {
      onNavigate("dashboard");
    }
  };

  return (
    <div className="calendar-view">
      <CalendarHeader
        onNavigate={onNavigate}
        onOpenCategoryManager={() => setShowCategoryManager(true)}
        onOpenQuickAdd={() => setShowCreateForm(true)}
        onBackClick={handleBackClick}
        currentUserRole={currentUserRole}
        showCreateForm={showCreateForm}
        editingEvent={!!editingEvent}
      />

      {/* View type toggle and filters - only show when not in form */}
      {!showCreateForm && !editingEvent && (
        <div style={{ 
          display: "flex", 
          flexDirection: "column",
          gap: "8px",
          marginBottom: "16px"
        }}>
          <CalendarViewSelector
            viewType={viewType}
            setViewType={setViewType}
          />
          <CalendarFilters
            showTasksOnly={showTasksOnly}
            setShowTasksOnly={setShowTasksOnly}
            showAllMembers={showAllMembers}
            setShowAllMembers={setShowAllMembers}
          />
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
              onLoadMoreEvents={loadMoreEvents}
              scrollToDate={scrollToDate}
              currentUserRole={currentUserRole}
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
                setDayActionMenuDate(date);
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
          allEvents={events}
          onSave={(eventData, scope, occurrenceDate) => {
            if (editingEvent) {
              void handleUpdateEvent(editingEvent.id, eventData, scope, occurrenceDate);
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

      {dayActionMenuDate && (
        <DayActionMenu
          date={dayActionMenuDate}
          events={events}
          onClose={() => setDayActionMenuDate(null)}
          onCreateEvent={(date) => {
            const dateStr = formatDateForEventForm(date);
            setInitialStartDate(dateStr);
            setEditingEvent(null);
            setShowCreateForm(true);
          }}
          onEditEvent={(event) => {
            setEditingEvent(event);
          }}
          onDeleteEvent={handleDeleteEvent}
          onGoToRollingView={(date) => {
            setSelectedDate(date);
            setViewType(CALENDAR_VIEW_TYPES.ROLLING);
            // Set scrollToDate after a small delay to ensure view has switched
            setTimeout(() => {
              setScrollToDate(date);
              // Clear scrollToDate after scrolling is complete
              setTimeout(() => {
                setScrollToDate(null);
              }, 2000);
            }, 100);
          }}
          currentUserRole={currentUserRole}
          currentUserId={currentMemberId}
        />
      )}
    </div>
  );
}

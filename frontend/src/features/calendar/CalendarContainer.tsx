import { useEffect, useState, useMemo } from "react";
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
import { DayActionMenu } from "./components/DayActionMenu";
import { useCalendarData } from "./hooks/useCalendarData";
import { useCalendarEvents } from "./hooks/useCalendarEvents";
import { formatDateForEventForm } from "./utils/dateFormatters";
import { CALENDAR_VIEW_TYPES, CalendarViewType } from "./constants";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers";

type CalendarContainerProps = {
  onNavigate?: (view: ViewKey) => void;
  showMenstrualCycle?: boolean;
  onNavigateMenstrualCycle?: () => void;
};

/**
 * Main container component for the calendar feature.
 * Manages all state, data fetching, and coordinates between different calendar views.
 *
 * Handles:
 * - View type switching (rolling, week, month)
 * - Event CRUD operations
 * - Category management
 *
 * @param onNavigate - Optional callback for navigation to other views
 */
export function CalendarContainer({ onNavigate, showMenstrualCycle, onNavigateMenstrualCycle }: CalendarContainerProps) {
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingEvent, setEditingEvent] = useState<CalendarEventResponse | null>(null);
  const [viewType, setViewType] = useState<CalendarViewType>(CALENDAR_VIEW_TYPES.ROLLING);
  const [currentWeek, setCurrentWeek] = useState(new Date());
  const [currentMonth, setCurrentMonth] = useState(new Date());
  const [showCategoryManager, setShowCategoryManager] = useState(false);
  const [currentMemberId, setCurrentMemberId] = useState<string | null>(null);
  const [selectedDate, setSelectedDate] = useState<Date>(new Date());
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
    currentUserRole,
    loadData,
    loadCategories,
    loadCurrentMember,
    setError,
    loadMoreEvents,
  } = useCalendarData(
    viewType,
    currentWeek,
    currentMonth,
  );

  // Use events hook
  const {
    handleCreateEvent,
    handleUpdateEvent,
    handleDeleteEvent,
  } = useCalendarEvents(
    loadData,
    setError,
    setShowCreateForm,
    setEditingEvent,
    currentMemberId,
    selectedDate,
    false,
    async () => {},
    async () => {},
    () => {},
    () => {}
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

  // Filter out task events — calendar only shows pure calendar events
  const filteredEvents = useMemo(() => {
    return events.filter(event => !event.isTask);
  }, [events]);

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
        showMenstrualCycle={showMenstrualCycle}
        onNavigateMenstrualCycle={onNavigateMenstrualCycle}
      />

      {/* View type selector — only show when not in form */}
      {!showCreateForm && !editingEvent && (
        <div style={{ marginBottom: "16px" }}>
          <CalendarViewSelector
            viewType={viewType}
            setViewType={setViewType}
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
              events={filteredEvents}
              categories={categories}
              members={members}
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
            />
          )}

          {viewType === CALENDAR_VIEW_TYPES.MONTH && (
            <MonthView
              events={filteredEvents}
              categories={categories}
              currentMonth={currentMonth}
              onMonthChange={setCurrentMonth}
              onEventClick={(event) => setEditingEvent(event)}
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
          onDelete={editingEvent ? (scope, occurrenceDate) => {
            void handleDeleteEvent(editingEvent.id, scope, occurrenceDate);
          } : undefined}
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
            setTimeout(() => {
              setScrollToDate(date);
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

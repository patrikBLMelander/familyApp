import { useEffect, useRef, useState, useCallback, useMemo } from "react";
import { CalendarEventResponse, CalendarEventCategoryResponse, CalendarTaskWithCompletionResponse } from "../../../shared/api/calendar";
import { FamilyMemberResponse } from "../../../shared/api/familyMembers";
import { fetchMemberPet, PetResponse } from "../../../shared/api/pets";
import { getPetFoodEmoji, getPetFoodName } from "../../pet/petFoodUtils";
import { formatDateTimeRange, formatAllDayEventRange, getAllDayEventDates } from "../utils/dateFormatters";
import { MAX_RECURRING_DAYS } from "../constants";
import { RecurringEventDialog } from "./RecurringEventDialog";

type RollingViewProps = {
  showTasksOnly: boolean;
  showAllMembers: boolean;
  selectedDate: Date;
  setSelectedDate: (date: Date) => void;
  showQuickAdd: boolean;
  setShowQuickAdd: (show: boolean) => void;
  quickAddTitle: string;
  setQuickAddTitle: (title: string) => void;
  handleQuickAdd: (title: string, memberId?: string | null) => Promise<void>;
  tasksWithCompletion: CalendarTaskWithCompletionResponse[];
  tasksByMember: Map<string, CalendarTaskWithCompletionResponse[]>;
  members: FamilyMemberResponse[];
  currentMemberId: string | null;
  events: CalendarEventResponse[];
  categories: CalendarEventCategoryResponse[];
  handleToggleTask: (eventId: string, memberId?: string) => void;
  handleDeleteEvent: (eventId: string) => Promise<void>;
  setEditingEvent: (event: CalendarEventResponse | null) => void;
  onLoadMoreEvents?: () => Promise<void>;
  scrollToDate?: Date | null;
  currentUserRole: "CHILD" | "ASSISTANT" | "PARENT" | null;
};


const SWIPE_THRESHOLD = 50; // px to trigger action

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
  onLoadMoreEvents,
  scrollToDate,
  currentUserRole,
}: RollingViewProps) {
  const [displayedEventCount, setDisplayedEventCount] = useState(15);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [petsByMemberId, setPetsByMemberId] = useState<Map<string, PetResponse>>(new Map());
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const observerRef = useRef<IntersectionObserver | null>(null);
  const loadMoreTriggerRef = useRef<HTMLDivElement>(null);
  const previousTotalEventCountRef = useRef(0);
  const loadMoreTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const expectingMoreEventsRef = useRef(false);
  const dateElementRefs = useRef<Map<string, HTMLElement>>(new Map());
  const [swipeStartX, setSwipeStartX] = useState<number | null>(null);
  const [swipeStartY, setSwipeStartY] = useState<number | null>(null);
  const [swipeOffset, setSwipeOffset] = useState<number>(0);
  const [swipedEventId, setSwipedEventId] = useState<string | null>(null);
  const [hasSwiped, setHasSwiped] = useState(false);
  const [quickAddMemberId, setQuickAddMemberId] = useState<string | null>(null);
  const swipeTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [recurringDialogEvent, setRecurringDialogEvent] = useState<{ event: CalendarEventResponse; occurrenceDate: string; action: "delete" | "edit" } | null>(null);
  
  // Helper function to check if an event is part of a recurring series
  // An event is part of a recurring series if:
  // 1. It has recurringType (base event), OR
  // 2. It doesn't have recurringType but there are other events with the same ID that have recurringType, OR
  // 3. There are multiple events with the same ID and different startDateTime (instances)
  const isRecurringEvent = useCallback((event: CalendarEventResponse): boolean => {
    if (event.recurringType) {
      return true; // Base recurring event
    }
    // Check if there are other events with same ID
    const eventsWithSameId = events.filter(e => e.id === event.id);
    if (eventsWithSameId.length <= 1) {
      return false; // Only one event with this ID, not recurring
    }
    // If there are multiple events with same ID, check if:
    // 1. Any has recurringType (base event), OR
    // 2. They have different startDateTime (instances of recurring event)
    const hasBaseEvent = eventsWithSameId.some(e => e.recurringType !== null);
    if (hasBaseEvent) {
      return true;
    }
    // Check if there are multiple events with same ID but different startDateTime
    // This indicates instances of a recurring event (even if base event is not in the list)
    const uniqueStartDates = new Set(eventsWithSameId.map(e => e.startDateTime));
    return uniqueStartDates.size > 1;
  }, [events]);
  
  // Get occurrence date from event (for instances, use startDateTime date part)
  const getOccurrenceDate = useCallback((event: CalendarEventResponse): string => {
    return event.startDateTime.substring(0, 10); // YYYY-MM-DD
  }, []);
  
  // Handle delete with recurring check
  const handleDeleteWithRecurringCheck = useCallback((event: CalendarEventResponse) => {
    if (isRecurringEvent(event)) {
      setRecurringDialogEvent({
        event,
        occurrenceDate: getOccurrenceDate(event),
        action: "delete",
      });
    } else {
      void handleDeleteEvent(event.id);
    }
  }, [isRecurringEvent, getOccurrenceDate, handleDeleteEvent]);
  
  // Handle edit with recurring check
  const handleEditWithRecurringCheck = useCallback((event: CalendarEventResponse) => {
    if (isRecurringEvent(event)) {
      setRecurringDialogEvent({
        event,
        occurrenceDate: getOccurrenceDate(event),
        action: "edit",
      });
    } else {
      setEditingEvent(event);
    }
  }, [isRecurringEvent, getOccurrenceDate, setEditingEvent]);
  
  // Load pets for all children
  useEffect(() => {
    const loadPets = async () => {
      const children = members.filter(m => m.role === "CHILD");
      const petPromises = children.map(async (child) => {
        try {
          const pet = await fetchMemberPet(child.id);
          return { memberId: child.id, pet };
        } catch (e) {
          return { memberId: child.id, pet: null };
        }
      });
      
      const petResults = await Promise.all(petPromises);
      const petsMap = new Map<string, PetResponse>();
      petResults.forEach(({ memberId, pet }) => {
        if (pet) {
          petsMap.set(memberId, pet);
        }
      });
      setPetsByMemberId(petsMap);
    };
    
    if (members.length > 0) {
      void loadPets();
    }
  }, [members]);
  
  // Filter events based on task/event toggle
  const now = new Date();
  const todayStr = now.toISOString().split("T")[0]; // YYYY-MM-DD format
  
  // Filter by task/event type based on showTasksOnly
  // IMPORTANT: For regular events (not tasks), we show ALL events regardless of participantIds
  // This ensures all family members' events are visible in the calendar view
  let filteredEvents = events.filter(event => {
    if (showTasksOnly) {
      if (!event.isTask) return false;
      // If showTasksOnly is true and showAllMembers is false, filter by current member
      if (!showAllMembers && currentMemberId) {
        return event.participantIds.includes(currentMemberId);
      }
      return true;
    } else {
      // IMPORTANT: For regular events (not tasks), show ALL events regardless of participantIds
      // Do NOT filter by participantIds - show ALL family events
      return !event.isTask; // Show only non-task events
    }
  });
  
  // For rolling view, also filter by date (today or future)
  filteredEvents = filteredEvents.filter(event => {
    const isFutureEvent = event.isAllDay
      ? getAllDayEventDates(event, MAX_RECURRING_DAYS).some(dateStr => dateStr >= todayStr)
      : new Date(event.startDateTime) >= now;
    return isFutureEvent;
  });

  // Group events by date - memoized to prevent unnecessary recalculations
  const eventsByDate = useMemo(() => {
    return filteredEvents.reduce((acc, event) => {
      if (event.isAllDay) {
        // For all-day events, get all dates the event spans
        const dates = getAllDayEventDates(event, MAX_RECURRING_DAYS);
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
  }, [filteredEvents]);

  const sortedDates = useMemo(() => {
    return Object.keys(eventsByDate).sort();
  }, [eventsByDate]);

  // Count total events across all dates
  const totalEventCount = sortedDates.reduce((count, dateKey) => {
    return count + eventsByDate[dateKey].length;
  }, 0);

  // Get dates to display based on displayedEventCount
  // We show complete dates, so if a date has events, we show all of them
  const displayedDates = useMemo(() => {
    let eventCount = 0;
    const datesToShow: string[] = [];
    
    for (const dateKey of sortedDates) {
      const dateEvents = eventsByDate[dateKey];
      const dateEventCount = dateEvents.length;
      
      // If adding this date would exceed the limit, stop
      // But if we haven't shown any dates yet, show at least one
      if (eventCount > 0 && eventCount + dateEventCount > displayedEventCount) {
        break;
      }
      
      datesToShow.push(dateKey);
      eventCount += dateEventCount;
      
      // If we've reached the limit, stop
      if (eventCount >= displayedEventCount) {
        break;
      }
    }
    
    return datesToShow;
  }, [sortedDates, eventsByDate, displayedEventCount]);

  // Load more events when reaching bottom
  const loadMoreEvents = useCallback(async () => {
    if (isLoadingMore) {
      return;
    }
    
    // Clear any pending timeout
    if (loadMoreTimeoutRef.current) {
      clearTimeout(loadMoreTimeoutRef.current);
      loadMoreTimeoutRef.current = null;
    }
    
    // Debounce: wait a bit before loading to prevent rapid-fire calls
    loadMoreTimeoutRef.current = setTimeout(async () => {
      if (isLoadingMore) {
        return;
      }
      
      setIsLoadingMore(true);
      
      try {
        // Check if we need to load more from API
        if (displayedEventCount >= totalEventCount && onLoadMoreEvents) {
          expectingMoreEventsRef.current = true;
          // Load more events from API
          await onLoadMoreEvents();
          // After loading, useEffect will handle increasing displayedEventCount
          // Don't increase here to avoid double increment
        } else {
          // Just show more from already loaded events
          setDisplayedEventCount(prev => prev + 15);
        }
      } catch (error) {
        console.error("Error loading more events:", error);
        expectingMoreEventsRef.current = false;
      } finally {
        setIsLoadingMore(false);
        loadMoreTimeoutRef.current = null;
      }
    }, 300); // 300ms debounce
  }, [isLoadingMore, onLoadMoreEvents, displayedEventCount, totalEventCount]);

  // Set up intersection observer for infinite scroll
  useEffect(() => {
    if (showTasksOnly || !loadMoreTriggerRef.current) return;

    // Disconnect previous observer if it exists
    if (observerRef.current) {
      observerRef.current.disconnect();
    }

    observerRef.current = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (entry.isIntersecting && !isLoadingMore) {
          void loadMoreEvents();
        }
      },
      {
        root: null,
        rootMargin: "200px", // Start loading 200px before reaching bottom
        threshold: 0.1,
      }
    );

    const currentTrigger = loadMoreTriggerRef.current;
    if (currentTrigger) {
      observerRef.current.observe(currentTrigger);
    }

    return () => {
      if (observerRef.current) {
        observerRef.current.disconnect();
      }
      if (loadMoreTimeoutRef.current) {
        clearTimeout(loadMoreTimeoutRef.current);
      }
    };
  }, [showTasksOnly, isLoadingMore, loadMoreEvents]);

  // Reset displayed count when filters change
  useEffect(() => {
    setDisplayedEventCount(15);
  }, [showTasksOnly, showAllMembers, currentMemberId]);

  // Cleanup swipe timeout on unmount
  useEffect(() => {
    return () => {
      if (swipeTimeoutRef.current) {
        clearTimeout(swipeTimeoutRef.current);
      }
    };
  }, []);

  // Track when we're loading more to detect API completion
  const lastEventCountRef = useRef(events.length);
  
  // When events array grows (after loading more), automatically show more
  useEffect(() => {
    if (showTasksOnly) {
      previousTotalEventCountRef.current = totalEventCount;
      lastEventCountRef.current = events.length;
      return;
    }
    
    // Check if events array actually grew (new events were added)
    const eventsArrayGrew = events.length > lastEventCountRef.current;
    const wasExpectingMore = expectingMoreEventsRef.current;
    
    // Only update if total actually increased
    if (totalEventCount > previousTotalEventCountRef.current) {
      const previousTotal = previousTotalEventCountRef.current;
      const increase = totalEventCount - previousTotal;
      
      // Always update displayedEventCount when new events are loaded
      // This ensures the UI updates when:
      // 1. We were expecting more events (API call just completed) - ALWAYS update
      // 2. Events array grew (new events added to state) - ALWAYS update
      // 3. We were showing all/most events (at or near the limit) - update to show new ones
      const shouldUpdate = wasExpectingMore || eventsArrayGrew || displayedEventCount >= previousTotal - 2;
      
      if (shouldUpdate) {
        if (wasExpectingMore) {
          expectingMoreEventsRef.current = false;
        }
        if (eventsArrayGrew) {
          lastEventCountRef.current = events.length;
        }
        
        // Force update by using functional setState
        setDisplayedEventCount(prev => {
          // Show at least 15 more, or the full increase if it's less than 15
          const increment = (wasExpectingMore || eventsArrayGrew)
            ? Math.max(15, increase)
            : 15;
          return Math.min(prev + increment, totalEventCount);
        });
      }
    } else if (eventsArrayGrew) {
      // Events array grew but totalEventCount didn't increase (filtering issue?)
      lastEventCountRef.current = events.length;
    }
    
    previousTotalEventCountRef.current = totalEventCount;
  }, [totalEventCount, showTasksOnly, displayedEventCount, events.length]);

  // Scroll to specific date when scrollToDate prop changes
  useEffect(() => {
    if (!scrollToDate || showTasksOnly) return;

    // Normalize the target date - use the date components directly to avoid timezone issues
    // scrollToDate should already be a Date object with the correct local date
    const year = scrollToDate.getFullYear();
    const month = String(scrollToDate.getMonth() + 1).padStart(2, "0");
    const day = String(scrollToDate.getDate()).padStart(2, "0");
    const targetDateStr = `${year}-${month}-${day}`;
    
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayStr = today.toISOString().split("T")[0];
    
    // If target date is in the past, don't scroll (rolling view only shows future events)
    if (targetDateStr < todayStr) {
      return;
    }
    
    // Function to attempt scrolling with retries
    const attemptScroll = (retries = 0) => {
      const element = dateElementRefs.current.get(targetDateStr);
      
      if (element) {
        // Element found, scroll to it
        const elementRect = element.getBoundingClientRect();
        const absoluteElementTop = elementRect.top + window.pageYOffset;
        const scrollPosition = absoluteElementTop - (window.innerHeight / 2) + (elementRect.height / 2);
        
        window.scrollTo({
          top: scrollPosition,
          behavior: "smooth",
        });
        return true;
      }
      
      // Element not found yet
      if (retries < 5) {
        // Retry after a delay
        setTimeout(() => attemptScroll(retries + 1), 200);
      }
      return false;
    };
    
    // Check if the date is in sortedDates
    const targetDateIndex = sortedDates.indexOf(targetDateStr);
    
    // Check if date is in displayedDates
    const isDisplayed = displayedDates.includes(targetDateStr);
    
    if (targetDateIndex === -1) {
      // Date not in events yet - might need to load more
      // Check if we need to load more events
      const lastSortedDate = sortedDates[sortedDates.length - 1];
      const needsMoreEvents = !lastSortedDate || targetDateStr > lastSortedDate;
      
      if (needsMoreEvents && onLoadMoreEvents) {
        // Load more events and then try to scroll
        onLoadMoreEvents().then(() => {
          // Wait a bit for state to update, then try scrolling
          setTimeout(() => attemptScroll(), 800);
        }).catch((error) => {
          console.error("Error loading more events:", error);
          // Try scrolling anyway in case the date appears
          setTimeout(() => attemptScroll(), 500);
        });
      } else {
        // No load function or date is before last date, just try to scroll anyway
        setTimeout(() => attemptScroll(), 300);
      }
      return;
    }
    
    if (!isDisplayed) {
      // Date exists but not displayed yet - calculate how many events to show
      let eventCount = 0;
      for (let i = 0; i <= targetDateIndex; i++) {
        const dateKey = sortedDates[i];
        eventCount += eventsByDate[dateKey]?.length || 0;
      }
      
      // Increase displayedEventCount to include this date
      setDisplayedEventCount(Math.max(displayedEventCount, eventCount + 15));
      
      // Wait for DOM to update, then scroll (with retries)
      setTimeout(() => attemptScroll(), 400);
    } else {
      // Date is already displayed, scroll to it (with retries)
      setTimeout(() => attemptScroll(), 150);
    }
  }, [scrollToDate, showTasksOnly, sortedDates, displayedDates, eventsByDate, displayedEventCount]);


  const handleQuickAddTask = async () => {
    await handleQuickAdd(quickAddTitle, quickAddMemberId);
    setQuickAddMemberId(null);
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
          {/* Quick Add Input - only shown when showAllMembers is false */}
          {showQuickAdd && !showAllMembers && (
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
                      setQuickAddMemberId(null);
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
                    setQuickAddMemberId(null);
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
                    const isQuickAddForThisMember = quickAddMemberId === member.id;
                    // Show member if they have tasks OR if quick add is active for them
                    if (memberTasks.length === 0 && !isQuickAddForThisMember) return null;
                    
                    return (
                      <section key={member.id} className="card">
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "12px" }}>
                          <div style={{ flex: 1 }}>
                            <h3 style={{ marginTop: 0, marginBottom: "4px", fontSize: "1rem", fontWeight: 600 }}>
                              {member.name}
                            </h3>
                          </div>
                          {!isQuickAddForThisMember && (
                            <button
                              type="button"
                              className="button-primary"
                              onClick={() => {
                                // Close any other open quick add and clear text
                                if (quickAddMemberId && quickAddMemberId !== member.id) {
                                  setQuickAddTitle("");
                                }
                                setQuickAddMemberId(member.id);
                                setShowQuickAdd(true);
                              }}
                              style={{ fontSize: "0.85rem", padding: "6px 12px" }}
                            >
                              + Add
                            </button>
                          )}
                        </div>
                        
                        {/* Quick Add Input for this specific member */}
                        {isQuickAddForThisMember && (
                          <div style={{ marginBottom: "12px", padding: "8px", background: "#f5f5f5", borderRadius: "6px" }}>
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
                                    setQuickAddMemberId(null);
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
                                  setQuickAddMemberId(null);
                                }}
                                style={{ fontSize: "0.9rem", padding: "8px 16px" }}
                              >
                                Avbryt
                              </button>
                            </div>
                          </div>
                        )}
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
                                    <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "space-between", gap: "8px" }}>
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
                                      {task.event.xpPoints && task.event.xpPoints > 0 && (() => {
                                        // If viewing all members and this member is a child, show food
                                        // If current user is a child, show food for their own tasks
                                        if (member.role === "CHILD") {
                                          const pet = petsByMemberId.get(member.id);
                                          const foodEmoji = pet ? getPetFoodEmoji(pet.petType) : "🍎";
                                          
                                          return (
                                            <span style={{
                                              fontSize: "1.2rem",
                                              flexShrink: 0
                                            }}>
                                              {foodEmoji}
                                            </span>
                                          );
                                        }
                                        
                                        // Hide XP for adults' own tasks (when not viewing all members)
                                        // If viewing all members, we already handled children above
                                        return null;
                                      })()}
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
          <div 
            ref={scrollContainerRef}
            style={{ display: "flex", flexDirection: "column", gap: "16px" }}
          >
          {displayedDates.map((dateKey) => {
            const dateEvents = eventsByDate[dateKey];
            const date = new Date(dateKey);
            const dateStr = date.toLocaleDateString("sv-SE", {
              weekday: "long",
              year: "numeric",
              month: "long",
              day: "numeric",
            });

            return (
              <section
                key={dateKey}
                ref={(el) => {
                  if (el) {
                    dateElementRefs.current.set(dateKey, el);
                  } else {
                    dateElementRefs.current.delete(dateKey);
                  }
                }}
                className="card"
              >
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

                    const isSwiped = swipedEventId === event.id;
                    const canEdit = currentUserRole === "PARENT" || currentUserRole === "ASSISTANT";
                    
                    return (
                      <li
                        key={event.id}
                        style={{
                          position: "relative",
                          marginBottom: "8px",
                          borderRadius: "8px",
                          overflow: "hidden",
                          transform: isSwiped ? `translateX(${swipeOffset}px)` : "translateX(0)",
                          transition: isSwiped ? "transform 0.2s ease" : "transform 0.3s ease",
                        }}
                        onTouchStart={(e) => {
                          if (!canEdit) return;
                          setSwipeStartX(e.touches[0].clientX);
                          setSwipeStartY(e.touches[0].clientY);
                          setHasSwiped(false);
                          if (swipedEventId !== event.id) {
                            setSwipedEventId(null);
                            setSwipeOffset(0);
                          }
                        }}
                        onTouchMove={(e) => {
                          if (!canEdit || swipeStartX === null || swipeStartY === null) return;
                          const currentX = e.touches[0].clientX;
                          const currentY = e.touches[0].clientY;
                          const deltaX = currentX - swipeStartX;
                          const deltaY = Math.abs(currentY - swipeStartY);
                          
                          // Only handle swipe if horizontal movement is greater than vertical
                          if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 10) {
                            e.preventDefault();
                            setHasSwiped(true);
                            if (deltaX < 0) {
                              // Swipe left - show delete button
                              setSwipedEventId(event.id);
                              setSwipeOffset(Math.max(deltaX, -80));
                            } else if (deltaX > 0) {
                              // Swipe right - show edit button
                              setSwipedEventId(event.id);
                              setSwipeOffset(Math.min(deltaX, 80));
                            }
                          }
                        }}
                        onTouchEnd={() => {
                          if (isSwiped) {
                            // If swiped left more than threshold, keep delete button open
                            if (swipeOffset < -SWIPE_THRESHOLD) {
                              setSwipeOffset(-80);
                            } 
                            // If swiped right more than threshold, trigger edit
                            else if (swipeOffset > SWIPE_THRESHOLD) {
                              handleEditWithRecurringCheck(event);
                              setSwipedEventId(null);
                              setSwipeOffset(0);
                            } 
                            // Otherwise close swipe
                            else {
                              setSwipedEventId(null);
                              setSwipeOffset(0);
                            }
                          }
                          // Reset after a short delay to allow click event to check hasSwiped
                          if (swipeTimeoutRef.current) {
                            clearTimeout(swipeTimeoutRef.current);
                          }
                          swipeTimeoutRef.current = setTimeout(() => {
                            setSwipeStartX(null);
                            setSwipeStartY(null);
                            setHasSwiped(false);
                            swipeTimeoutRef.current = null;
                          }, 100);
                        }}
                        onClick={(e) => {
                          // Only edit if not swiped and user has permission
                          if (!hasSwiped && !isSwiped && canEdit) {
                            handleEditWithRecurringCheck(event);
                          }
                        }}
                      >
                        <div
                          style={{
                            padding: "12px",
                            background: category?.color
                              ? `${category.color}20`
                              : "rgba(240, 240, 240, 0.5)",
                            borderLeft: `4px solid ${category?.color || "#b8e6b8"}`,
                            borderRadius: "8px",
                            cursor: canEdit ? "pointer" : "default",
                            userSelect: "none",
                            WebkitUserSelect: "none",
                          }}
                        >
                          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                            <div style={{ flex: 1 }}>
                              <div style={{ fontWeight: 600, marginBottom: "4px" }}>{event.title}</div>
                              <div style={{ fontSize: "0.85rem", color: "#6b6b6b", marginBottom: "4px" }}>
                                {event.isAllDay 
                                  ? formatAllDayEventRange(event.startDateTime, event.endDateTime)
                                  : formatDateTimeRange(event.startDateTime, event.endDateTime, false)
                                }
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
                            {/* Desktop buttons - shown only on desktop, hidden on mobile */}
                            {canEdit && (
                              <div 
                                className="event-actions-desktop"
                                style={{ 
                                  display: isSwiped ? "none" : "flex", 
                                  gap: "8px",
                                }}
                              >
                                <button
                                  type="button"
                                  className="todo-action-button"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    handleEditWithRecurringCheck(event);
                                  }}
                                  style={{ fontSize: "0.8rem", padding: "4px 8px" }}
                                >
                                  Redigera
                                </button>
                                <button
                                  type="button"
                                  className="todo-action-button-danger"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    handleDeleteWithRecurringCheck(event);
                                  }}
                                  style={{ fontSize: "0.8rem", padding: "4px 8px", borderRadius: "8px" }}
                                >
                                  Ta bort
                                </button>
                              </div>
                            )}
                          </div>
                        </div>
                        {/* Swipe edit button - shown when swiped right on mobile */}
                        {isSwiped && canEdit && swipeOffset > 0 && (
                          <button
                            type="button"
                            className="todo-edit-button"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleEditWithRecurringCheck(event);
                              setSwipedEventId(null);
                              setSwipeOffset(0);
                            }}
                            onTouchStart={(e) => e.stopPropagation()}
                            onTouchMove={(e) => e.stopPropagation()}
                            onTouchEnd={(e) => e.stopPropagation()}
                          >
                            Redigera
                          </button>
                        )}
                        {/* Swipe delete button - shown when swiped left on mobile */}
                        {isSwiped && canEdit && swipeOffset < 0 && (
                          <button
                            type="button"
                            className="todo-delete-button"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleDeleteWithRecurringCheck(event);
                              setSwipedEventId(null);
                              setSwipeOffset(0);
                            }}
                            onTouchStart={(e) => e.stopPropagation()}
                            onTouchMove={(e) => e.stopPropagation()}
                            onTouchEnd={(e) => e.stopPropagation()}
                          >
                            Ta bort
                          </button>
                        )}
                      </li>
                    );
                  })}
                </ul>
              </section>
            );
          })}
          {/* Load more trigger - invisible element at the bottom */}
          {!showTasksOnly && (displayedEventCount < totalEventCount || (displayedEventCount >= totalEventCount && onLoadMoreEvents)) && (
            <div ref={loadMoreTriggerRef} style={{ height: "1px", marginTop: "16px" }} />
          )}
        </div>
        )
      )}
      
      {/* Recurring event dialog */}
      {recurringDialogEvent && (
        <RecurringEventDialog
          event={recurringDialogEvent.event}
          occurrenceDate={recurringDialogEvent.occurrenceDate}
          action={recurringDialogEvent.action}
          onConfirm={(scope) => {
            if (recurringDialogEvent.action === "delete") {
              void handleDeleteEvent(recurringDialogEvent.event.id, scope, recurringDialogEvent.occurrenceDate);
            } else {
              // For edit, we need to set the event and scope, then open the form
              // We'll handle this in useCalendarEvents
              setEditingEvent(recurringDialogEvent.event);
              // Store scope and occurrenceDate for use in EventForm
              (recurringDialogEvent.event as any).__recurringScope = scope;
              (recurringDialogEvent.event as any).__occurrenceDate = recurringDialogEvent.occurrenceDate;
            }
            setRecurringDialogEvent(null);
          }}
          onCancel={() => {
            setRecurringDialogEvent(null);
          }}
        />
      )}
    </>
  );
}

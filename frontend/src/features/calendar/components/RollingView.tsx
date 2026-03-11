import { useEffect, useRef, useState, useCallback, useMemo } from "react";
import { CalendarEventResponse, CalendarEventCategoryResponse } from "../../../shared/api/calendar";
import { FamilyMemberResponse } from "../../../shared/api/familyMembers";
import { formatDateTimeRange, formatAllDayEventRange, getAllDayEventDates } from "../utils/dateFormatters";
import { MAX_RECURRING_DAYS } from "../constants";
import { RecurringEventDialog } from "./RecurringEventDialog";

type RollingViewProps = {
  events: CalendarEventResponse[];
  categories: CalendarEventCategoryResponse[];
  members: FamilyMemberResponse[];
  handleDeleteEvent: (eventId: string, scope?: "THIS" | "THIS_AND_FOLLOWING" | "ALL", occurrenceDate?: string) => Promise<void>;
  setEditingEvent: (event: CalendarEventResponse | null) => void;
  onLoadMoreEvents?: () => Promise<void>;
  scrollToDate?: Date | null;
  currentUserRole: "CHILD" | "ASSISTANT" | "PARENT" | null;
};

const SWIPE_THRESHOLD = 50; // px to trigger action

export function RollingView({
  events,
  categories,
  members,
  handleDeleteEvent,
  setEditingEvent,
  onLoadMoreEvents,
  scrollToDate,
  currentUserRole,
}: RollingViewProps) {
  const [displayedEventCount, setDisplayedEventCount] = useState(15);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
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
  const swipeTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [recurringDialogEvent, setRecurringDialogEvent] = useState<{ event: CalendarEventResponse; occurrenceDate: string; action: "delete" | "edit" } | null>(null);

  // Helper function to check if an event is part of a recurring series
  const isRecurringEvent = useCallback((event: CalendarEventResponse): boolean => {
    if (event.recurringType) {
      return true;
    }
    const eventsWithSameId = events.filter(e => e.id === event.id);
    if (eventsWithSameId.length <= 1) {
      return false;
    }
    const hasBaseEvent = eventsWithSameId.some(e => e.recurringType !== null);
    if (hasBaseEvent) {
      return true;
    }
    const uniqueStartDates = new Set(eventsWithSameId.map(e => e.startDateTime));
    return uniqueStartDates.size > 1;
  }, [events]);

  const getOccurrenceDate = useCallback((event: CalendarEventResponse): string => {
    return event.startDateTime.substring(0, 10);
  }, []);

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

  // Filter to non-task events from today forward
  const now = new Date();
  const todayStr = now.toISOString().split("T")[0];

  const filteredEvents = events.filter(event => {
    if (event.isTask) return false;
    const isFutureEvent = event.isAllDay
      ? getAllDayEventDates(event, MAX_RECURRING_DAYS).some(dateStr => dateStr >= todayStr)
      : new Date(event.startDateTime) >= now;
    return isFutureEvent;
  });

  // Group events by date
  const eventsByDate = useMemo(() => {
    return filteredEvents.reduce((acc, event) => {
      if (event.isAllDay) {
        const dates = getAllDayEventDates(event, MAX_RECURRING_DAYS);
        dates.forEach(dateKey => {
          if (!acc[dateKey]) {
            acc[dateKey] = [];
          }
          acc[dateKey].push(event);
        });
      } else {
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

  const totalEventCount = sortedDates.reduce((count, dateKey) => {
    return count + eventsByDate[dateKey].length;
  }, 0);

  const displayedDates = useMemo(() => {
    let eventCount = 0;
    const datesToShow: string[] = [];

    for (const dateKey of sortedDates) {
      const dateEvents = eventsByDate[dateKey];
      const dateEventCount = dateEvents.length;

      if (eventCount > 0 && eventCount + dateEventCount > displayedEventCount) {
        break;
      }

      datesToShow.push(dateKey);
      eventCount += dateEventCount;

      if (eventCount >= displayedEventCount) {
        break;
      }
    }

    return datesToShow;
  }, [sortedDates, eventsByDate, displayedEventCount]);

  const loadMoreEvents = useCallback(async () => {
    if (isLoadingMore) {
      return;
    }

    if (loadMoreTimeoutRef.current) {
      clearTimeout(loadMoreTimeoutRef.current);
      loadMoreTimeoutRef.current = null;
    }

    loadMoreTimeoutRef.current = setTimeout(async () => {
      if (isLoadingMore) {
        return;
      }

      setIsLoadingMore(true);

      try {
        if (displayedEventCount >= totalEventCount && onLoadMoreEvents) {
          expectingMoreEventsRef.current = true;
          await onLoadMoreEvents();
        } else {
          setDisplayedEventCount(prev => prev + 15);
        }
      } catch (error) {
        console.error("Error loading more events:", error);
        expectingMoreEventsRef.current = false;
      } finally {
        setIsLoadingMore(false);
        loadMoreTimeoutRef.current = null;
      }
    }, 300);
  }, [isLoadingMore, onLoadMoreEvents, displayedEventCount, totalEventCount]);

  // Set up intersection observer for infinite scroll
  useEffect(() => {
    if (!loadMoreTriggerRef.current) return;

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
        rootMargin: "200px",
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
  }, [isLoadingMore, loadMoreEvents]);

  // Cleanup swipe timeout on unmount
  useEffect(() => {
    return () => {
      if (swipeTimeoutRef.current) {
        clearTimeout(swipeTimeoutRef.current);
      }
    };
  }, []);

  const lastEventCountRef = useRef(events.length);

  // When events array grows (after loading more), automatically show more
  useEffect(() => {
    const eventsArrayGrew = events.length > lastEventCountRef.current;
    const wasExpectingMore = expectingMoreEventsRef.current;

    if (totalEventCount > previousTotalEventCountRef.current) {
      const previousTotal = previousTotalEventCountRef.current;
      const increase = totalEventCount - previousTotal;

      const shouldUpdate = wasExpectingMore || eventsArrayGrew || displayedEventCount >= previousTotal - 2;

      if (shouldUpdate) {
        if (wasExpectingMore) {
          expectingMoreEventsRef.current = false;
        }
        if (eventsArrayGrew) {
          lastEventCountRef.current = events.length;
        }

        setDisplayedEventCount(prev => {
          const increment = (wasExpectingMore || eventsArrayGrew)
            ? Math.max(15, increase)
            : 15;
          return Math.min(prev + increment, totalEventCount);
        });
      }
    } else if (eventsArrayGrew) {
      lastEventCountRef.current = events.length;
    }

    previousTotalEventCountRef.current = totalEventCount;
  }, [totalEventCount, displayedEventCount, events.length]);

  // Scroll to specific date when scrollToDate prop changes
  useEffect(() => {
    if (!scrollToDate) return;

    const year = scrollToDate.getFullYear();
    const month = String(scrollToDate.getMonth() + 1).padStart(2, "0");
    const day = String(scrollToDate.getDate()).padStart(2, "0");
    const targetDateStr = `${year}-${month}-${day}`;

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayDateStr = today.toISOString().split("T")[0];

    if (targetDateStr < todayDateStr) {
      return;
    }

    const attemptScroll = (retries = 0) => {
      const element = dateElementRefs.current.get(targetDateStr);

      if (element) {
        const elementRect = element.getBoundingClientRect();
        const absoluteElementTop = elementRect.top + window.pageYOffset;
        const scrollPosition = absoluteElementTop - (window.innerHeight / 2) + (elementRect.height / 2);

        window.scrollTo({
          top: scrollPosition,
          behavior: "smooth",
        });
        return true;
      }

      if (retries < 5) {
        setTimeout(() => attemptScroll(retries + 1), 200);
      }
      return false;
    };

    const targetDateIndex = sortedDates.indexOf(targetDateStr);
    const isDisplayed = displayedDates.includes(targetDateStr);

    if (targetDateIndex === -1) {
      const lastSortedDate = sortedDates[sortedDates.length - 1];
      const needsMoreEvents = !lastSortedDate || targetDateStr > lastSortedDate;

      if (needsMoreEvents && onLoadMoreEvents) {
        onLoadMoreEvents().then(() => {
          setTimeout(() => attemptScroll(), 800);
        }).catch((error) => {
          console.error("Error loading more events:", error);
          setTimeout(() => attemptScroll(), 500);
        });
      } else {
        setTimeout(() => attemptScroll(), 300);
      }
      return;
    }

    if (!isDisplayed) {
      let eventCount = 0;
      for (let i = 0; i <= targetDateIndex; i++) {
        const dateKey = sortedDates[i];
        eventCount += eventsByDate[dateKey]?.length || 0;
      }

      setDisplayedEventCount(Math.max(displayedEventCount, eventCount + 15));
      setTimeout(() => attemptScroll(), 400);
    } else {
      setTimeout(() => attemptScroll(), 150);
    }
  }, [scrollToDate, sortedDates, displayedDates, eventsByDate, displayedEventCount]);

  return (
    <>
      {sortedDates.length === 0 ? (
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

                          if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 10) {
                            e.preventDefault();
                            setHasSwiped(true);
                            if (deltaX < 0) {
                              setSwipedEventId(event.id);
                              setSwipeOffset(Math.max(deltaX, -80));
                            } else if (deltaX > 0) {
                              setSwipedEventId(event.id);
                              setSwipeOffset(Math.min(deltaX, 80));
                            }
                          }
                        }}
                        onTouchEnd={() => {
                          if (isSwiped) {
                            if (swipeOffset < -SWIPE_THRESHOLD) {
                              setSwipeOffset(-80);
                            } else if (swipeOffset > SWIPE_THRESHOLD) {
                              handleEditWithRecurringCheck(event);
                              setSwipedEventId(null);
                              setSwipeOffset(0);
                            } else {
                              setSwipedEventId(null);
                              setSwipeOffset(0);
                            }
                          }
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
                        onClick={() => {
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
          {(displayedEventCount < totalEventCount || (displayedEventCount >= totalEventCount && onLoadMoreEvents)) && (
            <div ref={loadMoreTriggerRef} style={{ height: "1px", marginTop: "16px" }} />
          )}
        </div>
      )}

      {recurringDialogEvent && (
        <RecurringEventDialog
          event={recurringDialogEvent.event}
          occurrenceDate={recurringDialogEvent.occurrenceDate}
          action={recurringDialogEvent.action}
          onConfirm={(scope) => {
            if (recurringDialogEvent.action === "delete") {
              void handleDeleteEvent(recurringDialogEvent.event.id, scope, recurringDialogEvent.occurrenceDate);
            } else {
              setEditingEvent(recurringDialogEvent.event);
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

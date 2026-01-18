import { CalendarEventResponse, CalendarEventCategoryResponse } from "../../../shared/api/calendar";
import { FamilyMemberResponse } from "../../../shared/api/familyMembers";
import { getEventsForDay } from "../utils/eventFilters";

export type WeekViewProps = {
  events: CalendarEventResponse[];
  categories: CalendarEventCategoryResponse[];
  members: FamilyMemberResponse[];
  currentWeek: Date;
  onWeekChange: (date: Date) => void;
  onEventClick: (event: CalendarEventResponse) => void;
  onEventDelete: (eventId: string) => void;
  onDayClick?: (date: Date, hour?: number) => void;
  showTasksOnly: boolean;
};

export function WeekView({ events, categories, members, currentWeek, onWeekChange, onEventClick, onEventDelete, onDayClick, showTasksOnly }: WeekViewProps) {
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

  // Get events for a specific day (using shared utility)
  const getDayEvents = (day: Date): CalendarEventResponse[] => {
    return getEventsForDay(events, day);
  };

  const getEventsForHour = (day: Date, hour: number): CalendarEventResponse[] => {
    const hourStart = new Date(day);
    hourStart.setHours(hour, 0, 0, 0);
    const hourEnd = new Date(day);
    hourEnd.setHours(hour, 59, 59, 999);

    return events.filter(event => {
      // Skip all-day events - they should only appear in the all-day section
      // Skip tasks - they appear in the tasks list
      if (event.isAllDay || event.isTask) {
        return false;
      }
      const eventStart = new Date(event.startDateTime);
      return eventStart >= hourStart && eventStart <= hourEnd;
    });
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

      {/* All-day events (non-tasks) - show in a grid matching the week layout */}
      {(() => {
        const hasAllDayEvents = weekDays.some(day => getDayEvents(day).filter(e => e.isAllDay && !e.isTask).length > 0);
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
                const dayEvents = getDayEvents(day).filter(e => e.isAllDay && !e.isTask);
                const handleDayClick = (e: React.MouseEvent) => {
                  // Only trigger if clicking on the day container itself, not on events
                  if (e.target === e.currentTarget || (e.target as HTMLElement).closest('[data-event]') === null) {
                    onDayClick?.(day);
                    e.stopPropagation();
                  }
                };
                return (
                  <div 
                    key={day.toISOString()} 
                    onClick={handleDayClick}
                    style={{ 
                      flex: 1, 
                      minWidth: 0, 
                      borderRight: dayIndex < 6 ? "1px solid #ddd" : "none",
                      padding: "4px 2px",
                      background: isToday ? "#b8e6b820" : "transparent",
                      cursor: onDayClick ? "pointer" : "default",
                      minHeight: "30px"
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

      {/* Tasks list - show as a list under each day */}
      {(() => {
        const hasTasks = weekDays.some(day => getDayEvents(day).filter(e => e.isTask).length > 0);
        if (!hasTasks) return null;
        
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
                Tasks
              </div>
              {weekDays.map((day, dayIndex) => {
                const isToday = day.toDateString() === new Date().toDateString();
                const dayTasks = getDayEvents(day).filter(e => e.isTask);
                // Sort tasks: required first, then by title
                const sortedTasks = [...dayTasks].sort((a, b) => {
                  if (a.isRequired !== b.isRequired) {
                    return a.isRequired ? -1 : 1; // Required first
                  }
                  return a.title.localeCompare(b.title);
                });
                
                const handleDayClick = (e: React.MouseEvent) => {
                  // Only trigger if clicking on the day container itself, not on tasks
                  if (e.target === e.currentTarget || (e.target as HTMLElement).closest('[data-event]') === null) {
                    onDayClick?.(day);
                    e.stopPropagation();
                  }
                };
                
                if (sortedTasks.length === 0) {
                  return (
                    <div 
                      key={day.toISOString()} 
                      onClick={handleDayClick}
                      style={{ 
                        flex: 1, 
                        minWidth: 0, 
                        borderRight: dayIndex < 6 ? "1px solid #ddd" : "none",
                        padding: "4px 2px",
                        background: isToday ? "#b8e6b820" : "transparent",
                        cursor: onDayClick ? "pointer" : "default",
                        minHeight: "40px"
                      }}
                    />
                  );
                }
                
                // Group tasks by member (each task can have multiple participants, so we'll show it for each)
                const tasksByMember = new Map<string, typeof sortedTasks>();
                sortedTasks.forEach(task => {
                  task.participantIds.forEach(participantId => {
                    if (!tasksByMember.has(participantId)) {
                      tasksByMember.set(participantId, []);
                    }
                    tasksByMember.get(participantId)!.push(task);
                  });
                });
                
                // Filter members to only show those that have tasks
                const membersWithTasks = members.filter(member => tasksByMember.has(member.id));
                
                return (
                  <div 
                    key={day.toISOString()} 
                    onClick={handleDayClick}
                    style={{ 
                      flex: 1, 
                      minWidth: 0, 
                      borderRight: dayIndex < 6 ? "1px solid #ddd" : "none",
                      padding: "4px 2px",
                      background: isToday ? "#b8e6b820" : "transparent",
                      cursor: onDayClick ? "pointer" : "default",
                      minHeight: "40px"
                    }}
                  >
                    <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                      {membersWithTasks.map(member => {
                        const memberTasks = tasksByMember.get(member.id) || [];
                        
                        return (
                          <div key={member.id} style={{ marginBottom: "4px" }}>
                            <div style={{
                              fontSize: "0.65rem",
                              fontWeight: 600,
                              color: "#6b6b6b",
                              marginBottom: "2px"
                            }}>
                              {member.name}
                            </div>
                            <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
                                {memberTasks.map(task => {
                                  const category = categories.find(c => c.id === task.categoryId);
                                  return (
                                    <li
                                      key={task.id}
                                      data-event
                                      onClick={(e) => {
                                        onEventClick(task);
                                        e.stopPropagation();
                                      }}
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
                                    title={task.title}
                                  >
                                    {task.title}
                                  </li>
                                );
                              })}
                            </ul>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        );
      })()}

      {/* Hourly grid - only show when not in tasks-only mode */}
      {!showTasksOnly && (
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
                
                const handleHourClick = (e: React.MouseEvent) => {
                  // Only trigger if clicking on the hour slot itself, not on events
                  if (e.target === e.currentTarget || (e.target as HTMLElement).closest('[data-event]') === null) {
                    onDayClick?.(day, hour);
                    e.stopPropagation();
                  }
                };
                
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
                    onClick={handleHourClick}
                    style={{
                      height: "60px",
                      borderBottom: "1px solid #f0f0f0",
                      position: "relative",
                      padding: "2px",
                      cursor: onDayClick ? "pointer" : "default"
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
      )}
    </div>
  );
}

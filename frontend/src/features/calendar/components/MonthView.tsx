import { CalendarEventResponse, CalendarEventCategoryResponse } from "../../../shared/api/calendar";
import { getEventsForDay } from "../utils/eventFilters";

export type MonthViewProps = {
  events: CalendarEventResponse[];
  categories: CalendarEventCategoryResponse[];
  currentMonth: Date;
  onMonthChange: (date: Date) => void;
  onEventClick: (event: CalendarEventResponse) => void;
  onDayClick?: (date: Date) => void;
};

export function MonthView({ events, categories, currentMonth, onMonthChange, onEventClick, onDayClick }: MonthViewProps) {
  const year = currentMonth.getFullYear();
  const month = currentMonth.getMonth();

  // Get first day of month and how many days
  const firstDay = new Date(year, month, 1);
  const lastDay = new Date(year, month + 1, 0);
  const daysInMonth = lastDay.getDate();
  const startingDayOfWeek = (firstDay.getDay() + 6) % 7; // Monday = 0

  // Get events for a specific day (using shared utility)
  const getDayEvents = (day: number): CalendarEventResponse[] => {
    return getEventsForDay(events, day, year, month);
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
            const dayDate = new Date(year, month, day);
            const dayEvents = getDayEvents(day);
            const todayClass = isToday(day);

            const handleDayClick = (e: React.MouseEvent) => {
              // Always trigger onDayClick when clicking on the day (including events)
              onDayClick?.(dayDate);
              e.stopPropagation();
            };

            return (
              <div
                key={day}
                onClick={handleDayClick}
                style={{
                  borderRight: (startingDayOfWeek + day) % 7 !== 0 ? "1px solid #ddd" : "none",
                  borderBottom: "1px solid #ddd",
                  padding: "2px",
                  background: todayClass ? "#b8e6b820" : "white",
                  position: "relative",
                  display: "flex",
                  flexDirection: "column",
                  overflow: "hidden",
                  height: "100%",
                  cursor: onDayClick ? "pointer" : "default"
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
                    const truncatedTitle = event.title.length > 15
                      ? event.title.substring(0, 15) + "..."
                      : event.title;
                    return (
                      <div
                        key={event.id}
                        data-event
                        onClick={(e) => { onEventClick(event); e.stopPropagation(); }}
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
                        title={event.title}
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

import { useState, useRef, useEffect, useCallback } from "react";
import { CyclePrediction, MenstrualCycleEntry } from "../../shared/api/menstrualCycle";
import { parseLocalDate, formatLocalDate, getDateKey as getDateKeyUtil } from "../../shared/utils/dateUtils";

type MenstrualCycleCalendarProps = {
  entries: MenstrualCycleEntry[];
  prediction: CyclePrediction | null;
  onDaysSelected?: (startDate: Date, endDate: Date) => void;
  onEntryDelete?: (entryId: string) => void;
  onSave?: (selectedDays: Set<string>) => void;
  currentMonth?: Date;
  onMonthChange?: (month: Date) => void;
  isSaving?: boolean;
};

type DayType = "period" | "fertile" | "ovulation" | "predicted-period" | "normal" | "today";

interface DayInfo {
  day: number;
  type: DayType;
  date: Date;
}

export function MenstrualCycleCalendar({ 
  entries, 
  prediction, 
  onDaysSelected, 
  onEntryDelete, 
  onSave,
  currentMonth: externalCurrentMonth,
  onMonthChange,
  isSaving = false
}: MenstrualCycleCalendarProps) {
  const [internalCurrentMonth, setInternalCurrentMonth] = useState(new Date());
  const currentMonth = externalCurrentMonth || internalCurrentMonth;
  const setCurrentMonth = onMonthChange || setInternalCurrentMonth;
  
  const getDateKey = (date: Date): string => {
    return getDateKeyUtil(date);
  };
  
  // Initialize selectedDays with all days from saved entries
  const initializeSelectedDays = () => {
    const savedDays = new Set<string>();
    entries.forEach(entry => {
      const start = parseLocalDate(entry.periodStartDate);
      const length = entry.periodLength || 5;
      for (let i = 0; i < length; i++) {
        const date = new Date(start);
        date.setDate(date.getDate() + i);
        savedDays.add(getDateKey(date));
      }
    });
    return savedDays;
  };

  const [selectedDays, setSelectedDays] = useState<Set<string>>(initializeSelectedDays);
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState<Date | null>(null);
  const [dragStartKey, setDragStartKey] = useState<string | null>(null);
  const [wasStartSelected, setWasStartSelected] = useState(false);
  const [hasMoved, setHasMoved] = useState(false);
  const [isTouchDevice, setIsTouchDevice] = useState(false);
  const calendarRef = useRef<HTMLDivElement>(null);
  const justDraggedRef = useRef(false);

  // Detect if device is touch-enabled
  useEffect(() => {
    const checkTouchDevice = () => {
      setIsTouchDevice('ontouchstart' in window || navigator.maxTouchPoints > 0);
    };
    checkTouchDevice();
    window.addEventListener('resize', checkTouchDevice);
    return () => window.removeEventListener('resize', checkTouchDevice);
  }, []);

  // Update selectedDays when entries change, but preserve currentMonth
  useEffect(() => {
    const savedDays = new Set<string>();
    entries.forEach(entry => {
      const start = parseLocalDate(entry.periodStartDate);
      const length = entry.periodLength || 5;
      for (let i = 0; i < length; i++) {
        const date = new Date(start);
        date.setDate(date.getDate() + i);
        savedDays.add(getDateKey(date));
      }
    });
    // Only update selectedDays if it actually changed to avoid unnecessary re-renders
    setSelectedDays(prevSelectedDays => {
      const currentSelectedStr = Array.from(prevSelectedDays).sort().join(",");
      const newSelectedStr = Array.from(savedDays).sort().join(",");
      if (currentSelectedStr !== newSelectedStr) {
        return savedDays;
      }
      return prevSelectedDays;
    });
  }, [entries]);

  const navigateMonth = (direction: "prev" | "next") => {
    const newDate = new Date(currentMonth);
    newDate.setMonth(newDate.getMonth() + (direction === "next" ? 1 : -1));
    setCurrentMonth(newDate);
  };

  const isDaySelected = (date: Date): boolean => {
    return selectedDays.has(getDateKey(date));
  };

  const getEntryForDate = (date: Date): MenstrualCycleEntry | null => {
    const checkDate = new Date(date);
    checkDate.setHours(0, 0, 0, 0);

    for (const entry of entries) {
      const periodStart = parseLocalDate(entry.periodStartDate);
      const periodLength = entry.periodLength || 5;
      const periodEnd = new Date(periodStart);
      periodEnd.setDate(periodEnd.getDate() + periodLength - 1);
      periodEnd.setHours(0, 0, 0, 0);

      if (checkDate >= periodStart && checkDate <= periodEnd) {
        return entry;
      }
    }
    return null;
  };

  const getDayType = (date: Date): DayType => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const checkDate = new Date(date);
    checkDate.setHours(0, 0, 0, 0);

    // Check if selected
    if (isDaySelected(checkDate)) {
      return "period"; // Use period styling for selected days
    }

    // Check if today
    if (checkDate.getTime() === today.getTime()) {
      return "today";
    }

    // Check historical periods
    if (getEntryForDate(checkDate)) {
      return "period";
    }

    // Check predicted period
    if (prediction) {
      const predictedStart = new Date(prediction.nextPeriodStart);
      predictedStart.setHours(0, 0, 0, 0);
      const predictedEnd = new Date(prediction.nextPeriodEnd);
      predictedEnd.setHours(0, 0, 0, 0);

      if (checkDate >= predictedStart && checkDate <= predictedEnd) {
        return "predicted-period";
      }

      // Check fertile window
      const fertileStart = new Date(prediction.fertileWindowStart);
      fertileStart.setHours(0, 0, 0, 0);
      const fertileEnd = new Date(prediction.fertileWindowEnd);
      fertileEnd.setHours(0, 0, 0, 0);

      if (checkDate >= fertileStart && checkDate <= fertileEnd) {
        // Check if ovulation day
        const ovulationDate = new Date(prediction.ovulationDate);
        ovulationDate.setHours(0, 0, 0, 0);
        if (checkDate.getTime() === ovulationDate.getTime()) {
          return "ovulation";
        }
        return "fertile";
      }
    }

    return "normal";
  };

  const getDayColor = (type: DayType, date: Date): string => {
    // If day is selected, use selection color
    if (isDaySelected(date)) {
      return "#dc2626"; // Red for selected days
    }

    switch (type) {
      case "period":
        return "#dc2626"; // Red for actual period
      case "predicted-period":
        return "#fca5a5"; // Light red for predicted period
      case "ovulation":
        return "#f59e0b"; // Orange for ovulation
      case "fertile":
        return "#fef3c7"; // Light yellow/cream for fertile window
      case "today":
        return "#3b82f6"; // Blue for today
      default:
        return "#ffffff"; // White for normal days
    }
  };

  const getDayBorderColor = (type: DayType): string => {
    switch (type) {
      case "today":
        return "#1d4ed8"; // Darker blue border for today
      case "period":
      case "predicted-period":
      case "ovulation":
      case "fertile":
        return "#e5e7eb"; // Light gray border
      default:
        return "#e5e7eb"; // Light gray border
    }
  };

  const getDayTextColor = (type: DayType, date: Date): string => {
    // If day is selected, use white text
    if (isDaySelected(date)) {
      return "#ffffff";
    }

    switch (type) {
      case "period":
      case "ovulation":
      case "today":
        return "#ffffff"; // White text on colored backgrounds
      case "predicted-period":
      case "fertile":
        return "#1f2937"; // Dark text on light backgrounds
      default:
        return "#1f2937"; // Dark text
    }
  };

  const toggleDay = (date: Date) => {
    const dateKey = getDateKey(date);
    const isCurrentlySelected = selectedDays.has(dateKey);
    const newSelected = new Set(selectedDays);
    
    if (isCurrentlySelected) {
      newSelected.delete(dateKey);
    } else {
      newSelected.add(dateKey);
    }
    
    setSelectedDays(newSelected);
  };

  const handleSave = () => {
    if (onSave) {
      onSave(selectedDays);
    }
  };

  const handleDayMouseDown = (date: Date) => {
    // On touch devices, don't start dragging - just toggle the day
    if (isTouchDevice) {
      return;
    }
    
    // Reset drag tracking
    justDraggedRef.current = false;
    
    const dateKey = getDateKey(date);
    const isCurrentlySelected = selectedDays.has(dateKey);
    const entry = getEntryForDate(date);
    
    // If clicking on an existing saved period, unselect it (will be deleted automatically)
    if (entry && !isCurrentlySelected) {
      // Start by selecting it so we can then unselect it
      const newSelected = new Set(selectedDays);
      newSelected.add(dateKey);
      setSelectedDays(newSelected);
      setIsDragging(true);
      setDragStart(date);
      setDragStartKey(dateKey);
      setWasStartSelected(false); // We want to unselect
      setHasMoved(false);
      return;
    }
    
    setIsDragging(true);
    setDragStart(date);
    setDragStartKey(dateKey);
    setWasStartSelected(isCurrentlySelected);
    setHasMoved(false);
    
    if (isCurrentlySelected) {
      // If clicking on a selected day, start unselecting
      const newSelected = new Set(selectedDays);
      newSelected.delete(dateKey);
      setSelectedDays(newSelected);
    } else {
      // If clicking on an unselected day, start selecting
      const newSelected = new Set(selectedDays);
      newSelected.add(dateKey);
      setSelectedDays(newSelected);
    }
  };

  const handleDayMouseEnter = (date: Date) => {
    // Don't allow dragging on touch devices
    if (isTouchDevice) {
      return;
    }
    
    if (isDragging && dragStart && dragStartKey !== null) {
      setHasMoved(true);
      justDraggedRef.current = true;
      const start = new Date(dragStart);
      const end = new Date(date);
      
      // Ensure start is before end
      if (start > end) {
        [start, end] = [end, start];
      }

      // Only allow selecting days in the past or today (not future)
      const today = new Date();
      today.setHours(23, 59, 59, 999);
      if (end > today) {
        end.setTime(today.getTime());
      }
      if (start > today) {
        return;
      }

      let newSelected: Set<string>;
      
      if (wasStartSelected) {
        // If we started from a selected day, we're unselecting
        newSelected = new Set(selectedDays);
        const current = new Date(start);
        while (current <= end) {
          if (current <= today) {
            newSelected.delete(getDateKey(current));
          }
          current.setDate(current.getDate() + 1);
        }
      } else {
        // If we started from an unselected day, we're selecting - keep existing selections and add new ones
        newSelected = new Set(selectedDays);
        const current = new Date(start);
        while (current <= end) {
          if (current <= today) {
            newSelected.add(getDateKey(current));
          }
          current.setDate(current.getDate() + 1);
        }
      }
      
      setSelectedDays(newSelected);
    }
  };

  const DRAG_RESET_DELAY_MS = 100;

  const handleDayMouseUp = useCallback(() => {
    // Reset drag tracking after a short delay to allow onClick to check it
    setTimeout(() => {
      justDraggedRef.current = false;
    }, DRAG_RESET_DELAY_MS);
    
    setIsDragging(false);
    setDragStart(null);
    setDragStartKey(null);
    setWasStartSelected(false);
    setHasMoved(false);
  }, []);

  const year = currentMonth.getFullYear();
  const month = currentMonth.getMonth();

  // Get first day of month and how many days
  const firstDay = new Date(year, month, 1);
  const lastDay = new Date(year, month + 1, 0);
  const daysInMonth = lastDay.getDate();
  const startingDayOfWeek = (firstDay.getDay() + 6) % 7; // Monday = 0

  const monthName = currentMonth.toLocaleDateString("sv-SE", { month: "long", year: "numeric" });
  const weekDays = ["Mån", "Tis", "Ons", "Tors", "Fre", "Lör", "Sön"];

  // Generate all days for the month
  const days: DayInfo[] = [];
  for (let day = 1; day <= daysInMonth; day++) {
    const date = new Date(year, month, day);
    days.push({
      day,
      type: getDayType(date),
      date,
    });
  }

  // Add global mouse up handler to stop dragging when mouse leaves calendar
  useEffect(() => {
    const handleGlobalMouseUp = () => {
      if (isDragging) {
        handleDayMouseUp();
      }
    };

    if (isDragging) {
      document.addEventListener("mouseup", handleGlobalMouseUp);
      return () => document.removeEventListener("mouseup", handleGlobalMouseUp);
    }
  }, [isDragging, handleDayMouseUp]);

  return (
    <div 
      ref={calendarRef}
      style={{ marginBottom: "20px" }}
      onMouseLeave={() => {
        if (isDragging) {
          handleDayMouseUp();
        }
      }}
    >
      <div style={{ 
        display: "flex", 
        alignItems: "center", 
        justifyContent: "space-between", 
        marginBottom: "16px",
        padding: "12px 16px",
        background: "linear-gradient(135deg, #f9fafb 0%, #f3f4f6 100%)",
        borderRadius: "10px",
        border: "1px solid rgba(220, 210, 200, 0.3)"
      }}>
        <button
          type="button"
          onClick={() => navigateMonth("prev")}
          style={{
            padding: "10px 14px",
            border: "1px solid rgba(220, 210, 200, 0.5)",
            borderRadius: "8px",
            background: "white",
            cursor: "pointer",
            fontSize: "1.2rem",
            color: "#6b6b6b",
            transition: "all 0.2s ease",
            boxShadow: "0 1px 3px rgba(0, 0, 0, 0.05)"
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = "#f3f4f6";
            e.currentTarget.style.borderColor = "#b8e6b8";
            e.currentTarget.style.color = "#2d5a2d";
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = "white";
            e.currentTarget.style.borderColor = "rgba(220, 210, 200, 0.5)";
            e.currentTarget.style.color = "#6b6b6b";
          }}
        >
          ←
        </button>
        <div style={{ 
          textAlign: "center", 
          fontWeight: 600, 
          textTransform: "capitalize", 
          fontSize: "1.15rem",
          color: "#3a3a3a"
        }}>
          {monthName}
        </div>
        <button
          type="button"
          onClick={() => navigateMonth("next")}
          style={{
            padding: "10px 14px",
            border: "1px solid rgba(220, 210, 200, 0.5)",
            borderRadius: "8px",
            background: "white",
            cursor: "pointer",
            fontSize: "1.2rem",
            color: "#6b6b6b",
            transition: "all 0.2s ease",
            boxShadow: "0 1px 3px rgba(0, 0, 0, 0.05)"
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = "#f3f4f6";
            e.currentTarget.style.borderColor = "#b8e6b8";
            e.currentTarget.style.color = "#2d5a2d";
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = "white";
            e.currentTarget.style.borderColor = "rgba(220, 210, 200, 0.5)";
            e.currentTarget.style.color = "#6b6b6b";
          }}
        >
          →
        </button>
      </div>

      <div style={{ border: "1px solid #ddd", borderRadius: "8px", overflow: "hidden", background: "white" }}>
        {/* Weekday headers */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", background: "#f5f5f5" }}>
          {weekDays.map((day) => (
            <div
              key={day}
              style={{
                padding: "8px",
                textAlign: "center",
                fontSize: "0.75rem",
                fontWeight: 600,
                color: "#6b6b6b",
                borderRight: "1px solid #ddd",
              }}
            >
              {day}
            </div>
          ))}
        </div>

        {/* Calendar grid */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)" }}>
          {/* Empty cells for days before month starts */}
          {Array.from({ length: startingDayOfWeek }).map((_, index) => (
            <div
              key={`empty-${index}`}
              style={{
                aspectRatio: "1",
                borderRight: "1px solid #e5e7eb",
                borderBottom: "1px solid #e5e7eb",
                background: "#f9fafb",
              }}
            />
          ))}

          {/* Days of the month */}
          {days.map((dayInfo) => {
            const isSelected = isDaySelected(dayInfo.date);
            const entry = getEntryForDate(dayInfo.date);
            const today = new Date();
            today.setHours(0, 0, 0, 0);
            const dayDate = new Date(dayInfo.date);
            dayDate.setHours(0, 0, 0, 0);
            const isPastOrToday = dayDate <= today;
            const canSelect = dayInfo.type === "normal" || dayInfo.type === "predicted-period" || dayInfo.type === "fertile" || (dayInfo.type === "period" && entry !== null);
            
            const bgColor = getDayColor(dayInfo.type, dayInfo.date);
            const borderColor = getDayBorderColor(dayInfo.type);
            const textColor = getDayTextColor(dayInfo.type, dayInfo.date);

            return (
              <div
                key={dayInfo.day}
                style={{
                  aspectRatio: "1",
                  borderRight: "1px solid #e5e7eb",
                  borderBottom: "1px solid #e5e7eb",
                  background: bgColor,
                  border: dayInfo.type === "today" ? `2px solid ${borderColor}` : `1px solid ${borderColor}`,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontSize: "0.9rem",
                  fontWeight: dayInfo.type === "today" || isSelected ? 700 : 500,
                  color: textColor,
                  cursor: isPastOrToday && canSelect ? "pointer" : "default",
                  position: "relative",
                  opacity: isPastOrToday ? 1 : 0.5,
                  transition: "all 0.15s ease",
                }}
                onClick={(e) => {
                  // On desktop, only toggle if we didn't drag
                  // On touch devices, always allow click
                  if (isPastOrToday && canSelect && (isTouchDevice || !justDraggedRef.current)) {
                    toggleDay(dayInfo.date);
                  }
                }}
                onMouseDown={(e) => {
                  e.preventDefault();
                  if (isPastOrToday && canSelect && !isTouchDevice) {
                    handleDayMouseDown(dayInfo.date);
                  }
                }}
                onMouseEnter={() => {
                  if (isPastOrToday && canSelect && !isTouchDevice) {
                    handleDayMouseEnter(dayInfo.date);
                  }
                }}
                onMouseUp={handleDayMouseUp}
                title={
                  isSelected
                    ? "Markerad för mensperiod"
                    : entry
                    ? "Klicka för att ta bort mensperiod"
                    : dayInfo.type === "period"
                    ? "Mensperiod"
                    : dayInfo.type === "predicted-period"
                    ? "Förväntad mens"
                    : dayInfo.type === "ovulation"
                    ? "Ägglossning"
                    : dayInfo.type === "fertile"
                    ? "Fertilt fönster"
                    : dayInfo.type === "today"
                    ? "Idag"
                    : isPastOrToday && canSelect
                    ? isTouchDevice
                      ? "Tryck för att markera/avmarkera mensperiod"
                      : "Klicka eller dra för att markera mensperiod. Klicka på 'Spara ändringar' när du är klar."
                    : ""
                }
              >
                {dayInfo.day}
                {isSelected && (
                  <div style={{
                    position: "absolute",
                    top: "2px",
                    right: "2px",
                    width: "6px",
                    height: "6px",
                    background: "#ffffff",
                    borderRadius: "50%",
                    boxShadow: "0 1px 2px rgba(0, 0, 0, 0.2)"
                  }} />
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* Save button */}
      {onSave && (
        <div style={{ 
          marginTop: "20px",
          display: "flex",
          justifyContent: "center"
        }}>
          <button
            type="button"
            onClick={handleSave}
            disabled={isSaving}
            style={{
              padding: "14px 32px",
              background: isSaving 
                ? "linear-gradient(135deg, #d1d5db 0%, #9ca3af 100%)"
                : "linear-gradient(135deg, #86efac 0%, #4ade80 100%)",
              color: isSaving ? "#6b7280" : "#166534",
              border: "none",
              borderRadius: "12px",
              fontSize: "1rem",
              fontWeight: 600,
              cursor: isSaving ? "not-allowed" : "pointer",
              boxShadow: isSaving 
                ? "0 2px 8px rgba(156, 163, 175, 0.3)"
                : "0 2px 8px rgba(134, 239, 172, 0.3)",
              transition: "all 0.2s ease",
              opacity: isSaving ? 0.7 : 1
            }}
            onMouseEnter={(e) => {
              if (!isSaving) {
                e.currentTarget.style.transform = "translateY(-2px)";
                e.currentTarget.style.boxShadow = "0 4px 12px rgba(134, 239, 172, 0.4)";
                e.currentTarget.style.background = "linear-gradient(135deg, #4ade80 0%, #22c55e 100%)";
              }
            }}
            onMouseLeave={(e) => {
              if (!isSaving) {
                e.currentTarget.style.transform = "translateY(0)";
                e.currentTarget.style.boxShadow = "0 2px 8px rgba(134, 239, 172, 0.3)";
                e.currentTarget.style.background = "linear-gradient(135deg, #86efac 0%, #4ade80 100%)";
              }
            }}
          >
            {isSaving ? "Sparar..." : "Spara ändringar"}
          </button>
        </div>
      )}

      {/* Legend */}
      <div style={{ 
        marginTop: "20px", 
        padding: "16px",
        background: "linear-gradient(135deg, #f9fafb 0%, #f3f4f6 100%)",
        borderRadius: "10px",
        border: "1px solid rgba(220, 210, 200, 0.3)"
      }}>
        <p style={{ 
          margin: "0 0 12px 0", 
          fontSize: "0.85rem", 
          fontWeight: 600, 
          color: "#6b6b6b",
          textTransform: "uppercase",
          letterSpacing: "0.5px"
        }}>
          Förklaring
        </p>
        <div style={{ display: "flex", flexWrap: "wrap", gap: "16px", fontSize: "0.85rem" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <div
              style={{
                width: "24px",
                height: "24px",
                borderRadius: "6px",
                background: "#dc2626",
                boxShadow: "0 2px 4px rgba(220, 38, 38, 0.2)"
              }}
            />
            <span style={{ fontWeight: 500, color: "#3a3a3a" }}>Mensperiod</span>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <div
              style={{
                width: "24px",
                height: "24px",
                borderRadius: "6px",
                background: "#fca5a5",
                boxShadow: "0 2px 4px rgba(248, 113, 113, 0.2)"
              }}
            />
            <span style={{ fontWeight: 500, color: "#3a3a3a" }}>Förväntad mens</span>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <div
              style={{
                width: "24px",
                height: "24px",
                borderRadius: "6px",
                background: "#f59e0b",
                boxShadow: "0 2px 4px rgba(245, 158, 11, 0.2)"
              }}
            />
            <span style={{ fontWeight: 500, color: "#3a3a3a" }}>Ägglossning</span>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <div
              style={{
                width: "24px",
                height: "24px",
                borderRadius: "6px",
                background: "#fef3c7",
                boxShadow: "0 2px 4px rgba(254, 243, 199, 0.3)"
              }}
            />
            <span style={{ fontWeight: 500, color: "#3a3a3a" }}>Fertilt fönster</span>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <div
              style={{
                width: "24px",
                height: "24px",
                borderRadius: "6px",
                background: "#3b82f6",
                border: "2px solid #1d4ed8",
                boxShadow: "0 2px 4px rgba(59, 130, 246, 0.3)"
              }}
            />
            <span style={{ fontWeight: 500, color: "#3a3a3a" }}>Idag</span>
          </div>
        </div>
      </div>
    </div>
  );
}

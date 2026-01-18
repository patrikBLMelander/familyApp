import { useEffect, useState } from "react";
import { CalendarEventResponse, CalendarEventCategoryResponse } from "../../../shared/api/calendar";
import { FamilyMemberResponse } from "../../../shared/api/familyMembers";
import { EventFormData } from "../types/eventForm";

export type EventFormProps = {
  event?: CalendarEventResponse | null;
  initialStartDate?: string | null;
  categories: CalendarEventCategoryResponse[];
  members: FamilyMemberResponse[];
  currentUserRole?: "CHILD" | "ASSISTANT" | "PARENT" | null;
  currentUserId?: string | null;
  onSave: (eventData: EventFormData) => void;
  onDelete?: () => void;
  onCancel: () => void;
};

export function EventForm({ event, initialStartDate, categories, members, currentUserRole, currentUserId, onSave, onDelete, onCancel }: EventFormProps) {
  const [title, setTitle] = useState(event?.title || "");
  const [description, setDescription] = useState(event?.description || "");
  const [showCategoryDropdown, setShowCategoryDropdown] = useState(false);
  
  // Calculate default end date (1 hour after start) for new events
  // Works with datetime-local format (YYYY-MM-DDTHH:mm)
  const getDefaultEndDate = (startDateStr: string): string => {
    if (!startDateStr) return "";
    // Parse the datetime-local string and add 1 hour
    const [datePart, timePart] = startDateStr.split("T");
    if (!timePart) return "";
    const [hours, minutes] = timePart.split(":").map(Number);
    let newHours = hours + 1;
    let newMinutes = minutes;
    if (newHours >= 24) {
      newHours = 0;
      // For simplicity, we don't handle day rollover here
    }
    return `${datePart}T${String(newHours).padStart(2, "0")}:${String(newMinutes).padStart(2, "0")}`;
  };
  
  // Convert ISO datetime string to datetime-local format (YYYY-MM-DDTHH:mm)
  // or date format (YYYY-MM-DD) for all-day events
  const isoToLocalDateTime = (isoString: string, isAllDay: boolean): string => {
    if (isAllDay) {
      // For all-day events, just extract the date part (YYYY-MM-DD)
      if (isoString.length >= 10) {
        return isoString.substring(0, 10);
      }
      // Fallback: parse and extract date
      const date = new Date(isoString);
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, "0");
      const day = String(date.getDate()).padStart(2, "0");
      return `${year}-${month}-${day}`;
    } else {
      // For regular events, convert to YYYY-MM-DDTHH:mm format
      // If it's already in the right format, return it
      if (isoString.length === 16 && isoString[10] === "T") {
        return isoString;
      }
      // Otherwise parse and convert
      const date = new Date(isoString);
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, "0");
      const day = String(date.getDate()).padStart(2, "0");
      const hours = String(date.getHours()).padStart(2, "0");
      const minutes = String(date.getMinutes()).padStart(2, "0");
      return `${year}-${month}-${day}T${hours}:${minutes}`;
    }
  };
  
  const [startDate, setStartDate] = useState(
    event ? isoToLocalDateTime(event.startDateTime, event.isAllDay) : (initialStartDate || "")
  );
  const [endDate, setEndDate] = useState(
    event?.endDateTime 
      ? isoToLocalDateTime(event.endDateTime, event.isAllDay)
      : startDate && !event?.isAllDay ? getDefaultEndDate(startDate) : ""
  );
  const [isAllDay, setIsAllDay] = useState(event?.isAllDay || false);
  const [location, setLocation] = useState(event?.location || "");
  const [categoryId, setCategoryId] = useState(event?.categoryId || "");
  const [participantIds, setParticipantIds] = useState<Set<string>>(
    new Set(event?.participantIds || [])
  );
  const [isRecurring, setIsRecurring] = useState(!!event?.recurringType);
  const [recurringType, setRecurringType] = useState<"DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY" | "">(
    (event?.recurringType as "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY") || ""
  );
  const [recurringInterval, setRecurringInterval] = useState(
    event?.recurringInterval?.toString() || "1"
  );
  const [recurringEndDate, setRecurringEndDate] = useState(
    event?.recurringEndDate ? new Date(event.recurringEndDate).toISOString().split("T")[0] : ""
  );
  const [recurringEndCount, setRecurringEndCount] = useState(
    event?.recurringEndCount?.toString() || ""
  );
  const [recurringEndType, setRecurringEndType] = useState<"never" | "date" | "count">(
    event?.recurringEndDate ? "date" : event?.recurringEndCount ? "count" : "never"
  );
  const [showRecurringTypeDropdown, setShowRecurringTypeDropdown] = useState(false);
  const [isTask, setIsTask] = useState(event?.isTask || false);
  const [xpPoints, setXpPoints] = useState(event?.xpPoints?.toString() || "1");
  const [isRequired, setIsRequired] = useState(event?.isRequired !== undefined ? event.isRequired : true);

  // When isTask is true, automatically set isAllDay to true
  useEffect(() => {
    if (isTask) {
      setIsAllDay(true);
    }
  }, [isTask]);

  // Update startDate when initialStartDate changes (only for new events, not when editing)
  useEffect(() => {
    if (!event && initialStartDate) {
      setStartDate(initialStartDate);
      // Set isAllDay based on format: YYYY-MM-DD = all-day, YYYY-MM-DDTHH:mm = with time
      const isAllDayFormat = !initialStartDate.includes("T");
      setIsAllDay(isAllDayFormat);
      // Also update endDate if it's a datetime (not all-day)
      if (!isAllDayFormat) {
        setEndDate(getDefaultEndDate(initialStartDate));
      }
    }
  }, [initialStartDate, event]);

  // Update end date when start date changes (only for new events, not when editing)
  const handleStartDateChange = (newStartDate: string) => {
    setStartDate(newStartDate);
    // Only auto-update end date if this is a new event (not editing)
    if (!event && newStartDate && !isAllDay) {
      setEndDate(getDefaultEndDate(newStartDate));
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !startDate) {
      return;
    }

    // Validate recurring fields
    if (isRecurring && !recurringType) {
      alert("Välj en typ för återkommande event.");
      return;
    }

    // For all-day events, the input type is "date" which gives us YYYY-MM-DD
    // We need to convert it to YYYY-MM-DDTHH:mm format (use 00:00 for start, 23:59 for end)
    let startDateTimeStr: string;
    let endDateTimeStr: string | null = null;
    
    if (isAllDay) {
      // All-day: convert date (YYYY-MM-DD) to datetime (YYYY-MM-DDTHH:mm)
      startDateTimeStr = `${startDate}T00:00`;
      if (endDate) {
        endDateTimeStr = `${endDate}T23:59`;
      }
    } else {
      // Regular event: already in YYYY-MM-DDTHH:mm format from datetime-local input
      startDateTimeStr = startDate;
      endDateTimeStr = endDate || null;
    }

    onSave({
      title: title.trim(),
      description: description.trim() || undefined,
      startDateTime: startDateTimeStr,
      endDateTime: endDateTimeStr,
      isAllDay: isTask ? true : isAllDay, // Tasks are always all-day
      location: location.trim() || undefined,
      categoryId: categoryId || undefined,
      participantIds: Array.from(participantIds),
      recurringType: isRecurring && recurringType ? (recurringType as "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY") : null,
      recurringInterval: isRecurring && recurringType && recurringInterval ? parseInt(recurringInterval, 10) : null,
      recurringEndDate: isRecurring && recurringType && recurringEndType === "date" && recurringEndDate ? recurringEndDate : null,
      recurringEndCount: isRecurring && recurringType && recurringEndType === "count" && recurringEndCount ? parseInt(recurringEndCount, 10) : null,
      isTask: isTask,
      xpPoints: isTask ? (xpPoints ? parseInt(xpPoints, 10) : 1) : null,
      isRequired: isTask ? isRequired : true,
    });
  };

  const toggleParticipant = (memberId: string) => {
    setParticipantIds((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(memberId)) {
        newSet.delete(memberId);
      } else {
        newSet.add(memberId);
      }
      return newSet;
    });
  };

  return (
    <section className="card">
      <h3 style={{ marginTop: 0 }}>{event ? "Redigera event" : "Nytt event"}</h3>
      <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
        <div>
          <label htmlFor="title" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
            Titel *
          </label>
          <input
            id="title"
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            style={{ width: "100%", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
          />
        </div>

        <div>
          <label htmlFor="description" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
            Beskrivning
          </label>
          <textarea
            id="description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            style={{ width: "100%", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
          />
        </div>

        {!isTask && (
          <div>
            <label style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
              <input
                type="checkbox"
                checked={isAllDay}
                onChange={(e) => {
                  setIsAllDay(e.target.checked);
                  // When switching to all-day, clear end date if it was set
                  // When switching from all-day, set default end date if not set
                  if (e.target.checked) {
                    // Switching to all-day - keep end date if it exists, but user can clear it
                  } else {
                    // Switching from all-day - set default end date if not set
                    if (!endDate && startDate) {
                      setEndDate(getDefaultEndDate(startDate));
                    }
                  }
                }}
              />
              <span>Hela dagen</span>
            </label>
          </div>
        )}

        <div>
          <label htmlFor="startDate" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
            Startdatum/tid *
          </label>
          <input
            id="startDate"
            type={isAllDay ? "date" : "datetime-local"}
            value={startDate}
            onChange={(e) => handleStartDateChange(e.target.value)}
            required
            style={{ width: "100%", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
          />
        </div>

        <div>
          <label htmlFor="endDate" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
            Slutdatum/tid
          </label>
          <input
            id="endDate"
            type={isAllDay ? "date" : "datetime-local"}
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            style={{ width: "100%", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
          />
        </div>

        <div>
          <label style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
            <input
              type="checkbox"
              checked={isRecurring}
              onChange={(e) => {
                setIsRecurring(e.target.checked);
                if (!e.target.checked) {
                  setRecurringType("");
                  setRecurringInterval("1");
                  setRecurringEndDate("");
                  setRecurringEndCount("");
                  setRecurringEndType("never");
                }
              }}
            />
            <span>Återkommande</span>
          </label>
        </div>

        {isRecurring && (
          <>
            <div style={{ position: "relative" }}>
              <label htmlFor="recurringType" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
                Typ
              </label>
              <button
                type="button"
                id="recurringType"
                onClick={() => setShowRecurringTypeDropdown(!showRecurringTypeDropdown)}
                style={{
                  width: "100%",
                  padding: "12px",
                  borderRadius: "6px",
                  border: "1px solid #ddd",
                  fontSize: "1rem",
                  backgroundColor: "white",
                  minHeight: "44px",
                  textAlign: "left",
                  cursor: "pointer",
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                }}
              >
                <span>
                  {recurringType === "DAILY" && "Dagligen"}
                  {recurringType === "WEEKLY" && "Veckovis"}
                  {recurringType === "MONTHLY" && "Månadsvis"}
                  {recurringType === "YEARLY" && "Årligen"}
                  {!recurringType && "Välj typ"}
                </span>
                <span style={{ fontSize: "0.8rem", color: "#6b6b6b" }}>▼</span>
              </button>
              {showRecurringTypeDropdown && (
                <>
                  <div
                    style={{
                      position: "fixed",
                      inset: 0,
                      zIndex: 100,
                    }}
                    onClick={() => setShowRecurringTypeDropdown(false)}
                  />
                  <div
                    style={{
                      position: "absolute",
                      top: "100%",
                      left: 0,
                      right: 0,
                      marginTop: "4px",
                      backgroundColor: "white",
                      border: "1px solid #ddd",
                      borderRadius: "6px",
                      boxShadow: "0 4px 12px rgba(0, 0, 0, 0.15)",
                      zIndex: 101,
                      maxHeight: "200px",
                      overflowY: "auto",
                    }}
                  >
                    <button
                      type="button"
                      onClick={() => {
                        setRecurringType("DAILY");
                        setShowRecurringTypeDropdown(false);
                      }}
                      style={{
                        width: "100%",
                        padding: "12px",
                        textAlign: "left",
                        border: "none",
                        backgroundColor: recurringType === "DAILY" ? "rgba(184, 230, 184, 0.2)" : "transparent",
                        cursor: "pointer",
                        fontSize: "1rem",
                        borderBottom: "1px solid #f0f0f0",
                      }}
                    >
                      Dagligen
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setRecurringType("WEEKLY");
                        setShowRecurringTypeDropdown(false);
                      }}
                      style={{
                        width: "100%",
                        padding: "12px",
                        textAlign: "left",
                        border: "none",
                        backgroundColor: recurringType === "WEEKLY" ? "rgba(184, 230, 184, 0.2)" : "transparent",
                        cursor: "pointer",
                        fontSize: "1rem",
                        borderBottom: "1px solid #f0f0f0",
                      }}
                    >
                      Veckovis
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setRecurringType("MONTHLY");
                        setShowRecurringTypeDropdown(false);
                      }}
                      style={{
                        width: "100%",
                        padding: "12px",
                        textAlign: "left",
                        border: "none",
                        backgroundColor: recurringType === "MONTHLY" ? "rgba(184, 230, 184, 0.2)" : "transparent",
                        cursor: "pointer",
                        fontSize: "1rem",
                        borderBottom: "1px solid #f0f0f0",
                      }}
                    >
                      Månadsvis
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setRecurringType("YEARLY");
                        setShowRecurringTypeDropdown(false);
                      }}
                      style={{
                        width: "100%",
                        padding: "12px",
                        textAlign: "left",
                        border: "none",
                        backgroundColor: recurringType === "YEARLY" ? "rgba(184, 230, 184, 0.2)" : "transparent",
                        cursor: "pointer",
                        fontSize: "1rem",
                      }}
                    >
                      Årligen
                    </button>
                  </div>
                </>
              )}
            </div>

            {recurringType && (
              <div>
                <label htmlFor="recurringInterval" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
                  Varje
                </label>
                <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                  <input
                    id="recurringInterval"
                    type="number"
                    min="1"
                    value={recurringInterval}
                    onChange={(e) => setRecurringInterval(e.target.value)}
                    required
                    style={{ width: "60px", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
                  />
                  <span>
                    {recurringType === "DAILY" && "dag(ar)"}
                    {recurringType === "WEEKLY" && "vecka(or)"}
                    {recurringType === "MONTHLY" && "månad(er)"}
                    {recurringType === "YEARLY" && "år"}
                  </span>
                </div>
              </div>
            )}

            {recurringType && (
              <div>
                <label style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>
                  Slutar
                </label>
                <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                  <label style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                    <input
                      type="radio"
                      name="recurringEndType"
                      checked={recurringEndType === "never"}
                      onChange={() => setRecurringEndType("never")}
                    />
                    <span>Aldrig</span>
                  </label>
                  <label style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                    <input
                      type="radio"
                      name="recurringEndType"
                      checked={recurringEndType === "date"}
                      onChange={() => setRecurringEndType("date")}
                    />
                    <span>På datum:</span>
                    <input
                      type="date"
                      value={recurringEndDate}
                      onChange={(e) => setRecurringEndDate(e.target.value)}
                      disabled={recurringEndType !== "date"}
                      style={{ padding: "4px", borderRadius: "4px", border: "1px solid #ddd" }}
                    />
                  </label>
                  <label style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                    <input
                      type="radio"
                      name="recurringEndType"
                      checked={recurringEndType === "count"}
                      onChange={() => setRecurringEndType("count")}
                    />
                    <span>Efter</span>
                    <input
                      type="number"
                      min="1"
                      value={recurringEndCount}
                      onChange={(e) => setRecurringEndCount(e.target.value)}
                      disabled={recurringEndType !== "count"}
                      style={{ width: "60px", padding: "4px", borderRadius: "4px", border: "1px solid #ddd" }}
                    />
                    <span>förekomster</span>
                  </label>
                </div>
              </div>
            )}
          </>
        )}

        <div>
          <label style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
            <input
              type="checkbox"
              checked={isTask}
              onChange={(e) => {
                setIsTask(e.target.checked);
                if (!e.target.checked) {
                  // Reset task-related fields when unchecked
                  setXpPoints("1");
                  setIsRequired(true);
                }
              }}
            />
            <span>Dagens Att Göra (ge poäng till djur)</span>
          </label>
        </div>

        {isTask && (
          <>
            <div>
              <label htmlFor="xpPoints" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
                XP-poäng
              </label>
              <input
                id="xpPoints"
                type="number"
                min="0"
                value={xpPoints}
                onChange={(e) => setXpPoints(e.target.value)}
                style={{ width: "100%", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
              />
            </div>
            <div>
              <label style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
                <input
                  type="checkbox"
                  checked={isRequired}
                  onChange={(e) => setIsRequired(e.target.checked)}
                />
                <span>Obligatorisk</span>
              </label>
              <p style={{ margin: "4px 0 0", fontSize: "0.85rem", color: "#718096" }}>
                Om avmarkerad blir det en extra syssla (frivillig)
              </p>
            </div>
          </>
        )}

        <div>
          <label htmlFor="location" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
            Plats
          </label>
          <input
            id="location"
            type="text"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            style={{ width: "100%", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
          />
        </div>

        <div style={{ position: "relative" }}>
          <label htmlFor="category" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
            Kategori
          </label>
          <button
            type="button"
            id="category"
            onClick={() => setShowCategoryDropdown(!showCategoryDropdown)}
            style={{
              width: "100%",
              padding: "12px",
              borderRadius: "6px",
              border: "1px solid #ddd",
              fontSize: "1rem",
              backgroundColor: "white",
              minHeight: "44px",
              textAlign: "left",
              cursor: "pointer",
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
            }}
          >
            <span>
              {categoryId
                ? categories.find((c) => c.id === categoryId)?.name || "Ingen kategori"
                : "Ingen kategori"}
            </span>
            <span style={{ fontSize: "0.8rem", color: "#6b6b6b" }}>▼</span>
          </button>
          {showCategoryDropdown && (
            <>
              <div
                style={{
                  position: "fixed",
                  inset: 0,
                  zIndex: 100,
                }}
                onClick={() => setShowCategoryDropdown(false)}
              />
              <div
                style={{
                  position: "absolute",
                  top: "100%",
                  left: 0,
                  right: 0,
                  marginTop: "4px",
                  backgroundColor: "white",
                  border: "1px solid #ddd",
                  borderRadius: "6px",
                  boxShadow: "0 4px 12px rgba(0, 0, 0, 0.15)",
                  zIndex: 101,
                  maxHeight: "200px",
                  overflowY: "auto",
                }}
              >
                <button
                  type="button"
                  onClick={() => {
                    setCategoryId("");
                    setShowCategoryDropdown(false);
                  }}
                  style={{
                    width: "100%",
                    padding: "12px",
                    textAlign: "left",
                    border: "none",
                    backgroundColor: categoryId === "" ? "rgba(184, 230, 184, 0.2)" : "transparent",
                    cursor: "pointer",
                    fontSize: "1rem",
                    borderBottom: "1px solid #f0f0f0",
                  }}
                >
                  Ingen kategori
                </button>
                {categories.map((cat) => (
                  <button
                    key={cat.id}
                    type="button"
                    onClick={() => {
                      setCategoryId(cat.id);
                      setShowCategoryDropdown(false);
                    }}
                    style={{
                      width: "100%",
                      padding: "12px",
                      textAlign: "left",
                      border: "none",
                      backgroundColor: categoryId === cat.id ? "rgba(184, 230, 184, 0.2)" : "transparent",
                      cursor: "pointer",
                      fontSize: "1rem",
                      borderBottom: categories.indexOf(cat) < categories.length - 1 ? "1px solid #f0f0f0" : "none",
                    }}
                  >
                    {cat.name}
                  </button>
                ))}
              </div>
            </>
          )}
        </div>

        <div>
          <label style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>
            Deltagare
          </label>
          <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
            {members.map((member) => (
              <label key={member.id} style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <input
                  type="checkbox"
                  checked={participantIds.has(member.id)}
                  onChange={() => toggleParticipant(member.id)}
                />
                <span>{member.name}</span>
              </label>
            ))}
          </div>
        </div>

        <div style={{ display: "flex", gap: "8px", marginTop: "12px", justifyContent: "space-between" }}>
          <div style={{ display: "flex", gap: "8px" }}>
            <button type="submit" className="button-primary">
              {event ? "Spara ändringar" : "Skapa event"}
            </button>
            <button type="button" onClick={onCancel} className="todo-action-button">
              Avbryt
            </button>
          </div>
          {event && onDelete && (
            // ASSISTANT can only delete events they created, PARENT can delete any
            (currentUserRole === "PARENT" || (currentUserRole === "ASSISTANT" && event.createdById === currentUserId)) && (
            <button 
              type="button" 
              onClick={() => onDelete()} 
              className="todo-action-button-danger"
              style={{ borderRadius: "10px" }}
            >
              Ta bort
            </button>
            )
          )}
        </div>
      </form>
    </section>
  );
}

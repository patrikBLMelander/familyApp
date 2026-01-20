import { useCallback } from "react";
import {
  createCalendarEvent,
  updateCalendarEvent,
  deleteCalendarEvent,
  CalendarEventResponse,
} from "../../../shared/api/calendar";
import { EventFormData } from "../types/eventForm";
import { formatDateForEventForm } from "../utils/dateFormatters";

type LoadDataCallback = () => Promise<void>;
type SetErrorCallback = (error: string | null) => void;
type SetShowCreateFormCallback = (show: boolean) => void;
type SetEditingEventCallback = (event: CalendarEventResponse | null) => void;
type SetQuickAddTitleCallback = (title: string) => void;
type SetShowQuickAddCallback = (show: boolean) => void;
type LoadTasksCallback = () => Promise<void>;
type LoadTasksForAllMembersCallback = () => Promise<void>;

export function useCalendarEvents(
  loadData: LoadDataCallback,
  setError: SetErrorCallback,
  setShowCreateForm: SetShowCreateFormCallback,
  setEditingEvent: SetEditingEventCallback,
  currentMemberId: string | null,
  selectedDate: Date,
  showAllMembers: boolean,
  loadTasks: LoadTasksCallback,
  loadTasksForAllMembers: LoadTasksForAllMembersCallback,
  setQuickAddTitle: SetQuickAddTitleCallback,
  setShowQuickAdd: SetShowQuickAddCallback
) {
  const handleCreateEvent = useCallback(async (eventData: EventFormData) => {
    try {
      await createCalendarEvent(
        eventData.title,
        eventData.startDateTime,
        eventData.endDateTime,
        eventData.isAllDay,
        eventData.description,
        eventData.categoryId,
        eventData.location,
        eventData.participantIds,
        eventData.recurringType,
        eventData.recurringInterval,
        eventData.recurringEndDate,
        eventData.recurringEndCount,
        eventData.isTask,
        eventData.xpPoints,
        eventData.isRequired
      );
      await loadData();
      setShowCreateForm(false);
    } catch (e) {
      setError("Kunde inte skapa event.");
      console.error("Error creating event:", e);
    }
  }, [loadData, setError, setShowCreateForm]);

  const handleUpdateEvent = useCallback(async (
    eventId: string,
    eventData: EventFormData
  ) => {
    try {
      await updateCalendarEvent(
        eventId,
        eventData.title,
        eventData.startDateTime,
        eventData.endDateTime,
        eventData.isAllDay,
        eventData.description,
        eventData.categoryId,
        eventData.location,
        eventData.participantIds,
        eventData.recurringType,
        eventData.recurringInterval,
        eventData.recurringEndDate,
        eventData.recurringEndCount,
        eventData.isTask,
        eventData.xpPoints,
        eventData.isRequired
      );
      await loadData();
      setEditingEvent(null);
    } catch (e) {
      setError("Kunde inte uppdatera event.");
      console.error("Error updating event:", e);
    }
  }, [loadData, setError, setEditingEvent]);

  const handleDeleteEvent = useCallback(async (eventId: string) => {
    if (!confirm("Är du säker på att du vill ta bort detta event?")) {
      return;
    }
    try {
      await deleteCalendarEvent(eventId);
      await loadData();
      setEditingEvent(null);
      setShowCreateForm(false);
    } catch (e) {
      setError("Kunde inte ta bort event.");
      console.error("Error deleting event:", e);
    }
  }, [loadData, setError, setEditingEvent, setShowCreateForm]);

  const handleQuickAdd = useCallback(async (quickAddTitle: string) => {
    if (!currentMemberId || !quickAddTitle.trim()) return;

    try {
      // Format date as YYYY-MM-DD for all-day event
      const dateStr = formatDateForEventForm(selectedDate);
      
      // Create all-day task with defaults
      await createCalendarEvent(
        quickAddTitle.trim(),
        `${dateStr}T00:00`, // startDateTime
        null, // endDateTime (null for all-day)
        true, // isAllDay
        undefined, // description
        undefined, // categoryId
        undefined, // location
        [currentMemberId], // participantIds
        null, // recurringType
        null, // recurringInterval
        null, // recurringEndDate
        null, // recurringEndCount
        true, // isTask
        1, // xpPoints
        true // isRequired
      );
      
      // Clear input and reload tasks
      setQuickAddTitle("");
      setShowQuickAdd(false);
      if (showAllMembers) {
        await loadTasksForAllMembers();
      } else {
        await loadTasks();
      }
    } catch (e) {
      console.error("Error creating quick task:", e);
      setError("Kunde inte skapa task.");
    }
  }, [
    currentMemberId,
    selectedDate,
    showAllMembers,
    loadTasks,
    loadTasksForAllMembers,
    setQuickAddTitle,
    setShowQuickAdd,
    setError,
  ]);

  return {
    handleCreateEvent,
    handleUpdateEvent,
    handleDeleteEvent,
    handleQuickAdd,
  };
}

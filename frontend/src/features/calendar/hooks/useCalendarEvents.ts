import { useCallback } from "react";
import {
  createCalendarEvent,
  updateCalendarEvent,
  deleteCalendarEvent,
  CalendarEventResponse,
} from "../../../shared/api/calendar";
import { EventFormData } from "../types/eventForm";
import { formatDateForEventForm } from "../utils/dateFormatters";
import { extractErrorMessage } from "../utils/errorHandling";

type LoadDataCallback = (forceReplace?: boolean) => Promise<void>;
type SetErrorCallback = (error: string | null) => void;
type SetShowCreateFormCallback = (show: boolean) => void;
type SetEditingEventCallback = (event: CalendarEventResponse | null) => void;
type SetQuickAddTitleCallback = (title: string) => void;
type SetShowQuickAddCallback = (show: boolean) => void;
type LoadTasksCallback = () => Promise<void>;
type LoadTasksForAllMembersCallback = () => Promise<void>;

/**
 * Custom hook for managing calendar event CRUD operations.
 * 
 * Handles:
 * - Creating new events
 * - Updating existing events
 * - Deleting events
 * - Quick add functionality (creating tasks quickly)
 * 
 * All operations include error handling and automatic data reloading.
 * 
 * @param loadData - Callback to reload all calendar data
 * @param setError - Callback to set error state
 * @param setShowCreateForm - Callback to show/hide create form
 * @param setEditingEvent - Callback to set event being edited
 * @param currentMemberId - Current logged-in member ID
 * @param selectedDate - Selected date for quick add
 * @param showAllMembers - Whether showing tasks for all members
 * @param loadTasks - Callback to reload tasks for current member
 * @param loadTasksForAllMembers - Callback to reload tasks for all members
 * @param setQuickAddTitle - Callback to set quick add title
 * @param setShowQuickAdd - Callback to show/hide quick add form
 * @returns Event CRUD operation handlers
 */
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
      const errorMessage = extractErrorMessage(e, "Kunde inte skapa event.");
      setError(errorMessage);
      console.error("Error creating event:", e);
    }
  }, [loadData, setError, setShowCreateForm]);

  const handleUpdateEvent = useCallback(async (
    eventId: string,
    eventData: EventFormData,
    scope?: "THIS" | "THIS_AND_FOLLOWING" | "ALL",
    occurrenceDate?: string
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
        eventData.isRequired,
        scope,
        occurrenceDate
      );
      await loadData();
      setEditingEvent(null);
    } catch (e) {
      const errorMessage = extractErrorMessage(e, "Kunde inte uppdatera event.");
      setError(errorMessage);
      console.error("Error updating event:", e);
    }
  }, [loadData, setError, setEditingEvent]);

  const handleDeleteEvent = useCallback(async (
    eventId: string,
    scope?: "THIS" | "THIS_AND_FOLLOWING" | "ALL",
    occurrenceDate?: string
  ) => {
    // Only show confirm dialog if scope is not provided (old behavior for non-recurring events)
    if (!scope && !confirm("Är du säker på att du vill ta bort detta event?")) {
      return;
    }
    try {
      await deleteCalendarEvent(eventId, scope, occurrenceDate);
      // Force replace to ensure deleted event is removed from list
      await loadData(true);
      setEditingEvent(null);
      setShowCreateForm(false);
    } catch (e) {
      const errorMessage = extractErrorMessage(e, "Kunde inte ta bort event.");
      setError(errorMessage);
      console.error("Error deleting event:", e);
    }
  }, [loadData, setError, setEditingEvent, setShowCreateForm]);

  const handleQuickAdd = useCallback(async (quickAddTitle: string, memberId?: string | null) => {
    // Use provided memberId or fall back to currentMemberId
    const targetMemberId = memberId || currentMemberId;
    if (!targetMemberId || !quickAddTitle.trim()) return;

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
        [targetMemberId], // participantIds
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
      try {
        if (showAllMembers) {
          await loadTasksForAllMembers();
        } else {
          await loadTasks();
        }
      } catch (reloadError) {
        console.error("Error reloading tasks after quick add:", reloadError);
        // Task was created successfully, but reload failed
        const reloadErrorMessage = extractErrorMessage(reloadError, "Task skapad, men kunde inte uppdatera listan. Ladda om sidan.");
        setError(reloadErrorMessage);
      }
    } catch (e) {
      console.error("Error creating quick task:", e);
      const errorMessage = extractErrorMessage(e, "Kunde inte skapa task.");
      setError(errorMessage);
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

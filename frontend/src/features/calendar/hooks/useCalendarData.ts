import { useState, useCallback, useRef } from "react";
import {
  fetchCalendarEvents,
  CalendarEventResponse,
  fetchCalendarCategories,
  CalendarEventCategoryResponse,
  fetchTasksForDate,
  toggleTaskCompletion,
  CalendarTaskWithCompletionResponse,
  getTaskCompletionsForMember,
  CalendarEventTaskCompletionResponse,
} from "../../../shared/api/calendar";
import { fetchAllFamilyMembers, FamilyMemberResponse, getMemberByDeviceToken } from "../../../shared/api/familyMembers";
import { CalendarViewType, CALENDAR_VIEW_TYPES } from "../constants";
import { extractErrorMessage } from "../utils/errorHandling";

/**
 * Custom hook for managing calendar data fetching and state.
 * 
 * Handles:
 * - Loading events, categories, and members
 * - Loading tasks with completion status
 * - Task completion toggling
 * - Current user role detection
 * 
 * Optimizes data fetching based on view type:
 * - Rolling view: Today to 30 days ahead
 * - Week view: 7 days before to 7 days after current week
 * - Month view: Full month range
 * 
 * @param viewType - Current calendar view type
 * @param currentWeek - Current week date (for week view)
 * @param currentMonth - Current month date (for month view)
 * @param selectedDate - Selected date (for rolling view)
 * @param showTasksOnly - Whether to show only tasks
 * @param showAllMembers - Whether to show tasks for all members
 * @param currentMemberId - Current logged-in member ID
 * @returns Calendar data, loading state, error state, and data loading functions
 */
export function useCalendarData(
  viewType: CalendarViewType,
  currentWeek: Date,
  currentMonth: Date,
  selectedDate: Date,
  showTasksOnly: boolean,
  showAllMembers: boolean,
  currentMemberId: string | null
) {
  const [events, setEvents] = useState<CalendarEventResponse[]>([]);
  const [categories, setCategories] = useState<CalendarEventCategoryResponse[]>([]);
  const [members, setMembers] = useState<FamilyMemberResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tasksWithCompletion, setTasksWithCompletion] = useState<CalendarTaskWithCompletionResponse[]>([]);
  const [tasksByMember, setTasksByMember] = useState<Map<string, CalendarTaskWithCompletionResponse[]>>(new Map());
  const [currentUserRole, setCurrentUserRole] = useState<"CHILD" | "ASSISTANT" | "PARENT" | null>(null);
  const [rollingViewEndDate, setRollingViewEndDate] = useState<Date | null>(null);

  const loadCurrentMember = useCallback(async () => {
    const deviceToken = localStorage.getItem("deviceToken");
    if (deviceToken) {
      try {
        const member = await getMemberByDeviceToken(deviceToken);
        setCurrentUserRole(member.role);
        return member.id;
      } catch (e) {
        console.error("Error loading current member:", e);
        return null;
      }
    }
    return null;
  }, []);

  const loadTasks = useCallback(async (memberId: string | null, date: Date) => {
    if (!memberId) return;
    try {
      const tasks = await fetchTasksForDate(memberId, date);
      // Sort tasks: required first, then by title
      const sortedTasks = [...tasks].sort((a, b) => {
        if (a.event.isRequired !== b.event.isRequired) {
          return a.event.isRequired ? -1 : 1; // Required first
        }
        return a.event.title.localeCompare(b.event.title);
      });
      setTasksWithCompletion(sortedTasks);
    } catch (e) {
      console.error("Error loading tasks:", e);
      setTasksWithCompletion([]);
    }
  }, []);

  const loadTasksForAllMembers = useCallback(async (allMembers: FamilyMemberResponse[], date: Date) => {
    try {
      const allTasks = new Map<string, CalendarTaskWithCompletionResponse[]>();
      
      // Optimize: Fetch events once for the date (shared across all members)
      const dateStart = new Date(date);
      dateStart.setHours(0, 0, 0, 0);
      const dateEnd = new Date(date);
      dateEnd.setHours(23, 59, 59, 999);
      
      // Fetch events and completions in parallel
      const [events, ...completionPromises] = await Promise.all([
        fetchCalendarEvents(dateStart, dateEnd),
        ...allMembers.map(member => getTaskCompletionsForMember(member.id))
      ]);
      
      // Get all completions
      const allCompletions = await Promise.all(completionPromises);
      const dateStr = date.toISOString().split("T")[0]; // YYYY-MM-DD
      
      // Process tasks for each member in parallel
      const memberTasksPromises = allMembers.map(async (member, index) => {
        try {
          // Filter to only tasks where member is a participant
          const taskEvents = events.filter(event => 
            event.isTask && event.participantIds.includes(member.id)
          );
          
          // Get completions for this member for the selected date
          const memberCompletions = allCompletions[index];
          const completionMap = new Map<string, CalendarEventTaskCompletionResponse>();
          memberCompletions.forEach(completion => {
            if (completion.occurrenceDate === dateStr) {
              completionMap.set(completion.eventId, completion);
            }
          });
          
          // Map events to tasks with completion status
          const tasks = taskEvents.map(event => ({
            event,
            completed: completionMap.has(event.id),
          }));
          
          // Sort tasks: required first, then by title
          const sortedTasks = [...tasks].sort((a, b) => {
            if (a.event.isRequired !== b.event.isRequired) {
              return a.event.isRequired ? -1 : 1; // Required first
            }
            return a.event.title.localeCompare(b.event.title);
          });
          
          return { memberId: member.id, tasks: sortedTasks };
        } catch (e) {
          console.error(`Error processing tasks for member ${member.id}:`, e);
          return { memberId: member.id, tasks: [] };
        }
      });
      
      // Wait for all members to be processed
      const memberTasksResults = await Promise.all(memberTasksPromises);
      
      // Build the map
      memberTasksResults.forEach(({ memberId, tasks }) => {
        if (tasks.length > 0) {
          allTasks.set(memberId, tasks);
        }
      });
      
      setTasksByMember(allTasks);
    } catch (e) {
      console.error("Error loading tasks for all members:", e);
      setTasksByMember(new Map());
    }
  }, []);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      
      // Optimize: For rolling view, only fetch events from today up to 30 days ahead
      // For week/month view, fetch a wider range to allow navigation
      let startDate: Date | undefined;
      let endDate: Date | undefined;
      
      if (viewType === CALENDAR_VIEW_TYPES.ROLLING) {
        // Rolling view: today to 30 days ahead
        // Reset end date on initial load (when rollingViewEndDate is null)
        if (!rollingViewEndDate) {
          startDate = new Date();
          startDate.setHours(0, 0, 0, 0);
          endDate = new Date();
          endDate.setDate(endDate.getDate() + 30);
          endDate.setHours(23, 59, 59, 999);
          setRollingViewEndDate(endDate);
        } else {
          // Use existing end date if already set (don't reset on every call)
          startDate = new Date();
          startDate.setHours(0, 0, 0, 0);
          endDate = rollingViewEndDate;
        }
      } else if (viewType === CALENDAR_VIEW_TYPES.WEEK) {
        // Week view: 7 days before current week to 7 days after (3 weeks total)
        const weekStart = new Date(currentWeek);
        const day = weekStart.getDay();
        const diff = weekStart.getDate() - day + (day === 0 ? -6 : 1);
        weekStart.setDate(diff);
        weekStart.setHours(0, 0, 0, 0);
        
        startDate = new Date(weekStart);
        startDate.setDate(startDate.getDate() - 7);
        
        endDate = new Date(weekStart);
        endDate.setDate(endDate.getDate() + 14); // 7 days before + 7 days week + 7 days after
        endDate.setHours(23, 59, 59, 999);
      } else if (viewType === CALENDAR_VIEW_TYPES.MONTH) {
        // Month view: first day of current month to last day (with some buffer)
        const year = currentMonth.getFullYear();
        const month = currentMonth.getMonth();
        startDate = new Date(year, month, 1);
        startDate.setHours(0, 0, 0, 0);
        
        endDate = new Date(year, month + 1, 0); // Last day of month
        endDate.setHours(23, 59, 59, 999);
      } else {
        // Default: if viewType is not set yet, use rolling view logic (today to 30 days)
        startDate = new Date();
        startDate.setHours(0, 0, 0, 0);
        endDate = new Date();
        endDate.setDate(endDate.getDate() + 30);
        endDate.setHours(23, 59, 59, 999);
      }
      
      const [eventsData, categoriesData, membersData] = await Promise.all([
        fetchCalendarEvents(startDate, endDate),
        fetchCalendarCategories(),
        fetchAllFamilyMembers(),
      ]);
      
      // For rolling view, only merge if we're extending the range (not initial load)
      // For other views or initial load, always replace
      if (viewType === CALENDAR_VIEW_TYPES.ROLLING && rollingViewEndDate && events.length > 0) {
        // This is extending the range - merge events
        // IMPORTANT: For recurring events, same ID can have different startDateTime
        // So we need to use both ID and startDateTime to identify unique events
        setEvents(prev => {
          // Create a Set of unique identifiers: "id:startDateTime"
          const existingEventKeys = new Set(
            prev.map(e => `${e.id}:${e.startDateTime}`)
          );
          const newEvents = eventsData.filter(
            e => !existingEventKeys.has(`${e.id}:${e.startDateTime}`)
          );
          return [...prev, ...newEvents].sort((a, b) => 
            new Date(a.startDateTime).getTime() - new Date(b.startDateTime).getTime()
          );
        });
      } else {
        // Initial load or view change - replace events
        setEvents(eventsData);
      }
      
      // Reset rolling view end date for non-rolling views
      if (viewType !== CALENDAR_VIEW_TYPES.ROLLING) {
        setRollingViewEndDate(null);
      }
      
      setCategories(categoriesData);
      setMembers(membersData);
    } catch (e) {
      const errorMessage = extractErrorMessage(e, "Kunde inte hämta kalenderdata.");
      setError(errorMessage);
      console.error("Error loading calendar data:", e);
    } finally {
      setLoading(false);
    }
  }, [viewType, currentWeek, currentMonth]); // Removed rollingViewEndDate to prevent infinite loops

  const loadMoreEventsRef = useRef(false);
  
  const loadMoreEvents = useCallback(async () => {
    if (viewType !== CALENDAR_VIEW_TYPES.ROLLING) {
      return;
    }
    
    // Prevent multiple simultaneous calls
    if (loadMoreEventsRef.current) {
      return;
    }
    
    const currentEndDate = rollingViewEndDate;
    if (!currentEndDate) {
      return;
    }
    
    loadMoreEventsRef.current = true;
    
    try {
      // Extend date range by 30 more days
      const newEndDate = new Date(currentEndDate);
      newEndDate.setDate(newEndDate.getDate() + 30);
      newEndDate.setHours(23, 59, 59, 999);
      
      // IMPORTANT: Fetch only events AFTER currentEndDate to avoid duplicates
      // We already have events from today to currentEndDate, so we only need
      // events from currentEndDate+1 to newEndDate
      const fetchStartDate = new Date(currentEndDate);
      fetchStartDate.setDate(fetchStartDate.getDate() + 1); // Start from day after currentEndDate
      fetchStartDate.setHours(0, 0, 0, 0);
      
      // Fetch events in the NEW range only (after currentEndDate)
      const eventsData = await fetchCalendarEvents(fetchStartDate, newEndDate);
      
      // Merge with existing events, avoiding duplicates
      // IMPORTANT: For recurring events, same ID can have different startDateTime
      // So we need to use both ID and startDateTime to identify unique events
      setEvents(prev => {
        // Create a Set of unique identifiers: "id:startDateTime"
        const existingEventKeys = new Set(
          prev.map(e => `${e.id}:${e.startDateTime}`)
        );
        
        // Filter out events that already exist (using id:startDateTime as key)
        const newEvents = eventsData.filter(
          e => !existingEventKeys.has(`${e.id}:${e.startDateTime}`)
        );
        
        return [...prev, ...newEvents].sort((a, b) => 
          new Date(a.startDateTime).getTime() - new Date(b.startDateTime).getTime()
        );
      });
      
      // Update the end date for next load
      setRollingViewEndDate(newEndDate);
    } catch (e) {
      console.error("Error loading more events:", e);
    } finally {
      loadMoreEventsRef.current = false;
    }
  }, [viewType, rollingViewEndDate]);

  const handleToggleTask = useCallback(async (
    eventId: string,
    memberId: string | null,
    date: Date,
    showAll: boolean
  ) => {
    if (!memberId) return;
    
    // Optimistic update
    if (showAll) {
      setTasksByMember((prev) => {
        const newMap = new Map(prev);
        const memberTasks = newMap.get(memberId) || [];
        newMap.set(
          memberId,
          memberTasks.map((task) =>
            task.event.id === eventId
              ? { ...task, completed: !task.completed }
              : task
          )
        );
        return newMap;
      });
    } else {
      setTasksWithCompletion((prev) =>
        prev.map((task) =>
          task.event.id === eventId
            ? { ...task, completed: !task.completed }
            : task
        )
      );
    }

    try {
      await toggleTaskCompletion(eventId, memberId, date);
      // Reload tasks to get updated state
      if (showAll) {
        // Use current members state
        await loadTasksForAllMembers(members, date);
      } else {
        await loadTasks(memberId, date);
      }
    } catch (e) {
      console.error("Error toggling task:", e);
      // Reload on error to revert
      if (showAll) {
        await loadTasksForAllMembers(members, date);
      } else {
        await loadTasks(memberId, date);
      }
    }
  }, [members, loadTasks, loadTasksForAllMembers]);

  const loadCategories = useCallback(async () => {
    try {
      const categoriesData = await fetchCalendarCategories();
      setCategories(categoriesData);
      // Clear error on success
      setError(null);
    } catch (e) {
      console.error("Error loading categories:", e);
      const errorMessage = extractErrorMessage(e, "Kunde inte ladda kategorier. Försök igen.");
      setError(errorMessage);
    }
  }, []);

  return {
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
  };
}

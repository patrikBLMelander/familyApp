import { useState, useCallback, useRef } from "react";
import {
  fetchCalendarEvents,
  CalendarEventResponse,
  fetchCalendarCategories,
  CalendarEventCategoryResponse,
} from "../../../shared/api/calendar";
import { fetchAllFamilyMembers, FamilyMemberResponse, getMemberByDeviceToken } from "../../../shared/api/familyMembers";
import { CalendarViewType, CALENDAR_VIEW_TYPES } from "../constants";
import { extractErrorMessage } from "../utils/errorHandling";

/**
 * Custom hook for managing calendar data fetching and state.
 *
 * Handles:
 * - Loading events, categories, and members
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
 * @returns Calendar data, loading state, error state, and data loading functions
 */
export function useCalendarData(
  viewType: CalendarViewType,
  currentWeek: Date,
  currentMonth: Date,
) {
  const [events, setEvents] = useState<CalendarEventResponse[]>([]);
  const [categories, setCategories] = useState<CalendarEventCategoryResponse[]>([]);
  const [members, setMembers] = useState<FamilyMemberResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
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

  const loadData = useCallback(async (forceReplace: boolean = false) => {
    try {
      setLoading(true);

      let startDate: Date | undefined;
      let endDate: Date | undefined;

      if (viewType === CALENDAR_VIEW_TYPES.ROLLING) {
        if (!rollingViewEndDate) {
          startDate = new Date();
          startDate.setHours(0, 0, 0, 0);
          endDate = new Date();
          endDate.setDate(endDate.getDate() + 30);
          endDate.setHours(23, 59, 59, 999);
          setRollingViewEndDate(endDate);
        } else {
          startDate = new Date();
          startDate.setHours(0, 0, 0, 0);
          endDate = rollingViewEndDate;
        }
      } else if (viewType === CALENDAR_VIEW_TYPES.WEEK) {
        const weekStart = new Date(currentWeek);
        const day = weekStart.getDay();
        const diff = weekStart.getDate() - day + (day === 0 ? -6 : 1);
        weekStart.setDate(diff);
        weekStart.setHours(0, 0, 0, 0);

        startDate = new Date(weekStart);
        startDate.setDate(startDate.getDate() - 7);

        endDate = new Date(weekStart);
        endDate.setDate(endDate.getDate() + 14);
        endDate.setHours(23, 59, 59, 999);
      } else if (viewType === CALENDAR_VIEW_TYPES.MONTH) {
        const year = currentMonth.getFullYear();
        const month = currentMonth.getMonth();
        startDate = new Date(year, month, 1);
        startDate.setHours(0, 0, 0, 0);

        endDate = new Date(year, month + 1, 0);
        endDate.setHours(23, 59, 59, 999);
      } else {
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

      if (!forceReplace && viewType === CALENDAR_VIEW_TYPES.ROLLING && rollingViewEndDate && events.length > 0) {
        setEvents(prev => {
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
        setEvents(eventsData);
      }

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

    if (loadMoreEventsRef.current) {
      return;
    }

    const currentEndDate = rollingViewEndDate;
    if (!currentEndDate) {
      return;
    }

    loadMoreEventsRef.current = true;

    try {
      const newEndDate = new Date(currentEndDate);
      newEndDate.setDate(newEndDate.getDate() + 30);
      newEndDate.setHours(23, 59, 59, 999);

      const fetchStartDate = new Date(currentEndDate);
      fetchStartDate.setDate(fetchStartDate.getDate() + 1);
      fetchStartDate.setHours(0, 0, 0, 0);

      const eventsData = await fetchCalendarEvents(fetchStartDate, newEndDate);

      setEvents(prev => {
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

      setRollingViewEndDate(newEndDate);
    } catch (e) {
      console.error("Error loading more events:", e);
    } finally {
      loadMoreEventsRef.current = false;
    }
  }, [viewType, rollingViewEndDate]);

  const loadCategories = useCallback(async () => {
    try {
      const categoriesData = await fetchCalendarCategories();
      setCategories(categoriesData);
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
    currentUserRole,
    loadData,
    loadCategories,
    loadCurrentMember,
    setError,
    loadMoreEvents,
  };
}

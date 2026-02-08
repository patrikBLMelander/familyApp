/**
 * Unit tests for calendar event filtering logic.
 * 
 * Tests the critical business logic that determines which events are shown
 * in calendar views based on:
 * - Event type (task vs regular event)
 * - Member visibility (showAllMembers flag)
 * - Participant IDs
 * 
 * IMPORTANT: Regular events (isTask=false) should ALWAYS be shown regardless
 * of participantIds. Only tasks (isTask=true) are filtered by participantIds.
 */

import { CalendarEventResponse } from "../../../../shared/api/calendar";
import { getAllDayEventDates } from "../dateFormatters";

/**
 * Helper function to create a mock calendar event for testing.
 */
function createMockEvent(overrides: Partial<CalendarEventResponse>): CalendarEventResponse {
  return {
    id: "event-1",
    familyId: "family-1",
    categoryId: null,
    title: "Test Event",
    description: null,
    startDateTime: "2024-01-15T10:00",
    endDateTime: null,
    isAllDay: false,
    location: null,
    createdById: "user-1",
    recurringType: null,
    recurringInterval: null,
    recurringEndDate: null,
    recurringEndCount: null,
    isTask: false,
    xpPoints: null,
    isRequired: false,
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
    participantIds: [],
    ...overrides,
  };
}

/**
 * Filter events by type (task vs event) and member visibility.
 * This is the same logic used in RollingView, AdultDashboard, and CalendarContainer.
 * 
 * @param events - Array of calendar events to filter
 * @param showTasksOnly - If true, show only tasks; if false, show only events
 * @param showAllMembers - If true, show all members' tasks; if false, filter by currentMemberId (only for tasks)
 * @param currentMemberId - Current logged-in member ID (only used for tasks when showAllMembers is false)
 * @returns Filtered array of events
 */
function filterEventsByType(
  events: CalendarEventResponse[],
  showTasksOnly: boolean,
  showAllMembers: boolean,
  currentMemberId: string | null
): CalendarEventResponse[] {
  return events.filter(event => {
    if (showTasksOnly) {
      if (!event.isTask) return false;
      // For tasks: filter by participantIds if showAllMembers is false
      if (!showAllMembers && currentMemberId) {
        return event.participantIds.includes(currentMemberId);
      }
      return true;
    } else {
      // For regular events: ALWAYS show all events regardless of participantIds
      // This ensures all family members' events are visible in the calendar view
      return !event.isTask;
    }
  });
}

describe("Event Filtering Logic", () => {
  describe("filterEventsByType - Regular Events (isTask=false)", () => {
    it("should show all events regardless of participantIds when showTasksOnly is false", () => {
      const events = [
        createMockEvent({ id: "event-1", isTask: false, participantIds: ["user-1"] }),
        createMockEvent({ id: "event-2", isTask: false, participantIds: ["user-2"] }),
        createMockEvent({ id: "event-3", isTask: false, participantIds: [] }),
        createMockEvent({ id: "event-4", isTask: false, participantIds: ["user-1", "user-2"] }),
      ];
      
      const result = filterEventsByType(events, false, false, "user-1");
      
      expect(result).toHaveLength(4);
      expect(result.map(e => e.id)).toEqual(["event-1", "event-2", "event-3", "event-4"]);
    });

    it("should show all events even when currentMemberId is provided", () => {
      const events = [
        createMockEvent({ id: "event-1", isTask: false, participantIds: ["user-1"] }),
        createMockEvent({ id: "event-2", isTask: false, participantIds: ["user-2"] }),
      ];
      
      const result = filterEventsByType(events, false, false, "user-1");
      
      expect(result).toHaveLength(2);
      expect(result.map(e => e.id)).toEqual(["event-1", "event-2"]);
    });

    it("should show all events even when showAllMembers is false", () => {
      const events = [
        createMockEvent({ id: "event-1", isTask: false, participantIds: ["user-1"] }),
        createMockEvent({ id: "event-2", isTask: false, participantIds: ["user-2"] }),
      ];
      
      const result = filterEventsByType(events, false, false, "user-1");
      
      expect(result).toHaveLength(2);
    });

    it("should exclude tasks when showTasksOnly is false", () => {
      const events = [
        createMockEvent({ id: "event-1", isTask: false, participantIds: ["user-1"] }),
        createMockEvent({ id: "task-1", isTask: true, participantIds: ["user-1"] }),
        createMockEvent({ id: "event-2", isTask: false, participantIds: ["user-2"] }),
      ];
      
      const result = filterEventsByType(events, false, false, "user-1");
      
      expect(result).toHaveLength(2);
      expect(result.map(e => e.id)).toEqual(["event-1", "event-2"]);
      expect(result.every(e => !e.isTask)).toBe(true);
    });
  });

  describe("filterEventsByType - Tasks (isTask=true)", () => {
    it("should show only tasks when showTasksOnly is true", () => {
      const events = [
        createMockEvent({ id: "event-1", isTask: false, participantIds: ["user-1"] }),
        createMockEvent({ id: "task-1", isTask: true, participantIds: ["user-1"] }),
        createMockEvent({ id: "task-2", isTask: true, participantIds: ["user-2"] }),
      ];
      
      const result = filterEventsByType(events, true, false, "user-1");
      
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe("task-1");
      expect(result.every(e => e.isTask)).toBe(true);
    });

    it("should filter tasks by participantIds when showAllMembers is false", () => {
      const events = [
        createMockEvent({ id: "task-1", isTask: true, participantIds: ["user-1"] }),
        createMockEvent({ id: "task-2", isTask: true, participantIds: ["user-2"] }),
        createMockEvent({ id: "task-3", isTask: true, participantIds: ["user-3"] }),
      ];
      
      const result = filterEventsByType(events, true, false, "user-1");
      
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe("task-1");
    });

    it("should show all tasks when showAllMembers is true", () => {
      const events = [
        createMockEvent({ id: "task-1", isTask: true, participantIds: ["user-1"] }),
        createMockEvent({ id: "task-2", isTask: true, participantIds: ["user-2"] }),
        createMockEvent({ id: "task-3", isTask: true, participantIds: ["user-3"] }),
      ];
      
      const result = filterEventsByType(events, true, true, "user-1");
      
      expect(result).toHaveLength(3);
      expect(result.map(e => e.id)).toEqual(["task-1", "task-2", "task-3"]);
    });

    it("should show tasks with empty participantIds when showAllMembers is true", () => {
      const events = [
        createMockEvent({ id: "task-1", isTask: true, participantIds: [] }),
        createMockEvent({ id: "task-2", isTask: true, participantIds: ["user-1"] }),
      ];
      
      const result = filterEventsByType(events, true, true, "user-1");
      
      expect(result).toHaveLength(2);
    });

    it("should show tasks with empty participantIds when showAllMembers is false and currentMemberId is null", () => {
      const events = [
        createMockEvent({ id: "task-1", isTask: true, participantIds: [] }),
        createMockEvent({ id: "task-2", isTask: true, participantIds: ["user-1"] }),
      ];
      
      const result = filterEventsByType(events, true, false, null);
      
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe("task-1");
    });

    it("should show tasks where user is in multiple participants", () => {
      const events = [
        createMockEvent({ id: "task-1", isTask: true, participantIds: ["user-1", "user-2"] }),
        createMockEvent({ id: "task-2", isTask: true, participantIds: ["user-2", "user-3"] }),
      ];
      
      const result = filterEventsByType(events, true, false, "user-1");
      
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe("task-1");
    });
  });

  describe("getAllDayEventDates", () => {
    it("should return empty array for non-all-day events", () => {
      const event = createMockEvent({ isAllDay: false });
      
      const result = getAllDayEventDates(event);
      
      expect(result).toEqual([]);
    });
  });

  describe("getAllDayEventDates - single day", () => {
    it("should return single date for all-day event without endDateTime", () => {
      const event = createMockEvent({
        isAllDay: true,
        startDateTime: "2024-01-15T00:00",
        endDateTime: null,
      });
      
      const result = getAllDayEventDates(event);
      
      expect(result).toEqual(["2024-01-15"]);
    });

    it("should return single date when start and end are the same day", () => {
      const event = createMockEvent({
        isAllDay: true,
        startDateTime: "2024-01-15T00:00",
        endDateTime: "2024-01-15T23:59",
      });
      
      const result = getAllDayEventDates(event);
      
      expect(result).toEqual(["2024-01-15"]);
    });
  });

  describe("getAllDayEventDates - multi-day", () => {
    it("should return all dates from start to end (inclusive)", () => {
      const event = createMockEvent({
        isAllDay: true,
        startDateTime: "2024-01-15T00:00",
        endDateTime: "2024-01-17T23:59",
      });
      
      const result = getAllDayEventDates(event);
      
      expect(result).toEqual(["2024-01-15", "2024-01-16", "2024-01-17"]);
    });

    it("should handle month boundaries correctly", () => {
      const event = createMockEvent({
        isAllDay: true,
        startDateTime: "2024-01-30T00:00",
        endDateTime: "2024-02-02T23:59",
      });
      
      const result = getAllDayEventDates(event);
      
      expect(result).toEqual(["2024-01-30", "2024-01-31", "2024-02-01", "2024-02-02"]);
    });

    it("should handle year boundaries correctly", () => {
      const event = createMockEvent({
        isAllDay: true,
        startDateTime: "2023-12-30T00:00",
        endDateTime: "2024-01-02T23:59",
      });
      
      const result = getAllDayEventDates(event);
      
      expect(result).toEqual(["2023-12-30", "2023-12-31", "2024-01-01", "2024-01-02"]);
    });

    it("should respect maxDays limit", () => {
      const event = createMockEvent({
        isAllDay: true,
        startDateTime: "2024-01-01T00:00",
        endDateTime: "2024-12-31T23:59",
      });
      
      const result = getAllDayEventDates(event, 10); // Limit to 10 days
      
      expect(result).toHaveLength(10);
      expect(result[0]).toBe("2024-01-01");
      expect(result[9]).toBe("2024-01-10");
    });

    it("should return only start date if end is before start", () => {
      const event = createMockEvent({
        isAllDay: true,
        startDateTime: "2024-01-15T00:00",
        endDateTime: "2024-01-10T23:59", // End before start
      });
      
      const result = getAllDayEventDates(event);
      
      expect(result).toEqual(["2024-01-15"]);
    });
  });

  describe("Edge Cases", () => {
    it("should handle empty events array", () => {
      const result = filterEventsByType([], false, false, null);
      
      expect(result).toEqual([]);
    });

    it("should handle events with null participantIds", () => {
      const events = [
        createMockEvent({ id: "event-1", isTask: false, participantIds: [] }),
      ];
      
      const result = filterEventsByType(events, false, false, "user-1");
      
      expect(result).toHaveLength(1);
    });

    it("should handle very long date ranges", () => {
      const event = createMockEvent({
        isAllDay: true,
        startDateTime: "2024-01-01T00:00",
        endDateTime: "2024-12-31T23:59",
      });
      
      const result = getAllDayEventDates(event, 365);
      
      expect(result).toHaveLength(365);
      expect(result[0]).toBe("2024-01-01");
      expect(result[364]).toBe("2024-12-30"); // 365 days from Jan 1
    });
  });
});

import { CalendarContainer } from "./CalendarContainer";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers";

type CalendarViewProps = {
  onNavigate?: (view: ViewKey) => void;
};

/**
 * CalendarView is a thin wrapper component that renders CalendarContainer.
 * All state management and logic is handled by CalendarContainer.
 * 
 * This component exists to maintain the public API while allowing
 * internal refactoring of the calendar implementation.
 * 
 * @param onNavigate - Optional callback for navigation to other views
 */
export function CalendarView({ onNavigate }: CalendarViewProps) {
  return <CalendarContainer onNavigate={onNavigate} />;
}

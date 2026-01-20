import { CalendarContainer } from "./CalendarContainer";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers";

type CalendarViewProps = {
  onNavigate?: (view: ViewKey) => void;
};

/**
 * CalendarView is a thin wrapper component that renders CalendarContainer.
 * All state management and logic is handled by CalendarContainer.
 */
export function CalendarView({ onNavigate }: CalendarViewProps) {
  return <CalendarContainer onNavigate={onNavigate} />;
}

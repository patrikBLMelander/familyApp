import { CalendarContainer } from "./CalendarContainer";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers";

type CalendarViewProps = {
  onNavigate?: (view: ViewKey) => void;
};

export function CalendarView({ onNavigate }: CalendarViewProps) {
  return <CalendarContainer onNavigate={onNavigate} />;
}

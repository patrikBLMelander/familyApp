import { CalendarContainer } from "./CalendarContainer";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers";

type CalendarViewProps = {
  onNavigate?: (view: ViewKey) => void;
  showMenstrualCycle?: boolean;
  onNavigateMenstrualCycle?: () => void;
};

export function CalendarView({ onNavigate, showMenstrualCycle, onNavigateMenstrualCycle }: CalendarViewProps) {
  return <CalendarContainer onNavigate={onNavigate} showMenstrualCycle={showMenstrualCycle} onNavigateMenstrualCycle={onNavigateMenstrualCycle} />;
}

import { getToggleButtonStyle, getToggleButtonContainerStyle, getMemberFilterContainerStyle } from "../utils/buttonStyles";

type CalendarFiltersProps = {
  showTasksOnly: boolean;
  setShowTasksOnly: (show: boolean) => void;
  showAllMembers: boolean;
  setShowAllMembers: (show: boolean) => void;
};

/**
 * Component for calendar filters.
 * Displays toggles for switching between schema/tasks view and member filtering.
 */
export function CalendarFilters({
  showTasksOnly,
  setShowTasksOnly,
  showAllMembers,
  setShowAllMembers,
}: CalendarFiltersProps) {
  return (
    <>
      {/* Task/Event toggle - show in all views */}
      <div style={getToggleButtonContainerStyle()}>
        <button
          type="button"
          onClick={() => setShowTasksOnly(false)}
          aria-label="Visa schema"
          aria-pressed={!showTasksOnly}
          style={getToggleButtonStyle(!showTasksOnly)}
        >
          Schema
        </button>
        <button
          type="button"
          onClick={() => setShowTasksOnly(true)}
          aria-label="Visa dagens att göra"
          aria-pressed={showTasksOnly}
          style={getToggleButtonStyle(showTasksOnly)}
        >
          Dagens Att Göra
        </button>
      </div>
      {showTasksOnly && (
        <div style={getMemberFilterContainerStyle()}>
          <button
            type="button"
            onClick={() => setShowAllMembers(false)}
            aria-label="Visa endast mina uppgifter"
            aria-pressed={!showAllMembers}
            style={getToggleButtonStyle(!showAllMembers)}
          >
            Endast mig
          </button>
          <button
            type="button"
            onClick={() => setShowAllMembers(true)}
            aria-label="Visa alla familjemedlemmars uppgifter"
            aria-pressed={showAllMembers}
            style={getToggleButtonStyle(showAllMembers)}
          >
            Alla familjemedlemmar
          </button>
        </div>
      )}
    </>
  );
}

import { CALENDAR_VIEW_TYPES, CalendarViewType } from "../constants";
import { getToggleButtonStyle, getToggleButtonContainerStyle } from "../utils/buttonStyles";

type CalendarViewSelectorProps = {
  viewType: CalendarViewType;
  setViewType: (viewType: CalendarViewType) => void;
};

/**
 * Component for selecting calendar view type (rolling, week, month).
 * Displays three toggle buttons for switching between view types.
 */
export function CalendarViewSelector({ viewType, setViewType }: CalendarViewSelectorProps) {
  const isRolling = viewType === CALENDAR_VIEW_TYPES.ROLLING;
  const isWeek = viewType === CALENDAR_VIEW_TYPES.WEEK;
  const isMonth = viewType === CALENDAR_VIEW_TYPES.MONTH;

  return (
    <div style={getToggleButtonContainerStyle()}>
      <button
        type="button"
        onClick={() => setViewType(CALENDAR_VIEW_TYPES.ROLLING)}
        aria-label="Visa rullande kalendervy"
        aria-pressed={isRolling}
        style={getToggleButtonStyle(isRolling)}
      >
        Rullande
      </button>
      <button
        type="button"
        onClick={() => setViewType(CALENDAR_VIEW_TYPES.WEEK)}
        aria-label="Visa veckokalendervy"
        aria-pressed={isWeek}
        style={getToggleButtonStyle(isWeek)}
      >
        Vecka
      </button>
      <button
        type="button"
        onClick={() => setViewType(CALENDAR_VIEW_TYPES.MONTH)}
        aria-label="Visa månadskalendervy"
        aria-pressed={isMonth}
        style={getToggleButtonStyle(isMonth)}
      >
        Månad
      </button>
    </div>
  );
}

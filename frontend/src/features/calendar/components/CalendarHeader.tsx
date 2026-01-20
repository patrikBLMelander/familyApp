type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers";

type CalendarHeaderProps = {
  onNavigate?: (view: ViewKey) => void;
  onOpenCategoryManager: () => void;
  onOpenQuickAdd: () => void;
  onBackClick: () => void;
  currentUserRole: "CHILD" | "ASSISTANT" | "PARENT" | null;
  showCreateForm: boolean;
  editingEvent: boolean;
};

/**
 * Component for calendar header with navigation and action buttons.
 * Displays back button, title, and action buttons (categories, new event).
 */
export function CalendarHeader({
  onNavigate,
  onOpenCategoryManager,
  onOpenQuickAdd,
  onBackClick,
  currentUserRole,
  showCreateForm,
  editingEvent,
}: CalendarHeaderProps) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "16px" }}>
      {onNavigate && (
        <button
          type="button"
          className="back-button"
          onClick={onBackClick}
          aria-label="Tillbaka"
        >
          ←
        </button>
      )}
      <h2 className="view-title" style={{ margin: 0, flex: 1 }}>Kalender</h2>
      {!showCreateForm && !editingEvent && (
        <div style={{ display: "flex", gap: "8px" }}>
          {currentUserRole === "PARENT" && (
            <button
              type="button"
              className="todo-action-button"
              onClick={onOpenCategoryManager}
              style={{ fontSize: "0.85rem", padding: "8px 12px" }}
            >
              Kategorier
            </button>
          )}
          <button
            type="button"
            className="button-primary"
            onClick={onOpenQuickAdd}
          >
            + Nytt event
          </button>
        </div>
      )}
    </div>
  );
}

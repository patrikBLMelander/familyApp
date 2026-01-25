import { CalendarEventResponse } from "../../../shared/api/calendar";

type OccurrenceScope = "THIS" | "THIS_AND_FOLLOWING" | "ALL";

type RecurringEventDialogProps = {
  event: CalendarEventResponse;
  occurrenceDate: string; // YYYY-MM-DD format
  onConfirm: (scope: OccurrenceScope) => void;
  onCancel: () => void;
  action: "delete" | "edit";
};

export function RecurringEventDialog({
  event,
  occurrenceDate,
  onConfirm,
  onCancel,
  action,
}: RecurringEventDialogProps) {
  const dateStr = new Date(occurrenceDate + "T00:00:00").toLocaleDateString("sv-SE", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });

  return (
    <div
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: "rgba(0, 0, 0, 0.5)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 1000,
        padding: "16px",
        boxSizing: "border-box",
      }}
      onClick={onCancel}
    >
      <div
        style={{
          backgroundColor: "white",
          borderRadius: "12px",
          padding: "20px",
          maxWidth: "400px",
          width: "100%",
          maxHeight: "calc(100vh - 32px)",
          boxShadow: "0 4px 20px rgba(0, 0, 0, 0.15)",
          overflowY: "auto",
          display: "flex",
          flexDirection: "column",
          boxSizing: "border-box",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <h3 style={{ marginTop: 0, marginBottom: "12px", fontSize: "1.1rem", fontWeight: 600, lineHeight: "1.3" }}>
          {action === "delete" ? "Ta bort återkommande händelse" : "Redigera återkommande händelse"}
        </h3>
        <p style={{ marginBottom: "16px", color: "#666", fontSize: "0.9rem", lineHeight: "1.4" }}>
          Denna händelse är en del av en återkommande serie. Vad vill du {action === "delete" ? "ta bort" : "redigera"}?
        </p>
        <div style={{ display: "flex", flexDirection: "column", gap: "10px", marginBottom: "16px", flex: "1 1 auto", minHeight: 0 }}>
          <button
            type="button"
            onClick={() => onConfirm("THIS")}
            style={{
              padding: "12px 14px",
              borderRadius: "8px",
              border: "2px solid #b8e6b8",
              backgroundColor: "white",
              color: "#2d5a2d",
              fontWeight: 600,
              cursor: "pointer",
              textAlign: "left",
              fontSize: "0.9rem",
              width: "100%",
              boxSizing: "border-box",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.backgroundColor = "#f0f8f0";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.backgroundColor = "white";
            }}
          >
            <div style={{ fontWeight: 600, marginBottom: "4px", lineHeight: "1.3" }}>Denna förekomst</div>
            <div style={{ fontSize: "0.8rem", color: "#666", lineHeight: "1.3", wordWrap: "break-word" }}>
              {action === "delete" ? "Tar bort" : "Redigerar"} bara händelsen den {dateStr}
            </div>
          </button>
          <button
            type="button"
            onClick={() => onConfirm("THIS_AND_FOLLOWING")}
            style={{
              padding: "12px 14px",
              borderRadius: "8px",
              border: "2px solid #b8e6b8",
              backgroundColor: "white",
              color: "#2d5a2d",
              fontWeight: 600,
              cursor: "pointer",
              textAlign: "left",
              fontSize: "0.9rem",
              width: "100%",
              boxSizing: "border-box",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.backgroundColor = "#f0f8f0";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.backgroundColor = "white";
            }}
          >
            <div style={{ fontWeight: 600, marginBottom: "4px", lineHeight: "1.3" }}>Denna och följande</div>
            <div style={{ fontSize: "0.8rem", color: "#666", lineHeight: "1.3", wordWrap: "break-word" }}>
              {action === "delete" ? "Tar bort" : "Redigerar"} händelsen den {dateStr} och alla framtida förekomster
            </div>
          </button>
          <button
            type="button"
            onClick={() => onConfirm("ALL")}
            style={{
              padding: "12px 14px",
              borderRadius: "8px",
              border: "2px solid #b8e6b8",
              backgroundColor: "white",
              color: "#2d5a2d",
              fontWeight: 600,
              cursor: "pointer",
              textAlign: "left",
              fontSize: "0.9rem",
              width: "100%",
              boxSizing: "border-box",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.backgroundColor = "#f0f8f0";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.backgroundColor = "white";
            }}
          >
            <div style={{ fontWeight: 600, marginBottom: "4px", lineHeight: "1.3" }}>Alla händelser</div>
            <div style={{ fontSize: "0.8rem", color: "#666", lineHeight: "1.3", wordWrap: "break-word" }}>
              {action === "delete" ? "Tar bort" : "Redigerar"} hela serien
            </div>
          </button>
        </div>
        <button
          type="button"
          onClick={onCancel}
          style={{
            padding: "10px 20px",
            borderRadius: "8px",
            border: "1px solid #ddd",
            backgroundColor: "white",
            color: "#666",
            fontWeight: 500,
            cursor: "pointer",
            width: "100%",
            boxSizing: "border-box",
            flexShrink: 0,
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.backgroundColor = "#f5f5f5";
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.backgroundColor = "white";
          }}
        >
          Avbryt
        </button>
      </div>
    </div>
  );
}

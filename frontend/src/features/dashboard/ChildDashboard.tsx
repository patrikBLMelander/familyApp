type ViewKey = "dailytasks" | "xp";

type ChildDashboardProps = {
  onNavigate?: (view: ViewKey) => void;
  childName?: string;
  onLogout?: () => void;
};

export function ChildDashboard({ onNavigate, childName, onLogout }: ChildDashboardProps) {
  const handleLogout = () => {
    if (confirm("Vill du gå tillbaka till föräldervyn?")) {
      // Restore parent token if it exists (from testing child view)
      const parentToken = localStorage.getItem("parentDeviceToken");
      if (parentToken) {
        localStorage.setItem("deviceToken", parentToken);
        localStorage.removeItem("parentDeviceToken");
      } else {
        // If no parent token saved, remove device token (full logout)
        localStorage.removeItem("deviceToken");
      }
      if (onLogout) {
        onLogout();
      } else {
        window.location.href = "/";
      }
    }
  };

  return (
    <div className="dashboard child-dashboard">
      {childName && (
        <div style={{ marginBottom: "16px", display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
          <div>
            <h2 style={{ margin: "0 0 4px", fontSize: "1.2rem" }}>Hej {childName}! 👋</h2>
            <p style={{ margin: "0", fontSize: "0.9rem", color: "#6b6b6b" }}>
              Här är dina sysslor för idag
            </p>
          </div>
          <button
            type="button"
            className="button-secondary"
            onClick={handleLogout}
            style={{ fontSize: "0.8rem", padding: "6px 12px" }}
          >
            Logga ut
          </button>
        </div>
      )}
      <section className="card-grid">
        <button
          type="button"
          className="card card-primary"
          onClick={() => onNavigate?.("dailytasks")}
        >
          <h2>Dagliga sysslor</h2>
          <p>Se dina sysslor för idag och bocka av dem när du är klar.</p>
        </button>
        <button
          type="button"
          className="card card-primary"
          onClick={() => onNavigate?.("xp")}
        >
          <h2>Min XP</h2>
          <p>Se din level, XP och badges! Utför sysslor för att få XP.</p>
        </button>
      </section>
    </div>
  );
}


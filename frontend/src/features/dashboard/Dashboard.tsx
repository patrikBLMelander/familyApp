type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "dailytasks" | "dailytasksadmin" | "familymembers";

type DashboardProps = {
  placeholder?: string;
  onNavigate?: (view: ViewKey) => void;
};

export function Dashboard({ placeholder, onNavigate }: DashboardProps) {
  return (
    <div className="dashboard">
      <section className="card-grid">
        <button
          type="button"
          className="card card-primary"
          onClick={() => onNavigate?.("todos")}
        >
          <h2>To do-listor</h2>
          <p>Snabb överblick över inköp, packlistor och vardagssysslor.</p>
        </button>
        <button
          type="button"
          className="card"
          onClick={() => onNavigate?.("dailytasks")}
        >
          <h2>Dagens sysslor</h2>
          <p>Se dagens sysslor för dig och barnen.</p>
        </button>
        <button
          type="button"
          className="card"
          onClick={() => onNavigate?.("familymembers")}
        >
          <h2>Familjemedlemmar</h2>
          <p>Hantera familjemedlemmar och bjuda in nya.</p>
        </button>
        <button
          type="button"
          className="card"
          disabled
          style={{ opacity: 0.5, cursor: "not-allowed" }}
        >
          <h2>Schema</h2>
          <p>Kommer snart</p>
        </button>
      </section>
      {placeholder && <p className="placeholder-text">{placeholder}</p>}
    </div>
  );
}




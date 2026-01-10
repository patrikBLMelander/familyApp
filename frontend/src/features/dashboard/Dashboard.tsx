type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "dailytasks" | "dailytasksadmin" | "familymembers" | "childrenxp";

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
          onClick={() => onNavigate?.("schedule")}
        >
          <h2>Schema</h2>
          <p>Se familjens aktiviteter och events.</p>
        </button>
        <button
          type="button"
          className="card"
          onClick={() => onNavigate?.("childrenxp")}
        >
          <h2>Mina Barns Djur</h2>
          <p>Se alla barns djur, level, XP och framsteg.</p>
        </button>
      </section>
      {placeholder && <p className="placeholder-text">{placeholder}</p>}
    </div>
  );
}




type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers" | "childrenxp";

type DashboardProps = {
  placeholder?: string;
  onNavigate?: (view: ViewKey) => void;
  familyId?: string | null;
};

// Allowed family IDs for Spotify Charts link
const SPOTIFY_CHARTS_ALLOWED_FAMILIES = [
  "ce69194a-934d-4234-b046-dae7473700c0", // Production
  "cdd48859-74c5-4dee-989f-0b091f62d630", // Localhost
];

export function Dashboard({ placeholder, onNavigate, familyId }: DashboardProps) {
  const showSpotifyLink = familyId && SPOTIFY_CHARTS_ALLOWED_FAMILIES.includes(familyId);
  const spotifyUrl = "https://spotify-charts-production.up.railway.app/";

  return (
    <div className="dashboard">
      <section className="card-grid">
        <button
          type="button"
          className="card card-primary"
          onClick={() => onNavigate?.("todos")}
        >
          <h2>Listor</h2>
          <p>Snabb överblick över inköp, packlistor och vardagssysslor.</p>
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
          <h2>Kalender</h2>
          <p>Se familjens aktiviteter, events och dagens sysslor.</p>
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
      
      {/* Spotify Charts Link - Only for specific families */}
      {showSpotifyLink && (
        <div style={{
          position: "fixed",
          bottom: "20px",
          right: "20px",
          zIndex: 1000,
        }}>
          <a
            href={spotifyUrl}
            target="_blank"
            rel="noopener noreferrer"
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              width: "56px",
              height: "56px",
              borderRadius: "50%",
              background: "#1DB954", // Spotify green
              color: "white",
              fontSize: "24px",
              textDecoration: "none",
              boxShadow: "0 4px 12px rgba(29, 185, 84, 0.4)",
              transition: "all 0.2s ease",
              cursor: "pointer",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = "scale(1.1)";
              e.currentTarget.style.boxShadow = "0 6px 16px rgba(29, 185, 84, 0.6)";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = "scale(1)";
              e.currentTarget.style.boxShadow = "0 4px 12px rgba(29, 185, 84, 0.4)";
            }}
            title="Spotify Charts"
            aria-label="Öppna Spotify Charts"
          >
            🎵
          </a>
        </div>
      )}
    </div>
  );
}




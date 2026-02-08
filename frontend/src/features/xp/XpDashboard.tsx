import { useEffect, useState } from "react";
import { fetchCurrentXpProgress, fetchXpHistory, XpProgressResponse, XpHistoryResponse } from "../../shared/api/xp";
import { fetchCurrentPet, PetResponse } from "../../shared/api/pets";
import { getPetFoodEmoji, getPetFoodName } from "../pet/petFoodUtils";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores";

type XpDashboardProps = {
  onNavigate?: (view: ViewKey) => void;
};

const MAX_LEVEL = 5;

export function XpDashboard({ onNavigate }: XpDashboardProps) {
  const [progress, setProgress] = useState<XpProgressResponse | null>(null);
  const [history, setHistory] = useState<XpHistoryResponse[]>([]);
  const [pet, setPet] = useState<PetResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const [progressData, historyData, petData] = await Promise.all([
          fetchCurrentXpProgress().catch(() => null),
          fetchXpHistory().catch(() => []),
          fetchCurrentPet().catch(() => null)
        ]);
        setProgress(progressData);
        setHistory(historyData);
        setPet(petData);
      } catch (e) {
        console.error("Error loading XP data:", e);
        setError("Kunde inte ladda XP-data.");
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, []);

  if (loading) {
    return (
      <div className="xp-dashboard">
        <section className="card">
          <p>Laddar...</p>
        </section>
      </div>
    );
  }

  if (error || !progress) {
    return (
      <div className="xp-dashboard">
        <div className="daily-tasks-header">
          <div style={{ flex: 1 }}>
            <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
              {onNavigate && (
                <button
                  type="button"
                  className="back-button"
                  onClick={() => onNavigate("dashboard")}
                  aria-label="Tillbaka"
                >
                  ←
                </button>
              )}
              <div style={{ flex: 1 }}>
                <h2 className="view-title" style={{ margin: 0 }}>Mat & Level</h2>
              </div>
            </div>
          </div>
        </div>
        <section className="card">
          <p className="error-text">{error || "Ingen XP-data hittades."}</p>
        </section>
      </div>
    );
  }

  // XP thresholds: [0, 10, 35, 70, 125]
  // Level 1: 0-9 XP (range = 10)
  // Level 2: 10-34 XP (range = 25)
  // Level 3: 35-69 XP (range = 35)
  // Level 4: 70-124 XP (range = 55)
  // Level 5: 125+ XP
  const XP_THRESHOLDS = [0, 10, 35, 70, 125];
  const progressPercentage = progress.currentLevel >= MAX_LEVEL 
    ? 100 
    : (() => {
        const currentLevelIndex = progress.currentLevel - 1; // 0-based index
        const nextLevelThreshold = XP_THRESHOLDS[progress.currentLevel]; // Threshold for next level
        const currentLevelThreshold = XP_THRESHOLDS[currentLevelIndex]; // Threshold for current level
        const xpRangeForCurrentLevel = nextLevelThreshold - currentLevelThreshold; // XP range for current level
        
        if (xpRangeForCurrentLevel <= 0) return 100; // Safety check
        
        // Calculate progress: how much XP we have in current level / total XP range for current level
        const progressValue = (progress.xpInCurrentLevel / xpRangeForCurrentLevel) * 100;
        return Math.min(100, Math.max(0, progressValue));
      })();
  const monthNames = ["Januari", "Februari", "Mars", "April", "Maj", "Juni", "Juli", "Augusti", "September", "Oktober", "November", "December"];
  const foodEmoji = pet ? getPetFoodEmoji(pet.petType) : "🍎";
  const foodName = pet ? getPetFoodName(pet.petType) : "mat";

  return (
    <div className="xp-dashboard">
      <div className="daily-tasks-header">
        <div style={{ flex: 1 }}>
          <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
            {onNavigate && (
              <button
                type="button"
                className="back-button"
                onClick={() => onNavigate("dashboard")}
                aria-label="Tillbaka"
              >
                ←
              </button>
            )}
            <div style={{ flex: 1 }}>
              <h2 className="view-title" style={{ margin: 0 }}>Mat & Level</h2>
              <p className="daily-tasks-date" style={{ margin: 0 }}>
                {monthNames[progress.month - 1]} {progress.year}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Current Level Card */}
      <section className="card" style={{ 
        background: "linear-gradient(135deg, rgba(184, 230, 184, 0.2) 0%, rgba(184, 230, 184, 0.05) 100%)",
        border: "2px solid rgba(184, 230, 184, 0.3)"
      }}>
        <div style={{ textAlign: "center", marginBottom: "20px" }}>
          <h2 style={{ margin: 0, fontSize: "2.5rem", fontWeight: 700, color: "#2d5a2d" }}>
            Level {progress.currentLevel}
          </h2>
          <p style={{ margin: "6px 0 0", fontSize: "1rem", color: "#6b6b6b" }}>
            {progress.currentXp} {foodEmoji} {foodName}
          </p>
        </div>

        {/* Progress Bar */}
        <div style={{ marginBottom: "16px" }}>
          <div style={{ 
            display: "flex", 
            justifyContent: "space-between", 
            marginBottom: "8px",
            fontSize: "0.85rem",
            color: "#6b6b6b",
            flexWrap: "wrap",
            gap: "4px"
          }}>
            <span>Progress till Level {Math.min(progress.currentLevel + 1, MAX_LEVEL)}</span>
            <span>{progress.xpInCurrentLevel} / {progress.xpInCurrentLevel + progress.xpForNextLevel} {foodEmoji}</span>
          </div>
          <div style={{
            width: "100%",
            height: "24px",
            background: "rgba(200, 190, 180, 0.2)",
            borderRadius: "12px",
            overflow: "hidden",
            position: "relative"
          }}>
            <div style={{
              width: `${progressPercentage}%`,
              height: "100%",
              background: "linear-gradient(90deg, rgba(184, 230, 184, 0.8) 0%, rgba(184, 230, 184, 1) 100%)",
              borderRadius: "12px",
              transition: "width 0.3s ease"
            }} />
          </div>
          {progress.currentLevel < MAX_LEVEL && (
            <p style={{ 
              margin: "8px 0 0", 
              fontSize: "0.85rem", 
              color: "#6b6b6b",
              textAlign: "center"
            }}>
              {progress.xpForNextLevel} {foodEmoji} kvar till nästa level
            </p>
          )}
        </div>

        {/* Stats */}
        <div style={{
          display: "grid",
          gridTemplateColumns: "1fr 1fr",
          gap: "12px",
          marginTop: "20px",
          paddingTop: "20px",
          borderTop: "1px solid rgba(200, 190, 180, 0.3)"
        }}>
          <div style={{ textAlign: "center" }}>
            <div style={{ fontSize: "1.3rem", fontWeight: 700, color: "#2d5a2d" }}>
              {progress.totalTasksCompleted}
            </div>
            <div style={{ fontSize: "0.85rem", color: "#6b6b6b" }}>
              Sysslor klara
            </div>
          </div>
          <div style={{ textAlign: "center" }}>
            <div style={{ fontSize: "1.3rem", fontWeight: 700, color: "#2d5a2d" }}>
              {progress.currentXp} {foodEmoji}
            </div>
            <div style={{ fontSize: "0.85rem", color: "#6b6b6b" }}>
              Total {foodName}
            </div>
          </div>
        </div>
      </section>

      {/* History Section */}
      {history.length > 0 && (
        <section className="card" style={{ marginTop: "16px" }}>
          <h3 style={{ marginTop: 0, marginBottom: "12px", fontSize: "1rem", fontWeight: 600 }}>
            Tidigare månader
          </h3>
          <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            {history.map((h) => {
              return (
                <div
                  key={`${h.year}-${h.month}`}
                  style={{
                    padding: "12px",
                    background: "rgba(240, 240, 240, 0.3)",
                    borderRadius: "8px",
                    display: "flex",
                    alignItems: "center",
                    gap: "12px"
                  }}
                >
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, marginBottom: "2px", color: "#2d5a2d" }}>
                      {monthNames[h.month - 1]} {h.year}
                    </div>
                    <div style={{ fontSize: "0.75rem", color: "#6b6b6b" }}>
                      {h.totalTasksCompleted} sysslor klara
                    </div>
                  </div>
                  <div style={{ textAlign: "right" }}>
                    <div style={{ fontWeight: 700, color: "#2d5a2d", fontSize: "0.9rem" }}>
                      Level {h.finalLevel}
                    </div>
                    <div style={{ fontSize: "0.75rem", color: "#6b6b6b" }}>
                      {h.finalXp} {foodEmoji}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      )}
    </div>
  );
}


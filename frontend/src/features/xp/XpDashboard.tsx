import { useEffect, useState } from "react";
import { fetchCurrentXpProgress, fetchXpHistory, XpProgressResponse, XpHistoryResponse } from "../../shared/api/xp";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "dailytasks" | "dailytasksadmin";

type XpDashboardProps = {
  onNavigate?: (view: ViewKey) => void;
};

const MAX_LEVEL = 10;
const XP_PER_LEVEL = 100;

// Badge emojis for each level - themed by month
const getLevelBadges = (): Record<number, string> => {
  const month = new Date().getMonth() + 1; // 1-12
  
  if (month === 1) {
    // Snow theme for January - each level has a unique snow-related emoji
    return {
      1: "❄️",
      2: "🌨️",
      3: "⛄",
      4: "🧊",
      5: "🎿",
      6: "🛷",
      7: "🧣",
      8: "🧤",
      9: "⛷️",
      10: "🏔️"
    };
  }
  
  if (month === 2) {
    // Love/Valentine theme for February
    return {
      1: "💝",
      2: "💖",
      3: "💗",
      4: "💓",
      5: "💕",
      6: "💞",
      7: "💟",
      8: "🌹",
      9: "💐",
      10: "💍"
    };
  }
  
  if (month === 3) {
    // Spring theme for March
    return {
      1: "🌱",
      2: "🌿",
      3: "🍀",
      4: "🌷",
      5: "🌻",
      6: "🌸",
      7: "🦋",
      8: "🐝",
      9: "🌞",
      10: "🌈"
    };
  }
  
  // Default badges for other months
  return {
    1: "🌱",
    2: "⭐",
    3: "🌟",
    4: "💫",
    5: "✨",
    6: "🎯",
    7: "🏆",
    8: "👑",
    9: "💎",
    10: "🌟"
  };
};

const LEVEL_BADGES = getLevelBadges();

// Get badges for a specific month (for history)
const getBadgesForMonth = (month: number): Record<number, string> => {
  if (month === 1) {
    return {
      1: "❄️", 2: "🌨️", 3: "⛄", 4: "🧊", 5: "🎿",
      6: "🛷", 7: "🧣", 8: "🧤", 9: "⛷️", 10: "🏔️"
    };
  }
  if (month === 2) {
    return {
      1: "💝", 2: "💖", 3: "💗", 4: "💓", 5: "💕",
      6: "💞", 7: "💟", 8: "🌹", 9: "💐", 10: "💍"
    };
  }
  if (month === 3) {
    return {
      1: "🌱", 2: "🌿", 3: "🍀", 4: "🌷", 5: "🌻",
      6: "🌸", 7: "🦋", 8: "🐝", 9: "🌞", 10: "🌈"
    };
  }
  return {
    1: "🌱", 2: "⭐", 3: "🌟", 4: "💫", 5: "✨",
    6: "🎯", 7: "🏆", 8: "👑", 9: "💎", 10: "🌟"
  };
};

export function XpDashboard({ onNavigate }: XpDashboardProps) {
  const [progress, setProgress] = useState<XpProgressResponse | null>(null);
  const [history, setHistory] = useState<XpHistoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const [progressData, historyData] = await Promise.all([
          fetchCurrentXpProgress().catch(() => null),
          fetchXpHistory().catch(() => [])
        ]);
        setProgress(progressData);
        setHistory(historyData);
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
                <h2 className="view-title" style={{ margin: 0 }}>XP & Level</h2>
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

  const progressPercentage = (progress.xpInCurrentLevel / XP_PER_LEVEL) * 100;
  const monthNames = ["Januari", "Februari", "Mars", "April", "Maj", "Juni", "Juli", "Augusti", "September", "Oktober", "November", "December"];

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
              <h2 className="view-title" style={{ margin: 0 }}>XP & Level</h2>
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
          <div style={{ fontSize: "3rem", marginBottom: "6px" }}>
            {LEVEL_BADGES[progress.currentLevel] || "⭐"}
          </div>
          <h2 style={{ margin: 0, fontSize: "1.5rem", fontWeight: 700, color: "#2d5a2d" }}>
            Level {progress.currentLevel}
          </h2>
          <p style={{ margin: "6px 0 0", fontSize: "1rem", color: "#6b6b6b" }}>
            {progress.currentXp} XP
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
            <span>{progress.xpInCurrentLevel} / {XP_PER_LEVEL} XP</span>
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
              {progress.xpForNextLevel} XP kvar till nästa level
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
              {progress.currentXp}
            </div>
            <div style={{ fontSize: "0.85rem", color: "#6b6b6b" }}>
              Total XP
            </div>
          </div>
        </div>
      </section>

      {/* Badges Section */}
      <section className="card" style={{ marginTop: "16px" }}>
        <h3 style={{ marginTop: 0, marginBottom: "12px", fontSize: "1rem", fontWeight: 600 }}>
          Dina Badges
        </h3>
        <div style={{
          display: "grid",
          gridTemplateColumns: "repeat(5, 1fr)",
          gap: "8px"
        }}>
          {Array.from({ length: MAX_LEVEL }, (_, i) => i + 1).map((level) => {
            const hasBadge = progress.currentLevel >= level;
            return (
              <div
                key={level}
                style={{
                  aspectRatio: "1",
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  justifyContent: "center",
                  background: hasBadge 
                    ? "rgba(184, 230, 184, 0.2)" 
                    : "rgba(240, 240, 240, 0.5)",
                  borderRadius: "12px",
                  border: hasBadge 
                    ? "2px solid rgba(184, 230, 184, 0.5)" 
                    : "1px solid rgba(200, 190, 180, 0.3)",
                  opacity: hasBadge ? 1 : 0.4,
                  transition: "all 0.2s ease"
                }}
              >
                <div style={{ fontSize: "1.5rem", marginBottom: "2px" }}>
                  {LEVEL_BADGES[level] || "⭐"}
                </div>
                <div style={{ 
                  fontSize: "0.7rem", 
                  fontWeight: hasBadge ? 600 : 400,
                  color: hasBadge ? "#2d5a2d" : "#a0a0a0"
                }}>
                  L{level}
                </div>
              </div>
            );
          })}
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
              const monthBadges = getBadgesForMonth(h.month);
              const historyBadge = monthBadges[Math.min(h.finalLevel, MAX_LEVEL)] || "⭐";
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
                  <div style={{ 
                    fontSize: "1.5rem",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    minWidth: "32px"
                  }}>
                    {historyBadge}
                  </div>
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
                      {h.finalXp} XP
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


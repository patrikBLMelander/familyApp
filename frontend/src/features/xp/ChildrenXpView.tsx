import { useEffect, useState } from "react";
import { fetchAllFamilyMembers, FamilyMemberResponse } from "../../shared/api/familyMembers";
import { fetchMemberXpProgress, fetchMemberXpHistory, awardBonusXp, XpProgressResponse, XpHistoryResponse } from "../../shared/api/xp";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "dailytasks" | "dailytasksadmin" | "familymembers";

type ChildrenXpViewProps = {
  onNavigate?: (view: ViewKey) => void;
};

const MAX_LEVEL = 10;
const XP_PER_LEVEL = 12;

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

type ChildXpData = {
  member: FamilyMemberResponse;
  progress: XpProgressResponse | null;
  history: XpHistoryResponse[];
  loading: boolean;
  error: string | null;
};

export function ChildrenXpView({ onNavigate }: ChildrenXpViewProps) {
  const [children, setChildren] = useState<FamilyMemberResponse[]>([]);
  const [childrenXpData, setChildrenXpData] = useState<Map<string, ChildXpData>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [bonusXpDialog, setBonusXpDialog] = useState<{ childId: string; childName: string } | null>(null);
  const [bonusXpAmount, setBonusXpAmount] = useState<string>("10");
  const [awardingXp, setAwardingXp] = useState(false);

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const members = await fetchAllFamilyMembers();
        const childrenMembers = members.filter(m => m.role === "CHILD");
        setChildren(childrenMembers);

        // Load XP data for each child
        const xpDataMap = new Map<string, ChildXpData>();
        
        for (const child of childrenMembers) {
          try {
            const [progress, history] = await Promise.all([
              fetchMemberXpProgress(child.id).catch(() => null),
              fetchMemberXpHistory(child.id).catch(() => [])
            ]);
            
            xpDataMap.set(child.id, {
              member: child,
              progress,
              history,
              loading: false,
              error: null
            });
          } catch (e) {
            xpDataMap.set(child.id, {
              member: child,
              progress: null,
              history: [],
              loading: false,
              error: "Kunde inte ladda XP-data"
            });
          }
        }

        setChildrenXpData(xpDataMap);
      } catch (e) {
        console.error("Error loading children XP data:", e);
        setError("Kunde inte ladda data.");
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, []);

  const handleAwardBonusXp = async () => {
    if (!bonusXpDialog) return;
    
    const xpAmount = parseInt(bonusXpAmount, 10);
    if (isNaN(xpAmount) || xpAmount <= 0 || xpAmount > 100) {
      alert("XP måste vara mellan 1 och 100");
      return;
    }

    try {
      setAwardingXp(true);
      const updatedProgress = await awardBonusXp(bonusXpDialog.childId, xpAmount);
      
      // Update the child's progress in state
      setChildrenXpData(prev => {
        const newMap = new Map(prev);
        const childData = newMap.get(bonusXpDialog.childId);
        if (childData) {
          newMap.set(bonusXpDialog.childId, {
            ...childData,
            progress: updatedProgress
          });
        }
        return newMap;
      });

      setBonusXpDialog(null);
      setBonusXpAmount("10");
    } catch (e) {
      console.error("Error awarding bonus XP:", e);
      alert("Kunde inte ge bonus-XP. Försök igen.");
    } finally {
      setAwardingXp(false);
    }
  };

  const monthNames = ["Januari", "Februari", "Mars", "April", "Maj", "Juni", "Juli", "Augusti", "September", "Oktober", "November", "December"];

  if (loading) {
    return (
      <div className="children-xp-view">
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
                <h2 className="view-title" style={{ margin: 0 }}>Barnens XP</h2>
              </div>
            </div>
          </div>
        </div>
        <section className="card">
          <p>Laddar...</p>
        </section>
      </div>
    );
  }

  if (error) {
    return (
      <div className="children-xp-view">
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
                <h2 className="view-title" style={{ margin: 0 }}>Barnens XP</h2>
              </div>
            </div>
          </div>
        </div>
        <section className="card">
          <p className="error-text">{error}</p>
        </section>
      </div>
    );
  }

  if (children.length === 0) {
    return (
      <div className="children-xp-view">
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
                <h2 className="view-title" style={{ margin: 0 }}>Barnens XP</h2>
              </div>
            </div>
          </div>
        </div>
        <section className="card">
          <p className="placeholder-text">Inga barn i familjen än.</p>
        </section>
      </div>
    );
  }

  return (
    <div className="children-xp-view">
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
              <h2 className="view-title" style={{ margin: 0 }}>Barnens XP</h2>
            </div>
          </div>
        </div>
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
        {children.map((child) => {
          const xpData = childrenXpData.get(child.id);
          const progress = xpData?.progress;
          const history = xpData?.history || [];

          if (!progress) {
            return (
              <section key={child.id} className="card">
                <h3 style={{ marginTop: 0, marginBottom: "8px", fontSize: "1rem", fontWeight: 600 }}>
                  {child.name}
                </h3>
                <p style={{ margin: 0, color: "#6b6b6b", fontSize: "0.9rem" }}>
                  {xpData?.error || "Ingen XP-data än"}
                </p>
              </section>
            );
          }

          const progressPercentage = (progress.xpInCurrentLevel / XP_PER_LEVEL) * 100;

          return (
            <section key={child.id} className="card" style={{ 
              background: "linear-gradient(135deg, rgba(184, 230, 184, 0.1) 0%, rgba(184, 230, 184, 0.05) 100%)",
              border: "2px solid rgba(184, 230, 184, 0.3)"
            }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "16px" }}>
                <h3 style={{ marginTop: 0, marginBottom: 0, fontSize: "1.1rem", fontWeight: 600 }}>
                  {child.name}
                </h3>
                <button
                  type="button"
                  className="button-primary"
                  onClick={() => setBonusXpDialog({ childId: child.id, childName: child.name })}
                  style={{ fontSize: "0.85rem", padding: "6px 12px", whiteSpace: "nowrap" }}
                >
                  + Ge XP
                </button>
              </div>

              <div style={{ textAlign: "center", marginBottom: "20px" }}>
                <div style={{ fontSize: "2.5rem", marginBottom: "6px" }}>
                  {LEVEL_BADGES[progress.currentLevel] || "⭐"}
                </div>
                <div style={{ fontSize: "1.3rem", fontWeight: 700, color: "#2d5a2d", marginBottom: "4px" }}>
                  Level {progress.currentLevel}
                </div>
                <div style={{ fontSize: "0.95rem", color: "#6b6b6b" }}>
                  {progress.currentXp} XP • {monthNames[progress.month - 1]} {progress.year}
                </div>
              </div>

              {/* Progress Bar */}
              <div style={{ marginBottom: "16px" }}>
                <div style={{ 
                  display: "flex", 
                  justifyContent: "space-between", 
                  marginBottom: "8px",
                  fontSize: "0.8rem",
                  color: "#6b6b6b",
                  flexWrap: "wrap",
                  gap: "4px"
                }}>
                  <span>Progress till Level {Math.min(progress.currentLevel + 1, MAX_LEVEL)}</span>
                  <span>{progress.xpInCurrentLevel} / {XP_PER_LEVEL} XP</span>
                </div>
                <div style={{
                  width: "100%",
                  height: "20px",
                  background: "rgba(200, 190, 180, 0.2)",
                  borderRadius: "10px",
                  overflow: "hidden",
                  position: "relative"
                }}>
                  <div style={{
                    width: `${progressPercentage}%`,
                    height: "100%",
                    background: "linear-gradient(90deg, rgba(184, 230, 184, 0.8) 0%, rgba(184, 230, 184, 1) 100%)",
                    borderRadius: "10px",
                    transition: "width 0.3s ease"
                  }} />
                </div>
                {progress.currentLevel < MAX_LEVEL && (
                  <p style={{ 
                    margin: "6px 0 0", 
                    fontSize: "0.8rem", 
                    color: "#6b6b6b",
                    textAlign: "center"
                  }}>
                    {progress.xpForNextLevel} XP kvar
                  </p>
                )}
              </div>

              {/* Stats */}
              <div style={{
                display: "grid",
                gridTemplateColumns: "1fr 1fr",
                gap: "12px",
                marginTop: "16px",
                paddingTop: "16px",
                borderTop: "1px solid rgba(200, 190, 180, 0.3)"
              }}>
                <div style={{ textAlign: "center" }}>
                  <div style={{ fontSize: "1.2rem", fontWeight: 700, color: "#2d5a2d" }}>
                    {progress.totalTasksCompleted}
                  </div>
                  <div style={{ fontSize: "0.8rem", color: "#6b6b6b" }}>
                    Sysslor klara
                  </div>
                </div>
                <div style={{ textAlign: "center" }}>
                  <div style={{ fontSize: "1.2rem", fontWeight: 700, color: "#2d5a2d" }}>
                    {progress.currentXp}
                  </div>
                  <div style={{ fontSize: "0.8rem", color: "#6b6b6b" }}>
                    Total XP
                  </div>
                </div>
              </div>

              {/* History */}
              {history.length > 0 && (
                <div style={{ marginTop: "16px", paddingTop: "16px", borderTop: "1px solid rgba(200, 190, 180, 0.3)" }}>
                  <div style={{ fontSize: "0.85rem", fontWeight: 600, marginBottom: "12px", color: "#6b6b6b" }}>
                    Tidigare månader
                  </div>
                  <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
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
                            gap: "12px",
                            fontSize: "0.85rem"
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
                </div>
              )}
            </section>
          );
        })}
      </div>

      {/* Bonus XP Dialog */}
      {bonusXpDialog && (
        <>
          <div 
            className="backdrop" 
            onClick={() => !awardingXp && setBonusXpDialog(null)}
            style={{ zIndex: 100 }}
          />
          <div style={{
            position: "fixed",
            top: "50%",
            left: "50%",
            transform: "translate(-50%, -50%)",
            background: "white",
            borderRadius: "12px",
            padding: "24px",
            boxShadow: "0 8px 32px rgba(0, 0, 0, 0.2)",
            zIndex: 101,
            maxWidth: "90%",
            width: "400px"
          }}>
            <h3 style={{ marginTop: 0, marginBottom: "16px", fontSize: "1.1rem", fontWeight: 600 }}>
              Ge bonus-XP till {bonusXpDialog.childName}
            </h3>
            <div style={{ marginBottom: "16px" }}>
              <label htmlFor="bonusXpAmount" style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>
                XP-poäng (1-100)
              </label>
              <input
                id="bonusXpAmount"
                type="number"
                min="1"
                max="100"
                value={bonusXpAmount}
                onChange={(e) => setBonusXpAmount(e.target.value)}
                disabled={awardingXp}
                style={{
                  width: "100%",
                  padding: "10px",
                  borderRadius: "8px",
                  border: "1px solid #ddd",
                  fontSize: "1rem"
                }}
              />
            </div>
            <div style={{ display: "flex", gap: "8px", justifyContent: "flex-end" }}>
              <button
                type="button"
                className="todo-action-button"
                onClick={() => setBonusXpDialog(null)}
                disabled={awardingXp}
              >
                Avbryt
              </button>
              <button
                type="button"
                className="button-primary"
                onClick={handleAwardBonusXp}
                disabled={awardingXp}
              >
                {awardingXp ? "Ger XP..." : "Ge XP"}
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

function getBadgesForMonth(month: number): Record<number, string> {
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
}

import { useEffect, useState } from "react";
import { fetchAllFamilyMembers, FamilyMemberResponse } from "../../shared/api/familyMembers";
import { fetchMemberXpProgress, fetchMemberXpHistory, awardBonusXp, XpProgressResponse, XpHistoryResponse } from "../../shared/api/xp";
import { fetchMemberPet, fetchMemberPetHistory, PetResponse, PetHistoryResponse } from "../../shared/api/pets";
import { PetVisualization } from "../pet/PetVisualization";
import { getIntegratedPetImagePath, getPetBackgroundImagePath, checkIntegratedImageExists, getPetNameSwedish } from "../pet/petImageUtils";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers";

type ChildrenXpViewProps = {
  onNavigate?: (view: ViewKey) => void;
};

const MAX_LEVEL = 5;
const XP_PER_LEVEL = 24;

type ChildXpData = {
  member: FamilyMemberResponse;
  progress: XpProgressResponse | null;
  history: XpHistoryResponse[];
  pet: PetResponse | null;
  petHistory: PetHistoryResponse[];
  loading: boolean;
  error: string | null;
};

export function ChildrenXpView({ onNavigate }: ChildrenXpViewProps) {
  const [children, setChildren] = useState<FamilyMemberResponse[]>([]);
  const [childrenXpData, setChildrenXpData] = useState<Map<string, ChildXpData>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [hasIntegratedImage, setHasIntegratedImage] = useState<Map<string, boolean>>(new Map());
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
        const integratedImageMap = new Map<string, boolean>();
        
        for (const child of childrenMembers) {
          try {
            const [progress, history, pet, petHistory] = await Promise.all([
              fetchMemberXpProgress(child.id).catch(() => null),
              fetchMemberXpHistory(child.id).catch(() => []),
              fetchMemberPet(child.id).catch(() => null),
              fetchMemberPetHistory(child.id).catch(() => [])
            ]);
            
            xpDataMap.set(child.id, {
              member: child,
              progress,
              history,
              pet,
              petHistory,
              loading: false,
              error: null
            });
            
            // Check if integrated image exists for this pet
            if (pet) {
              const integratedExists = await checkIntegratedImageExists(pet.petType, pet.growthStage);
              integratedImageMap.set(child.id, integratedExists);
            }
          } catch (e) {
            xpDataMap.set(child.id, {
              member: child,
              progress: null,
              history: [],
              pet: null,
              petHistory: [],
              loading: false,
              error: "Kunde inte ladda XP-data"
            });
          }
        }

        setChildrenXpData(xpDataMap);
        setHasIntegratedImage(integratedImageMap);
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
      
      // Reload pet data to get updated growth stage
      try {
        const [pet, petHistory] = await Promise.all([
          fetchMemberPet(bonusXpDialog.childId).catch(() => null),
          fetchMemberPetHistory(bonusXpDialog.childId).catch(() => [])
        ]);
        
        // Update the child's progress and pet in state
        setChildrenXpData(prev => {
          const newMap = new Map(prev);
          const childData = newMap.get(bonusXpDialog.childId);
          if (childData) {
            const updatedPet = pet || childData.pet;
            newMap.set(bonusXpDialog.childId, {
              ...childData,
              progress: updatedProgress,
              pet: updatedPet,
              petHistory: petHistory.length > 0 ? petHistory : childData.petHistory
            });
            
            // Check if integrated image exists for updated pet
            if (updatedPet) {
              checkIntegratedImageExists(updatedPet.petType, updatedPet.growthStage).then(exists => {
                setHasIntegratedImage(prev => {
                  const newMap = new Map(prev);
                  newMap.set(bonusXpDialog.childId, exists);
                  return newMap;
                });
              });
            }
          }
          return newMap;
        });
      } catch (e) {
        // If pet reload fails, still update progress
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
      }

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
                <h2 className="view-title" style={{ margin: 0 }}>Mina Barns Djur</h2>
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
                <h2 className="view-title" style={{ margin: 0 }}>Mina Barns Djur</h2>
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
                <h2 className="view-title" style={{ margin: 0 }}>Mina Barns Djur</h2>
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
          const pet = xpData?.pet;
          const petHistory = xpData?.petHistory || [];

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

          const hasIntegrated = hasIntegratedImage.get(child.id) || false;
          
          return (
            <section key={child.id} className="card" style={{ 
              background: pet 
                ? (hasIntegrated 
                    ? `url(${getIntegratedPetImagePath(pet.petType, pet.growthStage)})`
                    : `url(${getPetBackgroundImagePath(pet.petType)})`)
                : "linear-gradient(135deg, rgba(184, 230, 184, 0.1) 0%, rgba(184, 230, 184, 0.05) 100%)",
              backgroundSize: "cover",
              backgroundPosition: "center",
              backgroundRepeat: "no-repeat",
              backgroundColor: pet ? "white" : "transparent",
              border: "2px solid rgba(184, 230, 184, 0.3)",
              position: "relative",
              overflow: "hidden"
            }}>
              {/* Overlay for text readability if background image */}
              {pet && (
                <div style={{
                  position: "absolute",
                  top: 0,
                  left: 0,
                  width: "100%",
                  height: "100%",
                  background: "linear-gradient(to bottom, rgba(255,255,255,0.6) 0%, rgba(255,255,255,0.8) 50%, rgba(255,255,255,0.9) 100%)",
                  zIndex: 0,
                }} />
              )}
              
              <div style={{ position: "relative", zIndex: 1 }}>
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
                  {pet ? (
                    <>
                      {/* Only show PetVisualization if integrated image doesn't exist */}
                      {!hasIntegrated && (
                        <div style={{ marginBottom: "12px", display: "flex", justifyContent: "center" }}>
                          <PetVisualization petType={pet.petType} growthStage={pet.growthStage} size="medium" />
                        </div>
                      )}
                      <div style={{ fontSize: "1.1rem", fontWeight: 600, color: "#2d5a2d", marginBottom: "4px" }}>
                        {pet.name || getPetNameSwedish(pet.petType)}
                      </div>
                      <div style={{ fontSize: "0.85rem", color: "#6b6b6b", marginBottom: "4px" }}>
                        Växtsteg {pet.growthStage} av 5
                      </div>
                    </>
                  ) : (
                    <div style={{ fontSize: "2.5rem", marginBottom: "6px" }}>
                      🥚
                    </div>
                  )}
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
              {(history.length > 0 || petHistory.length > 0) && (
                <div style={{ marginTop: "16px", paddingTop: "16px", borderTop: "1px solid rgba(200, 190, 180, 0.3)" }}>
                  <div style={{ fontSize: "0.85rem", fontWeight: 600, marginBottom: "12px", color: "#6b6b6b" }}>
                    Tidigare månader
                  </div>
                  <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                    {history.map((h) => {
                      // Find matching pet history if available
                      const matchingPet = petHistory.find(p => p.year === h.year && p.month === h.month);
                      
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
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            minWidth: "48px",
                            minHeight: "48px"
                          }}>
                            {matchingPet ? (
                              <PetVisualization petType={matchingPet.petType} growthStage={matchingPet.finalGrowthStage} size="small" />
                            ) : (
                              <div style={{ fontSize: "1.5rem" }}>🥚</div>
                            )}
                          </div>
                          <div style={{ flex: 1 }}>
                            <div style={{ fontWeight: 600, marginBottom: "2px", color: "#2d5a2d" }}>
                              {monthNames[h.month - 1]} {h.year}
                            </div>
                            {matchingPet && (
                              <div style={{ fontSize: "0.7rem", color: "#6b6b6b", marginBottom: "2px" }}>
                                {getPetNameSwedish(matchingPet.petType)} • Växtsteg {matchingPet.finalGrowthStage}
                              </div>
                            )}
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
              </div>
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

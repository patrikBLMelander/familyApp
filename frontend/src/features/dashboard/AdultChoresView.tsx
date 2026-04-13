import { useState, useEffect } from "react";
import {
  getDailyChoresForDate,
  createDailyChore,
  markDailyChoreCompleted,
  unmarkDailyChoreCompleted,
  deleteDailyChore,
  DailyChoreWithCompletionResponse,
} from "../../shared/api/dailyChores";
import { getMemberByDeviceToken } from "../../shared/api/familyMembers";
import { fetchCurrentPet, feedPet, getLastFedDate, PetResponse } from "../../shared/api/pets";
import { fetchCurrentXpProgress, XpProgressResponse } from "../../shared/api/xp";
import { getRandomPetMessage } from "../pet/petFoodUtils";
import {
  getIntegratedPetImagePath,
  getPetBackgroundImagePath,
  checkIntegratedImageExists,
  getPetNameSwedish,
} from "../pet/petImageUtils";
import { HalfCircleProgress } from "./components/HalfCircleProgress";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers" | "childrenxp" | "childrenwallet" | "childview";

type AdultChoresViewProps = {
  onNavigate?: (view: ViewKey) => void;
};

function getTodayString(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

const MAX_LEVEL = 5;
const XP_THRESHOLDS = [0, 10, 35, 70, 125];

export function AdultChoresView({ onNavigate }: AdultChoresViewProps) {
  const [memberId, setMemberId] = useState<string | null>(null);
  const [chores, setChores] = useState<DailyChoreWithCompletionResponse[]>([]);
  const [loading, setLoading] = useState(true);

  // Pet display state
  const [showPet, setShowPet] = useState(false);
  const [petLoaded, setPetLoaded] = useState(false);
  const [pet, setPet] = useState<PetResponse | null>(null);
  const [xpProgress, setXpProgress] = useState<XpProgressResponse | null>(null);
  const [hasIntegratedImage, setHasIntegratedImage] = useState(false);
  const [petMood, setPetMood] = useState<"happy" | "hungry">("hungry");
  const [petMoodMessage, setPetMoodMessage] = useState("");
  const [showXpGain, setShowXpGain] = useState(false);
  const [xpGainAmount, setXpGainAmount] = useState(0);
  const [windowWidth, setWindowWidth] = useState(
    typeof window !== "undefined" ? window.innerWidth : 390
  );

  // Recurring form state
  const [showAddRecurring, setShowAddRecurring] = useState(false);
  const [recurringTitle, setRecurringTitle] = useState("");
  const [recurringWeekdays, setRecurringWeekdays] = useState<Set<string>>(new Set());
  const [recurringXp, setRecurringXp] = useState(1);
  const [addingRecurring, setAddingRecurring] = useState(false);
  const [recurringError, setRecurringError] = useState<string | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

  useEffect(() => {
    const handleResize = () => setWindowWidth(window.innerWidth);
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const deviceToken = localStorage.getItem("deviceToken");
        if (!deviceToken) return;
        const member = await getMemberByDeviceToken(deviceToken);
        setMemberId(member.id);
        const today = getTodayString();
        const fetchedChores = await getDailyChoresForDate(member.id, today).catch(() => [] as DailyChoreWithCompletionResponse[]);
        setChores(fetchedChores);
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, []);

  useEffect(() => {
    if (!showPet || petLoaded) return;
    const loadPet = async () => {
      const [fetchedPet, fetchedXp, lastFed] = await Promise.all([
        fetchCurrentPet().catch(() => null),
        fetchCurrentXpProgress().catch(() => null),
        getLastFedDate().catch(() => null),
      ]);
      setPet(fetchedPet);
      setXpProgress(fetchedXp);
      if (fetchedPet) {
        const exists = await checkIntegratedImageExists(fetchedPet.petType, fetchedPet.growthStage);
        setHasIntegratedImage(exists);
      }
      const mood = lastFed?.lastFedAt?.startsWith(getTodayString()) ? "happy" : "hungry";
      setPetMood(mood);
      setPetMoodMessage(getRandomPetMessage(mood));
      setPetLoaded(true);
    };
    void loadPet();
  }, [showPet, petLoaded]);

  const handleToggleChore = async (choreId: string) => {
    if (!memberId) return;
    const chore = chores.find(c => c.chore.id === choreId);
    if (!chore) return;
    const today = getTodayString();
    setChores(prev => prev.map(c => c.chore.id === choreId ? { ...c, completed: !c.completed } : c));
    try {
      if (chore.completed) {
        await unmarkDailyChoreCompleted(choreId, today);
      } else {
        await markDailyChoreCompleted(choreId, today);
        if (showPet && petLoaded && pet && chore.chore.xpPoints > 0) {
          const xpAmount = chore.chore.xpPoints;
          await feedPet(xpAmount).catch(() => {});
          setXpGainAmount(xpAmount);
          setShowXpGain(true);
          setTimeout(() => setShowXpGain(false), 1500);
          const prevLevel = xpProgress?.currentLevel ?? 0;
          const newXp = await fetchCurrentXpProgress().catch(() => null);
          setXpProgress(newXp);
          if (newXp && newXp.currentLevel > prevLevel) {
            const updatedPet = await fetchCurrentPet().catch(() => null);
            setPet(updatedPet);
            if (updatedPet) {
              setHasIntegratedImage(await checkIntegratedImageExists(updatedPet.petType, updatedPet.growthStage));
            }
          }
          setPetMood("happy");
          setPetMoodMessage(getRandomPetMessage("happy"));
        }
      }
    } catch {
      setChores(prev => prev.map(c => c.chore.id === choreId ? { ...c, completed: chore.completed } : c));
    }
  };

  const handleDeleteChore = async (choreId: string) => {
    setChores(prev => prev.filter(c => c.chore.id !== choreId));
    setConfirmDeleteId(null);
    try {
      await deleteDailyChore(choreId);
    } catch {
      // Restore on failure
      const today = getTodayString();
      if (memberId) {
        const restored = await getDailyChoresForDate(memberId, today).catch(() => [] as DailyChoreWithCompletionResponse[]);
        setChores(restored);
      }
    }
  };

  const handleAddRecurring = async () => {
    if (!memberId) return;
    if (!recurringTitle.trim()) { setRecurringError("Titel krävs"); return; }
    if (recurringWeekdays.size === 0) { setRecurringError("Välj minst en veckodag"); return; }
    setAddingRecurring(true);
    setRecurringError(null);
    try {
      await createDailyChore(memberId, recurringTitle.trim(), Array.from(recurringWeekdays), recurringXp);
      const today = getTodayString();
      const updatedChores = await getDailyChoresForDate(memberId, today).catch(() => [] as DailyChoreWithCompletionResponse[]);
      setChores(updatedChores);
      setRecurringTitle("");
      setRecurringWeekdays(new Set());
      setRecurringXp(1);
      setShowAddRecurring(false);
    } catch (e) {
      setRecurringError(e instanceof Error ? e.message : "Kunde inte skapa uppgifterna");
    } finally {
      setAddingRecurring(false);
    }
  };

  const completedCount = chores.filter(c => c.completed).length;
  const totalCount = chores.length;

  const progressPercentage = xpProgress
    ? xpProgress.currentLevel >= MAX_LEVEL
      ? 100
      : (() => {
          const idx = xpProgress.currentLevel - 1;
          const range = XP_THRESHOLDS[xpProgress.currentLevel] - XP_THRESHOLDS[idx];
          if (range <= 0) return 100;
          return Math.min(100, Math.max(0, (xpProgress.xpInCurrentLevel / range) * 100));
        })()
    : 0;

  const petDisplayName = pet ? (pet.name || getPetNameSwedish(pet.petType)) : null;

  return (
    <div
      style={{
        background: "linear-gradient(160deg, #E0E7FF 0%, #E0F2FE 100%)",
        minHeight: "100vh",
        margin: "0 -16px -24px",
        padding: "20px 16px",
      }}
    >
      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "20px" }}>
        <button
          type="button"
          onClick={() => onNavigate?.("dashboard")}
          style={{
            background: "rgba(255, 255, 255, 0.25)",
            border: "none",
            borderRadius: "10px",
            padding: "8px 14px",
            cursor: "pointer",
            fontSize: "1rem",
            color: "#1e3a5f",
            fontWeight: 600,
          }}
        >
          ← Tillbaka
        </button>
        <h2 style={{ margin: 0, fontSize: "1.4rem", fontWeight: 700, color: "#1C1917", flex: 1 }}>
          Mina sysslor idag
        </h2>
        <button
          type="button"
          onClick={() => setShowPet(v => !v)}
          style={{
            background: showPet ? "rgba(255,255,255,0.6)" : "rgba(255,255,255,0.25)",
            border: "none",
            borderRadius: "10px",
            padding: "8px 12px",
            cursor: "pointer",
            fontSize: "1.3rem",
            lineHeight: 1,
          }}
          title={showPet ? "Dölj djur" : "Visa djur"}
        >
          🐾
        </button>
      </div>

      {loading ? (
        <p style={{ color: "#57534E", textAlign: "center", padding: "40px 0" }}>Laddar...</p>
      ) : (
        <>
          {/* Optional pet card */}
          {showPet && (
            petLoaded ? (
              pet ? (
                <section
                  className="card"
                  style={{
                    padding: 0,
                    marginBottom: "24px",
                    borderRadius: "24px",
                    boxShadow: "0 10px 25px rgba(0,0,0,0.1)",
                    overflow: "visible",
                    backgroundColor: "white",
                    position: "relative",
                  }}
                >
                  <div
                    style={{
                      backgroundImage: hasIntegratedImage
                        ? `url(${getIntegratedPetImagePath(pet.petType, pet.growthStage)})`
                        : `url(${getPetBackgroundImagePath(pet.petType)})`,
                      backgroundSize: "cover",
                      backgroundPosition: "center",
                      backgroundRepeat: "no-repeat",
                      backgroundColor: "white",
                      width: "100%",
                      aspectRatio: "3 / 2",
                      position: "relative",
                      borderRadius: "24px 24px 0 0",
                      paddingBottom: windowWidth < 768 ? "40px" : "60px",
                    }}
                  >
                    <div
                      style={{
                        position: "absolute",
                        bottom: windowWidth < 768 ? "-40px" : "-60px",
                        left: "50%",
                        transform: "translateX(-50%)",
                        width: "100%",
                        display: "flex",
                        justifyContent: "center",
                        alignItems: "center",
                        zIndex: 10,
                      }}
                    >
                      <HalfCircleProgress
                        progress={progressPercentage}
                        currentLevel={xpProgress?.currentLevel ?? 1}
                        mood={petMood}
                        petName={petDisplayName ?? pet.petType}
                        size={windowWidth < 768 ? 100 : 140}
                        strokeWidth={windowWidth < 768 ? 7 : 10}
                      />
                    </div>
                  </div>
                  <div
                    style={{
                      paddingBottom: windowWidth < 768 ? "20px" : "30px",
                      marginTop: windowWidth < 768 ? "20px" : "30px",
                    }}
                  />
                  <p style={{ textAlign: "center", fontStyle: "italic", padding: "0 16px 12px", color: "#4a5568", fontSize: "0.9rem", margin: 0 }}>
                    {petMoodMessage}
                  </p>
                  {showXpGain && (
                    <div style={{
                      position: "absolute", top: "40%", left: "50%",
                      transform: "translateX(-50%)",
                      background: "rgba(0,0,0,0.7)", color: "white",
                      padding: "8px 16px", borderRadius: "20px",
                      fontSize: "1.2rem", fontWeight: 700, zIndex: 20, pointerEvents: "none"
                    }}>
                      +{xpGainAmount} XP
                    </div>
                  )}
                </section>
              ) : (
                <section
                  className="card"
                  style={{
                    background: "rgba(255,255,255,0.82)",
                    backdropFilter: "blur(8px)",
                    WebkitBackdropFilter: "blur(8px)",
                    borderRadius: "20px",
                    padding: "24px",
                    marginBottom: "24px",
                    textAlign: "center",
                  }}
                >
                  <div style={{ fontSize: "3rem", marginBottom: "8px" }}>🥚</div>
                  <p style={{ margin: 0, color: "#57534E" }}>Inget djur valt denna månad</p>
                </section>
              )
            ) : (
              <p style={{ color: "#57534E", textAlign: "center", padding: "20px 0" }}>Laddar djur...</p>
            )
          )}

        <section
          style={{
            background: "rgba(255, 255, 255, 0.82)",
            backdropFilter: "blur(8px)",
            WebkitBackdropFilter: "blur(8px)",
            borderRadius: "20px",
            padding: "24px",
            boxShadow: "0 4px 6px rgba(0,0,0,0.1)",
          }}
        >
          <div style={{ marginBottom: "16px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "10px" }}>
              <h3 style={{ margin: 0, fontSize: "1.2rem", fontWeight: 600, color: "#2d3748" }}>
                📝 Dagens sysslor
              </h3>
              <span style={{ fontSize: "1rem", fontWeight: 600, color: totalCount > 0 && completedCount === totalCount ? "#48bb78" : "#4a5568" }}>
                {completedCount} / {totalCount}
              </span>
            </div>
            <div style={{ display: "flex", gap: "8px" }}>
              <button
                type="button"
                onClick={() => { setShowAddRecurring(v => !v); setRecurringError(null); }}
                style={{
                  flex: 1,
                  padding: "8px 10px",
                  background: showAddRecurring ? "#4C1D95" : "#DDD6FE",
                  color: showAddRecurring ? "white" : "#4C1D95",
                  border: "none",
                  borderRadius: "10px",
                  fontWeight: 600,
                  fontSize: "0.85rem",
                  cursor: "pointer",
                }}
              >
                + Lägg till återkommande
              </button>
            </div>
          </div>

          {/* Inline recurring form */}
          {showAddRecurring && (
            <div style={{
              background: "#F5F3FF",
              borderRadius: "14px",
              padding: "16px",
              marginBottom: "16px",
              border: "2px solid #DDD6FE",
            }}>
              <p style={{ margin: "0 0 10px", fontWeight: 600, fontSize: "0.95rem", color: "#3B0764" }}>
                Återkommande uppgift
              </p>
              <input
                type="text"
                placeholder="Titel"
                value={recurringTitle}
                onChange={e => { setRecurringTitle(e.target.value); setRecurringError(null); }}
                style={{
                  width: "100%",
                  padding: "10px 12px",
                  border: "2px solid #DDD6FE",
                  borderRadius: "10px",
                  fontSize: "1rem",
                  marginBottom: "12px",
                  boxSizing: "border-box",
                  outline: "none",
                }}
              />
              <p style={{ margin: "0 0 8px", fontSize: "0.85rem", color: "#3B0764", fontWeight: 500 }}>
                Veckodagar
              </p>
              <div style={{ display: "flex", gap: "6px", flexWrap: "wrap", marginBottom: "14px" }}>
                {(["MON","TUE","WED","THU","FRI","SAT","SUN"] as const).map((wk, i) => {
                  const label = ["Mån","Tis","Ons","Tor","Fre","Lör","Sön"][i];
                  return (
                  <button
                    key={wk}
                    type="button"
                    onClick={() => setRecurringWeekdays(prev => {
                      const next = new Set(prev);
                      if (next.has(wk)) next.delete(wk); else next.add(wk);
                      return next;
                    })}
                    style={{
                      padding: "6px 12px",
                      borderRadius: "8px",
                      border: "none",
                      fontWeight: 600,
                      fontSize: "0.85rem",
                      cursor: "pointer",
                      background: recurringWeekdays.has(wk) ? "#4C1D95" : "#DDD6FE",
                      color: recurringWeekdays.has(wk) ? "white" : "#4C1D95",
                    }}
                  >
                    {label}
                  </button>
                  );
                })}
              </div>
              <p style={{ margin: "0 0 8px", fontSize: "0.85rem", color: "#3B0764", fontWeight: 500 }}>
                XP
              </p>
              <div style={{ display: "flex", gap: "8px", marginBottom: "14px" }}>
                {[1, 2, 3].map(n => (
                  <button
                    key={n}
                    type="button"
                    onClick={() => setRecurringXp(n)}
                    style={{
                      padding: "7px 18px",
                      borderRadius: "8px",
                      border: "none",
                      fontWeight: 600,
                      fontSize: "0.9rem",
                      cursor: "pointer",
                      background: recurringXp === n ? "#4C1D95" : "#DDD6FE",
                      color: recurringXp === n ? "white" : "#4C1D95",
                    }}
                  >
                    x{n}
                  </button>
                ))}
              </div>
              {recurringError && (
                <p style={{ margin: "0 0 8px", color: "#c53030", fontSize: "0.85rem" }}>{recurringError}</p>
              )}
              <div style={{ display: "flex", gap: "8px" }}>
                <button
                  type="button"
                  onClick={() => void handleAddRecurring()}
                  disabled={addingRecurring}
                  style={{
                    flex: 1,
                    padding: "11px",
                    background: addingRecurring ? "#cbd5e0" : "#4C1D95",
                    color: "white",
                    border: "none",
                    borderRadius: "10px",
                    fontWeight: 600,
                    fontSize: "0.95rem",
                    cursor: addingRecurring ? "not-allowed" : "pointer",
                  }}
                >
                  {addingRecurring ? "Sparar…" : "Skapa uppgifter"}
                </button>
                <button
                  type="button"
                  onClick={() => { setShowAddRecurring(false); setRecurringTitle(""); setRecurringWeekdays(new Set()); setRecurringXp(1); setRecurringError(null); }}
                  style={{
                    padding: "11px 18px",
                    background: "transparent",
                    color: "#4C1D95",
                    border: "2px solid #DDD6FE",
                    borderRadius: "10px",
                    fontWeight: 500,
                    fontSize: "0.95rem",
                    cursor: "pointer",
                  }}
                >
                  Avbryt
                </button>
              </div>
            </div>
          )}

          {chores.length === 0 ? (
            <p style={{ margin: 0, color: "#718096", textAlign: "center", padding: "20px 0" }}>
              Inga sysslor för idag! 🎉
            </p>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
              {chores.map((choreItem) => {
                const bgColor = choreItem.completed ? "#f0fff4" : "#f7fafc";
                const borderColor = choreItem.completed ? "#48bb78" : "#e2e8f0";
                return (
                  <div key={`chore-${choreItem.chore.id}`} style={{ borderRadius: "12px", border: `2px solid ${borderColor}`, overflow: "hidden" }}>
                    <div
                      style={{ padding: "14px 12px 14px 16px", background: bgColor, display: "flex", alignItems: "center", gap: "12px", cursor: "pointer" }}
                      onClick={() => void handleToggleChore(choreItem.chore.id)}
                    >
                      <div style={{ fontSize: "1.5rem", opacity: choreItem.completed ? 1 : 0.5, flexShrink: 0 }}>
                        {choreItem.completed ? "✅" : "⭕"}
                      </div>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontWeight: choreItem.completed ? 600 : 500, color: "#2d3748", textDecoration: choreItem.completed ? "line-through" : "none" }}>
                          {choreItem.chore.title}
                        </div>
                        {choreItem.chore.xpPoints > 0 && (
                          <div style={{ fontSize: "0.85rem", color: "#718096", marginTop: "2px" }}>
                            {choreItem.chore.xpPoints} XP · 🔁
                          </div>
                        )}
                      </div>
                      <button
                        onClick={(e) => { e.stopPropagation(); setConfirmDeleteId(choreItem.chore.id); }}
                        style={{ background: "none", border: "none", cursor: "pointer", padding: "4px 6px", fontSize: "1.1rem", color: "#a0aec0", flexShrink: 0 }}
                        aria-label="Ta bort syssla"
                      >
                        🗑️
                      </button>
                    </div>
                    {confirmDeleteId === choreItem.chore.id && (
                      <div style={{ padding: "10px 16px", background: "#fff5f5", borderTop: `1px solid ${borderColor}`, display: "flex", alignItems: "center", justifyContent: "space-between", gap: "8px" }}>
                        <span style={{ fontSize: "0.9rem", color: "#c53030" }}>Ta bort sysslan?</span>
                        <div style={{ display: "flex", gap: "8px" }}>
                          <button
                            onClick={() => void handleDeleteChore(choreItem.chore.id)}
                            style={{ padding: "5px 14px", background: "#e53e3e", color: "white", border: "none", borderRadius: "8px", fontWeight: 600, fontSize: "0.85rem", cursor: "pointer" }}
                          >
                            Ta bort
                          </button>
                          <button
                            onClick={() => setConfirmDeleteId(null)}
                            style={{ padding: "5px 14px", background: "#edf2f7", color: "#4a5568", border: "none", borderRadius: "8px", fontWeight: 600, fontSize: "0.85rem", cursor: "pointer" }}
                          >
                            Avbryt
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </section>
        </>
      )}
    </div>
  );
}

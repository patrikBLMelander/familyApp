import { useState, useEffect, useCallback } from "react";
import {
  getDailyChoresForDate,
  createDailyChore,
  deleteDailyChore,
  markDailyChoreCompleted,
  unmarkDailyChoreCompleted,
  DailyChoreWithCompletionResponse,
} from "../../shared/api/dailyChores";
import { fetchMemberPet, PetResponse } from "../../shared/api/pets";
import { fetchMemberXpProgress, XpProgressResponse } from "../../shared/api/xp";
import {
  getIntegratedPetImagePath,
  getPetBackgroundImagePath,
  checkIntegratedImageExists,
  getPetNameSwedish,
} from "../pet/petImageUtils";
import { getPetFoodName } from "../pet/petFoodUtils";
import { getPetGradient, isDarkPetTheme } from "../pet/petTheme";
import { HalfCircleProgress } from "./components/HalfCircleProgress";

type ParentChildViewProps = {
  childId: string;
  childName: string;
  onBack: () => void;
};

const MAX_LEVEL = 5;
const XP_THRESHOLDS = [0, 10, 35, 70, 125];

function getTodayString(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export function ParentChildView({ childId, childName, onBack }: ParentChildViewProps) {
  const [todayChores, setTodayChores] = useState<DailyChoreWithCompletionResponse[]>([]);
  const [pet, setPet] = useState<PetResponse | null>(null);
  const [xpProgress, setXpProgress] = useState<XpProgressResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [hasIntegratedImage, setHasIntegratedImage] = useState(false);
  const [windowWidth, setWindowWidth] = useState(
    typeof window !== "undefined" ? window.innerWidth : 390
  );

  // Week view state
  const [activeTab, setActiveTab] = useState<"today" | "week">("today");
  const [weekOffset, setWeekOffset] = useState(0); // 0 = current week
  const [weekTasks, setWeekTasks] = useState<Map<string, DailyChoreWithCompletionResponse[]>>(new Map());
  const [loadingWeek, setLoadingWeek] = useState(false);
  const [weekRefreshKey, setWeekRefreshKey] = useState(0);

  // Week chore form state
  const [showWeekChoreForm, setShowWeekChoreForm] = useState(false);
  const [weekChoreTitle, setWeekChoreTitle] = useState("");
  const [weekChoreWeekdays, setWeekChoreWeekdays] = useState<Set<string>>(new Set());
  const [weekChoreXp, setWeekChoreXp] = useState(1);
  const [weekChoreError, setWeekChoreError] = useState<string | null>(null);
  const [addingWeekChore, setAddingWeekChore] = useState(false);

  // Today recurring chore form state
  const [showAddTodayChore, setShowAddTodayChore] = useState(false);
  const [todayChoreTitle, setTodayChoreTitle] = useState("");
  const [todayChoreWeekdays, setTodayChoreWeekdays] = useState<Set<string>>(new Set());
  const [todayChoreXp, setTodayChoreXp] = useState(1);
  const [todayChoreError, setTodayChoreError] = useState<string | null>(null);
  const [addingTodayChore, setAddingTodayChore] = useState(false);


  useEffect(() => {
    const handleResize = () => setWindowWidth(window.innerWidth);
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const today = getTodayString();
        const [fetchedChores, fetchedPet, fetchedXp] = await Promise.all([
          getDailyChoresForDate(childId, today).catch(() => [] as DailyChoreWithCompletionResponse[]),
          fetchMemberPet(childId).catch(() => null),
          fetchMemberXpProgress(childId).catch(() => null),
        ]);
        setTodayChores(fetchedChores);
        setPet(fetchedPet);
        setXpProgress(fetchedXp);
        if (fetchedPet) {
          const exists = await checkIntegratedImageExists(fetchedPet.petType, fetchedPet.growthStage);
          setHasIntegratedImage(exists);
        }
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, [childId]);

  // Get Monday of the week at the given offset from today
  const getWeekDays = useCallback((offset: number): Date[] => {
    const today = new Date();
    const dayOfWeek = today.getDay(); // 0=Sun, 1=Mon...
    const monday = new Date(today);
    monday.setDate(today.getDate() - (dayOfWeek === 0 ? 6 : dayOfWeek - 1) + offset * 7);
    monday.setHours(0, 0, 0, 0);
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(monday);
      d.setDate(monday.getDate() + i);
      return d;
    });
  }, []);

  useEffect(() => {
    if (activeTab !== "week") return;
    let ignore = false;
    const loadWeek = async () => {
      setLoadingWeek(true);
      const days = getWeekDays(weekOffset);
      const entries = await Promise.all(
        days.map(async (day) => {
          const key = `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`;
          const chores = await getDailyChoresForDate(childId, key).catch(() => [] as DailyChoreWithCompletionResponse[]);
          return [key, chores] as const;
        })
      );
      if (!ignore) {
        setWeekTasks(new Map(entries));
        setLoadingWeek(false);
      }
    };
    void loadWeek();
    return () => { ignore = true; };
  }, [activeTab, weekOffset, childId, getWeekDays, weekRefreshKey]);

  const handleToggleTodayChore = async (choreId: string) => {
    const chore = todayChores.find(c => c.chore.id === choreId);
    if (!chore) return;
    const today = getTodayString();
    setTodayChores(prev => prev.map(c => c.chore.id === choreId ? { ...c, completed: !c.completed } : c));
    try {
      if (chore.completed) {
        await unmarkDailyChoreCompleted(choreId, today);
      } else {
        await markDailyChoreCompleted(choreId, today);
      }
    } catch {
      setTodayChores(prev => prev.map(c => c.chore.id === choreId ? { ...c, completed: chore.completed } : c));
    }
  };

  const handleAddWeekChore = async () => {
    if (!weekChoreTitle.trim()) { setWeekChoreError("Titel krävs"); return; }
    if (weekChoreWeekdays.size === 0) { setWeekChoreError("Välj minst en veckodag"); return; }
    setAddingWeekChore(true);
    setWeekChoreError(null);
    try {
      await createDailyChore(childId, weekChoreTitle.trim(), Array.from(weekChoreWeekdays), weekChoreXp);
      setWeekChoreTitle("");
      setWeekChoreWeekdays(new Set());
      setWeekChoreXp(1);
      setShowWeekChoreForm(false);
      setWeekRefreshKey(k => k + 1);
      // Also refresh today's chores in case the new chore applies to today
      const today = getTodayString();
      const updatedTodayChores = await getDailyChoresForDate(childId, today).catch(() => [] as DailyChoreWithCompletionResponse[]);
      setTodayChores(updatedTodayChores);
    } catch (e) {
      setWeekChoreError(e instanceof Error ? e.message : "Kunde inte skapa sysslan");
    } finally {
      setAddingWeekChore(false);
    }
  };

  const handleAddTodayChore = async () => {
    if (!todayChoreTitle.trim()) { setTodayChoreError("Titel krävs"); return; }
    if (todayChoreWeekdays.size === 0) { setTodayChoreError("Välj minst en veckodag"); return; }
    setAddingTodayChore(true);
    setTodayChoreError(null);
    try {
      await createDailyChore(childId, todayChoreTitle.trim(), Array.from(todayChoreWeekdays), todayChoreXp);
      const today = getTodayString();
      const updatedChores = await getDailyChoresForDate(childId, today).catch(() => [] as DailyChoreWithCompletionResponse[]);
      setTodayChores(updatedChores);
      setTodayChoreTitle("");
      setTodayChoreWeekdays(new Set());
      setTodayChoreXp(1);
      setShowAddTodayChore(false);
    } catch (e) {
      setTodayChoreError(e instanceof Error ? e.message : "Kunde inte skapa sysslan");
    } finally {
      setAddingTodayChore(false);
    }
  };

  const handleDeleteWeekTask = async (chore: DailyChoreWithCompletionResponse) => {
    try {
      await deleteDailyChore(chore.chore.id);
      setWeekRefreshKey(k => k + 1);
    } catch {
      // silently ignore
    }
  };

  const { from: gradFrom, to: gradTo } = getPetGradient(pet?.petType ?? "");
  const isDark = isDarkPetTheme(pet?.petType ?? "");

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

  const foodName = pet ? getPetFoodName(pet.petType) : "mat";
  const petDisplayName = pet ? (pet.name || getPetNameSwedish(pet.petType)) : null;
  const completedCount = todayChores.filter(c => c.completed).length;
  const totalCount = todayChores.length;

  return (
    <div
      style={{
        background: `linear-gradient(160deg, ${gradFrom} 0%, ${gradTo} 100%)`,
        minHeight: "100vh",
        margin: "0 -16px -24px",
        padding: "20px 16px",
      }}
    >
      {/* Header: back + child name */}
      <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "20px" }}>
        <button
          type="button"
          onClick={onBack}
          style={{
            background: "rgba(255, 255, 255, 0.25)",
            border: "none",
            borderRadius: "10px",
            padding: "8px 14px",
            cursor: "pointer",
            fontSize: "1rem",
            color: isDark ? "white" : "#1e3a5f",
            fontWeight: 600,
          }}
        >
          ← Tillbaka
        </button>
        <h2 style={{ margin: 0, fontSize: "1.4rem", fontWeight: 700, color: isDark ? "white" : "#1C1917" }}>
          {childName}
        </h2>
      </div>

      {loading ? (
        <p style={{ color: isDark ? "rgba(255,255,255,0.8)" : "#57534E", textAlign: "center", padding: "40px 0" }}>
          Laddar...
        </p>
      ) : (
        <>
          {/* Pet card */}
          {pet ? (
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
                    mood="happy"
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
          )}

          {/* Tasks section */}
          <section
            className="card"
            style={{
              background: "rgba(255, 255, 255, 0.82)",
              backdropFilter: "blur(8px)",
              WebkitBackdropFilter: "blur(8px)",
              borderRadius: "20px",
              padding: "24px",
              marginBottom: "24px",
              boxShadow: "0 4px 6px rgba(0,0,0,0.1)",
            }}
          >
            {/* Tab switcher */}
            <div style={{ display: "flex", gap: "8px", marginBottom: "16px" }}>
              <button
                type="button"
                onClick={() => setActiveTab("today")}
                style={{ flex: 1, padding: "9px", borderRadius: "10px", border: "none", fontWeight: 600, fontSize: "0.9rem", cursor: "pointer", background: activeTab === "today" ? "#0C4A6E" : "#BAE6FD", color: activeTab === "today" ? "white" : "#0C4A6E" }}
              >
                📝 Idag
              </button>
              <button
                type="button"
                onClick={() => setActiveTab("week")}
                style={{ flex: 1, padding: "9px", borderRadius: "10px", border: "none", fontWeight: 600, fontSize: "0.9rem", cursor: "pointer", background: activeTab === "week" ? "#0C4A6E" : "#BAE6FD", color: activeTab === "week" ? "white" : "#0C4A6E" }}
              >
                📅 Vecka
              </button>
            </div>

            {/* Week view */}
            {activeTab === "week" && (() => {
              const days = getWeekDays(weekOffset);
              const dayNames = ["Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön"];
              const weekdayKeys = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
              const todayStr = getTodayString();
              const first = days[0];
              const last = days[6];
              const weekLabel = `${first.getDate()}/${first.getMonth() + 1} – ${last.getDate()}/${last.getMonth() + 1}`;
              return (
                <div>
                  {/* Week navigation */}
                  <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "12px" }}>
                    <button type="button" onClick={() => setWeekOffset(w => w - 1)}
                      style={{ background: "#BAE6FD", border: "none", borderRadius: "8px", padding: "6px 14px", fontWeight: 700, fontSize: "1rem", cursor: "pointer", color: "#0C4A6E" }}>
                      ←
                    </button>
                    <span style={{ fontSize: "0.9rem", fontWeight: 600, color: "#2d3748" }}>
                      {weekOffset === 0 ? "Denna vecka" : weekOffset === 1 ? "Nästa vecka" : weekOffset === -1 ? "Förra veckan" : weekLabel}
                      {" · "}{weekLabel}
                    </span>
                    <button type="button" onClick={() => setWeekOffset(w => w + 1)}
                      style={{ background: "#BAE6FD", border: "none", borderRadius: "8px", padding: "6px 14px", fontWeight: 700, fontSize: "1rem", cursor: "pointer", color: "#0C4A6E" }}>
                      →
                    </button>
                  </div>

                  {/* Add chore button / form */}
                  <button
                    type="button"
                    onClick={() => { setShowWeekChoreForm(v => !v); setWeekChoreError(null); }}
                    style={{
                      width: "100%", padding: "8px", marginBottom: "12px",
                      background: showWeekChoreForm ? "#4C1D95" : "#DDD6FE",
                      color: showWeekChoreForm ? "white" : "#4C1D95",
                      border: "none", borderRadius: "10px", fontWeight: 600, fontSize: "0.85rem", cursor: "pointer",
                    }}
                  >
                    {showWeekChoreForm ? "Avbryt" : "+ Lägg till återkommande syssla"}
                  </button>

                  {showWeekChoreForm && (
                    <div style={{ background: "#F5F3FF", borderRadius: "12px", padding: "14px", marginBottom: "12px", border: "2px solid #DDD6FE" }}>
                      <input
                        type="text"
                        placeholder="Titel"
                        value={weekChoreTitle}
                        onChange={e => { setWeekChoreTitle(e.target.value); setWeekChoreError(null); }}
                        style={{ width: "100%", padding: "9px 12px", border: "2px solid #DDD6FE", borderRadius: "9px", fontSize: "0.95rem", marginBottom: "10px", boxSizing: "border-box", outline: "none" }}
                      />
                      <p style={{ margin: "0 0 6px", fontSize: "0.82rem", color: "#3B0764", fontWeight: 600 }}>Veckodagar</p>
                      <div style={{ display: "flex", gap: "5px", flexWrap: "wrap", marginBottom: "10px" }}>
                        {weekdayKeys.map((wk, i) => (
                          <button
                            key={wk}
                            type="button"
                            onClick={() => setWeekChoreWeekdays(prev => {
                              const next = new Set(prev);
                              if (next.has(wk)) next.delete(wk); else next.add(wk);
                              return next;
                            })}
                            style={{
                              padding: "5px 10px", borderRadius: "7px", border: "none", fontWeight: 600, fontSize: "0.8rem", cursor: "pointer",
                              background: weekChoreWeekdays.has(wk) ? "#4C1D95" : "#DDD6FE",
                              color: weekChoreWeekdays.has(wk) ? "white" : "#4C1D95",
                            }}
                          >
                            {dayNames[i]}
                          </button>
                        ))}
                      </div>
                      <p style={{ margin: "0 0 6px", fontSize: "0.82rem", color: "#3B0764", fontWeight: 600 }}>XP (mat)</p>
                      <div style={{ display: "flex", gap: "6px", marginBottom: "10px" }}>
                        {[1, 2, 3].map(n => (
                          <button key={n} type="button" onClick={() => setWeekChoreXp(n)}
                            style={{ padding: "5px 14px", borderRadius: "7px", border: "none", fontWeight: 600, fontSize: "0.85rem", cursor: "pointer", background: weekChoreXp === n ? "#4C1D95" : "#DDD6FE", color: weekChoreXp === n ? "white" : "#4C1D95" }}>
                            x{n}
                          </button>
                        ))}
                      </div>
                      {weekChoreError && <p style={{ margin: "0 0 8px", color: "#c53030", fontSize: "0.82rem" }}>{weekChoreError}</p>}
                      <button
                        type="button"
                        onClick={() => void handleAddWeekChore()}
                        disabled={addingWeekChore}
                        style={{ width: "100%", padding: "10px", background: addingWeekChore ? "#cbd5e0" : "#4C1D95", color: "white", border: "none", borderRadius: "9px", fontWeight: 600, fontSize: "0.9rem", cursor: addingWeekChore ? "not-allowed" : "pointer" }}
                      >
                        {addingWeekChore ? "Sparar…" : "Skapa syssla"}
                      </button>
                    </div>
                  )}

                  {loadingWeek ? (
                    <p style={{ textAlign: "center", color: "#718096", padding: "20px 0" }}>Laddar...</p>
                  ) : (
                    <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                      {days.map((day, i) => {
                        const key = `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`;
                        const dayTaskList = weekTasks.get(key) ?? [];
                        const done = dayTaskList.filter(t => t.completed).length;
                        const isToday = key === todayStr;
                        return (
                          <div key={key} style={{
                            borderRadius: "12px",
                            border: isToday ? "2px solid #0C4A6E" : "1px solid #e2e8f0",
                            overflow: "hidden",
                            background: isToday ? "#EFF6FF" : "white",
                          }}>
                            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "8px 12px", background: isToday ? "#DBEAFE" : "#f7fafc" }}>
                              <span style={{ fontWeight: 700, fontSize: "0.9rem", color: isToday ? "#0C4A6E" : "#2d3748" }}>
                                {dayNames[i]} {day.getDate()}/{day.getMonth() + 1}
                                {isToday && <span style={{ marginLeft: "6px", fontSize: "0.75rem", fontWeight: 600, color: "#0C4A6E" }}>idag</span>}
                              </span>
                              <span style={{ fontSize: "0.8rem", fontWeight: 600, color: dayTaskList.length === 0 ? "#a0aec0" : done === dayTaskList.length ? "#48bb78" : "#718096" }}>
                                {dayTaskList.length === 0 ? "–" : `${done}/${dayTaskList.length}`}
                              </span>
                            </div>
                            {dayTaskList.length > 0 && (
                              <ul style={{ margin: 0, padding: "6px 8px 8px", listStyle: "none", display: "flex", flexDirection: "column", gap: "3px" }}>
                                {dayTaskList.map(t => (
                                  <li key={`chore-${t.chore.id}`} style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "0.85rem", padding: "2px 4px", borderRadius: "6px" }}>
                                    <span style={{ fontSize: "0.9rem", opacity: t.completed ? 1 : 0.4, flexShrink: 0 }}>{t.completed ? "✅" : "⭕"}</span>
                                    <span style={{ flex: 1, color: t.completed ? "#718096" : "#2d3748", textDecoration: t.completed ? "line-through" : "none" }}>{t.chore.title}</span>
                                    {t.chore.xpPoints > 0 && (
                                      <span style={{ fontSize: "0.75rem", color: "#a0aec0", flexShrink: 0 }}>{t.chore.xpPoints} {foodName}</span>
                                    )}
                                    <button
                                      type="button"
                                      onClick={() => void handleDeleteWeekTask(t)}
                                      title="Ta bort"
                                      style={{ background: "none", border: "none", cursor: "pointer", color: "#FCA5A5", fontSize: "0.9rem", padding: "0 2px", flexShrink: 0, lineHeight: 1 }}
                                    >
                                      ✕
                                    </button>
                                  </li>
                                ))}
                              </ul>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })()}

            {/* Today view */}
            {activeTab === "today" && (<>
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
                  onClick={() => { setShowAddTodayChore(v => !v); setTodayChoreError(null); }}
                  style={{
                    flex: 1,
                    padding: "8px 10px",
                    background: showAddTodayChore ? "#4C1D95" : "#DDD6FE",
                    color: showAddTodayChore ? "white" : "#4C1D95",
                    border: "none",
                    borderRadius: "10px",
                    fontWeight: 600,
                    fontSize: "0.85rem",
                    cursor: "pointer",
                  }}
                >
                  🔁 Återkommande
                </button>
              </div>
            </div>

            {showAddTodayChore && (
              <div style={{ background: "#F5F3FF", borderRadius: "14px", padding: "16px", marginBottom: "16px", border: "2px solid #DDD6FE" }}>
                <p style={{ margin: "0 0 10px", fontWeight: 600, fontSize: "0.95rem", color: "#3B0764" }}>
                  Ny återkommande syssla – {childName}
                </p>
                <input
                  type="text"
                  placeholder="Titel"
                  value={todayChoreTitle}
                  onChange={e => { setTodayChoreTitle(e.target.value); setTodayChoreError(null); }}
                  style={{ width: "100%", padding: "10px 12px", border: "2px solid #DDD6FE", borderRadius: "10px", fontSize: "1rem", marginBottom: "12px", boxSizing: "border-box", outline: "none" }}
                />
                <p style={{ margin: "0 0 6px", fontSize: "0.85rem", color: "#3B0764", fontWeight: 600 }}>Veckodagar</p>
                <div style={{ display: "flex", gap: "5px", flexWrap: "wrap", marginBottom: "12px" }}>
                  {(["MON","TUE","WED","THU","FRI","SAT","SUN"] as const).map((wk, i) => {
                    const dayNames = ["Mån","Tis","Ons","Tor","Fre","Lör","Sön"];
                    return (
                      <button
                        key={wk}
                        type="button"
                        onClick={() => setTodayChoreWeekdays(prev => { const next = new Set(prev); if (next.has(wk)) next.delete(wk); else next.add(wk); return next; })}
                        style={{ padding: "6px 12px", borderRadius: "8px", border: "none", fontWeight: 600, fontSize: "0.85rem", cursor: "pointer", background: todayChoreWeekdays.has(wk) ? "#4C1D95" : "#DDD6FE", color: todayChoreWeekdays.has(wk) ? "white" : "#4C1D95" }}
                      >
                        {dayNames[i]}
                      </button>
                    );
                  })}
                </div>
                <p style={{ margin: "0 0 6px", fontSize: "0.85rem", color: "#3B0764", fontWeight: 600 }}>XP (mat)</p>
                <div style={{ display: "flex", gap: "8px", marginBottom: "14px" }}>
                  {[1, 2, 3].map(n => (
                    <button key={n} type="button" onClick={() => setTodayChoreXp(n)}
                      style={{ padding: "7px 18px", borderRadius: "8px", border: "none", fontWeight: 600, fontSize: "0.9rem", cursor: "pointer", background: todayChoreXp === n ? "#4C1D95" : "#DDD6FE", color: todayChoreXp === n ? "white" : "#4C1D95" }}>
                      x{n}
                    </button>
                  ))}
                </div>
                {todayChoreError && <p style={{ margin: "0 0 8px", color: "#c53030", fontSize: "0.85rem" }}>{todayChoreError}</p>}
                <div style={{ display: "flex", gap: "8px" }}>
                  <button
                    type="button"
                    onClick={() => void handleAddTodayChore()}
                    disabled={addingTodayChore}
                    style={{ flex: 1, padding: "11px", background: addingTodayChore ? "#cbd5e0" : "#4C1D95", color: "white", border: "none", borderRadius: "10px", fontWeight: 600, fontSize: "0.95rem", cursor: addingTodayChore ? "not-allowed" : "pointer" }}
                  >
                    {addingTodayChore ? "Sparar…" : "Skapa syssla"}
                  </button>
                  <button
                    type="button"
                    onClick={() => { setShowAddTodayChore(false); setTodayChoreTitle(""); setTodayChoreWeekdays(new Set()); setTodayChoreXp(1); setTodayChoreError(null); }}
                    style={{ padding: "11px 18px", background: "transparent", color: "#4C1D95", border: "2px solid #DDD6FE", borderRadius: "10px", fontWeight: 500, fontSize: "0.95rem", cursor: "pointer" }}
                  >
                    Avbryt
                  </button>
                </div>
              </div>
            )}

            {activeTab === "today" && todayChores.length === 0 ? (
              <p style={{ margin: 0, color: "#718096", textAlign: "center", padding: "20px 0" }}>
                Inga sysslor för idag! 🎉
              </p>
            ) : activeTab === "today" ? (
              <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
                {todayChores.map((choreItem) => {
                  const bgColor = choreItem.completed ? "#f0fff4" : "#f7fafc";
                  const borderColor = choreItem.completed ? "#48bb78" : "#e2e8f0";
                  return (
                    <div key={`chore-${choreItem.chore.id}`} style={{ borderRadius: "12px", border: `2px solid ${borderColor}`, overflow: "hidden" }}>
                      <div
                        style={{ padding: "14px 12px 14px 16px", background: bgColor, display: "flex", alignItems: "center", gap: "12px", cursor: "pointer" }}
                        onClick={() => void handleToggleTodayChore(choreItem.chore.id)}
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
                              {choreItem.chore.xpPoints} {foodName} · 🔁
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : null}
            </>)}
          </section>
        </>
      )}
    </div>
  );
}

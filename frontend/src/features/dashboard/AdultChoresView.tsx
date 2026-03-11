import { useState, useEffect } from "react";
import {
  fetchTasksForToday,
  CalendarTaskWithCompletionResponse,
  markTaskCompleted,
  unmarkTaskCompleted,
  createCalendarEvent,
  updateCalendarEvent,
  deleteCalendarEvent,
} from "../../shared/api/calendar";
import { getMemberByDeviceToken } from "../../shared/api/familyMembers";
import {
  getNextWeekday,
  getDateTwoYearsLater,
  formatDateForAPI,
  WEEKDAY_SHORT_NAMES,
} from "../calendar/utils/weekdayUtils";
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
  const [tasks, setTasks] = useState<CalendarTaskWithCompletionResponse[]>([]);
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

  // Add-task form state
  const [showAddTask, setShowAddTask] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [xpMultiplier, setXpMultiplier] = useState(1);
  const [addingTask, setAddingTask] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);

  // Recurring form state
  const [showAddRecurring, setShowAddRecurring] = useState(false);
  const [recurringTitle, setRecurringTitle] = useState("");
  const [recurringWeekdays, setRecurringWeekdays] = useState<Set<number>>(new Set());
  const [recurringXp, setRecurringXp] = useState(1);
  const [addingRecurring, setAddingRecurring] = useState(false);
  const [recurringError, setRecurringError] = useState<string | null>(null);

  // Task action menu state
  const [menuTaskId, setMenuTaskId] = useState<string | null>(null);
  const [editTaskId, setEditTaskId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState("");
  const [editXp, setEditXp] = useState(1);
  const [editScope, setEditScope] = useState<"THIS" | "THIS_AND_FOLLOWING" | "ALL">("THIS_AND_FOLLOWING");
  const [editingTask, setEditingTask] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null);
  const [deletingTask, setDeletingTask] = useState(false);

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
        const fetchedTasks = await fetchTasksForToday(member.id).catch(() => [] as CalendarTaskWithCompletionResponse[]);
        setTasks(fetchedTasks);
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

  const handleToggleTask = async (taskId: string) => {
    if (!memberId) return;
    const task = tasks.find((t) => t.event.id === taskId);
    if (!task) return;
    const today = getTodayString();
    setTasks((prev) =>
      prev.map((t) => (t.event.id === taskId ? { ...t, completed: !t.completed } : t))
    );
    try {
      if (task.completed) {
        await unmarkTaskCompleted(taskId, memberId, today);
      } else {
        await markTaskCompleted(taskId, memberId, today);
        if (showPet && petLoaded && pet) {
          const xpAmount = task.event.xpPoints ?? 1;
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
      setTasks((prev) =>
        prev.map((t) => (t.event.id === taskId ? { ...t, completed: task.completed } : t))
      );
    }
  };

  const handleAddTask = async () => {
    if (!memberId) return;
    if (!newTitle.trim()) { setAddError("Titel krävs"); return; }
    setAddingTask(true);
    setAddError(null);
    try {
      const today = getTodayString();
      await createCalendarEvent(
        newTitle.trim(),
        `${today}T00:00`,
        null,
        true,
        undefined, undefined, undefined,
        [memberId],
        null, null, null, null,
        true,
        xpMultiplier,
        true,
      );
      const updated = await fetchTasksForToday(memberId).catch(() => [] as CalendarTaskWithCompletionResponse[]);
      setTasks(updated);
      setNewTitle("");
      setXpMultiplier(1);
      setShowAddTask(false);
    } catch (e) {
      setAddError(e instanceof Error ? e.message : "Kunde inte skapa uppgiften");
    } finally {
      setAddingTask(false);
    }
  };

  const handleAddRecurring = async () => {
    if (!memberId) return;
    if (!recurringTitle.trim()) { setRecurringError("Titel krävs"); return; }
    if (recurringWeekdays.size === 0) { setRecurringError("Välj minst en veckodag"); return; }
    setAddingRecurring(true);
    setRecurringError(null);
    try {
      await Promise.all(
        Array.from(recurringWeekdays).map((weekday) => {
          const nextDate = getNextWeekday(weekday);
          const endDate = getDateTwoYearsLater(nextDate);
          return createCalendarEvent(
            recurringTitle.trim(),
            `${formatDateForAPI(nextDate)}T00:00`,
            null, true,
            undefined, undefined, undefined,
            [memberId],
            "WEEKLY", 1, formatDateForAPI(endDate), null,
            true, recurringXp, true,
          ).then(() => {});
        })
      );
      const updated = await fetchTasksForToday(memberId).catch(() => [] as CalendarTaskWithCompletionResponse[]);
      setTasks(updated);
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

  const openEditTask = (task: CalendarTaskWithCompletionResponse) => {
    setEditTaskId(task.event.id);
    setEditTitle(task.event.title);
    setEditXp(task.event.xpPoints ?? 1);
    setEditScope("THIS_AND_FOLLOWING");
    setEditError(null);
    setMenuTaskId(null);
  };

  const handleEditTask = async () => {
    if (!memberId) return;
    const task = tasks.find((t) => t.event.id === editTaskId);
    if (!task || !editTitle.trim()) { setEditError("Titel krävs"); return; }
    setEditingTask(true);
    setEditError(null);
    try {
      const isRecurring = !!task.event.recurringType;
      const today = getTodayString();
      const startDt = isRecurring && editScope !== "ALL"
        ? `${today}T00:00`
        : task.event.startDateTime;
      await updateCalendarEvent(
        task.event.id,
        editTitle.trim(),
        startDt,
        task.event.endDateTime ?? null,
        task.event.isAllDay ?? true,
        undefined, undefined, undefined,
        [memberId],
        isRecurring ? task.event.recurringType : undefined,
        isRecurring ? (task.event.recurringInterval ?? 1) : undefined,
        isRecurring ? task.event.recurringEndDate : undefined,
        isRecurring ? task.event.recurringEndCount : undefined,
        true,
        editXp,
        task.event.isRequired ?? true,
        isRecurring ? editScope : undefined,
        isRecurring ? today : undefined,
      );
      const updated = await fetchTasksForToday(memberId).catch(() => [] as CalendarTaskWithCompletionResponse[]);
      setTasks(updated);
      setEditTaskId(null);
    } catch (e) {
      setEditError(e instanceof Error ? e.message : "Kunde inte spara");
    } finally {
      setEditingTask(false);
    }
  };

  const handleDeleteTask = async (scope: "THIS" | "ALL") => {
    if (!deleteConfirmId || !memberId) return;
    const task = tasks.find((t) => t.event.id === deleteConfirmId);
    if (!task) return;
    setDeletingTask(true);
    try {
      const isRecurring = !!task.event.recurringType;
      await deleteCalendarEvent(
        deleteConfirmId,
        isRecurring ? scope : undefined,
        isRecurring ? getTodayString() : undefined,
      );
      const updated = await fetchTasksForToday(memberId).catch(() => [] as CalendarTaskWithCompletionResponse[]);
      setTasks(updated);
      setDeleteConfirmId(null);
    } catch {
      // silently ignore, re-fetch to get true state
    } finally {
      setDeletingTask(false);
    }
  };

  const completedCount = tasks.filter((t) => t.completed).length;

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
              <span style={{ fontSize: "1rem", fontWeight: 600, color: tasks.length > 0 && completedCount === tasks.length ? "#48bb78" : "#4a5568" }}>
                {completedCount} / {tasks.length}
              </span>
            </div>
            <div style={{ display: "flex", gap: "8px" }}>
              <button
                type="button"
                onClick={() => { setShowAddTask(v => !v); if (showAddRecurring) setShowAddRecurring(false); setAddError(null); }}
                style={{
                  flex: 1,
                  padding: "8px 10px",
                  background: showAddTask ? "#0C4A6E" : "#BAE6FD",
                  color: showAddTask ? "white" : "#0C4A6E",
                  border: "none",
                  borderRadius: "10px",
                  fontWeight: 600,
                  fontSize: "0.85rem",
                  cursor: "pointer",
                }}
              >
                + Lägg till idag
              </button>
              <button
                type="button"
                onClick={() => { setShowAddRecurring(v => !v); if (showAddTask) setShowAddTask(false); setRecurringError(null); }}
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

          {/* Inline add-task form */}
          {showAddTask && (
            <div style={{
              background: "#EFF6FF",
              borderRadius: "14px",
              padding: "16px",
              marginBottom: "16px",
              border: "2px solid #BAE6FD",
            }}>
              <p style={{ margin: "0 0 10px", fontWeight: 600, fontSize: "0.95rem", color: "#1e3a5f" }}>
                Ny uppgift idag
              </p>
              <input
                type="text"
                placeholder="Titel"
                value={newTitle}
                onChange={e => { setNewTitle(e.target.value); setAddError(null); }}
                onKeyDown={e => e.key === "Enter" && void handleAddTask()}
                style={{
                  width: "100%",
                  padding: "10px 12px",
                  border: "2px solid #BAE6FD",
                  borderRadius: "10px",
                  fontSize: "1rem",
                  marginBottom: "12px",
                  boxSizing: "border-box",
                  outline: "none",
                }}
              />
              <p style={{ margin: "0 0 8px", fontSize: "0.85rem", color: "#1e3a5f", fontWeight: 500 }}>
                XP
              </p>
              <div style={{ display: "flex", gap: "8px", marginBottom: "14px" }}>
                {[1, 2, 3].map(n => (
                  <button
                    key={n}
                    type="button"
                    onClick={() => setXpMultiplier(n)}
                    style={{
                      padding: "7px 18px",
                      borderRadius: "8px",
                      border: "none",
                      fontWeight: 600,
                      fontSize: "0.9rem",
                      cursor: "pointer",
                      background: xpMultiplier === n ? "#0C4A6E" : "#BAE6FD",
                      color: xpMultiplier === n ? "white" : "#0C4A6E",
                    }}
                  >
                    x{n}
                  </button>
                ))}
              </div>
              {addError && (
                <p style={{ margin: "0 0 8px", color: "#c53030", fontSize: "0.85rem" }}>{addError}</p>
              )}
              <div style={{ display: "flex", gap: "8px" }}>
                <button
                  type="button"
                  onClick={() => void handleAddTask()}
                  disabled={addingTask}
                  style={{
                    flex: 1,
                    padding: "11px",
                    background: addingTask ? "#cbd5e0" : "#0C4A6E",
                    color: "white",
                    border: "none",
                    borderRadius: "10px",
                    fontWeight: 600,
                    fontSize: "0.95rem",
                    cursor: addingTask ? "not-allowed" : "pointer",
                  }}
                >
                  {addingTask ? "Sparar…" : "Skapa uppgift"}
                </button>
                <button
                  type="button"
                  onClick={() => { setShowAddTask(false); setNewTitle(""); setXpMultiplier(1); setAddError(null); }}
                  style={{
                    padding: "11px 18px",
                    background: "transparent",
                    color: "#0C4A6E",
                    border: "2px solid #BAE6FD",
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
                {[1, 2, 3, 4, 5, 6, 0].map(day => (
                  <button
                    key={day}
                    type="button"
                    onClick={() => setRecurringWeekdays(prev => {
                      const next = new Set(prev);
                      if (next.has(day)) next.delete(day); else next.add(day);
                      return next;
                    })}
                    style={{
                      padding: "6px 12px",
                      borderRadius: "8px",
                      border: "none",
                      fontWeight: 600,
                      fontSize: "0.85rem",
                      cursor: "pointer",
                      background: recurringWeekdays.has(day) ? "#4C1D95" : "#DDD6FE",
                      color: recurringWeekdays.has(day) ? "white" : "#4C1D95",
                    }}
                  >
                    {WEEKDAY_SHORT_NAMES[day]}
                  </button>
                ))}
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

          {tasks.length === 0 ? (
            <p style={{ margin: 0, color: "#718096", textAlign: "center", padding: "20px 0" }}>
              Inga sysslor för idag! 🎉
            </p>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
              {tasks.map((task) => {
                const isEditing = editTaskId === task.event.id;
                const isMenuOpen = menuTaskId === task.event.id;
                const isDeleteConfirm = deleteConfirmId === task.event.id;
                const isRecurring = !!task.event.recurringType;
                const bgColor = task.completed ? "#f0fff4" : task.event.isRequired ? "#f7fafc" : "#fff7ed";
                const borderColor = task.completed ? "#48bb78" : task.event.isRequired ? "#e2e8f0" : "#fed7aa";

                return (
                  <div key={task.event.id} style={{ borderRadius: "12px", border: `2px solid ${borderColor}`, overflow: "hidden" }}>
                    {/* Main task row */}
                    <div
                      style={{
                        padding: "14px 12px 14px 16px",
                        background: bgColor,
                        display: "flex",
                        alignItems: "center",
                        gap: "12px",
                      }}
                    >
                      <div
                        onClick={() => void handleToggleTask(task.event.id)}
                        style={{ fontSize: "1.5rem", opacity: task.completed ? 1 : 0.5, cursor: "pointer", flexShrink: 0 }}
                      >
                        {task.completed ? "✅" : "⭕"}
                      </div>
                      <div
                        onClick={() => void handleToggleTask(task.event.id)}
                        style={{ flex: 1, cursor: "pointer" }}
                      >
                        <div style={{ fontWeight: task.completed ? 600 : 500, color: "#2d3748", textDecoration: task.completed ? "line-through" : "none" }}>
                          {task.event.title}
                        </div>
                        {task.event.xpPoints != null && task.event.xpPoints > 0 && (
                          <div style={{ fontSize: "0.85rem", color: "#718096", marginTop: "2px" }}>
                            {task.event.xpPoints} XP{isRecurring ? " · 🔁" : ""}
                          </div>
                        )}
                      </div>
                      <button
                        type="button"
                        onClick={() => { setMenuTaskId(isMenuOpen ? null : task.event.id); setDeleteConfirmId(null); }}
                        style={{
                          background: "none",
                          border: "none",
                          cursor: "pointer",
                          fontSize: "1.2rem",
                          color: "#718096",
                          padding: "4px 8px",
                          borderRadius: "8px",
                          flexShrink: 0,
                        }}
                      >
                        ⋯
                      </button>
                    </div>

                    {/* Action menu */}
                    {isMenuOpen && !isDeleteConfirm && !isEditing && (
                      <div style={{ background: "#f8fafc", borderTop: `1px solid ${borderColor}`, display: "flex", gap: "8px", padding: "10px 12px" }}>
                        <button
                          type="button"
                          onClick={() => openEditTask(task)}
                          style={{
                            flex: 1, padding: "8px", background: "#E0F2FE", color: "#0C4A6E",
                            border: "none", borderRadius: "8px", fontWeight: 600, fontSize: "0.85rem", cursor: "pointer",
                          }}
                        >
                          ✏️ Ändra
                        </button>
                        <button
                          type="button"
                          onClick={() => { setDeleteConfirmId(task.event.id); setMenuTaskId(null); }}
                          style={{
                            flex: 1, padding: "8px", background: "#FEE2E2", color: "#991B1B",
                            border: "none", borderRadius: "8px", fontWeight: 600, fontSize: "0.85rem", cursor: "pointer",
                          }}
                        >
                          🗑️ Ta bort
                        </button>
                      </div>
                    )}

                    {/* Delete confirm */}
                    {isDeleteConfirm && (
                      <div style={{ background: "#FEF2F2", borderTop: `1px solid #FCA5A5`, padding: "12px" }}>
                        <p style={{ margin: "0 0 10px", fontSize: "0.9rem", color: "#991B1B", fontWeight: 600 }}>
                          {isRecurring ? "Ta bort bara idag eller alla framtida?" : "Ta bort uppgiften?"}
                        </p>
                        <div style={{ display: "flex", gap: "8px" }}>
                          {isRecurring ? (
                            <>
                              <button
                                type="button"
                                onClick={() => void handleDeleteTask("THIS")}
                                disabled={deletingTask}
                                style={{ flex: 1, padding: "8px", background: deletingTask ? "#cbd5e0" : "#DC2626", color: "white", border: "none", borderRadius: "8px", fontWeight: 600, fontSize: "0.85rem", cursor: deletingTask ? "not-allowed" : "pointer" }}
                              >
                                Bara idag
                              </button>
                              <button
                                type="button"
                                onClick={() => void handleDeleteTask("ALL")}
                                disabled={deletingTask}
                                style={{ flex: 1, padding: "8px", background: deletingTask ? "#cbd5e0" : "#7F1D1D", color: "white", border: "none", borderRadius: "8px", fontWeight: 600, fontSize: "0.85rem", cursor: deletingTask ? "not-allowed" : "pointer" }}
                              >
                                Alla framtida
                              </button>
                            </>
                          ) : (
                            <button
                              type="button"
                              onClick={() => void handleDeleteTask("THIS")}
                              disabled={deletingTask}
                              style={{ flex: 1, padding: "8px", background: deletingTask ? "#cbd5e0" : "#DC2626", color: "white", border: "none", borderRadius: "8px", fontWeight: 600, fontSize: "0.85rem", cursor: deletingTask ? "not-allowed" : "pointer" }}
                            >
                              {deletingTask ? "Tar bort…" : "Ta bort"}
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={() => setDeleteConfirmId(null)}
                            style={{ padding: "8px 14px", background: "transparent", color: "#718096", border: "1px solid #e2e8f0", borderRadius: "8px", fontWeight: 500, fontSize: "0.85rem", cursor: "pointer" }}
                          >
                            Avbryt
                          </button>
                        </div>
                      </div>
                    )}

                    {/* Edit form */}
                    {isEditing && (
                      <div style={{ background: "#EFF6FF", borderTop: `1px solid #BAE6FD`, padding: "14px" }}>
                        <input
                          type="text"
                          value={editTitle}
                          onChange={e => { setEditTitle(e.target.value); setEditError(null); }}
                          onKeyDown={e => e.key === "Enter" && void handleEditTask()}
                          style={{
                            width: "100%", padding: "9px 12px", border: "2px solid #BAE6FD",
                            borderRadius: "10px", fontSize: "1rem", marginBottom: "10px",
                            boxSizing: "border-box", outline: "none",
                          }}
                        />
                        {isRecurring && (
                          <div style={{ marginBottom: "10px" }}>
                            <p style={{ margin: "0 0 6px", fontSize: "0.82rem", color: "#0C4A6E", fontWeight: 600 }}>Ändra</p>
                            <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
                              {(["THIS", "THIS_AND_FOLLOWING", "ALL"] as const).map(s => {
                                const label = s === "THIS" ? "Bara idag" : s === "THIS_AND_FOLLOWING" ? "Från och med idag" : "Alla";
                                return (
                                  <button
                                    key={s}
                                    type="button"
                                    onClick={() => setEditScope(s)}
                                    style={{
                                      padding: "5px 10px", borderRadius: "8px", border: "none",
                                      fontWeight: 600, fontSize: "0.8rem", cursor: "pointer",
                                      background: editScope === s ? "#0C4A6E" : "#BAE6FD",
                                      color: editScope === s ? "white" : "#0C4A6E",
                                    }}
                                  >
                                    {label}
                                  </button>
                                );
                              })}
                            </div>
                          </div>
                        )}
                        <div style={{ display: "flex", gap: "6px", marginBottom: "10px" }}>
                          {[1, 2, 3].map(n => (
                            <button
                              key={n}
                              type="button"
                              onClick={() => setEditXp(n)}
                              style={{
                                padding: "6px 16px", borderRadius: "8px", border: "none",
                                fontWeight: 600, fontSize: "0.85rem", cursor: "pointer",
                                background: editXp === n ? "#0C4A6E" : "#BAE6FD",
                                color: editXp === n ? "white" : "#0C4A6E",
                              }}
                            >
                              x{n}
                            </button>
                          ))}
                        </div>
                        {editError && <p style={{ margin: "0 0 8px", color: "#c53030", fontSize: "0.85rem" }}>{editError}</p>}
                        <div style={{ display: "flex", gap: "8px" }}>
                          <button
                            type="button"
                            onClick={() => void handleEditTask()}
                            disabled={editingTask}
                            style={{ flex: 1, padding: "9px", background: editingTask ? "#cbd5e0" : "#0C4A6E", color: "white", border: "none", borderRadius: "10px", fontWeight: 600, fontSize: "0.9rem", cursor: editingTask ? "not-allowed" : "pointer" }}
                          >
                            {editingTask ? "Sparar…" : "Spara"}
                          </button>
                          <button
                            type="button"
                            onClick={() => { setEditTaskId(null); setEditError(null); }}
                            style={{ padding: "9px 16px", background: "transparent", color: "#0C4A6E", border: "2px solid #BAE6FD", borderRadius: "10px", fontWeight: 500, fontSize: "0.9rem", cursor: "pointer" }}
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

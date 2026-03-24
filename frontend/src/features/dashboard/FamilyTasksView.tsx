import { useState, useEffect } from "react";
import { fetchAllFamilyMembers, FamilyMemberResponse } from "../../shared/api/familyMembers";
import { getDailyChoresForDate, markDailyChoreCompleted, unmarkDailyChoreCompleted, DailyChoreWithCompletionResponse } from "../../shared/api/dailyChores";
import { fetchMemberPet, PetResponse } from "../../shared/api/pets";
import { getPetGradient, isDarkPetTheme } from "../pet/petTheme";

type ViewKey = "dashboard" | "familytasks";

type FamilyTasksViewProps = {
  onNavigate?: (view: ViewKey) => void;
};

type MemberWithTasks = {
  member: FamilyMemberResponse;
  chores: DailyChoreWithCompletionResponse[];
  pet: PetResponse | null;
};

function getTodayString(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export function FamilyTasksView({ onNavigate }: FamilyTasksViewProps) {
  const [data, setData] = useState<MemberWithTasks[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    const load = async () => {
      try {
        setLoading(true);
        const members = await fetchAllFamilyMembers();
        const results = await Promise.all(
          members.map(async (member) => {
            const today = getTodayString();
            const [chores, pet] = await Promise.all([
              getDailyChoresForDate(member.id, today).catch(() => [] as DailyChoreWithCompletionResponse[]),
              fetchMemberPet(member.id).catch(() => null),
            ]);
            return { member, chores, pet };
          })
        );
        if (!ignore) setData(results);
      } catch (e) {
        if (!ignore) setError("Kunde inte ladda uppgifter.");
      } finally {
        if (!ignore) setLoading(false);
      }
    };
    void load();
    return () => { ignore = true; };
  }, []);

  const handleChoreToggle = async (memberId: string, choreId: string, completed: boolean) => {
    const key = `chore-${memberId}-${choreId}`;
    setTogglingId(key);
    const today = getTodayString();
    try {
      if (completed) {
        await unmarkDailyChoreCompleted(choreId, today);
      } else {
        await markDailyChoreCompleted(choreId, today);
      }
      setData(prev =>
        prev.map(row =>
          row.member.id !== memberId
            ? row
            : {
                ...row,
                chores: row.chores.map(c =>
                  c.chore.id === choreId ? { ...c, completed: !completed } : c
                ),
              }
        )
      );
    } catch {
      // silently ignore — state stays as-is
    } finally {
      setTogglingId(null);
    }
  };

  const today = new Date();
  const dayNames = ["söndag", "måndag", "tisdag", "onsdag", "torsdag", "fredag", "lördag"];
  const dateLabel = `${dayNames[today.getDay()]} ${today.getDate()}/${today.getMonth() + 1}`;

  return (
    <div style={{ padding: "16px", maxWidth: "480px", margin: "0 auto" }}>
      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "20px" }}>
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
        <div>
          <h2 className="view-title" style={{ margin: 0 }}>Uppgifter idag</h2>
          <p style={{ margin: 0, fontSize: "0.82rem", color: "#666" }}>{dateLabel}</p>
        </div>
      </div>

      {error && <p className="error-text">{error}</p>}

      {loading && (
        <section className="card">
          <p style={{ margin: 0, color: "#666" }}>Laddar...</p>
        </section>
      )}

      {!loading && data.length === 0 && (
        <section className="card">
          <p style={{ margin: 0, color: "#666" }}>Inga familjemedlemmar hittades.</p>
        </section>
      )}

      {!loading && data.map(({ member, chores, pet }) => {
        const done = chores.filter(c => c.completed).length;
        const total = chores.length;
        const petType = pet?.petType ?? null;
        const gradient = petType ? getPetGradient(petType) : null;
        const dark = petType ? isDarkPetTheme(petType) : false;
        const textColor = dark ? "rgba(255,255,255,0.95)" : "#1a1a1a";
        const subTextColor = dark ? "rgba(255,255,255,0.7)" : "#555";
        const cardBg = gradient
          ? `linear-gradient(135deg, ${gradient.from} 0%, ${gradient.to} 100%)`
          : "rgba(255,255,255,0.95)";

        // check/circle colors adapted for dark vs light theme
        const circleBorder = dark ? "rgba(255,255,255,0.45)" : "#ccc";
        const circleDone = dark ? "rgba(255,255,255,0.9)" : "#4ade80";
        const checkColor = dark ? gradient?.to ?? "#222" : "white";

        return (
          <section
            key={member.id}
            style={{
              marginBottom: "16px",
              borderRadius: "18px",
              overflow: "hidden",
              boxShadow: "0 4px 14px rgba(0,0,0,0.10)",
              background: cardBg,
            }}
          >
            {/* Card header */}
            <div style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              padding: "16px 16px 12px",
              gap: "12px",
            }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                  <span style={{ fontWeight: 700, fontSize: "1.05rem", color: textColor }}>
                    {member.name}
                  </span>
                </div>
                <span style={{
                  display: "inline-block",
                  marginTop: "4px",
                  fontSize: "0.78rem",
                  fontWeight: 600,
                  color: total === 0 ? subTextColor : done === total ? (dark ? "rgba(255,255,255,0.9)" : "#2d7a2d") : subTextColor,
                  background: dark ? "rgba(0,0,0,0.2)" : "rgba(0,0,0,0.07)",
                  borderRadius: "10px",
                  padding: "2px 9px",
                }}>
                  {total === 0 ? "Inga uppgifter idag" : done === total ? `✓ Allt klart (${total})` : `${done} / ${total} gjorda`}
                </span>
              </div>

            </div>

            {/* Task list */}
            {chores.length > 0 && (
              <div style={{
                background: dark ? "rgba(0,0,0,0.18)" : "rgba(255,255,255,0.55)",
                borderTop: dark ? "1px solid rgba(255,255,255,0.1)" : "1px solid rgba(0,0,0,0.06)",
                padding: "8px 14px 12px",
              }}>
                <ul style={{ margin: 0, padding: 0, listStyle: "none", display: "flex", flexDirection: "column", gap: "2px" }}>
                  {chores.map(({ chore, completed }) => {
                    const key = `chore-${member.id}-${chore.id}`;
                    const isToggling = togglingId === key;
                    return (
                      <li key={`chore-${chore.id}`}>
                        <button
                          type="button"
                          onClick={() => void handleChoreToggle(member.id, chore.id, completed)}
                          disabled={isToggling}
                          style={{
                            width: "100%",
                            display: "flex",
                            alignItems: "center",
                            gap: "10px",
                            background: "none",
                            border: "none",
                            padding: "7px 2px",
                            cursor: isToggling ? "default" : "pointer",
                            textAlign: "left",
                            opacity: isToggling ? 0.5 : 1,
                          }}
                          aria-label={`${completed ? "Markera som ej gjord" : "Markera som gjord"}: ${chore.title}`}
                        >
                          <span style={{
                            width: "22px",
                            height: "22px",
                            borderRadius: "50%",
                            border: completed ? "none" : `2px solid ${circleBorder}`,
                            background: completed ? circleDone : "transparent",
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            flexShrink: 0,
                            fontSize: "0.75rem",
                            color: checkColor,
                            fontWeight: 700,
                            transition: "background 0.15s",
                          }}>
                            {completed ? "✓" : ""}
                          </span>
                          <span style={{
                            fontSize: "0.92rem",
                            color: completed ? subTextColor : textColor,
                            textDecoration: completed ? "line-through" : "none",
                            flex: 1,
                          }}>
                            {chore.title}
                          </span>
                          {chore.xpPoints > 0 && (
                            <span style={{ fontSize: "0.75rem", color: subTextColor, flexShrink: 0 }}>
                              {chore.xpPoints} XP
                            </span>
                          )}
                        </button>
                      </li>
                    );
                  })}
                </ul>
              </div>
            )}
          </section>
        );
      })}
    </div>
  );
}

import { useEffect, useState } from "react";
import {
  getMenstrualCycleEntries,
  createMenstrualCycleEntry,
  deleteMenstrualCycleEntry,
  getCyclePrediction,
  MenstrualCycleEntry,
  CyclePrediction,
} from "../../shared/api/menstrualCycle";
import { getMemberByDeviceToken, FamilyMemberResponse } from "../../shared/api/familyMembers";
import { MenstrualCycleCalendar } from "./MenstrualCycleCalendar";
import { parseLocalDate, formatLocalDate } from "../../shared/utils/dateUtils";

type MenstrualCycleViewProps = {
  memberId?: string;
  onNavigate?: (view: string) => void;
};

export function MenstrualCycleView({ memberId, onNavigate }: MenstrualCycleViewProps) {
  const [currentMember, setCurrentMember] = useState<FamilyMemberResponse | null>(null);
  const [entries, setEntries] = useState<MenstrualCycleEntry[]>([]);
  const [prediction, setPrediction] = useState<CyclePrediction | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [calendarMonth, setCalendarMonth] = useState(new Date());
  const [historyExpanded, setHistoryExpanded] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [showInstructions, setShowInstructions] = useState(() => {
    const hasSeenInstructions = localStorage.getItem('menstrualCycleInstructionsSeen');
    return !hasSeenInstructions;
  });

  // Load current user
  useEffect(() => {
    const loadCurrentMember = async () => {
      try {
        const deviceToken = localStorage.getItem("deviceToken");
        if (deviceToken) {
          const member = await getMemberByDeviceToken(deviceToken);
          setCurrentMember(member);
        }
      } catch (e) {
        // Silently fail - user might not be logged in yet
        // Error will be handled by the component's error state
      }
    };
    void loadCurrentMember();
  }, []);

  // Use provided memberId or current user's memberId
  const targetMemberId = memberId || currentMember?.id;

  useEffect(() => {
    if (targetMemberId) {
      void loadData();
    }
  }, [targetMemberId]);

  const loadData = async () => {
    if (!targetMemberId) return;
    
    try {
      setLoading(true);
      setError(null);
      const [entriesData, predictionData] = await Promise.all([
        getMenstrualCycleEntries(targetMemberId),
        getCyclePrediction(targetMemberId),
      ]);
      setEntries(entriesData);
      setPrediction(predictionData);
    } catch (e) {
      const errorMessage = e instanceof Error ? e.message : "Kunde inte ladda menscykel-data.";
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteEntry = async (entryId: string) => {
    if (!targetMemberId) return;
    
    if (!confirm("Är du säker på att du vill ta bort denna mensperiod?")) {
      return;
    }

    try {
      await deleteMenstrualCycleEntry(targetMemberId, entryId);
      await loadData();
      setError(null);
    } catch (e) {
      const errorMessage = e instanceof Error ? e.message : "Kunde inte ta bort mensperiod.";
      setError(errorMessage);
    }
  };

  type CyclePhase = "menstruation" | "follicular" | "ovulation" | "luteal";

  const getPhaseLabel = (phase: CyclePhase | string): string => {
    switch (phase) {
      case "menstruation":
        return "Mens";
      case "follicular":
        return "Follikulärfas";
      case "ovulation":
        return "Ägglossning";
      case "luteal":
        return "Lutealfas";
      default:
        return phase;
    }
  };

  const getPhaseIcon = (phase: CyclePhase | string): string => {
    switch (phase) {
      case "menstruation":
        return "🩸";
      case "follicular":
        return "🌱";
      case "ovulation":
        return "✨";
      case "luteal":
        return "🌙";
      default:
        return "●";
    }
  };

  const getPhaseColor = (phase: CyclePhase | string): string => {
    switch (phase) {
      case "menstruation":
        return "#dc2626";
      case "follicular":
        return "#10b981";
      case "ovulation":
        return "#f59e0b";
      case "luteal":
        return "#8b5cf6";
      default:
        return "#6b6b6b";
    }
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString("sv-SE", {
      year: "numeric",
      month: "long",
      day: "numeric",
    });
  };

  // Show loading while fetching current member
  if (!targetMemberId && currentMember === null) {
    return (
      <div className="menstrual-cycle-view">
        <p>Laddar...</p>
      </div>
    );
  }

  if (!targetMemberId) {
    return (
      <div className="menstrual-cycle-view">
        <p className="error-text">Ingen användare vald.</p>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="menstrual-cycle-view">
        <p>Laddar...</p>
      </div>
    );
  }

  return (
    <div className="menstrual-cycle-view" style={{ maxWidth: "800px", margin: "0 auto", padding: "0 4px" }}>
      {/* Header */}
      <div style={{ 
        display: "flex", 
        alignItems: "center", 
        gap: "12px", 
        marginBottom: "24px",
        paddingBottom: "16px",
        borderBottom: "2px solid rgba(220, 210, 200, 0.3)"
      }}>
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
          <h2 className="view-title" style={{ margin: 0, fontSize: "1.5rem", fontWeight: 600 }}>
            Menscykel
          </h2>
          <p style={{ margin: "4px 0 0 0", fontSize: "0.9rem", color: "#6b6b6b" }}>
            Spåra din cykel och förutsäg kommande perioder
          </p>
        </div>
      </div>

      {error && (
        <div style={{
          padding: "12px 16px",
          background: "#fee2e2",
          border: "1px solid #fecaca",
          borderRadius: "10px",
          color: "#991b1b",
          marginBottom: "20px",
          fontSize: "0.9rem"
        }}>
          {error}
        </div>
      )}

      {/* Calendar View */}
      <section className="card" style={{ marginBottom: "24px", padding: "20px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "16px" }}>
          <h3 style={{ margin: 0, fontSize: "1.2rem", fontWeight: 600 }}>Kalender</h3>
        </div>
        {showInstructions && (
          <div style={{
            marginBottom: "16px",
            padding: "16px",
            background: "linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%)",
            borderRadius: "12px",
            border: "1px solid rgba(59, 130, 246, 0.2)",
            position: "relative"
          }}>
            <button
              type="button"
              onClick={() => {
                setShowInstructions(false);
                localStorage.setItem('menstrualCycleInstructionsSeen', 'true');
              }}
              style={{
                position: "absolute",
                top: "8px",
                right: "8px",
                background: "transparent",
                border: "none",
                fontSize: "1.2rem",
                cursor: "pointer",
                color: "#6b6b6b",
                padding: "4px 8px",
                borderRadius: "4px",
                transition: "all 0.2s ease"
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = "rgba(59, 130, 246, 0.1)";
                e.currentTarget.style.color = "#1e40af";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = "transparent";
                e.currentTarget.style.color = "#6b6b6b";
              }}
              aria-label="Stäng"
            >
              ×
            </button>
            <div style={{ display: "flex", alignItems: "flex-start", gap: "12px" }}>
              <span style={{ fontSize: "1.5rem", flexShrink: 0 }}>💡</span>
              <div style={{ flex: 1, paddingRight: "32px" }}>
                <p style={{ 
                  margin: "0 0 8px 0", 
                  fontSize: "0.95rem", 
                  color: "#1e40af",
                  fontWeight: 600
                }}>
                  Så här fungerar det:
                </p>
                <p style={{ 
                  margin: 0, 
                  fontSize: "0.9rem", 
                  color: "#1e3a8a",
                  lineHeight: 1.6
                }}>
                  Klicka eller dra över dagar i kalendern för att markera mensperiod. När du är klar, klicka på "Spara ändringar" för att spara dina ändringar.
                </p>
              </div>
            </div>
          </div>
        )}
        <MenstrualCycleCalendar 
          entries={entries} 
          prediction={prediction}
          currentMonth={calendarMonth}
          onMonthChange={setCalendarMonth}
          onSave={async (selectedDays) => {
            if (!targetMemberId || isSaving) return;
            
            setIsSaving(true);
            setError(null);
            
            try {
              // Calculate which entries to keep, delete, and create
              const currentEntryDays = new Map<string, { entryId: string; startDate: string; length: number }>();
              entries.forEach(entry => {
                const start = parseLocalDate(entry.periodStartDate);
                const length = entry.periodLength || 5;
                for (let i = 0; i < length; i++) {
                  const date = new Date(start);
                  date.setDate(date.getDate() + i);
                  const key = formatLocalDate(date);
                  if (!currentEntryDays.has(key)) {
                    currentEntryDays.set(key, { entryId: entry.id, startDate: entry.periodStartDate, length });
                  }
                }
              });

              // Find entries to delete (days that were saved but are now unselected)
              const entriesToDelete = new Set<string>();
              currentEntryDays.forEach((entryInfo, dateKey) => {
                if (!selectedDays.has(dateKey)) {
                  entriesToDelete.add(entryInfo.entryId);
                }
              });

              // Find days that need to be added (selected but not in any entry)
              const daysToAdd = new Set<string>();
              selectedDays.forEach(dateKey => {
                if (!currentEntryDays.has(dateKey)) {
                  daysToAdd.add(dateKey);
                }
              });

              // Delete entries that are no longer selected
              const deletePromises = Array.from(entriesToDelete).map(entryId =>
                deleteMenstrualCycleEntry(targetMemberId, entryId).catch(err => {
                  console.error(`Failed to delete entry ${entryId}:`, err);
                  throw err;
                })
              );
              await Promise.all(deletePromises);

              // Group consecutive days into entries and create them
              if (daysToAdd.size > 0) {
                const sortedDates = Array.from(daysToAdd)
                  .map(key => parseLocalDate(key))
                  .sort((a, b) => a.getTime() - b.getTime());

                // Group consecutive days into entries
                let currentStart = sortedDates[0];
                let currentEnd = sortedDates[0];

                for (let i = 1; i < sortedDates.length; i++) {
                  const prevDate = new Date(sortedDates[i - 1]);
                  prevDate.setDate(prevDate.getDate() + 1);
                  prevDate.setHours(0, 0, 0, 0);
                  const currentDate = sortedDates[i];
                  currentDate.setHours(0, 0, 0, 0);

                  // Check if dates are consecutive
                  if (prevDate.getTime() === currentDate.getTime()) {
                    // Consecutive, extend current range
                    currentEnd = currentDate;
                  } else {
                    // Not consecutive, save current range and start new
                    const periodLength = Math.ceil((currentEnd.getTime() - currentStart.getTime()) / (1000 * 60 * 60 * 24)) + 1;
                    await createMenstrualCycleEntry(
                      targetMemberId,
                      formatLocalDate(currentStart),
                      periodLength
                    ).catch(err => {
                      console.error(`Failed to create entry for ${formatLocalDate(currentStart)}:`, err);
                      throw err;
                    });
                    currentStart = currentDate;
                    currentEnd = currentDate;
                  }
                }

                // Save last range
                const periodLength = Math.ceil((currentEnd.getTime() - currentStart.getTime()) / (1000 * 60 * 60 * 24)) + 1;
                await createMenstrualCycleEntry(
                  targetMemberId,
                  formatLocalDate(currentStart),
                  periodLength
                ).catch(err => {
                  console.error(`Failed to create entry for ${formatLocalDate(currentStart)}:`, err);
                  throw err;
                });
              }

              // Reload data to reflect changes
              await loadData();
              setError(null);
            } catch (e) {
              const errorMessage = e instanceof Error ? e.message : "Kunde inte uppdatera mensperioder.";
              setError(errorMessage);
              // Reload on error to get correct state
              try {
                await loadData();
              } catch (reloadError) {
                console.error("Failed to reload data after error:", reloadError);
              }
            } finally {
              setIsSaving(false);
            }
          }}
          isSaving={isSaving}
        />
      </section>

      {/* Prediction Overview - Stats Cards */}
      {prediction && (
        <section className="card" style={{ marginBottom: "24px", padding: "20px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "20px" }}>
            <span style={{ fontSize: "1.3rem" }}>📊</span>
            <h3 style={{ margin: 0, fontSize: "1.2rem", fontWeight: 600 }}>Översikt</h3>
          </div>
          <div style={{ 
            display: "grid", 
            gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))",
            gap: "16px" 
          }}>
            {/* Current Cycle Day */}
            <div style={{
              background: "linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%)",
              padding: "16px",
              borderRadius: "12px",
              border: "1px solid rgba(59, 130, 246, 0.2)",
              textAlign: "center"
            }}>
              <p style={{ margin: 0, fontSize: "0.85rem", color: "#1e40af", fontWeight: 500, marginBottom: "8px" }}>
                Cykeldag
              </p>
              <p style={{ margin: 0, fontSize: "2rem", fontWeight: 700, color: "#1e40af" }}>
                {prediction.currentCycleDay}
              </p>
            </div>

            {/* Current Phase */}
            <div style={{
              background: `linear-gradient(135deg, ${getPhaseColor(prediction.currentPhase)}15 0%, ${getPhaseColor(prediction.currentPhase)}25 100%)`,
              padding: "16px",
              borderRadius: "12px",
              border: `1px solid ${getPhaseColor(prediction.currentPhase)}40`,
              textAlign: "center"
            }}>
              <p style={{ margin: 0, fontSize: "0.85rem", color: getPhaseColor(prediction.currentPhase), fontWeight: 500, marginBottom: "8px" }}>
                Nuvarande fas
              </p>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: "6px" }}>
                <span style={{ fontSize: "1.5rem" }}>{getPhaseIcon(prediction.currentPhase)}</span>
                <p style={{ margin: 0, fontSize: "1.1rem", fontWeight: 600, color: getPhaseColor(prediction.currentPhase) }}>
                  {getPhaseLabel(prediction.currentPhase)}
                </p>
              </div>
            </div>

            {/* Next Period */}
            <div style={{
              background: "linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%)",
              padding: "16px",
              borderRadius: "12px",
              border: "1px solid rgba(220, 38, 38, 0.2)",
              textAlign: "center"
            }}>
              <p style={{ margin: 0, fontSize: "0.85rem", color: "#991b1b", fontWeight: 500, marginBottom: "8px" }}>
                Nästa mens
              </p>
              <p style={{ margin: 0, fontSize: "0.95rem", fontWeight: 600, color: "#991b1b", lineHeight: 1.3 }}>
                {formatDate(prediction.nextPeriodStart)}
              </p>
            </div>

            {/* Ovulation */}
            <div style={{
              background: "linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%)",
              padding: "16px",
              borderRadius: "12px",
              border: "1px solid rgba(245, 158, 11, 0.2)",
              textAlign: "center"
            }}>
              <p style={{ margin: 0, fontSize: "0.85rem", color: "#92400e", fontWeight: 500, marginBottom: "8px" }}>
                Ägglossning
              </p>
              <p style={{ margin: 0, fontSize: "0.95rem", fontWeight: 600, color: "#92400e", lineHeight: 1.3 }}>
                {formatDate(prediction.ovulationDate)}
              </p>
            </div>
          </div>

          {/* Fertile Window */}
          <div style={{
            marginTop: "20px",
            padding: "16px",
            background: "linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%)",
            borderRadius: "12px",
            border: "1px solid rgba(16, 185, 129, 0.2)"
          }}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
              <span style={{ fontSize: "1.2rem" }}>🌱</span>
              <p style={{ margin: 0, fontSize: "0.9rem", fontWeight: 600, color: "#065f46" }}>
                Fertilt fönster
              </p>
            </div>
            <p style={{ margin: 0, fontSize: "1rem", color: "#047857", fontWeight: 500 }}>
              {formatDate(prediction.fertileWindowStart)} - {formatDate(prediction.fertileWindowEnd)}
            </p>
          </div>
        </section>
      )}

      {/* History */}
      <section className="card" style={{ padding: "20px" }}>
        <div style={{ 
          display: "flex", 
          alignItems: "center", 
          justifyContent: "space-between",
          marginBottom: historyExpanded ? "20px" : "0",
          cursor: "pointer"
        }}
        onClick={() => setHistoryExpanded(!historyExpanded)}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <span style={{ fontSize: "1.3rem" }}>📋</span>
            <h3 style={{ margin: 0, fontSize: "1.2rem", fontWeight: 600 }}>Historik</h3>
            {entries.length > 0 && (
              <span style={{ 
                fontSize: "0.85rem", 
                color: "#6b6b6b",
                fontWeight: 500
              }}>
                ({entries.length})
              </span>
            )}
          </div>
          <span style={{ 
            fontSize: "1.2rem",
            color: "#6b6b6b",
            transition: "transform 0.2s ease",
            transform: historyExpanded ? "rotate(180deg)" : "rotate(0deg)"
          }}>
            ▼
          </span>
        </div>
        {historyExpanded && (
          <>
            {entries.length === 0 ? (
              <div style={{
                padding: "40px 20px",
                textAlign: "center",
                background: "linear-gradient(135deg, #f9fafb 0%, #f3f4f6 100%)",
                borderRadius: "12px",
                border: "1px dashed rgba(220, 210, 200, 0.5)"
              }}>
                <p style={{ margin: 0, fontSize: "1rem", color: "#6b6b6b", lineHeight: 1.6 }}>
                  Inga mensperioder registrerade än.<br />
                  Markera dagar i kalendern ovan för att börja spåra din cykel!
                </p>
              </div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
                {entries.map((entry, index) => (
              <div
                key={entry.id}
                style={{
                  padding: "16px",
                  background: index === 0 
                    ? "linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%)"
                    : "linear-gradient(135deg, #ffffff 0%, #fef9f5 100%)",
                  borderRadius: "12px",
                  border: index === 0 
                    ? "1px solid rgba(220, 38, 38, 0.2)"
                    : "1px solid rgba(220, 210, 200, 0.3)",
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  transition: "all 0.2s ease",
                  boxShadow: index === 0 
                    ? "0 2px 8px rgba(220, 38, 38, 0.1)"
                    : "0 1px 3px rgba(0, 0, 0, 0.05)"
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.transform = "translateY(-2px)";
                  e.currentTarget.style.boxShadow = "0 4px 12px rgba(0, 0, 0, 0.1)";
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.transform = "translateY(0)";
                  e.currentTarget.style.boxShadow = index === 0 
                    ? "0 2px 8px rgba(220, 38, 38, 0.1)"
                    : "0 1px 3px rgba(0, 0, 0, 0.05)";
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: "12px", flex: 1 }}>
                  <div style={{
                    width: "48px",
                    height: "48px",
                    borderRadius: "12px",
                    background: index === 0 
                      ? "linear-gradient(135deg, #dc2626 0%, #b91c1c 100%)"
                      : "linear-gradient(135deg, #f3f4f6 0%, #e5e7eb 100%)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontSize: "1.5rem",
                    flexShrink: 0
                  }}>
                    {index === 0 ? "🩸" : "📅"}
                  </div>
                  <div style={{ flex: 1 }}>
                    <p style={{ 
                      margin: 0, 
                      fontWeight: 600, 
                      fontSize: "1rem",
                      color: index === 0 ? "#991b1b" : "#3a3a3a",
                      marginBottom: "4px"
                    }}>
                      {formatDate(entry.periodStartDate)}
                      {index === 0 && (
                        <span style={{
                          marginLeft: "8px",
                          fontSize: "0.75rem",
                          background: "#dc2626",
                          color: "white",
                          padding: "2px 8px",
                          borderRadius: "12px",
                          fontWeight: 500
                        }}>
                          Senaste
                        </span>
                      )}
                    </p>
                    <div style={{ display: "flex", gap: "12px", flexWrap: "wrap" }}>
                      <span style={{ fontSize: "0.85rem", color: "#6b6b6b" }}>
                        <strong>Längd:</strong> {entry.periodLength || "?"} dagar
                      </span>
                      {entry.cycleLength && entry.cycleLength > 0 && (
                        <span style={{ fontSize: "0.85rem", color: "#6b6b6b" }}>
                          <strong>Cykellängd:</strong> {entry.cycleLength} dagar
                        </span>
                      )}
                    </div>
                  </div>
                </div>
                <button
                  type="button"
                  className="button-secondary"
                  onClick={() => void handleDeleteEntry(entry.id)}
                  style={{ 
                    padding: "8px 16px", 
                    fontSize: "0.85rem",
                    opacity: 0.7,
                    transition: "all 0.2s ease"
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.opacity = "1";
                    e.currentTarget.style.background = "#fee2e2";
                    e.currentTarget.style.color = "#991b1b";
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.opacity = "0.7";
                  }}
                >
                  Ta bort
                </button>
                </div>
              ))}
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}

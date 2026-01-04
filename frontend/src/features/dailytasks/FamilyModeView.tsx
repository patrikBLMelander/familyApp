import { useEffect, useState, useRef, useCallback } from "react";
import {
  fetchTasksForToday,
  toggleTaskCompletion,
  DailyTaskWithCompletionResponse
} from "../../shared/api/dailyTasks";
import { fetchAllFamilyMembers, FamilyMemberResponse } from "../../shared/api/familyMembers";

type FamilyModeViewProps = {
  onToggle?: () => void;
};

type MemberTasks = {
  member: FamilyMemberResponse;
  tasks: DailyTaskWithCompletionResponse[];
  allCompleted: boolean;
};

export function FamilyModeView({ onToggle }: FamilyModeViewProps) {
  const [members, setMembers] = useState<FamilyMemberResponse[]>([]);
  const [memberTasks, setMemberTasks] = useState<Map<string, MemberTasks>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [celebrations, setCelebrations] = useState<Map<string, boolean>>(new Map());
  const [isLoading, setIsLoading] = useState(false);
  const [togglingTasks, setTogglingTasks] = useState<Set<string>>(new Set());
  const isLoadingRef = useRef(false);
  const togglingTasksRef = useRef<Set<string>>(new Set());

  // Keep refs in sync with state
  useEffect(() => {
    isLoadingRef.current = isLoading;
  }, [isLoading]);

  useEffect(() => {
    togglingTasksRef.current = togglingTasks;
  }, [togglingTasks]);

  const loadData = useCallback(async () => {
    if (isLoading) return; // Prevent concurrent loads
    
    try {
      setIsLoading(true);
      setLoading(true);
      const membersData = await fetchAllFamilyMembers();
      
      setMembers(membersData);

      // Load tasks for each member
      const tasksMap = new Map<string, MemberTasks>();
      
      for (const member of membersData) {
        try {
          const tasks = await fetchTasksForToday(member.id);
          const allCompleted = tasks.length > 0 && tasks.every(t => t.completed);
          
          tasksMap.set(member.id, {
            member,
            tasks,
            allCompleted
          });

          // Show celebration if all tasks are completed and we haven't shown it yet
          // Use functional update to avoid dependency on celebrations state
          setCelebrations(prev => {
            const newMap = new Map(prev);
            if (allCompleted && tasks.length > 0 && !newMap.get(member.id)) {
              newMap.set(member.id, true);
              // Hide celebration after 6 seconds
              setTimeout(() => {
                setCelebrations(prevCeleb => {
                  const newCelebMap = new Map(prevCeleb);
                  newCelebMap.delete(member.id);
                  return newCelebMap;
                });
              }, 6000);
            } else if (!allCompleted) {
              // Reset celebration state if tasks are unchecked
              newMap.delete(member.id);
            }
            return newMap;
          });
        } catch (e) {
          console.error(`Error loading tasks for ${member.name}:`, e);
        }
      }

      setMemberTasks(tasksMap);
    } catch (e) {
      console.error("Error loading data:", e);
      setError("Kunde inte ladda data.");
    } finally {
      setLoading(false);
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
    // Refresh every 60 seconds to keep data up to date (increased from 30 to reduce unnecessary reloads)
    const interval = setInterval(() => {
      // Only refresh if not currently loading and no toggles in progress
      if (!isLoadingRef.current && togglingTasksRef.current.size === 0) {
        void loadData();
      }
    }, 60000);
    return () => clearInterval(interval);
  }, [loadData]);

  const handleToggle = async (taskId: string, memberId: string) => {
    const toggleKey = `${taskId}-${memberId}`;
    
    // Prevent double-clicks/touches
    if (togglingTasks.has(toggleKey)) {
      return;
    }

    try {
      setTogglingTasks(prev => new Set(prev).add(toggleKey));
      
      // Optimistic update
      setMemberTasks(prev => {
        const newMap = new Map(prev);
        const memberData = newMap.get(memberId);
        if (memberData) {
          const updatedTasks = memberData.tasks.map(t => 
            t.task.id === taskId 
              ? { ...t, completed: !t.completed }
              : t
          );
          const allCompleted = updatedTasks.length > 0 && updatedTasks.every(t => t.completed);
          newMap.set(memberId, {
            ...memberData,
            tasks: updatedTasks,
            allCompleted
          });
        }
        return newMap;
      });

      await toggleTaskCompletion(taskId, memberId);
      
      // Reload only this member's data instead of all data
      try {
        const tasks = await fetchTasksForToday(memberId);
        const allCompleted = tasks.length > 0 && tasks.every(t => t.completed);
        
        setMemberTasks(prev => {
          const newMap = new Map(prev);
          const memberData = newMap.get(memberId);
          if (memberData) {
            newMap.set(memberId, {
              ...memberData,
              tasks,
              allCompleted
            });
          }
          return newMap;
        });

        // Show celebration if all tasks are completed
        if (allCompleted && tasks.length > 0 && !celebrations.get(memberId)) {
          setCelebrations(prev => new Map(prev).set(memberId, true));
          setTimeout(() => {
            setCelebrations(prev => {
              const newMap = new Map(prev);
              newMap.delete(memberId);
              return newMap;
            });
          }, 6000);
        } else if (!allCompleted) {
          setCelebrations(prev => {
            const newMap = new Map(prev);
            newMap.delete(memberId);
            return newMap;
          });
        }
      } catch (e) {
        console.error(`Error reloading tasks for member ${memberId}:`, e);
        // Fallback to full reload on error
        await loadData();
      }
    } catch (e) {
      console.error("Error toggling task:", e);
      setError("Kunde inte uppdatera syssla.");
      // Revert optimistic update on error
      await loadData();
    } finally {
      setTogglingTasks(prev => {
        const newSet = new Set(prev);
        newSet.delete(toggleKey);
        return newSet;
      });
    }
  };

  const today = new Date();
  const dayNames = ["Söndag", "Måndag", "Tisdag", "Onsdag", "Torsdag", "Fredag", "Lördag"];
  const todayName = dayNames[today.getDay()];

  // Separate members by role
  const parents = members.filter(m => m.role === "PARENT");
  const children = members.filter(m => m.role === "CHILD");

  return (
    <div className="family-mode-view">
      <div className="family-mode-header">
        <div style={{ flex: 1 }}>
          <h2 className="view-title" style={{ margin: 0, marginBottom: "4px" }}>Familjeläge</h2>
          <p className="daily-tasks-date" style={{ margin: 0 }}>{todayName} {today.toLocaleDateString('sv-SE')}</p>
        </div>
        {onToggle && (
          <button
            type="button"
            className="button-secondary"
            onClick={onToggle}
            style={{ fontSize: "0.9rem", padding: "8px 16px" }}
          >
            Stäng familjeläge
          </button>
        )}
      </div>

      {error && <p className="error-text">{error}</p>}

      {loading && (
        <section className="card">
          <p>Laddar...</p>
        </section>
      )}

      {!loading && (
        <>
          {/* Parents */}
          {parents.length > 0 && (
            <section className="card">
              <h3 style={{ marginTop: 0, marginBottom: "16px", fontSize: "1.1rem", fontWeight: 600 }}>
                Föräldrar
              </h3>
              <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
                {parents.map((parent) => {
                  const memberData = memberTasks.get(parent.id);
                  const tasks = memberData?.tasks || [];
                  const allCompleted = memberData?.allCompleted || false;
                  const showCelebration = celebrations.get(parent.id);

                  return (
                    <div key={parent.id} style={{ 
                      padding: "16px",
                      background: allCompleted ? "rgba(184, 230, 184, 0.1)" : "rgba(240, 240, 240, 0.3)",
                      borderRadius: "12px",
                      border: allCompleted ? "2px solid rgba(184, 230, 184, 0.5)" : "1px solid rgba(220, 210, 200, 0.3)"
                    }}>
                      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "12px" }}>
                        <h4 style={{ margin: 0, fontSize: "1.1rem", fontWeight: 600 }}>
                          {parent.name}
                        </h4>
                        {allCompleted && tasks.length > 0 && (
                          <span style={{ 
                            fontSize: "0.85rem", 
                            color: "#2d5a2d",
                            fontWeight: 500,
                            padding: "4px 12px",
                            background: "rgba(184, 230, 184, 0.3)",
                            borderRadius: "12px"
                          }}>
                            ✓ Alla klara
                          </span>
                        )}
                      </div>

                      {showCelebration && (
                        <div style={{
                          marginBottom: "12px",
                          padding: "12px",
                          background: "rgba(184, 230, 184, 0.2)",
                          borderRadius: "8px",
                          textAlign: "center"
                        }}>
                          <div style={{ fontSize: "1.5rem", marginBottom: "4px" }}>🎉</div>
                          <p style={{ margin: 0, fontWeight: 600, color: "#2d5a2d" }}>
                            Snyggt jobbat!
                          </p>
                        </div>
                      )}

                      {tasks.length === 0 ? (
                        <p style={{ margin: 0, color: "#6b6b6b", fontSize: "0.9rem" }}>
                          Inga sysslor för idag
                        </p>
                      ) : (
                        <ul className="daily-tasks-list" style={{ margin: 0 }}>
                          {tasks.map((taskWithCompletion) => {
                            const toggleKey = `${taskWithCompletion.task.id}-${parent.id}`;
                            const isToggling = togglingTasks.has(toggleKey);
                            
                            return (
                              <li key={`${parent.id}-${taskWithCompletion.task.id}`} className="daily-task-item">
                                <label className="daily-task-label">
                                  <input
                                    type="checkbox"
                                    checked={taskWithCompletion.completed}
                                    disabled={isToggling}
                                    onChange={(e) => {
                                      e.preventDefault();
                                      e.stopPropagation();
                                      void handleToggle(taskWithCompletion.task.id, parent.id);
                                    }}
                                    onClick={(e) => {
                                      e.stopPropagation();
                                    }}
                                  />
                                  <span className={taskWithCompletion.completed ? "daily-task-done" : ""}>
                                    {taskWithCompletion.task.name}
                                  </span>
                                </label>
                                {taskWithCompletion.task.description && (
                                  <p className="daily-task-description">{taskWithCompletion.task.description}</p>
                                )}
                              </li>
                            );
                          })}
                        </ul>
                      )}
                    </div>
                  );
                })}
              </div>
            </section>
          )}

          {/* Children */}
          {children.length > 0 && (
            <section className="card" style={{ marginTop: "20px" }}>
              <h3 style={{ marginTop: 0, marginBottom: "16px", fontSize: "1.1rem", fontWeight: 600 }}>
                Barn
              </h3>
              <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
                {children.map((child) => {
                  const memberData = memberTasks.get(child.id);
                  const tasks = memberData?.tasks || [];
                  const allCompleted = memberData?.allCompleted || false;
                  const showCelebration = celebrations.get(child.id);

                  return (
                    <div key={child.id} style={{ 
                      padding: "16px",
                      background: allCompleted ? "rgba(184, 230, 184, 0.1)" : "rgba(240, 240, 240, 0.3)",
                      borderRadius: "12px",
                      border: allCompleted ? "2px solid rgba(184, 230, 184, 0.5)" : "1px solid rgba(220, 210, 200, 0.3)"
                    }}>
                      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "12px" }}>
                        <h4 style={{ margin: 0, fontSize: "1.1rem", fontWeight: 600 }}>
                          {child.name}
                        </h4>
                        {allCompleted && tasks.length > 0 && (
                          <span style={{ 
                            fontSize: "0.85rem", 
                            color: "#2d5a2d",
                            fontWeight: 500,
                            padding: "4px 12px",
                            background: "rgba(184, 230, 184, 0.3)",
                            borderRadius: "12px"
                          }}>
                            ✓ Alla klara
                          </span>
                        )}
                      </div>

                      {showCelebration && (
                        <div style={{
                          marginBottom: "12px",
                          padding: "12px",
                          background: "rgba(184, 230, 184, 0.2)",
                          borderRadius: "8px",
                          textAlign: "center"
                        }}>
                          <div style={{ fontSize: "1.5rem", marginBottom: "4px" }}>🎉</div>
                          <p style={{ margin: 0, fontWeight: 600, color: "#2d5a2d" }}>
                            Nu får du ta skärmtid :)
                          </p>
                        </div>
                      )}

                      {tasks.length === 0 ? (
                        <p style={{ margin: 0, color: "#6b6b6b", fontSize: "0.9rem" }}>
                          Inga sysslor för idag
                        </p>
                      ) : (
                        <ul className="daily-tasks-list" style={{ margin: 0 }}>
                          {tasks.map((taskWithCompletion) => {
                            const toggleKey = `${taskWithCompletion.task.id}-${child.id}`;
                            const isToggling = togglingTasks.has(toggleKey);
                            
                            return (
                              <li key={`${child.id}-${taskWithCompletion.task.id}`} className="daily-task-item">
                                <label className="daily-task-label">
                                  <input
                                    type="checkbox"
                                    checked={taskWithCompletion.completed}
                                    disabled={isToggling}
                                    onChange={(e) => {
                                      e.preventDefault();
                                      e.stopPropagation();
                                      void handleToggle(taskWithCompletion.task.id, child.id);
                                    }}
                                    onClick={(e) => {
                                      e.stopPropagation();
                                    }}
                                  />
                                  <span className={taskWithCompletion.completed ? "daily-task-done" : ""}>
                                    {taskWithCompletion.task.name}
                                  </span>
                                </label>
                                <p style={{ 
                                  margin: "4px 0 0 34px", 
                                  fontSize: "0.75rem", 
                                  color: "#a0a0a0",
                                  fontStyle: "italic"
                                }}>
                                  {taskWithCompletion.task.isRequired ? "Obligatorisk" : "Extra"} • +{taskWithCompletion.task.xpPoints} XP
                                </p>
                                {taskWithCompletion.task.description && (
                                  <p className="daily-task-description">{taskWithCompletion.task.description}</p>
                                )}
                              </li>
                            );
                          })}
                        </ul>
                      )}
                    </div>
                  );
                })}
              </div>
            </section>
          )}

          {members.length === 0 && (
            <section className="card">
              <p className="placeholder-text">Inga familjemedlemmar hittades.</p>
            </section>
          )}
        </>
      )}
    </div>
  );
}


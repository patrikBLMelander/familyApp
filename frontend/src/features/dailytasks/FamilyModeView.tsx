import { useEffect, useState } from "react";
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

  useEffect(() => {
    void loadData();
    // Refresh every 30 seconds to keep data up to date
    const interval = setInterval(() => {
      void loadData();
    }, 30000);
    return () => clearInterval(interval);
  }, []);

  const loadData = async () => {
    try {
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
          if (allCompleted && tasks.length > 0 && !celebrations.get(member.id)) {
            setCelebrations(prev => new Map(prev).set(member.id, true));
            // Hide celebration after 6 seconds
            setTimeout(() => {
              setCelebrations(prev => {
                const newMap = new Map(prev);
                newMap.delete(member.id);
                return newMap;
              });
            }, 6000);
          } else if (!allCompleted) {
            // Reset celebration state if tasks are unchecked
            setCelebrations(prev => {
              const newMap = new Map(prev);
              newMap.delete(member.id);
              return newMap;
            });
          }
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
    }
  };

  const handleToggle = async (taskId: string, memberId: string) => {
    try {
      await toggleTaskCompletion(taskId, memberId);
      // Reload data after toggle
      await loadData();
    } catch (e) {
      console.error("Error toggling task:", e);
      setError("Kunde inte uppdatera syssla.");
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
                          {tasks.map((taskWithCompletion) => (
                            <li key={`${parent.id}-${taskWithCompletion.task.id}`} className="daily-task-item">
                              <label className="daily-task-label">
                                <input
                                  type="checkbox"
                                  checked={taskWithCompletion.completed}
                                  onChange={() => void handleToggle(taskWithCompletion.task.id, parent.id)}
                                />
                                <span className={taskWithCompletion.completed ? "daily-task-done" : ""}>
                                  {taskWithCompletion.task.name}
                                </span>
                              </label>
                              {taskWithCompletion.task.description && (
                                <p className="daily-task-description">{taskWithCompletion.task.description}</p>
                              )}
                            </li>
                          ))}
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
                          {tasks.map((taskWithCompletion) => (
                            <li key={`${child.id}-${taskWithCompletion.task.id}`} className="daily-task-item">
                              <label className="daily-task-label">
                                <input
                                  type="checkbox"
                                  checked={taskWithCompletion.completed}
                                  onChange={() => void handleToggle(taskWithCompletion.task.id, child.id)}
                                />
                                <span className={taskWithCompletion.completed ? "daily-task-done" : ""}>
                                  {taskWithCompletion.task.name}
                                </span>
                              </label>
                              {taskWithCompletion.task.description && (
                                <p className="daily-task-description">{taskWithCompletion.task.description}</p>
                              )}
                            </li>
                          ))}
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


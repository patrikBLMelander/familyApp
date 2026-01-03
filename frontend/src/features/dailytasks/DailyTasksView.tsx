import { useEffect, useState } from "react";
import {
  fetchTasksForToday,
  fetchTasksForTodayWithChildren,
  toggleTaskCompletion,
  DailyTaskWithCompletionResponse,
  DailyTaskWithChildrenCompletionResponse
} from "../../shared/api/dailyTasks";
import { useIsChild } from "../../shared/hooks/useIsChild";
import { FamilyModeView } from "./FamilyModeView";
import { fetchAllFamilyMembers } from "../../shared/api/familyMembers";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "dailytasks" | "dailytasksadmin";

type DailyTasksViewProps = {
  onNavigate?: (view: ViewKey) => void;
};

export function DailyTasksView({ onNavigate }: DailyTasksViewProps) {
  const { isChild, loading: isChildLoading } = useIsChild();
  const [familyMode, setFamilyMode] = useState(false);

  const handleLogout = () => {
    if (confirm("Vill du gå tillbaka till föräldervyn?")) {
      // Restore parent token if it exists (from testing child view)
      const parentToken = localStorage.getItem("parentDeviceToken");
      if (parentToken) {
        localStorage.setItem("deviceToken", parentToken);
        localStorage.removeItem("parentDeviceToken");
      } else {
        // If no parent token saved, remove device token (full logout)
        localStorage.removeItem("deviceToken");
      }
      window.location.href = "/";
    }
  };
  const [tasks, setTasks] = useState<DailyTaskWithCompletionResponse[]>([]);
  const [tasksWithChildren, setTasksWithChildren] = useState<DailyTaskWithChildrenCompletionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCelebration, setShowCelebration] = useState(false);
  const [hasShownCelebration, setHasShownCelebration] = useState(false);

  const getMemberIdFromToken = async (deviceToken: string): Promise<string | undefined> => {
    try {
      const { getMemberByDeviceToken } = await import("../../shared/api/familyMembers");
      const member = await getMemberByDeviceToken(deviceToken);
      return member.id;
    } catch {
      // Token invalid, clear it
      localStorage.removeItem("deviceToken");
      return undefined;
    }
  };

  useEffect(() => {
    // Wait for isChild to be determined before loading tasks
    if (isChildLoading) {
      return;
    }

    const load = async () => {
      try {
        setLoading(true);
        // Get deviceToken from localStorage (set when scanning QR code)
        const deviceToken = localStorage.getItem("deviceToken");
        
        if (isChild) {
          // For children, load their own tasks
          if (!deviceToken) {
            console.error("No device token found for child");
            setError("Ingen inloggningstoken hittades.");
            setLoading(false);
            return;
          }
          
          const memberId = await getMemberIdFromToken(deviceToken);
          console.log("Loading tasks for child, memberId:", memberId, "deviceToken:", deviceToken);
          
          if (!memberId) {
            console.error("Could not get memberId from device token");
            setError("Kunde inte hämta användarinformation.");
            setLoading(false);
            return;
          }
          
          const data = await fetchTasksForToday(memberId);
          console.log("Tasks loaded for child:", data);
          setTasks(data);
        } else {
          // For parents, load tasks with all members' completion info
          const data = await fetchTasksForTodayWithChildren();
          
          // Get all family members to determine roles
          const allMembers = await fetchAllFamilyMembers();
          const parentIds = new Set(allMembers.filter(m => m.role === "PARENT").map(m => m.id));
          const childIds = new Set(allMembers.filter(m => m.role === "CHILD").map(m => m.id));
          
          // Get current parent ID
          let currentParentId: string | undefined;
          if (deviceToken) {
            currentParentId = await getMemberIdFromToken(deviceToken);
          }
          
          // Separate tasks into parent tasks and children tasks based on who has completions
          const parentTasks: DailyTaskWithCompletionResponse[] = [];
          const childrenTasks: DailyTaskWithChildrenCompletionResponse[] = [];
          
          for (const taskWithChildren of data) {
            // Check if this task has any parent completions
            const hasParentCompletions = taskWithChildren.childCompletions.some(
              cc => parentIds.has(cc.childId)
            );
            // Check if this task has any child completions
            const hasChildCompletions = taskWithChildren.childCompletions.some(
              cc => childIds.has(cc.childId)
            );
            
            // Filter completions by role
            const parentCompletions = taskWithChildren.childCompletions.filter(
              cc => parentIds.has(cc.childId)
            );
            const childCompletions = taskWithChildren.childCompletions.filter(
              cc => childIds.has(cc.childId)
            );
            
            // If task has parent completions, show it as a parent task
            if (hasParentCompletions && currentParentId) {
              // Find this parent's completion status
              const parentCompletion = parentCompletions.find(pc => pc.childId === currentParentId);
              if (parentCompletion) {
                parentTasks.push({
                  task: taskWithChildren.task,
                  completed: parentCompletion.completed
                });
              }
            }
            
            // If task has child completions, show it as a children task
            if (hasChildCompletions) {
              childrenTasks.push({
                task: taskWithChildren.task,
                childCompletions: childCompletions
              });
            }
          }
          
          setTasks(parentTasks);
          setTasksWithChildren(childrenTasks);
        }
      } catch (e) {
        console.error("Error loading daily tasks:", e);
        setError("Kunde inte hämta dagens sysslor just nu.");
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [isChild, isChildLoading]);

  // Reset celebration state if a task is unchecked
  useEffect(() => {
    if (!isChild || loading || tasks.length === 0) return;
    
    const allCompleted = tasks.every((task) => task.completed);
    if (!allCompleted) {
      // Reset celebration state if a task is unchecked
      setHasShownCelebration(false);
    }
  }, [tasks, isChild, loading]);

  const handleToggle = async (taskId: string) => {
    // Check if all tasks were completed before this toggle
    const wasAllCompleted = tasks.length > 0 && tasks.every((task) => task.completed);
    
    // Optimistic update
    setTasks((prev) =>
      prev.map((task) =>
        task.task.id === taskId
          ? { ...task, completed: !task.completed }
          : task
      )
    );

    try {
      await toggleTaskCompletion(taskId);
      
      // Reload to get updated state
      const deviceToken = localStorage.getItem("deviceToken");
      
      if (isChild) {
        const memberId = deviceToken ? await getMemberIdFromToken(deviceToken) : undefined;
        const data = await fetchTasksForToday(memberId);
        setTasks(data);
        
        // Check if all tasks are now completed (and weren't before)
        const nowAllCompleted = data.length > 0 && data.every((task) => task.completed);
        if (nowAllCompleted && !wasAllCompleted) {
          // Trigger celebration after a short delay for better UX
          setTimeout(() => {
            setShowCelebration(true);
            setHasShownCelebration(true);
            setTimeout(() => {
              setShowCelebration(false);
            }, 6000);
          }, 300);
        }
      } else {
        // For parents, reload both own tasks and children tasks
        const data = await fetchTasksForTodayWithChildren();
        
        // Get all family members to determine roles
        const allMembers = await fetchAllFamilyMembers();
        const parentIds = new Set(allMembers.filter(m => m.role === "PARENT").map(m => m.id));
        const childIds = new Set(allMembers.filter(m => m.role === "CHILD").map(m => m.id));
        
        // Get current parent ID
        let currentParentId: string | undefined;
        if (deviceToken) {
          currentParentId = await getMemberIdFromToken(deviceToken);
        }
        
        // Separate tasks into parent tasks and children tasks
        const parentTasks: DailyTaskWithCompletionResponse[] = [];
        const childrenTasks: DailyTaskWithChildrenCompletionResponse[] = [];
        
        for (const taskWithChildren of data) {
          // Check if this task has any parent completions
          const hasParentCompletions = taskWithChildren.childCompletions.some(
            cc => parentIds.has(cc.childId)
          );
          // Check if this task has any child completions
          const hasChildCompletions = taskWithChildren.childCompletions.some(
            cc => childIds.has(cc.childId)
          );
          
          // Filter completions by role
          const parentCompletions = taskWithChildren.childCompletions.filter(
            cc => parentIds.has(cc.childId)
          );
          const childCompletions = taskWithChildren.childCompletions.filter(
            cc => childIds.has(cc.childId)
          );
          
          // If task has parent completions, show it as a parent task
          if (hasParentCompletions && currentParentId) {
            // Find this parent's completion status
            const parentCompletion = parentCompletions.find(pc => pc.childId === currentParentId);
            if (parentCompletion) {
              parentTasks.push({
                task: taskWithChildren.task,
                completed: parentCompletion.completed
              });
            }
          }
          
          // If task has child completions, show it as a children task
          if (hasChildCompletions) {
            childrenTasks.push({
              task: taskWithChildren.task,
              childCompletions: childCompletions
            });
          }
        }
        
        setTasks(parentTasks);
        setTasksWithChildren(childrenTasks);
        
        // Check if all parent tasks are now completed
        const nowAllCompleted = parentTasks.length > 0 && parentTasks.every((task) => task.completed);
        if (nowAllCompleted && !wasAllCompleted) {
          setTimeout(() => {
            setShowCelebration(true);
            setHasShownCelebration(true);
            setTimeout(() => {
              setShowCelebration(false);
            }, 6000);
          }, 300);
        }
      }
    } catch {
      // Revert on error
      const deviceToken = localStorage.getItem("deviceToken");
      if (isChild) {
        const memberId = deviceToken ? await getMemberIdFromToken(deviceToken) : undefined;
        const data = await fetchTasksForToday(memberId);
        setTasks(data);
      } else {
        const data = await fetchTasksForTodayWithChildren();
        
        // Get all family members to determine roles
        const allMembers = await fetchAllFamilyMembers();
        const parentIds = new Set(allMembers.filter(m => m.role === "PARENT").map(m => m.id));
        const childIds = new Set(allMembers.filter(m => m.role === "CHILD").map(m => m.id));
        
        // Get current parent ID
        let currentParentId: string | undefined;
        if (deviceToken) {
          currentParentId = await getMemberIdFromToken(deviceToken);
        }
        
        // Separate tasks into parent tasks and children tasks
        const parentTasks: DailyTaskWithCompletionResponse[] = [];
        const childrenTasks: DailyTaskWithChildrenCompletionResponse[] = [];
        
        for (const taskWithChildren of data) {
          // Check if this task has any parent completions
          const hasParentCompletions = taskWithChildren.childCompletions.some(
            cc => parentIds.has(cc.childId)
          );
          // Check if this task has any child completions
          const hasChildCompletions = taskWithChildren.childCompletions.some(
            cc => childIds.has(cc.childId)
          );
          
          // Filter completions by role
          const parentCompletions = taskWithChildren.childCompletions.filter(
            cc => parentIds.has(cc.childId)
          );
          const childCompletions = taskWithChildren.childCompletions.filter(
            cc => childIds.has(cc.childId)
          );
          
          // If task has parent completions, show it as a parent task
          if (hasParentCompletions && currentParentId) {
            // Find this parent's completion status
            const parentCompletion = parentCompletions.find(pc => pc.childId === currentParentId);
            if (parentCompletion) {
              parentTasks.push({
                task: taskWithChildren.task,
                completed: parentCompletion.completed
              });
            }
          }
          
          // If task has child completions, show it as a children task
          if (hasChildCompletions) {
            childrenTasks.push({
              task: taskWithChildren.task,
              childCompletions: childCompletions
            });
          }
        }
        setTasks(parentTasks);
        setTasksWithChildren(childrenTasks);
      }
      setError("Kunde inte uppdatera syssla.");
    }
  };

  const today = new Date();
  const dayNames = ["Söndag", "Måndag", "Tisdag", "Onsdag", "Torsdag", "Fredag", "Lördag"];
  const todayName = dayNames[today.getDay()];

  return (
    <div className="daily-tasks-view">
      {showCelebration && (
        <div className="celebration-overlay">
          <div className="celebration-confetti">
            {Array.from({ length: 20 }).map((_, i) => (
              <div
                key={i}
                className="confetti-piece"
                style={{
                  left: `${Math.random() * 100}%`,
                  animationDelay: `${Math.random() * 0.5}s`,
                  backgroundColor: ['#b8e6b8', '#ffd4a8', '#ffb8d8', '#d8b8ff', '#b8d8ff'][Math.floor(Math.random() * 5)]
                }}
              />
            ))}
          </div>
          <div className="celebration-content">
            <div className="celebration-emoji">🎉</div>
            <h2 className="celebration-title">Grattis!</h2>
            <p className="celebration-message">
              {isChild ? "Nu får du ta skärmtid :)" : "Snyggt jobbat! 🎉"}
            </p>
            <button
              type="button"
              className="button-primary"
              onClick={() => setShowCelebration(false)}
            >
              Stäng
            </button>
          </div>
        </div>
      )}
      {familyMode ? (
        <FamilyModeView onToggle={() => setFamilyMode(false)} />
      ) : (
        <>
          <div className="daily-tasks-header">
            <div style={{ flex: 1 }}>
              <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                {!isChild && onNavigate && (
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
                  <h2 className="view-title" style={{ margin: 0, marginBottom: "4px" }}>Dagens sysslor</h2>
                  <p className="daily-tasks-date" style={{ margin: 0 }}>{todayName} {today.toLocaleDateString('sv-SE')}</p>
                </div>
              </div>
            </div>
            <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
              {isChild && (
                <button
                  type="button"
                  className="button-secondary"
                  onClick={handleLogout}
                  style={{ fontSize: "0.8rem", padding: "6px 12px" }}
                >
                  Logga ut
                </button>
              )}
              {!isChild && (
                <>
                  <button
                    type="button"
                    className="button-primary"
                    onClick={() => setFamilyMode(true)}
                    style={{ fontSize: "0.9rem", padding: "8px 16px" }}
                  >
                    Familjeläge
                  </button>
                  {onNavigate && (
                    <button
                      type="button"
                      className="todo-action-button"
                      onClick={() => onNavigate("dailytasksadmin")}
                    >
                      Hantera sysslor
                    </button>
                  )}
                </>
              )}
            </div>
          </div>

          {error && <p className="error-text">{error}</p>}

          {(loading || isChildLoading) && (
            <section className="card">
              <p>Laddar...</p>
            </section>
          )}

          {!loading && !isChildLoading && isChild && (
            <section className="card">
              {tasks.length === 0 && (
                <p className="placeholder-text">Inga sysslor för idag! 🎉</p>
              )}

              {tasks.length > 0 && (
                <ul className="daily-tasks-list">
                  {tasks.map((taskWithCompletion) => (
                    <li key={taskWithCompletion.task.id} className="daily-task-item">
                      <label className="daily-task-label">
                        <input
                          type="checkbox"
                          checked={taskWithCompletion.completed}
                          onChange={() => handleToggle(taskWithCompletion.task.id)}
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
            </section>
          )}

          {!loading && !isChildLoading && !isChild && (
        <>
          {/* Parent's own tasks */}
          {tasks.length > 0 && (
            <section className="card">
              <h3 style={{ marginTop: 0, marginBottom: "12px", fontSize: "1rem", fontWeight: 600 }}>
                Mina sysslor
              </h3>
              <ul className="daily-tasks-list">
                {tasks.map((taskWithCompletion) => (
                  <li key={taskWithCompletion.task.id} className="daily-task-item">
                    <label className="daily-task-label">
                      <input
                        type="checkbox"
                        checked={taskWithCompletion.completed}
                        onChange={() => handleToggle(taskWithCompletion.task.id)}
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
            </section>
          )}

          {/* Separator */}
          {tasks.length > 0 && tasksWithChildren.length > 0 && (
            <div style={{ 
              margin: "16px 0", 
              padding: "0 16px",
              display: "flex",
              alignItems: "center",
              gap: "8px"
            }}>
              <div style={{ flex: 1, height: "1px", background: "rgba(200, 190, 180, 0.4)" }} />
              <span style={{ fontSize: "0.85rem", color: "#a0a0a0" }}>Barnens sysslor</span>
              <div style={{ flex: 1, height: "1px", background: "rgba(200, 190, 180, 0.4)" }} />
            </div>
          )}

          {/* Children's tasks */}
          {tasksWithChildren.length > 0 && (
            <section className="card">
              {tasks.length === 0 && (
                <h3 style={{ marginTop: 0, marginBottom: "12px", fontSize: "1rem", fontWeight: 600 }}>
                  Barnens sysslor
                </h3>
              )}
              <ul className="daily-tasks-list">
                {tasksWithChildren.map((taskWithChildren) => (
                  <li key={taskWithChildren.task.id} className="daily-task-item">
                    <div>
                      <span className={taskWithChildren.childCompletions.every(c => c.completed) ? "daily-task-done" : ""}>
                        {taskWithChildren.task.name}
                      </span>
                      {taskWithChildren.task.description && (
                        <p className="daily-task-description">{taskWithChildren.task.description}</p>
                      )}
                      <div style={{ marginTop: "8px", display: "flex", flexWrap: "wrap", gap: "6px" }}>
                        {taskWithChildren.childCompletions.map((childCompletion) => (
                          <span
                            key={childCompletion.childId}
                            style={{
                              fontSize: "0.8rem",
                              padding: "3px 8px",
                              borderRadius: "6px",
                              background: childCompletion.completed 
                                ? "rgba(184, 230, 184, 0.3)" 
                                : "rgba(240, 240, 240, 0.6)",
                              color: childCompletion.completed ? "#2d5a2d" : "#6b6b6b",
                              fontWeight: childCompletion.completed ? 500 : 400
                            }}
                          >
                            {childCompletion.childName}: {childCompletion.completed ? "✓" : "○"}
                          </span>
                        ))}
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {tasks.length === 0 && tasksWithChildren.length === 0 && (
            <section className="card">
              <p className="placeholder-text">Inga sysslor för idag! 🎉</p>
            </section>
          )}
        </>
      )}
        </>
      )}
    </div>
  );
}


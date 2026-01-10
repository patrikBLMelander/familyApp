import { useState, useEffect } from "react";
import { fetchCurrentPet, PetResponse } from "../../shared/api/pets";
import { fetchCurrentXpProgress, XpProgressResponse } from "../../shared/api/xp";
import { fetchTasksForToday, toggleTaskCompletion, DailyTaskWithCompletionResponse } from "../../shared/api/dailyTasks";
import { getMemberByDeviceToken } from "../../shared/api/familyMembers";
import { PetVisualization } from "../pet/PetVisualization";

type ViewKey = "dailytasks" | "xp";

type ChildDashboardProps = {
  onNavigate?: (view: ViewKey) => void;
  childName?: string;
  onLogout?: () => void;
};

const XP_PER_LEVEL = 24;

export function ChildDashboard({ onNavigate, childName, onLogout }: ChildDashboardProps) {
  const [pet, setPet] = useState<PetResponse | null>(null);
  const [xpProgress, setXpProgress] = useState<XpProgressResponse | null>(null);
  const [tasks, setTasks] = useState<DailyTaskWithCompletionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        setError(null);

        const deviceToken = localStorage.getItem("deviceToken");
        if (!deviceToken) {
          setError("Ingen inloggningstoken hittades.");
          return;
        }

        const member = await getMemberByDeviceToken(deviceToken);
        const memberId = member.id;

        // Load pet, XP, and tasks in parallel
        const [petData, xpData, tasksData] = await Promise.all([
          fetchCurrentPet().catch(() => null),
          fetchCurrentXpProgress().catch(() => null),
          fetchTasksForToday(memberId).catch(() => []),
        ]);

        setPet(petData);
        setXpProgress(xpData);
        setTasks(tasksData);
      } catch (e) {
        console.error("Error loading dashboard data:", e);
        setError("Kunde inte ladda data.");
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, []);

  const handleToggleTask = async (taskId: string) => {
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
      
      // Reload data to get updated state
      const deviceToken = localStorage.getItem("deviceToken");
      if (deviceToken) {
        const member = await getMemberByDeviceToken(deviceToken);
        const [xpData, tasksData] = await Promise.all([
          fetchCurrentXpProgress().catch(() => null),
          fetchTasksForToday(member.id).catch(() => []),
        ]);
        setXpProgress(xpData);
        setTasks(tasksData);
      }
    } catch (e) {
      console.error("Error toggling task:", e);
      // Revert on error by reloading
      const deviceToken = localStorage.getItem("deviceToken");
      if (deviceToken) {
        const member = await getMemberByDeviceToken(deviceToken);
        const tasksData = await fetchTasksForToday(member.id).catch(() => []);
        setTasks(tasksData);
      }
    }
  };

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
      if (onLogout) {
        onLogout();
      } else {
        window.location.href = "/";
      }
    }
  };

  if (loading) {
    return (
      <div className="dashboard child-dashboard" style={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}>
        <section className="card">
          <p>Laddar...</p>
        </section>
      </div>
    );
  }

  if (error || !pet) {
    return (
      <div className="dashboard child-dashboard">
        <section className="card">
          <p className="error-text">{error || "Ingen pet hittades. Välj ett ägg först!"}</p>
        </section>
      </div>
    );
  }

  // Calculate energy (XP) progress
  const energyPercentage = xpProgress 
    ? (xpProgress.xpInCurrentLevel / XP_PER_LEVEL) * 100 
    : 0;
  const totalEnergy = xpProgress?.currentXp || 0;
  const completedTasksCount = tasks.filter(t => t.completed).length;
  const totalTasksCount = tasks.length;

  return (
    <div className="dashboard child-dashboard" style={{
      background: "linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)",
      minHeight: "100vh",
      padding: "20px",
    }}>
      {/* Header */}
      {childName && (
        <div style={{ 
          marginBottom: "24px", 
          display: "flex", 
          justifyContent: "space-between", 
          alignItems: "flex-start" 
        }}>
          <div>
            <h2 style={{ margin: "0 0 4px", fontSize: "1.5rem", fontWeight: 700, color: "#2d3748" }}>
              Hej {childName}! 👋
            </h2>
            <p style={{ margin: 0, fontSize: "1rem", color: "#4a5568" }}>
              Ta hand om din {pet.petType === "dragon" ? "drake" : pet.petType === "cat" ? "katt" : pet.petType === "dog" ? "hund" : pet.petType === "bird" ? "fågel" : pet.petType === "bear" ? "björn" : "kanin"}!
            </p>
          </div>
          <button
            type="button"
            className="button-secondary"
            onClick={handleLogout}
            style={{ fontSize: "0.85rem", padding: "8px 16px" }}
          >
            Logga ut
          </button>
        </div>
      )}

      {/* Pet Visualization Card */}
      <section className="card" style={{
        backgroundImage: `url(/pets/${pet.petType}/${pet.petType}-background.png)`,
        backgroundSize: "cover",
        backgroundPosition: "center",
        backgroundRepeat: "no-repeat",
        backgroundColor: "white", // Fallback if image doesn't load
        borderRadius: "24px",
        padding: "40px 20px",
        marginBottom: "24px",
        textAlign: "center",
        boxShadow: "0 10px 25px rgba(0, 0, 0, 0.1)",
        position: "relative",
        overflow: "hidden",
      }}>
        {/* Semi-transparent overlay for text readability */}
        <div style={{
          position: "absolute",
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: "linear-gradient(to bottom, rgba(255, 255, 255, 0.3) 0%, rgba(255, 255, 255, 0.7) 100%)",
          pointerEvents: "none",
          borderRadius: "24px",
        }} />
        
        <div style={{ position: "relative", zIndex: 1 }}>
          <PetVisualization petType={pet.petType} growthStage={pet.growthStage} size="large" />
          
          <h3 style={{
            margin: "20px 0 8px",
            fontSize: "1.5rem",
            fontWeight: 700,
            color: "#2d3748",
            textShadow: "0 1px 2px rgba(255, 255, 255, 0.8)",
          }}>
            {pet.name || (pet.petType === "dragon" ? "Drak" : pet.petType === "cat" ? "Katt" : pet.petType === "dog" ? "Hund" : pet.petType === "bird" ? "Fågel" : pet.petType === "bear" ? "Björn" : "Kanin")}
          </h3>
          <p style={{
            margin: 0,
            fontSize: "1rem",
            color: "#4a5568",
            textShadow: "0 1px 2px rgba(255, 255, 255, 0.8)",
          }}>
            Växtsteg {pet.growthStage} av 5
          </p>
        </div>
      </section>

      {/* Energy Bar */}
      <section className="card" style={{
        background: "white",
        borderRadius: "20px",
        padding: "24px",
        marginBottom: "24px",
        boxShadow: "0 4px 6px rgba(0, 0, 0, 0.1)",
      }}>
        <div style={{ marginBottom: "12px" }}>
          <div style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            marginBottom: "8px",
          }}>
            <h3 style={{
              margin: 0,
              fontSize: "1.2rem",
              fontWeight: 600,
              color: "#2d3748",
            }}>
              ⚡ Energi
            </h3>
            <span style={{
              fontSize: "1rem",
              fontWeight: 600,
              color: "#4a5568",
            }}>
              {totalEnergy} XP
            </span>
          </div>
          <div style={{
            width: "100%",
            height: "28px",
            background: "#e2e8f0",
            borderRadius: "14px",
            overflow: "hidden",
            position: "relative",
          }}>
            <div style={{
              width: `${energyPercentage}%`,
              height: "100%",
              background: "linear-gradient(90deg, #667eea 0%, #764ba2 100%)",
              borderRadius: "14px",
              transition: "width 0.5s ease",
            }} />
          </div>
        </div>
        {xpProgress && xpProgress.currentLevel < 10 && (
          <p style={{
            margin: "8px 0 0",
            fontSize: "0.9rem",
            color: "#718096",
            textAlign: "center",
          }}>
            {xpProgress.xpForNextLevel} XP kvar till nästa level!
          </p>
        )}
      </section>

      {/* Tasks Section */}
      <section className="card" style={{
        background: "white",
        borderRadius: "20px",
        padding: "24px",
        marginBottom: "24px",
        boxShadow: "0 4px 6px rgba(0, 0, 0, 0.1)",
      }}>
        <div style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: "20px",
        }}>
          <h3 style={{
            margin: 0,
            fontSize: "1.2rem",
            fontWeight: 600,
            color: "#2d3748",
          }}>
            📝 Ge din vän energi
          </h3>
          <span style={{
            fontSize: "1rem",
            fontWeight: 600,
            color: completedTasksCount === totalTasksCount ? "#48bb78" : "#4a5568",
          }}>
            {completedTasksCount} / {totalTasksCount}
          </span>
        </div>

        {tasks.length === 0 ? (
          <p style={{
            margin: 0,
            color: "#718096",
            textAlign: "center",
            padding: "20px 0",
          }}>
            Inga sysslor för idag! 🎉
          </p>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            {tasks.slice(0, 3).map((task) => {
              // Different background colors for required vs extra tasks (only when not completed)
              const bgColor = task.completed 
                ? "#f0fff4" // Same green for all completed tasks
                : (task.task.isRequired ? "#f7fafc" : "#fff7ed");
              const borderColor = task.completed 
                ? "#48bb78" // Same green border for all completed tasks
                : (task.task.isRequired ? "#e2e8f0" : "#fed7aa");
              
              return (
                <div
                  key={task.task.id}
                  onClick={() => handleToggleTask(task.task.id)}
                  style={{
                    padding: "16px",
                    background: bgColor,
                    borderRadius: "12px",
                    border: `2px solid ${borderColor}`,
                    display: "flex",
                    alignItems: "center",
                    gap: "12px",
                    cursor: "pointer",
                    transition: "all 0.2s ease",
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.transform = "scale(1.02)";
                    e.currentTarget.style.boxShadow = "0 4px 8px rgba(0, 0, 0, 0.1)";
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.transform = "scale(1)";
                    e.currentTarget.style.boxShadow = "none";
                  }}
                >
                  <div style={{
                    fontSize: "1.5rem",
                    opacity: task.completed ? 1 : 0.5,
                  }}>
                    {task.completed ? "✅" : "⭕"}
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{
                      fontWeight: task.completed ? 600 : 500,
                      color: "#2d3748", // Same color for all text, readable
                      textDecoration: task.completed ? "line-through" : "none",
                    }}>
                      {task.task.name}
                    </div>
                    {task.task.xpPoints > 0 && (
                      <div style={{
                        fontSize: "0.85rem",
                        color: "#718096",
                        marginTop: "4px",
                      }}>
                        +{task.task.xpPoints} XP
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
            {tasks.length > 3 && (
              <button
                type="button"
                className="button-primary"
                onClick={() => onNavigate?.("dailytasks")}
                style={{
                  width: "100%",
                  padding: "12px",
                  marginTop: "8px",
                }}
              >
                Se alla sysslor ({tasks.length})
              </button>
            )}
            {tasks.length <= 3 && (
              <button
                type="button"
                className="button-primary"
                onClick={() => onNavigate?.("dailytasks")}
                style={{
                  width: "100%",
                  padding: "12px",
                  marginTop: "8px",
                }}
              >
                Se alla sysslor
              </button>
            )}
          </div>
        )}
      </section>
    </div>
  );
}

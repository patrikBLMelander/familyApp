import { useState, useEffect } from "react";
import { fetchCurrentPet, PetResponse } from "../../shared/api/pets";
import { fetchCurrentXpProgress, XpProgressResponse } from "../../shared/api/xp";
import { fetchTasksForToday, toggleTaskCompletion, CalendarTaskWithCompletionResponse } from "../../shared/api/calendar";
import { getMemberByDeviceToken } from "../../shared/api/familyMembers";
import { PetVisualization } from "../pet/PetVisualization";
import { getIntegratedPetImagePath, getPetBackgroundImagePath, checkIntegratedImageExists, getPetNameSwedish, getPetNameSwedishLowercase } from "../pet/petImageUtils";

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
  const [tasks, setTasks] = useState<CalendarTaskWithCompletionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [hasIntegratedImage, setHasIntegratedImage] = useState<boolean>(false);

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
        
        // Check if integrated image exists for this pet
        if (petData) {
          const integratedExists = await checkIntegratedImageExists(petData.petType, petData.growthStage);
          setHasIntegratedImage(integratedExists);
        }
        
        // Sort tasks: required first, then by title
        const sortedTasks = [...tasksData].sort((a, b) => {
          if (a.event.isRequired !== b.event.isRequired) {
            return a.event.isRequired ? -1 : 1; // Required first
          }
          return a.event.title.localeCompare(b.event.title);
        });
        setTasks(sortedTasks);
      } catch (e) {
        console.error("Error loading dashboard data:", e);
        setError("Kunde inte ladda data.");
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, []);

  const handleToggleTask = async (eventId: string) => {
    // Optimistic update
    setTasks((prev) =>
      prev.map((task) =>
        task.event.id === eventId
          ? { ...task, completed: !task.completed }
          : task
      )
    );

    try {
      const deviceToken = localStorage.getItem("deviceToken");
      if (!deviceToken) {
        throw new Error("No device token");
      }
      const member = await getMemberByDeviceToken(deviceToken);
      await toggleTaskCompletion(eventId, member.id);
      
      // Reload data to get updated state
      const [xpData, tasksData, petData] = await Promise.all([
        fetchCurrentXpProgress().catch(() => null),
        fetchTasksForToday(member.id).catch(() => []),
        fetchCurrentPet().catch(() => null),
      ]);
      setXpProgress(xpData);
      setTasks(tasksData);
      if (petData) {
        setPet(petData);
        // Check if integrated image exists for updated pet
        const integratedExists = await checkIntegratedImageExists(petData.petType, petData.growthStage);
        setHasIntegratedImage(integratedExists);
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
              Ta hand om din {getPetNameSwedishLowercase(pet.petType)}!
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
        padding: 0,
        marginBottom: "24px",
        borderRadius: "24px",
        boxShadow: "0 10px 25px rgba(0, 0, 0, 0.1)",
        overflow: "hidden",
        backgroundColor: "white",
      }}>
        {/* Image container with 3:2 aspect ratio (1440×960) */}
        <div style={{
          backgroundImage: hasIntegratedImage 
            ? `url(${getIntegratedPetImagePath(pet.petType, pet.growthStage)})`
            : `url(${getPetBackgroundImagePath(pet.petType)})`,
          backgroundSize: "cover",
          backgroundPosition: "center",
          backgroundRepeat: "no-repeat",
          backgroundColor: "white",
          width: "100%",
          aspectRatio: "3 / 2", // 1440×960 aspect ratio
          position: "relative",
        }}>
          {/* Only show PetVisualization if integrated image doesn't exist */}
          {!hasIntegratedImage && (
            <div style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              height: "100%",
              padding: "20px",
            }}>
              <PetVisualization petType={pet.petType} growthStage={pet.growthStage} size="large" />
            </div>
          )}
        </div>
        
        {/* Text below image */}
        <div style={{
          padding: "20px",
          textAlign: "center",
          borderTop: "1px solid rgba(0, 0, 0, 0.1)",
        }}>
          <h3 style={{
            margin: "0 0 4px",
            fontSize: "1.5rem",
            fontWeight: 700,
            color: "#2d3748",
          }}>
            {pet.name || getPetNameSwedish(pet.petType)}
          </h3>
          <p style={{
            margin: 0,
            fontSize: "1rem",
            color: "#4a5568",
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
            {tasks.map((task) => {
              // Different background colors for required vs extra tasks (only when not completed)
              const bgColor = task.completed 
                ? "#f0fff4" // Same green for all completed tasks
                : (task.event.isRequired ? "#f7fafc" : "#fff7ed");
              const borderColor = task.completed 
                ? "#48bb78" // Same green border for all completed tasks
                : (task.event.isRequired ? "#e2e8f0" : "#fed7aa");
              
              return (
                <div
                  key={task.event.id}
                  onClick={() => handleToggleTask(task.event.id)}
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
                      {task.event.title}
                    </div>
                    {task.event.xpPoints && task.event.xpPoints > 0 && (
                      <div style={{
                        fontSize: "0.85rem",
                        color: "#718096",
                        marginTop: "4px",
                      }}>
                        +{task.event.xpPoints} XP
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}

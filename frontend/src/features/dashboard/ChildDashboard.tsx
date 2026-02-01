import { useState, useEffect, useRef } from "react";
import { fetchCurrentPet, PetResponse, feedPet, getCollectedFood, CollectedFoodResponse, getLastFedDate } from "../../shared/api/pets";
import { fetchCurrentXpProgress, XpProgressResponse } from "../../shared/api/xp";
import { fetchTasksForToday, toggleTaskCompletion, CalendarTaskWithCompletionResponse } from "../../shared/api/calendar";
import { getMemberByDeviceToken } from "../../shared/api/familyMembers";
import { PetVisualization } from "../pet/PetVisualization";
import { getIntegratedPetImagePath, getPetBackgroundImagePath, checkIntegratedImageExists, getPetNameSwedish, getPetNameSwedishLowercase } from "../pet/petImageUtils";
import { getPetFoodEmoji, getPetFoodName, getRandomPetMessage } from "../pet/petFoodUtils";
import { HalfCircleProgress } from "./components/HalfCircleProgress";
import { ConfettiAnimation } from "./components/ConfettiAnimation";
import { FloatingXpNumber } from "./components/FloatingXpNumber";

type ViewKey = "dailytasks" | "xp" | "pethistory";

type ChildDashboardProps = {
  onNavigate?: (view: ViewKey) => void;
  childName?: string;
  onLogout?: () => void;
};

const MAX_LEVEL = 5;

// FoodItem is now managed by backend, we just track count

export function ChildDashboard({ onNavigate, childName, onLogout }: ChildDashboardProps) {
  const [pet, setPet] = useState<PetResponse | null>(null);
  const [xpProgress, setXpProgress] = useState<XpProgressResponse | null>(null);
  const [tasks, setTasks] = useState<CalendarTaskWithCompletionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [hasIntegratedImage, setHasIntegratedImage] = useState<boolean>(false);
  
  // Food collection state
  const [collectedFoodCount, setCollectedFoodCount] = useState<number>(0);
  const [isFeeding, setIsFeeding] = useState(false);
  const [showConfetti, setShowConfetti] = useState(false);
  const [floatingXp, setFloatingXp] = useState<number | null>(null);
  const [petMood, setPetMood] = useState<"happy" | "hungry">("happy");
  const [petMessage, setPetMessage] = useState<string>("");
  const [windowWidth, setWindowWidth] = useState<number>(typeof window !== "undefined" ? window.innerWidth : 1024);
  const previousLevelRef = useRef<number>(0);
  
  // Utility function to get today's date in local timezone (YYYY-MM-DD format)
  const getTodayLocalDateString = (): string => {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };
  
  // Check if a date string (ISO 8601) is today in local timezone
  const isDateToday = (dateString: string | null): boolean => {
    if (!dateString) return false;
    try {
      const date = new Date(dateString);
      const today = getTodayLocalDateString();
      const dateStr = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
      return dateStr === today;
    } catch (e) {
      console.error("Error parsing date:", e);
      return false;
    }
  };
  
  // Track window width for responsive design
  useEffect(() => {
    const handleResize = () => {
      setWindowWidth(window.innerWidth);
    };
    
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

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

        // Load pet, XP, tasks, collected food, and last fed date in parallel
        const [petData, xpData, tasksData, foodData, lastFedData] = await Promise.all([
          fetchCurrentPet().catch((e) => {
            console.error("Error fetching pet:", e);
            return null;
          }),
          fetchCurrentXpProgress().catch((e) => {
            console.error("Error fetching XP progress:", e);
            return null;
          }),
          fetchTasksForToday(memberId).catch((e) => {
            console.error("Error fetching tasks:", e);
            return [];
          }),
          getCollectedFood().catch((e) => {
            console.error("Error fetching collected food:", e);
            return { foodItems: [], totalCount: 0 };
          }),
          getLastFedDate().catch((e) => {
            console.error("Error fetching last fed date:", e);
            // Fallback: assume not fed today if we can't get the data
            return { lastFedAt: null };
          }),
        ]);

        setPet(petData);
        setXpProgress(xpData);
        
        // Initialize previous level
        if (xpData) {
          previousLevelRef.current = xpData.currentLevel;
        }
        
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
        
        // Set collected food count from backend
        setCollectedFoodCount(foodData.totalCount);
        
        // Check pet mood based on whether it has been fed today (using backend data)
        // Pet is happy if it has been fed today, hungry otherwise
        const wasFedToday = isDateToday(lastFedData.lastFedAt);
        if (wasFedToday) {
          // Pet has been fed today - happy!
          setPetMood("happy");
          setPetMessage(getRandomPetMessage("happy"));
        } else {
          // Pet has not been fed today - hungry
          setPetMood("hungry");
          setPetMessage(getRandomPetMessage("hungry"));
        }
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
    const task = tasks.find(t => t.event.id === eventId);
    if (!task) return;
    
    const wasCompleted = task.completed;
    const xpPoints = task.event.xpPoints || 0;
    
    // If uncompleting, check if we have enough unfed food
    if (wasCompleted && xpPoints > 0) {
      if (collectedFoodCount < xpPoints) {
        alert(`Du kan inte avmarkera denna syssla. Du behöver ${xpPoints} omatad mat, men du har bara ${collectedFoodCount}.`);
        return;
      }
    }
    
    // Optimistic update
    setTasks((prev) =>
      prev.map((t) =>
        t.event.id === eventId
          ? { ...t, completed: !t.completed }
          : t
      )
    );

    try {
      const deviceToken = localStorage.getItem("deviceToken");
      if (!deviceToken) {
        throw new Error("No device token");
      }
      const member = await getMemberByDeviceToken(deviceToken);
      await toggleTaskCompletion(eventId, member.id);
      
      // Reload data to get updated state (including food from backend)
      const [xpData, tasksData, petData, foodData] = await Promise.all([
        fetchCurrentXpProgress().catch(() => null),
        fetchTasksForToday(member.id).catch(() => []),
        fetchCurrentPet().catch(() => null),
        getCollectedFood().catch(() => ({ foodItems: [], totalCount: 0 })),
      ]);
      setXpProgress(xpData);
      setTasks(tasksData);
      setCollectedFoodCount(foodData.totalCount);
      if (petData) {
        setPet(petData);
        // Check if integrated image exists for updated pet
        const integratedExists = await checkIntegratedImageExists(petData.petType, petData.growthStage);
        setHasIntegratedImage(integratedExists);
      }
      
      // Don't change pet mood when toggling tasks - mood is only based on whether pet has been fed today
      // Pet mood will only change when actually feeding the pet
    } catch (e) {
      console.error("Error toggling task:", e);
      // Show user-friendly error message
      if (e instanceof Error && e.message.includes("inte tillräckligt")) {
        alert(e.message);
      }
      // Revert on error by reloading
      const deviceToken = localStorage.getItem("deviceToken");
      if (deviceToken) {
        const member = await getMemberByDeviceToken(deviceToken);
        const tasksData = await fetchTasksForToday(member.id).catch(() => []);
        setTasks(tasksData);
      }
    }
  };
  
  const handleFeed = async (amount: number | null = null) => {
    // amount = null means feed all, otherwise feed specific amount
    const feedAmount = amount ?? collectedFoodCount;
    
    if (feedAmount === 0 || isFeeding || !pet || feedAmount > collectedFoodCount) return;
    
    setIsFeeding(true);
    
    try {
      const deviceToken = localStorage.getItem("deviceToken");
      if (!deviceToken) {
        throw new Error("No device token");
      }
      const member = await getMemberByDeviceToken(deviceToken);
      
      // Award XP for food via feed endpoint (backend handles marking food as fed)
      await feedPet(feedAmount);
      
      // Show floating XP number
      setFloatingXp(feedAmount);
      
      // Reload collected food count and last fed date
      const [foodData, lastFedData] = await Promise.all([
        getCollectedFood().catch((e) => {
          console.error("Error fetching collected food after feeding:", e);
          return { foodItems: [], totalCount: 0 };
        }),
        getLastFedDate().catch((e) => {
          console.error("Error fetching last fed date after feeding:", e);
          // If we can't get the date, assume it was fed today (optimistic)
          return { lastFedAt: new Date().toISOString() };
        }),
      ]);
      setCollectedFoodCount(foodData.totalCount);
      
      // Update pet mood - pet is happy when fed!
      // Check if pet was fed today using backend data
      const wasFedToday = isDateToday(lastFedData.lastFedAt);
      if (wasFedToday) {
        setPetMood("happy");
        setPetMessage(getRandomPetMessage("happy"));
      } else {
        // This shouldn't happen after feeding, but handle it gracefully
        setPetMood("hungry");
        setPetMessage(getRandomPetMessage("hungry"));
      }
      
      // Reload XP progress to check for level up
      const xpData = await fetchCurrentXpProgress().catch(() => null);
      if (xpData) {
        const previousLevel = previousLevelRef.current;
        setXpProgress(xpData);
        
        // Check for level up
        if (xpData.currentLevel > previousLevel) {
          setShowConfetti(true);
          previousLevelRef.current = xpData.currentLevel;
          
          // Reload pet to get updated growth stage
          const petData = await fetchCurrentPet().catch(() => null);
          if (petData) {
            setPet(petData);
            const integratedExists = await checkIntegratedImageExists(petData.petType, petData.growthStage);
            setHasIntegratedImage(integratedExists);
          }
        }
      }
    } catch (e) {
      console.error("Error feeding pet:", e);
      if (e instanceof Error) {
        alert(e.message || "Kunde inte mata djuret. Försök igen.");
      }
    } finally {
      setTimeout(() => {
        setIsFeeding(false);
        setFloatingXp(null);
      }, 1500);
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
  // Clamp to 0-100 to ensure it doesn't exceed 100%
  const progressPercentage = xpProgress 
    ? xpProgress.currentLevel >= MAX_LEVEL
      ? 100
      : xpProgress.xpForNextLevel > 0
        ? Math.min(100, Math.max(0, (xpProgress.xpInCurrentLevel / (xpProgress.xpInCurrentLevel + xpProgress.xpForNextLevel)) * 100))
        : 0
    : 0;
  const totalEnergy = xpProgress?.currentXp || 0;
  const completedTasksCount = tasks.filter(t => t.completed).length;
  const totalTasksCount = tasks.length;
  const foodEmoji = pet ? getPetFoodEmoji(pet.petType) : "🍎";
  const foodName = pet ? getPetFoodName(pet.petType) : "mat";
  const totalFoodCount = collectedFoodCount;

  return (
    <div className="dashboard child-dashboard" style={{
      background: "linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)",
      minHeight: "100vh",
      padding: "20px",
      position: "relative",
    }}>
      {/* Confetti Animation */}
      {showConfetti && (
        <ConfettiAnimation
          onComplete={() => setShowConfetti(false)}
          duration={3000}
        />
      )}
      
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
          <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
            {onNavigate && (
              <button
                type="button"
                className="button-secondary"
                onClick={() => onNavigate("pethistory")}
                style={{ fontSize: "0.85rem", padding: "8px 16px" }}
              >
                🐾 Mina djur
              </button>
            )}
            <button
              type="button"
              className="button-secondary"
              onClick={handleLogout}
              style={{ fontSize: "0.85rem", padding: "8px 16px" }}
            >
              Logga ut
            </button>
          </div>
        </div>
      )}

      {/* Pet mood message - above image */}
      {petMessage && (
        <div style={{
          marginBottom: "16px",
          display: "flex",
          justifyContent: "center",
        }}>
          <div style={{
            background: petMood === "happy" ? "rgba(184, 230, 184, 0.95)" : "rgba(254, 202, 202, 0.95)",
            padding: "12px 20px",
            borderRadius: "16px",
            fontSize: "0.95rem",
            fontWeight: 600,
            color: petMood === "happy" ? "#2d5a2d" : "#c53030",
            maxWidth: "85%",
            textAlign: "center",
            boxShadow: "0 4px 12px rgba(0, 0, 0, 0.15)",
          }}>
            {petMessage}
          </div>
        </div>
      )}

      {/* Pet Visualization Card */}
      <section className="card" style={{
        padding: 0,
        marginBottom: "24px",
        borderRadius: "24px",
        boxShadow: "0 10px 25px rgba(0, 0, 0, 0.1)",
        overflow: "visible", // Changed from "hidden" to "visible" so progress ring is never clipped
        backgroundColor: "white",
        position: "relative",
      }}>
        {/* Image container with 3:2 aspect ratio (1440×960) */}
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
            aspectRatio: "3 / 2", // 1440×960 aspect ratio
            position: "relative",
            animation: showConfetti ? "pulseGradient 2s ease-in-out" : undefined,
            // Add padding-bottom to ensure progress ring always has space (if pet exists)
            paddingBottom: pet ? (windowWidth < 768 ? "40px" : "60px") : "0",
          }}
        >
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
          
          {/* Floating XP number */}
          {floatingXp !== null && (
            <FloatingXpNumber
              xp={floatingXp}
              onComplete={() => setFloatingXp(null)}
            />
          )}
          
          {/* Full circle progress bar at bottom - upper half goes up over image */}
          {/* Always show progress ring if we have a pet, even if xpProgress is null (will show 0 progress) */}
          {pet && (
            <div style={{
              position: "absolute",
              bottom: windowWidth < 768 ? "-40px" : "-60px", // Position so upper half is over image
              left: "50%",
              transform: "translateX(-50%)",
              width: "100%",
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
              zIndex: 10, // Ensure it's above other elements
            }}>
              <HalfCircleProgress
                progress={xpProgress ? progressPercentage : 0}
                currentLevel={xpProgress?.currentLevel || 1}
                mood={petMood}
                petName={pet.name || getPetNameSwedish(pet.petType)}
                size={windowWidth < 768 ? 100 : 140}
                strokeWidth={windowWidth < 768 ? 7 : 10}
              />
            </div>
          )}
        </div>
        
        {/* Text below image */}
        <div style={{
          padding: "20px",
          textAlign: "center",
          borderTop: "1px solid rgba(0, 0, 0, 0.1)",
          // Add margin-top to account for progress ring if pet exists
          marginTop: pet ? (windowWidth < 768 ? "20px" : "30px") : "0",
        }}>
          {/* Empty space - message moved to overlay */}
        </div>
      </section>

      {/* Food Collection & Feeding Section */}
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
          marginBottom: "16px",
        }}>
          <h3 style={{
            margin: 0,
            fontSize: "1.2rem",
            fontWeight: 600,
            color: "#2d3748",
          }}>
            🍽️ Mat att ge
          </h3>
          <span style={{
            fontSize: "1rem",
            fontWeight: 600,
            color: totalFoodCount > 0 ? "#48bb78" : "#4a5568",
          }}>
            {totalFoodCount} {foodName}
          </span>
        </div>
        
        {/* Food bowl visualization */}
        <div style={{
          background: "linear-gradient(135deg, #f7fafc 0%, #edf2f7 100%)",
          borderRadius: "16px",
          padding: "20px",
          marginBottom: "16px",
          minHeight: "80px",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          flexWrap: "wrap",
          gap: "8px",
          border: "2px solid #e2e8f0",
        }}>
          {totalFoodCount === 0 ? (
            <p style={{
              margin: 0,
              color: "#a0aec0",
              fontSize: "0.9rem",
              textAlign: "center",
            }}>
              Ingen mat ännu... Utför sysslor för att samla {foodName}!
            </p>
          ) : (
            Array.from({ length: Math.min(totalFoodCount, 20) }).map((_, i) => (
              <span
                key={i}
                style={{
                  fontSize: "1.5rem",
                  animation: i >= Math.max(0, totalFoodCount - 5) ? "bounce 0.5s ease" : undefined,
                }}
              >
                {foodEmoji}
              </span>
            ))
          )}
        </div>
        
        {/* Feed buttons */}
        <div style={{
          display: "flex",
          gap: "12px",
        }}>
          <button
            type="button"
            onClick={() => handleFeed(1)}
            disabled={totalFoodCount < 1 || isFeeding}
            style={{
              flex: 1,
              padding: "16px",
              fontSize: "1rem",
              fontWeight: 600,
              color: "white",
              background: totalFoodCount < 1 || isFeeding
                ? "#cbd5e0"
                : "linear-gradient(135deg, #48bb78 0%, #38a169 100%)",
              border: "none",
              borderRadius: "12px",
              cursor: totalFoodCount < 1 || isFeeding ? "not-allowed" : "pointer",
              transition: "all 0.2s ease",
              boxShadow: totalFoodCount < 1 || isFeeding
                ? "none"
                : "0 4px 12px rgba(72, 187, 120, 0.4)",
            }}
            onMouseEnter={(e) => {
              if (totalFoodCount >= 1 && !isFeeding) {
                e.currentTarget.style.transform = "scale(1.02)";
              }
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = "scale(1)";
            }}
          >
            {isFeeding ? "..." : `Mata 1 ${foodName}`}
          </button>
          <button
            type="button"
            onClick={() => handleFeed(null)}
            disabled={totalFoodCount === 0 || isFeeding}
            style={{
              flex: 1,
              padding: "16px",
              fontSize: "1rem",
              fontWeight: 600,
              color: "white",
              background: totalFoodCount === 0 || isFeeding
                ? "#cbd5e0"
                : "linear-gradient(135deg, #667eea 0%, #764ba2 100%)",
              border: "none",
              borderRadius: "12px",
              cursor: totalFoodCount === 0 || isFeeding ? "not-allowed" : "pointer",
              transition: "all 0.2s ease",
              boxShadow: totalFoodCount === 0 || isFeeding
                ? "none"
                : "0 4px 12px rgba(102, 126, 234, 0.4)",
            }}
            onMouseEnter={(e) => {
              if (totalFoodCount > 0 && !isFeeding) {
                e.currentTarget.style.transform = "scale(1.02)";
              }
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = "scale(1)";
            }}
          >
            {isFeeding ? "Ger mat..." : totalFoodCount > 0 ? `Mata allt (${totalFoodCount})` : "Ingen mat"}
          </button>
        </div>
        
        {xpProgress && xpProgress.currentLevel < MAX_LEVEL && (
          <p style={{
            margin: "12px 0 0",
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
            📝 Samla {foodName} för din vän
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
                        display: "flex",
                        alignItems: "center",
                        gap: "4px",
                      }}>
                        {task.completed && (
                          <>
                            {Array.from({ length: task.event.xpPoints }).map((_, i) => (
                              <span key={i} style={{ fontSize: "1rem" }}>{foodEmoji}</span>
                            ))}
                            <span style={{ marginLeft: "4px" }}>
                              +{task.event.xpPoints} {foodName}
                            </span>
                          </>
                        )}
                        {!task.completed && (
                          <span>
                            Ger {task.event.xpPoints} {foodName} när klar
                          </span>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </section>
      
      <style>{`
        @keyframes bounce {
          0%, 100% {
            transform: translateY(0);
          }
          50% {
            transform: translateY(-10px);
          }
        }
        
        @keyframes pulseGradient {
          0%, 100% {
            filter: brightness(1) saturate(1);
            box-shadow: 0 0 0 0 rgba(102, 126, 234, 0.7);
          }
          50% {
            filter: brightness(1.2) saturate(1.3);
            box-shadow: 0 0 30px 15px rgba(102, 126, 234, 0.5);
          }
        }
      `}</style>
    </div>
  );
}

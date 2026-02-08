import { useState, useEffect, useMemo, useRef, useCallback } from "react";
import { fetchTasksForToday, toggleTaskCompletion, CalendarTaskWithCompletionResponse, CalendarEventResponse, fetchCalendarEvents } from "../../shared/api/calendar";
import { getMemberByDeviceToken, fetchAllFamilyMembers } from "../../shared/api/familyMembers";
import { SimplifiedTaskForm } from "../calendar/components/SimplifiedTaskForm";
import { FamilyMemberResponse } from "../../shared/api/familyMembers";
import { formatDateTimeRange } from "../calendar/utils/dateFormatters";
import { CALENDAR_VIEW_TYPES } from "../calendar/constants";
import { fetchTodoLists, TodoListResponse, TODO_COLORS } from "../../shared/api/todos";
import { fetchCurrentPet, PetResponse, feedPet, getCollectedFood, getLastFedDate } from "../../shared/api/pets";
import { fetchCurrentXpProgress, XpProgressResponse } from "../../shared/api/xp";
import { PetVisualization } from "../pet/PetVisualization";
import { getIntegratedPetImagePath, getPetBackgroundImagePath, checkIntegratedImageExists, getPetNameSwedish, getPetNameSwedishLowercase } from "../pet/petImageUtils";
import { getPetFoodEmoji, getPetFoodName, getRandomPetMessage } from "../pet/petFoodUtils";
import { HalfCircleProgress } from "./components/HalfCircleProgress";
import { ConfettiAnimation } from "./components/ConfettiAnimation";
import { FloatingXpNumber } from "./components/FloatingXpNumber";
import { sortTasksByRequiredAndTitle } from "./utils/taskSorting";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers" | "childrenxp";

type AdultDashboardProps = {
  onNavigate?: (view: ViewKey) => void;
  familyId?: string | null;
  key?: string | number; // Allow key prop to force remount
};

type TabType = "calendar" | "todos" | "lists";

// Allowed family IDs for Spotify Charts link
const SPOTIFY_CHARTS_ALLOWED_FAMILIES = [
  "ce69194a-934d-4234-b046-dae7473700c0", // Production
  "cdd48859-74c5-4dee-989f-0b091f62d630", // Localhost
];

export function AdultDashboard({ onNavigate, familyId }: AdultDashboardProps) {
  const [activeTab, setActiveTab] = useState<TabType>("calendar");
  const showSpotifyLink = familyId && SPOTIFY_CHARTS_ALLOWED_FAMILIES.includes(familyId);
  
  // Att Göra-tab state
  const [tasks, setTasks] = useState<CalendarTaskWithCompletionResponse[]>([]);
  const [loadingTasks, setLoadingTasks] = useState(false);
  const [tasksError, setTasksError] = useState<string | null>(null);
  const [showQuickAdd, setShowQuickAdd] = useState(false);
  const [currentMember, setCurrentMember] = useState<FamilyMemberResponse | null>(null);
  const [members, setMembers] = useState<FamilyMemberResponse[]>([]);
  
  // Kalender-tab state
  const [calendarEvents, setCalendarEvents] = useState<CalendarEventResponse[]>([]);
  const [loadingCalendar, setLoadingCalendar] = useState(false);
  const [calendarError, setCalendarError] = useState<string | null>(null);
  const [calendarEndDate, setCalendarEndDate] = useState<Date | null>(null);
  const [showCalendarQuickAdd, setShowCalendarQuickAdd] = useState(false);
  const [isLoadingMoreEvents, setIsLoadingMoreEvents] = useState(false);
  const calendarScrollRef = useRef<HTMLDivElement>(null);
  const loadMoreTriggerRef = useRef<HTMLDivElement>(null);
  
  // Listor-tab state
  const [todoLists, setTodoLists] = useState<TodoListResponse[]>([]);
  const [loadingLists, setLoadingLists] = useState(false);
  const [listsError, setListsError] = useState<string | null>(null);
  
  // Pet state (for adults with pets enabled)
  const [pet, setPet] = useState<PetResponse | null>(null);
  const [xpProgress, setXpProgress] = useState<XpProgressResponse | null>(null);
  const [collectedFoodCount, setCollectedFoodCount] = useState<number>(0);
  const [isFeeding, setIsFeeding] = useState(false);
  const [showConfetti, setShowConfetti] = useState(false);
  const [floatingXp, setFloatingXp] = useState<number | null>(null);
  const [petMood, setPetMood] = useState<"happy" | "hungry">("happy");
  const [petMessage, setPetMessage] = useState<string>("");
  const [hasIntegratedImage, setHasIntegratedImage] = useState<boolean>(false);
  const [windowWidth, setWindowWidth] = useState<number>(typeof window !== "undefined" ? window.innerWidth : 1024);
  const previousLevelRef = useRef<number>(0);

  // Track window width for responsive design
  useEffect(() => {
    const handleResize = () => {
      setWindowWidth(window.innerWidth);
    };
    
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  // Utility function to get today's date in local timezone (YYYY-MM-DD format)
  const getTodayLocalDateString = (): string => {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };
  
  // Check if a date string (ISO 8601) is today in local timezone
  // Made stable with useCallback to avoid dependency issues in loadPetData
  const isDateToday = useCallback((dateString: string | null): boolean => {
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
  }, []); // getTodayLocalDateString is stable (no dependencies)

  // Function to load pet data (can be called after egg selection)
  const loadPetData = useCallback(async (member: FamilyMemberResponse) => {
    // Only load pet data if member is PARENT and has pets explicitly enabled
    if (member.role !== "PARENT" || member.petEnabled !== true) {
      // Clear pet state if pet is not enabled
      setPet(null);
      setXpProgress(null);
      setCollectedFoodCount(0);
      setPetMood("happy");
      setPetMessage("");
      setHasIntegratedImage(false);
      return;
    }
    
    try {
      const [petData, xpData, foodData, lastFedData] = await Promise.all([
        fetchCurrentPet().catch(() => null),
        fetchCurrentXpProgress().catch(() => null),
        getCollectedFood().catch(() => ({ foodItems: [], totalCount: 0 })),
        getLastFedDate().catch(() => ({ lastFedAt: null })),
      ]);
      
      if (petData) {
        setPet(petData);
        setXpProgress(xpData);
        setCollectedFoodCount(foodData.totalCount);
        
        if (xpData) {
          previousLevelRef.current = xpData.currentLevel;
        }
        
        // Check if integrated image exists
        const integratedExists = await checkIntegratedImageExists(petData.petType, petData.growthStage);
        setHasIntegratedImage(integratedExists);
        
        // Check pet mood
        const wasFedToday = isDateToday(lastFedData.lastFedAt);
        if (wasFedToday) {
          setPetMood("happy");
          setPetMessage(getRandomPetMessage("happy"));
        } else {
          setPetMood("hungry");
          setPetMessage(getRandomPetMessage("hungry"));
        }
      } else {
        // No pet - clear state
        setPet(null);
        setXpProgress(null);
        setCollectedFoodCount(0);
      }
    } catch (e) {
      // Error loading pet data, but that's okay - adult might not have pet
      if (e instanceof Error && !e.message.includes("404") && !e.message.includes("No pet")) {
        console.error("Error loading pet for adult:", e);
      }
    }
  }, [isDateToday]); // Include isDateToday in dependencies

  // Load current member, all family members, and pet (if adult has pet)
  useEffect(() => {
    const loadMember = async () => {
      try {
        const deviceToken = localStorage.getItem("deviceToken");
        if (!deviceToken) return;
        
        // Always reload member to get latest petEnabled status
        const member = await getMemberByDeviceToken(deviceToken);
        setCurrentMember(member);
        
        // Load all members for SimplifiedTaskForm
        const allMembers = await fetchAllFamilyMembers();
        setMembers(allMembers);
        
        // Load pet data for adult (will check petEnabled inside)
        await loadPetData(member);
      } catch (e) {
        console.error("Error loading member:", e);
      }
    };
    
    void loadMember();
  }, [loadPetData]);
  
  // Clear pet state if petEnabled becomes false
  useEffect(() => {
    if (currentMember?.role === "PARENT" && currentMember?.petEnabled !== true) {
      // Clear all pet-related state if pet is not enabled
      setPet(null);
      setXpProgress(null);
      setCollectedFoodCount(0);
      setPetMood("happy");
      setPetMessage("");
      setHasIntegratedImage(false);
    }
  }, [currentMember?.petEnabled]);

  // Reload member and pet data when returning from other views (focus or visibility change)
  useEffect(() => {
    const reloadMemberData = async () => {
      try {
        const deviceToken = localStorage.getItem("deviceToken");
        if (!deviceToken) return;
        
        // Reload member to get updated petEnabled status
        const member = await getMemberByDeviceToken(deviceToken);
        setCurrentMember(member);
        
        // Reload pet data if pet is enabled
        await loadPetData(member);
      } catch (e) {
        console.error("Error reloading member data:", e);
      }
    };
    
    const handleFocus = () => {
      void reloadMemberData();
    };
    
    const handleVisibilityChange = () => {
      if (!document.hidden) {
        void reloadMemberData();
      }
    };
    
    window.addEventListener("focus", handleFocus);
    document.addEventListener("visibilitychange", handleVisibilityChange);
    
    return () => {
      window.removeEventListener("focus", handleFocus);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [loadPetData]);

  // Load tasks when Att Göra-tab is active
  useEffect(() => {
    if (activeTab === "todos" && currentMember) {
      const loadTasks = async () => {
        try {
          setLoadingTasks(true);
          setTasksError(null);
          const tasksData = await fetchTasksForToday(currentMember.id);
          // Sort: required first, then by title
          const sortedTasks = sortTasksByRequiredAndTitle(tasksData);
          setTasks(sortedTasks);
        } catch (e) {
          console.error("Error loading tasks:", e);
          setTasksError("Kunde inte ladda tasks. Försök igen.");
        } finally {
          setLoadingTasks(false);
        }
      };
      
      void loadTasks();
    }
  }, [activeTab, currentMember]);

  // Load calendar events when Kalender-tab is active
  useEffect(() => {
    if (activeTab === "calendar") {
      const loadCalendar = async () => {
        try {
          setLoadingCalendar(true);
          setCalendarError(null);
          const now = new Date();
          const startDate = new Date(now);
          startDate.setHours(0, 0, 0, 0);
          
          const endDate = new Date(now);
          endDate.setDate(endDate.getDate() + 30); // Load 30 days ahead
          endDate.setHours(23, 59, 59, 999);
          
          const eventsData = await fetchCalendarEvents(startDate, endDate);
          setCalendarEvents(eventsData);
          setCalendarEndDate(endDate);
        } catch (e) {
          console.error("Error loading calendar:", e);
          setCalendarError("Kunde inte ladda kalender. Försök igen.");
        } finally {
          setLoadingCalendar(false);
        }
      };
      
      void loadCalendar();
    }
  }, [activeTab]);

  // Load todo lists when Listor-tab is active
  useEffect(() => {
    if (activeTab === "lists") {
      const loadLists = async () => {
        try {
          setLoadingLists(true);
          setListsError(null);
          const listsData = await fetchTodoLists();
          setTodoLists(listsData);
        } catch (e) {
          console.error("Error loading todo lists:", e);
          setListsError("Kunde inte ladda listor. Försök igen.");
        } finally {
          setLoadingLists(false);
        }
      };
      
      void loadLists();
    }
  }, [activeTab]);

  // Helper function to get all dates for a multi-day all-day event
  const getAllDayEventDates = (event: CalendarEventResponse): string[] => {
    if (!event.isAllDay || !event.endDateTime) return [];
    
    const start = new Date(event.startDateTime);
    const end = new Date(event.endDateTime);
    const dates: string[] = [];
    
    const current = new Date(start);
    while (current <= end) {
      dates.push(current.toISOString().split("T")[0]);
      current.setDate(current.getDate() + 1);
    }
    
    return dates;
  };

  // Group events by date for calendar view
  const eventsByDate = useMemo(() => {
    const now = new Date();
    const todayStr = now.toISOString().split("T")[0];
    
    // Filter to only future events (today or later)
    const futureEvents = calendarEvents.filter(event => {
      if (event.isAllDay) {
        const dates = getAllDayEventDates(event);
        return dates.some(dateStr => dateStr >= todayStr);
      }
      return new Date(event.startDateTime) >= now;
    });
    
    // Group by date
    return futureEvents.reduce((acc, event) => {
      if (event.isAllDay) {
        const dates = getAllDayEventDates(event);
        dates.forEach(dateKey => {
          if (dateKey >= todayStr) {
            if (!acc[dateKey]) {
              acc[dateKey] = [];
            }
            acc[dateKey].push(event);
          }
        });
      } else {
        const date = new Date(event.startDateTime);
        const dateKey = date.toISOString().split("T")[0];
        if (dateKey >= todayStr) {
          if (!acc[dateKey]) {
            acc[dateKey] = [];
          }
          acc[dateKey].push(event);
        }
      }
      return acc;
    }, {} as Record<string, CalendarEventResponse[]>);
  }, [calendarEvents]);

  const sortedDates = useMemo(() => {
    return Object.keys(eventsByDate).sort();
  }, [eventsByDate]);

  // Load more events when scrolling to bottom
  const loadMoreEvents = useCallback(async () => {
    if (isLoadingMoreEvents || !calendarEndDate) return;
    
    setIsLoadingMoreEvents(true);
    try {
      const newEndDate = new Date(calendarEndDate);
      newEndDate.setDate(newEndDate.getDate() + 30); // Load 30 more days
      newEndDate.setHours(23, 59, 59, 999);
      
      // Fetch only new events (after current end date)
      const fetchStartDate = new Date(calendarEndDate);
      fetchStartDate.setDate(fetchStartDate.getDate() + 1);
      fetchStartDate.setHours(0, 0, 0, 0);
      
      const newEvents = await fetchCalendarEvents(fetchStartDate, newEndDate);
      
      // Merge with existing events, avoiding duplicates
      setCalendarEvents(prev => {
        const existingKeys = new Set(prev.map(e => `${e.id}:${e.startDateTime}`));
        const uniqueNewEvents = newEvents.filter(
          e => !existingKeys.has(`${e.id}:${e.startDateTime}`)
        );
        return [...prev, ...uniqueNewEvents].sort((a, b) => 
          new Date(a.startDateTime).getTime() - new Date(b.startDateTime).getTime()
        );
      });
      
      setCalendarEndDate(newEndDate);
    } catch (e) {
      console.error("Error loading more events:", e);
    } finally {
      setIsLoadingMoreEvents(false);
    }
  }, [calendarEndDate, isLoadingMoreEvents]);

  // Set up intersection observer for infinite scroll
  useEffect(() => {
    if (activeTab !== "calendar" || !loadMoreTriggerRef.current) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (entry.isIntersecting && !isLoadingMoreEvents) {
          void loadMoreEvents();
        }
      },
      { root: null, rootMargin: "100px", threshold: 0.1 }
    );

    observer.observe(loadMoreTriggerRef.current);

    return () => {
      observer.disconnect();
    };
  }, [activeTab, isLoadingMoreEvents, loadMoreEvents]);

  const handleToggleTask = async (eventId: string) => {
    if (!currentMember) return;
    
    // Optimistic update
    setTasks((prev) =>
      prev.map((t) =>
        t.event.id === eventId
          ? { ...t, completed: !t.completed }
          : t
      )
    );

    try {
      await toggleTaskCompletion(eventId, currentMember.id);
      // Reload tasks
      const tasksData = await fetchTasksForToday(currentMember.id).catch(() => []);
      
      const sortedTasks = sortTasksByRequiredAndTitle(tasksData);
      setTasks(sortedTasks);
      
      // Update pet-related data if adult has pet enabled
      if (currentMember.petEnabled === true) {
        try {
          const [xpData, petData, foodData] = await Promise.all([
            fetchCurrentXpProgress().catch(() => null),
            fetchCurrentPet().catch(() => null),
            getCollectedFood().catch(() => ({ foodItems: [], totalCount: 0 })),
          ]);
          
          // Update pet-related data if pet exists
          if (petData) {
            setPet(petData);
            if (xpData) {
              const previousLevel = previousLevelRef.current;
              setXpProgress(xpData);
              
              if (xpData.currentLevel > previousLevel) {
                setShowConfetti(true);
                previousLevelRef.current = xpData.currentLevel;
                
                const integratedExists = await checkIntegratedImageExists(petData.petType, petData.growthStage);
                setHasIntegratedImage(integratedExists);
              }
            }
            setCollectedFoodCount(foodData.totalCount);
          }
        } catch (e) {
          // Silently ignore pet-related errors (404 is expected if no pet)
          console.debug("Pet data update skipped:", e);
        }
      }
    } catch (e) {
      console.error("Error toggling task:", e);
      // Reload on error to revert
      const tasksData = await fetchTasksForToday(currentMember.id);
      const sortedTasks = sortTasksByRequiredAndTitle(tasksData);
      setTasks(sortedTasks);
    }
  };

  const handleQuickAddSave = async () => {
    // Reload tasks after adding
    if (currentMember) {
      const tasksData = await fetchTasksForToday(currentMember.id);
      const sortedTasks = sortTasksByRequiredAndTitle(tasksData);
      setTasks(sortedTasks);
    }
    setShowQuickAdd(false);
  };

  const handleFeed = async (amount: number | null = null) => {
    if (!pet || !currentMember || currentMember.petEnabled !== true) return;
    const feedAmount = amount ?? collectedFoodCount;
    
    if (feedAmount === 0 || isFeeding || feedAmount > collectedFoodCount) return;
    
    setIsFeeding(true);
    
    try {
      await feedPet(feedAmount);
      setFloatingXp(feedAmount);
      
      const [foodData, lastFedData, xpData] = await Promise.all([
        getCollectedFood().catch(() => ({ foodItems: [], totalCount: 0 })),
        getLastFedDate().catch(() => ({ lastFedAt: null })),
        fetchCurrentXpProgress().catch(() => null),
      ]);
      
      setCollectedFoodCount(foodData.totalCount);
      
      const wasFedToday = isDateToday(lastFedData.lastFedAt);
      if (wasFedToday) {
        setPetMood("happy");
        setPetMessage(getRandomPetMessage("happy"));
      }
      
      if (xpData) {
        const previousLevel = previousLevelRef.current;
        setXpProgress(xpData);
        
        if (xpData.currentLevel > previousLevel) {
          setShowConfetti(true);
          previousLevelRef.current = xpData.currentLevel;
          
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
      alert("Kunde inte mata djuret. Försök igen.");
    } finally {
      setTimeout(() => {
        setIsFeeding(false);
        setFloatingXp(null);
      }, 1500);
    }
  };

  const MAX_LEVEL = 5;
  const progressPercentage = xpProgress 
    ? xpProgress.currentLevel >= MAX_LEVEL
      ? 100
      : xpProgress.xpForNextLevel > 0
        ? Math.min(100, Math.max(0, (xpProgress.xpInCurrentLevel / (xpProgress.xpInCurrentLevel + xpProgress.xpForNextLevel)) * 100))
        : 0
    : 0;
  const foodEmoji = pet ? getPetFoodEmoji(pet.petType) : "🍎";
  const foodName = pet ? getPetFoodName(pet.petType) : "mat";

  // Determine if pet section should be shown
  // Only show if: user is PARENT AND petEnabled is explicitly true
  const shouldShowPetSection = Boolean(
    currentMember?.role === "PARENT" && 
    currentMember?.petEnabled === true
  );
  const hasPet = pet !== null;

  return (
    <div className="dashboard adult-dashboard">
      {/* Pet Section (if adult has pets enabled and has pet) OR "Välj ägg" button (if enabled but no pet) */}
      {shouldShowPetSection && hasPet ? (
        <>
          {/* Confetti Animation */}
          {showConfetti && (
            <ConfettiAnimation
              onComplete={() => setShowConfetti(false)}
              duration={3000}
            />
          )}
          
          {/* Pet mood message */}
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
            overflow: "visible",
            backgroundColor: "white",
            position: "relative",
          }}>
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
                paddingBottom: windowWidth < 768 ? "40px" : "60px",
              }}
            >
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
              
              {floatingXp !== null && (
                <FloatingXpNumber
                  xp={floatingXp}
                  onComplete={() => setFloatingXp(null)}
                />
              )}
              
              {pet && (
                <div style={{
                  position: "absolute",
                  bottom: windowWidth < 768 ? "-40px" : "-60px",
                  left: "50%",
                  transform: "translateX(-50%)",
                  width: "100%",
                  display: "flex",
                  justifyContent: "center",
                  alignItems: "center",
                  zIndex: 10,
                }}>
                  <HalfCircleProgress
                    progress={progressPercentage}
                    currentLevel={xpProgress?.currentLevel || 1}
                    mood={petMood}
                    petName={pet.name || getPetNameSwedish(pet.petType)}
                    size={windowWidth < 768 ? 100 : 140}
                    strokeWidth={windowWidth < 768 ? 7 : 10}
                  />
                </div>
              )}
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
                color: collectedFoodCount > 0 ? "#48bb78" : "#4a5568",
              }}>
                {collectedFoodCount} {foodName}
              </span>
            </div>
            
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
              {collectedFoodCount === 0 ? (
                <p style={{
                  margin: 0,
                  color: "#a0aec0",
                  fontSize: "0.9rem",
                  textAlign: "center",
                }}>
                  Ingen mat ännu... Utför sysslor för att samla {foodName}!
                </p>
              ) : (
                Array.from({ length: Math.min(collectedFoodCount, 20) }).map((_, i) => (
                  <span
                    key={i}
                    style={{
                      fontSize: "1.5rem",
                    }}
                  >
                    {foodEmoji}
                  </span>
                ))
              )}
            </div>
            
            <div style={{
              display: "flex",
              gap: "12px",
            }}>
              <button
                type="button"
                onClick={() => handleFeed(1)}
                disabled={collectedFoodCount < 1 || isFeeding}
                style={{
                  flex: 1,
                  padding: "16px",
                  fontSize: "1rem",
                  fontWeight: 600,
                  color: "white",
                  background: collectedFoodCount < 1 || isFeeding
                    ? "#cbd5e0"
                    : "linear-gradient(135deg, #48bb78 0%, #38a169 100%)",
                  border: "none",
                  borderRadius: "12px",
                  cursor: collectedFoodCount < 1 || isFeeding ? "not-allowed" : "pointer",
                  transition: "all 0.2s ease",
                  boxShadow: collectedFoodCount < 1 || isFeeding
                    ? "none"
                    : "0 4px 12px rgba(72, 187, 120, 0.4)",
                }}
              >
                {isFeeding ? "..." : `Mata 1 ${foodName}`}
              </button>
              <button
                type="button"
                onClick={() => handleFeed(null)}
                disabled={collectedFoodCount === 0 || isFeeding}
                style={{
                  flex: 1,
                  padding: "16px",
                  fontSize: "1rem",
                  fontWeight: 600,
                  color: "white",
                  background: collectedFoodCount === 0 || isFeeding
                    ? "#cbd5e0"
                    : "linear-gradient(135deg, #667eea 0%, #764ba2 100%)",
                  border: "none",
                  borderRadius: "12px",
                  cursor: collectedFoodCount === 0 || isFeeding ? "not-allowed" : "pointer",
                  transition: "all 0.2s ease",
                  boxShadow: collectedFoodCount === 0 || isFeeding
                    ? "none"
                    : "0 4px 12px rgba(102, 126, 234, 0.4)",
                }}
              >
                {isFeeding ? "Ger mat..." : collectedFoodCount > 0 ? `Mata allt (${collectedFoodCount})` : "Ingen mat"}
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
        </>
      ) : shouldShowPetSection && !hasPet ? (
        // Show "Välj ägg" button if adult doesn't have pet
        <section className="card" style={{
          background: "white",
          borderRadius: "20px",
          padding: "24px",
          marginBottom: "24px",
          boxShadow: "0 4px 6px rgba(0, 0, 0, 0.1)",
          textAlign: "center",
        }}>
          <div style={{ marginBottom: "20px" }}>
            <div style={{ fontSize: "4rem", marginBottom: "12px" }}>🥚</div>
            <h3 style={{
              margin: "0 0 8px",
              fontSize: "1.2rem",
              fontWeight: 600,
              color: "#2d3748",
            }}>
              Välj ett djur!
            </h3>
            <p style={{
              margin: 0,
              fontSize: "0.9rem",
              color: "#718096",
            }}>
              Vuxna kan också ha djur och samla XP genom att göra sysslor.
            </p>
          </div>
          <button
            type="button"
            className="button-primary"
            onClick={() => onNavigate?.("eggselection")}
            style={{
              padding: "16px 32px",
              fontSize: "1rem",
              fontWeight: 600,
            }}
          >
            Välj ägg →
          </button>
        </section>
      ) : null}

      {/* Tab Navigation */}
      <div style={{
        display: "flex",
        borderBottom: "2px solid #e2e8f0",
        marginBottom: "20px",
        gap: "8px",
      }}>
        <button
          type="button"
          onClick={() => setActiveTab("calendar")}
          style={{
            flex: 1,
            padding: "12px 16px",
            border: "none",
            background: "transparent",
            color: activeTab === "calendar" ? "#2d3748" : "#718096",
            fontWeight: activeTab === "calendar" ? 600 : 400,
            fontSize: "1rem",
            cursor: "pointer",
            borderBottom: activeTab === "calendar" ? "3px solid #4299e1" : "3px solid transparent",
            transition: "all 0.2s ease",
          }}
          aria-label="Kalender"
          aria-pressed={activeTab === "calendar"}
        >
          Kalender
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("todos")}
          style={{
            flex: 1,
            padding: "12px 16px",
            border: "none",
            background: "transparent",
            color: activeTab === "todos" ? "#2d3748" : "#718096",
            fontWeight: activeTab === "todos" ? 600 : 400,
            fontSize: "1rem",
            cursor: "pointer",
            borderBottom: activeTab === "todos" ? "3px solid #4299e1" : "3px solid transparent",
            transition: "all 0.2s ease",
          }}
          aria-label="Att Göra"
          aria-pressed={activeTab === "todos"}
        >
          Att Göra
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("lists")}
          style={{
            flex: 1,
            padding: "12px 16px",
            border: "none",
            background: "transparent",
            color: activeTab === "lists" ? "#2d3748" : "#718096",
            fontWeight: activeTab === "lists" ? 600 : 400,
            fontSize: "1rem",
            cursor: "pointer",
            borderBottom: activeTab === "lists" ? "3px solid #4299e1" : "3px solid transparent",
            transition: "all 0.2s ease",
          }}
          aria-label="Listor"
          aria-pressed={activeTab === "lists"}
        >
          Listor
        </button>
      </div>

      {/* Tab Content */}
      <div style={{ minHeight: "400px" }}>
        {activeTab === "calendar" && (
          <div>
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
                  Kalender
                </h3>
                {currentMember && (
                  <button
                    type="button"
                    className="button-primary"
                    onClick={() => setShowCalendarQuickAdd(true)}
                    style={{ fontSize: "0.9rem", padding: "8px 16px" }}
                  >
                    + Lägg till task
                  </button>
                )}
              </div>

              {loadingCalendar ? (
                <p style={{ textAlign: "center", color: "#718096" }}>Laddar...</p>
              ) : calendarError ? (
                <div style={{ textAlign: "center" }}>
                  <p className="error-text" style={{ margin: "0 0 12px" }}>{calendarError}</p>
                  <button
                    type="button"
                    className="button-primary"
                    onClick={() => {
                      const now = new Date();
                      const startDate = new Date(now);
                      startDate.setHours(0, 0, 0, 0);
                      const endDate = new Date(now);
                      endDate.setDate(endDate.getDate() + 30);
                      endDate.setHours(23, 59, 59, 999);
                      setLoadingCalendar(true);
                      setCalendarError(null);
                      fetchCalendarEvents(startDate, endDate)
                        .then(data => {
                          setCalendarEvents(data);
                          setCalendarEndDate(endDate);
                        })
                        .catch(e => {
                          console.error("Error reloading calendar:", e);
                          setCalendarError("Kunde inte ladda kalender. Försök igen.");
                        })
                        .finally(() => setLoadingCalendar(false));
                    }}
                    style={{ fontSize: "0.9rem", padding: "8px 16px" }}
                  >
                    Försök igen
                  </button>
                </div>
              ) : sortedDates.length === 0 ? (
                <p className="placeholder-text" style={{ margin: 0, textAlign: "center" }}>
                  Inga kommande events. Skapa ditt första event i den fullständiga kalendern!
                </p>
              ) : (
                <div 
                  ref={calendarScrollRef}
                  style={{ display: "flex", flexDirection: "column", gap: "20px", maxHeight: "600px", overflowY: "auto" }}
                >
                  {sortedDates.map((dateKey) => {
                    const date = new Date(dateKey + "T00:00:00");
                    const isToday = dateKey === new Date().toISOString().split("T")[0];
                    const dateEvents = eventsByDate[dateKey];
                    
                    return (
                      <div key={dateKey}>
                        <h4 style={{
                          margin: "0 0 12px",
                          fontSize: "1rem",
                          fontWeight: 600,
                          color: isToday ? "#4299e1" : "#2d3748",
                        }}>
                          {isToday ? "Idag" : date.toLocaleDateString("sv-SE", {
                            weekday: "long",
                            year: "numeric",
                            month: "long",
                            day: "numeric",
                          })}
                        </h4>
                        <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                          {dateEvents.map((event) => {
                            const categoryColor = event.category?.color || "#e2e8f0";
                            
                            return (
                              <div
                                key={`${event.id}-${event.startDateTime}`}
                                style={{
                                  padding: "12px",
                                  background: event.isTask ? "#fff7ed" : "#f7fafc",
                                  borderRadius: "8px",
                                  border: `2px solid ${event.isTask ? "#fed7aa" : categoryColor}`,
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
                                onClick={() => onNavigate?.("schedule")}
                              >
                                <div style={{
                                  fontWeight: 600,
                                  color: "#2d3748",
                                  marginBottom: "4px",
                                }}>
                                  {event.title}
                                </div>
                                <div style={{
                                  fontSize: "0.85rem",
                                  color: "#718096",
                                }}>
                                  {formatDateTimeRange(event.startDateTime, event.endDateTime, event.isAllDay)}
                                </div>
                                {event.isTask && (
                                  <div style={{
                                    fontSize: "0.85rem",
                                    color: "#718096",
                                    marginTop: "4px",
                                  }}>
                                    {event.xpPoints ? `${event.xpPoints} XP` : "Uppgift"}
                                  </div>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    );
                  })}
                  
                  {/* Load more trigger */}
                  <div ref={loadMoreTriggerRef} style={{ height: "20px", display: "flex", justifyContent: "center", alignItems: "center", padding: "20px 0" }}>
                    {isLoadingMoreEvents && (
                      <p style={{ color: "#718096", fontSize: "0.9rem" }}>Laddar fler events...</p>
                    )}
                  </div>
                </div>
              )}
            </section>

            {showCalendarQuickAdd && currentMember && (
              <SimplifiedTaskForm
                members={members}
                currentUserId={currentMember.id}
                onSave={async () => {
                  // Reload calendar after adding
                  const now = new Date();
                  const startDate = new Date(now);
                  startDate.setHours(0, 0, 0, 0);
                  
                  const endDate = calendarEndDate || new Date(now);
                  endDate.setDate(endDate.getDate() + 30);
                  endDate.setHours(23, 59, 59, 999);
                  
                  const eventsData = await fetchCalendarEvents(startDate, endDate);
                  setCalendarEvents(eventsData);
                  setCalendarEndDate(endDate);
                  setShowCalendarQuickAdd(false);
                }}
                onCancel={() => setShowCalendarQuickAdd(false)}
              />
            )}
          </div>
        )}
        
        {activeTab === "todos" && (
          <div>
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
                <div>
                  <h3 style={{
                    margin: 0,
                    fontSize: "1.2rem",
                    fontWeight: 600,
                    color: "#2d3748",
                  }}>
                    Dagens Att Göra
                  </h3>
                  <p style={{
                    margin: "4px 0 0",
                    fontSize: "0.9rem",
                    color: "#718096",
                  }}>
                    {new Date().toLocaleDateString("sv-SE", {
                      weekday: "long",
                      year: "numeric",
                      month: "long",
                      day: "numeric",
                    })}
                  </p>
                </div>
                <button
                  type="button"
                  className="button-primary"
                  onClick={() => setShowQuickAdd(true)}
                  style={{ fontSize: "0.9rem", padding: "8px 16px" }}
                >
                  + Add
                </button>
              </div>

              {loadingTasks ? (
                <p style={{ textAlign: "center", color: "#718096" }}>Laddar...</p>
              ) : tasksError ? (
                <div style={{ textAlign: "center" }}>
                  <p className="error-text" style={{ margin: "0 0 12px" }}>{tasksError}</p>
                  <button
                    type="button"
                    className="button-primary"
                    onClick={() => {
                      if (currentMember) {
                        setLoadingTasks(true);
                        setTasksError(null);
                        fetchTasksForToday(currentMember.id)
                          .then(data => {
                            const sortedTasks = sortTasksByRequiredAndTitle(data);
                            setTasks(sortedTasks);
                          })
                          .catch(e => {
                            console.error("Error reloading tasks:", e);
                            setTasksError("Kunde inte ladda tasks. Försök igen.");
                          })
                          .finally(() => setLoadingTasks(false));
                      }
                    }}
                    style={{ fontSize: "0.9rem", padding: "8px 16px" }}
                  >
                    Försök igen
                  </button>
                </div>
              ) : tasks.length === 0 ? (
                <p className="placeholder-text" style={{ margin: 0, textAlign: "center" }}>
                  Inga dagens att göra. Skapa ditt första task!
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
                            color: "#2d3748",
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
                              {task.event.xpPoints} XP
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </section>

            {showQuickAdd && currentMember && (
              <SimplifiedTaskForm
                members={members}
                currentUserId={currentMember.id}
                onSave={handleQuickAddSave}
                onCancel={() => setShowQuickAdd(false)}
              />
            )}
          </div>
        )}
        
        {activeTab === "lists" && (
          <div>
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
                  Listor
                </h3>
                <button
                  type="button"
                  className="button-primary"
                  onClick={() => onNavigate?.("todos")}
                  style={{ fontSize: "0.9rem", padding: "8px 16px" }}
                >
                  Öppna alla listor →
                </button>
              </div>

              {loadingLists ? (
                <p style={{ textAlign: "center", color: "#718096" }}>Laddar...</p>
              ) : listsError ? (
                <div style={{ textAlign: "center" }}>
                  <p className="error-text" style={{ margin: "0 0 12px" }}>{listsError}</p>
                  <button
                    type="button"
                    className="button-primary"
                    onClick={() => {
                      setLoadingLists(true);
                      setListsError(null);
                      fetchTodoLists()
                        .then(data => setTodoLists(data))
                        .catch(e => {
                          console.error("Error reloading lists:", e);
                          setListsError("Kunde inte ladda listor. Försök igen.");
                        })
                        .finally(() => setLoadingLists(false));
                    }}
                    style={{ fontSize: "0.9rem", padding: "8px 16px" }}
                  >
                    Försök igen
                  </button>
                </div>
              ) : todoLists.length === 0 ? (
                <p className="placeholder-text" style={{ margin: 0, textAlign: "center" }}>
                  Inga listor ännu. Skapa din första lista!
                </p>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
                  {todoLists.map((list) => {
                    const undoneItems = list.items.filter(item => !item.done);
                    const undoneCount = undoneItems.length;
                    const totalCount = list.items.length;
                    
                    const colorConfig = TODO_COLORS.find(c => c.value === list.color) || TODO_COLORS[0];
                    
                    return (
                      <div
                        key={list.id}
                        onClick={() => onNavigate?.("todos")}
                        style={{
                          padding: "16px",
                          background: colorConfig.gradient,
                          borderRadius: "12px",
                          border: `2px solid ${colorConfig.border}`,
                          color: "#2d5a2d",
                          cursor: "pointer",
                          transition: "all 0.2s ease",
                          boxShadow: `0 1px 3px ${colorConfig.border}30`,
                        }}
                        onMouseEnter={(e) => {
                          e.currentTarget.style.transform = "scale(1.02)";
                          e.currentTarget.style.boxShadow = `0 2px 8px ${colorConfig.border}40`;
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.transform = "scale(1)";
                          e.currentTarget.style.boxShadow = `0 1px 3px ${colorConfig.border}30`;
                        }}
                      >
                        <div style={{
                          display: "flex",
                          justifyContent: "space-between",
                          alignItems: "center",
                        }}>
                          <div style={{ flex: 1 }}>
                            <div style={{
                              fontWeight: 600,
                              color: "#2d5a2d",
                              marginBottom: "4px",
                            }}>
                              {list.name}
                            </div>
                            <div style={{
                              fontSize: "0.85rem",
                              color: "#2d5a2d",
                              opacity: 0.8,
                            }}>
                              {undoneCount === 0 
                                ? totalCount === 0 
                                  ? "Inga items" 
                                  : "Alla klara ✓"
                                : `${undoneCount} oklara ${undoneCount === 1 ? "item" : "items"}`
                              }
                            </div>
                          </div>
                          {list.isPrivate && (
                            <span style={{
                              fontSize: "0.75rem",
                              color: "#718096",
                              marginLeft: "8px",
                            }}>
                              🔒
                            </span>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </section>
          </div>
        )}
      </div>

      {/* Spotify Charts Link - Only for specific families */}
      {showSpotifyLink && (
        <div style={{
          position: "fixed",
          bottom: "20px",
          right: "20px",
          zIndex: 1000,
        }}>
          <a
            href="https://spotify-charts-production.up.railway.app/"
            target="_blank"
            rel="noopener noreferrer"
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              width: "56px",
              height: "56px",
              borderRadius: "50%",
              background: "#1DB954", // Spotify green
              color: "white",
              fontSize: "24px",
              textDecoration: "none",
              boxShadow: "0 4px 12px rgba(29, 185, 84, 0.4)",
              transition: "all 0.2s ease",
              cursor: "pointer",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = "scale(1.1)";
              e.currentTarget.style.boxShadow = "0 6px 16px rgba(29, 185, 84, 0.6)";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = "scale(1)";
              e.currentTarget.style.boxShadow = "0 4px 12px rgba(29, 185, 84, 0.4)";
            }}
            title="Spotify Charts"
            aria-label="Öppna Spotify Charts"
          >
            🎵
          </a>
        </div>
      )}
    </div>
  );
}

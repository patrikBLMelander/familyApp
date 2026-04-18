import { useState, useEffect, useRef, useCallback } from "react";
import { getDailyChoresForDate, createDailyChore } from "../../shared/api/dailyChores";
import { getMemberByDeviceToken, fetchAllFamilyMembers, createFamilyMember, generateInviteToken } from "../../shared/api/familyMembers";
import { QRCodeSVG } from "qrcode.react";
import { FamilyMemberResponse } from "../../shared/api/familyMembers";
import { AGE_GROUPS, TASK_SUGGESTIONS, AgeGroup } from "../familymembers/taskSuggestions";
import { fetchCurrentPet, PetResponse, feedPet, getCollectedFood, getLastFedDate, fetchMemberPet } from "../../shared/api/pets";
import { fetchCurrentXpProgress, XpProgressResponse } from "../../shared/api/xp";
import { PetVisualization } from "../pet/PetVisualization";
import { getIntegratedPetImagePath, getPetBackgroundImagePath, checkIntegratedImageExists, checkStandaloneImageExists, getSeasonalBackgroundPath, getPetNameSwedish, getPetNameSwedishLowercase } from "../pet/petImageUtils";
import { getPetFoodEmoji, getPetFoodName, getRandomPetMessage } from "../pet/petFoodUtils";
import { HalfCircleProgress } from "./components/HalfCircleProgress";
import { ConfettiAnimation } from "./components/ConfettiAnimation";
import { FloatingXpNumber } from "./components/FloatingXpNumber";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers" | "childrenxp" | "childrenwallet" | "childview" | "familytasks";

type AdultDashboardProps = {
  onNavigate?: (view: ViewKey, params?: { listId?: string; childId?: string; childName?: string }) => void;
  familyId?: string | null;
  key?: string | number; // Allow key prop to force remount
};

type ChildSummary = {
  todaysDone: number;
  todaysTotal: number;
  hasPet: boolean;
  streakDays: number;
  nextTaskTitle: string | null;
};

// Allowed family IDs for Spotify Charts link
const SPOTIFY_CHARTS_ALLOWED_FAMILIES = [
  "ce69194a-934d-4234-b046-dae7473700c0", // Production
  "cdd48859-74c5-4dee-989f-0b091f62d630", // Localhost
];

export function AdultDashboard({ onNavigate, familyId }: AdultDashboardProps) {
  const showSpotifyLink = familyId && SPOTIFY_CHARTS_ALLOWED_FAMILIES.includes(familyId);

  const [currentMember, setCurrentMember] = useState<FamilyMemberResponse | null>(null);
  const [members, setMembers] = useState<FamilyMemberResponse[]>([]);

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
  const [hasStandaloneImage, setHasStandaloneImage] = useState<boolean>(false);
  const [windowWidth, setWindowWidth] = useState<number>(typeof window !== "undefined" ? window.innerWidth : 1024);
  const previousLevelRef = useRef<number>(0);

  // Child summary state
  const [childSummaries, setChildSummaries] = useState<Record<string, ChildSummary>>({});
  const [loadingChildren, setLoadingChildren] = useState(false);

  // Invite state
  const [inviteChildId, setInviteChildId] = useState<string | null>(null);
  const [inviteToken, setInviteToken] = useState<string | null>(null);
  const [inviteLoading, setInviteLoading] = useState(false);

  // Add child inline form state
  const [showAddChild, setShowAddChild] = useState(false);
  const [addChildName, setAddChildName] = useState("");
  const [addChildAgeGroup, setAddChildAgeGroup] = useState<AgeGroup | "">("");
  const [addChildError, setAddChildError] = useState<string | null>(null);
  const [savingChild, setSavingChild] = useState(false);
  const [addChildSuggestions, setAddChildSuggestions] = useState<{ memberId: string; memberName: string; ageGroup: AgeGroup; checked: Set<string> } | null>(null);
  const [creatingChildTasks, setCreatingChildTasks] = useState(false);

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

        // Check if integrated or standalone image exists
        const [integratedExists, standaloneExists] = await Promise.all([
          checkIntegratedImageExists(petData.petType, petData.growthStage),
          checkStandaloneImageExists(petData.petType, petData.growthStage),
        ]);
        setHasIntegratedImage(integratedExists);
        setHasStandaloneImage(standaloneExists);

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

        // Load all members
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

  // Load child summaries when members change
  useEffect(() => {
    const childMembers = members.filter(m => m.role === "CHILD" || m.role === "ASSISTANT");
    if (childMembers.length === 0) { setLoadingChildren(false); return; }
    setLoadingChildren(true);
    const fetchSummaries = async () => {
      const entries = await Promise.all(
        childMembers.map(async (child) => {
          try {
            const today = `${new Date().getFullYear()}-${String(new Date().getMonth()+1).padStart(2,'0')}-${String(new Date().getDate()).padStart(2,'0')}`;
            const [chores, childPet] = await Promise.all([
              getDailyChoresForDate(child.id, today).catch(() => []),
              fetchMemberPet(child.id).catch(() => null),
            ]);
            const todaysDone = chores.filter(c => c.completed).length;
            const todaysTotal = chores.length;
            const nextTaskTitle = chores.find(c => !c.completed)?.chore.title ?? null;
            return [child.id, { todaysDone, todaysTotal, hasPet: childPet !== null, streakDays: 0, nextTaskTitle }] as const;
          } catch {
            return [child.id, { todaysDone: 0, todaysTotal: 0, hasPet: false, streakDays: 0, nextTaskTitle: null }] as const;
          }
        })
      );
      setChildSummaries(Object.fromEntries(entries));
      setLoadingChildren(false);
    };
    void fetchSummaries();
  }, [members]);

  const handleAddChild = async () => {
    if (!addChildName.trim()) { setAddChildError("Namn krävs."); return; }
    setSavingChild(true);
    setAddChildError(null);
    try {
      const created = await createFamilyMember(addChildName.trim(), "CHILD");
      const name = addChildName.trim();
      const ageGroup = addChildAgeGroup;
      setAddChildName("");
      setAddChildAgeGroup("");
      setShowAddChild(false);
      // Reload members list
      const allMembers = await fetchAllFamilyMembers();
      setMembers(allMembers);
      if (ageGroup) {
        setAddChildSuggestions({
          memberId: created.id,
          memberName: name,
          ageGroup,
          checked: new Set(TASK_SUGGESTIONS[ageGroup]),
        });
      }
    } catch (e) {
      setAddChildError(e instanceof Error ? e.message : "Kunde inte skapa barn.");
    } finally {
      setSavingChild(false);
    }
  };

  const handleAddSuggestedTasks = async () => {
    if (!addChildSuggestions) return;
    setCreatingChildTasks(true);
    const allWeekdays = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
    try {
      await Promise.all(
        [...addChildSuggestions.checked].map((title) =>
          createDailyChore(addChildSuggestions.memberId, title, allWeekdays, 1)
        )
      );
      setAddChildSuggestions(null);
    } catch {
      setAddChildError("Kunde inte skapa uppgifter.");
    } finally {
      setCreatingChildTasks(false);
    }
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
            const [integratedExists, standaloneExists] = await Promise.all([
              checkIntegratedImageExists(petData.petType, petData.growthStage),
              checkStandaloneImageExists(petData.petType, petData.growthStage),
            ]);
            setHasIntegratedImage(integratedExists);
            setHasStandaloneImage(standaloneExists);
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
  // XP thresholds: [0, 10, 35, 70, 125]
  // Level 1: 0-9 XP (range = 10)
  // Level 2: 10-34 XP (range = 25)
  // Level 3: 35-69 XP (range = 35)
  // Level 4: 70-124 XP (range = 55)
  // Level 5: 125+ XP
  const XP_THRESHOLDS = [0, 10, 35, 70, 125];
  const progressPercentage = xpProgress
    ? xpProgress.currentLevel >= MAX_LEVEL
      ? 100
      : (() => {
          const currentLevelIndex = xpProgress.currentLevel - 1; // 0-based index
          const nextLevelThreshold = XP_THRESHOLDS[xpProgress.currentLevel]; // Threshold for next level
          const currentLevelThreshold = XP_THRESHOLDS[currentLevelIndex]; // Threshold for current level
          const xpRangeForCurrentLevel = nextLevelThreshold - currentLevelThreshold; // XP range for current level

          if (xpRangeForCurrentLevel <= 0) return 100; // Safety check

          // Calculate progress: how much XP we have in current level / total XP range for current level
          const progress = (xpProgress.xpInCurrentLevel / xpRangeForCurrentLevel) * 100;
          return Math.min(100, Math.max(0, progress));
        })()
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

  // Computed values for family overview
  const childrenMembers = members.filter(m => m.role === "CHILD" || m.role === "ASSISTANT");
  const summaryList = childrenMembers.map(c => childSummaries[c.id]).filter(Boolean) as ChildSummary[];
  const totalTasksToday = summaryList.reduce((sum, s) => sum + s.todaysTotal, 0);
  const totalCompletedToday = summaryList.reduce((sum, s) => sum + s.todaysDone, 0);
  const childrenWithPet = summaryList.filter(s => s.hasPet).length;
  const suggestion = childrenMembers
    .filter(c => childSummaries[c.id] !== undefined && childSummaries[c.id].todaysTotal > 0)
    .sort((a, b) => {
      const sa = childSummaries[a.id]!;
      const sb = childSummaries[b.id]!;
      return (sa.todaysDone / sa.todaysTotal) - (sb.todaysDone / sb.todaysTotal);
    })[0] ?? null;
  const suggestionChild = suggestion ? childSummaries[suggestion.id] : null;
  const suggestionMessage = suggestion && suggestionChild
    ? suggestionChild.todaysDone >= suggestionChild.todaysTotal
      ? `Ge extra beröm – ${suggestion.name} har gjort alla sina uppgifter idag!`
      : suggestionChild.nextTaskTitle
        ? `Påminn ${suggestion.name} om "${suggestionChild.nextTaskTitle}" för att mata sitt djur.`
        : `Påminn ${suggestion.name} om dagens uppgifter.`
    : null;

  const glassCard = {
    background: "rgba(255, 255, 255, 0.82)",
    backdropFilter: "blur(8px)",
    WebkitBackdropFilter: "blur(8px)",
    borderRadius: "16px",
    boxShadow: "0 2px 8px rgba(0,0,0,0.08)",
  } as const;

  return (
    <div className="dashboard adult-dashboard" style={{
      background: "linear-gradient(160deg, #E0E7FF 0%, #E0F2FE 100%)",
      minHeight: "100vh",
      margin: "0 -16px -24px",
      padding: "20px 16px",
    }}>
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
                backgroundImage: hasStandaloneImage
                  ? `url(${getSeasonalBackgroundPath()})`
                  : hasIntegratedImage
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
              {(hasStandaloneImage || !hasIntegratedImage) && (
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
            background: "rgba(255, 255, 255, 0.82)",
            backdropFilter: "blur(8px)",
            WebkitBackdropFilter: "blur(8px)",
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
              background: "#f7fafc",
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
                    : "#48bb78",
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
                    : "#764ba2",
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
          background: "rgba(255, 255, 255, 0.82)",
          backdropFilter: "blur(8px)",
          WebkitBackdropFilter: "blur(8px)",
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
            onClick={() => onNavigate?.("eggselection" as ViewKey)}
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

      {/* Family Summary Card - "Idag i familjen" */}
      {childrenMembers.length > 0 && (
        <section className="card" style={{ ...glassCard, marginBottom: "16px", padding: "20px" }}>
          <h3 style={{ margin: "0 0 8px", fontSize: "1.1rem", fontWeight: 600, color: "#1C1917" }}>
            Idag i familjen
          </h3>
          {loadingChildren ? (
            <p style={{ margin: 0, color: "#57534E", fontSize: "0.9rem" }}>Laddar...</p>
          ) : (
            <>
              <p style={{ margin: "0 0 4px", fontSize: "0.9rem", color: "#57534E" }}>
                {totalCompletedToday > 0 || totalTasksToday > 0
                  ? `Barnen har gjort ${totalCompletedToday} av ${totalTasksToday} uppgifter idag.`
                  : "Inga uppgifter planerade idag ännu."}
              </p>
              {childrenWithPet > 0 && (
                <p style={{ margin: 0, fontSize: "0.9rem", color: "#57534E" }}>
                  {childrenWithPet} av {childrenMembers.length} barn har ett aktivt djur just nu.
                </p>
              )}
            </>
          )}
        </section>
      )}

      {/* Shortcut tiles */}
      <div style={{ display: "flex", gap: "10px", marginBottom: "16px" }}>
        {[
          { icon: "/lists_icon.png",    label: "Listor",   view: "todos"    },
          { icon: "/todo_icon.png",     label: "Att göra", view: "chores"   },
          { icon: "/calender_icon.png", label: "Schema",   view: "schedule" },
        ].map(({ icon, label, view }) => (
          <button key={view} type="button" onClick={() => onNavigate?.(view as ViewKey)}
            style={{
              flex: 1,
              padding: "14px 8px",
              background: "rgba(255,255,255,0.82)",
              backdropFilter: "blur(8px)",
              WebkitBackdropFilter: "blur(8px)",
              border: "none",
              borderRadius: "16px",
              cursor: "pointer",
              display: "flex", flexDirection: "column",
              alignItems: "center", gap: "6px",
              boxShadow: "0 2px 8px rgba(0,0,0,0.08)",
            }}
          >
            <img src={icon} alt={label} style={{ width: "2rem", height: "2rem", objectFit: "contain" }} />
            <span style={{ fontSize: "0.8rem", fontWeight: 600, color: "#1e3a5f" }}>{label}</span>
          </button>
        ))}
      </div>

      {/* Suggestion Card - "Förslag idag" */}
      {suggestionMessage && !loadingChildren && (
        <section className="card" style={{
          ...glassCard,
          background: "rgba(255, 247, 237, 0.9)",
          marginBottom: "16px",
          padding: "20px",
        }}>
          <h3 style={{ margin: "0 0 6px", fontSize: "1rem", fontWeight: 600, color: "#1C1917" }}>
            Förslag idag
          </h3>
          <p style={{ margin: 0, fontSize: "0.9rem", color: "#57534E" }}>{suggestionMessage}</p>
        </section>
      )}

      {/* "Mina barn" heading */}
      <h3 style={{ margin: "8px 0 12px", fontSize: "1.2rem", fontWeight: 600, color: "#1C1917" }}>
        Mina barn
      </h3>

      {/* Child cards */}
      {loadingChildren && childrenMembers.length === 0 ? (
        <p style={{ color: "#57534E", textAlign: "center" }}>Laddar...</p>
      ) : childrenMembers.length === 0 ? (
        <section className="card" style={{ ...glassCard, textAlign: "center", padding: "24px", marginBottom: "16px" }}>
          <p style={{ margin: "0 0 4px", color: "#1C1917" }}>Inga barn i familjen ännu</p>
          <p style={{ margin: 0, fontSize: "0.85rem", color: "#57534E" }}>Lägg till ditt första barn nedan.</p>
        </section>
      ) : (
        childrenMembers.map(child => {
          const s = childSummaries[child.id];
          return (
            <section key={child.id} className="card" style={{
              ...glassCard,
              background: "rgba(255, 251, 235, 0.92)",
              marginBottom: "16px",
              padding: "20px",
            }}>
              <p style={{ margin: "0 0 4px", fontSize: "1.1rem", fontWeight: 700, color: "#1C1917" }}>{child.name}</p>
              {s && (
                <>
                  <p style={{ margin: "0 0 2px", fontSize: "0.85rem", color: "#1C1917" }}>
                    {s.todaysTotal > 0 ? `Idag: ${s.todaysDone} av ${s.todaysTotal} uppgifter gjorda` : "Idag: inga uppgifter planerade"}
                  </p>
                  <p style={{ margin: "0 0 2px", fontSize: "0.85rem", color: "#1C1917" }}>
                    {s.hasPet ? "Djur: aktivt den här månaden" : "Djur: inget ägg valt ännu"}
                  </p>
                  {s.streakDays > 0 && (
                    <p style={{ margin: "0 0 12px", fontSize: "0.85rem", color: "#2563EB", fontWeight: 500 }}>
                      🔥 Streak: {s.streakDays} dagar i rad
                    </p>
                  )}
                  {!s.streakDays && <div style={{ marginBottom: "12px" }} />}
                </>
              )}
              {/* Djur + Plånbok row */}
              <div style={{ display: "flex", gap: "10px", marginBottom: "8px" }}>
                <button type="button" onClick={() => onNavigate?.("childrenxp")}
                  style={{ flex: 1, padding: "12px", background: "#BAE6FD", color: "#0C4A6E", border: "none", borderRadius: "12px", fontWeight: 600, cursor: "pointer", fontSize: "0.9rem" }}>
                  🐾 Djur
                </button>
                <button type="button" onClick={() => onNavigate?.("childrenwallet")}
                  style={{ flex: 1, padding: "12px", background: "#BAE6FD", color: "#0C4A6E", border: "none", borderRadius: "12px", fontWeight: 600, cursor: "pointer", fontSize: "0.9rem" }}>
                  💰 Plånbok
                </button>
              </div>
              {/* Sysslor button */}
              <button type="button" onClick={() => onNavigate?.("childview", { childId: child.id, childName: child.name })}
                style={{ width: "100%", padding: "11px", background: "#BAE6FD", color: "#0C4A6E", border: "none", borderRadius: "12px", fontWeight: 600, cursor: "pointer", fontSize: "0.9rem", marginBottom: "8px" }}>
                {child.name}s Sysslor
              </button>
              {/* Bjud in button */}
              {inviteChildId === child.id && inviteToken ? (
                <div style={{ background: "#EFF6FF", borderRadius: "12px", padding: "16px", border: "2px solid #BAE6FD", textAlign: "center" }}>
                  <p style={{ margin: "0 0 12px", fontSize: "0.85rem", color: "#0C4A6E", fontWeight: 600 }}>
                    Skanna QR-koden för att koppla {child.name}s enhet
                  </p>
                  <div style={{ display: "flex", justifyContent: "center", marginBottom: "12px" }}>
                    <QRCodeSVG value={`${window.location.origin}/invite/${inviteToken}`} size={200} />
                  </div>
                  <p style={{ margin: "0 0 12px", fontSize: "0.75rem", color: "#4a5568", wordBreak: "break-all" }}>
                    {`${window.location.origin}/invite/${inviteToken}`}
                  </p>
                  <div style={{ display: "flex", gap: "8px" }}>
                    <button type="button"
                      onClick={() => { window.open(`${window.location.origin}/invite/${inviteToken}`, "_blank"); }}
                      style={{ flex: 1, padding: "9px", background: "#0C4A6E", color: "white", border: "none", borderRadius: "9px", fontWeight: 600, fontSize: "0.85rem", cursor: "pointer" }}>
                      Öppna länk
                    </button>
                    <button type="button"
                      onClick={() => { setInviteChildId(null); setInviteToken(null); }}
                      style={{ padding: "9px 14px", background: "transparent", color: "#0C4A6E", border: "2px solid #BAE6FD", borderRadius: "9px", fontWeight: 500, fontSize: "0.85rem", cursor: "pointer" }}>
                      Stäng
                    </button>
                  </div>
                </div>
              ) : (
                <button type="button"
                  disabled={inviteLoading && inviteChildId === child.id}
                  onClick={async () => {
                    setInviteChildId(child.id);
                    setInviteToken(null);
                    setInviteLoading(true);
                    try {
                      const token = await generateInviteToken(child.id);
                      setInviteToken(token);
                    } finally {
                      setInviteLoading(false);
                    }
                  }}
                  style={{ width: "100%", padding: "10px", background: "transparent", color: "#0C4A6E", border: "2px solid #BAE6FD", borderRadius: "12px", fontWeight: 500, cursor: "pointer", fontSize: "0.9rem" }}>
                  {inviteLoading && inviteChildId === child.id ? "Genererar…" : "Bjud in till appen"}
                </button>
              )}
            </section>
          );
        })
      )}

      {/* Add child suggestion screen */}
      {addChildSuggestions && (
        <section className="card" style={{ ...glassCard, padding: "20px", marginBottom: "16px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "10px" }}>
            <button type="button" className="back-button" onClick={() => setAddChildSuggestions(null)} aria-label="Hoppa över">←</button>
            <h3 style={{ margin: 0, fontSize: "1rem" }}>Föreslagna uppgifter för {addChildSuggestions.memberName}</h3>
          </div>
          <p style={{ fontSize: "0.85rem", color: "#57534E", margin: "0 0 12px" }}>
            Avbocka de uppgifter du inte vill lägga till.
          </p>
          <ul style={{ margin: "0 0 12px", padding: 0, listStyle: "none", display: "flex", flexDirection: "column", gap: "2px" }}>
            {TASK_SUGGESTIONS[addChildSuggestions.ageGroup].map((task) => {
              const isChecked = addChildSuggestions.checked.has(task);
              return (
                <li key={task}>
                  <label style={{ display: "flex", alignItems: "center", gap: "10px", padding: "7px 2px", cursor: "pointer" }}>
                    <input type="checkbox" checked={isChecked}
                      onChange={() => setAddChildSuggestions(prev => {
                        if (!prev) return prev;
                        const next = new Set(prev.checked);
                        if (isChecked) next.delete(task); else next.add(task);
                        return { ...prev, checked: next };
                      })}
                      style={{ width: "18px", height: "18px", flexShrink: 0 }}
                    />
                    <span style={{ fontSize: "0.92rem", color: "#1C1917" }}>{task}</span>
                  </label>
                </li>
              );
            })}
          </ul>
          {addChildError && <p style={{ color: "#c53030", fontSize: "0.85rem", margin: "0 0 8px" }}>{addChildError}</p>}
          <div style={{ display: "flex", gap: "8px" }}>
            <button type="button" className="button-primary"
              onClick={() => void handleAddSuggestedTasks()}
              disabled={creatingChildTasks || addChildSuggestions.checked.size === 0}
              style={{ flex: 1 }}
            >
              {creatingChildTasks ? "Skapar..." : `Lägg till ${addChildSuggestions.checked.size} uppgifter`}
            </button>
            <button type="button" className="button-secondary" onClick={() => setAddChildSuggestions(null)} style={{ flex: 1 }}>
              Hoppa över
            </button>
          </div>
        </section>
      )}

      {/* Lägg till barn — inline form or button */}
      {showAddChild ? (
        <section className="card" style={{ ...glassCard, padding: "20px", marginBottom: "10px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "14px" }}>
            <button type="button" className="back-button" onClick={() => { setShowAddChild(false); setAddChildName(""); setAddChildAgeGroup(""); setAddChildError(null); }} aria-label="Avbryt">←</button>
            <h3 style={{ margin: 0, fontSize: "1rem" }}>Lägg till barn</h3>
          </div>
          <input
            type="text"
            placeholder="Namn"
            value={addChildName}
            onChange={(e) => setAddChildName(e.target.value)}
            className="daily-task-form-input"
            style={{ marginBottom: "12px" }}
          />
          <p style={{ fontSize: "0.85rem", color: "#57534E", margin: "0 0 8px" }}>Ålder (valfritt — föreslår dagliga uppgifter)</p>
          <div className="role-selector" style={{ marginBottom: "14px" }}>
            <label className="role-option">
              <input type="radio" name="addChildAge" value="" checked={addChildAgeGroup === ""} onChange={() => setAddChildAgeGroup("")} />
              <span>Ingen</span>
            </label>
            {AGE_GROUPS.map((g) => (
              <label key={g.value} className="role-option">
                <input type="radio" name="addChildAge" value={g.value} checked={addChildAgeGroup === g.value} onChange={() => setAddChildAgeGroup(g.value)} />
                <span>{g.label}</span>
              </label>
            ))}
          </div>
          {addChildError && <p style={{ color: "#c53030", fontSize: "0.85rem", margin: "0 0 8px" }}>{addChildError}</p>}
          <button type="button" className="button-primary" onClick={() => void handleAddChild()} disabled={savingChild} style={{ width: "100%" }}>
            {savingChild ? "Sparar..." : "Spara"}
          </button>
        </section>
      ) : (
        <button type="button" onClick={() => setShowAddChild(true)}
          style={{ width: "100%", padding: "16px", background: "#BAE6FD", color: "#0C4A6E", border: "none", borderRadius: "14px", fontWeight: 600, cursor: "pointer", fontSize: "1rem", display: "flex", alignItems: "center", justifyContent: "center", gap: "8px", marginTop: "8px", marginBottom: "10px" }}>
          👤 Lägg till barn
        </button>
      )}

      {/* All tasks overview button */}
      {childrenMembers.length > 0 && (
        <button type="button" onClick={() => onNavigate?.("familytasks")}
          style={{ width: "100%", padding: "16px", background: "rgba(255,255,255,0.82)", color: "#0C4A6E", border: "2px solid #BAE6FD", borderRadius: "14px", fontWeight: 600, cursor: "pointer", fontSize: "1rem", display: "flex", alignItems: "center", justifyContent: "center", gap: "8px", marginBottom: "16px" }}>
          📋 Alla uppgifter idag
        </button>
      )}

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

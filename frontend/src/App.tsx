import { useState, useEffect } from "react";
import { Dashboard } from "./features/dashboard/Dashboard";
import { AdultDashboard } from "./features/dashboard/AdultDashboard";
import { ChildDashboard } from "./features/dashboard/ChildDashboard";
import { TodoListsView } from "./features/todos/TodoListsView";
import { FamilyMembersView } from "./features/familymembers/FamilyMembersView";
import { InviteView } from "./features/invite/InviteView";
import { CalendarView } from "./features/calendar/CalendarView";
import { ChildTestView } from "./features/debug/ChildTestView";
import { LoginRegisterView } from "./features/auth/LoginRegisterView";
import { XpDashboard } from "./features/xp/XpDashboard";
import { ChildrenXpView } from "./features/xp/ChildrenXpView";
import { EggSelectionView } from "./features/pet/EggSelectionView";
import { PetTestView } from "./features/pet/PetTestView";
import { ChildPetHistoryView } from "./features/pet/ChildPetHistoryView";
import { MenstrualCycleView } from "./features/menstrualcycle/MenstrualCycleView";
import { WalletDetailView } from "./features/wallet/WalletDetailView";
import { ChildrenWalletView } from "./features/wallet/ChildrenWalletView";
import { PrivacyPolicyView } from "./features/legal/PrivacyPolicyView";
import { ParentChildView } from "./features/dashboard/ParentChildView";
import { AdultChoresView } from "./features/dashboard/AdultChoresView";
import { FamilyTasksView } from "./features/dashboard/FamilyTasksView";
import { useIsChild } from "./shared/hooks/useIsChild";
import { usePwaInstall } from "./shared/hooks/usePwaInstall";
import { getFamily } from "./shared/api/family";
import { getMemberByDeviceToken } from "./shared/api/familyMembers";
import { fetchCurrentPet, PetResponse } from "./shared/api/pets";
import { FamilyResponse } from "./shared/api/family";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers" | "invite" | "childtest" | "login" | "xp" | "childrenxp" | "eggselection" | "pettest" | "pethistory" | "menstrualcycle" | "wallet" | "childrenwallet" | "privacy" | "childview" | "familytasks";

// Allowed family IDs for Spotify Charts link
const SPOTIFY_CHARTS_ALLOWED_FAMILIES = [
  "ce69194a-934d-4234-b046-dae7473700c0", // Production
  "cdd48859-74c5-4dee-989f-0b091f62d630", // Localhost
];

export function App() {
  console.log("=== FamilyApp Frontend Starting - XP System: 24 XP per level (5 levels) ===");
  const [currentView, setCurrentView] = useState<ViewKey>("login");
  const [menstrualCycleEnabled, setMenstrualCycleEnabled] = useState(
    () => localStorage.getItem("menstrualCycleEnabled") === "true"
  );
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [family, setFamily] = useState<FamilyResponse | null>(null);
  const { isChild, childMember, loading: childLoading } = useIsChild();
  const { isInstallable, isInstalled, isIOS, handleInstallClick } = usePwaInstall();
  const [hasPet, setHasPet] = useState<boolean | null>(null);
  const [navigationParams, setNavigationParams] = useState<{ listId?: string } | null>(null);
  const [selectedChild, setSelectedChild] = useState<{ id: string; name: string } | null>(null);

  // Check for hash-based routing (for test views) - must run first
  useEffect(() => {
    const hash = window.location.hash.replace("#", "");
    if (hash === "pettest" && import.meta.env.DEV) {
      setCurrentView("pettest");
      setIsAuthenticated(true); // Allow test view without auth
      return;
    }
  }, []);

  // Check authentication status and load family
  useEffect(() => {
    // Skip if we're on a test view
    const hash = window.location.hash.replace("#", "");
    if (hash === "pettest" && import.meta.env.DEV) {
      return;
    }

    const loadFamily = async () => {
      const deviceToken = localStorage.getItem("deviceToken");
      if (deviceToken) {
        try {
          const member = await getMemberByDeviceToken(deviceToken);
          if (member.familyId) {
            const familyData = await getFamily(member.familyId);
            setFamily(familyData);
          }
          setIsAuthenticated(true);
          setCurrentView("dashboard");
        } catch (e) {
          // Invalid token, clear and show login
          localStorage.removeItem("deviceToken");
          setIsAuthenticated(false);
          setCurrentView("login");
        }
      } else {
        setIsAuthenticated(false);
        setCurrentView("login");
      }
    };
    void loadFamily();
  }, []);

  // Check if we're on an invite page or public page
  useEffect(() => {
    const path = window.location.pathname;
    if (path.startsWith("/invite/")) {
      setCurrentView("invite");
      setIsAuthenticated(true); // Allow invite view even without token initially
    } else if (path === "/privacy") {
      setCurrentView("privacy");
    }
  }, []);

  // Check if child has selected a pet for the current month
  useEffect(() => {
    const checkPet = async () => {
      if (!childLoading && isChild && isAuthenticated) {
        try {
          const pet = await fetchCurrentPet();
          // fetchCurrentPet() returns null on 404 (no pet selected yet)
          setHasPet(pet !== null);
        } catch (e) {
          // Pet doesn't exist or error
          setHasPet(false);
        }
      } else if (!isChild) {
        setHasPet(null);
      }
    };
    void checkPet();
  }, [isChild, childLoading, isAuthenticated]);

  // Re-read menstrualCycleEnabled from localStorage whenever view changes
  // (settings are saved in FamilyMembersView)
  useEffect(() => {
    setMenstrualCycleEnabled(localStorage.getItem("menstrualCycleEnabled") === "true");
  }, [currentView]);

  // Navigate to correct view based on pet status when child logs in
  useEffect(() => {
    if (!childLoading && isChild && isAuthenticated && currentView !== "invite" && currentView !== "xp") {
      if (hasPet === false) {
        setCurrentView("eggselection");
      } else if (hasPet === true && currentView === "eggselection") {
        setCurrentView("dashboard");
      } else if (hasPet === true) {
        setCurrentView("dashboard");
      }
    }
  }, [isChild, childLoading, isAuthenticated, hasPet]);

  const handleNavigate = (view: ViewKey, params?: { listId?: string; childId?: string; childName?: string }) => {
    setCurrentView(view);
    if (view === "todos") {
      setNavigationParams(params || null);
    } else if (view === "childview" && params?.childId && params?.childName) {
      setSelectedChild({ id: params.childId, name: params.childName });
      setNavigationParams(null);
    } else {
      setNavigationParams(null);
    }
  };

  const handleLogout = () => {
    // Restore parent token if it exists (from testing child view)
    const parentToken = localStorage.getItem("parentDeviceToken");
    if (parentToken) {
      localStorage.setItem("deviceToken", parentToken);
      localStorage.removeItem("parentDeviceToken");
      setIsAuthenticated(true);
      setCurrentView("dashboard");
      // Reload family info
      void (async () => {
        try {
          const { getMemberByDeviceToken } = await import("./shared/api/familyMembers");
          const { getFamily } = await import("./shared/api/family");
          const member = await getMemberByDeviceToken(parentToken);
          if (member.familyId) {
            const familyData = await getFamily(member.familyId);
            setFamily(familyData);
          }
        } catch (e) {
          console.error("Failed to reload family:", e);
        }
      })();
    } else {
      // Full logout - no parent token saved
      localStorage.removeItem("deviceToken");
      setIsAuthenticated(false);
      setCurrentView("login");
    }
  };

  const handleLogin = async (deviceToken: string) => {
    localStorage.setItem("deviceToken", deviceToken);
    setIsAuthenticated(true);
    // Load family info
    try {
      const member = await getMemberByDeviceToken(deviceToken);
      if (member.familyId) {
        const familyData = await getFamily(member.familyId);
        setFamily(familyData);
      }
      // Check if child and has pet - will be handled by useEffect
      setCurrentView("dashboard");
    } catch (e) {
      console.error("Failed to load family:", e);
      setCurrentView("dashboard");
    }
  };

  const handleEggSelected = async (pet: PetResponse) => {
    setHasPet(true);
    setCurrentView("dashboard");
    // Reload family info to refresh state
    try {
      const deviceToken = localStorage.getItem("deviceToken");
      if (deviceToken) {
        const member = await getMemberByDeviceToken(deviceToken);
        if (member.familyId) {
          const familyData = await getFamily(member.familyId);
          setFamily(familyData);
        }
      }
    } catch (e) {
      console.error("Failed to reload family after egg selection:", e);
    }
  };

  const renderView = () => {
    // Show test views first (they work without auth in dev)
    if (currentView === "pettest" && import.meta.env.DEV) {
      return <PetTestView />;
    }

    // Public pages (no auth required)
    if (currentView === "privacy") {
      return <PrivacyPolicyView />;
    }

    // Show login/register if not authenticated
    if (!isAuthenticated && currentView !== "invite") {
      return <LoginRegisterView onLogin={handleLogin} />;
    }

    // If child or assistant is logged in, show appropriate views
    if (isChild) {
      const isAssistant = childMember?.role === "ASSISTANT";
      
      // ASSISTANT can see calendar and todos, CHILD cannot
      if (isAssistant && currentView === "schedule") {
        return <CalendarView onNavigate={handleNavigate} showMenstrualCycle={false} onNavigateMenstrualCycle={() => handleNavigate("menstrualcycle")} />;
      }
      if (isAssistant && currentView === "todos") {
        return <TodoListsView onNavigate={handleNavigate} />;
      }
      
      switch (currentView) {
        case "eggselection":
          return <EggSelectionView onEggSelected={handleEggSelected} />;
        case "pethistory":
          return <ChildPetHistoryView onNavigate={handleNavigate} childName={childMember?.name} />;
        case "xp":
          return <XpDashboard onNavigate={handleNavigate} />;
        case "wallet":
          return <WalletDetailView onNavigate={handleNavigate} />;
        case "dashboard":
        default:
          // If no pet, show egg selection instead
          if (hasPet === false) {
            return <EggSelectionView onEggSelected={handleEggSelected} />;
          }
          return <ChildDashboard onNavigate={handleNavigate} childName={childMember?.name} onLogout={handleLogout} familyId={family?.id} />;
      }
    }

    // Parent/admin views
    switch (currentView) {
      case "invite":
        return <InviteView />;
      case "childtest":
        // Only show in development
        if (import.meta.env.DEV) {
          return <ChildTestView />;
        }
        // In production, redirect to dashboard
        return null;
      case "pettest":
        // Only show in development
        if (import.meta.env.DEV) {
          return <PetTestView />;
        }
        // In production, redirect to dashboard
        return null;
      case "todos":
        return <TodoListsView onNavigate={handleNavigate} initialListId={navigationParams?.listId} />;
      case "familymembers":
        return <FamilyMembersView onNavigate={handleNavigate} />;
      case "schedule":
        return <CalendarView onNavigate={handleNavigate} showMenstrualCycle={menstrualCycleEnabled} onNavigateMenstrualCycle={() => handleNavigate("menstrualcycle")} />;
      case "chores":
        return <AdultChoresView onNavigate={handleNavigate} />;
      case "familytasks":
        return <FamilyTasksView onNavigate={handleNavigate} />;
      case "xp":
        return <XpDashboard onNavigate={handleNavigate} />;
      case "childrenxp":
        return <ChildrenXpView onNavigate={handleNavigate} />;
      case "childrenwallet":
        return <ChildrenWalletView onNavigate={handleNavigate} />;
      case "menstrualcycle":
        return <MenstrualCycleView onNavigate={handleNavigate} />;
      case "eggselection":
        return <EggSelectionView onEggSelected={handleEggSelected} />;
      case "childview":
        if (selectedChild) {
          return (
            <ParentChildView
              childId={selectedChild.id}
              childName={selectedChild.name}
              onBack={() => handleNavigate("dashboard")}
            />
          );
        }
        return <AdultDashboard onNavigate={handleNavigate} familyId={family?.id} />;
      case "dashboard":
      default:
        return <AdultDashboard onNavigate={handleNavigate} familyId={family?.id} />;
    }
  };

  return (
    <div className="app-root" style={isChild ? { paddingTop: 0 } : undefined}>
      {!isChild && (
        <header className="app-header">
          <div className="app-title">
            <h1>{family?.name || "FamilyApp"}</h1>
            <p>Hela familjens vardag, samlad på ett ställe.</p>
          </div>
          <button
            type="button"
            className="settings-button"
            aria-label="Inställningar"
            onClick={() => handleNavigate("familymembers")}
          >
            ⚙️
          </button>
        </header>
      )}

      <main className="app-main">{renderView()}</main>
    </div>
  );
}




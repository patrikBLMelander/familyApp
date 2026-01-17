import { useState, useEffect } from "react";
import { Dashboard } from "./features/dashboard/Dashboard";
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
import { useIsChild } from "./shared/hooks/useIsChild";
import { usePwaInstall } from "./shared/hooks/usePwaInstall";
import { getFamily } from "./shared/api/family";
import { getMemberByDeviceToken } from "./shared/api/familyMembers";
import { fetchCurrentPet, PetResponse } from "./shared/api/pets";
import { FamilyResponse } from "./shared/api/family";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers" | "invite" | "childtest" | "login" | "xp" | "childrenxp" | "eggselection" | "pettest";

export function App() {
  console.log("=== FamilyApp Frontend Starting - XP System: 24 XP per level (5 levels) ===");
  const [currentView, setCurrentView] = useState<ViewKey>("login");
  const [menuOpen, setMenuOpen] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [family, setFamily] = useState<FamilyResponse | null>(null);
  const { isChild, childMember, loading: childLoading } = useIsChild();
  const { isInstallable, isInstalled, isIOS, handleInstallClick } = usePwaInstall();
  const [hasPet, setHasPet] = useState<boolean | null>(null);

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

  // Check if we're on an invite page
  useEffect(() => {
    const path = window.location.pathname;
    if (path.startsWith("/invite/")) {
      setCurrentView("invite");
      setIsAuthenticated(true); // Allow invite view even without token initially
    }
  }, []);

  // Check if child has selected a pet for the current month
  useEffect(() => {
    const checkPet = async () => {
      if (!childLoading && isChild && isAuthenticated) {
        try {
          await fetchCurrentPet();
          setHasPet(true);
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

  const handleNavigate = (view: ViewKey) => {
    setCurrentView(view);
    setMenuOpen(false);
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

  const handleEggSelected = (pet: PetResponse) => {
    setHasPet(true);
    setCurrentView("dashboard");
  };

  const renderView = () => {
    // Show test views first (they work without auth in dev)
    if (currentView === "pettest" && import.meta.env.DEV) {
      return <PetTestView />;
    }

    // Show login/register if not authenticated
    if (!isAuthenticated && currentView !== "invite") {
      return <LoginRegisterView onLogin={handleLogin} />;
    }

    // If child or assistant is logged in, show appropriate views
    if (isChild) {
      const isAssistant = childMember?.role === "ASSISTANT";
      
      // ASSISTANT can see calendar, CHILD cannot
      if (isAssistant && currentView === "schedule") {
        return <CalendarView onNavigate={handleNavigate} />;
      }
      
      switch (currentView) {
        case "eggselection":
          return <EggSelectionView onEggSelected={handleEggSelected} />;
        case "xp":
          return <XpDashboard onNavigate={handleNavigate} />;
        case "dashboard":
        default:
          // If no pet, show egg selection instead
          if (hasPet === false) {
            return <EggSelectionView onEggSelected={handleEggSelected} />;
          }
          return <ChildDashboard onNavigate={handleNavigate} childName={childMember?.name} onLogout={handleLogout} />;
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
        return <TodoListsView onNavigate={handleNavigate} />;
      case "familymembers":
        return <FamilyMembersView onNavigate={handleNavigate} />;
      case "schedule":
        return <CalendarView onNavigate={handleNavigate} />;
      case "chores":
        return <Dashboard placeholder="Sysslor-vy kommer här." onNavigate={handleNavigate} />;
      case "xp":
        return <XpDashboard onNavigate={handleNavigate} />;
      case "childrenxp":
        return <ChildrenXpView onNavigate={handleNavigate} />;
      case "dashboard":
      default:
        return <Dashboard onNavigate={handleNavigate} />;
    }
  };

  return (
    <div className="app-root">
      <header className="app-header">
        <button
          type="button"
          className="hamburger-button"
          aria-label="Öppna huvudmeny"
          onClick={() => setMenuOpen((open) => !open)}
        >
          <span />
          <span />
          <span />
        </button>
        <div className="app-title">
          <h1>{family?.name || "FamilyApp"}</h1>
          <p>{isChild ? `${childMember?.name || "Barn"}'s vardag` : "Hela familjens vardag, samlad på ett ställe."}</p>
        </div>
      </header>

      <nav className={`side-menu ${menuOpen ? "side-menu-open" : ""}`}>
        {isChild ? (
          // Child/Assistant menu
          <>
            <button
              type="button"
              className="side-menu-item"
              onClick={() => handleNavigate("dashboard")}
            >
              Dashboard
            </button>
            {childMember?.role === "ASSISTANT" && (
              <button
                type="button"
                className="side-menu-item"
                onClick={() => handleNavigate("schedule")}
              >
                Kalender
              </button>
            )}
            {/* PWA Install button - show if not installed */}
            {!isInstalled && (
              <button
                type="button"
                className="side-menu-item"
                onClick={handleInstallClick}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "8px",
                  marginTop: "8px",
                  paddingTop: "12px",
                  borderTop: "1px solid rgba(220, 210, 200, 0.4)"
                }}
              >
                <span style={{ fontSize: "1.2rem" }}>📱</span>
                <span>Installera app</span>
              </button>
            )}
          </>
        ) : (
          // Parent menu - show all options
          <>
            <button
              type="button"
              className="side-menu-item"
              onClick={() => handleNavigate("dashboard")}
            >
              Dashboard
            </button>
            <button
              type="button"
              className="side-menu-item"
              onClick={() => handleNavigate("todos")}
            >
              Listor
            </button>
            <button
              type="button"
              className="side-menu-item"
              onClick={() => handleNavigate("schedule")}
            >
              Kalender
            </button>
            <button
              type="button"
              className="side-menu-item"
              onClick={() => handleNavigate("familymembers")}
            >
              Familjemedlemmar
            </button>
            {/* PWA Install button - show if not installed */}
            {!isInstalled && (
              <button
                type="button"
                className="side-menu-item"
                onClick={handleInstallClick}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "8px",
                  marginTop: "8px",
                  paddingTop: "12px",
                  borderTop: "1px solid rgba(220, 210, 200, 0.4)"
                }}
              >
                <span style={{ fontSize: "1.2rem" }}>📱</span>
                <span>Installera app</span>
              </button>
            )}
          </>
        )}
      </nav>

      {menuOpen && <div className="backdrop" onClick={() => setMenuOpen(false)} />}

      <main className="app-main">{renderView()}</main>
    </div>
  );
}




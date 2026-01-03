import { useState, useEffect } from "react";
import { Dashboard } from "./features/dashboard/Dashboard";
import { ChildDashboard } from "./features/dashboard/ChildDashboard";
import { TodoListsView } from "./features/todos/TodoListsView";
import { DailyTasksView } from "./features/dailytasks/DailyTasksView";
import { DailyTasksAdminView } from "./features/dailytasks/DailyTasksAdminView";
import { FamilyMembersView } from "./features/familymembers/FamilyMembersView";
import { InviteView } from "./features/invite/InviteView";
import { ChildTestView } from "./features/debug/ChildTestView";
import { LoginRegisterView } from "./features/auth/LoginRegisterView";
import { useIsChild } from "./shared/hooks/useIsChild";
import { getFamily } from "./shared/api/family";
import { getMemberByDeviceToken } from "./shared/api/familyMembers";
import { FamilyResponse } from "./shared/api/family";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "dailytasks" | "dailytasksadmin" | "familymembers" | "invite" | "childtest" | "login";

export function App() {
  const [currentView, setCurrentView] = useState<ViewKey>("login");
  const [menuOpen, setMenuOpen] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [family, setFamily] = useState<FamilyResponse | null>(null);
  const { isChild, childMember, loading: childLoading } = useIsChild();

  // Check authentication status and load family
  useEffect(() => {
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

  // Reset to dashboard when child status changes
  useEffect(() => {
    if (!childLoading && isChild && currentView !== "dailytasks" && currentView !== "invite") {
      setCurrentView("dashboard");
    }
  }, [isChild, childLoading]);

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
    setCurrentView("dashboard");
    // Load family info
    try {
      const member = await getMemberByDeviceToken(deviceToken);
      if (member.familyId) {
        const familyData = await getFamily(member.familyId);
        setFamily(familyData);
      }
    } catch (e) {
      console.error("Failed to load family:", e);
    }
  };

  const renderView = () => {
    // Show login/register if not authenticated
    if (!isAuthenticated && currentView !== "invite") {
      return <LoginRegisterView onLogin={handleLogin} />;
    }

    // If child is logged in, only show child-accessible views
    if (isChild) {
      switch (currentView) {
        case "dailytasks":
          return <DailyTasksView onNavigate={handleNavigate} />;
        case "dashboard":
        default:
          return <ChildDashboard onNavigate={handleNavigate} childName={childMember?.name} onLogout={handleLogout} />;
      }
    }

    // Parent/admin views
    switch (currentView) {
      case "invite":
        return <InviteView />;
      case "childtest":
        return <ChildTestView />;
      case "todos":
        return <TodoListsView onNavigate={handleNavigate} />;
      case "dailytasks":
        return <DailyTasksView onNavigate={handleNavigate} />;
      case "dailytasksadmin":
        return <DailyTasksAdminView onNavigate={handleNavigate} />;
      case "familymembers":
        return <FamilyMembersView onNavigate={handleNavigate} />;
      case "schedule":
        return <Dashboard placeholder="Schema-vy kommer här." onNavigate={handleNavigate} />;
      case "chores":
        return <Dashboard placeholder="Sysslor-vy kommer här." onNavigate={handleNavigate} />;
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
          // Child menu - only show daily tasks
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
              onClick={() => handleNavigate("dailytasks")}
            >
              Dagliga sysslor
            </button>
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
              To do-listor
            </button>
            <button
              type="button"
              className="side-menu-item"
              onClick={() => handleNavigate("schedule")}
            >
              Schema
            </button>
            <button
              type="button"
              className="side-menu-item"
              onClick={() => handleNavigate("dailytasks")}
            >
              Dagliga sysslor
            </button>
            <button
              type="button"
              className="side-menu-item"
              onClick={() => handleNavigate("familymembers")}
            >
              Familjemedlemmar
            </button>
          </>
        )}
      </nav>

      {menuOpen && <div className="backdrop" onClick={() => setMenuOpen(false)} />}

      <main className="app-main">{renderView()}</main>
    </div>
  );
}




import { useEffect, useState } from "react";
import { fetchAllFamilyMembers, getMemberByDeviceToken, FamilyMemberResponse } from "../../shared/api/familyMembers";

export function ChildTestView() {
  const [members, setMembers] = useState<FamilyMemberResponse[]>([]);
  const [currentDeviceToken, setCurrentDeviceToken] = useState<string | null>(null);
  const [currentMember, setCurrentMember] = useState<FamilyMemberResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    void loadData();
  }, []);

  const loadData = async () => {
    try {
      const [membersData, deviceToken] = await Promise.all([
        fetchAllFamilyMembers(),
        Promise.resolve(localStorage.getItem("deviceToken"))
      ]);
      setMembers(membersData);
      setCurrentDeviceToken(deviceToken);

      if (deviceToken) {
        try {
          const member = await getMemberByDeviceToken(deviceToken);
          setCurrentMember(member);
        } catch {
          setCurrentMember(null);
        }
      }
    } catch (e) {
      console.error("Error loading data:", e);
    } finally {
      setLoading(false);
    }
  };

  const handleSetAsChild = async (member: FamilyMemberResponse) => {
    if (!member.deviceToken) {
      alert("Detta barn har ingen inbjudningstoken än. Generera en QR-kod först i 'Familjemedlemmar'.");
      return;
    }

    // Save the current parent token before switching to child
    const currentToken = localStorage.getItem("deviceToken");
    if (currentToken) {
      localStorage.setItem("parentDeviceToken", currentToken);
    }

    // For testing: use the member's deviceToken directly
    // This simulates that this device has been linked to the member
    localStorage.setItem("deviceToken", member.deviceToken);
    setCurrentDeviceToken(member.deviceToken);
    setCurrentMember(member);
    
    // Reload to see the child's view
    setTimeout(() => {
      window.location.href = "/";
    }, 500);
  };

  const handleClearLogin = () => {
    // Restore parent token if it exists
    const parentToken = localStorage.getItem("parentDeviceToken");
    if (parentToken) {
      localStorage.setItem("deviceToken", parentToken);
      localStorage.removeItem("parentDeviceToken");
    } else {
      localStorage.removeItem("deviceToken");
    }
    setCurrentDeviceToken(null);
    setCurrentMember(null);
    alert("Du är nu tillbaka som förälder.");
    window.location.href = "/";
  };

  if (loading) {
    return (
      <div className="child-test-view">
        <section className="card">
          <p>Laddar...</p>
        </section>
      </div>
    );
  }

  return (
    <div className="child-test-view">
      <section className="card">
        <h2>Testa som barn</h2>
        <p style={{ fontSize: "0.9rem", color: "#6b6b6b", marginBottom: "16px" }}>
          Välj ett barn för att simulera att du är inloggad som det barnet.
        </p>

        {currentMember && (
          <div style={{
            padding: "12px",
            background: "rgba(184, 230, 184, 0.2)",
            borderRadius: "8px",
            marginBottom: "16px"
          }}>
            <p style={{ margin: "0 0 4px", fontWeight: "600" }}>För närvarande inloggad som:</p>
            <p style={{ margin: "0", fontSize: "1.1rem", color: "#2d5a2d" }}>{currentMember.name}</p>
            <button
              type="button"
              className="button-secondary"
              onClick={handleClearLogin}
              style={{ marginTop: "8px" }}
            >
              Tillbaka till föräldervyn
            </button>
          </div>
        )}

        {!currentMember && (
          <div style={{
            padding: "12px",
            background: "rgba(240, 240, 240, 0.6)",
            borderRadius: "8px",
            marginBottom: "16px"
          }}>
            <p style={{ margin: "0" }}>Du är för närvarande inte inloggad som något barn.</p>
          </div>
        )}

        <h3 style={{ fontSize: "1rem", marginTop: "20px", marginBottom: "12px" }}>Välj barn att testa som:</h3>
        {members.length === 0 ? (
          <p className="placeholder-text">Inga familjemedlemmar skapade än.</p>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
            {members.map((member) => (
              <button
                key={member.id}
                type="button"
                className={currentMember?.id === member.id ? "button-primary" : "button-secondary"}
                onClick={() => void handleSetAsChild(member)}
                disabled={!member.deviceToken}
                style={{
                  textAlign: "left",
                  opacity: member.deviceToken ? 1 : 0.5
                }}
              >
                {member.name}
                {!member.deviceToken && " (ingen inbjudning genererad)"}
                {currentMember?.id === member.id && " ✓"}
              </button>
            ))}
          </div>
        )}

        <div style={{
          marginTop: "20px",
          padding: "12px",
          background: "rgba(255, 240, 200, 0.3)",
          borderRadius: "8px",
          fontSize: "0.85rem",
          color: "#6b6b6b"
        }}>
          <p style={{ margin: "0 0 8px", fontWeight: "600" }}>Tips:</p>
          <ul style={{ margin: "0", paddingLeft: "20px" }}>
            <li>För att testa som ett barn måste det ha en genererad QR-kod/inbjudning</li>
            <li>När du är inloggad som barn ser du bara sysslor som är tilldelade till det barnet</li>
            <li>Klicka på "Tillbaka till föräldervyn" för att återgå till föräldervyn</li>
          </ul>
        </div>
      </section>
    </div>
  );
}


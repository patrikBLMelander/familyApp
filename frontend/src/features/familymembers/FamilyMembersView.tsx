import { useEffect, useState, useRef } from "react";
import { QRCodeSVG } from "qrcode.react";
import {
  fetchAllFamilyMembers,
  createFamilyMember,
  updateFamilyMember,
  updateFamilyMemberPassword,
  updateFamilyMemberEmail,
  deleteFamilyMember,
  generateInviteToken,
  FamilyMemberResponse,
  FamilyMemberRole,
} from "../../shared/api/familyMembers";
import { updateMenstrualCycleSettings } from "../../shared/api/menstrualCycle";
import { updatePetSettings } from "../../shared/api/familyMembers";

type FamilyMembersViewProps = {
  onNavigate?: (view: string) => void;
};

export function FamilyMembersView({ onNavigate }: FamilyMembersViewProps) {
  const [members, setMembers] = useState<FamilyMemberResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [memberName, setMemberName] = useState("");
  const [memberRole, setMemberRole] = useState<FamilyMemberRole>("CHILD");
  const [inviteToken, setInviteToken] = useState<string | null>(null);
  const [inviteMemberId, setInviteMemberId] = useState<string | null>(null);
  const [passwordEditingId, setPasswordEditingId] = useState<string | null>(null);
  const [newPassword, setNewPassword] = useState("");
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [newPasswordConfirm, setNewPasswordConfirm] = useState("");
  const [showNewPasswordConfirm, setShowNewPasswordConfirm] = useState(false);
  const [emailEditingId, setEmailEditingId] = useState<string | null>(null);
  const [newEmail, setNewEmail] = useState("");
  const [menstrualCycleSettingsId, setMenstrualCycleSettingsId] = useState<string | null>(null);
  const [menstrualCycleEnabled, setMenstrualCycleEnabled] = useState(false);
  const [menstrualCyclePrivate, setMenstrualCyclePrivate] = useState(true);
  const [petSettingsId, setPetSettingsId] = useState<string | null>(null);
  const [petEnabled, setPetEnabled] = useState(false);
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const qrCodeRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    void loadMembers();
  }, []);

  // Scroll to QR code when it's displayed
  useEffect(() => {
    if (inviteToken && qrCodeRef.current) {
      // Small delay to ensure the element is rendered
      const timeoutId = setTimeout(() => {
        qrCodeRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
      }, 100);
      
      // Cleanup timeout if component unmounts or inviteToken changes
      return () => clearTimeout(timeoutId);
    }
  }, [inviteToken]);

  const loadMembers = async () => {
    try {
      setLoading(true);
      const data = await fetchAllFamilyMembers();
      setMembers(data);
    } catch (e) {
      const errorMessage = e instanceof Error ? e.message : "Kunde inte hämta familjemedlemmar.";
      if (errorMessage.includes("401") || errorMessage.includes("Unauthorized")) {
        setError("Du är inte inloggad. Logga in och försök igen.");
      } else if (errorMessage.includes("403") || errorMessage.includes("Forbidden")) {
        setError("Du har inte behörighet att se familjemedlemmar.");
      } else if (errorMessage.includes("Network") || errorMessage.includes("Failed to fetch")) {
        setError("Kunde inte ansluta till servern. Kontrollera din internetanslutning.");
      } else {
        setError(errorMessage);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async () => {
    if (!memberName.trim()) {
      setError("Namn krävs.");
      return;
    }

    try {
      await createFamilyMember(memberName.trim(), memberRole);
      await loadMembers();
      setMemberName("");
      setMemberRole("CHILD");
      setShowCreateForm(false);
      setError(null);
    } catch (e) {
      const errorMessage = e instanceof Error ? e.message : "Kunde inte skapa familjemedlem.";
      if (errorMessage.includes("already exists") || errorMessage.includes("finns redan")) {
        setError("En familjemedlem med detta namn finns redan.");
      } else if (errorMessage.includes("401") || errorMessage.includes("Unauthorized")) {
        setError("Du är inte inloggad. Logga in och försök igen.");
      } else {
        setError(errorMessage);
      }
    }
  };

  const handleUpdate = async (memberId: string) => {
    if (!memberName.trim()) {
      setError("Namn krävs.");
      return;
    }

    try {
      // Update backend first and use the returned updated member
      const updatedMember = await updateFamilyMember(memberId, memberName.trim());
      
      // Update state with the returned member data (bypasses cache issues)
      setMembers(prevMembers => 
        prevMembers.map(m => 
          m.id === memberId ? updatedMember : m
        )
      );
      
      // Close edit mode
      setEditingId(null);
      setMemberName("");
      setError(null);
    } catch (e) {
      // Reload on error to get correct state
      await loadMembers();
      
      const errorMessage = e instanceof Error ? e.message : "Kunde inte uppdatera familjemedlem.";
      if (errorMessage.includes("401") || errorMessage.includes("Unauthorized")) {
        setError("Du är inte inloggad. Logga in och försök igen.");
      } else if (errorMessage.includes("404") || errorMessage.includes("Not Found")) {
        setError("Familjemedlemmen hittades inte. Den kan ha tagits bort.");
      } else {
        setError(errorMessage);
      }
    }
  };

  const handleDelete = async (memberId: string) => {
    if (!confirm("Är du säker på att du vill ta bort denna familjemedlem?")) {
      return;
    }

    try {
      await deleteFamilyMember(memberId);
      await loadMembers();
    } catch (e) {
      const errorMessage = e instanceof Error ? e.message : "Kunde inte ta bort familjemedlem.";
      if (errorMessage.includes("401") || errorMessage.includes("Unauthorized")) {
        setError("Du är inte inloggad. Logga in och försök igen.");
      } else if (errorMessage.includes("403") || errorMessage.includes("Forbidden")) {
        setError("Du har inte behörighet att ta bort denna familjemedlem.");
      } else if (errorMessage.includes("404") || errorMessage.includes("Not Found")) {
        setError("Familjemedlemmen hittades inte. Den kan redan ha tagits bort.");
      } else {
        setError(errorMessage);
      }
    }
  };

  const handleGenerateInvite = async (memberId: string) => {
    try {
      const token = await generateInviteToken(memberId);
      setInviteToken(token);
      setInviteMemberId(memberId);
    } catch (e) {
      const errorMessage = e instanceof Error ? e.message : "Kunde inte generera inbjudan.";
      if (errorMessage.includes("401") || errorMessage.includes("Unauthorized")) {
        setError("Du är inte inloggad. Logga in och försök igen.");
      } else if (errorMessage.includes("403") || errorMessage.includes("Forbidden")) {
        setError("Du har inte behörighet att generera inbjudningar.");
      } else if (errorMessage.includes("404") || errorMessage.includes("Not Found")) {
        setError("Familjemedlemmen hittades inte.");
      } else {
        setError(errorMessage);
      }
    }
  };

  const handleUpdatePassword = async (memberId: string) => {
    if (newPassword.length < 6) {
      setError("Lösenordet måste vara minst 6 tecken långt.");
      return;
    }
    if (newPassword !== newPasswordConfirm) {
      setError("Lösenorden matchar inte.");
      return;
    }

    try {
      // Update backend first and use the returned updated member
      const updatedMember = await updateFamilyMemberPassword(memberId, newPassword);
      
      // Update state with the returned member data (bypasses cache issues)
      setMembers(prevMembers => 
        prevMembers.map(m => 
          m.id === memberId ? updatedMember : m
        )
      );
      
      setPasswordEditingId(null);
      setNewPassword("");
      setNewPasswordConfirm("");
      setShowNewPassword(false);
      setShowNewPasswordConfirm(false);
      setError(null);
    } catch (e) {
      // Reload on error to get correct state
      await loadMembers();
      
      const errorMessage = e instanceof Error ? e.message : "Kunde inte uppdatera lösenord.";
      setError(errorMessage);
      console.error("Password update error:", e);
    }
  };

  const handleUpdateMenstrualCycleSettings = async (memberId: string) => {
    try {
      await updateMenstrualCycleSettings(memberId, menstrualCycleEnabled, menstrualCyclePrivate);
      await loadMembers();
      setMenstrualCycleSettingsId(null);
      setError(null);
    } catch (e) {
      const errorMessage = e instanceof Error ? e.message : "Kunde inte uppdatera menscykel-inställningar.";
      setError(errorMessage);
      console.error("Menstrual cycle settings update error:", e);
    }
  };

  const handleUpdatePetSettings = async (memberId: string) => {
    try {
      await updatePetSettings(memberId, petEnabled);
      await loadMembers();
      setPetSettingsId(null);
      setError(null);
    } catch (e) {
      const errorMessage = e instanceof Error ? e.message : "Kunde inte uppdatera djur-inställningar.";
      setError(errorMessage);
      console.error("Pet settings update error:", e);
    }
  };

  const handleUpdateEmail = async (memberId: string) => {
    // Improved email validation using regex
    const trimmedEmail = newEmail.trim();
    if (trimmedEmail) {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(trimmedEmail)) {
        setError("Ogiltig e-postadress. Ange en giltig e-postadress (t.ex. namn@exempel.se).");
        return;
      }
    }

    try {
      // Update backend first and use the returned updated member
      const updatedMember = await updateFamilyMemberEmail(memberId, trimmedEmail || "");
      
      // Update state with the returned member data (bypasses cache issues)
      setMembers(prevMembers => 
        prevMembers.map(m => 
          m.id === memberId ? updatedMember : m
        )
      );
      
      setEmailEditingId(null);
      setNewEmail("");
      setError(null);
    } catch (e) {
      // Reload on error to get correct state
      await loadMembers();
      
      const errorMessage = e instanceof Error ? e.message : "Kunde inte uppdatera e-postadress.";
      // Check for specific error messages from backend
      if (errorMessage.includes("already in use") || errorMessage.includes("används redan")) {
        setError("Denna e-postadress används redan av ett annat konto.");
      } else if (errorMessage.includes("Invalid") || errorMessage.includes("Ogiltig")) {
        setError("Ogiltig e-postadress. Kontrollera att adressen är korrekt.");
      } else {
        setError(errorMessage);
      }
      console.error("Email update error:", e);
    }
  };

  const inviteUrl = inviteToken
    ? `${window.location.origin}/invite/${inviteToken}`
    : null;

  return (
    <div className="family-members-view">
      <div className="family-members-header">
        <div style={{ display: "flex", alignItems: "center", gap: "12px", flex: 1 }}>
          {onNavigate && (
            <button
              type="button"
              className="back-button"
              onClick={() => onNavigate("dashboard")}
              aria-label="Tillbaka"
            >
              ←
            </button>
          )}
          <h2 className="view-title" style={{ margin: 0, flex: 1 }}>Familjemedlemmar</h2>
        </div>
        {!showCreateForm && (
          <button
            type="button"
            className="todo-action-button"
            onClick={() => {
              setShowCreateForm(true);
              setEditingId(null);
              setMemberName("");
            }}
          >
            + Ny familjemedlem
          </button>
        )}
      </div>

      {error && <p className="error-text">{error}</p>}

      {showCreateForm && (
        <section className="card">
          <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "12px" }}>
            <button
              type="button"
              className="back-button"
              onClick={() => {
                setShowCreateForm(false);
                setEditingId(null);
                setMemberName("");
                setError(null);
              }}
              aria-label="Tillbaka"
            >
              ←
            </button>
            <h3 style={{ margin: 0, flex: 1 }}>{editingId ? "Redigera familjemedlem" : "Lägg till familjemedlem"}</h3>
          </div>
          <div className="family-member-form">
            <input
              type="text"
              placeholder="Namn"
              value={memberName}
              onChange={(e) => setMemberName(e.target.value)}
              className="daily-task-form-input"
            />
            {!editingId && (
              <div className="role-selector">
                <label className="role-option">
                  <input
                    type="radio"
                    name="role"
                    value="CHILD"
                    checked={memberRole === "CHILD"}
                    onChange={(e) => setMemberRole(e.target.value as FamilyMemberRole)}
                  />
                  <span>Barn</span>
                </label>
                <label className="role-option">
                  <input
                    type="radio"
                    name="role"
                    value="ASSISTANT"
                    checked={memberRole === "ASSISTANT"}
                    onChange={(e) => setMemberRole(e.target.value as FamilyMemberRole)}
                  />
                  <span>Äldre barn (kan skapa events, få djur)</span>
                </label>
                <label className="role-option">
                  <input
                    type="radio"
                    name="role"
                    value="PARENT"
                    checked={memberRole === "PARENT"}
                    onChange={(e) => setMemberRole(e.target.value as FamilyMemberRole)}
                  />
                  <span>Förälder</span>
                </label>
              </div>
            )}
            <div className="form-actions">
              <button
                type="button"
                onClick={() => {
                  if (editingId) {
                    void handleUpdate(editingId);
                  } else {
                    void handleCreate();
                  }
                }}
                className="button-primary"
              >
                {editingId ? "Spara" : "Skapa"}
              </button>
            </div>
          </div>
        </section>
      )}

      {inviteToken && inviteUrl && (
        <section className="card" ref={qrCodeRef}>
          <h3>QR-kod för inbjudan</h3>
          <p>
            {(() => {
              const member = members.find(m => m.id === inviteMemberId);
              if (member?.role === "PARENT") {
                return "Låt föräldern skanna denna QR-kod för att koppla sin enhet:";
              } else if (member?.role === "ASSISTANT") {
                return "Låt äldre barnet skanna denna QR-kod eller använd länken för att koppla sin enhet:";
              } else {
                return "Låt barnet skanna denna QR-kod för att koppla sin enhet:";
              }
            })()}
          </p>
          <div className="qr-code-container">
            <QRCodeSVG value={inviteUrl} size={256} />
          </div>
          <p className="invite-url">{inviteUrl}</p>
          <div style={{ display: "flex", gap: "8px", flexDirection: "column" }}>
            <button
              type="button"
              className="button-primary"
              onClick={() => {
                // Open in new tab/window to simulate child device
                window.open(inviteUrl, "_blank");
              }}
            >
              Öppna i ny flik (testa)
            </button>
            <button
              type="button"
              className="button-secondary"
              onClick={() => {
                setInviteToken(null);
                setInviteMemberId(null);
              }}
            >
              Stäng
            </button>
          </div>
        </section>
      )}

      <section className="card">
        {loading && <p>Laddar...</p>}
        {!loading && members.length === 0 && (
          <p className="placeholder-text">
            Inga familjemedlemmar skapade än. Skapa din första familjemedlem ovan!
          </p>
        )}

        {!loading && members.length > 0 && (
          <p style={{ fontSize: "0.85rem", color: "#6b6b6b", marginTop: "12px", marginBottom: "8px" }}>
            <strong>Tips:</strong> Huvudanvändaren (Admin) kan redigeras men inte tas bort. Du kan ändra namnet genom att klicka på "Redigera".
          </p>
        )}

        {!loading && members.length > 0 && (
          <ul className="family-members-list">
            {members.map((member) => {
              const isAdmin = member.id === "00000000-0000-0000-0000-000000000001";
              return (
              <li key={member.id} className="family-member-item">
                {editingId === member.id ? (
                  <div className="family-member-form">
                    <input
                      type="text"
                      value={memberName}
                      onChange={(e) => setMemberName(e.target.value)}
                      className="daily-task-form-input"
                    />
                    <div className="form-actions">
                      <button
                        type="button"
                        onClick={() => void handleUpdate(member.id)}
                        className="button-primary"
                      >
                        Spara
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setEditingId(null);
                          setMemberName("");
                        }}
                        className="button-secondary"
                      >
                        Avbryt
                      </button>
                    </div>
                  </div>
                ) : emailEditingId === member.id ? (
                  <div className="family-member-form">
                    <input
                      type="email"
                      placeholder="E-postadress (valfritt)"
                      value={newEmail}
                      onChange={(e) => setNewEmail(e.target.value)}
                      className="daily-task-form-input"
                    />
                    <p className="form-hint">
                      E-postadress används för inloggning med email + lösenord. Lämna tomt för att ta bort.
                    </p>
                    <div className="form-actions">
                      <button
                        type="button"
                        onClick={() => void handleUpdateEmail(member.id)}
                        className="button-primary"
                      >
                        Spara e-post
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setEmailEditingId(null);
                          setNewEmail("");
                        }}
                        className="button-secondary"
                      >
                        Avbryt
                      </button>
                    </div>
                  </div>
                ) : menstrualCycleSettingsId === member.id ? (
                  <div className="family-member-form">
                    <div style={{ marginBottom: "16px" }}>
                      <label style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "12px" }}>
                        <input
                          type="checkbox"
                          checked={menstrualCycleEnabled}
                          onChange={(e) => setMenstrualCycleEnabled(e.target.checked)}
                          style={{ width: "18px", height: "18px" }}
                        />
                        <span>Aktivera menscykel-spårning</span>
                      </label>
                      {menstrualCycleEnabled && (
                        <div style={{ marginLeft: "26px", marginTop: "12px" }}>
                          <p style={{ marginBottom: "8px", fontSize: "0.9rem", color: "#666" }}>
                            Synlighet:
                          </p>
                          <label style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
                            <input
                              type="radio"
                              name={`menstrual-cycle-privacy-${member.id}`}
                              checked={menstrualCyclePrivate}
                              onChange={() => setMenstrualCyclePrivate(true)}
                              style={{ width: "16px", height: "16px" }}
                            />
                            <span>Privat (bara synlig för mig)</span>
                          </label>
                          <label style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                            <input
                              type="radio"
                              name={`menstrual-cycle-privacy-${member.id}`}
                              checked={!menstrualCyclePrivate}
                              onChange={() => setMenstrualCyclePrivate(false)}
                              style={{ width: "16px", height: "16px" }}
                            />
                            <span>Delad med andra vuxna i familjen</span>
                          </label>
                        </div>
                      )}
                    </div>
                    <div className="form-actions">
                      <button
                        type="button"
                        onClick={() => void handleUpdateMenstrualCycleSettings(member.id)}
                        className="button-primary"
                      >
                        Spara inställningar
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setMenstrualCycleSettingsId(null);
                          setMenstrualCycleEnabled(false);
                          setMenstrualCyclePrivate(true);
                        }}
                        className="button-secondary"
                      >
                        Avbryt
                      </button>
                    </div>
                  </div>
                ) : passwordEditingId === member.id ? (
                  <div className="family-member-form">
                    <div style={{ position: "relative" }}>
                      <input
                        type={showNewPassword ? "text" : "password"}
                        placeholder="Nytt lösenord (minst 6 tecken)"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        className="daily-task-form-input"
                        minLength={6}
                        style={{ paddingRight: "40px", width: "100%" }}
                      />
                      <button
                        type="button"
                        onClick={() => setShowNewPassword(!showNewPassword)}
                        style={{
                          position: "absolute",
                          right: "8px",
                          top: "50%",
                          transform: "translateY(-50%)",
                          background: "none",
                          border: "none",
                          cursor: "pointer",
                          fontSize: "14px",
                          color: "#666",
                          padding: "4px 8px"
                        }}
                        aria-label={showNewPassword ? "Dölj lösenord" : "Visa lösenord"}
                      >
                        {showNewPassword ? "🙈" : "👁️"}
                      </button>
                    </div>
                    <div style={{ position: "relative" }}>
                      <input
                        type={showNewPasswordConfirm ? "text" : "password"}
                        placeholder="Bekräfta lösenord"
                        value={newPasswordConfirm}
                        onChange={(e) => setNewPasswordConfirm(e.target.value)}
                        className="daily-task-form-input"
                        minLength={6}
                        style={{ paddingRight: "40px", width: "100%" }}
                      />
                      <button
                        type="button"
                        onClick={() => setShowNewPasswordConfirm(!showNewPasswordConfirm)}
                        style={{
                          position: "absolute",
                          right: "8px",
                          top: "50%",
                          transform: "translateY(-50%)",
                          background: "none",
                          border: "none",
                          cursor: "pointer",
                          fontSize: "14px",
                          color: "#666",
                          padding: "4px 8px"
                        }}
                        aria-label={showNewPasswordConfirm ? "Dölj lösenord" : "Visa lösenord"}
                      >
                        {showNewPasswordConfirm ? "🙈" : "👁️"}
                      </button>
                    </div>
                    <div className="form-actions">
                      <button
                        type="button"
                        onClick={() => void handleUpdatePassword(member.id)}
                        className="button-primary"
                      >
                        Spara lösenord
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setPasswordEditingId(null);
                          setNewPassword("");
                          setNewPasswordConfirm("");
                          setShowNewPassword(false);
                          setShowNewPasswordConfirm(false);
                        }}
                        className="button-secondary"
                      >
                        Avbryt
                      </button>
                    </div>
                  </div>
                ) : petSettingsId === member.id ? (
                  <div className="family-member-form">
                    <div style={{ marginBottom: "16px" }}>
                      <label style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "12px" }}>
                        <input
                          type="checkbox"
                          checked={petEnabled}
                          onChange={(e) => setPetEnabled(e.target.checked)}
                          style={{ width: "18px", height: "18px" }}
                        />
                        <span>Aktivera djur för denna vuxen</span>
                      </label>
                      <p style={{ marginLeft: "26px", fontSize: "0.85rem", color: "#666", marginTop: "8px" }}>
                        När djur är aktiverat kan vuxna få djur, samla XP och mata sina djur precis som barnen.
                      </p>
                    </div>
                    <div className="form-actions">
                      <button
                        type="button"
                        onClick={() => void handleUpdatePetSettings(member.id)}
                        className="button-primary"
                      >
                        Spara inställningar
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setPetSettingsId(null);
                          setPetEnabled(false);
                        }}
                        className="button-secondary"
                      >
                        Avbryt
                      </button>
                    </div>
                  </div>
                ) : (
                  <>
                    <div className="family-member-content">
                      <div style={{ display: "flex", alignItems: "center", gap: "12px", flex: 1 }}>
                        {/* Role icon with visual hierarchy - simple monochrome */}
                        <div style={{ 
                          display: "flex", 
                          flexDirection: "column", 
                          alignItems: "center", 
                          justifyContent: "flex-end",
                          gap: "2px",
                          minWidth: "32px"
                        }}>
                          {(() => {
                            const role = isAdmin ? "PARENT" : member.role;
                            if (role === "PARENT") {
                              return (
                                <div style={{ 
                                  fontSize: "1.8rem", 
                                  lineHeight: 1,
                                  color: "#3a3a3a",
                                  fontWeight: 300
                                }} title="Förälder">
                                  ●
                                </div>
                              );
                            } else if (role === "ASSISTANT") {
                              return (
                                <div style={{ 
                                  fontSize: "1.4rem", 
                                  lineHeight: 1,
                                  color: "#3a3a3a",
                                  fontWeight: 300
                                }} title="Äldre barn">
                                  ●
                                </div>
                              );
                            } else {
                              return (
                                <div style={{ 
                                  fontSize: "1.1rem", 
                                  lineHeight: 1,
                                  color: "#3a3a3a",
                                  fontWeight: 300
                                }} title="Barn">
                                  ●
                                </div>
                              );
                            }
                          })()}
                        </div>
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <h4 style={{ margin: 0, marginBottom: "4px" }}>{member.name}</h4>
                          <div style={{ display: "flex", alignItems: "center", gap: "8px", flexWrap: "wrap" }}>
                            {/* Email icon */}
                            {member.email && (
                              <span style={{ 
                                fontSize: "0.75rem", 
                                color: "#666",
                                display: "flex",
                                alignItems: "center",
                                gap: "4px"
                              }} title={member.email}>
                                <span style={{ fontSize: "0.7rem", color: "#666" }}>@</span>
                                <span style={{ maxWidth: "150px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{member.email}</span>
                              </span>
                            )}
                            {/* Device status icon */}
                            <span style={{ 
                              fontSize: "0.75rem",
                              display: "flex",
                              alignItems: "center",
                              gap: "4px"
                            }} title={member.deviceToken ? "Enhet kopplad" : "Enhet ej kopplad"}>
                              <span style={{ 
                                fontSize: "0.6rem",
                                color: member.deviceToken ? "#2d5a2d" : "#999",
                                fontWeight: 600
                              }}>
                                {member.deviceToken ? "●" : "○"}
                              </span>
                              <span style={{ color: member.deviceToken ? "#2d5a2d" : "#8b4a3a", fontSize: "0.7rem" }}>
                                {member.deviceToken ? "Kopplad" : "Ej kopplad"}
                              </span>
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div className="family-member-actions" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", width: "100%" }}>
                      {/* Left side - Primary actions */}
                      <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                        {!isAdmin && (
                          <>
                            {/* QR/Invite button with text */}
                            {(member.role === "CHILD" || member.role === "PARENT" || member.role === "ASSISTANT") && (
                              <button
                                type="button"
                                className="button-secondary"
                                onClick={() => void handleGenerateInvite(member.id)}
                                style={{ 
                                  padding: "8px 14px",
                                  fontSize: "0.85rem",
                                  display: "flex",
                                  alignItems: "center",
                                  gap: "6px"
                                }}
                              >
                                <span style={{ fontSize: "0.9rem", color: "#3a3a3a" }}>◉</span>
                                <span>{member.role === "ASSISTANT" ? "QR-kod" : member.role === "PARENT" ? "Bjud in" : "QR-kod"}</span>
                              </button>
                            )}
                          </>
                        )}
                        
                        {/* Edit menu button - clean icon */}
                        <div style={{ position: "relative" }}>
                          <button
                            type="button"
                            className="button-secondary"
                            onClick={() => setOpenMenuId(openMenuId === member.id ? null : member.id)}
                            title="Redigera"
                            aria-label="Redigera familjemedlem"
                            aria-expanded={openMenuId === member.id}
                            aria-haspopup="true"
                            style={{ 
                              width: "44px", 
                              height: "44px",
                              padding: 0,
                              display: "flex",
                              alignItems: "center",
                              justifyContent: "center",
                              fontSize: "1.1rem",
                              color: "#2563eb",
                              borderColor: "rgba(37, 99, 235, 0.3)",
                              backgroundColor: "rgba(37, 99, 235, 0.05)",
                              fontWeight: 300
                            }}
                          >
                            ✎
                          </button>
                          {openMenuId === member.id && (
                            <>
                              <div 
                                className="todo-menu-backdrop" 
                                onClick={() => setOpenMenuId(null)} 
                              />
                              <div className="todo-menu-dropdown" style={{ right: 0, left: "auto", minWidth: "180px" }}>
                                {/* Edit name */}
                                <button
                                  type="button"
                                  className="todo-menu-item"
                                  onClick={() => {
                                    setEditingId(member.id);
                                    setMemberName(member.name);
                                    setShowCreateForm(false);
                                    setOpenMenuId(null);
                                  }}
                                >
                                  <span style={{ marginRight: "8px", color: "#666" }}>✎</span>
                                  Redigera namn
                                </button>
                                
                                {/* Email options - only for PARENT and ASSISTANT */}
                                {(member.role === "PARENT" || member.role === "ASSISTANT" || (member.role === "PARENT" && isAdmin)) && (
                                  <button
                                    type="button"
                                    className="todo-menu-item"
                                    onClick={() => {
                                      setEmailEditingId(member.id);
                                      setEditingId(null);
                                      setPasswordEditingId(null);
                                      setShowCreateForm(false);
                                      setNewEmail(member.email || "");
                                      setOpenMenuId(null);
                                    }}
                                  >
                                    <span style={{ marginRight: "8px", color: "#666" }}>@</span>
                                    {member.email ? "Ändra e-post" : "Lägg till e-post"}
                                  </button>
                                )}
                                
                                {/* Password options - only for PARENT and ASSISTANT */}
                                {(member.role === "PARENT" || member.role === "ASSISTANT" || (member.role === "PARENT" && isAdmin)) && (
                                  <button
                                    type="button"
                                    className="todo-menu-item"
                                    onClick={() => {
                                      setPasswordEditingId(member.id);
                                      setEditingId(null);
                                      setEmailEditingId(null);
                                      setMenstrualCycleSettingsId(null);
                                      setShowCreateForm(false);
                                      setNewPassword("");
                                      setNewPasswordConfirm("");
                                      setOpenMenuId(null);
                                    }}
                                  >
                                    <span style={{ marginRight: "8px", color: "#666" }}>🔐</span>
                                    Ändra lösenord
                                  </button>
                                )}
                                
                                {/* Menstrual cycle settings - only for PARENT and ASSISTANT */}
                                {(member.role === "PARENT" || member.role === "ASSISTANT" || (member.role === "PARENT" && isAdmin)) && (
                                  <button
                                    type="button"
                                    className="todo-menu-item"
                                    onClick={() => {
                                      setMenstrualCycleSettingsId(member.id);
                                      setEditingId(null);
                                      setEmailEditingId(null);
                                      setPasswordEditingId(null);
                                      setShowCreateForm(false);
                                      setMenstrualCycleEnabled(member.menstrualCycleEnabled || false);
                                      setMenstrualCyclePrivate(member.menstrualCyclePrivate !== false);
                                      setOpenMenuId(null);
                                    }}
                                  >
                                    <span style={{ marginRight: "8px", color: "#666" }}>🩸</span>
                                    Menscykel-inställningar
                                  </button>
                                )}
                                
                                {/* Pet settings - only for PARENT */}
                                {member.role === "PARENT" && (
                                  <button
                                    type="button"
                                    className="todo-menu-item"
                                    onClick={() => {
                                      setPetSettingsId(member.id);
                                      setPetEnabled(member.petEnabled || false);
                                      setEditingId(null);
                                      setPasswordEditingId(null);
                                      setEmailEditingId(null);
                                      setMenstrualCycleSettingsId(null);
                                      setShowCreateForm(false);
                                      setOpenMenuId(null);
                                    }}
                                  >
                                    <span style={{ marginRight: "8px", color: "#666" }}>🐾</span>
                                    Djur-inställningar
                                  </button>
                                )}
                                
                                {/* Delete option in menu */}
                                {!isAdmin && (
                                  <button
                                    type="button"
                                    className="todo-menu-item todo-menu-item-danger"
                                    onClick={() => {
                                      void handleDelete(member.id);
                                      setOpenMenuId(null);
                                    }}
                                  >
                                    <span style={{ marginRight: "8px" }}>×</span>
                                    Ta bort
                                  </button>
                                )}
                              </div>
                            </>
                          )}
                        </div>
                      </div>
                      
                      {/* Right side - Delete button, right-aligned */}
                      {!isAdmin && (
                        <button
                          type="button"
                          className="button-secondary"
                          onClick={() => void handleDelete(member.id)}
                          title="Ta bort"
                          aria-label={`Ta bort familjemedlem ${member.name}`}
                          style={{ 
                            padding: "8px 12px",
                            fontSize: "1.3rem",
                            minWidth: "44px",
                            opacity: 0.6,
                            borderColor: "rgba(200, 100, 100, 0.2)",
                            marginLeft: "auto",
                            color: "#999",
                            fontWeight: 300,
                            lineHeight: 1
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.opacity = "1";
                            e.currentTarget.style.borderColor = "rgba(200, 100, 100, 0.5)";
                            e.currentTarget.style.color = "#c55a5a";
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.opacity = "0.6";
                            e.currentTarget.style.borderColor = "rgba(200, 100, 100, 0.2)";
                            e.currentTarget.style.color = "#999";
                          }}
                        >
                          ×
                        </button>
                      )}
                    </div>
                  </>
                )}
              </li>
            );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}


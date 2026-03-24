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
  updatePetSettings,
} from "../../shared/api/familyMembers";
import { updateMenstrualCycleSettings } from "../../shared/api/menstrualCycle";
import { createDailyChore } from "../../shared/api/dailyChores";
import { GiveAllowanceDialog } from "../wallet/GiveAllowanceDialog";
import { AGE_GROUPS, TASK_SUGGESTIONS, AgeGroup } from "./taskSuggestions";

type FamilyMembersViewProps = {
  onNavigate?: (view: string) => void;
};

type ActiveFormType = "name" | "email" | "password";

export function FamilyMembersView({ onNavigate }: FamilyMembersViewProps) {
  const [members, setMembers] = useState<FamilyMemberResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [memberName, setMemberName] = useState("");
  const [memberRole, setMemberRole] = useState<FamilyMemberRole>("CHILD");
  const [inviteToken, setInviteToken] = useState<string | null>(null);
  const [inviteMemberId, setInviteMemberId] = useState<string | null>(null);
  const [activeForm, setActiveForm] = useState<{ memberId: string; type: ActiveFormType } | null>(null);
  const [newPassword, setNewPassword] = useState("");
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [newPasswordConfirm, setNewPasswordConfirm] = useState("");
  const [showNewPasswordConfirm, setShowNewPasswordConfirm] = useState(false);
  const [newEmail, setNewEmail] = useState("");
  const [allowanceDialogMember, setAllowanceDialogMember] = useState<{ id: string; name: string } | null>(null);
  const [memberAgeGroup, setMemberAgeGroup] = useState<AgeGroup | "">("");
  const [suggestions, setSuggestions] = useState<{ memberId: string; memberName: string; ageGroup: AgeGroup; checked: Set<string> } | null>(null);
  const [creatingTasks, setCreatingTasks] = useState(false);
  const qrCodeRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    void loadMembers();
  }, []);

  useEffect(() => {
    if (inviteToken && qrCodeRef.current) {
      const timeoutId = setTimeout(() => {
        qrCodeRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
      }, 100);
      return () => clearTimeout(timeoutId);
    }
  }, [inviteToken]);

  const loadMembers = async () => {
    try {
      setLoading(true);
      const data = await fetchAllFamilyMembers();
      setMembers(data);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Kunde inte hämta familjemedlemmar.";
      if (msg.includes("401") || msg.includes("Unauthorized")) {
        setError("Du är inte inloggad. Logga in och försök igen.");
      } else if (msg.includes("403") || msg.includes("Forbidden")) {
        setError("Du har inte behörighet att se familjemedlemmar.");
      } else if (msg.includes("Network") || msg.includes("Failed to fetch")) {
        setError("Kunde inte ansluta till servern. Kontrollera din internetanslutning.");
      } else {
        setError(msg);
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
      const created = await createFamilyMember(memberName.trim(), memberRole);
      await loadMembers();
      const nameForSuggestions = memberName.trim();
      const ageGroup = memberAgeGroup;
      setMemberName("");
      setMemberRole("CHILD");
      setMemberAgeGroup("");
      setShowCreateForm(false);
      setError(null);
      // Show task suggestions if age was selected and role can have tasks
      if (ageGroup && (memberRole === "CHILD" || memberRole === "ASSISTANT")) {
        const tasks = TASK_SUGGESTIONS[ageGroup];
        setSuggestions({
          memberId: created.id,
          memberName: nameForSuggestions,
          ageGroup,
          checked: new Set(tasks),
        });
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Kunde inte skapa familjemedlem.";
      if (msg.includes("already exists") || msg.includes("finns redan")) {
        setError("En familjemedlem med detta namn finns redan.");
      } else if (msg.includes("401") || msg.includes("Unauthorized")) {
        setError("Du är inte inloggad. Logga in och försök igen.");
      } else {
        setError(msg);
      }
    }
  };

  const handleAddSuggestedTasks = async () => {
    if (!suggestions) return;
    setCreatingTasks(true);
    const allWeekdays = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
    try {
      await Promise.all(
        [...suggestions.checked].map((title) =>
          createDailyChore(suggestions.memberId, title, allWeekdays, 1)
        )
      );
      setSuggestions(null);
    } catch {
      setError("Kunde inte skapa uppgifter. Försök igen.");
    } finally {
      setCreatingTasks(false);
    }
  };

  const handleUpdate = async (memberId: string) => {
    if (!memberName.trim()) {
      setError("Namn krävs.");
      return;
    }
    try {
      const updatedMember = await updateFamilyMember(memberId, memberName.trim());
      setMembers(prev => prev.map(m => m.id === memberId ? updatedMember : m));
      closeForm();
    } catch (e) {
      await loadMembers();
      const msg = e instanceof Error ? e.message : "Kunde inte uppdatera familjemedlem.";
      if (msg.includes("401") || msg.includes("Unauthorized")) {
        setError("Du är inte inloggad. Logga in och försök igen.");
      } else if (msg.includes("404") || msg.includes("Not Found")) {
        setError("Familjemedlemmen hittades inte. Den kan ha tagits bort.");
      } else {
        setError(msg);
      }
    }
  };

  const handleDelete = async (memberId: string) => {
    if (!confirm("Är du säker på att du vill ta bort denna familjemedlem?")) return;
    try {
      await deleteFamilyMember(memberId);
      await loadMembers();
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Kunde inte ta bort familjemedlem.";
      if (msg.includes("401") || msg.includes("Unauthorized")) {
        setError("Du är inte inloggad. Logga in och försök igen.");
      } else if (msg.includes("403") || msg.includes("Forbidden")) {
        setError("Du har inte behörighet att ta bort denna familjemedlem.");
      } else if (msg.includes("404") || msg.includes("Not Found")) {
        setError("Familjemedlemmen hittades inte. Den kan redan ha tagits bort.");
      } else {
        setError(msg);
      }
    }
  };

  const handleGenerateInvite = async (memberId: string) => {
    try {
      const token = await generateInviteToken(memberId);
      setInviteToken(token);
      setInviteMemberId(memberId);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Kunde inte generera inbjudan.";
      if (msg.includes("401") || msg.includes("Unauthorized")) {
        setError("Du är inte inloggad. Logga in och försök igen.");
      } else if (msg.includes("403") || msg.includes("Forbidden")) {
        setError("Du har inte behörighet att generera inbjudningar.");
      } else if (msg.includes("404") || msg.includes("Not Found")) {
        setError("Familjemedlemmen hittades inte.");
      } else {
        setError(msg);
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
      const updatedMember = await updateFamilyMemberPassword(memberId, newPassword);
      setMembers(prev => prev.map(m => m.id === memberId ? updatedMember : m));
      closeForm();
    } catch (e) {
      await loadMembers();
      const msg = e instanceof Error ? e.message : "Kunde inte uppdatera lösenord.";
      setError(msg);
    }
  };

  const handleUpdateEmail = async (memberId: string) => {
    const trimmedEmail = newEmail.trim();
    if (trimmedEmail) {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(trimmedEmail)) {
        setError("Ogiltig e-postadress. Ange en giltig e-postadress (t.ex. namn@exempel.se).");
        return;
      }
    }
    try {
      const updatedMember = await updateFamilyMemberEmail(memberId, trimmedEmail || "");
      setMembers(prev => prev.map(m => m.id === memberId ? updatedMember : m));
      closeForm();
    } catch (e) {
      await loadMembers();
      const msg = e instanceof Error ? e.message : "Kunde inte uppdatera e-postadress.";
      if (msg.includes("already in use") || msg.includes("används redan")) {
        setError("Denna e-postadress används redan av ett annat konto.");
      } else if (msg.includes("Invalid") || msg.includes("Ogiltig")) {
        setError("Ogiltig e-postadress. Kontrollera att adressen är korrekt.");
      } else {
        setError(msg);
      }
    }
  };

  const handleToggleMenstrualCycle = async (member: FamilyMemberResponse, enabled: boolean) => {
    // Optimistic update
    setMembers(prev => prev.map(m => m.id === member.id ? { ...m, menstrualCycleEnabled: enabled } : m));
    localStorage.setItem("menstrualCycleEnabled", String(enabled));
    try {
      await updateMenstrualCycleSettings(member.id, enabled, member.menstrualCyclePrivate !== false);
    } catch (e) {
      // Revert on error
      setMembers(prev => prev.map(m => m.id === member.id ? { ...m, menstrualCycleEnabled: !enabled } : m));
      localStorage.setItem("menstrualCycleEnabled", String(!enabled));
      setError("Kunde inte uppdatera menscykel-inställningar.");
    }
  };

  const handleTogglePet = async (member: FamilyMemberResponse, enabled: boolean) => {
    // Optimistic update
    setMembers(prev => prev.map(m => m.id === member.id ? { ...m, petEnabled: enabled } : m));
    try {
      await updatePetSettings(member.id, enabled);
    } catch (e) {
      // Revert on error
      setMembers(prev => prev.map(m => m.id === member.id ? { ...m, petEnabled: !enabled } : m));
      setError("Kunde inte uppdatera djur-inställningar.");
    }
  };

  const openForm = (memberId: string, type: ActiveFormType, initialValue?: string) => {
    setActiveForm({ memberId, type });
    setError(null);
    if (type === "name") setMemberName(initialValue ?? "");
    if (type === "email") setNewEmail(initialValue ?? "");
    if (type === "password") {
      setNewPassword("");
      setNewPasswordConfirm("");
      setShowNewPassword(false);
      setShowNewPasswordConfirm(false);
    }
  };

  const closeForm = () => {
    setActiveForm(null);
    setMemberName("");
    setNewEmail("");
    setNewPassword("");
    setNewPasswordConfirm("");
    setError(null);
  };

  const inviteUrl = inviteToken ? `${window.location.origin}/invite/${inviteToken}` : null;

  const roleLabel = (role: FamilyMemberRole, isAdmin: boolean): string => {
    if (isAdmin) return "Admin";
    if (role === "PARENT") return "Förälder";
    if (role === "ASSISTANT") return "Äldre barn";
    return "Barn";
  };

  const roleBadgeClass = (role: FamilyMemberRole, isAdmin: boolean): string => {
    if (isAdmin || role === "PARENT") return "role-badge role-badge-parent";
    if (role === "ASSISTANT") return "role-badge role-badge-assistant";
    return "role-badge role-badge-child";
  };

  const btnStyle: React.CSSProperties = { fontSize: "0.8rem", padding: "6px 10px" };
  const btnDangerStyle: React.CSSProperties = {
    ...btnStyle,
    color: "#c55a5a",
    borderColor: "rgba(200,100,100,0.3)",
  };

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
                setMemberName("");
                setError(null);
              }}
              aria-label="Tillbaka"
            >
              ←
            </button>
            <h3 style={{ margin: 0, flex: 1 }}>Lägg till familjemedlem</h3>
          </div>
          <div className="family-member-form">
            <input
              type="text"
              placeholder="Namn"
              value={memberName}
              onChange={(e) => setMemberName(e.target.value)}
              className="daily-task-form-input"
            />
            <div className="role-selector">
              <label className="role-option">
                <input type="radio" name="role" value="CHILD" checked={memberRole === "CHILD"} onChange={() => setMemberRole("CHILD")} />
                <span>Barn</span>
              </label>
              <label className="role-option">
                <input type="radio" name="role" value="PARENT" checked={memberRole === "PARENT"} onChange={() => setMemberRole("PARENT")} />
                <span>Förälder</span>
              </label>
            </div>
            {(memberRole === "CHILD" || memberRole === "ASSISTANT") && (
              <div>
                <p style={{ fontSize: "0.85rem", color: "#555", margin: "0 0 6px" }}>
                  Ålder (valfritt — föreslår dagliga uppgifter)
                </p>
                <div className="role-selector">
                  <label className="role-option">
                    <input type="radio" name="ageGroup" value="" checked={memberAgeGroup === ""} onChange={() => setMemberAgeGroup("")} />
                    <span>Ingen</span>
                  </label>
                  {AGE_GROUPS.map((g) => (
                    <label key={g.value} className="role-option">
                      <input type="radio" name="ageGroup" value={g.value} checked={memberAgeGroup === g.value} onChange={() => setMemberAgeGroup(g.value)} />
                      <span>{g.label}</span>
                    </label>
                  ))}
                </div>
              </div>
            )}
            <div className="form-actions">
              <button type="button" onClick={() => void handleCreate()} className="button-primary">
                Skapa
              </button>
            </div>
          </div>
        </section>
      )}

      {suggestions && (
        <section className="card">
          <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "12px" }}>
            <button
              type="button"
              className="back-button"
              onClick={() => setSuggestions(null)}
              aria-label="Hoppa över"
            >
              ←
            </button>
            <h3 style={{ margin: 0, flex: 1 }}>Föreslagna uppgifter för {suggestions.memberName}</h3>
          </div>
          <p style={{ fontSize: "0.85rem", color: "#666", marginTop: 0, marginBottom: "14px" }}>
            Välj vilka dagliga uppgifter du vill lägga till. Alla är förbockade — avbocka de du inte vill ha.
          </p>
          <ul style={{ margin: 0, padding: 0, listStyle: "none", display: "flex", flexDirection: "column", gap: "4px" }}>
            {TASK_SUGGESTIONS[suggestions.ageGroup].map((task) => {
              const isChecked = suggestions.checked.has(task);
              return (
                <li key={task}>
                  <label style={{ display: "flex", alignItems: "center", gap: "10px", padding: "8px 4px", cursor: "pointer" }}>
                    <input
                      type="checkbox"
                      checked={isChecked}
                      onChange={() => {
                        setSuggestions(prev => {
                          if (!prev) return prev;
                          const next = new Set(prev.checked);
                          if (isChecked) next.delete(task); else next.add(task);
                          return { ...prev, checked: next };
                        });
                      }}
                      style={{ width: "18px", height: "18px", flexShrink: 0 }}
                    />
                    <span style={{ fontSize: "0.92rem" }}>{task}</span>
                  </label>
                </li>
              );
            })}
          </ul>
          <p style={{ fontSize: "0.78rem", color: "#888", margin: "12px 0 16px" }}>
            Uppgifterna skapas som dagliga återkommande uppgifter (mån–sön).
          </p>
          <div className="form-actions">
            <button
              type="button"
              className="button-primary"
              onClick={() => void handleAddSuggestedTasks()}
              disabled={creatingTasks || suggestions.checked.size === 0}
            >
              {creatingTasks ? "Skapar..." : `Lägg till ${suggestions.checked.size} uppgift${suggestions.checked.size !== 1 ? "er" : ""}`}
            </button>
            <button type="button" className="button-secondary" onClick={() => setSuggestions(null)}>
              Hoppa över
            </button>
          </div>
        </section>
      )}

      {inviteToken && inviteUrl && (
        <section className="card" ref={qrCodeRef}>
          <h3>QR-kod för inbjudan</h3>
          <p>
            {(() => {
              const member = members.find(m => m.id === inviteMemberId);
              if (member?.role === "PARENT") return "Låt föräldern skanna denna QR-kod för att koppla sin enhet:";
              if (member?.role === "ASSISTANT") return "Låt äldre barnet skanna denna QR-kod eller använd länken för att koppla sin enhet:";
              return "Låt barnet skanna denna QR-kod för att koppla sin enhet:";
            })()}
          </p>
          <div className="qr-code-container">
            <QRCodeSVG value={inviteUrl} size={256} />
          </div>
          <p className="invite-url">{inviteUrl}</p>
          <div style={{ display: "flex", gap: "8px", flexDirection: "column" }}>
            <button type="button" className="button-primary" onClick={() => window.open(inviteUrl, "_blank")}>
              Öppna i ny flik (testa)
            </button>
            <button type="button" className="button-secondary" onClick={() => { setInviteToken(null); setInviteMemberId(null); }}>
              Stäng
            </button>
          </div>
        </section>
      )}

      <section className="card">
        {loading && <p>Laddar...</p>}
        {!loading && members.length === 0 && (
          <p className="placeholder-text">Inga familjemedlemmar skapade än.</p>
        )}
        {!loading && members.length > 0 && (
          <p style={{ fontSize: "0.85rem", color: "#6b6b6b", marginTop: "12px", marginBottom: "8px" }}>
            <strong>Tips:</strong> Huvudanvändaren (Admin) kan redigeras men inte tas bort.
          </p>
        )}

        {!loading && members.length > 0 && (
          <ul className="family-members-list">
            {members.map((member) => {
              const isAdmin = member.id === "00000000-0000-0000-0000-000000000001";
              const isExpanded = activeForm?.memberId === member.id;
              const canEditCredentials = member.role === "PARENT" || member.role === "ASSISTANT";

              return (
                <li key={member.id} className="family-member-item" style={{ flexDirection: "column", alignItems: "stretch", gap: "8px" }}>

                  {/* Header: name + badge + status */}
                  <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: "8px", flexWrap: "wrap" }}>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: "8px", flexWrap: "wrap" }}>
                        <span style={{ fontWeight: 700, fontSize: "1rem" }}>{member.name}</span>
                        <span className={roleBadgeClass(member.role, isAdmin)}>{roleLabel(member.role, isAdmin)}</span>
                        <span style={{ fontSize: "0.72rem", color: member.deviceToken ? "#2d7a2d" : "#999", whiteSpace: "nowrap" }}>
                          {member.deviceToken ? "● Kopplad" : "○ Ej kopplad"}
                        </span>
                      </div>
                      {member.email && (
                        <div style={{ fontSize: "0.78rem", color: "#777", marginTop: "2px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", maxWidth: "220px" }}>
                          {member.email}
                        </div>
                      )}
                    </div>
                    {!isAdmin && (
                      <button
                        type="button"
                        onClick={() => void handleDelete(member.id)}
                        aria-label={`Ta bort ${member.name}`}
                        style={{ background: "none", border: "none", cursor: "pointer", fontSize: "1.2rem", color: "#bbb", padding: "0 2px", lineHeight: 1, flexShrink: 0 }}
                        onMouseEnter={(e) => { e.currentTarget.style.color = "#c55"; }}
                        onMouseLeave={(e) => { e.currentTarget.style.color = "#bbb"; }}
                      >
                        ×
                      </button>
                    )}
                  </div>

                  {/* Toggles (PARENT/ASSISTANT only) */}
                  {canEditCredentials && (
                    <div style={{ display: "flex", gap: "20px", flexWrap: "wrap" }}>
                      <label className="toggle-switch-label">
                        <input
                          type="checkbox"
                          className="toggle-switch-input"
                          checked={member.menstrualCycleEnabled || false}
                          onChange={(e) => void handleToggleMenstrualCycle(member, e.target.checked)}
                        />
                        <span className="toggle-switch-track" />
                        🩸 Menscykel
                      </label>
                      {member.role === "PARENT" && (
                        <label className="toggle-switch-label">
                          <input
                            type="checkbox"
                            className="toggle-switch-input"
                            checked={member.petEnabled || false}
                            onChange={(e) => void handleTogglePet(member, e.target.checked)}
                          />
                          <span className="toggle-switch-track" />
                          🐾 Djur
                        </label>
                      )}
                    </div>
                  )}

                  {/* Action buttons */}
                  <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
                    {!isAdmin && (
                      <button type="button" className="button-secondary" style={btnStyle}
                        onClick={() => void handleGenerateInvite(member.id)}>
                        QR-kod
                      </button>
                    )}
                    <button
                      type="button"
                      className={`button-secondary${isExpanded && activeForm?.type === "name" ? " button-secondary--active" : ""}`}
                      style={btnStyle}
                      onClick={() => isExpanded && activeForm?.type === "name" ? closeForm() : openForm(member.id, "name", member.name)}
                    >
                      Namn
                    </button>
                    {canEditCredentials && (
                      <>
                        <button
                          type="button"
                          className={`button-secondary${isExpanded && activeForm?.type === "email" ? " button-secondary--active" : ""}`}
                          style={btnStyle}
                          onClick={() => isExpanded && activeForm?.type === "email" ? closeForm() : openForm(member.id, "email", member.email || "")}
                        >
                          E-post
                        </button>
                        <button
                          type="button"
                          className={`button-secondary${isExpanded && activeForm?.type === "password" ? " button-secondary--active" : ""}`}
                          style={btnStyle}
                          onClick={() => isExpanded && activeForm?.type === "password" ? closeForm() : openForm(member.id, "password")}
                        >
                          Lösenord
                        </button>
                      </>
                    )}
                    {(member.role === "CHILD" || member.role === "ASSISTANT") && (
                      <button type="button" className="button-secondary" style={btnStyle}
                        onClick={() => setAllowanceDialogMember({ id: member.id, name: member.name })}>
                        💰 Pengar
                      </button>
                    )}
                  </div>

                  {/* Expanded form: name */}
                  {isExpanded && activeForm?.type === "name" && (
                    <div className="family-member-form">
                      <input type="text" value={memberName} onChange={(e) => setMemberName(e.target.value)} className="daily-task-form-input" placeholder="Namn" />
                      <div className="form-actions">
                        <button type="button" className="button-primary" onClick={() => void handleUpdate(member.id)}>Spara</button>
                        <button type="button" className="button-secondary" onClick={closeForm}>Avbryt</button>
                      </div>
                    </div>
                  )}

                  {/* Expanded form: email */}
                  {isExpanded && activeForm?.type === "email" && (
                    <div className="family-member-form">
                      <input type="email" placeholder="E-postadress (valfritt)" value={newEmail} onChange={(e) => setNewEmail(e.target.value)} className="daily-task-form-input" />
                      <p className="form-hint">Lämna tomt för att ta bort e-post.</p>
                      <div className="form-actions">
                        <button type="button" className="button-primary" onClick={() => void handleUpdateEmail(member.id)}>Spara</button>
                        <button type="button" className="button-secondary" onClick={closeForm}>Avbryt</button>
                      </div>
                    </div>
                  )}

                  {/* Expanded form: password */}
                  {isExpanded && activeForm?.type === "password" && (
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
                        <button type="button" onClick={() => setShowNewPassword(!showNewPassword)}
                          style={{ position: "absolute", right: "8px", top: "50%", transform: "translateY(-50%)", background: "none", border: "none", cursor: "pointer", fontSize: "14px", color: "#666", padding: "4px 8px" }}
                          aria-label={showNewPassword ? "Dölj lösenord" : "Visa lösenord"}>
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
                        <button type="button" onClick={() => setShowNewPasswordConfirm(!showNewPasswordConfirm)}
                          style={{ position: "absolute", right: "8px", top: "50%", transform: "translateY(-50%)", background: "none", border: "none", cursor: "pointer", fontSize: "14px", color: "#666", padding: "4px 8px" }}
                          aria-label={showNewPasswordConfirm ? "Dölj lösenord" : "Visa lösenord"}>
                          {showNewPasswordConfirm ? "🙈" : "👁️"}
                        </button>
                      </div>
                      <div className="form-actions">
                        <button type="button" className="button-primary" onClick={() => void handleUpdatePassword(member.id)}>Spara</button>
                        <button type="button" className="button-secondary" onClick={closeForm}>Avbryt</button>
                      </div>
                    </div>
                  )}

                </li>
              );
            })}
          </ul>
        )}
      </section>

      {allowanceDialogMember && (
        <GiveAllowanceDialog
          childName={allowanceDialogMember.name}
          childMemberId={allowanceDialogMember.id}
          onClose={() => setAllowanceDialogMember(null)}
          onSuccess={() => setAllowanceDialogMember(null)}
        />
      )}
    </div>
  );
}

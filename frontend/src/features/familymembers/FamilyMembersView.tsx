import { useEffect, useState } from "react";
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

  useEffect(() => {
    void loadMembers();
  }, []);

  const loadMembers = async () => {
    try {
      setLoading(true);
      const data = await fetchAllFamilyMembers();
      setMembers(data);
    } catch (e) {
      setError("Kunde inte hämta familjemedlemmar.");
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
    } catch {
      setError("Kunde inte skapa familjemedlem.");
    }
  };

  const handleUpdate = async (memberId: string) => {
    if (!memberName.trim()) {
      setError("Namn krävs.");
      return;
    }

    try {
      await updateFamilyMember(memberId, memberName.trim());
      await loadMembers();
      setEditingId(null);
      setMemberName("");
      setError(null);
    } catch {
      setError("Kunde inte uppdatera familjemedlem.");
    }
  };

  const handleDelete = async (memberId: string) => {
    if (!confirm("Är du säker på att du vill ta bort denna familjemedlem?")) {
      return;
    }

    try {
      await deleteFamilyMember(memberId);
      await loadMembers();
    } catch {
      setError("Kunde inte ta bort familjemedlem.");
    }
  };

  const handleGenerateInvite = async (memberId: string) => {
    try {
      const token = await generateInviteToken(memberId);
      setInviteToken(token);
      setInviteMemberId(memberId);
    } catch {
      setError("Kunde inte generera inbjudan.");
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
      await updateFamilyMemberPassword(memberId, newPassword);
      await loadMembers();
      setPasswordEditingId(null);
      setNewPassword("");
      setNewPasswordConfirm("");
      setShowNewPassword(false);
      setShowNewPasswordConfirm(false);
      setError(null);
    } catch (e) {
      setError("Kunde inte uppdatera lösenord.");
      console.error("Password update error:", e);
    }
  };

  const handleUpdateEmail = async (memberId: string) => {
    // Basic email validation
    if (newEmail && newEmail.trim() && (!newEmail.includes("@") || !newEmail.includes("."))) {
      setError("Ogiltig e-postadress.");
      return;
    }

    try {
      await updateFamilyMemberEmail(memberId, newEmail.trim() || "");
      await loadMembers();
      setEmailEditingId(null);
      setNewEmail("");
      setError(null);
    } catch (e) {
      setError("Kunde inte uppdatera e-postadress.");
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
        <section className="card">
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
                ) : (
                  <>
                    <div className="family-member-content">
                      <div>
                        <h4>{member.name}</h4>
                        <span className={`role-badge role-badge-${member.role.toLowerCase()}`}>
                          {isAdmin ? "Huvudanvändare" : member.role === "PARENT" ? "Förälder" : member.role === "ASSISTANT" ? "Äldre barn" : "Barn"}
                        </span>
                      </div>
                      {member.email && (
                        <p className="device-token-info" style={{ fontSize: "0.85rem", color: "#666" }}>
                          📧 {member.email}
                        </p>
                      )}
                      {member.deviceToken && (
                        <p className="device-token-info">Enhet kopplad</p>
                      )}
                    </div>
                    <div className="family-member-actions">
                      <button
                        type="button"
                        className="button-secondary"
                        onClick={() => {
                          setEditingId(member.id);
                          setMemberName(member.name);
                          setShowCreateForm(false);
                        }}
                      >
                        Redigera
                      </button>
                      {!isAdmin && member.role === "CHILD" && (
                        <button
                          type="button"
                          className="button-secondary"
                          onClick={() => void handleGenerateInvite(member.id)}
                        >
                          Visa QR-kod
                        </button>
                      )}
                      {!isAdmin && (member.role === "PARENT" || member.role === "ASSISTANT") && (
                        <>
                          <button
                            type="button"
                            className="button-secondary"
                            onClick={() => void handleGenerateInvite(member.id)}
                          >
                            {member.role === "ASSISTANT" ? "Visa QR-kod" : "Bjud in"}
                          </button>
                          <button
                            type="button"
                            className="button-secondary"
                            onClick={() => {
                              setEmailEditingId(member.id);
                              setEditingId(null);
                              setPasswordEditingId(null);
                              setShowCreateForm(false);
                              setNewEmail(member.email || "");
                            }}
                          >
                            {member.email ? "Ändra e-post" : "Lägg till e-post"}
                          </button>
                          <button
                            type="button"
                            className="button-secondary"
                            onClick={() => {
                              setPasswordEditingId(member.id);
                              setEditingId(null);
                              setEmailEditingId(null);
                              setShowCreateForm(false);
                              setNewPassword("");
                              setNewPasswordConfirm("");
                            }}
                          >
                            Ändra lösenord
                          </button>
                        </>
                      )}
                      {member.role === "PARENT" && isAdmin && (
                        <button
                          type="button"
                          className="button-secondary"
                          onClick={() => {
                            setPasswordEditingId(member.id);
                            setEditingId(null);
                            setShowCreateForm(false);
                            setNewPassword("");
                            setNewPasswordConfirm("");
                          }}
                        >
                          Ändra lösenord
                        </button>
                      )}
                      {!isAdmin && (
                        <button
                          type="button"
                          className="button-danger"
                          onClick={() => void handleDelete(member.id)}
                        >
                          Ta bort
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


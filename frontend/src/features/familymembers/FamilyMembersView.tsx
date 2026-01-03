import { useEffect, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import {
  fetchAllFamilyMembers,
  createFamilyMember,
  updateFamilyMember,
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
            {members.find(m => m.id === inviteMemberId)?.role === "PARENT"
              ? "Låt föräldern skanna denna QR-kod för att koppla sin enhet:"
              : "Låt barnet skanna denna QR-kod för att koppla sin enhet:"}
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
                ) : (
                  <>
                    <div className="family-member-content">
                      <div>
                        <h4>{member.name}</h4>
                        <span className={`role-badge role-badge-${member.role.toLowerCase()}`}>
                          {isAdmin ? "Huvudanvändare" : member.role === "PARENT" ? "Förälder" : "Barn"}
                        </span>
                      </div>
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
                      {!isAdmin && member.role === "PARENT" && (
                        <button
                          type="button"
                          className="button-secondary"
                          onClick={() => void handleGenerateInvite(member.id)}
                        >
                          Bjud in
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


import { useEffect, useState } from "react";
import {
  fetchAllDailyTasks,
  createDailyTask,
  updateDailyTask,
  deleteDailyTask,
  DailyTaskResponse,
  DayOfWeek
} from "../../shared/api/dailyTasks";
import { fetchAllFamilyMembers, FamilyMemberResponse } from "../../shared/api/familyMembers";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "dailytasks" | "dailytasksadmin";

type DailyTasksAdminViewProps = {
  onNavigate?: (view: ViewKey) => void;
};

const DAYS_OF_WEEK: { value: DayOfWeek; label: string }[] = [
  { value: "MONDAY", label: "Måndag" },
  { value: "TUESDAY", label: "Tisdag" },
  { value: "WEDNESDAY", label: "Onsdag" },
  { value: "THURSDAY", label: "Torsdag" },
  { value: "FRIDAY", label: "Fredag" },
  { value: "SATURDAY", label: "Lördag" },
  { value: "SUNDAY", label: "Söndag" }
];

export function DailyTasksAdminView({ onNavigate }: DailyTasksAdminViewProps) {
  const [tasks, setTasks] = useState<DailyTaskResponse[]>([]);
  const [members, setMembers] = useState<FamilyMemberResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [formName, setFormName] = useState("");
  const [formDescription, setFormDescription] = useState("");
  const [formDays, setFormDays] = useState<Set<DayOfWeek>>(new Set());
  const [formMemberIds, setFormMemberIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const [tasksData, membersData] = await Promise.all([
          fetchAllDailyTasks(),
          fetchAllFamilyMembers()
        ]);
        setTasks(tasksData);
        setMembers(membersData);
      } catch (e) {
        setError("Kunde inte hämta data.");
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, []);

  const handleCreate = async () => {
    if (!formName.trim() || formDays.size === 0) {
      setError("Namn och minst en veckodag krävs.");
      return;
    }

    try {
      const created = await createDailyTask(
        formName.trim(),
        formDescription.trim() || null,
        Array.from(formDays),
        formMemberIds.size > 0 ? Array.from(formMemberIds) : undefined
      );
      setTasks((prev) => [...prev, created].sort((a, b) => a.position - b.position));
      setFormName("");
      setFormDescription("");
      setFormDays(new Set());
      setFormMemberIds(new Set());
      setShowCreateForm(false);
      setError(null);
    } catch {
      setError("Kunde inte skapa syssla.");
    }
  };

  const handleUpdate = async (task: DailyTaskResponse) => {
    if (!formName.trim() || formDays.size === 0) {
      setError("Namn och minst en veckodag krävs.");
      return;
    }

    try {
      const updated = await updateDailyTask(
        task.id,
        formName.trim(),
        formDescription.trim() || null,
        Array.from(formDays),
        formMemberIds.size > 0 ? Array.from(formMemberIds) : undefined
      );
      setTasks((prev) =>
        prev.map((t) => (t.id === updated.id ? updated : t)).sort((a, b) => a.position - b.position)
      );
      setEditingId(null);
      setFormName("");
      setFormDescription("");
      setFormDays(new Set());
      setFormMemberIds(new Set());
      setError(null);
    } catch {
      setError("Kunde inte uppdatera syssla.");
    }
  };

  const handleDelete = async (taskId: string) => {
    if (!confirm("Är du säker på att du vill ta bort denna syssla?")) {
      return;
    }

    try {
      await deleteDailyTask(taskId);
      setTasks((prev) => prev.filter((t) => t.id !== taskId));
    } catch {
      setError("Kunde inte ta bort syssla.");
    }
  };

  const startEdit = (task: DailyTaskResponse) => {
    setEditingId(task.id);
    setFormName(task.name);
    setFormDescription(task.description || "");
    setFormDays(new Set(task.daysOfWeek));
    setFormMemberIds(new Set(task.memberIds || []));
    setShowCreateForm(false);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setShowCreateForm(false);
    setFormName("");
    setFormDescription("");
    setFormDays(new Set());
    setFormMemberIds(new Set());
  };

  const toggleMember = (memberId: string) => {
    setFormMemberIds((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(memberId)) {
        newSet.delete(memberId);
      } else {
        newSet.add(memberId);
      }
      return newSet;
    });
  };

  const selectAllMembers = () => {
    setFormMemberIds(new Set(members.map((m) => m.id)));
  };

  const clearAllMembers = () => {
    setFormMemberIds(new Set());
  };

  const toggleDay = (day: DayOfWeek) => {
    setFormDays((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(day)) {
        newSet.delete(day);
      } else {
        newSet.add(day);
      }
      return newSet;
    });
  };

  const selectAllDays = () => {
    setFormDays(new Set(DAYS_OF_WEEK.map((d) => d.value)));
  };

  const clearAllDays = () => {
    setFormDays(new Set());
  };

  return (
    <div className="daily-tasks-admin-view">
      <div className="daily-tasks-admin-header">
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: "12px", flex: 1 }}>
            {onNavigate && (
              <button
                type="button"
                className="back-button"
                onClick={() => onNavigate("dailytasks")}
                aria-label="Tillbaka"
              >
                ←
              </button>
            )}
            <h2 className="view-title" style={{ margin: 0, flex: 1 }}>Hantera dagliga sysslor</h2>
          </div>
        </div>
        <button
          type="button"
          className="todo-action-button"
          onClick={() => {
            cancelEdit();
            setShowCreateForm(true);
          }}
        >
          + Ny syssla
        </button>
      </div>

      {error && <p className="error-text">{error}</p>}

      {showCreateForm && (
        <section className="card">
          <h3>Skapa ny syssla</h3>
          <div className="daily-task-form">
            <input
              type="text"
              placeholder="Namn, t.ex. Städa ditt rum"
              value={formName}
              onChange={(e) => setFormName(e.target.value)}
              className="daily-task-form-input"
            />
            <input
              type="text"
              placeholder="Beskrivning (valfritt)"
              value={formDescription}
              onChange={(e) => setFormDescription(e.target.value)}
              className="daily-task-form-input"
            />
            <div className="daily-task-days">
              <div className="daily-task-days-header">
                <span>Vilka dagar?</span>
                <div className="daily-task-days-actions">
                  <button type="button" onClick={selectAllDays} className="daily-task-day-action">
                    Alla
                  </button>
                  <button type="button" onClick={clearAllDays} className="daily-task-day-action">
                    Ingen
                  </button>
                </div>
              </div>
              <div className="daily-task-days-grid">
                {DAYS_OF_WEEK.map((day) => (
                  <label key={day.value} className="daily-task-day-checkbox">
                    <input
                      type="checkbox"
                      checked={formDays.has(day.value)}
                      onChange={() => toggleDay(day.value)}
                    />
                    <span>{day.label}</span>
                  </label>
                ))}
              </div>
                    </div>
            <div className="daily-task-members">
              <div className="daily-task-days-header">
                <span>Vilka barn? (tomt = alla)</span>
                {members.length > 0 && (
                  <div className="daily-task-days-actions">
                    <button type="button" onClick={selectAllMembers} className="daily-task-day-action">
                      Alla
                    </button>
                    <button type="button" onClick={clearAllMembers} className="daily-task-day-action">
                      Ingen
                    </button>
                  </div>
                )}
              </div>
              {members.length === 0 ? (
                <p className="placeholder-text">Skapa familjemedlemmar först för att tilldela sysslor.</p>
              ) : (
                <div className="daily-task-members-list">
                  {members.map((member) => (
                    <label key={member.id} className="daily-task-day-checkbox">
                      <input
                        type="checkbox"
                        checked={formMemberIds.has(member.id)}
                        onChange={() => toggleMember(member.id)}
                      />
                      <span>{member.name}</span>
                    </label>
                  ))}
                </div>
              )}
            </div>
            <div className="daily-task-form-actions">
              <button type="button" onClick={handleCreate} className="todo-action-button">
                Skapa
              </button>
              <button type="button" onClick={cancelEdit} className="todo-action-button">
                Avbryt
              </button>
            </div>
          </div>
        </section>
      )}

      <section className="card">
        {loading && <p>Laddar...</p>}
        {!loading && tasks.length === 0 && (
          <p className="placeholder-text">Inga sysslor skapade än. Skapa din första syssla ovan!</p>
        )}

        {!loading && tasks.length > 0 && (
          <ul className="daily-tasks-admin-list">
            {tasks.map((task) => (
              <li key={task.id} className="daily-task-admin-item">
                {editingId === task.id ? (
                  <div className="daily-task-form">
                    <input
                      type="text"
                      value={formName}
                      onChange={(e) => setFormName(e.target.value)}
                      className="daily-task-form-input"
                    />
                    <input
                      type="text"
                      placeholder="Beskrivning (valfritt)"
                      value={formDescription}
                      onChange={(e) => setFormDescription(e.target.value)}
                      className="daily-task-form-input"
                    />
                    <div className="daily-task-days">
                      <div className="daily-task-days-header">
                        <span>Vilka dagar?</span>
                        <div className="daily-task-days-actions">
                          <button type="button" onClick={selectAllDays} className="daily-task-day-action">
                            Alla
                          </button>
                          <button type="button" onClick={clearAllDays} className="daily-task-day-action">
                            Ingen
                          </button>
                        </div>
                      </div>
                      <div className="daily-task-days-grid">
                        {DAYS_OF_WEEK.map((day) => (
                          <label key={day.value} className="daily-task-day-checkbox">
                            <input
                              type="checkbox"
                              checked={formDays.has(day.value)}
                              onChange={() => toggleDay(day.value)}
                            />
                            <span>{day.label}</span>
                          </label>
                        ))}
                      </div>
                    </div>
                    <div className="daily-task-members">
                      <div className="daily-task-days-header">
                        <span>Vilka barn? (tomt = alla)</span>
                        {members.length > 0 && (
                          <div className="daily-task-days-actions">
                            <button type="button" onClick={selectAllMembers} className="daily-task-day-action">
                              Alla
                            </button>
                            <button type="button" onClick={clearAllMembers} className="daily-task-day-action">
                              Ingen
                            </button>
                          </div>
                        )}
                      </div>
                      {members.length === 0 ? (
                        <p className="placeholder-text">Skapa familjemedlemmar först för att tilldela sysslor.</p>
                      ) : (
                        <div className="daily-task-members-list">
                          {members.map((member) => (
                            <label key={member.id} className="daily-task-day-checkbox">
                              <input
                                type="checkbox"
                                checked={formMemberIds.has(member.id)}
                                onChange={() => toggleMember(member.id)}
                              />
                              <span>{member.name}</span>
                            </label>
                          ))}
                        </div>
                      )}
                    </div>
                    <div className="daily-task-form-actions">
                      <button
                        type="button"
                        onClick={() => handleUpdate(task)}
                        className="todo-action-button"
                      >
                        Spara
                      </button>
                      <button
                        type="button"
                        onClick={cancelEdit}
                        className="todo-action-button"
                      >
                        Avbryt
                      </button>
                    </div>
                  </div>
                ) : (
                  <>
                    <div className="daily-task-admin-content">
                      <div>
                        <h4>{task.name}</h4>
                        {task.description && <p className="daily-task-description">{task.description}</p>}
                        <div className="daily-task-days-display">
                          {task.daysOfWeek.length === 7 ? (
                            <span className="daily-task-day-badge">Alla dagar</span>
                          ) : (
                            task.daysOfWeek.map((day) => {
                              const dayLabel = DAYS_OF_WEEK.find((d) => d.value === day)?.label || day;
                              return (
                                <span key={day} className="daily-task-day-badge">
                                  {dayLabel}
                                </span>
                              );
                            })
                          )}
                        </div>
                        <div className="daily-task-members-display">
                          {task.memberIds && task.memberIds.length > 0 ? (
                            task.memberIds.map((memberId) => {
                              const member = members.find((m) => m.id === memberId);
                              return member ? (
                                <span key={memberId} className="daily-task-day-badge">
                                  {member.name}
                                </span>
                              ) : null;
                            })
                          ) : (
                            <span className="daily-task-day-badge">Alla barn</span>
                          )}
                        </div>
                      </div>
                      <div className="daily-task-admin-actions">
                        <button
                          type="button"
                          className="todo-action-button"
                          onClick={() => startEdit(task)}
                        >
                          Redigera
                        </button>
                        <button
                          type="button"
                          className="todo-action-button todo-action-button-danger"
                          onClick={() => handleDelete(task.id)}
                        >
                          Ta bort
                        </button>
                      </div>
                    </div>
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}


import { useState } from "react";
import {
  CalendarEventCategoryResponse,
  createCalendarCategory,
  updateCalendarCategory,
  deleteCalendarCategory,
} from "../../../shared/api/calendar";
import { CATEGORY_COLORS } from "../constants";

export type CategoryManagerProps = {
  categories: CalendarEventCategoryResponse[];
  onClose: () => void;
  onUpdate: () => Promise<void>;
};

export function CategoryManager({ categories, onClose, onUpdate }: CategoryManagerProps) {
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingCategory, setEditingCategory] = useState<CalendarEventCategoryResponse | null>(null);
  const [categoryName, setCategoryName] = useState("");
  const [categoryColor, setCategoryColor] = useState(CATEGORY_COLORS[0].value);
  const [error, setError] = useState<string | null>(null);

  const handleCreateCategory = async () => {
    if (!categoryName.trim()) {
      setError("Kategorinamn krävs.");
      return;
    }

    try {
      await createCalendarCategory(categoryName.trim(), categoryColor);
      await onUpdate();
      setCategoryName("");
      setCategoryColor(CATEGORY_COLORS[0].value);
      setShowCreateForm(false);
      setError(null);
    } catch (e) {
      setError("Kunde inte skapa kategori.");
      console.error("Error creating category:", e);
    }
  };

  const handleUpdateCategory = async () => {
    if (!editingCategory || !categoryName.trim()) {
      setError("Kategorinamn krävs.");
      return;
    }

    try {
      await updateCalendarCategory(editingCategory.id, categoryName.trim(), categoryColor);
      await onUpdate();
      setEditingCategory(null);
      setCategoryName("");
      setCategoryColor(CATEGORY_COLORS[0].value);
      setError(null);
    } catch (e) {
      setError("Kunde inte uppdatera kategori.");
      console.error("Error updating category:", e);
    }
  };

  const handleDeleteCategory = async (categoryId: string) => {
    if (!confirm("Är du säker på att du vill ta bort denna kategori? Events som använder denna kategori kommer inte längre ha en kategori.")) {
      return;
    }

    try {
      await deleteCalendarCategory(categoryId);
      await onUpdate();
    } catch (e) {
      setError("Kunde inte ta bort kategori.");
      console.error("Error deleting category:", e);
    }
  };

  const startEdit = (category: CalendarEventCategoryResponse) => {
    setEditingCategory(category);
    setCategoryName(category.name);
    setCategoryColor(category.color);
    setShowCreateForm(false);
    setError(null);
  };

  const cancelEdit = () => {
    setEditingCategory(null);
    setShowCreateForm(false);
    setCategoryName("");
    setCategoryColor(CATEGORY_COLORS[0].value);
    setError(null);
  };

  return (
    <section className="card">
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "16px" }}>
        <h3 style={{ margin: 0 }}>Hantera kategorier</h3>
        <button
          type="button"
          className="todo-action-button"
          onClick={onClose}
        >
          Stäng
        </button>
      </div>

      {error && <p className="error-text">{error}</p>}

      {!showCreateForm && !editingCategory && (
        <>
          <div style={{ display: "flex", flexDirection: "column", gap: "8px", marginBottom: "16px" }}>
            {categories.map((category) => (
              <div
                key={category.id}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "12px",
                  padding: "12px",
                  background: "rgba(240, 240, 240, 0.5)",
                  borderRadius: "8px",
                  border: `2px solid ${category.color}`
                }}
              >
                <div
                  style={{
                    width: "24px",
                    height: "24px",
                    borderRadius: "6px",
                    background: category.color,
                    flexShrink: 0
                  }}
                />
                <div style={{ flex: 1, fontWeight: 500 }}>{category.name}</div>
                <button
                  type="button"
                  className="todo-action-button"
                  onClick={() => startEdit(category)}
                  style={{ fontSize: "0.8rem", padding: "4px 8px" }}
                >
                  Redigera
                </button>
                <button
                  type="button"
                  className="delete-button"
                  onClick={() => void handleDeleteCategory(category.id)}
                  style={{ fontSize: "0.8rem", padding: "4px 8px" }}
                >
                  Ta bort
                </button>
              </div>
            ))}
            {categories.length === 0 && (
              <p className="placeholder-text" style={{ textAlign: "center", padding: "16px" }}>
                Inga kategorier än. Skapa din första kategori!
              </p>
            )}
          </div>

          <button
            type="button"
            className="button-primary"
            onClick={() => {
              setShowCreateForm(true);
              setError(null);
            }}
          >
            + Ny kategori
          </button>
        </>
      )}

      {(showCreateForm || editingCategory) && (
        <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
          <h4 style={{ margin: 0 }}>{editingCategory ? "Redigera kategori" : "Ny kategori"}</h4>

          <div>
            <label htmlFor="categoryName" style={{ display: "block", marginBottom: "4px", fontWeight: 500 }}>
              Namn *
            </label>
            <input
              id="categoryName"
              type="text"
              value={categoryName}
              onChange={(e) => setCategoryName(e.target.value)}
              placeholder="T.ex. Skola, Idrott, Familj"
              required
              style={{ width: "100%", padding: "8px", borderRadius: "6px", border: "1px solid #ddd" }}
            />
          </div>

          <div>
            <label style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>
              Färg
            </label>
            <div style={{ display: "flex", flexWrap: "wrap", gap: "8px" }}>
              {CATEGORY_COLORS.map((color) => (
                <button
                  key={color.value}
                  type="button"
                  onClick={() => setCategoryColor(color.value)}
                  style={{
                    width: "44px",
                    height: "44px",
                    borderRadius: "8px",
                    border: categoryColor === color.value ? "3px solid #2d5a2d" : "2px solid #ddd",
                    background: color.value,
                    cursor: "pointer",
                    boxShadow: categoryColor === color.value ? "0 2px 8px rgba(0, 0, 0, 0.2)" : "0 1px 3px rgba(0, 0, 0, 0.1)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontSize: "1.2rem"
                  }}
                  title={color.label}
                >
                  {categoryColor === color.value && "✓"}
                </button>
              ))}
            </div>
          </div>

          <div style={{ display: "flex", gap: "8px", marginTop: "8px" }}>
            <button
              type="button"
              className="button-primary"
              onClick={editingCategory ? handleUpdateCategory : handleCreateCategory}
            >
              {editingCategory ? "Spara ändringar" : "Skapa kategori"}
            </button>
            <button
              type="button"
              className="todo-action-button"
              onClick={cancelEdit}
            >
              Avbryt
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

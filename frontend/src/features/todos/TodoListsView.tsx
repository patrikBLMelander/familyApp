import { useEffect, useState } from "react";
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragEndEvent,
  DragStartEvent,
} from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
  horizontalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import {
  fetchTodoLists,
  createTodoList,
  addTodoItem,
  toggleTodoItem,
  clearDoneItems,
  reorderTodoItems,
  reorderTodoLists,
  deleteTodoItem,
  deleteTodoList,
  updateTodoListName,
  updateTodoListColor,
  updateTodoListPrivacy,
  TODO_COLORS
} from "../../shared/api/todos";

type TodoItem = {
  id: string;
  description: string;
  done: boolean;
  position: number;
};

type TodoList = {
  id: string;
  name: string;
  position: number;
  color: string;
  isPrivate: boolean;
  items: TodoItem[];
};

type TodoListsViewProps = {
  onNavigate?: (view: "dashboard") => void;
};

export function TodoListsView({ onNavigate }: TodoListsViewProps) {
  const [lists, setLists] = useState<TodoList[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [newListName, setNewListName] = useState("");
  const [newItemDescription, setNewItemDescription] = useState("");
  const [activeListId, setActiveListId] = useState<string | null>(null);
  const [swipeStartX, setSwipeStartX] = useState<number | null>(null);
  const [swipeOffset, setSwipeOffset] = useState<number>(0);
  const [swipedItemId, setSwipedItemId] = useState<string | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [editingName, setEditingName] = useState(false);
  const [editNameValue, setEditNameValue] = useState("");
  const [showColorPicker, setShowColorPicker] = useState(false);
  const [activeDragId, setActiveDragId] = useState<string | null>(null);

  // Configure sensors for @dnd-kit
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 8, // Require 8px movement before drag starts
      },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const data = await fetchTodoLists();
        setLists(
          data.slice().sort((a, b) => a.position - b.position)
        );
        if (data.length > 0 && !activeListId) {
          setActiveListId(data[0].id);
        }
      } catch (e) {
        setError("Kunde inte hämta to do-listor just nu.");
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [activeListId]);

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setMenuOpen(false);
        setEditingName(false);
      }
    };
    window.addEventListener("keydown", handleEscape);
    return () => window.removeEventListener("keydown", handleEscape);
  }, []);

  const handleCreateList = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!newListName.trim()) return;

    try {
      const created = await createTodoList(newListName.trim(), false);
      setLists((prev) =>
        [...prev, created].slice().sort((a, b) => a.position - b.position)
      );
      setNewListName("");
      setActiveListId(created.id);
    } catch {
      setError("Kunde inte skapa lista.");
    }
  };

  const handleAddItem = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!activeListId || !newItemDescription.trim()) return;

    try {
      const updatedList = await addTodoItem(activeListId, newItemDescription.trim());
      setLists((prev) => prev.map((l) => (l.id === updatedList.id ? updatedList : l)));
      setNewItemDescription("");
    } catch {
      setError("Kunde inte lägga till uppgift.");
    }
  };

  const handleToggleItem = async (listId: string, itemId: string) => {
    try {
      const updatedList = await toggleTodoItem(listId, itemId);
      setLists((prev) => prev.map((l) => (l.id === updatedList.id ? updatedList : l)));
    } catch {
      setError("Kunde inte uppdatera uppgift.");
    }
  };


  const handleDeleteItem = async (itemId: string) => {
    if (!activeListId) return;
    // Close swipe immediately for better UX
    setSwipedItemId(null);
    setSwipeOffset(0);
    
    // Optimistic update - remove item immediately
    setLists((prev) => prev.map((list) => {
      if (list.id !== activeListId) return list;
      return {
        ...list,
        items: list.items.filter((item) => item.id !== itemId)
      };
    }));
    
    try {
      const updatedList = await deleteTodoItem(activeListId, itemId);
      // Update with server response to ensure consistency
      setLists((prev) => prev.map((l) => (l.id === updatedList.id ? updatedList : l)));
    } catch {
      setError("Kunde inte ta bort uppgift.");
      // Reload lists on error to restore state
      const data = await fetchTodoLists();
      setLists(data.slice().sort((a, b) => a.position - b.position));
    }
  };

  const handleDeleteList = async (listId: string, event?: React.MouseEvent | MouseEvent) => {
    if (event) {
      event.stopPropagation();
    }
    setMenuOpen(false);
    if (!confirm("Är du säker på att du vill ta bort denna lista?")) {
      return;
    }
    
    try {
      await deleteTodoList(listId);
      setLists((prev) => prev.filter((l) => l.id !== listId));
      // If deleted list was active, switch to first available list
      if (activeListId === listId) {
        const remaining = lists.filter((l) => l.id !== listId);
        setActiveListId(remaining.length > 0 ? remaining[0].id : null);
      }
    } catch {
      setError("Kunde inte ta bort lista.");
    }
  };

  const handleClearDone = async () => {
    if (!activeListId) return;
    setMenuOpen(false);
    try {
      const updatedList = await clearDoneItems(activeListId);
      setLists((prev) => prev.map((l) => (l.id === updatedList.id ? updatedList : l)));
    } catch {
      setError("Kunde inte ta bort klara uppgifter.");
    }
  };

  const handleCopyList = async () => {
    if (!activeListId || !activeList) return;
    setMenuOpen(false);
    
    try {
      const copied = await createTodoList(`${activeList.name} (kopia)`);
      // Copy color
      await updateTodoListColor(copied.id, activeList.color);
      // Copy all items in order
      for (const item of activeList.items.sort((a, b) => a.position - b.position)) {
        await addTodoItem(copied.id, item.description);
      }
      // Reload lists to get the new one with items
      const data = await fetchTodoLists();
      const sorted = data.slice().sort((a, b) => a.position - b.position);
      setLists(sorted);
      // Find the copied list (should be the last one or find by name)
      const copiedList = sorted.find(l => l.id === copied.id);
      if (copiedList) {
        setActiveListId(copiedList.id);
      }
    } catch {
      setError("Kunde inte kopiera lista.");
    }
  };

  const handleRenameList = async () => {
    if (!activeListId || !editNameValue.trim()) {
      setEditingName(false);
      setEditNameValue("");
      return;
    }
    
    try {
      const updated = await updateTodoListName(activeListId, editNameValue.trim());
      setLists((prev) => prev.map((l) => (l.id === updated.id ? updated : l)));
      setEditingName(false);
      setEditNameValue("");
      setMenuOpen(false);
    } catch {
      setError("Kunde inte byta namn på lista.");
    }
  };

  const startEditingName = () => {
    if (activeList) {
      setEditNameValue(activeList.name);
      setEditingName(true);
      setMenuOpen(false);
    }
  };

  const handleChangeColor = async (color: string) => {
    if (!activeListId) return;
    
    try {
      const updated = await updateTodoListColor(activeListId, color);
      setLists((prev) => prev.map((l) => (l.id === updated.id ? updated : l)));
      setShowColorPicker(false);
      setMenuOpen(false);
    } catch {
      setError("Kunde inte ändra färg på lista.");
    }
  };

  const handleTogglePrivacy = async () => {
    if (!activeListId || !activeList) return;
    
    try {
      const updated = await updateTodoListPrivacy(activeListId, !activeList.isPrivate);
      setLists((prev) => prev.map((l) => (l.id === updated.id ? updated : l)));
      setMenuOpen(false);
    } catch {
      setError("Kunde inte ändra privat-status på lista.");
    }
  };

  const activeList = lists.find((l) => l.id === activeListId) ?? lists[0];

  // Handle drag end for todo items
  const handleItemsDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!activeListId || !over || active.id === over.id) {
      setActiveDragId(null);
      return;
    }

    setLists((prev) => {
      return prev.map((list) => {
        if (list.id !== activeListId) return list;

        const activeItems = list.items.filter((item) => !item.done);
        const doneItems = list.items.filter((item) => item.done);

        const oldIndex = activeItems.findIndex((item) => item.id === active.id);
        const newIndex = activeItems.findIndex((item) => item.id === over.id);
        
        if (oldIndex === -1 || newIndex === -1) {
          return list;
        }

        const reordered = arrayMove(activeItems, oldIndex, newIndex);
        const newItemsWithPos = [...reordered, ...doneItems].map((item, index) => ({
          ...item,
          position: index
        }));

        // Fire-and-forget till backend
        void reorderTodoItems(list.id, newItemsWithPos.map((item) => item.id));

        return { ...list, items: newItemsWithPos };
      });
    });

    setActiveDragId(null);
  };

  // Handle drag end for todo lists
  const handleListsDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) {
      setActiveDragId(null);
      return;
    }

    setLists((prev) => {
      const oldIndex = prev.findIndex((l) => l.id === active.id);
      const newIndex = prev.findIndex((l) => l.id === over.id);
      
      if (oldIndex === -1 || newIndex === -1) {
        return prev;
      }

      const reordered = arrayMove(prev, oldIndex, newIndex);
      const reorderedWithPos = reordered.map((list, index) => ({
        ...list,
        position: index
      }));

      // Fire-and-forget till backend
      void reorderTodoLists(reorderedWithPos.map((l) => l.id));

      return reorderedWithPos;
    });

    setActiveDragId(null);
  };

  const handleDragStart = (event: DragStartEvent) => {
    setActiveDragId(event.active.id as string);
  };

  return (
    <div className="todos-view">
      <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
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
        <h2 className="view-title" style={{ margin: 0, flex: 1 }}>To do-listor</h2>
      </div>

      {error && <p className="error-text">{error}</p>}

      <section className="list-selector">
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragStart={handleDragStart}
          onDragEnd={handleListsDragEnd}
        >
          <div className="list-selector-scroll">
            <SortableContext
              items={lists.map((l) => l.id)}
              strategy={horizontalListSortingStrategy}
            >
              {lists.map((list) => (
                <SortableTodoListChip
                  key={list.id}
                  list={list}
                  isActive={activeList?.id === list.id}
                  onClick={() => {
                    if (activeDragId !== list.id) {
                      setActiveListId(list.id);
                    }
                  }}
                />
              ))}
            </SortableContext>
          </div>
        </DndContext>
        <form onSubmit={handleCreateList} className="inline-form">
          <input
            type="text"
            placeholder="Ny lista, t.ex. Inköp"
            value={newListName}
            onChange={(event) => setNewListName(event.target.value)}
          />
          <button type="submit">Skapa</button>
        </form>
      </section>

      <section 
        className="card todo-card"
        style={activeList ? {
          border: `2px solid ${TODO_COLORS.find(c => c.value === activeList.color)?.border || "#a8d8a8"}`
        } : {}}
      >
        {loading && <p>Laddar...</p>}
        {!loading && !activeList && <p>Skapa din första lista för att komma igång.</p>}

        {activeList && (
          <>
            <div className="todo-card-header">
              {editingName ? (
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    void handleRenameList();
                  }}
                  className="todo-rename-form"
                >
                  <input
                    type="text"
                    value={editNameValue}
                    onChange={(e) => setEditNameValue(e.target.value)}
                    onBlur={() => {
                      void handleRenameList();
                    }}
                    autoFocus
                    className="todo-rename-input"
                  />
                </form>
              ) : (
                <h3>
                  {activeList.name}
                  {activeList.isPrivate && (
                    <span className="private-indicator-header" title="Privat lista">🔒</span>
                  )}
                </h3>
              )}
              <div className="todo-card-menu">
                <button
                  type="button"
                  className="todo-menu-button"
                  onClick={() => setMenuOpen(!menuOpen)}
                  title="Meny"
                >
                  ⋮
                </button>
                {menuOpen && (
                  <>
                    <div className="todo-menu-backdrop" onClick={() => setMenuOpen(false)} />
                    <div className="todo-menu-dropdown">
                      <button
                        type="button"
                        className="todo-menu-item"
                        onClick={startEditingName}
                      >
                        Byt namn
                      </button>
                      <button
                        type="button"
                        className="todo-menu-item"
                        onClick={handleClearDone}
                      >
                        Rensa
                      </button>
                      <button
                        type="button"
                        className="todo-menu-item"
                        onClick={handleCopyList}
                      >
                        Kopiera
                      </button>
                      <button
                        type="button"
                        className="todo-menu-item"
                        onClick={() => {
                          setShowColorPicker(true);
                          setMenuOpen(false);
                        }}
                      >
                        Ändra färg
                      </button>
                      <button
                        type="button"
                        className="todo-menu-item"
                        onClick={handleTogglePrivacy}
                      >
                        {activeList?.isPrivate ? "Gör publik" : "Gör privat"}
                      </button>
                      <button
                        type="button"
                        className="todo-menu-item todo-menu-item-danger"
                        onClick={() => handleDeleteList(activeList.id, new MouseEvent('click'))}
                      >
                        Ta bort
                      </button>
                    </div>
                  </>
                )}
                {showColorPicker && (
                  <>
                    <div className="todo-menu-backdrop" onClick={() => setShowColorPicker(false)} />
                    <div className="todo-menu-dropdown todo-color-picker">
                      <div style={{ padding: "8px 12px", fontSize: "0.85rem", fontWeight: "600", borderBottom: "1px solid rgba(220, 210, 200, 0.3)" }}>
                        Välj färg
                      </div>
                      <div className="todo-color-options">
                        {TODO_COLORS.map((color) => (
                          <button
                            key={color.value}
                            type="button"
                            className="todo-color-option"
                            onClick={() => handleChangeColor(color.value)}
                            style={{
                              background: color.gradient,
                              border: activeList?.color === color.value ? `2px solid ${color.border}` : "1px solid rgba(220, 210, 200, 0.5)"
                            }}
                            title={color.label}
                          >
                            {activeList?.color === color.value && "✓"}
                          </button>
                        ))}
                      </div>
                    </div>
                  </>
                )}
              </div>
            </div>

            {(() => {
              const activeItems = activeList.items.filter((item) => !item.done);
              const doneItems = activeList.items.filter((item) => item.done);

              return (
                <>
                  <DndContext
                    sensors={sensors}
                    collisionDetection={closestCenter}
                    onDragStart={handleDragStart}
                    onDragEnd={handleItemsDragEnd}
                  >
                    <SortableContext
                      items={activeItems.map((item) => item.id)}
                      strategy={verticalListSortingStrategy}
                    >
                      <ul className="todo-items">
                        {activeItems.map((item) => (
                          <SortableTodoItem
                            key={item.id}
                            item={item}
                            onToggle={() => handleToggleItem(activeList.id, item.id)}
                            onDelete={() => handleDeleteItem(item.id)}
                            swipedItemId={swipedItemId}
                            swipeOffset={swipeOffset}
                            swipeStartX={swipeStartX}
                            setSwipeStartX={setSwipeStartX}
                            setSwipedItemId={setSwipedItemId}
                            setSwipeOffset={setSwipeOffset}
                          />
                        ))}
                      </ul>
                    </SortableContext>
                  </DndContext>

                  {doneItems.length > 0 && (
                    <>
                      <div className="todo-separator">
                        <span>Klart</span>
                      </div>
                      <ul className="todo-items todo-items-done">
                        {doneItems.map((item) => (
                          <li
                            key={item.id}
                            style={{
                              transform: swipedItemId === item.id ? `translateX(${swipeOffset}px)` : 'translateX(0)',
                              transition: swipedItemId === item.id && swipeOffset === -80 ? 'transform 0.2s ease' : 'none'
                            }}
                            onTouchStart={(event) => {
                              setSwipeStartX(event.touches[0].clientX);
                              if (swipedItemId !== item.id) {
                                setSwipedItemId(null);
                                setSwipeOffset(0);
                              }
                            }}
                            onTouchMove={(event) => {
                              if (swipeStartX === null) return;
                              const deltaX = event.touches[0].clientX - swipeStartX;
                              if (Math.abs(deltaX) > 10) {
                                event.preventDefault();
                                if (deltaX < 0) {
                                  setSwipedItemId(item.id);
                                  setSwipeOffset(Math.max(deltaX, -80));
                                } else {
                                  setSwipedItemId(null);
                                  setSwipeOffset(0);
                                }
                              }
                            }}
                            onTouchEnd={(event) => {
                              if (swipedItemId === item.id) {
                                // If swiped more than 50px, delete automatically
                                if (swipeOffset < -50) {
                                  void handleDeleteItem(item.id);
                                } else {
                                  // Otherwise close swipe
                                  setSwipedItemId(null);
                                  setSwipeOffset(0);
                                }
                              }
                              setSwipeStartX(null);
                            }}
                          >
                            <div className="todo-item-content">
                              <label>
                                <input
                                  type="checkbox"
                                  checked={item.done}
                                  onChange={() => handleToggleItem(activeList.id, item.id)}
                                />
                                <span className={item.done ? "todo-done" : ""}>{item.description}</span>
                              </label>
                            </div>
                            {swipedItemId === item.id && (
                              <button
                                className="todo-delete-button"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  void handleDeleteItem(item.id);
                                }}
                                onTouchStart={(e) => e.stopPropagation()}
                                onTouchMove={(e) => e.stopPropagation()}
                                onTouchEnd={(e) => e.stopPropagation()}
                                type="button"
                              >
                                Ta bort
                              </button>
                            )}
                          </li>
                        ))}
                      </ul>
                    </>
                  )}
                </>
              );
            })()}

            <form onSubmit={handleAddItem} className="inline-form">
              <input
                type="text"
                placeholder="Lägg till uppgift"
                value={newItemDescription}
                onChange={(event) => setNewItemDescription(event.target.value)}
              />
              <button 
                type="submit"
                className="add-item-button"
                style={activeList ? {
                  background: TODO_COLORS.find(c => c.value === activeList.color)?.gradient || "linear-gradient(135deg, #b8e6b8 0%, #a8d8a8 100%)",
                  color: "#2d5a2d",
                  boxShadow: `0 2px 6px ${TODO_COLORS.find(c => c.value === activeList.color)?.border || "#a8d8a8"}30`
                } : {}}
              >
                Lägg till
              </button>
            </form>
          </>
        )}
      </section>
    </div>
  );
}

// Sortable Todo Item Component
function SortableTodoItem({
  item,
  onToggle,
  onDelete,
  swipedItemId,
  swipeOffset,
  swipeStartX,
  setSwipeStartX,
  setSwipedItemId,
  setSwipeOffset,
}: {
  item: TodoItem;
  onToggle: () => void;
  onDelete: () => void;
  swipedItemId: string | null;
  swipeOffset: number;
  swipeStartX: number | null;
  setSwipeStartX: (x: number | null) => void;
  setSwipedItemId: (id: string | null) => void;
  setSwipeOffset: (offset: number) => void;
}) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: item.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  return (
    <li
      ref={setNodeRef}
      style={{
        ...style,
        transform: swipedItemId === item.id 
          ? `translateX(${swipeOffset}px) ${style.transform || ''}` 
          : style.transform,
        transition: swipedItemId === item.id && swipeOffset === -80 
          ? 'transform 0.2s ease' 
          : style.transition,
      }}
      className={`todo-draggable${isDragging ? " todo-draggable-active" : ""}`}
      data-item-id={item.id}
      onTouchStart={(e) => {
        setSwipeStartX(e.touches[0].clientX);
        if (swipedItemId !== item.id) {
          setSwipedItemId(null);
          setSwipeOffset(0);
        }
      }}
      onTouchMove={(e) => {
        if (swipeStartX === null) return;
        const deltaX = e.touches[0].clientX - swipeStartX;
        const deltaY = Math.abs(e.touches[0].clientY - e.touches[0].clientY);
        
        // Only handle swipe if horizontal movement is greater
        if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 10) {
          e.preventDefault();
          if (deltaX < 0) {
            setSwipedItemId(item.id);
            setSwipeOffset(Math.max(deltaX, -80));
          } else {
            setSwipedItemId(null);
            setSwipeOffset(0);
          }
        }
      }}
      onTouchEnd={() => {
        if (swipedItemId === item.id) {
          if (swipeOffset < -50) {
            onDelete();
          } else {
            setSwipedItemId(null);
            setSwipeOffset(0);
          }
        }
        setSwipeStartX(null);
      }}
    >
      <div className="todo-item-content" {...attributes} {...listeners}>
        <label>
          <input
            type="checkbox"
            checked={item.done}
            onChange={onToggle}
          />
          <span className={item.done ? "todo-done" : ""}>{item.description}</span>
        </label>
      </div>
      {swipedItemId === item.id && (
        <button
          className="todo-delete-button"
          onClick={(e) => {
            e.stopPropagation();
            onDelete();
          }}
          type="button"
        >
          Ta bort
        </button>
      )}
    </li>
  );
}

// Sortable Todo List Chip Component
function SortableTodoListChip({
  list,
  isActive,
  onClick,
}: {
  list: TodoList;
  isActive: boolean;
  onClick: () => void;
}) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: list.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  return (
    <button
      ref={setNodeRef}
      style={{
        ...style,
        background: TODO_COLORS.find(c => c.value === list.color)?.gradient || "linear-gradient(90deg, #b8e6b8 0%, #a8d8a8 100%)",
        border: isActive 
          ? `2px solid ${TODO_COLORS.find(c => c.value === list.color)?.border || "#a8d8a8"}`
          : `1px solid ${TODO_COLORS.find(c => c.value === list.color)?.border || "#a8d8a8"}80`,
        color: "#2d5a2d",
        boxShadow: isActive 
          ? `0 2px 8px ${TODO_COLORS.find(c => c.value === list.color)?.border || "#a8d8a8"}40`
          : `0 1px 3px ${TODO_COLORS.find(c => c.value === list.color)?.border || "#a8d8a8"}30`,
        fontWeight: isActive ? "600" : "500",
        position: "relative",
      }}
      className={`chip chip-draggable${isActive ? " chip-active" : ""}${isDragging ? " chip-dragging" : ""}`}
      onClick={onClick}
      {...attributes}
      {...listeners}
    >
      {list.name}
    </button>
  );
}



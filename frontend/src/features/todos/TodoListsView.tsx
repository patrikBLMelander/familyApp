import { useEffect, useState } from "react";
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
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const [dragOverId, setDragOverId] = useState<string | null>(null);
  const [draggingListId, setDraggingListId] = useState<string | null>(null);
  const [dragOverListId, setDragOverListId] = useState<string | null>(null);
  const [touchStartY, setTouchStartY] = useState<number | null>(null);
  const [touchStartListX, setTouchStartListX] = useState<number | null>(null);
  const [touchStartListY, setTouchStartListY] = useState<number | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [isDraggingList, setIsDraggingList] = useState(false);
  const [swipeStartX, setSwipeStartX] = useState<number | null>(null);
  const [swipeOffset, setSwipeOffset] = useState<number>(0);
  const [swipedItemId, setSwipedItemId] = useState<string | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [editingName, setEditingName] = useState(false);
  const [editNameValue, setEditNameValue] = useState("");
  const [showColorPicker, setShowColorPicker] = useState(false);

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

  const handleReorder = async () => {
    if (!activeListId || !draggingId || !dragOverId || draggingId === dragOverId) {
      setDraggingId(null);
      setDragOverId(null);
      return;
    }

    setLists((prev) => {
      return prev.map((list) => {
        if (list.id !== activeListId) return list;

        const activeItems = list.items.filter((item) => !item.done);
        const doneItems = list.items.filter((item) => item.done);

        const dragIndex = activeItems.findIndex((item) => item.id === draggingId);
        const targetIndex = activeItems.findIndex((item) => item.id === dragOverId);
        if (dragIndex === -1 || targetIndex === -1) {
          return list;
        }

        const reordered = [...activeItems];
        const [moved] = reordered.splice(dragIndex, 1);
        reordered.splice(targetIndex, 0, moved);

        // Bygg ny lista med uppdaterade positioner i den ordning vi vill visa.
        const newItemsWithPos = [...reordered, ...doneItems].map((item, index) => ({
          ...item,
          position: index
        }));

        // Fire-and-forget till backend; vi väntar inte på svaret för att UI ska kännas snabbt.
        void reorderTodoItems(list.id, newItemsWithPos.map((item) => item.id));

        return { ...list, items: newItemsWithPos };
      });
    });

    setDraggingId(null);
    setDragOverId(null);
  };

  const activeList = lists.find((l) => l.id === activeListId) ?? lists[0];

  const handleReorderLists = () => {
    if (!draggingListId || !dragOverListId || draggingListId === dragOverListId) {
      setDraggingListId(null);
      setDragOverListId(null);
      return;
    }

    setLists((prev) => {
      const current = [...prev];
      const fromIndex = current.findIndex((l) => l.id === draggingListId);
      const toIndex = current.findIndex((l) => l.id === dragOverListId);
      if (fromIndex === -1 || toIndex === -1) {
        return prev;
      }

      const [moved] = current.splice(fromIndex, 1);
      current.splice(toIndex, 0, moved);

      const withPositions = current.map((list, index) => ({
        ...list,
        position: index
      }));

      void reorderTodoLists(withPositions.map((l) => l.id));

      return withPositions;
    });

    setDraggingListId(null);
    setDragOverListId(null);
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
        <div className="list-selector-scroll">
          {lists.map((list) => (
            <button
              key={list.id}
              type="button"
              className={`chip chip-draggable${
                activeList?.id === list.id ? " chip-active" : ""
              }${dragOverListId === list.id ? " chip-drop-target" : ""}`}
              style={{
                background: TODO_COLORS.find(c => c.value === list.color)?.gradient || "linear-gradient(90deg, #b8e6b8 0%, #a8d8a8 100%)",
                border: activeList?.id === list.id 
                  ? `2px solid ${TODO_COLORS.find(c => c.value === list.color)?.border || "#a8d8a8"}`
                  : `1px solid ${TODO_COLORS.find(c => c.value === list.color)?.border || "#a8d8a8"}80`,
                color: "#2d5a2d",
                boxShadow: activeList?.id === list.id 
                  ? `0 2px 8px ${TODO_COLORS.find(c => c.value === list.color)?.border || "#a8d8a8"}40`
                  : `0 1px 3px ${TODO_COLORS.find(c => c.value === list.color)?.border || "#a8d8a8"}30`,
                fontWeight: activeList?.id === list.id ? "600" : "500",
                position: "relative"
              }}
              onClick={(event) => {
                // Only set active if not dragging and not just finished dragging
                if (!isDraggingList && !draggingListId) {
                  setActiveListId(list.id);
                }
              }}
              draggable
              data-dragging={draggingListId === list.id ? "true" : "false"}
              onDragStart={() => {
                setDraggingListId(list.id);
                setDragOverListId(null);
              }}
              onDragOver={(event) => {
                event.preventDefault();
                if (dragOverListId !== list.id) {
                  setDragOverListId(list.id);
                }
              }}
              onDrop={(event) => {
                event.preventDefault();
                handleReorderLists();
              }}
              onDragEnd={() => {
                setDraggingListId(null);
                setDragOverListId(null);
              }}
              onTouchStart={(event) => {
                setTouchStartListX(event.touches[0].clientX);
                setTouchStartListY(event.touches[0].clientY);
                setIsDraggingList(false);
              }}
              onTouchMove={(event) => {
                if (touchStartListX === null || touchStartListY === null) return;
                const deltaX = Math.abs(event.touches[0].clientX - touchStartListX);
                const deltaY = Math.abs(event.touches[0].clientY - touchStartListY);
                // Only start dragging if moved more than 10px (prioritize horizontal movement for lists)
                if (deltaX > 10 || (deltaX > 5 && deltaY < deltaX)) {
                  if (!isDraggingList) {
                    setIsDraggingList(true);
                    setDraggingListId(list.id);
                    setDragOverListId(null);
                  }
                  event.preventDefault(); // Prevent scrolling while dragging
                  const touchX = event.touches[0].clientX;
                  const touchY = event.touches[0].clientY;
                  const element = document.elementFromPoint(touchX, touchY);
                  const targetChip = element?.closest('.chip-draggable');
                  if (targetChip) {
                    const targetId = targetChip.getAttribute('data-list-id');
                    if (targetId && targetId !== draggingListId) {
                      setDragOverListId(targetId);
                    }
                  }
                }
              }}
              onTouchEnd={(event) => {
                if (isDraggingList && draggingListId && dragOverListId) {
                  event.preventDefault();
                  handleReorderLists();
                  // Prevent onClick from firing after drag
                  setTimeout(() => {
                    setDraggingListId(null);
                    setDragOverListId(null);
                  }, 100);
                } else {
                  setDraggingListId(null);
                  setDragOverListId(null);
                }
                setTouchStartListX(null);
                setTouchStartListY(null);
                setIsDraggingList(false);
              }}
              data-list-id={list.id}
            >
              {list.name}
            </button>
          ))}
        </div>
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
                  <ul className="todo-items">
                    {activeItems.map((item, index) => (
                      <li
                        key={item.id}
                        className={`todo-draggable${
                          draggingId === item.id ? " todo-draggable-active" : ""
                        }${dragOverId === item.id ? " todo-drop-target" : ""}`}
                        draggable
                        onDragStart={() => {
                          setDraggingId(item.id);
                          setDragOverId(null);
                        }}
                        onDragOver={(event) => {
                          event.preventDefault();
                          if (dragOverId !== item.id) {
                            setDragOverId(item.id);
                          }
                        }}
                        onDrop={(event) => {
                          event.preventDefault();
                          void handleReorder();
                        }}
                        onDragEnd={() => {
                          setDraggingId(null);
                          setDragOverId(null);
                        }}
                        onTouchStart={(event) => {
                          setTouchStartY(event.touches[0].clientY);
                          setSwipeStartX(event.touches[0].clientX);
                          setIsDragging(false);
                          // Close other swiped items
                          if (swipedItemId !== item.id) {
                            setSwipedItemId(null);
                            setSwipeOffset(0);
                          }
                        }}
                        onTouchMove={(event) => {
                          if (touchStartY === null || swipeStartX === null) return;
                          const deltaY = Math.abs(event.touches[0].clientY - touchStartY);
                          const deltaX = event.touches[0].clientX - swipeStartX;
                          
                          // Determine if it's a swipe (horizontal) or drag (vertical)
                          if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 10) {
                            // Horizontal swipe - show delete
                            event.preventDefault();
                            if (deltaX < 0) {
                              // Swiping left
                              setSwipedItemId(item.id);
                              setSwipeOffset(Math.max(deltaX, -80));
                            } else {
                              // Swiping right - close
                              setSwipedItemId(null);
                              setSwipeOffset(0);
                            }
                          } else if (deltaY > 10) {
                            // Vertical drag - reorder
                            if (!isDragging) {
                              setIsDragging(true);
                              setDraggingId(item.id);
                              setDragOverId(null);
                            }
                            event.preventDefault();
                            const touchY = event.touches[0].clientY;
                            const element = document.elementFromPoint(event.touches[0].clientX, touchY);
                            const targetItem = element?.closest('.todo-draggable');
                            if (targetItem) {
                              const targetId = targetItem.getAttribute('data-item-id');
                              if (targetId && targetId !== draggingId) {
                                setDragOverId(targetId);
                              }
                            }
                          }
                        }}
                        onTouchEnd={(event) => {
                          if (isDragging && draggingId && dragOverId) {
                            event.preventDefault();
                            void handleReorder();
                            setDraggingId(null);
                            setDragOverId(null);
                            setIsDragging(false);
                          } else if (swipedItemId === item.id) {
                            // If swiped more than 50px, delete automatically
                            if (swipeOffset < -50) {
                              void handleDeleteItem(item.id);
                            } else {
                              // Otherwise close swipe
                              setSwipedItemId(null);
                              setSwipeOffset(0);
                            }
                          }
                          setTouchStartY(null);
                          setSwipeStartX(null);
                        }}
                        data-item-id={item.id}
                        style={{
                          transform: swipedItemId === item.id ? `translateX(${swipeOffset}px)` : 'translateX(0)',
                          transition: swipedItemId === item.id && swipeOffset === -80 ? 'transform 0.2s ease' : 'none'
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



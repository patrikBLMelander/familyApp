# Code Review: Todo Item Edit Feature (Swipe Right)

**Reviewer:** Senior Developer  
**Date:** 2024  
**Feature:** Swipe right to edit todo items  
**Files Changed:**
- `backend/src/main/java/com/familyapp/api/todo/TodoListController.java`
- `backend/src/main/java/com/familyapp/application/todo/TodoListService.java`
- `frontend/src/shared/api/todos.ts`
- `frontend/src/features/todos/TodoListsView.tsx`
- `frontend/src/styles.css`

---

## ✅ Positiva Aspekter

1. **Konsekvent API-design**: Följer samma mönster som övriga endpoints (PATCH, request records)
2. **Återanvändbar service-metod**: `updateItem` är väl strukturerad och följer samma mönster som andra metoder
3. **Visuell feedback**: Bra CSS-styling för edit-knappen med tydlig visuell skillnad från delete
4. **Funktionalitet fungerar**: Core-featuren är implementerad och fungerar

---

## 🔴 Kritiska Problem

### 1. **Felaktig deltaY-beräkning i swipe-logik**
**Location:** `TodoListsView.tsx:758, 861`

```typescript
const deltaY = Math.abs(e.touches[0].clientY - e.touches[0].clientY);
```

Detta jämför alltid samma värde med sig själv, vilket alltid ger 0. Detta gör att vertikal swipe-detektering inte fungerar korrekt.

**Fix:**
```typescript
const startY = swipeStartY ?? e.touches[0].clientY; // Spara startY i state
const deltaY = Math.abs(e.touches[0].clientY - startY);
```

### 2. **Saknad validering i backend**
**Location:** `TodoListService.java:191-205`

```java
public TodoList updateItem(UUID listId, UUID itemId, String description) {
    // ...
    item.setDescription(description); // Ingen validering!
```

Om `description` är tom efter trim (vilket frontend hanterar), borde backend också validera detta. Detta är en säkerhetsrisk.

**Fix:**
```java
if (description == null || description.trim().isEmpty()) {
    throw new IllegalArgumentException("Description cannot be empty");
}
```

### 3. **onBlur triggar save automatiskt - dålig UX**
**Location:** `TodoListsView.tsx:910-912, 696-698`

När användaren klickar utanför input-fältet sparas ändringarna automatiskt. Detta kan vara frustrerande om:
- Användaren klickar av misstag
- Användaren vill avbryta men råkar klicka utanför
- Det finns en loading state som blockerar andra interaktioner

**Rekommendation:** Lägg till en "Avbryt"-knapp eller använd Escape-tangent.

---

## ⚠️ Viktiga Förbättringar

### 4. **Duplicerad swipe-logik**
**Location:** `TodoListsView.tsx:646-665` (done items) och `858-895` (SortableTodoItem)

Samma swipe-logik är implementerad två gånger. Detta ökar risken för buggar och gör underhåll svårare.

**Rekommendation:** Extrahera till en custom hook:
```typescript
function useSwipeActions(onSwipeLeft, onSwipeRight) {
  // Swipe logic here
  return { onTouchStart, onTouchMove, onTouchEnd };
}
```

### 5. **Magic numbers utan konstanter**
**Location:** Flera ställen med `50`, `80`, `-80`

```typescript
if (swipeOffset < -50) { // Vad betyder 50?
setSwipeOffset(Math.max(deltaX, -80)); // Varför 80?
```

**Fix:**
```typescript
const SWIPE_THRESHOLD = 50; // px för att trigga action
const MAX_SWIPE_OFFSET = 80; // px max swipe distance
```

### 6. **Saknad optimistisk uppdatering**
**Location:** `TodoListsView.tsx:296-311`

`handleUpdateItem` väntar på server-response innan UI uppdateras. `handleDeleteItem` använder optimistisk uppdatering vilket ger bättre UX.

**Rekommendation:** Implementera optimistisk uppdatering även för edit:
```typescript
// Optimistic update
setLists((prev) => prev.map((list) => {
  if (list.id !== activeListId) return list;
  return {
    ...list,
    items: list.items.map((item) => 
      item.id === itemId 
        ? { ...item, description: editItemDescription.trim() }
        : item
    )
  };
}));
```

### 7. **Oanvända props**
**Location:** `SortableTodoItem` tar emot `onCancelEdit` och `activeListId` men använder dem aldrig.

**Fix:** Ta bort oanvända props eller implementera funktionalitet.

### 8. **Saknad Escape-tangent hantering**
**Location:** `TodoListsView.tsx:106-115`

Escape-tangent hanteras för `editingName` men inte för `editingItemId`.

**Fix:**
```typescript
useEffect(() => {
  const handleEscape = (e: KeyboardEvent) => {
    if (e.key === "Escape") {
      setMenuOpen(false);
      setEditingName(false);
      if (editingItemId) {
        setEditingItemId(null);
        setEditItemDescription("");
      }
    }
  };
  window.addEventListener("keydown", handleEscape);
  return () => window.removeEventListener("keydown", handleEscape);
}, [editingItemId]);
```

### 9. **Ingen loading state**
**Location:** `handleUpdateItem`

Under uppdatering finns ingen visuell feedback. Användaren vet inte om operationen pågår eller om den misslyckades.

**Rekommendation:** Lägg till loading state och disable input under uppdatering.

### 10. **Saknad error recovery**
**Location:** `handleUpdateItem:308-310`

Vid fel visas bara ett generiskt felmeddelande. Ingen återställning av föregående värde eller retry-möjlighet.

**Rekommendation:** Återställ föregående värde vid fel:
```typescript
catch {
  setError("Kunde inte uppdatera uppgift.");
  // Restore previous value
  const previousItem = activeList?.items.find(i => i.id === itemId);
  if (previousItem) {
    setEditItemDescription(previousItem.description);
  }
}
```

---

## 💡 Mindre Förbättringar

### 11. **Inkonsekvent error handling**
`handleDeleteItem` har error recovery med reload, men `handleUpdateItem` har inte det. Bör vara konsekvent.

### 12. **Saknad accessibility**
- Inga ARIA-labels för edit-knappen
- Ingen keyboard navigation för swipe-actions
- Saknad `aria-live` region för edit-mode

### 13. **Transform-strängkonkatenering kan vara problematisk**
**Location:** `TodoListsView.tsx:842-844`

```typescript
transform: swipedItemId === item.id 
  ? `translateX(${swipeOffset}px) ${style.transform || ''}` 
  : style.transform,
```

Om `style.transform` redan innehåller `translateX` kan detta skapa konflikter. Bättre att använda CSS custom properties eller kombinera transforms korrekt.

### 14. **Saknad debouncing för swipe**
Vid snabba swipes kan flera state-uppdateringar triggas. Overväg debouncing eller throttling.

### 15. **Backend: Saknad uppdatering av item.updatedAt**
**Location:** `TodoListService.java:200`

Endast `list.setUpdatedAt()` uppdateras, men inte `item.setUpdatedAt()` om denna property finns.

---

## 📋 Testning

### Saknade test-scenarion:
1. ✅ Swipe right triggar edit
2. ✅ Swipe left triggar delete (befintlig)
3. ❌ Swipe right + swipe left i snabb följd
4. ❌ Edit item med tom sträng
5. ❌ Edit item med mycket lång text
6. ❌ Edit item medan drag-and-drop pågår
7. ❌ Network error under edit
8. ❌ Edit item medan annan item redigeras
9. ❌ Escape-tangent avbryter edit
10. ❌ Click utanför input avbryter edit (om implementerat)

---

## 🎯 Prioriterade Åtgärder

### Must Fix (Innan merge):
1. Fixa deltaY-beräkningen (#1)
2. Lägg till backend-validering (#2)
3. Implementera Escape-tangent (#8)

### Should Fix (Snart):
4. Extrahera duplicerad swipe-logik (#4)
5. Lägg till optimistisk uppdatering (#6)
6. Förbättra error recovery (#10)

### Nice to Have:
7. Magic numbers till konstanter (#5)
8. Loading states (#9)
9. Accessibility-förbättringar (#12)

---

## 📝 Sammanfattning

**Overall Assessment:** ⚠️ **Conditional Approval**

Implementationen fungerar men har flera problem som bör åtgärdas innan produktion:
- Kritiska buggar i swipe-detektering
- Saknad validering i backend
- Dålig UX för edit-avbrytning

**Rekommendation:** Fixa kritiska problem (#1, #2, #8) och åtminstone en viktig förbättring (#4 eller #6) innan merge.

**Estimated Fix Time:** 2-4 timmar för kritiska fixes, ytterligare 4-6 timmar för alla förbättringar.

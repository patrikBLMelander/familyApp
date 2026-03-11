# Code Review: List Navigation Fix

**Datum:** 2024  
**Reviewer:** Senior Developer  
**Ändring:** Fix för navigation till specifik lista från AdultDashboard

## Översikt

Ändringen löser problemet där klick på en lista i AdultDashboard alltid navigerade till första listan istället för den valda. Implementationen är funktionell men har flera områden som behöver förbättras.

---

## ✅ Positiva Aspekter

1. **Tydlig separation of concerns**: Ändringen är lokaliserad till relevanta komponenter
2. **Backward compatible**: TypeScript-signaturen är kompatibel med befintlig kod (optional params)
3. **Funktionell lösning**: Problemet löses på ett direkt sätt

---

## ⚠️ Kritiska Problem

### 1. **Memory Leak / Stale State i App.tsx**

**Problem:**
```typescript
const [navigationParams, setNavigationParams] = useState<{ listId?: string } | null>(null);

const handleNavigate = (view: ViewKey, params?: { listId?: string }) => {
  setCurrentView(view);
  setMenuOpen(false);
  setNavigationParams(params || null);  // ⚠️ Problemet: rensas aldrig
};
```

**Scenario:**
1. Användare klickar på lista A → `navigationParams = { listId: "A" }`
2. Användare navigerar till dashboard → `navigationParams` är fortfarande `{ listId: "A" }`
3. Användare navigerar tillbaka till todos (utan att klicka på lista) → använder fortfarande lista A

**Konsekvens:** Fel lista kan visas när man navigerar tillbaka till todos-vyn.

**Lösning:**
```typescript
const handleNavigate = (view: ViewKey, params?: { listId?: string }) => {
  setCurrentView(view);
  setMenuOpen(false);
  // Rensa params när man navigerar bort från todos
  if (view === "todos") {
    setNavigationParams(params || null);
  } else {
    setNavigationParams(null);
  }
};
```

---

### 2. **Race Condition i TodoListsView.tsx**

**Problem:**
Två separata `useEffect` hooks som båda kan uppdatera `activeListId`:

```typescript
// Effect 1: Laddar listor och sätter activeListId
useEffect(() => {
  // ... laddar listor
  if (initialListId && sortedLists.find(l => l.id === initialListId)) {
    setActiveListId(initialListId);  // ⚠️ Kan köras samtidigt med Effect 2
  }
}, []);

// Effect 2: Uppdaterar activeListId när initialListId ändras
useEffect(() => {
  if (initialListId && lists.length > 0) {
    setActiveListId(initialListId);  // ⚠️ Kan köras samtidigt med Effect 1
  }
}, [initialListId, lists]);
```

**Konsekvens:** Odefinierat beteende om båda effects körs samtidigt.

**Lösning:** Konsolidera logiken till en enda effect eller använd en ref för att tracka om initial load är klar.

---

### 3. **Stale Closure i TodoListsView.tsx**

**Problem:**
```typescript
useEffect(() => {
  const load = async () => {
    // ...
    if (initialListId && sortedLists.find(l => l.id === initialListId)) {
      setActiveListId(initialListId);  // ⚠️ Använder initialListId från första render
    } else if (!activeListId) {  // ⚠️ Använder activeListId från första render
      setActiveListId(sortedLists[0].id);
    }
  };
  void load();
}, []);  // ⚠️ Tom dependency array
```

**Konsekvens:** Om `initialListId` ändras efter mount kommer den första effecten inte reagera.

**Lösning:** Ta bort första effectens logik för `initialListId` och låt andra effecten hantera det.

---

## 🔧 Förbättringsförslag

### 4. **Saknad Validering**

**Problem:**
```typescript
onClick={() => onNavigate?.("todos", { listId: list.id })}
```

Om `list.id` är `undefined` eller om listan raderas mellan klick och navigation, kommer `TodoListsView` att försöka visa en lista som inte finns.

**Lösning:**
```typescript
// I TodoListsView.tsx
useEffect(() => {
  if (initialListId && lists.length > 0) {
    const listExists = lists.find(l => l.id === initialListId);
    if (listExists) {
      setActiveListId(initialListId);
    } else {
      // Fallback till första listan om initialListId inte finns
      console.warn(`List with id ${initialListId} not found, falling back to first list`);
      setActiveListId(lists[0].id);
    }
  }
}, [initialListId, lists]);
```

---

### 5. **Type Safety - Optional Params Pattern**

**Förbättring:**
Istället för att ändra signaturen för `onNavigate` i `AdultDashboard`, överväg en mer explicit approach:

```typescript
// Alternativ 1: Separata navigation functions
type AdultDashboardProps = {
  onNavigate?: (view: ViewKey) => void;
  onNavigateToList?: (listId: string) => void;
};

// Alternativ 2: Union type för tydlighet
type NavigationParams = 
  | { view: "todos"; listId: string }
  | { view: Exclude<ViewKey, "todos"> };

type AdultDashboardProps = {
  onNavigate?: (params: NavigationParams) => void;
};
```

**Nuvarande approach fungerar** men är mindre explicit.

---

### 6. **Performance - Onödiga Re-renders**

**Problem:**
```typescript
useEffect(() => {
  if (initialListId && lists.length > 0) {
    const listExists = lists.find(l => l.id === initialListId);
    if (listExists) {
      setActiveListId(initialListId);  // ⚠️ Körs varje gång lists ändras
    }
  }
}, [initialListId, lists]);
```

**Konsekvens:** Effect körs varje gång `lists` uppdateras, även om `activeListId` redan är korrekt.

**Lösning:**
```typescript
useEffect(() => {
  if (initialListId && lists.length > 0 && activeListId !== initialListId) {
    const listExists = lists.find(l => l.id === initialListId);
    if (listExists) {
      setActiveListId(initialListId);
    }
  }
}, [initialListId, lists, activeListId]);
```

---

## 📝 Rekommenderade Ändringar

### Prioritet 1 (Kritiskt):
1. ✅ Fixa memory leak i `App.tsx` - rensa `navigationParams` när man navigerar bort
2. ✅ Konsolidera `useEffect` logik i `TodoListsView.tsx` för att undvika race conditions
3. ✅ Lägg till validering för att hantera fall där `listId` inte finns

### Prioritet 2 (Viktigt):
4. ✅ Förbättra dependency arrays i `useEffect` hooks
5. ✅ Lägg till fallback-hantering om lista inte hittas

### Prioritet 3 (Nice to have):
6. Överväg mer explicit type system för navigation params
7. Lägg till error logging för edge cases

---

## 🧪 Test Scenarios att Verifiera

1. ✅ Klicka på lista A → ska visa lista A
2. ✅ Klicka på lista B → ska visa lista B  
3. ✅ Navigera till dashboard → sedan tillbaka till todos (utan klick) → ska visa första listan
4. ✅ Navigera till todos med listId → lista raderas → ska fallback till första listan
5. ✅ Navigera till todos med ogiltigt listId → ska fallback till första listan
6. ✅ Snabb navigation: klicka på lista A, sedan direkt lista B → ska visa lista B

---

## Sammanfattning

**Status:** ✅ **Fixar Implementerade**

Alla kritiska problem har åtgärdats:
- ✅ Memory leak fixad - `navigationParams` rensas när man navigerar bort från todos
- ✅ Race conditions eliminerade - useEffect logik konsoliderad
- ✅ Validering och fallback implementerad - hanterar ogiltiga listId

**Status efter fixes:** ✅ **Redo för Merge**

---

## Implementerade Fixar

### Fix 1: Memory Leak i App.tsx ✅
```typescript
const handleNavigate = (view: ViewKey, params?: { listId?: string }) => {
  setCurrentView(view);
  setMenuOpen(false);
  // Only set navigation params for todos view, clear otherwise
  if (view === "todos") {
    setNavigationParams(params || null);
  } else {
    setNavigationParams(null);
  }
};
```

### Fix 2: Konsoliderad useEffect Logik ✅
- Separerat list-loading från activeListId-hantering
- En enda useEffect hanterar all activeListId-logik
- Inkluderar validering och fallback för ogiltiga listId
- Undviker onödiga uppdateringar genom att kontrollera `activeListId !== initialListId`

### Fix 3: Validering och Fallback ✅
- Kontrollerar om listId finns innan användning
- Fallback till första listan om listId inte hittas
- Console warning för debugging

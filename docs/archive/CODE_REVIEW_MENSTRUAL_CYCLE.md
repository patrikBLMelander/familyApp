# Code Review: Menstrual Cycle Feature

## Översikt
Denna code review täcker menscykel-funktionaliteten i både frontend (React/TypeScript) och backend (Java/Spring Boot).

---

## ✅ Styrkor

### Frontend
1. **Bra separation of concerns** - Tydlig uppdelning mellan View och Calendar-komponenter
2. **Type safety** - Bra användning av TypeScript types
3. **User experience** - Bra drag-and-drop funktionalitet och touch-device support
4. **State management** - Tydlig hantering av state med useState och useEffect
5. **Error handling** - Tydliga felmeddelanden för användaren

### Backend
1. **Säkerhet** - Bra validering och access control
2. **Validering** - Validering av period length, dates, etc.
3. **Transaktioner** - Korrekt användning av @Transactional
4. **Domain model** - Tydlig separation mellan domain, infrastructure och API layers

---

## ⚠️ Problem och Förbättringar

### 🔴 Kritiska Problem

#### 1. **Race Condition i Save-funktionen** (Frontend)
**Fil:** `MenstrualCycleView.tsx:290-365`

**Problem:**
```typescript
// Delete all existing entries - we'll recreate them from selectedDays
for (const entry of entries) {
  await deleteMenstrualCycleEntry(targetMemberId, entry.id);
}
```

Om användaren klickar på "Spara" flera gånger snabbt kan detta orsaka race conditions där entries tas bort medan nya skapas, vilket kan leda till dataförlust.

**Lösning:**
- Lägg till en `isSaving` state och disable knappen under sparande
- Eller använd en queue/transaction-liknande approach
- Eller optimera genom att bara skapa/ta bort entries som faktiskt ändrats

**Förslag:**
```typescript
const [isSaving, setIsSaving] = useState(false);

onSave={async (selectedDays) => {
  if (!targetMemberId || isSaving) return;
  
  setIsSaving(true);
  try {
    // ... existing code ...
  } finally {
    setIsSaving(false);
  }
}}
```

#### 2. **Saknad Error Handling för Network Failures** (Frontend)
**Fil:** `MenstrualCycleView.tsx:290-365`

**Problem:**
Om nätverket går ner mitt i en save-operation (t.ex. efter att entries tagits bort men innan nya skapats), kan data gå förlorad.

**Lösning:**
- Implementera retry-logik
- Eller använd en optimistisk update-strategi där vi först skapar nya entries, sedan tar bort gamla
- Eller implementera en transaction-liknande approach på backend

#### 3. **useEffect Dependency Warning** (Frontend)
**Fil:** `MenstrualCycleCalendar.tsx:91-108`

**Problem:**
```typescript
useEffect(() => {
  // ... uses selectedDays but not in dependency array
}, [entries]);
```

`selectedDays` används i useEffect men finns inte i dependency array, vilket kan orsaka stale closures.

**Lösning:**
Lägg till `selectedDays` i dependency array eller refaktorera logiken.

---

### 🟡 Viktiga Förbättringar

#### 4. **Kodupprepning - Date Parsing** (Frontend)
**Fil:** `MenstrualCycleCalendar.tsx:36-47` och `MenstrualCycleView.tsx:295-306`

**Problem:**
Samma `parseLocalDate` och `formatLocalDate` funktioner är duplicerade i två filer.

**Lösning:**
Flytta till en shared utility-fil:
```typescript
// frontend/src/shared/utils/dateUtils.ts
export function parseLocalDate(dateString: string): Date {
  const [year, month, day] = dateString.split('-').map(Number);
  return new Date(year, month - 1, day, 0, 0, 0, 0);
}

export function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
```

#### 5. **Ineffektiv Save-strategi** (Frontend)
**Fil:** `MenstrualCycleView.tsx:308-354`

**Problem:**
Varje gång användaren sparar tas ALLA entries bort och skapas om, även om inget ändrats. Detta är ineffektivt och kan orsaka onödiga API-anrop.

**Lösning:**
Jämför `selectedDays` med befintliga entries och skapa/ta bort endast vad som behövs:
```typescript
// Calculate diff
const entriesToDelete = entries.filter(entry => {
  // Check if entry should be deleted
});
const daysToAdd = // Calculate new days to add

// Only delete/create what's needed
```

#### 6. **Saknad Loading State på Save-knappen** (Frontend)
**Fil:** `MenstrualCycleCalendar.tsx:638-665`

**Problem:**
Användaren får ingen feedback när spara-operationen pågår.

**Lösning:**
Lägg till loading state:
```typescript
const [isSaving, setIsSaving] = useState(false);

<button
  disabled={isSaving}
  onClick={async () => {
    setIsSaving(true);
    try {
      await handleSave();
    } finally {
      setIsSaving(false);
    }
  }}
>
  {isSaving ? "Sparar..." : "Spara ändringar"}
</button>
```

#### 7. **Memory Leak Risk** (Frontend)
**Fil:** `MenstrualCycleCalendar.tsx:403-414`

**Problem:**
`handleDayMouseUp` används i useEffect dependency men är inte memoized, vilket kan orsaka memory leaks.

**Lösning:**
Använd `useCallback`:
```typescript
const handleDayMouseUp = useCallback(() => {
  // ... existing code ...
}, []);
```

#### 8. **Saknad Input Validation** (Backend)
**Fil:** `MenstrualCycleService.java:56-74`

**Problem:**
Validering av period length är bra, men det saknas validering för:
- Att period start date inte är för långt i det förflutna (t.ex. > 10 år)
- Att period length är rimlig i förhållande till cycle length
- Att det inte finns överlappande entries

**Lösning:**
Lägg till ytterligare validering:
```java
// Validate date is not too far in the past
if (periodStartDate.isBefore(LocalDate.now().minusYears(10))) {
    throw new IllegalArgumentException("Period start date cannot be more than 10 years in the past");
}

// Check for overlapping entries
var overlapping = repository.findByMemberIdAndPeriodStartDate(memberId, periodStartDate);
if (overlapping.isPresent()) {
    throw new IllegalArgumentException("An entry already exists for this period start date");
}
```

#### 9. **Saknad Transaction Rollback Handling** (Backend)
**Fil:** `MenstrualCycleService.java`

**Problem:**
Om flera entries skapas och en misslyckas, kan det lämna databasen i inkonsistent tillstånd.

**Lösning:**
Eftersom metoden redan är `@Transactional` borde detta hanteras automatiskt, men överväg att lägga till explicit rollback-logik för kritiska operationer.

#### 10. **Saknad Rate Limiting** (Backend)
**Fil:** `MenstrualCycleController.java`

**Problem:**
Ingen rate limiting på API-endpoints, vilket kan leda till abuse.

**Lösning:**
Implementera rate limiting (t.ex. med Spring Security eller en custom filter).

---

### 🟢 Mindre Förbättringar

#### 11. **Magic Numbers** (Frontend)
**Fil:** `MenstrualCycleCalendar.tsx:368`

**Problem:**
```typescript
setTimeout(() => {
  justDraggedRef.current = false;
}, 100);
```

Magic number `100` bör vara en konstant.

**Lösning:**
```typescript
const DRAG_RESET_DELAY_MS = 100;
setTimeout(() => {
  justDraggedRef.current = false;
}, DRAG_RESET_DELAY_MS);
```

#### 12. **Saknad Accessibility** (Frontend)
**Fil:** `MenstrualCycleCalendar.tsx`

**Problem:**
- Kalender-dagar saknar `role="button"` och `aria-label`
- Keyboard navigation saknas
- Focus management saknas

**Lösning:**
Lägg till ARIA-attribut och keyboard support:
```typescript
<div
  role="button"
  tabIndex={0}
  aria-label={`${dayInfo.day} ${monthName}`}
  onKeyDown={(e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      toggleDay(dayInfo.date);
    }
  }}
>
```

#### 13. **Saknad Error Boundary** (Frontend)
**Fil:** `MenstrualCycleView.tsx`

**Problem:**
Om ett oväntat fel uppstår kan hela komponenten krascha.

**Lösning:**
Lägg till error boundary runt komponenten.

#### 14. **Console.error i Production** (Frontend)
**Fil:** `MenstrualCycleView.tsx:41`

**Problem:**
```typescript
console.error("Failed to load current member:", e);
```

Console.error bör inte finnas i production code.

**Lösning:**
Använd en proper logging service eller ta bort i production builds.

#### 15. **Saknad Unit Tests**
**Problem:**
Inga unit tests hittades för menscykel-funktionaliteten.

**Lösning:**
Lägg till tests för:
- Date parsing/formatting
- Cycle calculation logic
- Entry grouping logic
- Validation logic

#### 16. **Saknad Type Safety för Phase** (Frontend)
**Fil:** `MenstrualCycleView.tsx:93-136`

**Problem:**
```typescript
const getPhaseLabel = (phase: string) => {
```

`phase` är `string` istället för en union type.

**Lösning:**
```typescript
type CyclePhase = "menstruation" | "follicular" | "ovulation" | "luteal";

const getPhaseLabel = (phase: CyclePhase): string => {
  // ...
}
```

#### 17. **Ineffektiv Date Comparison** (Frontend)
**Fil:** `MenstrualCycleCalendar.tsx:138-188`

**Problem:**
Många `new Date()` skapas i `getDayType`, vilket kan vara ineffektivt.

**Lösning:**
Memoize `today` date eller flytta ut den.

#### 18. **Saknad Optimistic Updates** (Frontend)
**Problem:**
UI uppdateras först efter API-anrop, vilket kan kännas långsamt.

**Lösning:**
Implementera optimistic updates för bättre UX.

---

## 📊 Prestanda

### Frontend
- **Kalender-rendering:** O(n) där n = antal dagar i månaden - OK
- **Entry grouping:** O(n log n) för sortering - OK för normala dataset
- **State updates:** Många re-renders kan optimeras med `useMemo` och `useCallback`

### Backend
- **Database queries:** En query per entry vid delete - kan optimeras med batch delete
- **Cycle calculation:** O(n) där n = antal entries - OK

---

## 🔒 Säkerhet

### ✅ Bra
- Access control validering
- Input validation
- Device token authentication

### ⚠️ Förbättringar
- Rate limiting saknas
- CORS headers bör verifieras
- XSS protection - inline styles är OK men överväg CSS-in-JS library

---

## 📝 Dokumentation

### Saknas
- JSDoc/TSDoc kommentarer på funktioner
- README för menscykel-feature
- API dokumentation

---

## 🎯 Rekommendationer per Prioritet

### Hög prioritet
1. Fixa race condition i save-funktionen (#1)
2. Lägg till loading state på save-knappen (#6)
3. Fixa useEffect dependency warning (#3)
4. Lägg till error handling för network failures (#2)

### Medel prioritet
5. Refaktorera duplicerad kod (#4)
6. Optimera save-strategi (#5)
7. Lägg till input validation (#8)
8. Lägg till accessibility (#12)

### Låg prioritet
9. Fixa magic numbers (#11)
10. Lägg till unit tests (#15)
11. Förbättra type safety (#16)
12. Lägg till dokumentation

---

## ✅ Sammanfattning

**Totalt antal problem:** 18
- 🔴 Kritiska: 3
- 🟡 Viktiga: 7
- 🟢 Mindre: 8

**Övergripande bedömning:** 
Implementationen är solid med bra separation of concerns och säkerhet. De kritiska problemen bör fixas innan production, men koden är i stort sett välstrukturerad och maintainable.

**Nästa steg:**
1. Fixa de kritiska problemen (race conditions, error handling)
2. Lägg till loading states för bättre UX
3. Refaktorera duplicerad kod
4. Lägg till tests

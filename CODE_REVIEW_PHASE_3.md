# Code Review: Phase 3 - CalendarContainer Implementation

**Datum:** 2026-01-20  
**Reviewer:** Senior Developer  
**Scope:** Phase 3 refactoring - CalendarContainer + CalendarView separation

---

## 📊 Översikt

**Mål:** Separera state management från presentation genom att skapa `CalendarContainer` och göra `CalendarView` till en tunn wrapper.

**Resultat:**
- ✅ `CalendarView.tsx`: 15 rader (från 464 rader) - 97% minskning
- ✅ `CalendarContainer.tsx`: 462 rader - All state management och logik
- ✅ Separation of concerns: Tydlig separation mellan wrapper och container

---

## ✅ Styrkor

### 1. **Tydlig Separation of Concerns**
- `CalendarView` är nu en ren wrapper som bara renderar `CalendarContainer`
- All state management är isolerad i `CalendarContainer`
- Hooks är väl separerade (`useCalendarData`, `useCalendarEvents`)

### 2. **Bra Hook-struktur**
- `useCalendarData`: Hanterar all data fetching och state
- `useCalendarEvents`: Hanterar alla CRUD-operationer
- Tydlig ansvarsfördelning

### 3. **Konsistent Error Handling**
- Try-catch blocks i alla async operations
- Användarvänliga felmeddelanden
- Error state hanteras korrekt

### 4. **Performance Optimizations**
- `useCallback` används konsekvent för att förhindra onödiga re-renders
- Optimistic updates i `handleToggleTask`
- Smart data fetching baserat på view type

---

## ⚠️ Problem och Förbättringsmöjligheter

### 🔴 Hög Prioritet

#### 1. **Duplicerad Date Formatting Logic**
**Fil:** `CalendarContainer.tsx` (rader 386-392, 410-415)

```typescript
// WeekView onDayClick
const year = date.getFullYear();
const month = String(date.getMonth() + 1).padStart(2, "0");
const day = String(date.getDate()).padStart(2, "0");
const hourStr = hour !== undefined ? String(hour).padStart(2, "0") : "00";
const dateStr = `${year}-${month}-${day}T${hourStr}:00`;

// MonthView onDayClick
const year = date.getFullYear();
const month = String(date.getMonth() + 1).padStart(2, "0");
const day = String(date.getDate()).padStart(2, "0");
const dateStr = `${year}-${month}-${day}`;
```

**Problem:** Samma logik finns i två ställen och även i `useCalendarEvents.ts` (rader 109-112).

**Lösning:** Extrahera till utility-funktioner i `dateFormatters.ts`:
```typescript
export function formatDateForEventForm(date: Date, hour?: number): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  if (hour !== undefined) {
    const hourStr = String(hour).padStart(2, "0");
    return `${year}-${month}-${day}T${hourStr}:00`;
  }
  return `${year}-${month}-${day}`;
}
```

#### 2. **useEffect Dependencies - Potentiella Buggar**
**Fil:** `CalendarContainer.tsx` (rader 84-93, 96-98, 101-109)

```typescript
useEffect(() => {
  const loadMember = async () => {
    const memberId = await loadCurrentMember();
    if (memberId) {
      setCurrentMemberId(memberId);
    }
  };
  void loadMember();
  void loadData();
}, [loadCurrentMember, loadData]); // ⚠️ loadData ändras vid varje render
```

**Problem:** 
- `loadData` är en `useCallback` som ändras när `viewType`, `currentWeek`, eller `currentMonth` ändras
- Detta kan orsaka oändliga loops eller onödiga re-renders
- `loadCurrentMember` är stabil (tom dependency array), men `loadData` är det inte

**Lösning:** 
```typescript
// Option 1: Separera dependencies
useEffect(() => {
  void loadData();
}, [viewType, currentWeek, currentMonth]); // Explicit dependencies

useEffect(() => {
  const loadMember = async () => {
    const memberId = await loadCurrentMember();
    if (memberId) {
      setCurrentMemberId(memberId);
    }
  };
  void loadMember();
}, [loadCurrentMember]); // loadCurrentMember är stabil
```

#### 3. **handleToggleTaskWrapper - Onödig Wrapper**
**Fil:** `CalendarContainer.tsx` (rader 125-128)

```typescript
const handleToggleTaskWrapper = (eventId: string, memberId?: string) => {
  void handleToggleTask(eventId, memberId || currentMemberId, selectedDate, showAllMembers);
};
```

**Problem:** 
- Onödig wrapper-funktion som bara ändrar signaturen
- `RollingView` kunde anpassa sig till `handleToggleTask`-signaturen istället

**Lösning:** 
- Antingen: Ändra `RollingView` props för att matcha `handleToggleTask`-signaturen
- Eller: Flytta wrapper-logiken till `useCalendarData` och returnera en wrapper-funktion direkt

#### 4. **Missing Error Handling i loadCategories**
**Fil:** `useCalendarData.ts` (rader 260-268)

```typescript
const loadCategories = useCallback(async () => {
  try {
    const categoriesData = await fetchCalendarCategories();
    setCategories(categoriesData);
  } catch (e) {
    console.error("Error loading categories:", e);
    // Don't set error state here, just log it
  }
}, []);
```

**Problem:** 
- Fel ignoreras tyst
- Användaren får ingen feedback om kategorier inte kan laddas
- Kan orsaka förvirring när kategorier inte uppdateras

**Lösning:** 
```typescript
const loadCategories = useCallback(async () => {
  try {
    const categoriesData = await fetchCalendarCategories();
    setCategories(categoriesData);
  } catch (e) {
    console.error("Error loading categories:", e);
    // Set error state or show a toast notification
    setError("Kunde inte ladda kategorier. Försök igen.");
  }
}, [setError]);
```

### 🟡 Medel Prioritet

#### 5. **Inline Styles - Stora Block**
**Fil:** `CalendarContainer.tsx` (rader 177-340)

**Problem:** 
- Mycket inline styles gör koden svårläst
- Svårt att underhålla och konsistent styling
- Ingen möjlighet att återanvända styles

**Lösning:** 
- Extrahera till CSS-klasser eller styled components
- Skapa en `CalendarFilters.tsx` komponent (som planerat i Phase 4)

#### 6. **Magic Strings för View Types**
**Fil:** `CalendarContainer.tsx`, `useCalendarData.ts`

```typescript
type CalendarViewType = "rolling" | "week" | "month";
```

**Problem:** 
- Magic strings används direkt i koden
- Risk för typo-buggar
- Svårt att refaktorera

**Lösning:** 
- Skapa en `constants.ts` fil:
```typescript
export const CALENDAR_VIEW_TYPES = {
  ROLLING: "rolling",
  WEEK: "week",
  MONTH: "month",
} as const;

export type CalendarViewType = typeof CALENDAR_VIEW_TYPES[keyof typeof CALENDAR_VIEW_TYPES];
```

#### 7. **filteredEvents Beräknas Vid Varje Render**
**Fil:** `CalendarContainer.tsx` (rader 111-123)

```typescript
const filteredEvents = events.filter(event => {
  // ... filtering logic
});
```

**Problem:** 
- Beräknas vid varje render, även när `events`, `showTasksOnly`, `showAllMembers`, eller `currentMemberId` inte ändrats
- Kan vara kostsamt med många events

**Lösning:** 
```typescript
const filteredEvents = useMemo(() => {
  return events.filter(event => {
    // ... filtering logic
  });
}, [events, showTasksOnly, showAllMembers, currentMemberId]);
```

#### 8. **handleToggleTask - Missing Dependency**
**Fil:** `useCalendarData.ts` (rad 258)

```typescript
const handleToggleTask = useCallback(async (
  eventId: string,
  memberId: string | null,
  date: Date,
  showAll: boolean
) => {
  // ... uses members, loadTasks, loadTasksForAllMembers
}, [members, loadTasks, loadTasksForAllMembers]);
```

**Problem:** 
- `members` är en state-variabel som kan ändras
- Men `handleToggleTask` används i `loadTasksForAllMembers` som inte är i dependencies
- Kan orsaka stale closure problem

**Lösning:** 
- Kontrollera om `members` verkligen behövs i dependencies
- Eller använd en ref för `members` om den inte ska trigga re-renders

### 🟢 Låg Prioritet (Nice to Have)

#### 9. **Type Safety - SetEditingEventCallback**
**Fil:** `useCalendarEvents.ts` (rad 12)

```typescript
type SetEditingEventCallback = (event: any) => void;
```

**Problem:** 
- Använder `any` istället för `CalendarEventResponse | null`

**Lösning:** 
```typescript
type SetEditingEventCallback = (event: CalendarEventResponse | null) => void;
```

#### 10. **JSDoc Comments Saknas**
**Problem:** 
- Inga JSDoc-kommentarer för publika funktioner
- Svårt att förstå vad funktioner gör utan att läsa implementationen

**Lösning:** 
- Lägg till JSDoc-kommentarer för alla publika funktioner och hooks

#### 11. **Error Messages - Hårdkodade Strängar**
**Fil:** `useCalendarEvents.ts`, `useCalendarData.ts`

**Problem:** 
- Hårdkodade svenska strängar i koden
- Svårt att internationalisera senare

**Lösning:** 
- Extrahera till en `messages.ts` eller `i18n`-fil

---

## 📝 Specifika Code Smells

### 1. **Long Parameter Lists**
`useCalendarEvents` tar 9 parametrar - överväg att gruppera i ett objekt:
```typescript
type UseCalendarEventsProps = {
  loadData: LoadDataCallback;
  setError: SetErrorCallback;
  // ... etc
};

export function useCalendarEvents(props: UseCalendarEventsProps) {
  // ...
}
```

### 2. **Prop Drilling**
Många props skickas genom flera lager. Överväg Context API för delad state.

### 3. **Conditional Rendering Complexity**
Många nested conditionals i JSX gör det svårt att läsa. Överväg att extrahera till separata komponenter.

---

## 🎯 Rekommendationer

### Omedelbart (Innan Push):
1. ✅ Fixa duplicerad date formatting logic
2. ✅ Fixa useEffect dependencies för att undvika potentiella buggar
3. ✅ Lägg till error handling i `loadCategories`
4. ✅ Fixa type safety för `SetEditingEventCallback`

### Kort sikt (Nästa iteration):
5. Extrahera inline styles till CSS-klasser
6. Använd `useMemo` för `filteredEvents`
7. Skapa constants för view types

### Lång sikt (Phase 4+):
8. Extrahera filter-komponenter (som planerat)
9. Lägg till JSDoc-kommentarer
10. Överväg Context API för delad state

---

## ✅ Positiva Aspekter

1. **Bra separation:** CalendarView är nu en ren wrapper
2. **Hooks är väl strukturerade:** Tydlig ansvarsfördelning
3. **Performance:** useCallback används konsekvent
4. **Error handling:** Try-catch blocks finns på rätt ställen
5. **Type safety:** TypeScript används konsekvent (utom `any` i ett fall)

---

## 📊 Sammanfattning

**Totalt antal problem:** 11
- 🔴 Hög prioritet: 4
- 🟡 Medel prioritet: 4
- 🟢 Låg prioritet: 3

**Rekommendation:** Fixa de 4 höga prioritetsproblemen innan push. Resten kan göras i efterföljande iterationer.

**Overall Grade:** B+ (Bra implementation med några förbättringsområden)

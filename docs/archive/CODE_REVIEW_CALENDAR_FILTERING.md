# Kodgranskning: Kalendervy Filtrering - Visa Alla Familjemedlemmars Events

**Datum:** 2024  
**Granskare:** Senior Utvecklare  
**Ändringar:** Förbättring av kommentarer för att klargöra att alla familjemedlemmars events visas i kalendervyn

---

## Sammanfattning

Ändringarna består huvudsakligen av förbättrade kommentarer som explicit förklarar att kalendervyn visar **alla familjemedlemmars events**, inte bara events där den inloggade användaren är deltagare. Detta gäller för vanliga events (inte tasks).

**Filer ändrade:**
- `frontend/src/features/dashboard/AdultDashboard.tsx`
- `frontend/src/features/calendar/components/RollingView.tsx`

---

## Detaljerad Granskning

### ✅ Positiva Aspekter

#### 1. **Tydlig Dokumentation**
- **AdultDashboard.tsx (rad 452-453, 459-460, 463):**
  - Tydliga `IMPORTANT`-kommentarer som förklarar beteendet
  - Förklarar skillnaden mellan events och tasks
  - Klargör att filtrering INTE sker på participantIds för vanliga events

- **RollingView.tsx (rad 207-208, 218-219):**
  - Konsistent dokumentation med AdultDashboard
  - Förklarar skillnaden mellan tasks och events i filtreringen

#### 2. **Konsistens mellan Komponenter**
- Båda komponenterna använder samma logik och dokumentation
- Tydlig separation: tasks filtreras på participantIds (när `showAllMembers` är false), events gör det inte

#### 3. **Korrekt Implementering**
- Filtreringen är redan korrekt implementerad
- Ingen faktisk kodändring behövdes, bara förtydligande

---

### ⚠️ Förbättringsområden

#### 1. **Kodduplicering: `getAllDayEventDates`**

**Problem:**
- `getAllDayEventDates` är duplicerad i både `AdultDashboard.tsx` och `RollingView.tsx`
- Samma logik finns på två ställen, vilket ökar underhållsarbete

**Rekommendation:**
```typescript
// Skapa en delad utility-fil:
// frontend/src/features/calendar/utils/dateUtils.ts

export function getAllDayEventDates(event: CalendarEventResponse): string[] {
  if (!event.isAllDay) return [];
  
  const startDateStr = event.startDateTime.substring(0, 10);
  if (!event.endDateTime) {
    return [startDateStr];
  }
  
  const endDateStr = event.endDateTime.substring(0, 10);
  const dates: string[] = [];
  const startDate = new Date(startDateStr + "T00:00:00");
  const endDate = new Date(endDateStr + "T00:00:00");
  
  if (endDate < startDate) {
    return [startDateStr];
  }
  
  const MAX_RECURRING_DAYS = 365;
  let dayCount = 0;
  
  const currentDate = new Date(startDate);
  while (currentDate <= endDate && dayCount < MAX_RECURRING_DAYS) {
    const year = currentDate.getFullYear();
    const month = String(currentDate.getMonth() + 1).padStart(2, "0");
    const day = String(currentDate.getDate()).padStart(2, "0");
    dates.push(`${year}-${month}-${day}`);
    currentDate.setDate(currentDate.getDate() + 1);
    dayCount++;
  }
  
  return dates;
}
```

**Prioritet:** Medium  
**Effort:** Låg (30 min)

---

#### 2. **Potentiell Förvirring: Tasks vs Events**

**Problem:**
- Logiken för tasks och events är olika:
  - **Tasks:** Filtreras på participantIds när `showAllMembers` är false
  - **Events:** Filtreras ALDRIG på participantIds
- Detta kan vara förvirrande för framtida utvecklare

**Rekommendation:**
Överväg att extrahera filtreringslogiken till en dedikerad funktion med tydlig dokumentation:

```typescript
// frontend/src/features/calendar/utils/eventFilters.ts

/**
 * Filters calendar events based on type (task vs event) and member visibility.
 * 
 * @param events - Array of calendar events to filter
 * @param showTasksOnly - If true, show only tasks; if false, show only events
 * @param showAllMembers - If true, show all members' tasks; if false, filter by currentMemberId (only for tasks)
 * @param currentMemberId - Current logged-in member ID (only used for tasks when showAllMembers is false)
 * @returns Filtered array of events
 */
export function filterEventsByType(
  events: CalendarEventResponse[],
  showTasksOnly: boolean,
  showAllMembers: boolean,
  currentMemberId: string | null
): CalendarEventResponse[] {
  return events.filter(event => {
    if (showTasksOnly) {
      if (!event.isTask) return false;
      // For tasks: filter by participantIds if showAllMembers is false
      if (!showAllMembers && currentMemberId) {
        return event.participantIds.includes(currentMemberId);
      }
      return true;
    } else {
      // For regular events: ALWAYS show all events regardless of participantIds
      // This ensures all family members' events are visible in the calendar view
      return !event.isTask;
    }
  });
}
```

**Prioritet:** Low (nice-to-have)  
**Effort:** Medium (1-2 timmar)

---

#### 3. **Type Safety: `filteredEvents` Variabel**

**Problem:**
- `filteredEvents` är av typen `CalendarEventResponse[]` men kan potentiellt innehålla `undefined` om filtreringen misslyckas
- Ingen explicit type assertion eller validering

**Nuvarande kod:**
```typescript
let filteredEvents = calendarEvents.filter(event => {
  return !event.isTask;
});
```

**Rekommendation:**
Koden är faktiskt korrekt som den är - TypeScript garanterar att `filter` returnerar `CalendarEventResponse[]`. Men för extra säkerhet kan vi lägga till en type guard:

```typescript
let filteredEvents: CalendarEventResponse[] = calendarEvents.filter((event): event is CalendarEventResponse => {
  return !event.isTask;
});
```

**Prioritet:** Very Low  
**Effort:** Minimal (5 min)

---

#### 4. **Performance: Flera `.filter()`-anrop**

**Problem:**
- I både `AdultDashboard` och `RollingView` görs två separata `.filter()`-anrop:
  1. Filtrera på task/event typ
  2. Filtrera på datum (today or future)

**Nuvarande kod:**
```typescript
let filteredEvents = calendarEvents.filter(event => {
  return !event.isTask;
});

filteredEvents = filteredEvents.filter(event => {
  const isFutureEvent = event.isAllDay
    ? getAllDayEventDates(event).some(dateStr => dateStr >= todayStr)
    : new Date(event.startDateTime) >= now;
  return isFutureEvent;
});
```

**Rekommendation:**
Kombinera till ett enda `.filter()`-anrop för bättre prestanda (särskilt för stora datasets):

```typescript
const filteredEvents = calendarEvents.filter(event => {
  // Filter by type
  if (event.isTask) return false;
  
  // Filter by date
  const isFutureEvent = event.isAllDay
    ? getAllDayEventDates(event).some(dateStr => dateStr >= todayStr)
    : new Date(event.startDateTime) >= now;
  
  return isFutureEvent;
});
```

**Prioritet:** Low  
**Effort:** Low (15 min)

---

#### 5. **Dokumentation: Saknas i TypeScript Types**

**Problem:**
- Kommentarerna finns bara i komponenterna, inte i type-definitionerna
- Framtida utvecklare som bara tittar på typerna missar viktig information

**Rekommendation:**
Överväg att lägga till JSDoc-kommentarer i type-definitionen:

```typescript
// frontend/src/shared/api/calendar.ts

/**
 * Calendar event response from the API.
 * 
 * IMPORTANT: For regular events (isTask=false), the participantIds array does NOT
 * affect visibility in calendar views. All family events are shown regardless of
 * participantIds. Only tasks (isTask=true) are filtered by participantIds.
 */
export type CalendarEventResponse = {
  // ... existing fields
  participantIds: string[]; // Only used for filtering tasks, not regular events
};
```

**Prioritet:** Low  
**Effort:** Low (10 min)

---

### 🔍 Ytterligare Observationer

#### 1. **Konsistens med CalendarContainer**

**Observation:**
- `CalendarContainer.tsx` har samma filtreringslogik för week/month views (rad 139-152)
- Den filtrerar också korrekt (inte på participantIds för events)
- Men saknar de explicita kommentarerna som lades till i `RollingView` och `AdultDashboard`

**Rekommendation:**
Lägg till samma tydliga kommentarer i `CalendarContainer.tsx` för konsistens:

```typescript
// Filter events for week/month views (rolling view handles its own filtering)
// IMPORTANT: For regular events (not tasks), we show ALL events regardless of participantIds
// This ensures all family members' events are visible in the calendar view
const filteredEvents = useMemo(() => {
  return events.filter(event => {
    if (showTasksOnly) {
      if (!event.isTask) return false;
      // If showTasksOnly is true and showAllMembers is false, filter by current member
      if (!showAllMembers && currentMemberId) {
        return event.participantIds.includes(currentMemberId);
      }
      return true;
    } else {
      // IMPORTANT: For regular events (not tasks), show ALL events regardless of participantIds
      // Do NOT filter by participantIds - show ALL family events
      return !event.isTask; // Show only non-task events
    }
  });
}, [events, showTasksOnly, showAllMembers, currentMemberId]);
```

**Prioritet:** Medium (för konsistens)  
**Effort:** Low (5 min)

---

#### 2. **Testning**

**Observation:**
- Inga tester verkar finnas för filtreringslogiken
- Detta är kritisk business logic som bör testas

**Rekommendation:**
Lägg till unit tests för filtreringslogiken:

```typescript
// frontend/src/features/calendar/utils/__tests__/eventFilters.test.ts

describe('filterEventsByType', () => {
  it('should show all events regardless of participantIds', () => {
    const events = [
      { id: '1', isTask: false, participantIds: ['user1'] },
      { id: '2', isTask: false, participantIds: ['user2'] },
      { id: '3', isTask: false, participantIds: [] },
    ];
    
    const result = filterEventsByType(events, false, false, 'user1');
    
    expect(result).toHaveLength(3); // All events shown
    expect(result.map(e => e.id)).toEqual(['1', '2', '3']);
  });
  
  it('should filter tasks by participantIds when showAllMembers is false', () => {
    const events = [
      { id: '1', isTask: true, participantIds: ['user1'] },
      { id: '2', isTask: true, participantIds: ['user2'] },
    ];
    
    const result = filterEventsByType(events, true, false, 'user1');
    
    expect(result).toHaveLength(1);
    expect(result[0].id).toBe('1');
  });
  
  // ... more tests
});
```

**Prioritet:** High (för framtida säkerhet)  
**Effort:** Medium (2-3 timmar)

---

## Sammanfattning av Rekommendationer

### Kritiska (Måste fixas)
- **Inga kritiska problem hittade** ✅

### Viktiga (Bör fixas)
1. **Lägg till samma kommentarer i CalendarContainer.tsx** (5 min)
2. **Extrahera `getAllDayEventDates` till delad utility** (30 min)
3. **Lägg till unit tests för filtreringslogiken** (2-3 timmar)

### Nice-to-have
1. **Extrahera filtreringslogik till dedikerad funktion** (1-2 timmar)
2. **Kombinera flera `.filter()`-anrop till ett** (15 min)
3. **Lägg till JSDoc i type-definitioner** (10 min)

---

## Slutsats

**Övergripande Betyg: ✅ Godkänd med förbättringsförslag**

Ändringarna är korrekta och väl dokumenterade. Huvudsakliga förbättringsmöjligheterna är:
1. Eliminera kodduplicering (`getAllDayEventDates`)
2. Lägg till samma kommentarer i `CalendarContainer.tsx` för konsistens
3. Lägg till tester för filtreringslogiken

Koden är produktionready, men ovanstående förbättringar skulle göra den mer underhållbar och robust.

---

## Godkännande

- [x] Kodkvalitet: ✅ Godkänd
- [x] Dokumentation: ✅ Godkänd (med förbättringsförslag)
- [x] Konsistens: ✅ Godkänd (med förbättringsförslag)
- [x] Prestanda: ✅ Godkänd (med förbättringsförslag)
- [x] Testning: ⚠️ Saknas (rekommenderas)

**Status:** ✅ **GODKÄND MED FÖRBÄTTRINGSFÖRSLAG**

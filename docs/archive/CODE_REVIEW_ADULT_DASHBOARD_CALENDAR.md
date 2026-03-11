# Kodgranskning: AdultDashboard Kalender-förbättringar

## Översikt
Granskning av ändringar för att visa kategorifärger och alla familjemedlemmars events i kalendervyn.

## Kritiska problem

### 1. ⚠️ `hexToRgba` saknar felhantering
**Plats:** Rad 36-46

**Problem:**
```typescript
function hexToRgba(hex: string, opacity: number): string {
  const cleanHex = hex.replace("#", "");
  const r = parseInt(cleanHex.substring(0, 2), 16);
  const g = parseInt(cleanHex.substring(2, 4), 16);
  const b = parseInt(cleanHex.substring(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${opacity})`;
}
```

**Problem:**
- Ingen validering av hex-format (kan vara 3 eller 6 tecken)
- Ingen hantering av ogiltiga värden (NaN)
- Ingen validering av opacity (0-1)
- Kan krascha vid ogiltiga färger

**Rekommendation:**
```typescript
function hexToRgba(hex: string, opacity: number): string {
  if (!hex || typeof hex !== 'string') {
    return `rgba(184, 230, 184, ${opacity})`; // Fallback till grön
  }
  
  // Validera opacity
  const validOpacity = Math.max(0, Math.min(1, opacity));
  
  // Ta bort # om det finns
  const cleanHex = hex.replace("#", "");
  
  // Hantera både 3 och 6 tecken hex
  let fullHex = cleanHex;
  if (cleanHex.length === 3) {
    fullHex = cleanHex.split('').map(char => char + char).join('');
  }
  
  if (fullHex.length !== 6) {
    return `rgba(184, 230, 184, ${validOpacity})`; // Fallback
  }
  
  const r = parseInt(fullHex.substring(0, 2), 16);
  const g = parseInt(fullHex.substring(2, 4), 16);
  const b = parseInt(fullHex.substring(4, 6), 16);
  
  // Validera att parsing lyckades
  if (isNaN(r) || isNaN(g) || isNaN(b)) {
    return `rgba(184, 230, 184, ${validOpacity})`; // Fallback
  }
  
  return `rgba(${r}, ${g}, ${b}, ${validOpacity})`;
}
```

### 2. ⚠️ Duplicerad logik för all-day event formatering
**Plats:** Rad 1099-1126

**Problem:**
- Samma logik finns redan i `RollingView.tsx` (rad 1111-1139)
- Inline IIFE gör koden svårläsbar
- Svår att underhålla (måste uppdateras på två ställen)

**Rekommendation:**
Skapa en utility-funktion i `dateFormatters.ts`:
```typescript
export function formatAllDayEventRange(
  startDateTime: string,
  endDateTime: string | null
): string {
  const startDate = new Date(startDateTime.substring(0, 10));
  const startDateStr = startDate.toLocaleDateString("sv-SE", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
  
  if (!endDateTime) {
    return `${startDateStr} - Heldag`;
  }
  
  const endDate = new Date(endDateTime.substring(0, 10));
  const endDateStr = endDate.toLocaleDateString("sv-SE", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
  
  // Check if same day
  if (startDateTime.substring(0, 10) === endDateTime.substring(0, 10)) {
    return `${startDateStr} - Heldag`;
  }
  
  // Multi-day: show range
  return `${startDateStr} - ${endDateStr} - Heldag`;
}
```

### 3. ⚠️ Performance: Category lookup i render-loop
**Plats:** Rad 1057-1062

**Problem:**
```typescript
const category = calendarCategories.find((c) => {
  if (!event.categoryId || !c.id) return false;
  return String(c.id) === String(event.categoryId);
});
```

**Problem:**
- Körs för varje event vid varje render
- `String()` konvertering görs flera gånger
- Ingen memoization

**Rekommendation:**
Skapa en Map för O(1) lookup:
```typescript
// I useMemo för eventsByDate eller separat useMemo
const categoryMap = useMemo(() => {
  const map = new Map<string, CalendarEventCategoryResponse>();
  calendarCategories.forEach(cat => {
    if (cat.id) {
      map.set(String(cat.id), cat);
    }
  });
  return map;
}, [calendarCategories]);

// I render:
const category = event.categoryId 
  ? categoryMap.get(String(event.categoryId)) 
  : undefined;
```

### 4. ⚠️ Performance: Participant names lookup
**Plats:** Rad 1154-1157

**Problem:**
```typescript
👥 {event.participantIds
  .map((id) => members.find((m) => m.id === id)?.name)
  .filter(Boolean)
  .join(", ")}
```

**Problem:**
- `members.find()` är O(n) för varje participant
- Körs för varje event vid varje render

**Rekommendation:**
Skapa en members Map:
```typescript
const membersMap = useMemo(() => {
  const map = new Map<string, string>();
  members.forEach(member => {
    if (member.id && member.name) {
      map.set(member.id, member.name);
    }
  });
  return map;
}, [members]);

// I render:
const participantNames = event.participantIds
  .map(id => membersMap.get(id))
  .filter(Boolean)
  .join(", ");
```

### 5. ⚠️ Magic numbers
**Plats:** Rad 1070

**Problem:**
```typescript
hexToRgba(category.color, 0.2)
```

**Rekommendation:**
Definiera konstanter:
```typescript
const CATEGORY_BACKGROUND_OPACITY = 0.2;
const FALLBACK_BACKGROUND_COLOR = "rgba(240, 240, 240, 0.5)";
const FALLBACK_BORDER_COLOR = "#b8e6b8";
```

### 6. ⚠️ Inline styles - mycket repetitivt
**Plats:** Rad 1087-1158

**Problem:**
- Mycket repetitiv inline styling
- Svår att underhålla
- Ökar bundle size

**Rekommendation:**
Extrahera till styled components eller CSS classes:
```typescript
const eventCardStyle = {
  padding: "12px",
  borderRadius: "8px",
  cursor: "pointer",
  transition: "all 0.2s ease",
};

const eventTitleStyle = {
  fontWeight: 600,
  color: "#2d3748",
  marginBottom: "4px",
};

const eventDetailStyle = {
  fontSize: "0.85rem",
  color: "#6b6b6b",
};
```

### 7. ⚠️ Komplex conditional marginBottom
**Plats:** Rad 1097, 1135, 1144

**Problem:**
```typescript
marginBottom: event.description || event.location || event.participantIds.length > 0 ? "4px" : "0",
```

**Problem:**
- Svårläsbar
- Upprepas flera gånger
- Lätt att göra fel

**Rekommendation:**
Skapa en helper:
```typescript
const getMarginBottom = (hasNext: boolean) => hasNext ? "4px" : "0";

// Använd:
marginBottom: getMarginBottom(!!event.description || !!event.location || event.participantIds.length > 0)
```

### 8. ⚠️ Saknar error boundary för category lookup
**Plats:** Rad 1057-1062

**Problem:**
- Om `calendarCategories` är undefined eller null kan det krascha
- Ingen fallback om API-anrop misslyckas

**Rekommendation:**
Lägg till defensive checks:
```typescript
const category = (calendarCategories || []).find((c) => {
  if (!event.categoryId || !c?.id) return false;
  return String(c.id) === String(event.categoryId);
});
```

## Mindre problem

### 9. ⚠️ Saknar loading state för categories
**Problem:**
- Om categories laddas långsammare än events kan färger saknas initialt
- Ingen visuell feedback

**Rekommendation:**
Visa loading state eller skeleton medan categories laddas.

### 10. ⚠️ Saknar cleanup i useEffect
**Plats:** Rad 279-310

**Problem:**
- Om komponenten unmountas under API-anrop kan state uppdateras
- Risk för memory leaks

**Rekommendation:**
```typescript
useEffect(() => {
  if (activeTab === "calendar") {
    let cancelled = false;
    
    const loadCalendar = async () => {
      try {
        setLoadingCalendar(true);
        setCalendarError(null);
        // ... date setup ...
        
        const [eventsData, categoriesData] = await Promise.all([
          fetchCalendarEvents(startDate, endDate),
          fetchCalendarCategories()
        ]);
        
        if (!cancelled) {
          setCalendarEvents(eventsData);
          setCalendarCategories(categoriesData);
          setCalendarEndDate(endDate);
        }
      } catch (e) {
        if (!cancelled) {
          console.error("Error loading calendar:", e);
          setCalendarError("Kunde inte ladda kalender. Försök igen.");
        }
      } finally {
        if (!cancelled) {
          setLoadingCalendar(false);
        }
      }
    };
    
    void loadCalendar();
    
    return () => {
      cancelled = true;
    };
  }
}, [activeTab]);
```

## Positiva aspekter

✅ Bra användning av `Promise.all` för parallell datahämtning  
✅ Tydlig kommentar om att kalendervyn visar alla familjemedlemmars events  
✅ Konsekvent användning av TypeScript-typer  
✅ Bra separation av concerns (categories hämtas separat)  

## Prioritering

**Hög prioritet:**
1. Felhantering i `hexToRgba` (#1)
2. Performance-optimering med Maps (#3, #4)
3. Cleanup i useEffect (#10)

**Medel prioritet:**
4. Extrahera duplicerad logik (#2)
5. Magic numbers (#5)
6. Defensive checks (#8)

**Låg prioritet:**
7. Inline styles refactoring (#6)
8. Helper functions för marginBottom (#7)
9. Loading state för categories (#9)

## Rekommenderad åtgärdsplan

1. **Omedelbart:** Fixa `hexToRgba` felhantering
2. **Kort sikt:** Implementera Maps för performance
3. **Medel sikt:** Extrahera duplicerad logik till utilities
4. **Lång sikt:** Refaktorera styling och lägg till error boundaries

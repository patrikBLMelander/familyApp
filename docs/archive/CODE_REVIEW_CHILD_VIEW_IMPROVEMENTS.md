# Kodgranskning: Child View Improvements
**Datum:** 2024  
**Granskare:** Senior Developer  
**Omfattning:** Food collection system, progress bar, pet mood, feeding mechanism

---

## 📋 Sammanfattning

**Övergripande bedömning:** ✅ **Godkänd med förbättringsförslag**

Implementationen är väl genomförd med tydlig separation av concerns och bra UX. Det finns några områden som kan förbättras, särskilt kring state management, error handling och edge cases.

---

## ✅ Styrkor

### 1. **Arkitektur & Struktur**
- ✅ Tydlig separation mellan frontend och backend
- ✅ Bra komponentstruktur (HalfCircleProgress, ConfettiAnimation, etc.)
- ✅ Utility-funktioner är väl organiserade (petFoodUtils.ts)
- ✅ TypeScript används konsekvent med tydliga typer

### 2. **Backend Implementation**
- ✅ Tydlig separation: XP award flyttad från task completion till feeding
- ✅ Validering i PetService.feedPet() (positive XP, child-only)
- ✅ Automatisk growth stage update efter feeding
- ✅ Bra kommentarer som förklarar designbeslut

### 3. **Frontend UX**
- ✅ Bra visuell feedback (confetti, floating XP, animations)
- ✅ Responsive design (mindre cirkel på mobil)
- ✅ Optimistic updates för bättre UX
- ✅ Tydlig visuell hierarki

---

## ⚠️ Kritiska Problem

### 1. **Food Collection State Persistence** 🔴
**Problem:** Food collection state försvinner vid page reload eller refresh.

**Nuvarande implementation:**
```typescript
// Food räknas om från completed tasks vid load
const completedTasks = sortedTasks.filter(t => t.completed && ...);
const foodFromTasks: FoodItem[] = [];
// ... skapar food items
setCollectedFood(foodFromTasks);
```

**Problem:**
- Om användaren samlar mat men inte matar, och sedan laddar om sidan, försvinner maten
- Detta kan vara förvirrande för användaren
- Food collection är inte persistent mellan sessions

**Lösning:**
- Spara collected food i localStorage/sessionStorage
- Eller: Spara "unfed food" i backend (ny tabell/kolumn)
- Eller: Behåll nuvarande approach men dokumentera begränsningen tydligt

**Prioritet:** Medium (UX-problem, inte funktionalitet)

---

### 2. **Task Uncompletion & Food Removal** 🟡
**Problem:** När en task uncompletas, tas food bort från slutet av arrayen, vilket kan vara fel om användaren har matat delvis.

**Nuvarande implementation:**
```typescript
else if (wasCompleted && xpPoints > 0) {
  // Remove food items (remove last xpPoints items)
  setCollectedFood(prev => prev.slice(0, prev.length - xpPoints));
}
```

**Problem:**
- Om användaren har samlat 10 mat, matat 5, och sedan uncompletar en task som gav 2 mat, tas fel mat bort
- Bör ta bort mat som är kopplad till specifik task, inte bara "sista X items"

**Lösning:**
- Spara taskId i FoodItem och ta bort baserat på taskId
- Eller: Behåll nuvarande approach men dokumentera begränsningen

**Prioritet:** Low (edge case, sällan användare uncompletar tasks efter att ha matat)

---

### 3. **XP Reversal vid Task Uncompletion** 🟡
**Problem:** Om en task uncompletas efter att mat har matats, kan XP inte enkelt reverseras.

**Backend kommentar:**
```java
// NOTE: XP is no longer removed here since XP is only awarded when feeding.
// If a task is uncompleted, the food should be removed from the frontend's food collection,
// but XP that was already awarded from feeding cannot be easily reversed.
```

**Problem:**
- Om användaren matar med mat från task A, och sedan uncompletar task A, försvinner maten men XP finns kvar
- Detta kan leda till "gratis XP" om användaren utnyttjar detta

**Lösning:**
- Implementera XP reversal i backend (XpService.removeXp())
- Eller: Förhindra uncompletion av tasks som redan har matats
- Eller: Behåll nuvarande approach men dokumentera begränsningen

**Prioritet:** Medium (kan utnyttjas, men kräver medveten handling)

---

### 4. **Window Width State & SSR** 🟡
**Problem:** `windowWidth` state kan orsaka hydration mismatch i SSR-miljöer.

**Nuvarande implementation:**
```typescript
const [windowWidth, setWindowWidth] = useState<number>(
  typeof window !== "undefined" ? window.innerWidth : 1024
);
```

**Problem:**
- Om appen körs i SSR (t.ex. Next.js i framtiden), kan detta orsaka hydration mismatch
- Initial render använder 1024, men client-side kan vara annat värde

**Lösning:**
- Använd CSS media queries istället för JavaScript
- Eller: Använd `useEffect` för att sätta initial värde endast på client
- Nuvarande lösning fungerar men är inte optimal för SSR

**Prioritet:** Low (app verkar inte använda SSR just nu)

---

## 🔧 Förbättringsförslag

### 1. **Error Handling**

**Nuvarande:**
```typescript
} catch (e) {
  console.error("Error feeding pet:", e);
}
```

**Förbättring:**
- Visa användarvänligt felmeddelande till användaren
- Logga mer detaljerad information för debugging
- Hantera specifika fel (nätverksfel, valideringsfel, etc.)

**Exempel:**
```typescript
} catch (e) {
  console.error("Error feeding pet:", e);
  if (e instanceof Error) {
    setError(e.message);
  } else {
    setError("Kunde inte mata djuret. Försök igen.");
  }
}
```

---

### 2. **Food Collection Logic - Task ID Tracking**

**Förbättring:**
Spara taskId i FoodItem för bättre tracking:

```typescript
type FoodItem = {
  id: string;
  xp: number;
  collectedAt: number;
  taskId?: string; // Lägg till taskId
};
```

Detta gör det möjligt att:
- Ta bort rätt mat när task uncompletas
- Visa vilken task som gav vilken mat
- Bättre debugging

---

### 3. **Performance Optimizations**

**a) Food Bowl Rendering:**
```typescript
// Nuvarande: Renderar upp till 20 items
Array.from({ length: Math.min(totalFoodCount, 20) }).map((_, i) => (
```

**Förbättring:**
- Använd virtualisering för stora listor
- Eller: Visa "20+" istället för att rendera alla
- Eller: Använd CSS för att visa många items effektivt

**b) Window Resize Handler:**
```typescript
// Nuvarande: Uppdaterar state vid varje resize
window.addEventListener("resize", handleResize);
```

**Förbättring:**
- Debounce resize events
- Eller: Använd CSS media queries istället

---

### 4. **Type Safety**

**Förbättring:**
Lägg till mer specifika typer:

```typescript
// Nuvarande
const foodEmoji = pet ? getPetFoodEmoji(pet.petType) : "🍎";

// Förbättring
type PetMood = "happy" | "hungry";
type PetType = "dragon" | "cat" | "dog" | ...;
```

---

### 5. **Code Duplication**

**Problem:** Pet mood logic upprepas på flera ställen:

```typescript
// I load()
if (todayCompletedTasks.length === 0) {
  setPetMood("hungry");
  setPetMessage(getRandomPetMessage("hungry"));
} else {
  setPetMood("happy");
  setPetMessage(getRandomPetMessage("happy"));
}

// I handleToggleTask()
if (todayCompletedTasks.length === 0) {
  setPetMood("hungry");
  setPetMessage(getRandomPetMessage("hungry"));
} else {
  setPetMood("happy");
  setPetMessage(getRandomPetMessage("happy"));
}
```

**Förbättring:**
Extrahera till helper function:

```typescript
const updatePetMood = (completedTasks: CalendarTaskWithCompletionResponse[]) => {
  const hasCompletedTasks = completedTasks.length > 0;
  const mood: PetMood = hasCompletedTasks ? "happy" : "hungry";
  setPetMood(mood);
  setPetMessage(getRandomPetMessage(mood));
};
```

---

### 6. **Backend Validation**

**Förbättring:**
Lägg till mer validering i PetController:

```java
@PostMapping("/feed")
public void feedPet(
    @RequestBody FeedPetRequest request,
    @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
) {
    // Lägg till max limit för XP amount
    if (request.xpAmount() > 1000) {
        throw new IllegalArgumentException("XP amount too large");
    }
    // ... resten
}
```

---

### 7. **Accessibility**

**Förbättring:**
- Lägg till ARIA labels för progress bar
- Lägg till keyboard navigation för feed button
- Lägg till screen reader text för animations

---

## 🧪 Testning

### Saknade Tester

1. **Unit Tests:**
   - `getPetFoodEmoji()` - testa alla pet types
   - `getRandomPetMessage()` - testa att den returnerar valid message
   - Food collection logic

2. **Integration Tests:**
   - Task completion → food collection
   - Feeding → XP award
   - Level up detection

3. **E2E Tests:**
   - Full flow: Task completion → Food collection → Feeding → Level up

---

## 📝 Dokumentation

### Bra:
- ✅ Tydliga kommentarer i backend
- ✅ Bra komponentnamn
- ✅ Tydliga funktionsnamn

### Förbättring:
- Lägg till JSDoc kommentarer för komplexa funktioner
- Dokumentera edge cases (t.ex. food persistence)
- Lägg till README för nya komponenter

---

## 🔒 Säkerhet

### Bra:
- ✅ Validering i backend (child-only, positive XP)
- ✅ Device token authentication

### Förbättring:
- Rate limiting på feed endpoint (förhindra spam)
- Max XP per feed (förhindra abuse)
- Validera att XP amount är rimlig

---

## 📊 Performance

### Bra:
- ✅ Parallel data loading (Promise.all)
- ✅ Optimistic updates

### Förbättring:
- Debounce window resize
- Memoize expensive calculations
- Lazy load animations

---

## 🎯 Rekommendationer

### Prioritet 1 (Hög):
1. ✅ **Fix food persistence** - Spara i localStorage eller backend
2. ✅ **Förbättra error handling** - Visa användarvänliga felmeddelanden
3. ✅ **Task ID tracking** - Spara taskId i FoodItem

### Prioritet 2 (Medium):
4. ✅ **XP reversal** - Implementera eller dokumentera begränsningen
5. ✅ **Code deduplication** - Extrahera pet mood logic
6. ✅ **Backend validation** - Lägg till max limits

### Prioritet 3 (Låg):
7. ✅ **Performance optimizations** - Debounce, memoization
8. ✅ **Accessibility** - ARIA labels, keyboard navigation
9. ✅ **Testing** - Lägg till unit/integration tests

---

## ✅ Slutsats

Implementationen är **solid och väl genomförd**. De flesta problem är edge cases eller förbättringsmöjligheter snarare än kritiska buggar. 

**Huvudsakliga styrkor:**
- Tydlig arkitektur
- Bra UX
- Tydlig separation av concerns

**Huvudsakliga förbättringsområden:**
- Food persistence
- Error handling
- Code deduplication

**Rekommendation:** Godkänd för production med förbättringsförslag som kan implementeras iterativt.

---

## 📌 Ytterligare Noteringar

1. **Gradient ID collision:** `gradientId` använder `currentLevel` för unikhet, men om samma level visas flera gånger kan det orsaka collision. Överväg att använda UUID eller timestamp.

2. **Animation performance:** Confetti animation skapar 50 DOM elements. Överväg att använda Canvas för bättre performance på äldre enheter.

3. **Food bowl limit:** Visar max 20 items. Överväg att visa "20+ 🐟" istället för att rendera alla.

4. **Pet mood daily reset:** Mood resetas baserat på `lastFedDateRef.current !== today`, men detta kan vara problematiskt om användaren är i olika tidszoner. Överväg att använda UTC datum.

---

**Granskad av:** Senior Developer  
**Status:** ✅ Godkänd med förbättringsförslag

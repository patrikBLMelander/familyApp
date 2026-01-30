# Code Review: Pet Mood Baserad på Matning

## Översikt
Ändringar för att göra pet mood beroende av faktisk matning istället för klara sysslor.

## Identifierade Problem

### 🔴 KRITISKA PROBLEM

#### 1. **localStorage är per enhet, inte per användare**
**Problem:**
```typescript
localStorage.setItem("lastFedDate", today);
```
- Om flera barn använder samma enhet delar de samma `lastFedDate`
- Om ett barn matar djuret på en enhet och loggar in på en annan, är mood fel
- Data är inte synkroniserad mellan enheter

**Lösning:**
- Använd backend-data istället (se punkt 2)
- Backend har redan `fedAt` i `CollectedFoodEntity` som spårar när mat matades

#### 2. **Backend har redan denna data - vi använder den inte**
**Problem:**
- Backend spårar redan `fedAt` i `CollectedFoodEntity` när mat matas
- Vi gör en extra API-call till `getCollectedFood()` men använder inte `fedAt`-data
- Vi duplicerar data i localStorage istället för att använda backend

**Lösning:**
- Lägg till `fedAt` i `FoodItemResponse` och `CollectedFoodResponse`
- Kontrollera om någon mat har `fedAt` idag (lokalt datum)
- Ta bort localStorage-baserad lösning

#### 3. **Timezone-problem**
**Problem:**
```typescript
const today = new Date().toISOString().split('T')[0];
```
- `toISOString()` ger UTC-datum, inte lokalt datum
- Om användaren är i t.ex. PST (UTC-8) och matar kl 23:00 lokal tid, blir det nästa dag i UTC
- Detta kan ge fel mood om dagen byter i UTC men inte lokalt

**Lösning:**
```typescript
const today = new Date().toLocaleDateString('sv-SE'); // YYYY-MM-DD format
// eller
const today = new Date().toISOString().split('T')[0]; // Men använd lokalt datum istället
```

### 🟡 MEDELSTORA PROBLEM

#### 4. **Ingen error handling för localStorage**
**Problem:**
```typescript
localStorage.getItem("lastFedDate")
localStorage.setItem("lastFedDate", today)
```
- Om localStorage är disabled (t.ex. i private browsing) kraschar koden
- Om localStorage är full kan `setItem` faila

**Lösning:**
```typescript
try {
  const stored = localStorage.getItem("lastFedDate");
  // ...
} catch (e) {
  // Fallback till backend eller default
}
```

#### 5. **localStorage key collision risk**
**Problem:**
- `"lastFedDate"` är en generisk nyckel
- Kan kollidera med andra delar av appen
- Borde vara namespaced, t.ex. `"pet_lastFedDate_${memberId}"`

**Lösning:**
- Använd memberId i nyckeln om vi behåller localStorage
- Bättre: använd bara backend

#### 6. **Race condition risk**
**Problem:**
- Om användaren matar djuret två gånger snabbt kan båda calls uppdatera `lastFedDateRef`
- Ingen lock eller debouncing

**Lösning:**
- `isFeeding` state skyddar redan mot detta, men borde verifiera

### 🟢 MINDRE PROBLEM / FÖRBÄTTRINGAR

#### 7. **Kodduplicering**
**Problem:**
```typescript
const today = new Date().toISOString().split('T')[0];
```
- Förekommer på flera ställen
- Borde vara en utility-funktion

**Lösning:**
```typescript
function getTodayDateString(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
```

#### 8. **Kommentarer kan förbättras**
**Problem:**
- Vissa kommentarer är bra, men vissa logiksteg saknar förklaring

**Förslag:**
- Lägg till kommentar om varför vi använder localStorage (tills vi byter till backend)
- Förklara timezone-hantering

#### 9. **Ingen validering av datumformat**
**Problem:**
- Om localStorage innehåller ogiltigt datumformat kan jämförelsen faila tyst

**Lösning:**
```typescript
function isValidDateString(dateStr: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(dateStr);
}
```

## Rekommenderade Ändringar

### Prioritet 1: Använd Backend Data

**Nuvarande situation:**
- Backend returnerar bara `unfedFood` (inte all food)
- `FoodItemResponse` inkluderar inte `fedAt`
- Vi kan inte se när mat senast matades via API:et

**Lösningsalternativ:**

**Alternativ A: Lägg till endpoint för senaste matning**
```java
@GetMapping("/pets/last-fed-date")
public String getLastFedDate(@RequestHeader("X-Device-Token") String deviceToken) {
    // Returnera datum när pet senast matades (eller null)
    // Kolla senaste fedAt från collected_food tabellen
}
```

**Alternativ B: Inkludera fedAt i FoodItemResponse och returnera all food**
- Ändra `FoodItemResponse` att inkludera `fedAt` (nullable)
- Ändra `getCollectedFood()` att returnera all food, inte bara unfed
- Frontend kan då kolla om någon mat har `fedAt` idag

**Alternativ C: Lägg till separat fält i API**
```java
public record CollectedFoodResponse(
    List<FoodItemResponse> foodItems,
    int totalCount,
    OffsetDateTime lastFedAt  // När pet senast matades
) {}
```

**Rekommendation:** Alternativ A eller C (enklast att implementera)

### Prioritet 2: Fixa Timezone
1. Använd lokalt datum istället för UTC
2. Skapa utility-funktion för datumhantering

### Prioritet 3: Error Handling
1. Lägg till try-catch för localStorage
2. Validera datumformat
3. Fallback till backend om localStorage failar

## Exempel på Förbättrad Implementation

```typescript
// Utility function
function getTodayLocalDateString(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

// Check if pet was fed today using backend data
function wasPetFedToday(foodItems: FoodItemResponse[]): boolean {
  const today = getTodayLocalDateString();
  return foodItems.some(item => {
    if (!item.fedAt) return false;
    const fedDate = new Date(item.fedAt).toLocaleDateString('sv-SE');
    return fedDate === today;
  });
}

// In load function
const today = getTodayLocalDateString();
const fedToday = wasPetFedToday(foodData.foodItems);

if (fedToday) {
  setPetMood("happy");
  setPetMessage(getRandomPetMessage("happy"));
} else {
  setPetMood("hungry");
  setPetMessage(getRandomPetMessage("hungry"));
}
```

## Positiva Aspekter

✅ **Bra logikändring**: Pet mood baseras nu på faktisk matning, inte sysslor
✅ **Bra kommentarer**: Koden är förklarad
✅ **Konsekvent**: Samma logik används på flera ställen
✅ **Inga breaking changes**: Ändringen är bakåtkompatibel

## Testning

Följande scenarion bör testas:
1. ✅ Matar djuret → blir glad
2. ✅ Klarar syssla → förblir hungrigt (om inte matat)
3. ⚠️ Loggar in på ny enhet → mood ska vara korrekt (kräver backend-fix)
4. ⚠️ Matar kl 23:00 lokal tid → datum ska vara korrekt (kräver timezone-fix)
5. ⚠️ Flera barn på samma enhet → ska ha olika mood (kräver backend-fix)

## Slutsats

**Status: ⚠️ FUNGERAR MEN BEHÖVER FÖRBÄTTRAS**

Koden fungerar för grundläggande användning, men har flera problem som bör fixas:
- **Kritisk**: Använd backend-data istället för localStorage
- **Kritisk**: Fixa timezone-hantering
- **Viktig**: Lägg till error handling

Rekommendation: Implementera backend-baserad lösning innan release.

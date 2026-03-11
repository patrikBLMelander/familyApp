# Todo: Vuxendashboard Omdesign

## Översikt
Skapa ny vuxendashboard med tab-navigation (Kalender, Att Göra, Listor). Befintliga vyer behålls och nås via hamburgermenyn.

---

## Fas 1: Grundläggande Dashboard-struktur

### 1.1 Skapa ny AdultDashboard komponent
- [ ] Skapa `frontend/src/features/dashboard/AdultDashboard.tsx`
- [ ] Definiera props: `onNavigate`, `familyId?`
- [ ] Definiera tab-typer: `"calendar" | "todos" | "lists"`
- [ ] State för aktiv tab (default: "calendar")
- [ ] Grundläggande layout med header och tab-navigation

### 1.2 Tab-navigation UI
- [ ] Skapa tab-buttons: "Kalender", "Att Göra", "Listor"
- [ ] Visuell indikator för aktiv tab (underline eller highlight)
- [ ] Smooth transition vid tab-växling
- [ ] Touch-friendly design (stora touch-targets)
- [ ] Responsiv design (mobil/desktop)

### 1.3 Uppdatera routing
- [ ] Uppdatera `App.tsx` routing för vuxna (PARENT role)
- [ ] Ersätt `<Dashboard>` med `<AdultDashboard>` för vuxna
- [ ] Behåll alla befintliga vyer i routing (CalendarView, TodoListsView, etc.)
- [ ] Testa att navigation fungerar

---

## Fas 2: Kalender-tab (Förenklad rullande vy)

### 2.1 Förenklad RollingView komponent
- [ ] Skapa `SimplifiedRollingView.tsx` eller modifiera befintlig RollingView
- [ ] Endast visning: Events och tasks per dag
- [ ] Scrollbar för att navigera framåt i tiden
- [ ] "Ladda fler"-funktion när man når slutet
- [ ] Klicka på dag för att se detaljer (read-only)

### 2.2 Filter och funktioner
- [ ] Filter-toggle: "Visa endast Dagens Att Göra"
- [ ] Filter-dropdown: Välj familjemedlem (eller "Alla")
- [ ] Quick-add för snabba tasks (enklare dialog)
- [ ] Länk/knapp: "Öppna fullständig kalender" → navigerar till CalendarView

### 2.3 Data-hantering
- [ ] Använd `useCalendarData` hook för att hämta events/tasks
- [ ] Filtrera för rullande vy (från idag och framåt)
- [ ] Loading states
- [ ] Error handling

---

## Fas 3: Att Göra-tab

### 3.1 Task-lista
- [ ] Hämta dagens tasks för inloggad vuxen
- [ ] Använd `fetchTasksForToday` API
- [ ] Sortering: required tasks först, sedan alfabetiskt
- [ ] Lista med checkboxar för varje task

### 3.2 Task-visualisering
- [ ] Checkbox + task-titel
- [ ] XP-poäng (om task har XP)
- [ ] Färgkodning:
  - Orange: Extra task (ej klar)
  - Grå: Obligatorisk task (ej klar)
  - Grön: Klar task
- [ ] Touch-friendly checkboxar

### 3.3 Task-hantering
- [ ] Toggle task completion (`toggleTaskCompletion`)
- [ ] Snabb-add knapp: "+ Add" för att skapa ny task
- [ ] Snabb-add öppnar enkel dialog för task-skapande
- [ ] Placeholder om inga tasks: "Inga dagens att göra. Skapa ditt första task!"

### 3.4 Header
- [ ] "Dagens Att Göra" titel
- [ ] Datum-visning (om annan dag vald)
- [ ] "+ Add"-knapp

---

## Fas 4: Listor-tab

### 4.1 Översikt över listor
- [ ] Hämta alla todo-listor
- [ ] Använd befintlig API för todo-listor
- [ ] Visa kompakt överblick

### 4.2 List-visualisering
- [ ] Card-baserad layout
- [ ] För varje lista visa:
  - Listnamn
  - Ikon/emoji (om listan har en)
  - "X oklara items" (eller "Alla klara ✓")
- [ ] Klicka på lista för att öppna fullständig TodoListsView

### 4.3 Header och funktioner
- [ ] "Listor" titel
- [ ] "+ Skapa ny lista"-knapp
- [ ] Placeholder om inga listor: "Skapa din första lista!"
- [ ] Navigation till fullständig TodoListsView vid klick

---

## Fas 5: Djur-integration (Valfritt - om djur ska aktiveras för vuxna)

### 5.1 Backend-ändringar
- [ ] Uppdatera `XpService.awardXp()` för att tillåta PARENT role
- [ ] Uppdatera `PetService` för att tillåta pets för PARENT role
- [ ] Testa XP-systemet för vuxna
- [ ] Testa pet-skapande för vuxna

### 5.2 Frontend: Aktivering av djur
- [ ] Lägg till checkbox i `FamilyMembersView`: "Aktivera djur för denna vuxen"
- [ ] Spara inställning i backend (nytt fält eller logik)
- [ ] Visa djur-område på dashboard om aktiverat

### 5.3 Frontend: Djur-visualisering
- [ ] Om djur aktiverat: Visa pet-visualisering (samma som barnen)
- [ ] Pet mood message (happy/hungry)
- [ ] XP progress ring (halvcirkel)
- [ ] Mat-samling och matning
- [ ] Level och XP-visning
- [ ] Confetti vid level-up

### 5.4 Placering av djur
- [ ] Besluta var djur ska visas (ovanför tabs eller i första tabben)
- [ ] Implementera vald placering

---

## Fas 6: Polish och förbättringar

### 6.1 Animerade transitions
- [ ] Smooth tab-transitions
- [ ] Loading animations
- [ ] Hover-effects på tabs

### 6.2 Loading states
- [ ] Loading indicators för varje tab
- [ ] Skeleton screens eller spinners

### 6.3 Error handling
- [ ] Error messages om API-anrop misslyckas
- [ ] Retry-funktionalitet
- [ ] User-friendly felmeddelanden

### 6.4 Accessibility
- [ ] ARIA labels för tabs
- [ ] Keyboard navigation (Tab, Enter, Arrow keys)
- [ ] Screen reader support
- [ ] Focus management

### 6.5 Responsiv design
- [ ] Mobil: Tabs stackade eller horisontella
- [ ] Desktop: Optimal layout
- [ ] Testa på olika skärmstorlekar

---

## Fas 7: Testing

### 7.1 Funktionalitetstester
- [ ] Testa tab-navigation
- [ ] Testa Kalender-tab (visning, scroll, quick-add)
- [ ] Testa Att Göra-tab (toggle, add, sortering)
- [ ] Testa Listor-tab (översikt, navigation)
- [ ] Testa navigation till fullständiga vyer via länkar

### 7.2 Integrationstester
- [ ] Testa att alla gamla vyer fortfarande fungerar via menyn
- [ ] Testa att routing fungerar korrekt
- [ ] Testa att data laddas korrekt i alla tabs
- [ ] Testa att filter fungerar i Kalender-tab

### 7.3 Användartester
- [ ] Testa på mobil
- [ ] Testa på desktop
- [ ] Testa med olika datamängder (många/få tasks, events, listor)

---

## Noteringar

### Befintliga vyer som BEHÅLLS (nås via meny):
- ✅ `CalendarView` - Fullständig kalendervy (månad, vecka, rullande, event-skapande)
- ✅ `TodoListsView` - Fullständig todo-listor vy
- ✅ `FamilyMembersView` - Familjemedlemmar
- ✅ `ChildrenXpView` - Mina Barns Djur
- ✅ `MenstrualCycleView` - Menscykel
- ✅ Alla andra befintliga vyer

### Endast Dashboard ändras:
- 🔄 `Dashboard.tsx` → `AdultDashboard.tsx` (ny komponent)
- Gamla `Dashboard.tsx` kan tas bort efter implementation

### API:er att använda:
- `fetchTasksForToday(memberId)` - För Att Göra-tab
- `useCalendarData` hook - För Kalender-tab
- Befintliga todo-list API:er - För Listor-tab
- `fetchCurrentPet()`, `feedPet()`, etc. - För djur (om implementerat)

---

## Prioritering

**Hög prioritet (Måste ha):**
1. Fas 1: Grundläggande Dashboard-struktur
2. Fas 2: Kalender-tab
3. Fas 3: Att Göra-tab
4. Fas 4: Listor-tab
5. Fas 7: Testing

**Medel prioritet (Bör ha):**
6. Fas 6: Polish och förbättringar

**Låg prioritet (Nice to have):**
7. Fas 5: Djur-integration (kan göras senare separat)

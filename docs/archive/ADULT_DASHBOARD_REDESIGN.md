# Designförslag: Vuxendashboard Omdesign

## Översikt

Den nya vuxendashboarden ska ta inspiration från barnens dashboard men anpassas för vuxnas behov. Fokus ligger på snabb åtkomst till de viktigaste funktionerna: kalender, "att göra" och listor, medan resten flyttas till hamburgarmenyn.

## Huvudprinciper

1. **Stilren och snabb** - Enkel navigation till det viktigaste
2. **Djur som centralt element** - Om vuxen har aktiverat djur (valfritt, default avstängt)
3. **Tab-baserad navigation** - Lätt växling mellan Kalender, Att Göra och Listor
4. **Hamburgermeny för resten** - Alla andra funktioner under menyn

---

## Layout-struktur

### 1. Header (samma som nu)
- Hamburger-ikon (vänster)
- Familjenamn och tagline (mitten)
- Samma som nuvarande implementation

### 2. Huvudområde: Tab-navigation

#### Tab 1: "Kalender" (Default)
- **Förenklad rullande kalendervy** - Endast visning, ingen fullständig kalenderfunktionalitet
- Visar dagens datum och framåt (rullande vy)
- **Begränsad funktionalitet:**
  - Scrollbar för att navigera framåt i tiden
  - Events och tasks per dag (endast visning)
  - Klicka på dag för att se detaljer
  - Quick-add för snabb task-skapande (enklare version)
  - Filter: "Visa endast Dagens Att Göra"
  - Filter: Filtrera på familjemedlem
- **Saknas på dashboard:**
  - ❌ Månadsvy
  - ❌ Veckovy
  - ❌ Skapa events (endast tasks via quick-add)
  - ❌ Redigera events
  - ❌ Kategorihantering
- **För fullständig kalender:** Gå via hamburgermenyn → "Kalender" (fullständig CalendarView med alla funktioner)

#### Tab 2: "Att Göra"
- Visar dagens tasks för inloggad vuxen
- Samma design som barnens "Att Göra"-sektion
- Lista med checkboxar
- Sortering: required tasks först, sedan alfabetiskt
- Snabb-add knapp för att lägga till nya tasks
- Om inga tasks: "Inga dagens att göra. Skapa ditt första task!"

#### Tab 3: "Listor"
- Snabb överblick över alla todo-listor
- Kortfattad lista med:
  - Listnamn
  - Antal oklara items
  - Klicka för att öppna fullständig lista
- "Skapa ny lista"-knapp

### 3. Djur-sektion (Om aktiverat)

**Placering:** Ovanför tab-navigationen, eller som en del av första tabben

**Om vuxen har djur aktiverat:**
- Visar samma pet-visualisering som barnen har
- Pet mood message (happy/hungry)
- XP progress ring (halvcirkel)
- Mat-samling och matning (samma som barnen)
- Level och XP-visning

**Om vuxen INTE har djur:**
- Visa inget djur-område alls (eller en diskret "Aktivera djur"-länk)

**Aktivering av djur:**
- Görs i familjemedlemsvyn (redigera vuxen)
- Checkbox: "Aktivera djur för denna vuxen"
- Om aktiverat: vuxen får samma XP-system som barn/assistants

---

## Detaljerad Design

### Tab-navigation UI

```
┌─────────────────────────────────────┐
│  [Kalender] [Att Göra] [Listor]     │  ← Tab-buttons
├─────────────────────────────────────┤
│                                     │
│  [Innehåll för vald tab]           │
│                                     │
└─────────────────────────────────────┘
```

**Tab-stil:**
- Underline-indikator för aktiv tab
- Smooth transition vid tab-växling
- Touch-friendly (stor touch-target)
- Visuell feedback vid hover/active

### Kalender-tab

**Innehåll:**
- **Förenklad version** av `RollingView`-komponenten
- Header med:
  - "Kalender" titel
  - Länk/knapp: "Öppna fullständig kalender" (tar dig till CalendarView via meny)
  - Filter-toggle: "Visa endast Dagens Att Göra"
  - Filter-dropdown: Välj familjemedlem (eller "Alla")
- Scrollbar för att navigera framåt i tiden
- "Ladda fler"-funktion när man når slutet
- **Endast visning** - Klicka på event/task för att se detaljer, men ingen redigering
- **Quick-add för tasks** - Enkel dialog för att snabbt lägga till tasks

**Skillnad från fullständig kalendervy:**
- Dashboard: Förenklad rullande vy, endast visning + quick-add tasks
- Fullständig kalender (via meny): Alla vyer (rullande, vecka, månad), skapa/redigera events, kategorihantering

**Fördelar:**
- Snabb överblick över kommande dagar direkt från dashboard
- Ingen extra navigation för att se vad som händer
- Lätt att skapa snabba tasks
- För avancerad kalenderhantering: gå via meny

### Att Göra-tab

**Innehåll:**
- Header:
  - "Dagens Att Göra" (eller datum om annan dag vald)
  - "+ Add"-knapp för snabb task-skapande
- Task-lista:
  - Checkbox + task-titel
  - XP-poäng (om task har XP)
  - Färgkodning: Orange (extra), Grå (obligatorisk), Grön (klar)
- Om inga tasks: Placeholder med "Skapa ditt första task!"

**Design:**
- Samma stil som barnens task-lista
- Kompakt men läsbar
- Touch-friendly checkboxar

### Listor-tab

**Innehåll:**
- Header:
  - "Listor"
  - "+ Skapa ny lista"-knapp
- Lista över alla todo-listor:
  - Listnamn
  - Ikon/emoji (om listan har en)
  - "X oklara items" (eller "Alla klara ✓")
  - Klicka för att öppna fullständig lista
- Om inga listor: "Skapa din första lista!"

**Design:**
- Card-baserad layout
- Kompakt överblick
- Snabb navigation till fullständig lista

---

## Djur-integration för vuxna

### Tekniska krav

**Backend:**
- Vuxna (PARENT role) kan ha djur om de vill
- XP-systemet ska fungera för vuxna också (nu är det begränsat till CHILD/ASSISTANT)
- PetService ska tillåta pets för PARENT role

**Frontend:**
- Checkbox i FamilyMembersView för att aktivera djur för vuxen
- Dashboard visar djur om aktiverat
- Samma pet-visualisering och matning som barnen

### UX för djur

**Om djur är aktiverat:**
- Djur visas prominent på dashboarden
- Samma funktionalitet som barnen:
  - Mata djur med insamlad mat
  - XP-progress
  - Level-uppnåelse
  - Pet mood messages

**Om djur INTE är aktiverat:**
- Inget djur visas
- Dashboard fokuserar på tabs (Kalender, Att Göra, Listor)

---

## Navigation och Meny

### Hamburgermeny (befintlig)

**I menyn (fullständig funktionalitet):**
- **Kalender** - Fullständig CalendarView med:
  - Rullande vy, Veckovy, Månadsvy
  - Skapa och redigera events
  - Kategorihantering
  - Alla filter och funktioner
- Familjemedlemmar (redan där)
- Menscykel (redan där)
- Mina Barns Djur (redan där)
- Eventuellt: Inställningar (framtida)

**På dashboard (förenklad/snabb åtkomst):**
- Kalender (tab) - Förenklad rullande vy, endast visning + quick-add tasks
- Att Göra (tab) - Dagens tasks
- Listor (tab) - Snabb överblick
- Djur (om aktiverat)

**Navigation-flöde:**
- Dashboard → Snabb överblick och enkla åtgärder
- Meny → Fullständig funktionalitet och avancerade funktioner

---

## Responsiv Design

### Mobil (< 768px)
- Tabs: Full bredd, stackade om nödvändigt
- Kalender: Scrollbar, kompakt visning
- Att Göra: Enkel lista, stora touch-targets
- Listor: Card-layout, en kolumn

### Desktop (> 768px)
- Tabs: Horisontell layout
- Kalender: Mer utrymme, bättre översikt
- Att Göra: Två kolumner om många tasks
- Listor: Grid-layout för listor

---

## Implementation-steg (för framtida implementering)

### Fas 1: Tab-struktur
1. Skapa ny `AdultDashboard.tsx` komponent
2. Implementera tab-navigation
3. Skapa förenklad RollingView för Kalender-tab (endast visning + quick-add)
4. Implementera Att Göra-tab
5. Implementera Listor-tab
6. Lägg till länk/knapp "Öppna fullständig kalender" som navigerar till CalendarView

### Fas 2: Djur för vuxna
1. Backend: Tillåt XP för PARENT role
2. Backend: Tillåt pets för PARENT role
3. Frontend: Checkbox i FamilyMembersView
4. Frontend: Visa djur på dashboard om aktiverat
5. Testa matning och XP för vuxna

### Fas 3: Navigation
1. Uppdatera hamburgermeny
2. Ta bort gamla Dashboard.tsx
3. Uppdatera routing i App.tsx
4. Testa all navigation

### Fas 4: Polish
1. Animerade tab-transitions
2. Loading states
3. Error handling
4. Accessibility (ARIA labels, keyboard navigation)

---

## Design-inspiration från Barnens Dashboard

### Vad vi tar med:
- ✅ Pet-visualisering (om aktiverat)
- ✅ Mat-samling och matning
- ✅ XP progress ring
- ✅ Task-lista design
- ✅ Pet mood messages
- ✅ Confetti vid level-up

### Vad vi anpassar:
- 🔄 Tab-navigation istället för enkel vy
- 🔄 Förenklad kalender direkt i dashboard (fullständig via meny)
- 🔄 Listor som egen tab
- 🔄 Mer kompakt layout (vuxna behöver mer information)
- 🔄 Tydlig separation: Dashboard = snabb överblick, Meny = fullständig funktionalitet

---

## Öppna frågor

1. **Djur för vuxna:**
   - Ska vuxna ha samma XP-system som barnen?
   - Ska vuxna kunna ge mat till sig själva eller bara till barnen?
   - Ska vuxna ha samma level-system (1-5)?

2. **Tab-ordning:**
   - Ska Kalender vara default, eller Att Göra?
   - Ska användaren kunna ändra default tab?

3. **Kalender i dashboard:** ✅ **Besvarat**
   - Förenklad rullande vy med endast visning
   - Quick-add för tasks (enklare version)
   - Fullständig kalendervy finns via hamburgermenyn

4. **Listor-tab:**
   - Ska det vara översikt eller fullständig lista?
   - Ska man kunna redigera listor direkt från dashboard?

---

## Mockup-beskrivning

### Desktop-layout:
```
┌─────────────────────────────────────────────────────┐
│ ☰  Familjenamn                    [Hamburger meny]   │
├─────────────────────────────────────────────────────┤
│                                                     │
│  [Djur-visualisering - om aktiverat]              │
│                                                     │
├─────────────────────────────────────────────────────┤
│  [Kalender] [Att Göra] [Listor]                    │
├─────────────────────────────────────────────────────┤
│                                                     │
│  [Innehåll för vald tab]                          │
│                                                     │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### Mobil-layout:
```
┌─────────────────────┐
│ ☰  Familjenamn      │
├─────────────────────┤
│                     │
│ [Djur - om aktiverat│
│                     │
├─────────────────────┤
│ [Kalender]          │
│ [Att Göra]          │
│ [Listor]            │
├─────────────────────┤
│                     │
│ [Tab-innehåll]      │
│                     │
└─────────────────────┘
```

---

## Sammanfattning

Den nya vuxendashboarden blir:
- **Snabbare** - Direkt åtkomst till kalenderöversikt, todos och listor
- **Stilrenare** - Tabs istället för card-grid
- **Mer funktionell** - Förenklad kalender direkt i dashboard, fullständig via meny
- **Flexibel** - Djur om man vill, annars fokus på produktivitet
- **Konsistent** - Samma design-språk som barnens dashboard
- **Tydlig separation** - Dashboard för snabb överblick, Meny för fullständig funktionalitet

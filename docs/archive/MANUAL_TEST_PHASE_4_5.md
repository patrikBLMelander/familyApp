# Manual Test Guide - Calendar Refactoring Phase 4-5

## 🎯 Test Scope
Testa alla ändringar från Phase 4-5, inklusive:
- UI-komponenter (CalendarHeader, CalendarViewSelector, CalendarFilters)
- Error handling-förbättringar
- Accessibility-attribut
- Button styling (konsistens)
- Alla funktioner fungerar som innan

---

## ✅ Quick Test (5 minuter)
Om du bara har tid för snabbtest, fokusera på:
1. ✅ Öppna kalendern → kontrollera att header ser ut som innan
2. ✅ Växla mellan vyer (Rullande/Vecka/Månad) → kontrollera att det fungerar
3. ✅ Växla mellan Schema/Dagens Att Göra → kontrollera att filters fungerar
4. ✅ Skapa ett nytt event → kontrollera att det fungerar
5. ✅ Klicka på back-knapp → kontrollera att formulär stängs

---

## 📋 Fullständig Testlista

### 1. UI-komponenter - Header

#### 1.1 CalendarHeader visuellt
- [ ] Header ser ut exakt som innan
- [ ] Back-knapp (←) visas korrekt
- [ ] Titel "Kalender" visas korrekt
- [ ] "Kategorier"-knapp visas för PARENT (döljs för CHILD/ASSISTANT)
- [ ] "+ Nytt event"-knapp visas korrekt
- [ ] Knappar döljs när formulär är öppet

#### 1.2 CalendarHeader funktionalitet
- [ ] Back-knapp stänger formulär om öppet
- [ ] Back-knapp går till dashboard om inget formulär är öppet
- [ ] "Kategorier"-knapp öppnar CategoryManager (PARENT only)
- [ ] "+ Nytt event"-knapp öppnar EventForm

---

### 2. UI-komponenter - View Selector

#### 2.1 CalendarViewSelector visuellt
- [ ] Tre knappar visas korrekt (Rullande, Vecka, Månad)
- [ ] Aktiv vy är markerad med grön bakgrund (#b8e6b8)
- [ ] Inaktiv vy har transparent bakgrund
- [ ] Textfärg ändras korrekt (aktiv: #2d5a2d, inaktiv: #6b6b6b)
- [ ] Font weight ändras korrekt (aktiv: 600, inaktiv: 400)
- [ ] Styling ser identisk ut som innan

#### 2.2 CalendarViewSelector funktionalitet
- [ ] Klicka på "Rullande" → växlar till rolling view
- [ ] Klicka på "Vecka" → växlar till week view
- [ ] Klicka på "Månad" → växlar till month view
- [ ] Aktiv knapp uppdateras korrekt när man växlar
- [ ] Data laddas korrekt för varje vy

#### 2.3 CalendarViewSelector accessibility
- [ ] Screen reader kan läsa "Visa rullande kalendervy" (testa med VoiceOver/TalkBack)
- [ ] Screen reader kan läsa "Visa veckokalendervy"
- [ ] Screen reader kan läsa "Visa månadskalendervy"
- [ ] `aria-pressed` är korrekt (true för aktiv, false för inaktiv)

---

### 3. UI-komponenter - Filters

#### 3.1 CalendarFilters visuellt
- [ ] Två knappar visas korrekt (Schema, Dagens Att Göra)
- [ ] "Schema" är aktiv som standard
- [ ] Aktiv knapp har grön bakgrund
- [ ] Inaktiv knapp har transparent bakgrund
- [ ] Styling ser identisk ut som innan

#### 3.2 CalendarFilters funktionalitet - Schema/Tasks
- [ ] Klicka på "Schema" → visar events (inte tasks)
- [ ] Klicka på "Dagens Att Göra" → visar tasks
- [ ] Aktiv knapp uppdateras korrekt
- [ ] Data filtreras korrekt

#### 3.3 CalendarFilters funktionalitet - Member Filter
- [ ] När "Dagens Att Göra" är aktiv → "Endast mig"/"Alla familjemedlemmar" visas
- [ ] När "Schema" är aktiv → member filter döljs
- [ ] "Endast mig" visar bara mina tasks
- [ ] "Alla familjemedlemmar" visar alla tasks
- [ ] Aktiv knapp uppdateras korrekt

#### 3.4 CalendarFilters accessibility
- [ ] Screen reader kan läsa "Visa schema"
- [ ] Screen reader kan läsa "Visa dagens att göra"
- [ ] Screen reader kan läsa "Visa endast mina uppgifter"
- [ ] Screen reader kan läsa "Visa alla familjemedlemmars uppgifter"
- [ ] `aria-pressed` är korrekt för alla knappar

---

### 4. Error Handling

#### 4.1 Error messages
- [ ] Skapa event med felaktig data → får tydligt felmeddelande
- [ ] Uppdatera event med fel → får tydligt felmeddelande
- [ ] Ta bort event med fel → får tydligt felmeddelande
- [ ] Quick add med fel → får tydligt felmeddelande
- [ ] Ladda kalenderdata med fel → får tydligt felmeddelande
- [ ] Ladda kategorier med fel → får tydligt felmeddelande

#### 4.2 Error message extraction
- [ ] Om API returnerar specifikt felmeddelande → visas det meddelandet
- [ ] Om API returnerar generiskt fel → visas standardmeddelande
- [ ] Error messages är på svenska och begripliga

#### 4.3 Error recovery
- [ ] Efter fel → kan man försöka igen
- [ ] Error state rensas när operation lyckas
- [ ] UI återgår till normal state efter fel

---

### 5. Alla vyer fungerar

#### 5.1 Rolling View
- [ ] Laddas korrekt
- [ ] Visar events/tasks korrekt
- [ ] Task completion fungerar
- [ ] Quick add fungerar
- [ ] Navigation fungerar
- [ ] Filtering fungerar

#### 5.2 Week View
- [ ] Laddas korrekt
- [ ] Visar events/tasks korrekt
- [ ] Klicka på dag/tid → öppnar EventForm
- [ ] Klicka på event → öppnar EventForm för redigering
- [ ] Ta bort event fungerar
- [ ] Filtering fungerar

#### 5.3 Month View
- [ ] Laddas korrekt
- [ ] Visar events/tasks korrekt
- [ ] Klicka på dag → öppnar EventForm
- [ ] Klicka på event → öppnar EventForm för redigering
- [ ] Ta bort event fungerar
- [ ] Navigation fungerar

---

### 6. CRUD Operations

#### 6.1 Create Event
- [ ] "+ Nytt event" öppnar formulär
- [ ] Fyll i formulär → spara → event skapas
- [ ] Event visas i kalendern
- [ ] Felhantering fungerar

#### 6.2 Update Event
- [ ] Klicka på event → öppnar formulär
- [ ] Ändra data → spara → event uppdateras
- [ ] Ändringar visas i kalendern
- [ ] Felhantering fungerar

#### 6.3 Delete Event
- [ ] Klicka på event → öppnar formulär
- [ ] Klicka på "Ta bort" → bekräfta → event tas bort
- [ ] Event försvinner från kalendern
- [ ] Felhantering fungerar

#### 6.4 Quick Add
- [ ] Quick add fungerar i rolling view
- [ ] Skapar task korrekt
- [ ] Task visas i listan
- [ ] Felhantering fungerar (inkl. reload-fel)

---

### 7. Category Management

#### 7.1 Category CRUD
- [ ] Skapa kategori → visas i listan
- [ ] Uppdatera kategori → ändringar visas
- [ ] Ta bort kategori → försvinner från listan
- [ ] Felhantering fungerar

#### 7.2 Category Usage
- [ ] Kategorier visas i EventForm
- [ ] Kan välja kategori när man skapar event
- [ ] Kategorier visas korrekt i kalendern

---

### 8. Task Completion

#### 8.1 Toggle Task
- [ ] Klicka på task → togglar completion
- [ ] UI uppdateras omedelbart (optimistic update)
- [ ] Task completion sparas korrekt
- [ ] Fungerar för "Endast mig"
- [ ] Fungerar för "Alla familjemedlemmar"

#### 8.2 Task Reload
- [ ] Efter toggle → data reloadas korrekt
- [ ] Om reload misslyckas → error visas
- [ ] Task state är korrekt efter reload

---

### 9. Navigation & State Management

#### 9.1 View Switching
- [ ] Växla mellan vyer → data laddas korrekt
- [ ] State bevaras korrekt (filters, selected date, etc.)
- [ ] Inga onödiga reloads

#### 9.2 Filter State
- [ ] Filter state bevaras när man växlar vy
- [ ] Filter state bevaras när man öppnar/stänger formulär
- [ ] Filter state återställs korrekt

#### 9.3 Date Navigation
- [ ] Ändra datum i rolling view → data laddas
- [ ] Ändra vecka i week view → data laddas
- [ ] Ändra månad i month view → data laddas

---

### 10. Edge Cases

#### 10.1 Empty States
- [ ] Inga events → visar tom kalender korrekt
- [ ] Inga tasks → visar tom lista korrekt
- [ ] Inga kategorier → visar tom lista korrekt

#### 10.2 Loading States
- [ ] Loading indicator visas när data laddas
- [ ] Loading indicator försvinner när data är laddad
- [ ] Inga flickering eller glitchy states

#### 10.3 Error States
- [ ] Error meddelande visas korrekt
- [ ] Error meddelande kan stängas/döljas
- [ ] UI är användbar även med error state

#### 10.4 Role-based Access
- [ ] PARENT ser "Kategorier"-knapp
- [ ] CHILD/ASSISTANT ser inte "Kategorier"-knapp
- [ ] Alla roller kan använda view selector och filters

---

### 11. Performance

#### 11.1 Loading Performance
- [ ] Data laddas snabbt
- [ ] Inga onödiga API calls
- [ ] Smooth transitions mellan vyer

#### 11.2 UI Performance
- [ ] Inga lag när man klickar på knappar
- [ ] Smooth animations/transitions
- [ ] Inga flickering

---

### 12. Regression Testing

#### 12.1 Tidigare funktioner
- [ ] Alla tidigare funktioner fungerar fortfarande
- [ ] Inga nya buggar introducerade
- [ ] Inga visuella förändringar (utom eventuella förbättringar)

#### 12.2 Browser Console
- [ ] Inga errors i konsolen
- [ ] Inga warnings i konsolen
- [ ] Inga TypeScript errors

---

## 🐛 Vad ska du leta efter?

### ✅ Fungerar allt som innan?
- Alla funktioner ska fungera exakt som innan
- Inga nya buggar
- Inga visuella förändringar (utom eventuella förbättringar)

### ✅ Ser UI ut exakt likadant?
- Alla knappar ser ut som innan
- Spacing och layout är samma
- Färger och styling är identiska
- Responsiv design fungerar (mobil/desktop)

### ✅ Inga nya fel?
- Inga errors i konsolen
- Inga TypeScript errors
- Inga linter errors
- Inga runtime errors

### ✅ Accessibility fungerar?
- Screen readers kan läsa alla knappar
- `aria-pressed` är korrekt
- Alla knappar har `aria-label`

---

## 📝 Om något inte fungerar

### Notera:
1. **Exakt vad som inte fungerar** - beskriv problemet
2. **När det händer** - vilken vy, vilken åtgärd
3. **Konsol-fel** - kopiera eventuella errors från konsolen
4. **Steg för att reproducera** - hur man återskapar problemet

### Testa i olika vyer:
- Rolling view
- Week view
- Month view

### Testa med olika roller:
- PARENT
- CHILD
- ASSISTANT

---

## ✅ Checklista innan push

- [ ] Alla UI-komponenter fungerar
- [ ] Alla vyer fungerar
- [ ] Alla CRUD-operationer fungerar
- [ ] Error handling fungerar
- [ ] Accessibility fungerar
- [ ] Inga errors i konsolen
- [ ] Inga visuella förändringar (utom förbättringar)
- [ ] Alla tidigare funktioner fungerar fortfarande

---

**När du är klar med testningen, säg till så kan vi pusha koden!**

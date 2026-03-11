# Diskussion: Merga Kalender och Dagens Sysslor

## Nuvarande Situation

### Daily Tasks (Dagens Sysslor)
- **Struktur:**
  - `daily_task`: id, name, description, daysOfWeek (monday-sunday booleans), position, isRequired, xpPoints, family_id
  - `daily_task_member`: many-to-many (task_id, member_id) - om tom = alla medlemmar
  - `daily_task_completion`: id, task_id, member_id, completed_date, completed_at
  
- **Begränsningar:**
  - Använder daysOfWeek (monday-sunday) - svårt att ha olika personer med samma syssla på olika dagar
  - En task kan bara ha en uppsättning daysOfWeek
  - One-time tasks måste skapas individuellt för varje dag
  - Ingen direkt koppling till kalendern

### Calendar Events
- **Struktur:**
  - `calendar_event`: id, family_id, category_id, title, description, start_datetime, end_datetime, is_all_day, location, created_by_id, recurring_type, recurring_interval, recurring_end_date, recurring_end_count
  - `calendar_event_participant`: many-to-many (event_id, member_id)
  - `calendar_event_category`: id, family_id, name, color
  
- **Stärkor:**
  - Stödjer återkommande events (DAILY, WEEKLY, MONTHLY, YEARLY)
  - Stödjer specifika datum (one-time events)
  - Stödjer flera deltagare per event
  - Stödjer flera dagar (start_datetime till end_datetime)
  - All-day events
  - Kategorier med färger

## Önskad Slutprodukt

1. **ToDo Listor → "Listor"** (enkelt namnbyte)
2. **Merga Kalender och Dagens Sysslor:**
   - Föräldrars vy: Utgår från kalendervyn
   - Barnens vy: Samma som nu (förenklad vy med pet)
   - När man skapar kalenderhändelse kan man markera den som "Dagens Att Göra" med XP
3. **"Dagens Att Göra" kan vara:**
   - Återkommande (recurring) - ex. "Tvätta tandborste" varje dag
   - One-time - ex. "Städa rummet" en specifik dag
4. **Olika personer kan ha samma syssla men på olika dagar**
5. **Lätt att skapa flera one-time sysslor på en dag**
6. **Veckovy för varje familjemedlems "Dagens att Göra"**
7. **I veckovy:**
   - Klicka på dag för att skapa syssla
   - Dra sysslor över flera dagar (drag & drop)

## Förslag på Databas-Struktur

### Alternativ 1: Utöka calendar_event (Rekommenderat)

**Fördelar:**
- Återanvänder befintlig kalenderinfrastruktur
- Stödjer redan recurring, participants, dates
- Mindre kodförändringar
- Unified data model

**Ändringar:**
```sql
-- Utöka calendar_event tabell
ALTER TABLE calendar_event 
  ADD COLUMN is_task BOOLEAN NOT NULL DEFAULT FALSE,  -- TRUE = "Dagens Att Göra", FALSE = vanlig event
  ADD COLUMN xp_points INTEGER,  -- NULL eller 0 om is_task = FALSE, annars XP-poäng
  ADD COLUMN is_required BOOLEAN NOT NULL DEFAULT TRUE;  -- TRUE = obligatorisk, FALSE = extra

-- Behåll calendar_event_participant för att definiera vilka som ska göra tasken
-- (eller ska varje participant ha sin egen instans?)

-- Task completion (ny tabell)
CREATE TABLE calendar_event_task_completion (
    id              VARCHAR(36) PRIMARY KEY,
    event_id        VARCHAR(36) NOT NULL,
    member_id       VARCHAR(36) NOT NULL,  -- Vem som markerade klart (för historik/spårning)
    occurrence_date DATE NOT NULL,  -- För recurring events: vilken occurrence (datum)
    completed_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (event_id) REFERENCES calendar_event(id) ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE,
    UNIQUE KEY unique_event_member_date (event_id, member_id, occurrence_date)
);

-- Notera: För events med flera participants och is_task=TRUE:
-- Completion är gemensam: När NÅGON participant markerar klart, är tasken klar för ALLA participants
-- I databasen spåras completion med (event_id, member_id, occurrence_date) för att veta vem som markerade
-- Backend-logik: Check completion = finns det MINST EN completion för (event_id, occurrence_date) bland alla participants?
```

**Problem att lösa:**
- Recurring events: Hur hanterar vi completion? 
  - Förslag: `occurrence_date` i completion-tabellen
  - För one-time events: `occurrence_date = DATE(start_datetime)`
  - För recurring: räkna ut occurrence_date baserat på recurring pattern

- Participants vs individuella tasks:
  - **Beslut:** Om två personer delar samma event (participants) och det är en task, blir den klarmarkerad om EN person gör den (gemensam completion)
  - För "samma" syssla på olika personer med olika dagar: Skapa två separata events (Lösning A)
  - För vanliga events: Behåll participant-systemet som nu

**Participants för tasks:**
- Om event har flera participants och is_task=TRUE: Completion är gemensam
  - När EN person markerar klart, är det klart för alla participants
  - I databasen spåras completion fortfarande med (event_id, member_id, occurrence_date) för att veta vem som markerade
  - Men i UI/backend-logiken: Om event har participants, räcker det att NÅGON participant har markerat klart för att tasken ska visas som klar för alla
- Om man vill ha olika personer med samma syssla på olika dagar: Skapa separata events (en per person)

### Alternativ 2: Nya tabeller (mer komplext)

Skapa nya tabeller för tasks och behålla calendar_events separat.
- Mer kod
- Dubblering av funktionalitet
- Inte rekommenderat

## UI/UX Design

### Föräldrars Vy

**Kalendervyn (befintlig) med utökningar:**

1. **Vykonvertering:**
   - Toggle mellan "Månadsvy", "Veckovy", "Dagvy"
   - Veckovy visar alla familjemedlemmar i kolumner
   - **Toggle/filter:** "Visa endast Dagens Att Göra" (filtrera bort vanliga events)
   - **Filter:** Filtrera på person (dropdown eller checkbox-lista)

2. **Skapa Event/Task:**

   a. **Fullständig dialog/formulär:**
   - Dialog/formulär för att skapa event (befintlig design)
   - Checkbox: "Dagens Att Göra" (is_task)
   - Om is_task är kryssad:
     - Visa XP-fält (xp_points)
     - Visa is_required checkbox (obligatorisk/extra)
     - Visa participants (vem ska göra tasken)
   - Recurring/One-time: Välj i samma dialog som nu
   - Date picker: Välj start/slut datum
   
   b. **Snabb addering (NY):**
   - Enkel input-fält när man kollar på dagens sysslor
   - "Add"/"Lägg till" knapp
   - Skriva task-namn (ex. "Städa")
   - Klicka "Lägg till" → Skapar automatiskt:
     - is_task = TRUE
     - is_required = TRUE (default)
     - xp_points = 10 (default)
     - one-time event för idag
     - participant = inloggad användare (eller välj snabbt)
   - Tillåter snabbt att lägga till många one-time tasks på en dag

3. **Veckovy:**
   - Kolumner: Måndag-Söndag
   - Rader per familjemedlem (eller vice versa)
   - Klicka på dag → Öppna dialog för att skapa task/event (fullständig dialog)
   - Drag & drop: 
     - Dra task från en dag till annan → Flytta start_datetime (och hela serien om recurring)
     - **Drag över flera dagar:** Om man drar en task så den täcker flera dagar (ex. Måndag-Torsdag), skapas en recurring task som upprepas DAILY för de dagarna
       - Ex: "Städa" drags från Måndag till Torsdag → recurring_type='DAILY', start_datetime=Måndag, recurring_end_date=Torsdag
       - Task ska göras varje dag inom perioden (om man gör den på Måndag, måste man också göra den på Tisdag, etc.)
   - Färgkodning: 
     - Events: Enligt kategori-färg
     - Tasks: Orange (extra) / Grå (obligatorisk) när ej klar
     - Completed tasks: Grön (samma för alla)

4. **Task Completion (för föräldrar):**
   - Se completion-status per dag/medlem
   - Klicka för att markera/avmarkera completion
   - Veckovy visar completion-status visuellt
   - Om event har flera participants: När EN person markerar klart, markeras det klart för alla participants

### Barnens Vy

**Förenklad vy (befintlig ChildDashboard):**
- Behåll nuvarande design
- Visar endast sina egna tasks för idag
- Kan markera completion direkt från dashboard
- Pet/XP-visualisering som nu

## Data Migration

**Från daily_task → calendar_event:**

**Krav:**
- Inga events delas mellan personer (varje event har endast en participant)
- Om samma task ska gälla flera personer: Skapa separata events (en per person)

**Migreringsstrategi:**

1. **För varje kombination av (daily_task, member_id):**
   - Om tasken gäller alla (ingen koppling i daily_task_member): Skapa event per medlem separat
   - Om tasken gäller specifik medlem: Skapa event för den medlemmen
   
2. **Hantera daysOfWeek (monday, tuesday, etc.):**

   **Problem:** daily_task har daysOfWeek (ex. Tisdag, Torsdag), men calendar_event använder start_datetime + recurring_type.
   
   **Lösning: Skapa separata events för varje aktiva veckodag:**
   
   - För varje aktiva veckodag (monday=true, tuesday=true, etc.):
     - Skapa ett calendar_event med:
       - `is_task = TRUE`
       - `title = daily_task.name`
       - `description = daily_task.description`
       - `xp_points = daily_task.xp_points`
       - `is_required = daily_task.is_required`
       - `recurring_type = 'WEEKLY'`
       - `recurring_interval = 1`
       - `start_datetime = nästa förekommande av den veckodagen` (ex. nästa Tisdag kl 00:00)
       - `is_all_day = TRUE`
     - Lägg till en participant (member_id)
     - **Varje event = en veckodag för en person**
   
   **Exempel:**
   ```
   daily_task: "Städa rummet", monday=false, tuesday=true, wednesday=false, thursday=true
   member_id: "child1"
   
   → Skapa 2 events:
     Event1: "Städa rummet" för child1, recurring_type='WEEKLY', start_datetime=nästa Tisdag
     Event2: "Städa rummet" för child1, recurring_type='WEEKLY', start_datetime=nästa Torsdag
   ```
   
   **Fördelar:**
   - Enkel och tydlig logik
   - WEEKLY recurring fungerar direkt (varje vecka på samma veckodag)
   - Lätt att förstå och underhålla
   
   **Nackdelar:**
   - Fler events i databasen (en per veckodag per person)
   - Men: Detta är en engångsmigration, och framtida events skapas direkt i kalendern

3. **Migrera completion:**
   - För varje daily_task_completion:
     - Hitta motsvarande calendar_event (baserat på task name, member_id, och veckodag)
     - Skapa calendar_event_task_completion med:
       - `event_id = kalender_event_id`
       - `member_id = daily_task_completion.member_id`
       - `occurrence_date = daily_task_completion.completed_date`

**Migreringsscript struktur:**
```sql
-- Pseudokod för migration:
FOR EACH daily_task:
  FOR EACH member_id (använd alla medlemmar om daily_task_member är tom):
    FOR EACH active_day (monday, tuesday, etc. där daily_task.day = true):
      CREATE calendar_event (
        is_task=TRUE,
        title=daily_task.name,
        recurring_type='WEEKLY',
        start_datetime=next occurrence of day,
        ...
      )
      CREATE calendar_event_participant (event_id, member_id)
      
      -- Migrera completions för denna kombination
      FOR EACH completion WHERE task_id=daily_task.id AND member_id=member_id:
        CREATE calendar_event_task_completion (
          event_id=new_event_id,
          member_id=completion.member_id,
          occurrence_date=completion.completed_date (måste matcha veckodag)
        )
```

## Tekniska Utmaningar

### 1. Recurring Events + Completion

**Problem:** Hur spårar vi completion för recurring events?

**Lösning:**
- `calendar_event_task_completion.occurrence_date` = vilken occurrence (datum)
- För one-time events: occurrence_date = DATE(start_datetime)
- För recurring events: occurrence_date = räknat datum baserat på pattern
- Backend-service: `getOccurrences(event, startDate, endDate)` → lista av dates
- Completion-check: Finns det en completion för (event_id, member_id, occurrence_date)?

**Exempel:**
```
Event: "Tvätta tandborste", recurring_type='DAILY', start_datetime='2024-01-01', participants=[child1, child2]
Occurrences: 2024-01-01, 2024-01-02, 2024-01-03, ...
Completion: (event_id, member_id='child1', occurrence_date='2024-01-01')
Completion: (event_id, member_id='child1', occurrence_date='2024-01-02')
Completion: (event_id, member_id='child2', occurrence_date='2024-01-01')
```

**Completion för events med participants:**
- Completion spåras i databasen per person (member_id) och occurrence_date
- **Men för events med flera participants och is_task=TRUE:** Completion är gemensam
  - När NÅGON participant markerar klart för en occurrence_date, är tasken klar för ALLA participants
  - Backend-logik: Check completion = finns det MINST EN completion för (event_id, occurrence_date) bland alla participants?
  - I UI: Visas som klart för alla participants när någon har markerat klart

### 2. Olika Personer, Samma Task, Olika Dagar

**Problem:** "Städa rummet" för barn1 på Måndag, barn2 på Torsdag

**Beslut: Lösning A (Enklare)**
- Skapa 2 separata events:
  - Event1: "Städa rummet" för barn1, start_datetime=2024-01-08 (Måndag)
  - Event2: "Städa rummet" för barn2, start_datetime=2024-01-11 (Torsdag)
- Om recurring: Ett event per person med olika recurring patterns
- **Tydligt och enkelt:** Varje event har sina egna deltagare och datum

### 3. Drag & Drop

**Frontend-utmaning:**
- Bibliotek: react-dnd, @dnd-kit, eller custom
- Dra task → Ändra start_datetime (flytta task till annan dag)
- **Drag över flera dagar:**
  - Om man drar en task så den täcker flera dagar (ex. Måndag-Torsdag)
  - Skapa recurring_type='DAILY' med start_datetime=Måndag, recurring_end_date=Torsdag
  - Task ska göras varje dag inom perioden (varje dag är en separat occurrence)
  - Om man gör tasken på Måndag, måste man också göra den på Tisdag, etc.
- För befintliga recurring events: Flytta start_datetime (hela serien flyttas)

### 4. Veckovy Performance

**Problem:** Många events/tasks för många medlemmar = mycket data

**Lösning:**
- Lazy loading: Ladda bara synlig vecka
- Backend: Endpoint som returnerar events för date range + members
- Cache: Client-side caching av loaded weeks

## Implementation Plan (Högnivå)

### Fas 1: Databas & Backend
1. Utöka calendar_event med is_task, xp_points, is_required
2. Skapa calendar_event_task_completion tabell
3. **Migrera data från daily_task → calendar_event:**
   - För varje kombination av (task, member, veckodag): Skapa separat event med WEEKLY recurring
   - Migrera completions med rätt event_id + occurrence_date matchning
   - Säkerställ att inga events delas mellan personer
4. Uppdatera CalendarEvent domain model
5. Uppdatera CalendarService för task-hantering
6. Skapa endpoints för task completion

### Fas 2: Frontend - Föräldrars Vy
1. Lägg till veckovy i CalendarView
2. Utöka event creation dialog med task-fields (is_task, xp_points, is_required)
3. Implementera "snabb addering" för one-time tasks (enkel input + knapp)
4. Toggle/filter: "Visa endast Dagens Att Göra"
5. Filter: Filtrera på person
6. Visa tasks med färgkodning (orange/grå när ej klar, grön när klar)
7. Task completion UI
8. **Fas 2.5 (Senare):** Implementera drag & drop (flytta tasks, stretch över flera dagar)

### Fas 3: Frontend - Barnens Vy
1. Uppdatera ChildDashboard att använda calendar_event API
2. Filtrera för current user + today
3. Behåll nuvarande UX

### Fas 4: Cleanup
1. Ta bort daily_task tabeller (eller deprecate)
2. Ta bort DailyTasksView för föräldrar
3. Uppdatera navigation
4. Uppdatera dokumentation

## Beslut och Svar

1. **Participants:** ✅ **Besvarat**
   - Om två personer delar samma event (participants) och is_task=TRUE: Completion är gemensam (när EN person markerar klart, är det klart för alla participants)
   - I databasen spåras completion fortfarande med (event_id, member_id, occurrence_date), men logiken behandlar det som gemensamt för alla participants
   - För "samma" syssla på olika personer med olika dagar: Skapa två separata events (Lösning A)

2. **Recurring Tasks:** ✅ **Besvarat**
   - Om en task är recurring och en person har missat en dag: Bara markeras som missed (ingen makeup task skapas)
   - Completion är optional per occurrence

3. **Backward Compatibility:** ✅ **Besvarat**
   - Migrera direkt från daily_task → calendar_event
   - **Viktigt:** Inga events delas mellan personer (varje event har endast en participant)
   - Om samma task ska gälla flera personer: Skapa separata events (en per person)
   - Behålla backup av daily_task-tabellerna innan migration
   - **Migreringsstrategi:** För varje kombination av (task, member, veckodag) skapas ett separat event med WEEKLY recurring
   - **Migreringsstrategi:** För varje kombination av (task, member, veckodag) skapas ett separat event med WEEKLY recurring

4. **Veckovy:** Ska alla medlemmar visas i samma vy, eller separat vy per medlem?
   - **Beslut:** Toggle/filter för att visa alla eller filtrera på person

5. **Drag & Drop:**
   - **Beslut:** Drag över flera dagar = recurring DAILY task som upprepas varje dag inom perioden
   - Om man gör tasken på Måndag, måste man också göra den på Tisdag, etc. (varje dag är en separat occurrence)
   - **Priority:** Kan börja med click-to-create, drag & drop i senare fas

6. **Obligatoriska/Extra:** ✅ **Bekräftat**
   - is_required fält behålls (TRUE = obligatorisk, FALSE = extra)
   - Färgkodning: Orange (extra) / Grå (obligatorisk) när ej klar, Grön när klar

7. **Snabb Addering:** ✅ **Nytt krav**
   - Enkel input för att snabbt lägga till flera one-time tasks på en dag
   - "Add"/"Lägg till" knapp
   - Defaults: is_task=TRUE, is_required=TRUE, xp_points=10, one-time för idag

## Nästa Steg

1. Diskutera och besvara frågor ovan
2. Finalisera databas-struktur
3. Skapa detaljerad implementation plan
4. Börja med Fas 1 (databas & backend)


# Implementation Status: Calendar/Tasks Merge

## ✅ Klart

### Fas 1: Databas & Backend
- ✅ Utöka calendar_event med is_task, xp_points, is_required
- ✅ Skapa calendar_event_task_completion tabell
- ✅ Migrera data från daily_task → calendar_event
- ✅ Uppdatera CalendarEvent domain model
- ✅ Uppdatera CalendarService för task-hantering
- ✅ Skapa endpoints för task completion
- ✅ XP-integration (awardXp/removeXp)
- ✅ Valideringar (member, occurrenceDate, xpPoints, etc.)

### Fas 2: Frontend - Föräldrars Vy
- ✅ Veckovy i CalendarView
- ✅ Event creation dialog med task-fields (is_task, xp_points, is_required)
- ✅ Snabb addering för one-time tasks (showQuickAdd)
- ✅ Toggle/filter: "Visa endast Dagens Att Göra" (showTasksOnly)
- ✅ Filter: Filtrera på person (showAllMembers, currentMemberId)
- ✅ Visa tasks med färgkodning (orange/grå när ej klar, grön när klar)
- ✅ Task completion UI (i rolling view med handleToggleTask)
- ✅ Klicka på dag i veckovyn för att skapa event
- ✅ Klicka på dag i månadsvyn för att skapa event (nytt!)
- ✅ Datum fylls automatiskt när man klickar på dag (nytt!)

### Fas 3: Frontend - Barnens Vy
- ✅ ChildDashboard använder calendar_event API (fetchTasksForToday)
- ✅ Filtrerar för current user + today
- ✅ Behåller nuvarande UX

## ❌ Återstående

### Fas 4: Cleanup
1. ✅ **Ta bort DailyTasksView för föräldrar**
   - ✅ Tagit bort från App.tsx routing (case statements)
   - ✅ Tagit bort imports från App.tsx
   - ✅ Tagit bort från useEffect check
   - ✅ Tagit bort filerna (DailyTasksView.tsx, DailyTasksAdminView.tsx, FamilyModeView.tsx)
   - ✅ Tagit bort dailytasks-mappen

2. ✅ **Uppdatera navigation**
   - ✅ Tagit bort "dailytasks" och "dailytasksadmin" från ViewKey types i:
     - App.tsx
     - Dashboard.tsx
     - CalendarView.tsx
     - XpDashboard.tsx
     - ChildrenXpView.tsx
   - ✅ Tagit bort imports och routing i App.tsx
   - ⚠️ ChildDashboard.tsx har fortfarande "dailytasks" i ViewKey type, men används inte (ChildDashboard använder calendar API direkt)

3. ✅ **Uppdatera dokumentation**
   - ✅ Uppdaterat README om att tasks nu hanteras i kalendern
   - ✅ Uppdaterat funktionslista i README

4. ❓ **daily_task tabeller**
   - Beslut behövs: Ska de behållas för backup eller tas bort?
   - Om de behålls: Markera som deprecated i dokumentation

### Fas 2.5: Framtida Förbättringar (Låg prioritet)
- ❌ **Drag & drop för tasks**
  - Flytta tasks mellan dagar
  - Dra task över flera dagar för att skapa recurring DAILY task
  - Markerat som "Senare" i implementation plan

## Sammanfattning

**Huvudsakligen återstår: Cleanup (Fas 4)**
- Ta bort gamla DailyTasksView/DailyTasksAdminView
- Uppdatera navigation och typer
- Uppdatera dokumentation

**Backend och core-funktionalitet är klar!** Allt fungerar, bara cleanup kvar.

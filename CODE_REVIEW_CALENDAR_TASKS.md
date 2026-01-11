# Kod-Review: Calendar/Tasks Merge Implementation

## ✅ Vad som är bra

1. **Migrations:** Tydliga och korrekta
2. **Domain Models:** Bra struktur, tydliga records
3. **Entities:** Korrekt mappning
4. **Repository:** Bra queries för completion lookup

## ❌ Kritiska Problem

### 1. **SAKNAS: XP-systemet är inte integrerat!**

**Problem:** `CalendarService.markTaskCompleted()` anropar INTE `XpService.awardXp()` när en task markeras som klar. Detta betyder att barn INTE får XP när de markerar calendar events som tasks.

**Jämförelse:**
- `DailyTaskService.toggleTaskCompletion()` anropar `xpService.awardXp(memberId, task.getXpPoints())` ✅
- `CalendarService.markTaskCompleted()` gör INTE detta ❌

**Fix behövs:**
- Injecta `XpService` i `CalendarService`
- Anropa `xpService.awardXp(memberId, eventEntity.getXpPoints())` när task markeras som klar
- Anropa `xpService.removeXp(memberId, eventEntity.getXpPoints())` när completion tas bort

**Komplicering:** `xpPoints` kan vara `null` (Integer), men `XpService.awardXp()` tar `int`. Behöver hantera null-check.

### 2. **Saknad validering: Member måste vara participant**

**Problem:** `markTaskCompleted()` accepterar vilken memberId som helst, även om de inte är participants i eventet.

**Fix behövs:**
- Validera att memberId är en participant i eventet (eller åtminstone i samma family)
- Alternativt: Låt alla i samma family markera tasks (men dokumentera detta)

### 3. **Saknad validering: occurrenceDate för recurring events**

**Problem:** För recurring events, vi accepterar vilket datum som helst, även om det inte matchar ett faktiskt occurrence av eventet.

**Fix behövs:**
- Validera att occurrenceDate faktiskt matchar ett occurrence av eventet (för recurring events)
- För one-time events: Validera att occurrenceDate == DATE(startDateTime)

### 4. **Null-säkerhet: xpPoints**

**Problem:** `xpPoints` är `Integer` (kan vara null), men `XpService.awardXp()` tar `int`.

**Fix behövs:**
- I `markTaskCompleted()`: Kolla att `eventEntity.getXpPoints() != null` innan anrop till XpService
- Om null: Antingen kasta exception eller använd default (0 eller 10)

## ⚠️ Mindre Problem / Förbättringar

### 5. **Access control i Controller**

**Problem:** `markTaskCompleted` endpoint tillåter att man markerar tasks för vilken member som helst (om memberId skickas).

**Fix behövs:**
- Validera att användaren har rätt att markera tasks för den angivna memberId
- T.ex. samma family, eller parent som markerar för sitt barn

### 6. **Gemensam completion för participants**

**Problem:** Logiken för "gemensam completion" är inte helt implementerad. Vi spårar completion per member, men logiken för att kolla om någon participant har markerat klart behöver verifieras.

**Fix behövs:**
- Dokumentera tydligt hur completion check fungerar för events med participants
- Eventuellt lägga till en metod som kollar completion-status för alla participants

### 7. **Saknad validering: isTask + xpPoints**

**Problem:** Vi tillåter att `isTask=false` men `xpPoints > 0`, vilket inte är meningsfullt.

**Fix behövs:**
- Validering i `createEvent()` och `updateEvent()`: Om `isTask=false`, bör `xpPoints` vara null eller 0
- Eventuellt: Om `isTask=true` och `xpPoints` är null, använd default (t.ex. 10)

### 8. **Saknad validering: isRequired för icke-tasks**

**Problem:** `isRequired` används bara när `isTask=true`, men vi tillåter att sätta det även för vanliga events.

**Fix behövs:**
- Validering eller dokumentation: `isRequired` ignoreras när `isTask=false`

## 📝 Sammanfattning

**Måste fixas innan merge:**
1. ✅ Integrera XP-systemet (awardXp/removeXp) - **FIXAD**
2. ✅ Validera member är participant eller i samma family - **FIXAD** (validerar family membership)
3. ✅ Hantera null för xpPoints - **FIXAD** (null-check innan XpService-anrop)
4. ✅ Validera occurrenceDate för recurring events - **FIXAD** (validateOccurrenceDate metod)

**Bör fixas:**
5. ✅ Access control i Controller - **FIXAD** (validerar family membership i endpoints)
6. ✅ Validering isTask + xpPoints - **FIXAD** (validering i createEvent och updateEvent)

**Kan fixas senare:**
7. Dokumentera gemensam completion-logik mer tydligt
8. Validering isRequired för icke-tasks (ignoreras när isTask=false, ok för nu)


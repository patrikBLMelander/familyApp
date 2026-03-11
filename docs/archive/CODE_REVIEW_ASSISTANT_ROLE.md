# Code Review: ASSISTANT Role Implementation
**Datum:** 2026-01-30  
**Granskare:** Senior Full Stack Developer  
**Scope:** Komplett granskning av ASSISTANT-rollens implementation

---

## Executive Summary

ASSISTANT-rollen är implementerad som en hybrid mellan CHILD och PARENT, vilket är korrekt enligt designintentionen. Implementationen är övergripande solid men har några områden som behöver förbättras för bättre konsistens, säkerhet och underhållbarhet.

**Övergripande bedömning:** ✅ **Godkänd med förbättringsförslag**

---

## 1. Backend Review

### ✅ Styrkor

#### 1.1 Roll-definition och typsäkerhet
- **Location:** `FamilyMember.java`
- **Status:** ✅ Utmärkt
- Rollen är tydligt definierad i enum med kommentarer
- Enum används konsekvent i backend-koden

#### 1.2 Pet System (Djur-system)
- **Location:** `PetController.java`, `PetService.java`
- **Status:** ✅ Korrekt implementerat
- Både CHILD och ASSISTANT kan:
  - Välja ägg (`selectEgg`)
  - Mata djur (`feedPet`)
- Rollkontroller är konsekventa på både controller- och service-nivå
- Felmeddelanden är tydliga

#### 1.3 XP System
- **Location:** `XpService.java`, `XpController.java`
- **Status:** ✅ Korrekt implementerat
- Både CHILD och ASSISTANT kan:
  - Få XP (`awardXp`)
  - Få bonus XP (`awardBonusXp`)
  - Förlora XP (`removeXp`)
- Rollkontroller är konsekventa i alla tre metoder
- Bonus XP-endpoint tillåter att ge bonus XP till ASSISTANT

#### 1.4 Email/Password Authentication
- **Location:** `FamilyService.java`, `FamilyMemberService.java`
- **Status:** ✅ Korrekt implementerat
- ASSISTANT kan logga in med email/password
- ASSISTANT kan uppdatera sin egen email och password
- Säkerhetskontroller är korrekta (self-service begränsad till egen användare)

### ⚠️ Problem och Förbättringsförslag

#### 1.1 Inkonsistent rollkontroll (MEDIUM)
**Location:** `PetService.java`, `XpService.java`

**Problem:**
```java
// PetService.java - använder String-jämförelse
String role = member.getRole();
if (!"CHILD".equals(role) && !"ASSISTANT".equals(role)) {
    throw new IllegalArgumentException("Only children and assistants can select pets");
}

// PetController.java - använder enum-jämförelse
if (member.role() != FamilyMember.Role.CHILD && member.role() != FamilyMember.Role.ASSISTANT) {
    throw new IllegalArgumentException("Only children and assistants can select eggs");
}
```

**Rekommendation:**
- Standardisera på enum-jämförelser i hela backend
- Skapa en helper-metod för att kontrollera om en roll är "child-like":
```java
public static boolean isChildLikeRole(FamilyMember.Role role) {
    return role == Role.CHILD || role == Role.ASSISTANT;
}
```

**Prioritet:** Medium - Fungerar men kan förbättras

#### 1.2 Saknad rollkontroll i vissa endpoints (LOW)
**Location:** `PetController.java`

**Problem:**
- `/current` endpoint saknar rollkontroll - alla kan hämta sin egen pet
- `/history` endpoint saknar rollkontroll - alla kan hämta sin egen history
- `/collected-food` endpoint saknar rollkontroll

**Rekommendation:**
- Lägg till rollkontroller även om de inte är kritiska (defense in depth)
- Eller dokumentera att dessa endpoints är avsiktligt öppna

**Prioritet:** Low - Säkerhetsmässigt OK eftersom de bara returnerar användarens egen data

#### 1.3 Error Messages - Inkonsistens (LOW)
**Location:** Flera filer

**Problem:**
- Olika formuleringar: "Only children and assistants" vs "Only children and assistants can select pets"
- Vissa meddelanden är mer specifika än andra

**Rekommendation:**
- Standardisera felmeddelanden
- Överväg att använda konstanter för vanliga meddelanden

**Prioritet:** Low - UX-förbättring

---

## 2. Frontend Review

### ✅ Styrkor

#### 2.1 Hook Implementation
- **Location:** `useIsChild.ts`
- **Status:** ✅ Utmärkt
- Hook hanterar både CHILD och ASSISTANT korrekt
- Kommentarer är tydliga
- Error handling är robust

#### 2.2 Navigation och Routing
- **Location:** `App.tsx`
- **Status:** ✅ Korrekt implementerat
- ASSISTANT kan navigera till:
  - Dashboard (samma som CHILD)
  - Listor (todos)
  - Kalender (schedule)
- Routing-logik är tydlig och välstrukturerad

#### 2.3 Meny Implementation
- **Location:** `App.tsx` (side-menu)
- **Status:** ✅ Korrekt implementerat
- Meny visar rätt alternativ baserat på roll
- ASSISTANT ser Listor och Kalender i menyn

#### 2.4 Calendar Permissions
- **Location:** `DayActionMenu.tsx`, `EventForm.tsx`, `RollingView.tsx`
- **Status:** ✅ Korrekt implementerat
- ASSISTANT kan skapa, redigera och ta bort events
- Permissions är konsekventa i hela calendar-featuren

### ⚠️ Problem och Förbättringsförslag

#### 2.1 Hook-namn kan vara missvisande (LOW)
**Location:** `useIsChild.ts`

**Problem:**
- Hook heter `useIsChild` men returnerar `true` för både CHILD och ASSISTANT
- Kan vara förvirrande för nya utvecklare

**Rekommendation:**
- Överväg att döpa om till `useIsChildOrAssistant` eller `useIsChildLike`
- Eller lägg till tydligare kommentarer
- Alternativt: skapa en separat `useIsAssistant` hook om det behövs

**Prioritet:** Low - Fungerar men kan förbättras

#### 2.2 Saknad Type Safety (MEDIUM)
**Location:** `App.tsx`

**Problem:**
```typescript
const isAssistant = childMember?.role === "ASSISTANT";
```

**Rekommendation:**
- Använd enum eller konstant istället för magic string
- Skapa en helper-funktion:
```typescript
const isAssistant = childMember?.role === "ASSISTANT" as const;
// eller
const isAssistant = isAssistantRole(childMember?.role);
```

**Prioritet:** Medium - Förbättrar type safety

#### 2.3 Duplicerad rollkontroll (LOW)
**Location:** `App.tsx`, `DayActionMenu.tsx`, `EventForm.tsx`

**Problem:**
- Rollkontroll `currentUserRole === "ASSISTANT"` upprepas på flera ställen
- Kan leda till inkonsistens vid framtida ändringar

**Rekommendation:**
- Skapa helper-funktioner:
```typescript
export function canEditEvents(role: FamilyMemberRole | null): boolean {
  return role === "PARENT" || role === "ASSISTANT";
}

export function canAccessCalendar(role: FamilyMemberRole | null): boolean {
  return role === "PARENT" || role === "ASSISTANT";
}
```

**Prioritet:** Low - Code quality improvement

#### 2.4 Todos Permissions (VERIFIED ✅)
**Location:** `TodoListController.java`, `TodoListService.java`

**Status:** ✅ Korrekt implementerat
- Todos använder family-based access control (inte role-based)
- ASSISTANT kan använda todos eftersom de tillhör samma family
- `validateListAccess()` verifierar family membership, inte roll
- Detta är korrekt design - todos är family-scoped, inte role-scoped

**Inga ändringar behövs**

---

## 3. Säkerhetsgranskning

### ✅ Säkerhetsstyrkor

1. **Rollbaserad åtkomstkontroll:** ✅ Korrekt implementerad
2. **Self-service begränsningar:** ✅ ASSISTANT kan bara uppdatera sig själv
3. **Family isolation:** ✅ Alla queries verifierar family membership
4. **Password hashing:** ✅ BCrypt används korrekt
5. **Email uniqueness:** ✅ Database constraint finns

### ⚠️ Säkerhetsöverväganden

#### 3.1 Defense in Depth (LOW)
- Vissa endpoints saknar rollkontroller (se 1.2)
- Rekommenderas att lägga till även om de inte är kritiska

#### 3.2 Rate Limiting (FUTURE)
- Inga rate limits på login/password attempts
- Rekommenderas för produktion (men är inte kritiskt för ASSISTANT-rollen specifikt)

---

## 4. Konsistens och Underhållbarhet

### ✅ Positiva aspekter

1. **Konsistent rollhantering:** Rollkontroller är konsekventa mellan frontend och backend
2. **Tydlig separation:** ASSISTANT behandlas korrekt som hybrid mellan CHILD och PARENT
3. **God dokumentation:** Kommentarer förklarar intentionen

### ⚠️ Förbättringsområden

1. **Standardisera rollkontroller:** Använd samma metod (enum vs string) överallt
2. **Helper-funktioner:** Skapa återanvändbara funktioner för vanliga rollkontroller
3. **Type safety:** Förbättra TypeScript type safety i frontend

---

## 5. Test Coverage

### Rekommenderade Tester

#### 5.1 Backend Tests
- [ ] ASSISTANT kan välja ägg
- [ ] ASSISTANT kan mata djur
- [ ] ASSISTANT kan få XP
- [ ] ASSISTANT kan få bonus XP
- [ ] ASSISTANT kan logga in med email/password
- [ ] ASSISTANT kan uppdatera sin egen email/password
- [ ] ASSISTANT kan INTE uppdatera andras email/password
- [ ] ASSISTANT kan INTE hantera familjemedlemmar

#### 5.2 Frontend Tests
- [ ] ASSISTANT ser rätt meny-alternativ
- [ ] ASSISTANT kan navigera till todos
- [ ] ASSISTANT kan navigera till kalender
- [ ] ASSISTANT kan skapa events i kalendern
- [ ] ASSISTANT kan redigera events i kalendern
- [ ] ASSISTANT kan ta bort events i kalendern
- [ ] ASSISTANT ser samma dashboard som CHILD

---

## 6. Rekommendationer per Prioritet

### 🔴 Hög Prioritet
*Inga kritiska problem hittade*

### 🟡 Medium Prioritet

1. **Standardisera rollkontroller i backend**
   - Använd enum-jämförelser konsekvent
   - Skapa helper-metoder för vanliga kontroller

2. **Förbättra Type Safety i frontend**
   - Använd konstanter/enums istället för magic strings
   - Skapa helper-funktioner för rollkontroller

### 🟢 Low Prioritet

1. **Standardisera felmeddelanden**
2. **Överväg att döpa om `useIsChild` hook**
3. **Lägg till rollkontroller i endpoints som saknar dem (defense in depth)**
4. ~~**Verifiera todos-permissions för ASSISTANT**~~ ✅ Verifierat - korrekt implementerat

---

## 7. Slutsats

ASSISTANT-rollen är **väl implementerad** och följer designintentionen korrekt. Implementationen är säker och funktionell, men kan förbättras med standardisering och refaktorisering för bättre underhållbarhet.

**Rekommendation:** ✅ **Godkänd för produktion** med förbättringsförslag för framtida iterationer.

### Nästa Steg

1. Implementera medium-prioritets förbättringar i nästa sprint
2. Lägg till tester för ASSISTANT-funktionalitet
3. Dokumentera ASSISTANT-permissions tydligt för teamet

---

## Bilaga: Checklista för ASSISTANT-funktionalitet

### Backend
- [x] ASSISTANT kan välja ägg
- [x] ASSISTANT kan mata djur
- [x] ASSISTANT kan få XP
- [x] ASSISTANT kan få bonus XP
- [x] ASSISTANT kan logga in med email/password
- [x] ASSISTANT kan uppdatera sin egen email/password
- [x] ASSISTANT kan INTE uppdatera andras email/password
- [x] ASSISTANT kan INTE hantera familjemedlemmar

### Frontend
- [x] ASSISTANT ser Dashboard (samma som CHILD)
- [x] ASSISTANT ser Listor i menyn
- [x] ASSISTANT ser Kalender i menyn
- [x] ASSISTANT kan navigera till todos
- [x] ASSISTANT kan navigera till kalender
- [x] ASSISTANT kan skapa events
- [x] ASSISTANT kan redigera events
- [x] ASSISTANT kan ta bort events
- [x] ASSISTANT kan INTE se "Kategorier"-knapp (endast PARENT)

---

**Review slutförd:** 2026-01-30

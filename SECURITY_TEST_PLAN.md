# Säkerhetstestplan - Familjisolering

## Översikt
Detta dokument beskriver testplanen för att verifiera att alla familjer är korrekt isolerade från varandra och att inga användare kan komma åt data från andra familjer.

## Testscenarier

### 1. Grundläggande funktionalitet (Regression Testing)
Verifiera att allt fungerar som tidigare efter säkerhetsförbättringarna.

#### 1.1 Kalender
- [ ] Skapa event i egen familj
- [ ] Uppdatera event i egen familj
- [ ] Radera event i egen familj
- [ ] Skapa kategori i egen familj
- [ ] Uppdatera kategori i egen familj
- [ ] Radera kategori i egen familj
- [ ] Markera task som klar i egen familj
- [ ] Se task completions i egen familj

#### 1.2 Todo-listor
- [ ] Skapa lista i egen familj
- [ ] Uppdatera lista i egen familj
- [ ] Radera lista i egen familj
- [ ] Lägga till item i egen lista
- [ ] Uppdatera item i egen lista
- [ ] Radera item i egen lista
- [ ] Toggle item i egen lista
- [ ] Reorder items i egen lista
- [ ] Reorder lists i egen familj

#### 1.3 Daily Tasks
- [ ] Skapa task i egen familj
- [ ] Uppdatera task i egen familj
- [ ] Radera task i egen familj
- [ ] Toggle task completion i egen familj
- [ ] Reorder tasks i egen familj

#### 1.4 XP och Pets
- [ ] Se egen XP progress
- [ ] Se egen XP history
- [ ] Se egen pet
- [ ] Se egen pet history
- [ ] Se familjemedlemmars XP (samma familj)
- [ ] Se familjemedlemmars pets (samma familj)

### 2. Säkerhetstester - Familjisolering

#### 2.1 Kalender - Cross-Family Access Prevention
- [ ] **FAIL**: Försök uppdatera event från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök radera event från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök uppdatera kategori från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök radera kategori från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök se task completions för event från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök se task completions för member från annan familj → ska ge "Access denied"

#### 2.2 Todo-listor - Cross-Family Access Prevention
- [ ] **FAIL**: Försök uppdatera lista från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök radera lista från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök lägga till item i lista från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök uppdatera item i lista från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök radera item i lista från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök toggle item i lista från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök reorder items i lista från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök reorder lists från annan familj → ska ge "Access denied"

#### 2.3 Daily Tasks - Cross-Family Access Prevention
- [ ] **FAIL**: Försök uppdatera task från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök radera task från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök toggle task completion för task från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök toggle task completion för member från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök reorder tasks från annan familj → ska ge "Access denied"

#### 2.4 XP och Pets - Cross-Family Access Prevention
- [ ] **FAIL**: Försök se XP progress för member från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök se XP history för member från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök se pet för member från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök se pet history för member från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök ge bonus XP till member från annan familj → ska ge "Access denied"

#### 2.5 Familjemedlemmar - Cross-Family Access Prevention
- [ ] **FAIL**: Försök se familjemedlemmar från annan familj → ska returnera tom lista
- [ ] **FAIL**: Försök skapa familjemedlem i annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök uppdatera familjemedlem från annan familj → ska ge "Access denied"
- [ ] **FAIL**: Försök radera familjemedlem från annan familj → ska ge "Access denied"

### 3. Edge Cases

#### 3.1 Ogiltiga Tokens
- [ ] **FAIL**: Försök använda ogiltig device token → ska ge "Invalid device token"
- [ ] **FAIL**: Försök använda tom device token → ska ge "Invalid device token"
- [ ] **FAIL**: Försök använda null device token → ska ge "Invalid device token"

#### 3.2 Saknade Tokens
- [ ] **FAIL**: Försök använda endpoint utan device token där det krävs → ska ge "Device token is required"

#### 3.3 Ogiltiga IDs
- [ ] **FAIL**: Försök använda ogiltigt event ID → ska ge "Event not found" eller "Access denied"
- [ ] **FAIL**: Försök använda ogiltigt list ID → ska ge "Access denied"
- [ ] **FAIL**: Försök använda ogiltigt task ID → ska ge "Access denied"
- [ ] **FAIL**: Försök använda ogiltigt member ID → ska ge "Access denied"

## Testmetod

### Automatiserade Tester
Ett bash-script (`test_security.sh`) kommer att testa:
1. Skapa två familjer
2. Skapa data i varje familj
3. Försöka komma åt varandras data
4. Verifiera att alla försök misslyckas med korrekt felmeddelande

### Manuella Tester
1. Registrera två familjer i appen
2. Skapa data i varje familj (events, lists, tasks)
3. Försök komma åt varandras data via API eller UI
4. Verifiera att inget data visas eller kan modifieras

## Förväntade Resultat

### ✅ Success Cases
- Alla operationer i egen familj fungerar som tidigare
- Inga regressioner i funktionalitet
- Alla endpoints returnerar korrekt data för egen familj

### ❌ Failure Cases
- Alla försök att komma åt data från annan familj ska ge:
  - HTTP 400 eller 403
  - Tydligt felmeddelande: "Access denied: [resource] does not belong to your family"
  - Inga data returneras

## Testmiljö

### Krav
- Backend körs lokalt eller i testmiljö
- Databas är tom eller har testdata
- Två testfamiljer kan skapas

### Testdata
- Family 1: "Test Family 1" med admin "Admin1"
- Family 2: "Test Family 2" med admin "Admin2"
- Varje familj har:
  - 1-2 calendar events
  - 1-2 todo lists med items
  - 1-2 daily tasks
  - 1-2 family members

## Verifiering

### Automatiserad Verifiering
- Script verifierar HTTP status codes
- Script verifierar felmeddelanden
- Script verifierar att inga data returneras vid obehörig åtkomst

### Manuell Verifiering
- Kontrollera i databasen att data är korrekt isolerad
- Kontrollera i UI att inga data från andra familjer visas
- Kontrollera loggar för säkerhetshändelser

## Riskbedömning

### Höga Risker
- ❌ Cross-family data leakage (KRITISKT)
- ❌ Unauthorized data modification (KRITISKT)
- ❌ Unauthorized data deletion (KRITISKT)

### Medelhöga Risker
- ⚠️ Regression i funktionalitet
- ⚠️ Performance degradation

### Låga Risker
- ℹ️ UI/UX förändringar
- ℹ️ Felmeddelanden

## Testresultat

### Status
- [ ] Alla automatiserade tester passerar
- [ ] Alla manuella tester passerar
- [ ] Inga säkerhetsluckor identifierade
- [ ] Inga regressioner identifierade

### Kända Problem
(Lista eventuella problem som hittats under testning)

---

## Nästa Steg

1. Kör automatiserade tester
2. Genomför manuella tester
3. Dokumentera resultat
4. Fixa eventuella problem
5. Verifiera fixar
6. Godkänn för produktion

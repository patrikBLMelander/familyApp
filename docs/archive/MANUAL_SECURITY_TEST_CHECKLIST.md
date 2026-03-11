# Manuell Säkerhetstest - Checklista

## Förberedelser

### 1. Skapa Testmiljö
- [ ] Starta backend lokalt eller i testmiljö
- [ ] Rensa databas eller använd testdatabas
- [ ] Öppna appen i webbläsare (eller två olika webbläsare/inkognito)

### 2. Skapa Testfamiljer
- [ ] Registrera "Test Family 1" med admin "Admin1" (email: admin1@test.com)
- [ ] Registrera "Test Family 2" med admin "Admin2" (email: admin2@test.com)
- [ ] Notera device tokens för båda familjerna

## Test 1: Kalender - Cross-Family Access

### 1.1 Skapa Data
- [ ] Logga in som Family 1
- [ ] Skapa kategori "Family1 Category"
- [ ] Skapa event "Family1 Event"
- [ ] Logga ut

- [ ] Logga in som Family 2
- [ ] Skapa kategori "Family2 Category"
- [ ] Skapa event "Family2 Event"
- [ ] Logga ut

### 1.2 Verifiera Isolation
- [ ] Logga in som Family 1
- [ ] Verifiera att ENDAST "Family1 Event" syns
- [ ] Verifiera att ENDAST "Family1 Category" syns
- [ ] Försök uppdatera "Family2 Event" (om ID är känt) → ska misslyckas
- [ ] Försök radera "Family2 Category" (om ID är känt) → ska misslyckas

- [ ] Logga in som Family 2
- [ ] Verifiera att ENDAST "Family2 Event" syns
- [ ] Verifiera att ENDAST "Family2 Category" syns
- [ ] Försök uppdatera "Family1 Event" (om ID är känt) → ska misslyckas
- [ ] Försök radera "Family1 Category" (om ID är känt) → ska misslyckas

## Test 2: Todo-listor - Cross-Family Access

### 2.1 Skapa Data
- [ ] Logga in som Family 1
- [ ] Skapa lista "Family1 List"
- [ ] Lägg till item "Family1 Item 1"
- [ ] Logga ut

- [ ] Logga in som Family 2
- [ ] Skapa lista "Family2 List"
- [ ] Lägg till item "Family2 Item 1"
- [ ] Logga ut

### 2.2 Verifiera Isolation
- [ ] Logga in som Family 1
- [ ] Verifiera att ENDAST "Family1 List" syns
- [ ] Försök uppdatera "Family2 List" (om ID är känt) → ska misslyckas
- [ ] Försök radera "Family2 List" (om ID är känt) → ska misslyckas

- [ ] Logga in som Family 2
- [ ] Verifiera att ENDAST "Family2 List" syns
- [ ] Försök uppdatera "Family1 List" (om ID är känt) → ska misslyckas
- [ ] Försök radera "Family1 List" (om ID är känt) → ska misslyckas

## Test 3: Daily Tasks - Cross-Family Access

### 3.1 Skapa Data
- [ ] Logga in som Family 1
- [ ] Skapa task "Family1 Task" (för måndagar)
- [ ] Logga ut

- [ ] Logga in som Family 2
- [ ] Skapa task "Family2 Task" (för måndagar)
- [ ] Logga ut

### 3.2 Verifiera Isolation
- [ ] Logga in som Family 1
- [ ] Verifiera att ENDAST "Family1 Task" syns
- [ ] Försök uppdatera "Family2 Task" (om ID är känt) → ska misslyckas
- [ ] Försök radera "Family2 Task" (om ID är känt) → ska misslyckas

- [ ] Logga in som Family 2
- [ ] Verifiera att ENDAST "Family2 Task" syns
- [ ] Försök uppdatera "Family1 Task" (om ID är känt) → ska misslyckas
- [ ] Försök radera "Family1 Task" (om ID är känt) → ska misslyckas

## Test 4: Familjemedlemmar - Cross-Family Access

### 4.1 Skapa Data
- [ ] Logga in som Family 1
- [ ] Skapa familjemedlem "Child1"
- [ ] Logga ut

- [ ] Logga in som Family 2
- [ ] Skapa familjemedlem "Child2"
- [ ] Logga ut

### 4.2 Verifiera Isolation
- [ ] Logga in som Family 1
- [ ] Verifiera att ENDAST "Child1" och "Admin1" syns
- [ ] Verifiera att "Child2" INTE syns

- [ ] Logga in som Family 2
- [ ] Verifiera att ENDAST "Child2" och "Admin2" syns
- [ ] Verifiera att "Child1" INTE syns

## Test 5: XP och Pets - Cross-Family Access

### 5.1 Skapa Data
- [ ] Logga in som Family 1 → Child1
- [ ] Välj ägg för Child1
- [ ] Logga ut

- [ ] Logga in som Family 2 → Child2
- [ ] Välj ägg för Child2
- [ ] Logga ut

### 5.2 Verifiera Isolation
- [ ] Logga in som Family 1 → Admin1
- [ ] Gå till "Barnens XP"
- [ ] Verifiera att ENDAST Child1 syns
- [ ] Försök se Child2's XP → ska misslyckas eller inte synas

- [ ] Logga in som Family 2 → Admin2
- [ ] Gå till "Barnens XP"
- [ ] Verifiera att ENDAST Child2 syns
- [ ] Försök se Child1's XP → ska misslyckas eller inte synas

## Test 6: Regression Testing - Verifiera att allt fungerar

### 6.1 Kalender
- [ ] Skapa event i egen familj → ska fungera
- [ ] Uppdatera event i egen familj → ska fungera
- [ ] Radera event i egen familj → ska fungera
- [ ] Skapa kategori i egen familj → ska fungera
- [ ] Uppdatera kategori i egen familj → ska fungera
- [ ] Radera kategori i egen familj → ska fungera

### 6.2 Todo-listor
- [ ] Skapa lista i egen familj → ska fungera
- [ ] Uppdatera lista i egen familj → ska fungera
- [ ] Radera lista i egen familj → ska fungera
- [ ] Lägg till item i egen lista → ska fungera
- [ ] Uppdatera item i egen lista → ska fungera
- [ ] Radera item i egen lista → ska fungera

### 6.3 Daily Tasks
- [ ] Skapa task i egen familj → ska fungera
- [ ] Uppdatera task i egen familj → ska fungera
- [ ] Radera task i egen familj → ska fungera
- [ ] Toggle task completion i egen familj → ska fungera

## Test 7: Felmeddelanden

### 7.1 Verifiera Tydliga Felmeddelanden
- [ ] Försök komma åt annan familjs data → ska visa tydligt felmeddelande
- [ ] Felmeddelandet ska innehålla "Access denied" eller liknande
- [ ] Felmeddelandet ska INTE exponera tekniska detaljer (stack traces, etc.)

## Test 8: Edge Cases

### 8.1 Ogiltiga Tokens
- [ ] Försök använda ogiltig device token → ska ge tydligt felmeddelande
- [ ] Försök använda tom device token → ska ge tydligt felmeddelande

### 8.2 Saknade Tokens
- [ ] Försök använda endpoint utan token (där det krävs) → ska ge tydligt felmeddelande

### 8.3 Ogiltiga IDs
- [ ] Försök använda ogiltigt event ID → ska ge tydligt felmeddelande
- [ ] Försök använda ogiltigt list ID → ska ge tydligt felmeddelande
- [ ] Försök använda ogiltigt task ID → ska ge tydligt felmeddelande

## Testresultat

### Status
- [ ] Alla tester passerade
- [ ] Inga säkerhetsluckor identifierade
- [ ] Inga regressioner identifierade
- [ ] Alla felmeddelanden är tydliga

### Kända Problem
(Lista eventuella problem som hittats)

### Godkännande
- [ ] Testat av: ________________
- [ ] Datum: ________________
- [ ] Godkänd för produktion: ☐ Ja ☐ Nej

---

## Tips för Testning

1. **Använd två webbläsare eller inkognito-läge** för att enkelt växla mellan familjer
2. **Öppna Developer Tools** för att se API-anrop och felmeddelanden
3. **Kontrollera Network tab** för att verifiera att inga data från andra familjer hämtas
4. **Kontrollera Console** för eventuella JavaScript-fel
5. **Testa både via UI och direkt API-anrop** (via curl eller Postman)

## Om Test Misslyckas

1. Dokumentera exakt vad som hände
2. Ta skärmdumpar av felmeddelanden
3. Kopiera API-responser från Network tab
4. Rapportera till utvecklare med detaljerad information

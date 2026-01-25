# Automatiserade Tester för Bonus Mat

## Vad jag KAN testa automatiskt

### ✅ 1. Backend API Endpoints (via HTTP requests)

#### 1.1 Ge Bonus Mat
- [x] `POST /api/v1/xp/members/{memberId}/bonus` med korrekt data
- [x] Validering: 0, negativt, >100, text
- [x] Felhantering: saknad device token, fel roll, fel familj
- [x] Verifiera att food items skapas i databasen

#### 1.2 Hämta Insamlad Mat
- [x] `GET /api/v1/pets/collected-food` returnerar korrekt data
- [x] Verifiera att bonus mat (eventId = null) ingår
- [x] Verifiera att task-mat (eventId != null) ingår
- [x] Verifiera att totalCount stämmer

#### 1.3 Mata Djuret
- [x] `POST /api/v1/pets/feed` med korrekt xpAmount
- [x] Verifiera att XP ökar
- [x] Verifiera att food items markeras som matade
- [x] Verifiera att pet growth stage uppdateras

### ✅ 2. Databas-verifieringar (via SQL queries)

#### 2.1 Tabellstruktur
- [x] Verifiera att `collected_food.event_id` är nullable
- [x] Verifiera att foreign key constraints är korrekta
- [x] Verifiera att indexes finns

#### 2.2 Data-integritet
- [x] Verifiera att bonus mat sparas med `event_id = NULL`
- [x] Verifiera att task-mat sparas med `event_id` satt
- [x] Verifiera att `is_fed` flaggan fungerar
- [x] Verifiera att `collected_at` och `fed_at` timestamps är korrekta

#### 2.3 Query-prestanda
- [x] Verifiera att queries för unfed food fungerar
- [x] Verifiera att count queries fungerar
- [x] Verifiera att batch operations (saveAll, deleteAll) fungerar

### ✅ 3. Kodanalys och Logik-verifiering

#### 3.1 Backend Logik
- [x] Verifiera att `addBonusFood()` skapar food items korrekt
- [x] Verifiera att `markFoodAsFed()` markerar food korrekt
- [x] Verifiera att `removeFoodFromTask()` hanterar bonus mat
- [x] Verifiera att error messages är tydliga

#### 3.2 Frontend Types
- [x] Verifiera att `FoodItemResponse.eventId` är `string | null`
- [x] Verifiera att TypeScript types är korrekta
- [x] Verifiera att inga type errors finns

#### 3.3 Linter och Build
- [x] Köra linter för att hitta fel
- [x] Verifiera att build fungerar
- [x] Verifiera att inga compilation errors finns

---

## Vad jag INTE kan testa automatiskt

### ❌ 1. UI/UX Tester
- [ ] Se visuella element (knappar, färger, layout)
- [ ] Klicka på knappar eller interagera med UI
- [ ] Se animations (confetti, floating numbers)
- [ ] Verifiera att progress bar ser korrekt ut
- [ ] Verifiera att pet mood messages visas korrekt

### ❌ 2. Manuella Användarflöden
- [ ] Full flow: Ge mat → Logga in som barn → Se mat → Mata djuret
- [ ] Verifiera att knappar är disabled när de ska vara
- [ ] Verifiera att felmeddelanden visas korrekt i UI
- [ ] Verifiera att loading states fungerar

### ❌ 3. Cross-browser och Mobil
- [ ] Testa i olika webbläsare (Chrome, Safari, Firefox)
- [ ] Testa på mobil (iOS, Android)
- [ ] Verifiera responsive design
- [ ] Verifiera touch interactions

### ❌ 4. Performance i Webbläsare
- [ ] Verifiera att animations är smidiga
- [ ] Verifiera att sidan laddar snabbt
- [ ] Verifiera att inga memory leaks finns

---

## Test Script som jag kan skapa

Jag kan skapa ett bash-script (liknande `test_caching.sh`) som testar:

1. **API Endpoints:**
   - Ge bonus mat
   - Hämta insamlad mat
   - Mata djuret
   - Verifiera responses

2. **Databas-queries:**
   - Verifiera att food items skapas
   - Verifiera att food items markeras som matade
   - Verifiera att counts stämmer

3. **Error Cases:**
   - Testa validering
   - Testa felhantering
   - Testa access control

---

## Rekommenderad Test-Ordning

### Steg 1: Automatiserade Tester (Jag kan göra)
1. ✅ Skapa test script för API endpoints
2. ✅ Verifiera databas-struktur
3. ✅ Testa error cases
4. ✅ Verifiera kod-logik

### Steg 2: Manuella Tester (Du behöver göra)
1. ❌ Testa UI/UX i webbläsare
2. ❌ Testa fulla användarflöden
3. ❌ Testa på mobil
4. ❌ Verifiera visuell feedback

---

## Vill du att jag skapar ett test script?

Jag kan skapa ett script som testar:
- API endpoints automatiskt
- Databas-integritet
- Error handling
- Basic funktionalitet

Säg till om du vill att jag skapar det! 🚀

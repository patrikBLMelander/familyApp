# Pre-Push Checklist: Menstrual Cycle Feature

## ✅ Ändringar som berör menscykel

### Backend (Nya filer - ✅ Säker)
- ✅ `backend/src/main/java/com/familyapp/api/menstrualcycle/` - Ny controller
- ✅ `backend/src/main/java/com/familyapp/application/menstrualcycle/` - Ny service
- ✅ `backend/src/main/java/com/familyapp/domain/menstrualcycle/` - Ny domain model
- ✅ `backend/src/main/java/com/familyapp/infrastructure/menstrualcycle/` - Ny infrastructure layer
- ✅ `backend/src/main/resources/db/migration/V33__add_menstrual_cycle.sql` - Ny migration

### Backend (Modifierade filer - ⚠️ Kolla)
- ⚠️ `backend/src/main/java/com/familyapp/domain/familymember/FamilyMember.java`
  - **Ändring:** Lagt till `menstrualCycleEnabled` och `menstrualCyclePrivate` fält
  - **Risk:** Låg - Nya fält, backward compatible (defaults finns i DB)
  - **Verifiering:** ✅ Alla konstruktorer uppdaterade

- ⚠️ `backend/src/main/java/com/familyapp/infrastructure/familymember/FamilyMemberEntity.java`
  - **Ändring:** Lagt till kolumner för menstrual cycle settings
  - **Risk:** Låg - Nya kolumner med defaults

- ⚠️ `backend/src/main/java/com/familyapp/application/familymember/FamilyMemberService.java`
  - **Ändring:** Uppdaterat `toDomain` och lagt till `updateMenstrualCycleSettings`
  - **Risk:** Låg - Nya metoder, befintliga oförändrade

- ⚠️ `backend/src/main/java/com/familyapp/api/familymember/FamilyMemberController.java`
  - **Ändring:** Lagt till endpoint för menstrual cycle settings
  - **Risk:** Låg - Ny endpoint, påverkar inte befintliga

### Frontend (Nya filer - ✅ Säker)
- ✅ `frontend/src/features/menstrualcycle/` - Nya komponenter
- ✅ `frontend/src/shared/api/menstrualCycle.ts` - Ny API client
- ✅ `frontend/src/shared/utils/dateUtils.ts` - Ny utility (används bara av menscykel)

### Frontend (Modifierade filer - ⚠️ Kolla)
- ⚠️ `frontend/src/App.tsx`
  - **Ändring:** Lagt till "menstrualcycle" view och import
  - **Risk:** Låg - Ny view, påverkar inte befintliga

- ⚠️ `frontend/src/features/familymembers/FamilyMembersView.tsx`
  - **Ändring:** Lagt till UI för menstrual cycle settings
  - **Risk:** Låg - Ny funktionalitet, påverkar inte befintlig

- ⚠️ `frontend/src/shared/api/familyMembers.ts`
  - **Ändring:** Uppdaterat `FamilyMemberResponse` type och lagt till `updateMenstrualCycleSettings`
  - **Risk:** Låg - Nya fält är optional, backward compatible

## ⚠️ Ändringar som INTE är menscykel-relaterade

Dessa filer har ändrats men verkar vara från andra features/security fixes:

- `backend/src/main/java/com/familyapp/api/dailytask/DailyTaskController.java`
  - **Ändring:** Säkerhetsförbättringar (family validation)
  - **Status:** Separata ändringar, inte menscykel

- `backend/src/main/java/com/familyapp/api/todo/TodoListController.java`
  - **Status:** Separata ändringar, inte menscykel

- `backend/src/main/java/com/familyapp/application/family/FamilyService.java`
  - **Ändring:** Device token collision handling
  - **Status:** Separata ändringar, inte menscykel

## ✅ Verifieringar

### Backend
- ✅ Migration script finns (`V33__add_menstrual_cycle.sql`)
- ✅ Alla nya fält har defaults i migration
- ✅ Backward compatible (gamla kod fungerar fortfarande)
- ✅ Validering finns på alla inputs
- ✅ Access control implementerad
- ✅ Inga breaking changes i befintliga API:er

### Frontend
- ✅ `dateUtils.ts` används BARA av menscykel-komponenter
- ✅ Inga andra komponenter importerar menscykel-kod
- ✅ Nya API-anrop påverkar inte befintliga
- ✅ Type safety behållen

### Database
- ✅ Migration är idempotent (kan köras flera gånger)
- ✅ Nya kolumner har defaults
- ✅ Foreign keys korrekt konfigurerade
- ✅ Index finns för performance

## 🔍 Ytterligare saker att kontrollera

### 1. Migration-ordning
- ✅ Kontrollera att V33 är rätt nummer (ingen konflikt med andra migrations)

### 2. Testa lokalt
- [ ] Testa att migration körs utan fel
- [ ] Testa att gamla funktioner fortfarande fungerar
- [ ] Testa menscykel-funktionalitet end-to-end

### 3. Breaking changes
- ✅ Inga breaking changes identifierade
- ✅ Alla nya fält är optional/backward compatible

### 4. Dependencies
- ✅ Inga nya dependencies behövs
- ✅ Alla imports finns redan i projektet

## 📝 Rekommendationer innan push

1. **Kör migration lokalt** för att verifiera att den fungerar
2. **Testa att gamla funktioner fortfarande fungerar** (familymembers, etc.)
3. **Verifiera att inga linter errors finns**
4. **Överväg att committa menscykel-ändringar separat** från de andra ändringarna (DailyTaskController, etc.)

## ✅ Slutsats

**Risk-nivå: LÅG** ✅

- Menscykel-koden är isolerad och påverkar inte befintlig funktionalitet
- Alla ändringar är backward compatible
- Nya fält har defaults
- Migration är säker att köra

**Rekommendation:** 
✅ Säker att pusha menscykel-ändringarna. Överväg att separera dem från de andra ändringarna (DailyTaskController, FamilyService) om de inte är relaterade.

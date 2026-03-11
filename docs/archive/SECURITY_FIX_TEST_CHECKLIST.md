# Test Checklist: Security Fixes

## Översikt av Säkerhetsfixar

### 1. DailyTaskController - Cross-Family Access Prevention
**Problem som fixas:** Användare kunde tidigare uppdatera/ta bort/toggle tasks från andra familjer.

**Ändringar:**
- `updateTask` - Validerar att task tillhör samma family
- `deleteTask` - Validerar att task tillhör samma family  
- `toggleTaskCompletion` - Validerar att task och member tillhör samma family
- `reorderTasks` - Validerar att alla tasks tillhör samma family

### 2. TodoListController - Cross-Family Access Prevention
**Problem som fixas:** Användare kunde tidigare modifiera todo lists från andra familjer.

**Ändringar:**
- Alla endpoints kräver nu `deviceToken` och validerar att list tillhör samma family
- Ny `validateListAccess` metod som används överallt

### 3. FamilyService - Device Token Collision Prevention
**Problem som fixas:** Extremt sällsynt men möjligt att två användare får samma device token.

**Ändringar:**
- Retry-logik vid token generation (max 10 försök)
- Cache eviction för att undvika stale entries

---

## 🔴 Kritiska Tester (Måste testas innan push)

### DailyTaskController Tests

#### Test 1: Update Task - Cross-Family Access Denied
**Scenario:**
1. Skapa en task i Family A
2. Försök uppdatera tasken med en användare från Family B
3. **Förväntat resultat:** `IllegalArgumentException` med meddelande "Access denied: Task does not belong to your family"

**Test-steg:**
```bash
# 1. Skapa task i Family A
POST /api/v1/daily-tasks
Headers: X-Device-Token: <familyA_user_token>
Body: { name: "Test Task", ... }

# 2. Försök uppdatera med Family B user
PATCH /api/v1/daily-tasks/{taskId}
Headers: X-Device-Token: <familyB_user_token>
Body: { name: "Hacked Task", ... }

# Förväntat: 400 Bad Request med "Access denied"
```

#### Test 2: Delete Task - Cross-Family Access Denied
**Scenario:**
1. Skapa en task i Family A
2. Försök ta bort tasken med en användare från Family B
3. **Förväntat resultat:** `IllegalArgumentException` med meddelande "Access denied"

**Test-steg:**
```bash
DELETE /api/v1/daily-tasks/{taskId}
Headers: X-Device-Token: <familyB_user_token>

# Förväntat: 400 Bad Request med "Access denied"
```

#### Test 3: Toggle Task - Cross-Family Access Denied
**Scenario:**
1. Skapa en task i Family A
2. Försök toggle tasken med en användare från Family B
3. **Förväntat resultat:** `IllegalArgumentException`

**Test-steg:**
```bash
POST /api/v1/daily-tasks/{taskId}/toggle
Headers: X-Device-Token: <familyB_user_token>

# Förväntat: 400 Bad Request med "Access denied"
```

#### Test 4: Toggle Task - Cross-Family Member Denied
**Scenario:**
1. Skapa en task i Family A
2. Försök toggle tasken för en member från Family B (med memberId parameter)
3. **Förväntat resultat:** `IllegalArgumentException` med "Member is not in the same family"

**Test-steg:**
```bash
POST /api/v1/daily-tasks/{taskId}/toggle?memberId=<familyB_member_id>
Headers: X-Device-Token: <familyA_user_token>

# Förväntat: 400 Bad Request med "Member is not in the same family"
```

#### Test 5: Reorder Tasks - Cross-Family Task Denied
**Scenario:**
1. Skapa tasks i både Family A och Family B
2. Försök reordera med en mix av tasks från båda familjer
3. **Förväntat resultat:** `IllegalArgumentException` när första task från fel family hittas

**Test-steg:**
```bash
POST /api/v1/daily-tasks/reorder
Headers: X-Device-Token: <familyA_user_token>
Body: { taskIds: [familyA_task1, familyB_task1, familyA_task2] }

# Förväntat: 400 Bad Request med "Access denied" för familyB_task1
```

#### Test 6: Toggle Task - Missing Device Token
**Scenario:**
1. Försök toggle en task utan device token
2. **Förväntat resultat:** `IllegalArgumentException` med "Device token is required"

**Test-steg:**
```bash
POST /api/v1/daily-tasks/{taskId}/toggle
# Ingen X-Device-Token header

# Förväntat: 400 Bad Request med "Device token is required"
```

#### Test 7: Normal Operation - Same Family (Regression Test)
**Scenario:**
1. Skapa task i Family A
2. Uppdatera, ta bort, toggle med användare från samma family
3. **Förväntat resultat:** Alla operationer fungerar normalt

**Test-steg:**
```bash
# Alla operationer med samma family token ska fungera
# Detta är en regression test för att säkerställa att vi inte bröt befintlig funktionalitet
```

---

### TodoListController Tests

#### Test 8: Update List Name - Cross-Family Access Denied
**Scenario:**
1. Skapa en todo list i Family A
2. Försök uppdatera listan med användare från Family B
3. **Förväntat resultat:** `IllegalArgumentException` med "Access denied"

**Test-steg:**
```bash
PATCH /api/v1/todo-lists/{listId}
Headers: X-Device-Token: <familyB_user_token>
Body: { name: "Hacked List" }

# Förväntat: 400 Bad Request med "Access denied"
```

#### Test 9: Add Item - Cross-Family Access Denied
**Scenario:**
1. Skapa en todo list i Family A
2. Försök lägga till item med användare från Family B
3. **Förväntat resultat:** `IllegalArgumentException`

**Test-steg:**
```bash
POST /api/v1/todo-lists/{listId}/items
Headers: X-Device-Token: <familyB_user_token>
Body: { description: "Hacked item" }

# Förväntat: 400 Bad Request med "Access denied"
```

#### Test 10: Delete List - Cross-Family Access Denied
**Scenario:**
1. Skapa en todo list i Family A
2. Försök ta bort listan med användare från Family B
3. **Förväntat resultat:** `IllegalArgumentException`

**Test-steg:**
```bash
DELETE /api/v1/todo-lists/{listId}
Headers: X-Device-Token: <familyB_user_token>

# Förväntat: 400 Bad Request med "Access denied"
```

#### Test 11: Reorder Lists - Cross-Family Lists Denied
**Scenario:**
1. Skapa lists i både Family A och Family B
2. Försök reordera med en mix av lists från båda familjer
3. **Förväntat resultat:** `IllegalArgumentException`

**Test-steg:**
```bash
POST /api/v1/todo-lists/reorder
Headers: X-Device-Token: <familyA_user_token>
Body: { listIds: [familyA_list1, familyB_list1, familyA_list2] }

# Förväntat: 400 Bad Request med "Access denied"
```

#### Test 12: All Todo Operations - Missing Device Token
**Scenario:**
1. Försök utföra alla todo-operationer utan device token
2. **Förväntat resultat:** `IllegalArgumentException` med "Device token is required"

**Endpoints att testa:**
- `PATCH /api/v1/todo-lists/{listId}`
- `PATCH /api/v1/todo-lists/{listId}/color`
- `PATCH /api/v1/todo-lists/{listId}/privacy`
- `POST /api/v1/todo-lists/{listId}/items`
- `PATCH /api/v1/todo-lists/{listId}/items/{itemId}/toggle`
- `PATCH /api/v1/todo-lists/{listId}/items/{itemId}`
- `DELETE /api/v1/todo-lists/{listId}/items/done`
- `DELETE /api/v1/todo-lists/{listId}/items/{itemId}`
- `POST /api/v1/todo-lists/{listId}/items/reorder`
- `POST /api/v1/todo-lists/reorder`
- `DELETE /api/v1/todo-lists/{listId}`

#### Test 13: Normal Todo Operations - Same Family (Regression Test)
**Scenario:**
1. Alla todo-operationer med användare från samma family
2. **Förväntat resultat:** Alla operationer fungerar normalt

---

### FamilyService Tests

#### Test 14: Device Token Collision - Retry Logic
**Scenario:**
1. Simulera en token collision (extremt sällsynt i verkligheten)
2. **Förväntat resultat:** Systemet genererar ny token och försöker igen (max 10 gånger)

**Test-steg:**
- Detta är svårt att testa manuellt eftersom UUID collisions är extremt sällsynta
- Överväg unit test eller mock test för detta
- Verifiera att retry-logiken fungerar korrekt

#### Test 15: Device Token Generation - Normal Case
**Scenario:**
1. Skapa ny family (normal case)
2. Logga in (normal case)
3. **Förväntat resultat:** Unik device token genereras utan problem

**Test-steg:**
```bash
# Skapa ny family
POST /api/v1/families
# Verifiera att admin får device token

# Logga in
POST /api/v1/families/{familyId}/login
# Verifiera att ny device token genereras
```

#### Test 16: Cache Eviction - Token Generation
**Scenario:**
1. Verifiera att cache eviction anropas vid token generation
2. **Förväntat resultat:** Inga stale cache entries

**Test-steg:**
- Detta kräver att man kollar loggar eller använder debugger
- Verifiera att `cacheService.evictDeviceToken()` anropas

---

## 🟡 Viktiga Regression Tests

### Test 17: Daily Tasks - Normal Operations Still Work
**Testa att alla normala operationer fortfarande fungerar:**
- ✅ Skapa task (samma family)
- ✅ Uppdatera task (samma family)
- ✅ Ta bort task (samma family)
- ✅ Toggle task completion (samma family, samma member)
- ✅ Toggle task completion (samma family, annan member)
- ✅ Reorder tasks (samma family)
- ✅ Hämta tasks för idag
- ✅ Hämta alla tasks

### Test 18: Todo Lists - Normal Operations Still Work
**Testa att alla normala operationer fortfarande fungerar:**
- ✅ Skapa list (samma family)
- ✅ Uppdatera list name (samma family)
- ✅ Uppdatera list color (samma family)
- ✅ Uppdatera list privacy (samma family)
- ✅ Lägga till item (samma family)
- ✅ Toggle item (samma family)
- ✅ Uppdatera item (samma family)
- ✅ Ta bort item (samma family)
- ✅ Clear done items (samma family)
- ✅ Reorder items (samma family)
- ✅ Reorder lists (samma family)
- ✅ Ta bort list (samma family)

### Test 19: Edge Cases
**Testa edge cases:**
- ✅ Null/empty device token hantering
- ✅ Invalid device token hantering
- ✅ Task/List som inte finns (404 vs 403)
- ✅ Member som inte finns vid toggle
- ✅ Empty taskIds/listIds arrays vid reorder

---

## 🟢 Performance & Load Tests

### Test 20: Performance Impact
**Scenario:**
1. Mät tiden för operationer före och efter fixarna
2. **Förväntat resultat:** Minimal performance impact (< 50ms overhead)

**Operationer att mäta:**
- Update task (nu med extra validering)
- Delete task (nu med extra validering)
- Toggle task (nu med extra validering)
- Update list (nu med extra validering)

### Test 21: Database Query Impact
**Scenario:**
1. Verifiera att extra queries inte orsakar N+1 problem
2. **Förväntat resultat:** Antal queries är rimligt

**Kontrollera:**
- `getAllTasks()` anropas en gång per request (inte per task)
- `getAllLists()` anropas en gång per request (inte per list)

---

## 📋 Test Execution Checklist

### Förberedelser
- [ ] Skapa två test-familjer (Family A och Family B)
- [ ] Skapa användare i båda familjerna
- [ ] Skapa test-data (tasks, lists) i båda familjerna
- [ ] Förbered API-test-verktyg (Postman, curl, etc.)

### Kritiska Tester (Måste köras)
- [ ] Test 1: Update Task - Cross-Family Denied
- [ ] Test 2: Delete Task - Cross-Family Denied
- [ ] Test 3: Toggle Task - Cross-Family Denied
- [ ] Test 4: Toggle Task - Cross-Family Member Denied
- [ ] Test 5: Reorder Tasks - Cross-Family Denied
- [ ] Test 6: Toggle Task - Missing Token
- [ ] Test 7: Normal Daily Task Operations (Regression)
- [ ] Test 8: Update List - Cross-Family Denied
- [ ] Test 9: Add Item - Cross-Family Denied
- [ ] Test 10: Delete List - Cross-Family Denied
- [ ] Test 11: Reorder Lists - Cross-Family Denied
- [ ] Test 12: All Todo Operations - Missing Token
- [ ] Test 13: Normal Todo Operations (Regression)
- [ ] Test 15: Device Token Generation - Normal Case

### Viktiga Regression Tester
- [ ] Test 17: Daily Tasks - Normal Operations
- [ ] Test 18: Todo Lists - Normal Operations
- [ ] Test 19: Edge Cases

### Performance Tester (Optional men rekommenderat)
- [ ] Test 20: Performance Impact
- [ ] Test 21: Database Query Impact

---

## ✅ Frontend Compatibility Check

### Verifiering: Device Token i Frontend
**Status:** ✅ VERIFIERAT

Alla frontend API-anrop använder redan `getHeaders()` som inkluderar deviceToken:
- ✅ `dailyTasks.ts` - Alla endpoints använder `getHeaders()`
- ✅ `todos.ts` - Alla endpoints använder `getHeaders()`

**Potentiell Risk:** Om `deviceToken` är `null` i localStorage kommer backend nu att kasta exception istället för att fortsätta. Detta är faktiskt en förbättring (mer säkert), men kan orsaka problem om användare inte är inloggade.

**Rekommendation:** Testa edge case där användare inte är inloggade.

---

## 🚨 Kända Risker

### Risk 1: Breaking Changes för Frontend
**Problem:** Om användare inte är inloggade (deviceToken saknas) kommer requests nu att faila.

**Verifiering:**
- [x] ✅ Alla frontend-anrop inkluderar deviceToken (verifierat i kod)
- [ ] ⚠️ Testa edge case: Vad händer om användare inte är inloggad?
- [ ] Testa att frontend hanterar 400 errors korrekt

### Risk 2: Legacy Data
**Problem:** Gamla tasks/lists kanske saknar family-relation.

**Verifiering:**
- [ ] Kontrollera att gamla tasks/lists fortfarande fungerar
- [ ] Överväg migration för att sätta family på gamla data
- [ ] Verifiera att `getAllTasks(requesterFamilyId)` hanterar tasks utan family korrekt

### Risk 3: Null Device Token Edge Case
**Problem:** Om `deviceToken` är null/empty i localStorage, kommer alla requests att faila.

**Verifiering:**
- [ ] Testa vad som händer när användare inte är inloggad
- [ ] Verifiera att frontend hanterar detta gracefully (redirect till login?)
- [ ] Överväg om backend ska tillåta vissa read-only endpoints utan token

### Risk 4: Performance Degradation
**Problem:** Extra queries kan påverka prestanda.

**Verifiering:**
- [ ] Mät response times före/efter
- [ ] Överväg caching om nödvändigt
- [ ] Verifiera att `getAllTasks()` och `getAllLists()` inte orsakar N+1 problem

---

## ✅ Godkännande Kriterier

Innan push måste följande vara uppfyllt:

1. ✅ Alla kritiska tester (1-15) har körts och passerat
2. ✅ Alla regression tester (17-19) har körts och passerat
3. ✅ Edge case: Null device token hanteras korrekt
4. ✅ Performance impact är acceptabel (< 50ms)
5. ✅ Error messages är tydliga och användarvänliga
6. ✅ Frontend hanterar 400 errors gracefully
7. ✅ Legacy data fungerar fortfarande
8. ✅ Logging finns för säkerhetshändelser (optional men rekommenderat)

---

## 🎯 Prioriterad Test-Plan

### Måste testas (Kritiskt):
1. **Cross-family access tests** (Test 1-5, 8-11) - Verifiera att säkerhetsfixarna fungerar
2. **Regression tests** (Test 7, 13, 17-18) - Verifiera att inget brutits
3. **Missing token tests** (Test 6, 12) - Verifiera edge cases

### Bör testas (Viktigt):
4. **Edge cases** (Test 19) - Null handling, invalid IDs, etc.
5. **Performance** (Test 20-21) - Verifiera att prestanda är OK

### Kan testas senare (Optional):
6. **Device token collision** (Test 14) - Extremt sällsynt, kan testas med mocks

---

## 📝 Testrapportmall

När du kör testerna, dokumentera resultatet:

```
Test #X: [Test Name]
Status: ✅ PASS / ❌ FAIL
Resultat: [Beskrivning]
Fel (om något): [Error message]
```

---

## 🔍 Ytterligare Verifieringar

### Code Review Checklist
- [ ] Inga N+1 query problems
- [ ] Error messages är tydliga
- [ ] Inga memory leaks
- [ ] Proper exception handling
- [ ] Logging för säkerhetshändelser (optional)

### Security Review
- [ ] Inga SQL injection risks
- [ ] Inga XSS risks
- [ ] Proper input validation
- [ ] Rate limiting övervägt (optional)

# Praktisk Test-Guide: Säkerhetsfixar

## 🎯 Snabböversikt

Detta är en steg-för-steg guide för att testa säkerhetsfixarna innan push. Vi testar att:
1. ✅ Cross-family access blockeras korrekt
2. ✅ Normal funktionalitet fortfarande fungerar
3. ✅ Edge cases hanteras korrekt

---

## 📋 Förberedelser

### Steg 1: Skapa Test-Data

Du behöver två familjer för att testa cross-family access:

**Option A: Använd befintliga familjer**
- Family A: [Din befintliga family]
- Family B: [En annan befintliga family, eller skapa ny]

**Option B: Skapa nya test-familjer**
1. Registrera Family A med email: `test-family-a@example.com`
2. Registrera Family B med email: `test-family-b@example.com`
3. Notera device tokens för båda

### Steg 2: Förbered Test-Data

**I Family A:**
- Skapa minst 1 Daily Task (notera `taskId`)
- Skapa minst 1 Todo List (notera `listId`)

**I Family B:**
- Skapa minst 1 Daily Task (notera `taskId`)
- Skapa minst 1 Todo List (notera `listId`)

### Steg 3: Hämta Device Tokens

**Family A:**
- Device Token: `___________________________`

**Family B:**
- Device Token: `___________________________`

**Family A Task ID:**
- Task ID: `___________________________`

**Family B Task ID:**
- Task ID: `___________________________`

**Family A List ID:**
- List ID: `___________________________`

**Family B List ID:**
- List ID: `___________________________`

---

## 🧪 Test-Session 1: Daily Tasks - Cross-Family Access

### Test 1.1: Update Task - Cross-Family Denied ✅/❌

**Mål:** Verifiera att Family B inte kan uppdatera Family A's task.

**Steg:**
1. Öppna browser developer tools (F12)
2. Gå till Network tab
3. Logga in som Family B
4. Försök uppdatera Family A's task via frontend (eller använd curl/Postman)

**Förväntat resultat:**
- ❌ Request ska faila med 400 Bad Request
- Error message: "Access denied: Task does not belong to your family"

**Curl test (alternativ):**
```bash
curl -X PATCH "http://localhost:8080/api/v1/daily-tasks/{FAMILY_A_TASK_ID}" \
  -H "Content-Type: application/json" \
  -H "X-Device-Token: {FAMILY_B_DEVICE_TOKEN}" \
  -d '{
    "name": "Hacked Task",
    "description": "This should fail",
    "daysOfWeek": ["MONDAY"],
    "isRequired": true,
    "xpPoints": 10
  }'
```

**Resultat:** ✅ PASS / ❌ FAIL
**Noteringar:** _________________________________

---

### Test 1.2: Delete Task - Cross-Family Denied ✅/❌

**Mål:** Verifiera att Family B inte kan radera Family A's task.

**Steg:**
1. Logga in som Family B
2. Försök radera Family A's task via frontend

**Förväntat resultat:**
- ❌ Request ska faila med 400 Bad Request
- Error message: "Access denied: Task does not belong to your family"

**Curl test:**
```bash
curl -X DELETE "http://localhost:8080/api/v1/daily-tasks/{FAMILY_A_TASK_ID}" \
  -H "X-Device-Token: {FAMILY_B_DEVICE_TOKEN}"
```

**Resultat:** ✅ PASS / ❌ FAIL
**Noteringar:** _________________________________

---

### Test 1.3: Toggle Task - Cross-Family Denied ✅/❌

**Mål:** Verifiera att Family B inte kan toggle Family A's task.

**Steg:**
1. Logga in som Family B
2. Försök toggle Family A's task via frontend

**Förväntat resultat:**
- ❌ Request ska faila med 400 Bad Request
- Error message: "Access denied: Task does not belong to your family"

**Curl test:**
```bash
curl -X POST "http://localhost:8080/api/v1/daily-tasks/{FAMILY_A_TASK_ID}/toggle" \
  -H "X-Device-Token: {FAMILY_B_DEVICE_TOKEN}"
```

**Resultat:** ✅ PASS / ❌ FAIL
**Noteringar:** _________________________________

---

### Test 1.4: Reorder Tasks - Cross-Family Denied ✅/❌

**Mål:** Verifiera att Family B inte kan reordera tasks från Family A.

**Steg:**
1. Logga in som Family B
2. Skapa en task i Family B (notera ID)
3. Försök reordera med både Family B's task ID och Family A's task ID

**Förväntat resultat:**
- ❌ Request ska faila med 400 Bad Request
- Error message: "Access denied: Task does not belong to your family"

**Curl test:**
```bash
curl -X POST "http://localhost:8080/api/v1/daily-tasks/reorder" \
  -H "Content-Type: application/json" \
  -H "X-Device-Token: {FAMILY_B_DEVICE_TOKEN}" \
  -d '{
    "taskIds": ["{FAMILY_B_TASK_ID}", "{FAMILY_A_TASK_ID}"]
  }'
```

**Resultat:** ✅ PASS / ❌ FAIL
**Noteringar:** _________________________________

---

## 🧪 Test-Session 2: Todo Lists - Cross-Family Access

### Test 2.1: Update List Name - Cross-Family Denied ✅/❌

**Mål:** Verifiera att Family B inte kan uppdatera Family A's list.

**Steg:**
1. Logga in som Family B
2. Försök uppdatera Family A's list name via frontend

**Förväntat resultat:**
- ❌ Request ska faila med 400 Bad Request
- Error message: "Access denied: List does not belong to your family"

**Curl test:**
```bash
curl -X PATCH "http://localhost:8080/api/v1/todo-lists/{FAMILY_A_LIST_ID}" \
  -H "Content-Type: application/json" \
  -H "X-Device-Token: {FAMILY_B_DEVICE_TOKEN}" \
  -d '{
    "name": "Hacked List"
  }'
```

**Resultat:** ✅ PASS / ❌ FAIL
**Noteringar:** _________________________________

---

### Test 2.2: Delete List - Cross-Family Denied ✅/❌

**Mål:** Verifiera att Family B inte kan radera Family A's list.

**Steg:**
1. Logga in som Family B
2. Försök radera Family A's list via frontend

**Förväntat resultat:**
- ❌ Request ska faila med 400 Bad Request
- Error message: "Access denied: List does not belong to your family"

**Curl test:**
```bash
curl -X DELETE "http://localhost:8080/api/v1/todo-lists/{FAMILY_A_LIST_ID}" \
  -H "X-Device-Token: {FAMILY_B_DEVICE_TOKEN}"
```

**Resultat:** ✅ PASS / ❌ FAIL
**Noteringar:** _________________________________

---

### Test 2.3: Add Item - Cross-Family Denied ✅/❌

**Mål:** Verifiera att Family B inte kan lägga till items i Family A's list.

**Steg:**
1. Logga in som Family B
2. Försök lägga till item i Family A's list via frontend

**Förväntat resultat:**
- ❌ Request ska faila med 400 Bad Request
- Error message: "Access denied: List does not belong to your family"

**Curl test:**
```bash
curl -X POST "http://localhost:8080/api/v1/todo-lists/{FAMILY_A_LIST_ID}/items" \
  -H "Content-Type: application/json" \
  -H "X-Device-Token: {FAMILY_B_DEVICE_TOKEN}" \
  -d '{
    "description": "Hacked item"
  }'
```

**Resultat:** ✅ PASS / ❌ FAIL
**Noteringar:** _________________________________

---

### Test 2.4: Reorder Lists - Cross-Family Denied ✅/❌

**Mål:** Verifiera att Family B inte kan reordera lists från Family A.

**Steg:**
1. Logga in som Family B
2. Skapa en list i Family B (notera ID)
3. Försök reordera med både Family B's list ID och Family A's list ID

**Förväntat resultat:**
- ❌ Request ska faila med 400 Bad Request
- Error message: "Access denied: List does not belong to your family"

**Curl test:**
```bash
curl -X POST "http://localhost:8080/api/v1/todo-lists/reorder" \
  -H "Content-Type: application/json" \
  -H "X-Device-Token: {FAMILY_B_DEVICE_TOKEN}" \
  -d '{
    "listIds": ["{FAMILY_B_LIST_ID}", "{FAMILY_A_LIST_ID}"]
  }'
```

**Resultat:** ✅ PASS / ❌ FAIL
**Noteringar:** _________________________________

---

## 🧪 Test-Session 3: Regression Tests (Normal Operation)

### Test 3.1: Daily Tasks - Normal Operations ✅/❌

**Mål:** Verifiera att normala operationer fortfarande fungerar.

**Steg:**
1. Logga in som Family A
2. Testa alla normala operationer:
   - ✅ Skapa ny task
   - ✅ Uppdatera egen task
   - ✅ Ta bort egen task
   - ✅ Toggle egen task completion
   - ✅ Reordera egna tasks
   - ✅ Hämta tasks för idag
   - ✅ Hämta alla tasks

**Förväntat resultat:**
- ✅ Alla operationer ska fungera normalt
- ✅ Inga errors
- ✅ Data sparas korrekt

**Resultat:** ✅ PASS / ❌ FAIL
**Noteringar:** _________________________________

---

### Test 3.2: Todo Lists - Normal Operations ✅/❌

**Mål:** Verifiera att normala operationer fortfarande fungerar.

**Steg:**
1. Logga in som Family A
2. Testa alla normala operationer:
   - ✅ Skapa ny list
   - ✅ Uppdatera egen list name
   - ✅ Uppdatera egen list color
   - ✅ Uppdatera egen list privacy
   - ✅ Lägga till item i egen list
   - ✅ Toggle item i egen list
   - ✅ Uppdatera item i egen list
   - ✅ Ta bort item från egen list
   - ✅ Clear done items
   - ✅ Reordera items i egen list
   - ✅ Reordera lists
   - ✅ Ta bort egen list

**Förväntat resultat:**
- ✅ Alla operationer ska fungera normalt
- ✅ Inga errors
- ✅ Data sparas korrekt

**Resultat:** ✅ PASS / ❌ FAIL
**Noteringar:** _________________________________

---

## 🧪 Test-Session 4: Edge Cases

### Test 4.1: Missing Device Token ✅/❌

**Mål:** Verifiera att requests utan device token hanteras korrekt.

**Steg:**
1. Försök göra requests utan device token (eller med invalid token)

**Endpoints att testa:**
- `PATCH /api/v1/daily-tasks/{taskId}` (utan token)
- `DELETE /api/v1/daily-tasks/{taskId}` (utan token)
- `POST /api/v1/daily-tasks/{taskId}/toggle` (utan token)
- `PATCH /api/v1/todo-lists/{listId}` (utan token)
- `DELETE /api/v1/todo-lists/{listId}` (utan token)

**Förväntat resultat:**
- ❌ Request ska faila med 400 Bad Request
- Error message: "Device token is required" eller "Invalid device token"

**Resultat:** ✅ PASS / ❌ FAIL
**Noteringar:** _________________________________

---

### Test 4.2: Invalid Task/List ID ✅/❌

**Mål:** Verifiera att invalid IDs hanteras korrekt.

**Steg:**
1. Försök uppdatera/radera en task/list med ett ID som inte finns

**Förväntat resultat:**
- ❌ Request ska faila med 400 Bad Request
- Error message: "Access denied" eller "not found"

**Resultat:** ✅ PASS / ❌ FAIL
**Noteringar:** _________________________________

---

### Test 4.3: Toggle Task - Cross-Family Member ✅/❌

**Mål:** Verifiera att man inte kan toggle task för member från annan family.

**Steg:**
1. Logga in som Family A
2. Hämta en member ID från Family B
3. Försök toggle en task med `?memberId={FAMILY_B_MEMBER_ID}`

**Förväntat resultat:**
- ❌ Request ska faila med 400 Bad Request
- Error message: "Access denied: Member is not in the same family"

**Resultat:** ✅ PASS / ❌ FAIL
**Noteringar:** _________________________________

---

## 📊 Test-Sammanfattning

### Cross-Family Access Tests
- [ ] Test 1.1: Update Task - Cross-Family Denied
- [ ] Test 1.2: Delete Task - Cross-Family Denied
- [ ] Test 1.3: Toggle Task - Cross-Family Denied
- [ ] Test 1.4: Reorder Tasks - Cross-Family Denied
- [ ] Test 2.1: Update List Name - Cross-Family Denied
- [ ] Test 2.2: Delete List - Cross-Family Denied
- [ ] Test 2.3: Add Item - Cross-Family Denied
- [ ] Test 2.4: Reorder Lists - Cross-Family Denied

### Regression Tests
- [ ] Test 3.1: Daily Tasks - Normal Operations
- [ ] Test 3.2: Todo Lists - Normal Operations

### Edge Cases
- [ ] Test 4.1: Missing Device Token
- [ ] Test 4.2: Invalid Task/List ID
- [ ] Test 4.3: Toggle Task - Cross-Family Member

---

## ✅ Godkännande Kriterier

Innan push måste följande vara uppfyllt:

- [ ] **Alla Cross-Family Access Tests (1.1-2.4) har passerat**
- [ ] **Alla Regression Tests (3.1-3.2) har passerat**
- [ ] **Alla Edge Case Tests (4.1-4.3) har passerat**
- [ ] **Inga breaking changes för frontend**
- [ ] **Error messages är tydliga och användarvänliga**

---

## 🚨 Om Något Failar

### Om Cross-Family Access Tests Failar
- **Kritiskt!** Säkerhetsfixarna fungerar inte korrekt
- **Åtgärd:** Granska koden och fixa valideringslogiken
- **Inte pusha** innan detta är fixat

### Om Regression Tests Failar
- **Kritiskt!** Vi har brutit befintlig funktionalitet
- **Åtgärd:** Granska vad som ändrats och fixa
- **Inte pusha** innan detta är fixat

### Om Edge Case Tests Failar
- **Viktigt!** Edge cases hanteras inte korrekt
- **Åtgärd:** Fixa error handling för edge cases
- **Överväg** om detta blockerar push (beroende på allvarlighet)

---

## 💡 Tips för Testning

### Använd Browser Developer Tools
1. Öppna F12 → Network tab
2. Filtrera på "daily-tasks" eller "todo-lists"
3. Kolla response status och error messages

### Använd Postman eller Insomnia
- Skapa en collection med alla test-requests
- Spara device tokens som environment variables
- Enkelt att köra alla tester igen

### Använd Curl (för automatiserade tester)
- Se curl-exemplen ovan
- Skapa ett shell-script för att köra alla tester

### Testa Både Frontend och Backend
- Testa via frontend för att se användarupplevelsen
- Testa via API direkt för att verifiera backend-logiken

---

## 📝 Test-Rapportmall

När du är klar, fyll i denna rapport:

```
Test-Datum: _______________
Testare: _______________

Sammanfattning:
- Totalt antal tester: ___
- Passerade: ___
- Failade: ___

Kritiska Problem:
1. _________________________________
2. _________________________________

Rekommendation:
[ ] Klart att pusha
[ ] Behöver fixas innan push
[ ] Behöver mer testning

Signatur: _______________
```

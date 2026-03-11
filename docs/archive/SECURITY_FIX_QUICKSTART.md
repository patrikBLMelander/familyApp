# Snabbstart: Testa Säkerhetsfixar

## 🚀 Snabbaste Sättet att Testa

### Steg 1: Förbered Test-Data (5 minuter)

1. **Starta applikationen:**
   ```bash
   # Backend
   cd backend && ./mvnw spring-boot:run
   
   # Frontend (i annat terminal)
   cd frontend && npm run dev
   ```

2. **Skapa två test-familjer:**
   - Öppna appen i två olika browsers (eller incognito)
   - Registrera Family A: `test-family-a@example.com`
   - Registrera Family B: `test-family-b@example.com`
   - Notera device tokens från localStorage:
     ```javascript
     // I browser console:
     localStorage.getItem('deviceToken')
     ```

3. **Skapa test-data:**
   - I Family A: Skapa 1 Daily Task och 1 Todo List
   - I Family B: Skapa 1 Daily Task och 1 Todo List
   - Notera ID:n från Network tab eller browser console

---

### Steg 2: Testa via Browser (Rekommenderat - 10 minuter)

**Detta är det enklaste sättet att testa!**

1. **Öppna Browser Developer Tools (F12)**
   - Gå till Network tab
   - Filtrera på "daily-tasks" eller "todo-lists"

2. **Testa Cross-Family Access:**
   - Logga in som Family B
   - Försök uppdatera/radera Family A's task eller list
   - **Förväntat:** Request ska faila med 400 Bad Request
   - **Kolla response:** Ska innehålla "Access denied"

3. **Testa Normal Operation:**
   - Logga in som Family A
   - Testa att skapa/uppdatera/radera egna tasks och lists
   - **Förväntat:** Allt ska fungera normalt

---

### Steg 3: Testa via Script (Alternativ - 5 minuter)

Om du vill automatisera testerna:

```bash
# Sätt environment variables
export FAMILY_A_TOKEN="your-family-a-device-token"
export FAMILY_B_TOKEN="your-family-b-device-token"
export FAMILY_A_TASK_ID="task-id-from-family-a"
export FAMILY_A_LIST_ID="list-id-from-family-a"
export API_BASE_URL="http://localhost:8080/api/v1"  # Optional

# Kör test-scriptet
./test_security_fixes.sh
```

---

## ✅ Minimal Test-Set (Måste testas)

Om du har begränsat med tid, testa minst dessa:

### 1. Cross-Family Access Test (2 minuter)
- [ ] Logga in som Family B
- [ ] Försök uppdatera Family A's task → Ska faila
- [ ] Försök radera Family A's list → Ska faila

### 2. Normal Operation Test (2 minuter)
- [ ] Logga in som Family A
- [ ] Skapa ny task → Ska fungera
- [ ] Uppdatera egen task → Ska fungera
- [ ] Skapa ny list → Ska fungera
- [ ] Uppdatera egen list → Ska fungera

### 3. Missing Token Test (1 minut)
- [ ] Öppna Network tab
- [ ] Gör en request utan device token (eller med invalid token)
- [ ] Ska faila med "Device token is required"

**Totalt: ~5 minuter**

---

## 📋 Fullständig Test-Lista

Se `SECURITY_FIX_TESTING_GUIDE.md` för komplett test-guide med alla tester.

---

## 🐛 Om Något Failar

### Problem: Cross-Family Access fungerar inte
- **Symptom:** Family B kan fortfarande modifiera Family A's data
- **Åtgärd:** Granska backend-koden, verifiera att validering körs
- **Inte pusha** innan detta är fixat

### Problem: Normal operation fungerar inte
- **Symptom:** Man kan inte längre modifiera egna tasks/lists
- **Åtgärd:** Granska backend-koden, verifiera att validering inte blockerar legitima requests
- **Inte pusha** innan detta är fixat

### Problem: Frontend visar felmeddelanden
- **Symptom:** Felmeddelanden är otydliga eller saknas
- **Åtgärd:** Verifiera att frontend hanterar 400 errors korrekt
- **Överväg** om detta blockerar push (beroende på allvarlighet)

---

## 💡 Tips

### Hitta Device Tokens
```javascript
// I browser console (när inloggad):
localStorage.getItem('deviceToken')
```

### Hitta Task/List IDs
1. Öppna Network tab (F12)
2. Gör en request (t.ex. hämta tasks)
3. Kolla response JSON för ID:n

### Testa via Browser Console
```javascript
// Testa cross-family access direkt från console:
fetch('http://localhost:8080/api/v1/daily-tasks/{FAMILY_A_TASK_ID}', {
  method: 'PATCH',
  headers: {
    'Content-Type': 'application/json',
    'X-Device-Token': '{FAMILY_B_DEVICE_TOKEN}'
  },
  body: JSON.stringify({
    name: 'Hacked Task',
    description: 'Test',
    daysOfWeek: ['MONDAY'],
    isRequired: true,
    xpPoints: 10
  })
})
.then(r => r.json())
.then(console.log)
.catch(console.error)
```

---

## ✅ Godkännande Checklista

Innan push, verifiera:

- [ ] **Cross-family access blockeras** (Family B kan inte modifiera Family A's data)
- [ ] **Normal operation fungerar** (Man kan modifiera egna data)
- [ ] **Missing token hanteras** (Requests utan token failar korrekt)
- [ ] **Error messages är tydliga** (Användaren förstår vad som gick fel)
- [ ] **Inga breaking changes** (Befintlig funktionalitet fungerar fortfarande)

---

## 📞 Hjälp

Om du stöter på problem:
1. Kolla `SECURITY_FIX_TESTING_GUIDE.md` för detaljerade test-steg
2. Kolla `SECURITY_ANALYSIS.md` för förklaring av problemet
3. Kolla backend logs för error messages

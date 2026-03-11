# Riskanalys: Säkerhetsfixar

## 🔴 KRITISK RISK: DailyTaskController tillåter requests utan token!

### Problem
I `DailyTaskController.updateTask()` och `deleteTask()`:

```java
UUID requesterFamilyId = null;
if (deviceToken != null && !deviceToken.isEmpty()) {
    // ... get familyId
}

// Verify task belongs to requester's family
if (requesterFamilyId != null) {  // <-- PROBLEM: Om null, hoppas valideringen över!
    // ... validation
}
```

**Konsekvens:** Om någon gör en request **utan device token**, hoppas valideringen över och de kan fortfarande modifiera tasks!

### Åtgärd
**MÅSTE FIXAS INNAN PUSH!**

Lägg till krav på device token i DailyTaskController, precis som i TodoListController:

```java
if (deviceToken == null || deviceToken.isEmpty()) {
    throw new IllegalArgumentException("Device token is required");
}
```

---

## 🟡 MEDEL RISK: Breaking Change i TodoListController

### Problem
`TodoListController.validateListAccess()` kräver nu device token:

```java
if (deviceToken == null || deviceToken.isEmpty()) {
    throw new IllegalArgumentException("Device token is required");
}
```

**Konsekvens:** Om frontend någon gång inte skickar token (t.ex. vid edge cases), kommer requests att faila.

### Verifiering
✅ **GOOD NEWS:** Vi har verifierat att frontend alltid skickar deviceToken via `getHeaders()`, så detta bör inte vara ett problem.

### Åtgärd
- ✅ Verifierat att frontend alltid skickar token
- ⚠️ Överväg att testa edge cases där token kan saknas

---

## 🟡 MEDEL RISK: Frontend Error Handling

### Problem
När backend kastar `IllegalArgumentException` med "Access denied", kommer frontend att:
1. Få 400 Bad Request
2. `handleJson()` kastar Error med meddelandet
3. Komponenter måste catcha och hantera detta

### Verifiering
✅ **GOOD NEWS:** De flesta komponenter har try/catch:
- `TodoListsView` har error handling
- `FamilyMembersView` har error handling
- `ChildDashboard` har error handling

### Åtgärd
- ✅ Verifierat att komponenter hanterar errors
- ⚠️ Överväg att testa att error messages visas korrekt för användaren

---

## 🟢 LÅG RISK: Performance

### Problem
Extra queries för validering kan påverka prestanda:
- `getAllTasks(requesterFamilyId)` anropas för varje update/delete
- `getAllLists(requester.id(), requesterFamilyId)` anropas för varje operation

### Verifiering
- Queries är optimerade (använder familyId direkt)
- Antal queries är rimligt (1 extra query per operation)

### Åtgärd
- ✅ Queries är optimerade
- ⚠️ Överväg att mäta response times i produktion

---

## 📊 Risk-Sammanfattning

| Risk | Allvarlighet | Sannolikhet | Status |
|------|--------------|-------------|--------|
| DailyTaskController tillåter requests utan token | 🔴 KRITISK | Hög | ⚠️ MÅSTE FIXAS |
| Breaking change i TodoListController | 🟡 MEDEL | Låg | ✅ Verifierat |
| Frontend error handling | 🟡 MEDEL | Låg | ✅ Verifierat |
| Performance degradation | 🟢 LÅG | Låg | ✅ Acceptabelt |

---

## ✅ Rekommendationer

### Innan Push:

1. **🔴 KRITISKT: Fixa DailyTaskController**
   - Lägg till krav på device token i `updateTask()`, `deleteTask()`, `toggleTaskCompletion()`, `reorderTasks()`
   - Se TodoListController som exempel

2. **🟡 Testa Edge Cases**
   - Testa requests utan token
   - Testa requests med invalid token
   - Verifiera att error messages visas korrekt

3. **🟢 Performance Monitoring**
   - Överväg att mäta response times efter push
   - Överväg att lägga till logging för säkerhetshändelser

---

## 🎯 Slutsats

**Huvudrisken är att DailyTaskController tillåter requests utan token!**

Detta måste fixas innan push, annars är säkerhetsfixarna ofullständiga.

Efter fix:
- ✅ Säkerhetsfixarna fungerar korrekt
- ✅ Breaking changes är minimala (frontend skickar alltid token)
- ✅ Error handling fungerar
- ✅ Performance är acceptabel

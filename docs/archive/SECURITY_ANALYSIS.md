# Säkerhetsanalys: Cross-Family Access Problem

## 🔴 Problem Identifierat

**JA, det finns ett säkerhetsproblem!** Olika familjer KAN komma åt och modifiera varandras data om de känner till ID:n.

## Detaljerad Analys

### DailyTaskController (FÖRE säkerhetsfixarna)

#### `updateTask(taskId, ...)`
```java
// INGEN validering av family!
var task = service.updateTask(taskId, ...);
```

**Service-lagret:**
```java
public DailyTask updateTask(UUID taskId, ...) {
    var entity = taskRepository.findById(taskId)  // <-- Hittar task direkt, ingen family-check!
        .orElseThrow(() -> new IllegalArgumentException("Daily task not found: " + taskId));
    // Uppdaterar tasken utan att kolla family
}
```

**Sårbarhet:** Om någon känner till ett `taskId` från en annan family, kan de uppdatera den tasken.

#### `deleteTask(taskId)`
```java
// INGEN validering av family!
public void deleteTask(@PathVariable("taskId") UUID taskId) {
    service.deleteTask(taskId);
}
```

**Service-lagret:**
```java
public void deleteTask(UUID taskId) {
    taskRepository.deleteById(taskId);  // <-- Raderar direkt, ingen family-check!
}
```

**Sårbarhet:** Om någon känner till ett `taskId` från en annan family, kan de radera den tasken.

#### `toggleTaskCompletion(taskId, ...)`
```java
// INGEN validering att task tillhör samma family!
service.toggleTaskCompletion(taskId, memberId);
```

**Sårbarhet:** Om någon känner till ett `taskId` från en annan family, kan de toggle completion för den tasken.

#### `reorderTasks(taskIds)`
```java
// INGEN validering att alla tasks tillhör samma family!
public List<DailyTaskResponse> reorderTasks(@RequestBody ReorderTasksRequest request) {
    return service.reorderTasks(request.taskIds())...
}
```

**Sårbarhet:** Om någon känner till `taskId`s från andra familjer, kan de inkludera dem i reorder-requesten.

---

### TodoListController (FÖRE säkerhetsfixarna)

#### `updateListName(listId, ...)`
```java
// INGEN validering av family!
public TodoListResponse updateListName(@PathVariable("listId") UUID listId, ...) {
    var list = service.updateListName(listId, request.name());
}
```

**Service-lagret:**
```java
public TodoList updateListName(UUID listId, String name) {
    var list = listRepository.findById(listId)  // <-- Hittar list direkt, ingen family-check!
        .orElseThrow(() -> new IllegalArgumentException("Todo list not found: " + listId));
    // Uppdaterar listan utan att kolla family
}
```

**Sårbarhet:** Om någon känner till ett `listId` från en annan family, kan de uppdatera den listan.

#### `deleteList(listId)`
```java
// INGEN validering av family!
public void deleteList(@PathVariable("listId") UUID listId) {
    service.deleteList(listId);
}
```

**Service-lagret:**
```java
public void deleteList(UUID listId) {
    var list = listRepository.findById(listId)  // <-- Hittar list direkt, ingen family-check!
        .orElseThrow(() -> new IllegalArgumentException("Todo list not found: " + listId));
    listRepository.delete(list);
}
```

**Sårbarhet:** Om någon känner till ett `listId` från en annan family, kan de radera den listan.

#### Alla andra TodoList endpoints
- `updateListColor(listId, ...)` - Samma problem
- `updateListPrivacy(listId, ...)` - Samma problem
- `addItem(listId, ...)` - Samma problem
- `toggleItem(listId, itemId, ...)` - Samma problem
- `updateItem(listId, itemId, ...)` - Samma problem
- `deleteItem(listId, itemId, ...)` - Samma problem
- `clearDone(listId, ...)` - Samma problem
- `reorderItems(listId, ...)` - Samma problem
- `reorderLists(listIds, ...)` - Samma problem

---

## Hur Stort är Problemet?

### Praktisk Risk
**Medelhög risk** - Det krävs att någon:
1. Känner till ett ID från en annan family (UUIDs är svåra att gissa, men inte omöjliga)
2. Har tillgång till API:et (vilket alla inloggade användare har)
3. Vet vilka endpoints som finns

### Attack Vektorer
1. **UUID Gissning:** UUIDs är 128-bit, så sannolikheten är extremt låg, men inte noll
2. **Information Leakage:** Om IDs exponeras någonstans (logs, errors, etc.)
3. **Social Engineering:** Om någon får tag på ett ID från en annan family
4. **Brute Force:** Teoretiskt möjligt men opraktiskt (2^128 möjligheter)

### Vad Skyddar Mot Det Nu?
**Ingenting på backend-nivå!** Enda skyddet är:
- Frontend visar bara tasks/lists från egen family (men detta är "security by obscurity")
- UUIDs är svåra att gissa (men inte omöjliga)

---

## Vad Säkerhetsfixarna Gör

### DailyTaskController (EFTER säkerhetsfixarna)

#### `updateTask(taskId, ...)`
```java
// VALIDERAR att task tillhör samma family!
UUID requesterFamilyId = getFamilyFromToken(deviceToken);
var allTasks = service.getAllTasks(requesterFamilyId);
var task = allTasks.stream()
    .filter(t -> t.id().equals(taskId))
    .findFirst();
if (task.isEmpty()) {
    throw new IllegalArgumentException("Access denied: Task does not belong to your family");
}
```

**Resultat:** Om task inte tillhör requester's family, kastas exception.

#### `deleteTask(taskId, ...)`
```java
// VALIDERAR att task tillhör samma family!
// Samma validering som updateTask
```

#### `toggleTaskCompletion(taskId, ...)`
```java
// VALIDERAR att både task OCH member tillhör samma family!
if (memberIdParam != null) {
    var targetMember = memberService.getMemberById(memberIdParam);
    if (!requesterFamilyId.equals(targetMember.familyId())) {
        throw new IllegalArgumentException("Access denied: Member is not in the same family");
    }
}
```

#### `reorderTasks(taskIds, ...)`
```java
// VALIDERAR att alla tasks tillhör samma family!
for (UUID taskId : request.taskIds()) {
    if (!taskIds.contains(taskId)) {
        throw new IllegalArgumentException("Access denied: Task does not belong to your family");
    }
}
```

### TodoListController (EFTER säkerhetsfixarna)

#### Alla endpoints
```java
// VALIDERAR att list tillhör samma family!
private void validateListAccess(UUID listId, String deviceToken) {
    var requester = memberService.getMemberByDeviceToken(deviceToken);
    UUID requesterFamilyId = requester.familyId();
    
    var list = service.getAllLists(requester.id(), requesterFamilyId).stream()
        .filter(l -> l.id().equals(listId))
        .findFirst();
    
    if (list.isEmpty()) {
        throw new IllegalArgumentException("Access denied: List does not belong to your family");
    }
}
```

---

## Sammanfattning

### FÖRE Säkerhetsfixarna
- ❌ Ingen validering av family i update/delete/toggle/reorder endpoints
- ❌ Om någon känner till ett ID från en annan family, kan de modifiera/radera det
- ⚠️ Enda skyddet är "security by obscurity" (UUIDs är svåra att gissa)

### EFTER Säkerhetsfixarna
- ✅ Alla endpoints validerar att resource tillhör samma family
- ✅ Cross-family access nekas med tydligt felmeddelande
- ✅ Säkerhet på backend-nivå, inte bara frontend

### Slutsats
**JA, det fanns ett säkerhetsproblem, och säkerhetsfixarna löser det!**

Problemet var inte kritiskt (UUIDs är svåra att gissa), men det var definitivt en säkerhetslucka som borde fixas. Säkerhetsfixarna gör systemet mycket säkrare.

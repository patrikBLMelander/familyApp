# Säkerhetsfix - Familjemedlemmar Cross-Family Access

**Datum:** 2026-01-27  
**Prioritet:** KRITISK  
**Status:** FIXAD

## Problem

Under manuell testning identifierades kritiska säkerhetsbuggar:

1. **Familjemedlemmar:** Family 2 kunde se Family 1's medlemmar
2. **Kalender events:** När Family 2 skapade events kunde de se Family 1's medlemmar i deltagarlistan
3. **Gammalt konto:** Även det gamla kontot "Melander" kunde se Family 1's medlemmar

## Rotorsak

Problemet låg i cache-hanteringen i `FamilyMemberService.getAllMembers()`:

```java
@Cacheable(value = "familyMembers", key = "#familyId != null ? #familyId.toString() : 'all'")
```

När `familyId` var `null` användes cache-nyckeln `'all'`, vilket betydde att:
- Alla familjers medlemmar kunde cachas tillsammans
- Om cache returnerade data för `'all'`-nyckeln kunde alla familjer se alla medlemmar
- Detta var en kritisk säkerhetsbugg som tillät cross-family data exposure

## Lösning

### 1. Fixat cache-nyckel i `FamilyMemberService.getAllMembers()`

**Före:**
```java
@Cacheable(value = "familyMembers", key = "#familyId != null ? #familyId.toString() : 'all'")
public List<FamilyMember> getAllMembers(UUID familyId) {
    if (familyId != null) {
        return repository.findByFamilyIdOrderByNameAsc(familyId)...
    } else {
        return repository.findAll()... // Farligt!
    }
}
```

**Efter:**
```java
@Cacheable(value = "familyMembers", key = "#familyId.toString()", unless = "#familyId == null")
public List<FamilyMember> getAllMembers(UUID familyId) {
    // SECURITY: familyId must never be null - this would expose all families' members
    if (familyId == null) {
        log.error("CRITICAL SECURITY ISSUE: getAllMembers called with null familyId - returning empty list");
        return List.of();
    }
    
    return repository.findByFamilyIdOrderByNameAsc(familyId)...
}
```

### 2. Förbättrad validering i `FamilyMemberController.getAllMembers()`

**Före:**
```java
UUID familyId = null;
if (deviceToken != null && !deviceToken.isEmpty()) {
    try {
        var member = service.getMemberByDeviceToken(deviceToken);
        familyId = member.familyId();
    } catch (IllegalArgumentException e) {
        return List.of();
    }
} else {
    return List.of();
}
```

**Efter:**
```java
// SECURITY: Device token is required - no token means no access
if (deviceToken == null || deviceToken.isEmpty()) {
    return List.of();
}

UUID familyId;
try {
    var member = service.getMemberByDeviceToken(deviceToken);
    familyId = member.familyId();
    
    // SECURITY: Double-check that familyId is not null
    if (familyId == null) {
        throw new IllegalArgumentException("Member has no family ID");
    }
} catch (IllegalArgumentException e) {
    return List.of();
}

// SECURITY: familyId is guaranteed to be non-null at this point
return service.getAllMembers(familyId)...
```

### 3. Fixat `CacheService.evictFamilyMembers()` och `putFamilyMembers()`

**Före:**
```java
public void putFamilyMembers(UUID familyId, List<FamilyMember> members) {
    put("familyMembers", familyId != null ? familyId.toString() : "all", members);
}

public void evictFamilyMembers(UUID familyId) {
    String key = familyId != null ? familyId.toString() : "all";
    evict("familyMembers", key);
    if (familyId != null) {
        evict("familyMembers", "all"); // Farligt!
    }
}
```

**Efter:**
```java
public void putFamilyMembers(UUID familyId, List<FamilyMember> members) {
    if (familyId == null) {
        log.warn("SECURITY: Attempted to cache family members with null familyId - ignoring");
        return;
    }
    put("familyMembers", familyId.toString(), members);
}

public void evictFamilyMembers(UUID familyId) {
    if (familyId == null) {
        log.warn("SECURITY: Attempted to evict family members cache with null familyId - ignoring");
        return;
    }
    evict("familyMembers", familyId.toString());
}
```

## Testning

### Manuell testning krävs:

1. **Testa familjemedlemmar isolering:**
   - Registrera Family 1 → Lägg till medlem "Member1"
   - Öppna inkognito → Registrera Family 2
   - Gå till Familjemedlemmar
   - **VERIFIERA:** Family 2 ska INTE se "Member1"

2. **Testa kalender events deltagare:**
   - Family 1: Lägg till medlem "Member1"
   - Family 2: Gå till Kalender → Skapa event
   - **VERIFIERA:** Family 2 ska INTE se "Member1" i deltagarlistan

3. **Testa cache:**
   - Family 1: Lägg till medlem
   - Family 2: Gå till Familjemedlemmar
   - **VERIFIERA:** Family 2 ska INTE se Family 1's medlemmar
   - Upprepa flera gånger för att testa cache

## Filer Ändrade

1. `backend/src/main/java/com/familyapp/application/familymember/FamilyMemberService.java`
   - Fixat cache-nyckel för `getAllMembers()`
   - Lagt till säkerhetsvalidering för `null` familyId

2. `backend/src/main/java/com/familyapp/api/familymember/FamilyMemberController.java`
   - Förbättrad validering i `getAllMembers()`
   - Säkerställt att `familyId` aldrig är `null`

3. `backend/src/main/java/com/familyapp/application/cache/CacheService.java`
   - Fixat `putFamilyMembers()` och `evictFamilyMembers()`
   - Tog bort farlig `'all'` cache-nyckel

## Verifiering

Efter dessa ändringar:
- ✅ `familyId` kan aldrig vara `null` när `getAllMembers()` anropas
- ✅ Cache använder alltid `familyId.toString()` som nyckel (aldrig `'all'`)
- ✅ Om `familyId` är `null` returneras tom lista istället för alla medlemmar
- ✅ Loggning läggs till för att identifiera säkerhetsproblem

## Nästa Steg

1. ✅ Kompilera och verifiera att inga fel uppstår
2. ⏳ Manuell testning för att verifiera fixen
3. ⏳ Testa med flera familjer samtidigt
4. ⏳ Verifiera att cache fungerar korrekt

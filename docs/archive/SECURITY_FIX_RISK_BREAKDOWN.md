# Risk-Breakdown: Säkerhetsfixar

## Frågan: Vilken typ av risk finns?

**Två huvudtyper av risk:**
1. **Breaking changes** - Gammal funktionalitet slutar fungera
2. **Säkerhetsfixarna fungerar inte** - Säkerheten vi implementerat har buggar

---

## 🔴 HUVUDRISK: Säkerhetsfixarna fungerar inte korrekt

### Problem: DailyTaskController tillåter requests utan token

**Vad händer:**
- Om `deviceToken` saknas → `requesterFamilyId` blir `null`
- Om `requesterFamilyId` är `null` → valideringen hoppas över (rad 108: `if (requesterFamilyId != null)`)
- **Resultat:** Requests utan token kan fortfarande modifiera tasks!

**Allvarlighet:** 🔴 KRITISK
- Säkerhetsfixarna är ofullständiga
- Cross-family access är fortfarande möjligt via requests utan token

**Sannolikhet:** Medel
- Kräver att någon gör request utan token (vilket inte är normalt, men möjligt)

---

## 🟡 SEKUNDÄR RISK: Breaking Change (gammal funktionalitet slutar fungera)

### Scenario 1: Användare inte inloggad (localStorage saknar token)

**Vad händer:**
- `localStorage.getItem("deviceToken")` returnerar `null`
- `getHeaders()` lägger INTE till `X-Device-Token` header
- **TodoListController:** Kräver token → kastar exception → request failar
- **DailyTaskController:** Tillåter request utan token → går igenom (men validerar inte)

**Allvarlighet:** 🟡 MEDEL
- Om användare inte är inloggad, kan de inte modifiera todo lists
- Men de kan fortfarande modifiera daily tasks (säkerhetslucka!)

**Sannolikhet:** Låg
- Normalt sett är användare alltid inloggade när de använder appen
- Men kan hända vid edge cases (t.ex. token expired, localStorage cleared)

### Scenario 2: Invalid eller expired token

**Vad händer:**
- Token finns i localStorage men är invalid/expired
- Backend kastar `IllegalArgumentException("Invalid device token")`
- Frontend får 400 Bad Request
- Komponenter måste hantera detta

**Allvarlighet:** 🟢 LÅG
- Frontend har error handling
- Användare får felmeddelande och kan logga in igen

**Sannolikhet:** Låg
- Tokens är inte time-based (de expirerar inte)
- Invalid tokens är ovanliga

---

## 📊 Risk-Jämförelse

| Risk | Typ | Allvarlighet | Sannolikhet | Konsekvens |
|------|-----|-------------|-------------|------------|
| DailyTaskController tillåter requests utan token | Säkerhet | 🔴 KRITISK | Medel | Säkerhetslucka kvarstår |
| TodoListController kräver token (breaking change) | Breaking | 🟡 MEDEL | Låg | Användare utan token kan inte modifiera lists |
| Invalid token hantering | Breaking | 🟢 LÅG | Låg | Error message visas, användare kan logga in igen |

---

## ✅ Svar på din fråga

### **Huvudrisk: Säkerhetsfixarna fungerar inte korrekt**

**DailyTaskController-problemet:**
- Säkerhetsfixarna är ofullständiga
- Requests utan token kan fortfarande modifiera tasks
- Cross-family access är fortfarande möjligt (via requests utan token)

### **Sekundär risk: Breaking change**

**TodoListController:**
- Kräver nu device token
- Om användare inte är inloggad (saknar token) → requests failar
- Men detta är ovanligt (användare är normalt inloggade)

**DailyTaskController:**
- Tillåter requests utan token (säkerhetslucka)
- Men detta är också ett problem, inte bara breaking change

---

## 🎯 Slutsats

**Huvudrisken är att säkerhetsfixarna inte fungerar korrekt**, inte att gammal funktionalitet slutar fungera.

**Breaking changes är minimala:**
- Frontend skickar alltid token när användare är inloggad
- Edge case: Om användare inte är inloggad → TodoList requests failar (men detta är förväntat beteende)

**Säkerhetsproblemet är större:**
- DailyTaskController måste fixas för att säkerhetsfixarna ska fungera korrekt
- Annars finns det fortfarande en säkerhetslucka

---

## 💡 Rekommendation

**Fixa DailyTaskController innan push:**
- Lägg till krav på device token
- Då fungerar säkerhetsfixarna korrekt
- Breaking changes blir minimala (endast edge case där användare inte är inloggad)

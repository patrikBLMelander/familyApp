# Plan: Android-app (Google Play) – KidQuest

**App-namn:** KidQuest  
**Projekt-sökväg:** `android/KidQuest/` (i detta repo)

**Syfte:** Återskapa kärnan av webappen som en Android-app för Google Play. Fokus: barnvyn + vuxenfunktioner (konto, barn, uppgifter, barnens ekonomi).

**Datum:** 2026-02-24

---

## 1. Omfattning

### 1.1 Vad som INGÅR i Android-appen

| Användare | Funktionalitet |
|-----------|----------------|
| **Vuxen** | Skapa konto (familj + admin), logga in (e-post/lösenord), lägga till barn, skapa/redigera **uppgifter att göra** (dagens sysslor), hantera **barnens ekonomi** (ge pengar, ev. sparmål/transaktioner). |
| **Barn** | **Barnvy:** Dagens uppgifter (kryssa av), husdjur + XP (mata djuret), plånbok (saldo, notiser, registrera utgift). |

### 1.2 Vad som UTGÅR (i första versionen)

- Kalender (events) – kan läggas till senare om ni vill.
- ToDo-listor (separata listor) – kan läggas till senare.
- Menscykel, barnens djuroversikt (ChildrenXpView), övriga vuxenvyer.
- PWA/installerbara webappen – Android-appen ersätter behovet på mobil.

### 1.3 Teknisk strategi

- **Samma backend:** Befintlig Java/Spring Boot API (`/api/v1`) används oförändrad.
- **Auth:** Samma som webben: device token (sparas säkert på enheten) och för vuxna e-post + lösenord. Android skickar `X-Device-Token` (eller motsvarande) vid anrop.
- **En klient:** Android-appen är en ny klient; ingen ändring i backend behövs för grundflödena (eventuellt små tillägg för t.ex. push-notiser senare).

---

## 2. Teknikval: Native Android (rekommenderat)

- **Språk:** Kotlin  
- **UI:** Jetpack Compose (modernt, deklarativt)  
- **Arkitektur:** MVVM eller MVI, med Repository-lager som anropar backend  
- **Nätverk:** Retrofit (REST) + OkHttp  
- **Auth-lagring:** EncryptedSharedPreferences eller DataStore (för device token / session)  
- **Bygg:** Gradle (Kotlin DSL), minSdk 24+, targetSdk 34  

**Alternativ:** React Native om du vill återanvända TypeScript/React och eventuellt delad kod med webben; då blir planens faser lika, men implementationen i React Native-komponenter istället för Compose.

---

## 3. Faser – översikt

| Fas | Innehåll | Resultat |
|-----|----------|----------|
| **0** | Miljö + projekt | Android Studio, repo, app som startar och kan anropa backend (t.ex. health). |
| **1** | Auth (vuxen) | Registrering, inloggning (e-post/lösenord), spara token, enkel “är jag inloggad”-kontroll. |
| **2** | Vuxen: Familj och barn | Hämta familj, lista barn, **lägga till barn**. |
| **3** | Vuxen: Uppgifter | Hämta/skapa/redigera “dagens uppgifter” (samma API som webben: calendar tasks och/eller daily tasks – välj en modell). |
| **4** | Vuxen: Barnens ekonomi | Ge pengar till barn, visa saldon/transaktioner (ev. sparmål). |
| **5** | Barnvy | Växling barn/vuxen (eller separat inloggning för barn via device token), barn-dashboard: uppgifter, djur, plånbok. |
| **6** | Polish + Play Store | Signering, ikon, splash, integritetstext, Play Console, första uppladdning. |

---

## 4. Fas 0: Miljö och projekt

### 4.1 Krav

- **Android Studio** (senaste stabila, t.ex. Ladybug eller nyare).  
- **JDK 17.**  
- **Git:** projektet under version control (redan familyApp-repo).

### 4.2 Skapa projekt

- Nytt Android-projekt i Android Studio: “Empty Activity” med **Compose**.  
- Språk: **Kotlin**, minSdk **24**, targetSdk **34**.  
- Placera projektet i samma repo under **`android/KidQuest/`** (projektets rot = KidQuest-mappen).

### 4.3 Konfiguration

- **Base URL för API:** Sätt i `BuildConfig` eller en config-fil (t.ex. `https://backend-production-5c57.up.railway.app/api/v1` för prod). Ha en variant för lokal utveckling (t.ex. `http://10.0.2.2:8080/api/v1` i emulator).  
- **Retrofit + OkHttp:**  
  - Bas-URL från config.  
  - Header `X-Device-Token` (eller det namn backend använder) sätts från lagrad token om användaren är inloggad.  
- En enkel **splash/startskärm** som antingen går till inloggning eller till huvudflöde om token finns.

### 4.4 Verifiering

- En minimal **“health” eller “families/me”**-anrop (om ni har sådan endpoint) eller ett enkelt GET mot en icke-skyddad endpoint för att verifiera att nätverket och URL fungerar från appen.

---

## 5. Fas 1: Autentisering (vuxen)

### 5.1 Flöden

- **Registrering:**  
  - Input: familjens namn, admin namn, e-post, lösenord.  
  - Anrop: `POST /families/register` (samma som webben).  
  - Spara `deviceToken` från svaret säkert (EncryptedSharedPreferences/DataStore).  
- **Inloggning:**  
  - Input: e-post + lösenord.  
  - Anrop: `POST /families/login-by-email` (eller motsvarande).  
  - Spara `deviceToken`.  
- **“Är jag inloggad?”:** Om lagrad token finns, anropa t.ex. `GET /family-members/by-device-token/{token}`. Vid 200 → vuxen/barn-flöde; vid 401 → inloggningsskärm.

### 5.2 UI

- En skärm med två “flikar” eller knappar: **Registrera** / **Logga in**.  
- Formulär för respektive flöde.  
- Felmeddelanden från backend visas (t.ex. “Felaktigt lösenord”).  
- Efter lyckad registrering/inloggning: navigera till vuxenens startsida (t.ex. “Hem” eller “Mina barn”).

### 5.3 Säkerhet

- Spara endast token; skicka aldrig lösenord i klartext efter inloggning.  
- Använd HTTPS i produktion (redan så på backend).

---

## 6. Fas 2: Vuxen – Familj och barn

### 6.1 API (befintligt)

- Hämta medlemmar: t.ex. `GET /family-members` (filtrera på familj om backend kräver familyId).  
- Skapa barn: `POST /family-members` med namn och roll CHILD, kopplat till aktuell familj (familyId från inloggad användare).

### 6.2 UI

- **Startsida vuxen:**  
  - Visa familjens namn (hämtat från `GET /families/{id}` eller från medlemsdata).  
  - Lista **barn** (medlemmar med roll CHILD).  
  - Knapp: **“Lägg till barn”**.  
- **Lägg till barn:** Dialog eller egen skärm med namn; vid sparning anropa POST och uppdatera listan.

### 6.3 Barn och device token (för barnvy senare)

- I webappen loggar barn in med **device token** (genereras/länkas till barnet).  
- I Android kan ni antingen:  
  - **A)** Låta vuxen “växla till barnvy” genom att välja barn i listan (appen anropar då API som barn genom att temporärt använda barnets token – kräver att backend stödjer att hämta/länka token för barn), eller  
  - **B)** Barnet loggar in på en egen enhet med sin device token (som vuxen har visat/gett från webben).  
- För enkelhetens skull: **Fas 5** kan implementera “Vuxen väljer barn → visa barnvy för det barnet” om backend tillåter att vuxen hämtar barnets data (t.ex. tasks, pet, wallet) via memberId; annars barn-inloggning med device token.

---

## 7. Fas 3: Vuxen – Uppgifter att göra

### 7.1 API (befintligt)

- Webappen använder både **calendar events med task-completion** och **daily tasks**.  
- För “dagens uppgifter” för barn:  
  - Kalender: `GET /calendar/members/{memberId}/task-completions` (dagens), `POST/PUT /calendar/events` för att skapa uppgifter, `POST /calendar/events/{eventId}/task-completion` för att markera.  
  - Eller daily tasks: `GET /daily-tasks/today?memberId=...`, `POST /daily-tasks`, toggle m.m.  
- **Rekommendation:** Välj en modell (t.ex. bara calendar-baserade “dagens uppgifter”) och implementera den i appen så att vuxen kan skapa uppgifter kopplade till barn och dag.

### 7.2 UI

- **“Att göra” / “Uppgifter”:**  
  - Lista dagens uppgifter per barn (eller gemensam lista med barn som etikett).  
  - Knapp “Lägg till uppgift”: titel, val av barn, ev. datum/återkommande.  
  - Redigera/ta bort om API stödjer det.  
- Barn kan sedan i **barnvy** (Fas 5) bara se och bocka av sina egna uppgifter.

---

## 8. Fas 4: Vuxen – Barnens ekonomi

### 8.1 API (befintligt)

- Ge pengar: `POST /wallet/allowance` (childMemberId, amount, description, givenByMemberId, ev. savings goal allocations).  
- Saldo för barn: `GET /wallet/members/{memberId}/balance`.  
- Transaktioner: `GET /wallet/members/{memberId}/transactions?limit=...`.  
- Sparmål: `GET /wallet/members/{memberId}/savings-goals/active`, ev. skapa/uppdatera.

### 8.2 UI

- **“Barnens ekonomi” / “Plånböcker”:**  
  - En kort/rad per barn med namn, saldo, knapp **“Ge pengar”**.  
  - “Ge pengar”: dialog med belopp, beskrivning, ev. fördelning till sparmål.  
  - Klicka på barn → detaljvy: saldo, senaste transaktioner (ev. sparmål om ni vill ha det i v1).

---

## 9. Fas 5: Barnvy

### 9.1 Tillgång till barnvy

- **Variant A – Växling på samma enhet:** Vuxen väljer “Öppna som [Barnnamn]” → appen visar barnvy för det barnet (kräver att API accepterar anrop med vuxens token men med memberId = barn, där det är tillåtet).  
- **Variant B – Barn egen inloggning:** Skärm “Logga in som barn” med device token; token kopplad till barnet (samma som webben).  
- Implementera den variant som matchar er backend och användning (en device per barn vs delad device).

### 9.2 Barn-dashboard (samma logik som ChildDashboard i webben)

- **Dagens uppgifter:**  
  - Hämta: `GET /calendar/members/{memberId}/task-completions` för idag (eller daily-tasks-ekvivalent).  
  - Visa lista med checkbox; vid klick anropa toggle completion.  
- **Husdjur + XP:**  
  - `GET /pets/current`, `GET /xp/current`.  
  - Visa djur och XP-ring (förenklad visuell liknande webben).  
  - Knapp “Mata”: `POST /pets/feed` (och uppdatera collected food om ni vill).  
- **Plånbok:**  
  - Saldo: `GET /wallet/balance`.  
  - Notiser: `GET /wallet/notifications/unshown`, `POST .../mark-shown`.  
  - Registrera utgift: dialog med belopp, kategori (om ni har det), anrop `POST /wallet/expense`.

### 9.3 UI

- En skärm med sektioner: Uppgifter, Djur, Plånbok (eller flikar).  
- Enkel, stor touch, barnvänliga färger och ikoner.

---

## 10. Fas 6: Polish och Google Play

### 10.1 Appen

- **Ikon och splash:** Tydlig ikon, enkel splash (t.ex. appnamn + logotyp).  
- **Namn:** T.ex. “Familjeappen” eller samma som webben.  
- **Integritet:** Kort integritetspolicy (var lagras vad, att ni använder er backend, ingen delning med tredje part utöver er server). Länk till policy på webben eller statisk sida.

### 10.2 Google Play

- **Google Play Console:** Skapa utvecklarkonto (engångskostnad).  
- **App signing:** Låt Google hantera signering (rekommenderat) eller egen upload-nyckel.  
- **Första releasen:**  
  - Build: release-variant, signerad.  
  - Ladda upp AAB (Android App Bundle).  
  - Fyll i butiksinfo: kort beskrivning, skärmdumpar, integritetspolicy, innehållsgrad (t.ex. 3+).  
- **COPPA/familjeapp:** Om appen riktar sig till barn under 13, följ Play Policy för barninnehåll (begränsad datainsamling m.m.).

---

## 11. Ordning och tidsuppskattning (grova)

| Fas | Uppskattning (om du är ny i Android) |
|-----|--------------------------------------|
| 0   | 1–2 dagar                            |
| 1   | 2–3 dagar                            |
| 2   | 1–2 dagar                            |
| 3   | 2–4 dagar                            |
| 4   | 1–2 dagar                            |
| 5   | 3–5 dagar                            |
| 6   | 1–2 dagar                            |

Totalt grovt **2–4 veckor** för en första användbar version, beroende på hur mycket du vill att Cursor ska skissa kod åt dig och hur mycket du själv lär dig samtidigt.

---

## 12. Nästa steg

1. **Besluta:** Native Android (Kotlin + Compose) eller React Native.  
2. **Skapa Android-projekt** (Fas 0) i `android/` (eller eget repo).  
3. **Implementera Fas 1** (auth) så att du kan logga in från appen mot er befintliga backend.  
4. **Fortsätt fas för fas** enligt tabellen ovan; prioritera “lägg till barn” och “uppgifter” så att vuxenflödet fungerar, därefter barnvy och ekonomi.

Om du vill kan vi i nästa steg bryta ner **Fas 0** eller **Fas 1** till konkreta filer och kodsteg (t.ex. “skapa denna Retrofit-interface”, “denna Compose-skärm”) direkt i ditt repo.

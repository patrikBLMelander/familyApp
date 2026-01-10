# Framtida Funktioner

Detta dokument beskriver planerade funktioner och förbättringar för FamilyApp.

## Prioriterade Funktioner

### 1. Push-notifikationer för Events ⭐ Hög prioritet

**Beskrivning:**
- Push-notifikationer för kalenderevents via PWA
- Konfigurerbar tid innan eventet (t.ex. 15 min, 1 timme, 1 dag innan)
- Aktiveras per event (checkbox: "Påminn mig")
- Fungerar med PWA service worker

**Tekniska krav:**
- PWA Notification API
- Service Worker för background notifications
- Backend endpoint för att schemalägga notifikationer
- UI för att konfigurera notifikationstid per event

**Användningsfall:**
- "Påminn mig 1 timme innan fotbollsträning"
- "Påminn mig 1 dag innan läkarbesök"

---

### 2. Level-uppnåelse Meddelanden ⭐ Hög prioritet

**Beskrivning:**
- Meddelande/notifikation när ett barn uppnår en ny level
- Kan vara push-notifikation eller in-app meddelande
- Visar badge och level-uppnåelse

**Tekniska krav:**
- Detektera när level ökar i XP-systemet
- Trigger push-notifikation eller in-app toast
- Visuell feedback med badge och level

**Användningsfall:**
- "Grattis! Du har nått Level 5! 🎉"
- Push-notifikation: "Du har uppnått Level 3!"

---

### 3. Statistik och Insikter

**Status:** Ointressant i nuläget

---

### 4. Notifikation när alla sysslor är klara ⭐ Medel prioritet

**Beskrivning:**
- Push-notifikation till föräldrar när ett barn gjort klart alla sina dagliga sysslor
- Barn ser själva i appen när de markerar sysslor, så inga notiser till barn
- Kan inkludera XP-summa för dagen

**Tekniska krav:**
- Detektera när alla sysslor i en dag är klara
- Push-notifikation via PWA (endast till föräldrar)
- Konfigurerbar (vilka föräldrar ska få notifikationer)

**Användningsfall:**
- "Emma har gjort klart alla sina sysslor idag! 🎉"
- "Emma har fått 15 XP idag!"

---

### 5. Delade Listor

**Status:** Redan implementerat (Todo-listor)

---

### 6. Onboarding och Användarhandledning ⭐ Hög prioritet

**Beskrivning:**
- Guide för nya användare (särskilt admin/första föräldern)
- Tips och tricks för att komma igång
- Snabbstart-guide för nya familjer
- Hjälp för andra föräldrar som ansluter

**Tekniska krav:**
- Onboarding-wizard/guide komponent
- Tooltips och hjälptexter
- "Första gången?"-flöde
- Dokumentation i appen

**Användningsfall:**
- "Välkommen! Låt oss skapa din första todo-lista"
- "Här är hur du lägger till sysslor"
- "Så här fungerar XP-systemet"

---

### 7. Pet/Ägg-system för Barnvyn 🐣 ⭐ Hög prioritet

**Beskrivning:**
- Helt ny barnvyn inspirerad av Finch-appen
- Barn väljer ett ägg vid första inloggningen i månaden
- Ägget kläcks och blir ett djur som växer under månaden baserat på sysslornas XP/energi
- Varje syssla ger djuret energi (kopplat till XP-systemet)
- Vid månadsskiftet: celebration och nytt ägg-val
- Barnvyn fokuserar på pet-visualisering med gulligt design för 6-10-åringar
- Vuxenvyn förblir oförändrad

**Designbeslut:**
- **5 ägg-typer** (samma färg = samma djur, deterministisk mappning)
- **5 growth stages** per djur (steg 1-5 baserat på XP/level)
- **Celebration vid månadsskiftet** när nytt ägg kläcks
- **Visning av tidigare djur** (historik över tidigare månaders pets)
- Använder befintligt XP-system som bas (ingen ändring i XP-logiken)

**Tekniska krav (Backend):**
- Ny tabell: `child_pet`
  - `id`, `member_id`, `year`, `month`
  - `selected_egg_type` (t.ex. "blue_egg", "green_egg", "red_egg", "yellow_egg", "purple_egg")
  - `pet_type` (bestäms från äggval - t.ex. "dragon", "cat", "dog", "bird", "rabbit")
  - `growth_stage` (1-5, baserat på level/XP)
  - `hatched_at` (när djuret kläcktes)
  - Relation till `member_xp_progress` för att beräkna growth_stage
- Ny tabell: `pet_history` (eller utökning av `child_pet` med flagga för historik)
  - Spara gamla pets för visning
- Ny service: `PetService`
  - Metoder: `selectEgg()`, `getCurrentPet()`, `getPetHistory()`, `calculateGrowthStage()`
  - Mappning ägg-typ → pet-typ (deterministisk)
  - Beräkna growth_stage från XP/level (t.ex. Level 1-2 = stage 1, Level 3-4 = stage 2, etc.)
- Utöka `XpService.monthlyReset()` för att hantera pet-reset
  - Skapa ny `child_pet` record för ny månad
  - Spara gamla pet i history
- API endpoints:
  - `GET /api/pets/current` - Hämta nuvarande pet
  - `POST /api/pets/select-egg` - Välj ägg (första gången i månaden)
  - `GET /api/pets/history` - Hämta tidigare månaders pets

**Tekniska krav (Frontend):**
- **Äggval-skärm** (ny komponent)
  - Visa när barn loggar in första gången i månaden (eller om pet inte valt)
  - 5 olika ägg att välja mellan (visuellt tilltalande)
  - Animation när ägget kläcks efter val
- **Ny ChildDashboard**
  - Huvudfokus på pet-visualisering (stor, centrerad)
  - Pet-visualisering baserad på growth_stage (emojis eller SVG)
  - Energi-bar (baserad på XP progress)
  - Tasks presenteras som "Feed your pet" / "Ge din vän energi"
  - Gulligt design för 6-10-åringar (färgglatt, stora knappar, enkelt)
- **Pet-growth visualisering**
  - 5 olika visualiseringar per pet-typ (en per growth stage)
  - Animationer när pet växer (vid level-uppgång)
  - Feedback när tasks kompletteras
- **Celebration vid månadsskiftet**
  - Modal/overlay när ny månad börjar
  - Visar förra månadens pet (slutresultat)
  - Celebration-animation
  - Främjar nytt ägg-val
- **Pet History**
  - Vy för att se alla tidigare månaders pets
  - Kan vara separat vy eller del av dashboard
- **Design-system för barn**
  - Barnvänligt färgschema (ljusa, mjuka färger)
  - Stora touch-friendly knappar
  - Enkla ikoner och illustrationer
  - Pet-illustrationer (SVG eller emojis)

**Användningsfall:**
- "Välj ditt ägg för denna månaden!"
- "Grattis! Ditt ägg har kläckts och blev en drake! 🐉"
- "Din vän växer! Utför sysslor för att ge den energi!"
- "Du har klarat alla sysslor idag! Din drake är glad! 🎉"
- "Ny månad! Ditt sista månads djur var så söt! Välj ett nytt ägg!"

**Komplexitetsbedömning:**
- **Medel-hög komplexitet** - Större frontend-arbete, men backend är relativt enkelt
- Kräver designarbete för pet-visualiseringar
- Användarupplevelse är kritisk för målgruppen (6-10-åringar)

**Tidsuppskattning:**
- Backend (datamodell + service + endpoints): ~1 vecka
- Frontend (äggval + ny dashboard + pet-visualisering): ~3-4 veckor
- Design och polering: ~1-2 veckor
- **Totalt: 5-7 veckor** (beroende på designval och visualiseringar)

**Rekommendation:**
- Börja med emojis för pet-visualisering (snabbast att implementera)
- Kan uppgraderas till SVG-illustrationer senare
- Testa med riktiga barn för feedback på design och UX
- Överväg att göra pet-typer tematiska per månad (som badges idag)

---

### 8. Dark Mode 🌙

**Beskrivning:**
- Tema-växling mellan ljust och mörkt läge
- Systeminställning (följer OS)
- Manuell växling
- Sparas i localStorage

**Tekniska krav:**
- CSS variabler för teman
- Toggle-knapp i UI
- System preference detection
- Smooth transitions

---

## Framtida Visioner

### 9. Automatisk Budgetuppföljning 💰

**Beskrivning:**
- Automatisk kategorisering av utgifter från bankkonton
- Statistik och uppföljning av utgifter
- Integration med bank-API

**Banker att stödja:**
- Swedbank
- American Express (Amex)

**Tekniska överväganden:**
- **Bank-API:** Använd Open Banking API (PSD2 i EU)
  - Säker och officiell metod
  - Kräver OAuth 2.0 autentisering
  - Swedbank: Open Banking API
  - Amex: American Express API (kolla deras developer portal)

**Komplexitetsbedömning:**
- **Medel-hög komplexitet** - Inte trivialt men görbart
- **OAuth 2.0 flow:** Standardiserat men kräver noggrann implementation
- **API-integration:** Varje bank har eget API och dokumentation
- **Säkerhet:** Kritiskt - hantera tokens säkert, använd HTTPS, encrypta data
- **Underhåll:** API:er kan ändras, kräver uppdateringar

**Tekniska krav:**
- OAuth 2.0 integration (Authorization Code flow)
- Bank-API klienter för Swedbank och Amex
- Token management och refresh
- Transaktionshantering och parsing
- Kategoriseringslogik (regelbaserad eller ML)
- Statistik och visualisering
- Säker datalagring (encrypted)

**Tidsuppskattning:**
- Initial research och setup: 1-2 veckor
- Swedbank integration: 2-3 veckor
- Amex integration: 2-3 veckor
- Kategorisering och statistik: 2-3 veckor
- **Totalt: 7-11 veckor** (beroende på API-dokumentation och komplexitet)

**Rekommendation:**
- Börja med en bank (t.ex. Swedbank) för att lära sig processen
- Använd befintliga bibliotek/verktyg för OAuth där det går
- Testa noggrant i sandbox-miljö först
- Implementera robust felhantering och logging

---

## Implementationsordning (Föreslagen)

### Kritiskt (Gör först):
1. **Förbättrad Autentisering (9.2 - Lösenordshantering)** ⚠️ **KRITISKT**
   - Email login utan lösenord är ett säkerhetsproblem
   - Måste fixas innan fler familjer börjar använda appen

### Hög prioritet:
2. **Multi-Device Support (9.1)** 
   - Förbättrar användarupplevelsen avsevärt
   - Kan göras parallellt med lösenordshantering

3. **Pet/Ägg-system för Barnvyn (7)** (Stor förändring, hög värde för barn)
4. **Level-uppnåelse Meddelanden** (Enklast, hög värde)
5. **Onboarding** (Viktigt för användarvänlighet)
6. **Push-notifikationer för Events** (Mer komplext, men hög värde)

### Medel prioritet:
7. **Notifikation när alla sysslor är klara** (Medel komplexitet)
8. **Förbättrat Login-flöde för Barn (9.3)**

### Lägre prioritet:
9. **Dark Mode** (Nice-to-have, relativt enkelt)
10. **Budgetuppföljning** (Långsiktigt projekt, kräver research)

---

## Säkerhets- och Autentiseringsförbättringar 🔒

### 10. Förbättrad Autentisering och Multi-Device Support ⚠️ KRITISKT

**Problem identifierade:**

1. **Token försvinner vid login på ny enhet:**
   - När man loggar in med email på en ny enhet, genereras en ny device token som ersätter den gamla
   - Detta gör att man blir utloggad på andra enheter
   - Problem för användare som vill vara inloggad på flera enheter

2. **Email login saknar autentisering:**
   - Email login kräver endast email, inget lösenord
   - Vem som helst kan logga in med någons email
   - **KRITISKT säkerhetsproblem** när fler familjer börjar använda appen

3. **Svårt för barn att logga in igen:**
   - Barn behöver QR-kod för att logga in första gången
   - Om de tappar sin token är det svårt att komma in igen
   - Kräver att föräldrar genererar ny QR-kod

**Lösningar:**

#### 10.1 Multi-Device Support
- **Stöd för flera device tokens per användare**
- Skapa ny tabell `device_tokens` med relation till `family_member`
- Tillåt flera aktiva tokens per användare
- Möjlighet att se och hantera aktiva enheter
- "Logga ut från alla enheter" funktion

**Tekniska krav:**
- Ny migration: `device_tokens` tabell
- Uppdatera `FamilyMemberService` för att hantera flera tokens
- UI för att se aktiva enheter
- Endpoint för att logga ut från specifik enhet

#### 10.2 Email + Lösenord Autentisering
- **Lägg till lösenordshantering**
- Hasha lösenord (BCrypt)
- Email + lösenord vid login
- "Glömt lösenord?" funktion (email med reset-länk)
- Lösenordskrav vid registrering
- **Föräldrar (inte admin) ska kunna lägga till/uppdatera sin email** under familjemedlemmar
  - UI i familjemedlemmar-vyn för att lägga till email
  - Endast föräldrar kan uppdatera sin egen email
  - Email krävs för att kunna använda "Glömt lösenord?"

**Tekniska krav:**
- Ny kolumn: `password_hash` i `family_member`
- BCrypt för lösenordshashing
- Uppdatera `loginByEmail` till `loginByEmailAndPassword`
- UI för föräldrar att lägga till/uppdatera sin email
- Email service för password reset
- UI för lösenordsändring

**Email-funktionalitet för "Glömt lösenord?":**

**Vad behövs:**
- **Email service provider:** 
  - Alternativ 1: **SMTP via egen email** (t.ex. Gmail, Outlook)
    - Kräver SMTP-konfiguration
    - Behöver en "from"-adress (t.ex. `noreply@familyapp.com` eller `support@familyapp.com`)
    - Enklast att komma igång med
  - Alternativ 2: **Email service** (t.ex. SendGrid, Mailgun, AWS SES)
    - Mer professionellt
    - Bättre deliverability
    - Kostar pengar vid större volym
    - Gratis tier finns (t.ex. SendGrid: 100 emails/dag gratis)

**Implementation:**
- **Komplexitet:** Medel - Inte svårt men kräver konfiguration
- **Tidsuppskattning:** 1-2 dagar för grundläggande setup
- **Steg:**
  1. Välj email provider (rekommenderar SendGrid eller SMTP)
  2. Konfigurera email credentials i backend (environment variables)
  3. Implementera email service i Spring Boot (JavaMailSender eller SendGrid SDK)
  4. Skapa "Forgot Password" endpoint
  5. Generera säker reset token (UUID + expiration)
  6. Skicka email med reset-länk
  7. Skapa "Reset Password" endpoint
  8. UI för "Glömt lösenord?" och reset-formulär

**Rekommendation:**
- För att komma igång snabbt: Använd **SMTP med Gmail** eller **SendGrid free tier**
- Skapa en dedikerad email-adress: `noreply@familyapp.com` eller `support@familyapp.com`
- Om du inte har egen domän ännu: Använd Gmail med app-specific password
- För produktion: Överväg SendGrid/Mailgun för bättre deliverability

**Säkerhet:**
- Reset tokens ska ha expiration (t.ex. 1 timme)
- Tokens ska vara unika och slumpmässiga
- Invalidera token efter användning
- Rate limiting på "forgot password" requests (förhindra spam)

#### 10.3 Förbättrat Login-flöde för Barn
- **"Glömt token?"-flöde**
- Föräldrar kan generera ny QR-kod från admin-vyn
- Alternativt: Föräldrar kan "logga in som barn" temporärt
- Eller: Barn kan be föräldrar om ny QR-kod via appen

**Tekniska krav:**
- UI för föräldrar att generera ny QR-kod för barn
- Eventuellt: "Request new token" funktion som skickar notifikation till föräldrar

**Prioritet:** ⚠️ **HÖGST** - Email login utan lösenord är ett kritiskt säkerhetsproblem

**Tidsuppskattning:**
- Multi-device support: 1-2 veckor
- Lösenordshantering: 1-2 veckor
- Förbättrat login-flöde: 1 vecka
- **Totalt: 3-5 veckor**

---

## Anteckningar

- Alla push-notifikationer kräver PWA service worker
- Överväg användarinställningar för notifikationer (opt-in/opt-out)
- Budgetuppföljning är ett större projekt som kräver noggrann planering och potentiellt externa partners/API:er
- **KRITISKT:** Email login utan lösenord måste fixas innan fler familjer börjar använda appen


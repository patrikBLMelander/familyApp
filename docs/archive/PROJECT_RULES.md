## Övergripande mål

- **Syfte**: Skapa en modern, lättanvänd familjeapp för hela hushållet, med fokus på enkelhet, tydlighet och tillförlitlighet.
- **Kärnfunktioner (första versioner)**:
  - To do-listor (t.ex. inköpslistor, packlistor, dagliga sysslor).
  - Gemensamt schema med aktiviteter.
  - Uppföljning av dagliga sysslor i hemmet.
- **Framtida funktioner**:
  - Automatisk budgetkontroll, inkl. hämtning/scraping av utgifter från olika konton (med hög säkerhet och transparens).

## Teknisk stack

- **Backend**:
  - Java (modern version, t.ex. Java 21 eller senare).
  - Spring Boot (modern version, fokus på enkel, modulär arkitektur).
  - Databasmigreringar ska alltid ske med **Flyway**.
  - Relationsdatabas **MySQL** som primär datalagring.
- **Frontend**:
  - React (modern version).
  - Projektet ska använda TypeScript (tydlig typning, bättre underhållbarhet).
  - Byggverktyg och bundler kan vara t.ex. Vite eller Create React App, men ska väljas med fokus på enkelhet och snabb utveckling.

## Arkitektur och designprinciper

- **Clean Code & Clean Architecture**:
  - Koden ska vara lätt att läsa, förstå och testa.
  - Hög sammanhållning (high cohesion) och låg koppling (low coupling).
  - Domänlogik ska vara tydligt separerad från infrastruktur (databas, webbramverk, UI).
  - Undvik läckage av ramverksklasser in i domänmodellen (t.ex. inga Spring-annoteringar direkt på domänobjekt om det går att undvika).
- **API-design**:
  - REST-baserade API:er med tydlig resursmodell (t.ex. `/todos`, `/lists`, `/activities`, `/household-tasks`).
  - JSON som standardformat.
  - Tydlig versionshantering av API:er (t.ex. `/api/v1/...`).
  - Validering av indata på både backend och frontend (med tydliga felmeddelanden).
- **Modularisering**:
  - Backend-projektet struktureras i tydliga lager/paket, t.ex.:
    - `domain` (entiteter, värdeobjekt, domänlogik).
    - `application`/`service` (use cases, orchestration).
    - `infrastructure` (repositories, externa integrationer).
    - `api`/`web` (REST-kontrollers, DTO:er).
  - Frontend-projektet struktureras utifrån features/domäner (t.ex. `features/todos`, `features/schedule`, `features/chores`), inte bara tekniska mappar (`components`, `utils`).

## Databas och migreringar

- **Regler för databas**:
  - Alla ändringar i databasschema ska göras via Flyway-migreringar.
  - Inga manuella ändringar direkt i databasen i utvecklings- eller produktionsmiljö (förutom felsökning).
  - Migreringsfiler ska vara små, tydliga och ha beskrivande namn.
- **Datalagring**:
  - Utgå från att data ska kunna bevaras långsiktigt (ingen “snabb-hack”-lagring).
  - Viktiga tabeller (t.ex. användare, aktiviteter, transaktioner) ska ha tydliga primärnycklar, index och relationsmodeller.

## Kvalitet, testning och robusthet

- **Testning**:
  - Backend:
    - Enhetstester för domänlogik och services.
    - Integrationstester för API:er och databasinteraktion (gärna med testcontainer eller in-memory databas).
  - Frontend:
    - Enhetstester för viktiga komponenter och hooks.
    - Eventuellt end-to-end-tester för kritiska flöden (t.ex. skapa/bocka av todo-lista).
- **Felhantering**:
  - Backend ska alltid returnera tydliga, strukturerade fel (t.ex. ett standardiserat error-objekt).
  - Frontend ska presentera begripliga felmeddelanden för användaren (inga tekniska stacktraces).
  - Loggning av fel på backend med struktur (t.ex. via loggframework) för enkel felsökning.

## Säkerhet och integritet

- **Autentisering & auktorisering**:
  - Systemet ska från början designas med stöd för autentisering (t.ex. JWT eller OAuth2) även om första versionen kan vara förenklad.
  - Åtkomst till familjedata ska begränsas till familjemedlemmar och relevanta behörigheter.
- **Hantera känslig data**:
  - Lösenord och tokens får aldrig loggas i klartext.
  - I budget-/bankintegrationsdelen ska vi:
    - Minimera mängden känslig data vi lagrar.
    - Säkerställa kryptering i vila (at rest) och under transport (in transit).
    - Vara tydliga mot användarna om vilken data som hämtas och hur den används.

## UX och användarupplevelse

- **Målgrupp**: Hela familjen – vuxna, tonåringar, och ev. yngre barn (med begränsade rättigheter).
- **Regler för UX**:
  - Gränssnittet ska vara enkelt, intuitivt och **mobil-först (mobile first)**.
  - Design och layout utformas i första hand för **mobilvy** (smala skärmar), och skalas sedan upp till surfplatta/desktop.
  - Viktigaste flödena (t.ex. lägga till/bocka av en uppgift) ska kunna göras med så få klick/tryck som möjligt.
  - Använd tydliga texter och ikoner; undvik tekniskt språk.
  - Tillgänglighet ska beaktas (kontraster, läsbarhet, tydliga klickytor – speciellt på mobil).
  - Komponenter och layouter ska testas för minst:
    - Smal mobilskärm (t.ex. 320–375 px bredd).
    - “Normal” mobilskärm (t.ex. 390–430 px bredd).
  - Navigation ska fungera bra med tumme (t.ex. bottomeny eller lättåtkomliga knappar).

## Kodstil och standarder

- **Backend (Java)**:
  - Följ etablerad Java-kodstil (t.ex. Google Java Style eller liknande).
  - Använd moderna Java-språkfunktioner där det passar (records, streams, pattern matching, etc.), men utan att offra läsbarhet.
  - Undvik “magiska tal/strängar”; använd konstanter och enum:ar.
  - Filer och klasser ska ha tydliga och beskrivande namn.
- **Frontend (React + TypeScript)**:
  - Funktionella komponenter och hooks (ingen klassbaserad React om det inte finns särskilda skäl).
  - Strikt typning med TypeScript; `any` ska undvikas.
  - Delad logik samlas i hooks (`useSomething`), inte duplicerad i flera komponenter.
  - Komponenter ska vara så små och fokuserade som möjligt.

## Versionshantering och struktur

- **Git-regler**:
  - Beskrivande commit-meddelanden (vad och varför, inte bara “fix”).
  - Funktionsgrenar (feature branches) för större ändringar.
  - Håll `main`/`master` stabil och körbar.
- **Projektstruktur**:
  - Backend och frontend kan ligga i samma repo men i separata mappar, t.ex.:
    - `backend/`
    - `frontend/`
  - Dokumentation (t.ex. detta dokument) ligger i rotmappen eller i en `docs/`-mapp.

## Containerisering och deployment

- **Docker**:
  - Både backend (Spring Boot) och frontend (React) ska kunna köras via Docker-containrar.
  - Varje del ska ha en tydlig Dockerfile med fokus på:
    - Små, säkra och effektiva images (multi-stage builds där det är relevant).
    - Separata images för backend och frontend.
  - Lokalt utvecklingsläge kan stödjas via `docker-compose` eller motsvarande för att:
    - Starta backend, frontend och MySQL tillsammans.
- **Databas (MySQL)**:
  - MySQL är "first-class citizen" i alla miljöer (lokalt, staging, production).
  - Standardkonfiguration för lokalt dev kan ligga i `docker-compose` med tydliga standardvärden (användarnamn, lösenord, databasnamn).
  - Flyway ska konfigureras så att migreringar körs automatiskt vid uppstart av backend-containern.
- **Deployment (Railway)**:
  - Projektet ska kunna deployas till Railway:
    - Backend som en separat service (Docker-baserad).
    - Frontend som egen service (antingen statiskt byggd klient eller container, beroende på vad som passar bäst).
    - MySQL som Railway-managed databas.
  - Konfiguration (t.ex. DB-url, credentials, API-baser) hanteras via **miljövariabler** och **inte** hårdkodas:
    - Separata inställningar för utveckling, test och produktion.
  - Alla miljöspecifika hemligheter (API-nycklar, DB-lösenord, tokens) ska aldrig checkas in i git.

## Utbyggbarhet och framtida funktioner

- **Design för framtiden**:
  - Nya domäner (t.ex. budget, ekonomi, påminnelser, notiser) ska kunna läggas till utan att bryta befintlig kod.
  - API:er ska designas så att de kan utökas bakåtkompatibelt när det går.
  - Affärslogik ska ligga i domän-/service-lager, inte i UI eller controllers.

## Beslutsprinciper

- **När vi står inför val** (ramverk, bibliotek, designbeslut):
  - Föredra:
    - Enkelhet framför “coola” lösningar.
    - Läsbarhet framför maximal “cleverness”.
    - Stabilitet och långsiktig underhållbarhet framför snabb hackighet.
  - Alla större tekniska beslut dokumenteras kort i en beslutssektion (t.ex. i denna fil eller separat `DECISIONS.md`).

## Nästa steg i projektet

- Sätta upp grundstruktur i repot:
  - `backend/` med ett tomt (eller nästan tomt) Spring Boot-projekt.
  - `frontend/` med ett tomt (eller nästan tomt) React + TypeScript-projekt.
  - Grundläggande README som beskriver hur man kör båda delarna.
- Därefter:
  - Designa första versionen av datamodell och API för to do-listor.
  - Skapa första enkla flödet i frontend för att:
    - Visa en lista.
    - Lägga till en uppgift.
    - Bocka av en uppgift.



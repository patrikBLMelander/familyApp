# Menscykel - Forskningsresultat och Implementeringsförslag

## Hur man räknar ut menscykeln

### Grundläggande principer

1. **Cykeldefinition**: En menscykel räknas från **första dagen du får mens till dagen innan nästa mens börjar**
   - Längden beräknas genom att räkna dagarna mellan den senaste mensens första dag och nästa mens första dag

2. **Normal cykelängd**:
   - **Normalt**: 21-35 dagar (vanligast omkring 28 dagar)
   - **Variation**: 95% av kvinnor har en cykel mellan 15-45 dagar
   - Längden är mycket individuell och varierar från person till person

3. **Cykelns faser**:
   - **Mens** (dag 1-7): Livmodern stöter ut slemhinnan
   - **Follikulärfas** (7-21 dagar): Östrogen ökar, kroppen förbereder ägg
   - **Ägglossning**: Ett mogent ägg släpper från äggstocken - sker ofta omkring 14 dagar **före** nästa mens
   - **Lutealfas** (10-16 dagar): Progesteron är dominant

### Beräkningsmetod

För att förutsäga nästa mens och ägglossning behöver man:

1. **Spåra historiska data**:
   - Första dagen av varje mens (datum)
   - Längden på varje cykel (antal dagar)
   - Eventuellt längden på mensperioden (antal dagar)

2. **Beräkna genomsnittlig cykellängd**:
   - Om man har flera cykler: beräkna genomsnittet av de senaste 3-6 cyklerna
   - Om man bara har en cykel: använd den längden
   - Om man inte har någon data: använd standard 28 dagar som utgångspunkt

3. **Förutsäga nästa mens**:
   - Nästa mens = Senaste mensens första dag + genomsnittlig cykellängd
   - Exempel: Om senaste mens började 1 januari och genomsnittlig cykel är 28 dagar, bör nästa mens börja 29 januari

4. **Förutsäga ägglossning**:
   - Ägglossning sker vanligtvis 14 dagar **före** nästa mens
   - Ägglossning = Nästa mens - 14 dagar
   - Exempel: Om nästa mens börjar 29 januari, sker ägglossningen omkring 15 januari

5. **Fertila fönstret**:
   - Spermier kan överleva upp till 5 dagar
   - Ägget överlever upp till 24 timmar efter ägglossning
   - Fertilt fönster: 5 dagar före ägglossning till 1 dag efter ägglossning
   - Totalt: 6 dagars fertilt fönster

### Algoritm för implementation

```typescript
interface MenstrualCycleData {
  periodStartDates: Date[];  // Första dagen av varje mens
  averageCycleLength?: number;  // Genomsnittlig cykellängd (beräknas)
  averagePeriodLength?: number;  // Genomsnittlig menslängd (valfritt)
}

interface CyclePrediction {
  nextPeriodStart: Date;
  nextPeriodEnd: Date;
  ovulationDate: Date;
  fertileWindowStart: Date;
  fertileWindowEnd: Date;
  currentCycleDay: number;  // Vilken dag i nuvarande cykel
  currentPhase: 'menstruation' | 'follicular' | 'ovulation' | 'luteal';
}

function calculateCycle(data: MenstrualCycleData): CyclePrediction {
  // 1. Beräkna genomsnittlig cykellängd
  let avgCycleLength = 28; // Standard
  if (data.averageCycleLength) {
    avgCycleLength = data.averageCycleLength;
  } else if (data.periodStartDates.length >= 2) {
    // Beräkna från historiska data
    const cycles: number[] = [];
    for (let i = 1; i < data.periodStartDates.length; i++) {
      const days = Math.floor(
        (data.periodStartDates[i].getTime() - data.periodStartDates[i-1].getTime()) 
        / (1000 * 60 * 60 * 24)
      );
      cycles.push(days);
    }
    avgCycleLength = Math.round(
      cycles.reduce((sum, len) => sum + len, 0) / cycles.length
    );
  }

  // 2. Förutsäg nästa mens
  const lastPeriodStart = data.periodStartDates[data.periodStartDates.length - 1];
  const nextPeriodStart = new Date(lastPeriodStart);
  nextPeriodStart.setDate(nextPeriodStart.getDate() + avgCycleLength);

  // 3. Förutsäg menslängd (standard 5 dagar om inte angivet)
  const periodLength = data.averagePeriodLength || 5;
  const nextPeriodEnd = new Date(nextPeriodStart);
  nextPeriodEnd.setDate(nextPeriodEnd.getDate() + periodLength - 1);

  // 4. Förutsäg ägglossning (14 dagar före nästa mens)
  const ovulationDate = new Date(nextPeriodStart);
  ovulationDate.setDate(ovulationDate.getDate() - 14);

  // 5. Fertilt fönster (5 dagar före ägglossning till 1 dag efter)
  const fertileWindowStart = new Date(ovulationDate);
  fertileWindowStart.setDate(fertileWindowStart.getDate() - 5);
  const fertileWindowEnd = new Date(ovulationDate);
  fertileWindowEnd.setDate(fertileWindowEnd.getDate() + 1);

  // 6. Beräkna nuvarande cykeldag
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const daysSinceLastPeriod = Math.floor(
    (today.getTime() - lastPeriodStart.getTime()) / (1000 * 60 * 60 * 24)
  );
  const currentCycleDay = daysSinceLastPeriod + 1;

  // 7. Bestäm nuvarande fas
  let currentPhase: CyclePrediction['currentPhase'];
  if (currentCycleDay <= periodLength) {
    currentPhase = 'menstruation';
  } else if (currentCycleDay <= avgCycleLength - 14) {
    currentPhase = 'follicular';
  } else if (currentCycleDay <= avgCycleLength - 10) {
    currentPhase = 'ovulation';
  } else {
    currentPhase = 'luteal';
  }

  return {
    nextPeriodStart,
    nextPeriodEnd,
    ovulationDate,
    fertileWindowStart,
    fertileWindowEnd,
    currentCycleDay,
    currentPhase
  };
}
```

## Implementeringsförslag

### Datamodell

#### Backend (Java)

1. **Ny tabell: `menstrual_cycle`**
   ```sql
   CREATE TABLE menstrual_cycle (
       id VARCHAR(36) PRIMARY KEY,
       family_member_id VARCHAR(36) NOT NULL,
       period_start_date DATE NOT NULL,
       period_length INT DEFAULT 5,
       cycle_length INT,  -- Beräknas automatiskt
       created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
       FOREIGN KEY (family_member_id) REFERENCES family_member(id) ON DELETE CASCADE
   );
   ```

2. **Nya fält i `family_member` tabell**:
   ```sql
   ALTER TABLE family_member ADD COLUMN menstrual_cycle_enabled BOOLEAN DEFAULT FALSE;
   ALTER TABLE family_member ADD COLUMN menstrual_cycle_private BOOLEAN DEFAULT TRUE;
   ```
   - `menstrual_cycle_enabled`: Om menscykel-spårning är aktiverad för denna användare
   - `menstrual_cycle_private`: Om menscykel-data är privat (bara synlig för användaren själv) eller delad med andra vuxna i familjen

#### Frontend (TypeScript)

1. **Nya typer**:
   ```typescript
   export type MenstrualCycleEntry = {
     id: string;
     periodStartDate: string;  // ISO date string
     periodLength: number;
     cycleLength?: number;
   };

   export type MenstrualCycleSettings = {
     enabled: boolean;
     isPrivate: boolean;  // true = privat, false = synlig för andra vuxna
   };

   export type CyclePrediction = {
     nextPeriodStart: string;
     nextPeriodEnd: string;
     ovulationDate: string;
     fertileWindowStart: string;
     fertileWindowEnd: string;
     currentCycleDay: number;
     currentPhase: 'menstruation' | 'follicular' | 'ovulation' | 'luteal';
   };
   ```

### UI/UX Design

#### Familjesidan - Redigera användare

När man redigerar en vuxen användare (PARENT eller ASSISTANT) ska det finnas:

1. **Checkbox: "Aktivera menscykel-spårning"**
   - När den är ikryssad visas ytterligare alternativ

2. **Radio buttons för synlighet**:
   - "Privat" (bara synlig för användaren själv)
   - "Delad med andra vuxna" (synlig för alla vuxna i familjen)

3. **Möjlighet att lägga till mensperioder**:
   - Datumväljare för första dagen av mens
   - Eventuellt längd på mensperioden (standard 5 dagar)

#### Dashboard/Vy för menscykel

1. **Översikt**:
   - Nuvarande cykeldag
   - Nuvarande fas
   - Nästa förväntade mens (datum)
   - Nästa ägglossning (datum)
   - Fertilt fönster (datumintervall)

2. **Kalendervy**:
   - Visar förväntade mensperioder
   - Visar ägglossning
   - Visar fertilt fönster
   - Historiska mensperioder

3. **Lägg till mensperiod**:
   - Enkel knapp för att registrera att mens började idag
   - Eller datumväljare för att lägga till historisk data

### API Endpoints

#### Backend

1. `GET /api/v1/family-members/{memberId}/menstrual-cycle`
   - Hämta menscykel-data för en användare
   - Kräver att användaren har behörighet (själv eller annan vuxen om `isPrivate = false`)

2. `POST /api/v1/family-members/{memberId}/menstrual-cycle/entries`
   - Lägg till en ny mensperiod
   - Body: `{ periodStartDate: "2024-01-15", periodLength: 5 }`

3. `PATCH /api/v1/family-members/{memberId}/menstrual-cycle/settings`
   - Uppdatera inställningar (enabled, isPrivate)
   - Body: `{ enabled: true, isPrivate: false }`

4. `GET /api/v1/family-members/{memberId}/menstrual-cycle/prediction`
   - Hämta förutsägelse baserat på historisk data
   - Returnerar: `CyclePrediction`

5. `DELETE /api/v1/family-members/{memberId}/menstrual-cycle/entries/{entryId}`
   - Ta bort en mensperiod

### Säkerhet och integritet

1. **Behörigheter**:
   - Endast vuxna (PARENT, ASSISTANT) kan aktivera menscykel-spårning
   - Användaren själv kan alltid se sin egen data
   - Andra vuxna kan se data om `isPrivate = false`
   - Barn (CHILD) kan inte se någon menscykel-data

2. **Dataskydd**:
   - Menscykel-data är känslig information
   - Ska behandlas med samma säkerhet som lösenord och e-post
   - Logga inte menscykel-data i loggar

### Implementation-steg

1. **Fas 1: Backend datamodell**
   - Skapa databasmigrationer
   - Uppdatera `FamilyMember` domain model
   - Skapa `MenstrualCycle` domain model och entity

2. **Fas 2: Backend API**
   - Implementera service layer
   - Implementera controller endpoints
   - Implementera beräkningslogik

3. **Fas 3: Frontend API client**
   - Uppdatera `familyMembers.ts` med nya API-anrop
   - Skapa `menstrualCycle.ts` för menscykel-specifika API-anrop

4. **Fas 4: Frontend UI - Inställningar**
   - Lägg till checkbox och radio buttons i `FamilyMembersView.tsx`
   - Uppdatera `updateFamilyMember` för att hantera inställningar

5. **Fas 5: Frontend UI - Vy**
   - Skapa ny komponent `MenstrualCycleView.tsx`
   - Lägg till navigering till vyn
   - Implementera kalendervy och översikt

6. **Fas 6: Testning**
   - Testa beräkningslogik med olika scenarion
   - Testa behörigheter och säkerhet
   - Testa UI/UX

### Överväganden

1. **Regelbundna vs oregelbundna cykler**:
   - Algoritmen fungerar bäst för regelbundna cykler
   - För oregelbundna cykler kan förutsägelserna vara mindre exakta
   - Överväg att visa konfidensintervall eller varningar

2. **Första gången**:
   - Om användaren inte har historisk data, använd standard 28 dagar
   - Visa tydligt att förutsägelserna blir mer exakta med mer data

3. **Integritet**:
   - Tänk på att detta är känslig information
   - Standard ska vara "privat" (isPrivate = true)
   - Tydlig kommunikation om vem som kan se data

4. **Framtida förbättringar**:
   - Symptom-spårning (humör, smärta, etc.)
   - Notifikationer för när nästa mens förväntas
   - Export av data
   - Integration med andra hälsotjänster

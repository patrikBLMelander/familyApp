-- Automatisk vecko- och månadspeng.
--
-- Ett schema per barn. Tre sorter: WEEKLY och MONTHLY betalar samma belopp varje
-- gång, LEVEL betalar utifrån vilken XP-nivå barnet nådde under månaden som gick.
--
-- Nivåbeloppen ligger som fem kolumner i stället för en egen tabell. Antalet nivåer
-- är fem och har varit det sedan XP-systemet skrevs; en tabell skulle vara en
-- generalisering av något som inte varierar, och göra varje läsning till en join.
--
-- day_of_month tillåter 1-28 så att dagen finns i februari. Alternativet -- "sista
-- dagen i månaden" -- är ett annat val och får ett eget läge den dagen någon vill ha
-- det, hellre än att smygas in som 29-31.

CREATE TABLE recurring_allowance (
    id VARCHAR(36) PRIMARY KEY,

    -- UNIQUE: ett schema per barn. Flera samtidiga scheman vore ett annat och
    -- svårare löfte att hålla ("vilket betalade den 1:a?").
    member_id VARCHAR(36) NOT NULL UNIQUE,
    created_by_member_id VARCHAR(36) NULL,

    kind VARCHAR(16) NOT NULL,        -- WEEKLY | MONTHLY | LEVEL
    amount INT NULL,                  -- WEEKLY och MONTHLY
    weekday INT NULL,                 -- 1=mån ... 7=sön, WEEKLY
    day_of_month INT NULL,            -- 1-28, MONTHLY och LEVEL

    level_1_amount INT NULL,
    level_2_amount INT NULL,
    level_3_amount INT NULL,
    level_4_amount INT NULL,
    level_5_amount INT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Jobbet frågar "vad är förfallet och obetalt", aldrig "är det fredag idag".
    -- Railway startar om containern ofta, och ett jobb som bara tittar på dagens
    -- datum hoppar tyst över en vecka om omstarten råkar ligga fel.
    next_due_on DATE NOT NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_recurring_allowance_member
        FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE,
    CONSTRAINT fk_recurring_allowance_creator
        FOREIGN KEY (created_by_member_id) REFERENCES family_member(id) ON DELETE SET NULL
);

CREATE INDEX idx_recurring_allowance_due ON recurring_allowance(active, next_due_on);

-- Kvitto per utbetalning, och det som gör dubbelbetalning omöjlig.
--
-- Det unika indexet på (member_id, due_date) är hela poängen: en andra körning för
-- samma förfallodag krockar med indexet i stället för att betala igen. En dubbel
-- XP-nollställning går att laga; en dubbel utbetalning är riktiga pengar som redan
-- ligger i ett barns plånbok.
CREATE TABLE recurring_allowance_payment (
    id VARCHAR(36) PRIMARY KEY,
    member_id VARCHAR(36) NOT NULL,
    due_date DATE NOT NULL,
    amount INT NOT NULL,

    -- PAID, eller varför den inte betalades. En utebliven peng ska gå att förklara
    -- för en förälder som undrar, inte bara saknas.
    status VARCHAR(32) NOT NULL,
    note VARCHAR(255) NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_recurring_allowance_payment UNIQUE (member_id, due_date),
    CONSTRAINT fk_recurring_allowance_payment_member
        FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE
);

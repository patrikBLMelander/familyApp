-- Subscription state, one row per family.
--
-- The trial is a server-side clock rather than a store-side introductory offer, so
-- that a family gets one trial regardless of which platform they signed up on, no
-- card is needed to start, and the same rule applies to web. The stores handle
-- payment only; whether a family is entitled is decided here.
--
-- Entitlement is family-level on purpose. One parent subscribes and the whole
-- household is covered, including children whose device tokens have no store
-- account of their own.

CREATE TABLE family_subscription (
    family_id VARCHAR(36) PRIMARY KEY,

    -- TRIAL, ACTIVE, GRACE, EXPIRED, CANCELED. Derived from the dates below rather
    -- than trusted on its own; stored so it can be queried and indexed.
    status VARCHAR(20) NOT NULL DEFAULT 'TRIAL',

    trial_started_at DATETIME(6) NOT NULL,
    trial_ends_at DATETIME(6) NOT NULL,

    -- Everything below is written only from provider webhooks, never from a client.
    current_period_end DATETIME(6) NULL,
    platform VARCHAR(16) NULL,               -- ANDROID, IOS, WEB
    store_product_id VARCHAR(100) NULL,
    store_transaction_id VARCHAR(255) NULL,  -- original transaction, stable across renewals
    provider_customer_id VARCHAR(255) NULL,  -- RevenueCat app_user_id, set to the family id
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,

    -- Free access granted by hand: family, friends, beta testers, a support gesture.
    -- Deliberately separate from the store columns rather than faking a paid period,
    -- so a comp cannot be overwritten by a webhook and never looks like revenue.
    -- is_comped with a NULL expiry means forever; with a date, until then.
    is_comped BOOLEAN NOT NULL DEFAULT FALSE,
    comp_expires_at DATETIME(6) NULL,
    comp_reason VARCHAR(255) NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_family_subscription_family
        FOREIGN KEY (family_id) REFERENCES family(id) ON DELETE CASCADE
);

CREATE INDEX idx_family_subscription_status ON family_subscription(status);
CREATE INDEX idx_family_subscription_trial_ends ON family_subscription(trial_ends_at);

-- Every webhook that changes the row, kept so a billing dispute can be reconstructed
-- and so a redelivered event can be recognised and ignored.
CREATE TABLE subscription_event (
    id VARCHAR(36) PRIMARY KEY,
    family_id VARCHAR(36) NULL,

    -- The provider's own event id. UNIQUE is what makes webhook handling idempotent:
    -- a redelivery hits this constraint instead of applying twice.
    provider_event_id VARCHAR(255) NOT NULL UNIQUE,

    event_type VARCHAR(50) NOT NULL,
    payload TEXT NULL,
    received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_subscription_event_family
        FOREIGN KEY (family_id) REFERENCES family(id) ON DELETE CASCADE
);

CREATE INDEX idx_subscription_event_family ON subscription_event(family_id, received_at);

-- Existing families get a full trial starting now, not back-dated to when they
-- registered. Back-dating would expire everyone who signed up more than three
-- months ago -- including the current testers -- the moment this deploys.
--
-- To grandfather them permanently free instead, comp them (see below).
--
-- Granting free access, by family:
--   UPDATE family_subscription SET is_comped = TRUE, comp_expires_at = NULL,
--          comp_reason = 'familj'
--    WHERE family_id = (SELECT id FROM family WHERE name = 'Melander');
--
-- Or by a parent's email, which is usually easier to look up:
--   UPDATE family_subscription SET is_comped = TRUE, comp_reason = 'betatestare'
--    WHERE family_id = (SELECT family_id FROM family_member WHERE email = 'x@y.se');
--
-- Time-limited instead of forever:
--   ... SET is_comped = TRUE, comp_expires_at = NOW() + INTERVAL 12 MONTH ...
--
-- Revoking:
--   UPDATE family_subscription SET is_comped = FALSE, comp_expires_at = NULL,
--          comp_reason = NULL WHERE family_id = '...';
INSERT INTO family_subscription (family_id, status, trial_started_at, trial_ends_at, created_at, updated_at)
SELECT f.id, 'TRIAL', NOW(6), DATE_ADD(NOW(6), INTERVAL 3 MONTH), NOW(6), NOW(6)
FROM family f
WHERE NOT EXISTS (SELECT 1 FROM family_subscription s WHERE s.family_id = f.id);

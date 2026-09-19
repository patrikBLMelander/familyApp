-- Affiliate program -- backend foundation (Phase 1).
--
-- Everything here is additive: four new tables, nothing existing changes. Safe to deploy
-- ahead of the code that reads them.
--
-- Money model: commission is a recurring share of the NET (after store fee) for each
-- PAID period, capped at commission_month_cap periods per referred family. The engine
-- lives in AffiliateCommissionService, hooked into SubscriptionWebhookService.

-- An affiliate + their portal login. Invite-only: an admin creates the row (status
-- INVITED, password_hash NULL); the affiliate then activates by setting a password,
-- which only works because the row already exists. commission_pct / month_cap are
-- per-affiliate so terms can vary.
CREATE TABLE affiliate (
    id                   VARCHAR(36) PRIMARY KEY,
    name                 VARCHAR(255) NOT NULL,
    email                VARCHAR(255) NOT NULL,
    password_hash        VARCHAR(255) NULL,
    referral_code        VARCHAR(64)  NOT NULL,
    commission_pct       DECIMAL(5,2) NOT NULL DEFAULT 20.00,
    commission_month_cap INT          NOT NULL DEFAULT 12,
    status               VARCHAR(20)  NOT NULL DEFAULT 'INVITED',  -- INVITED | ACTIVE | PAUSED
    session_token_hash   VARCHAR(64)  NULL,                        -- SHA-256 of the portal token
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    CONSTRAINT uq_affiliate_email UNIQUE (email),
    CONSTRAINT uq_affiliate_referral_code UNIQUE (referral_code)
);
CREATE INDEX idx_affiliate_session ON affiliate(session_token_hash);

-- Which family belongs to which affiliate. First-touch: UNIQUE(family_id) means a family
-- is claimed by the first affiliate only and never reassigned.
CREATE TABLE affiliate_referral (
    id            VARCHAR(36) PRIMARY KEY,
    affiliate_id  VARCHAR(36) NOT NULL,
    family_id     VARCHAR(36) NOT NULL,
    source        VARCHAR(20) NOT NULL,   -- CODE | WEB | PLAY
    attributed_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_referral_family UNIQUE (family_id),
    CONSTRAINT fk_referral_affiliate FOREIGN KEY (affiliate_id)
        REFERENCES affiliate(id) ON DELETE CASCADE
);
CREATE INDEX idx_referral_affiliate ON affiliate_referral(affiliate_id);

-- One row per PAID period. subscription_event_ref is the RevenueCat event id -- UNIQUE
-- makes accrual idempotent against webhook redelivery. period_index counts 1..cap.
CREATE TABLE affiliate_commission (
    id                     VARCHAR(36)  PRIMARY KEY,
    affiliate_id           VARCHAR(36)  NOT NULL,
    family_id              VARCHAR(36)  NOT NULL,
    subscription_event_ref VARCHAR(255) NOT NULL,
    period_index           INT          NOT NULL,
    amount                 DECIMAL(12,2) NOT NULL,
    currency               VARCHAR(3)   NOT NULL,
    status                 VARCHAR(20)  NOT NULL,   -- PENDING | APPROVED | PAID | CLAWED_BACK
    payout_id              VARCHAR(36)  NULL,
    earned_at              DATETIME(6)  NOT NULL,
    CONSTRAINT uq_commission_event UNIQUE (subscription_event_ref),
    CONSTRAINT fk_commission_affiliate FOREIGN KEY (affiliate_id)
        REFERENCES affiliate(id) ON DELETE CASCADE
);
CREATE INDEX idx_commission_affiliate ON affiliate_commission(affiliate_id);
CREATE INDEX idx_commission_family ON affiliate_commission(family_id);
CREATE INDEX idx_commission_status ON affiliate_commission(status);

-- One payout round for one affiliate. total_amount is the sum of APPROVED commissions it
-- covers; those commissions point back via payout_id.
CREATE TABLE affiliate_payout (
    id           VARCHAR(36)  PRIMARY KEY,
    affiliate_id VARCHAR(36)  NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    currency     VARCHAR(3)   NOT NULL,
    status       VARCHAR(20)  NOT NULL,   -- PENDING | PAID
    method       VARCHAR(50)  NULL,
    paid_at      DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    CONSTRAINT fk_payout_affiliate FOREIGN KEY (affiliate_id)
        REFERENCES affiliate(id) ON DELETE CASCADE
);
CREATE INDEX idx_payout_affiliate ON affiliate_payout(affiliate_id);

-- Password reset tokens.
--
-- Until now a parent who forgot their password was locked out permanently: the only
-- recovery was another parent setting it for them, which does nothing for a
-- single-parent family, or editing the database by hand.
--
-- The token is stored HASHED, never in plain text. A reset token is a temporary
-- password in everything but name, so a leaked backup of this table would otherwise
-- hand over every account with an outstanding reset. Hashing means the copy we hold is
-- useless to anyone who takes it -- the same reasoning as password_hash, for the same
-- reason.
--
-- SHA-256 rather than BCrypt here on purpose. BCrypt is deliberately slow to defend a
-- low-entropy human password against offline guessing; these tokens are 256 bits of
-- randomness, so guessing is not the threat and slowness would only make verification
-- expensive.
--
-- used_at rather than deleting the row: a used token stays visible for a while, so
-- "this link says it has already been used" can be told apart from "no such link".

CREATE TABLE password_reset_token (
    id VARCHAR(36) PRIMARY KEY,
    member_id VARCHAR(36) NOT NULL,

    -- Hex SHA-256 of the token that was e-mailed. UNIQUE both to prevent collisions
    -- and to make the lookup an index hit.
    token_hash VARCHAR(64) NOT NULL UNIQUE,

    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_password_reset_token_member
        FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE
);

-- Requesting a reset invalidates any earlier outstanding one, and the rate limit asks
-- "when did this member last request?" -- both are lookups by member.
CREATE INDEX idx_password_reset_token_member ON password_reset_token(member_id, created_at);

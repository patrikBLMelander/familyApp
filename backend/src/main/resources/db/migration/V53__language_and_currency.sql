-- i18n: a member's chosen app language (NULL = follow the device) and the
-- family's wallet currency. Existing families keep kronor.
ALTER TABLE family_member ADD COLUMN language VARCHAR(5) NULL;
ALTER TABLE family ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'SEK';

-- Give invite codes their own column.
--
-- An invite code was stored in family_member.device_token -- the same column the
-- member's phone authenticates with. Generating a code therefore overwrote whatever
-- the paired device was using, and since the Android invite dialog generates one the
-- moment it opens, a parent merely looking at a child's QR code logged that child out
-- of their phone. Recovery meant scanning the new code, which consumed it again.
--
-- Sharing the column had a second consequence: link-device-by-token looked up an
-- invite by device_token, so any valid device token doubled as an invite. Knowing a
-- child's token was enough to bind another device to that child's account. Looking up
-- only by invite_token closes that.
--
-- Invites are valid for an hour and reusable until they expire. Reusable matters: a
-- child reinstalling the app should not need a parent to break their session first.
--
-- Outstanding codes issued before this are not migrated. They live in device_token,
-- and recognising them here would preserve exactly the flaw above. Invites are
-- short-lived, so the cost is asking for a fresh code.

ALTER TABLE family_member
    ADD COLUMN invite_token VARCHAR(36) NULL,
    ADD COLUMN invite_expires_at DATETIME(6) NULL;

-- Unique so a lookup is unambiguous. MySQL permits many NULLs in a unique index, so
-- every member without an outstanding invite is unaffected.
CREATE UNIQUE INDEX idx_family_member_invite_token ON family_member(invite_token);

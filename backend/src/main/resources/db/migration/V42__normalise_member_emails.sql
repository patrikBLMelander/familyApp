-- Store emails lowercased.
--
-- Logins have been working for capitalised addresses only because MySQL's default
-- collation is case-insensitive: the production logs show both Melander.joakim@ and
-- melander.joakim@ resolving to the same account. That is implicit, and a collation
-- change or a move to another database would silently break every login with a
-- capitalised address.
--
-- With the code now lowercasing on both read and write, existing rows are brought
-- into line so behaviour no longer depends on the collation at all.
--
-- The unique index on email means a case-insensitive collation has already prevented
-- two rows differing only in case, so this cannot collide.
UPDATE family_member
   SET email = LOWER(email)
 WHERE email IS NOT NULL
   AND email <> LOWER(email);

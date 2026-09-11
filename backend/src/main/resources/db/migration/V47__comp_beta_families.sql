-- Lifetime free access for every family that was here before launch.
--
-- They ran the closed test. Between them they found the login that rejected correct
-- passwords, the invite dialog that logged a child out, the egg that was chosen and
-- then lost, and the three complaints that turned feeding into something a child can
-- see. Charging them 29 kr a month afterwards is not the trade anyone had in mind.
--
-- Comped rather than a longer trial, because a trial ends. is_comped with a NULL
-- expiry is the one state that outranks everything: SubscriptionService.resolveStatus
-- checks it before the store period and before the trial clock, and no webhook can
-- revoke it. That is exactly what "lifetime" has to mean -- including after a family
-- subscribes, cancels, and lets the paid period lapse years from now.
--
-- THE CUTOFF IS A LITERAL DATE ON PURPOSE. A Flyway migration runs when it is
-- deployed, not when it is written. An unbounded UPDATE would comp whoever happens to
-- exist at deploy time -- and if this ships late, or is replayed against a restored
-- database after launch, that set includes paying customers. Pinning the date makes
-- the outcome the same whenever it runs.
--
-- Families with no subscription row get one. Rows are created lazily on the first
-- status read (SubscriptionService.getOrCreate), so a family that registered and never
-- opened a screen that asks has no row at all, and an UPDATE on its own would skip
-- them without a sound. The trial dates are still filled in even though nothing reads
-- them while is_comped is true: if a comp is ever revoked, the row underneath should be
-- sane rather than NULL.
--
-- One thing this does NOT do: cancel anybody's store subscription. Comping a family
-- that is genuinely paying leaves them paying and comped at the same time. Check
-- before deploying:
--
--   SELECT family_id, status, platform, current_period_end FROM family_subscription
--    WHERE current_period_end > NOW();
--
-- To add a tester who joined after this ran:
--   UPDATE family_subscription SET is_comped = TRUE, comp_expires_at = NULL,
--          comp_reason = 'betatestare'
--    WHERE family_id = (SELECT family_id FROM family_member WHERE email = 'x@y.se');
--
-- To revoke one:
--   UPDATE family_subscription SET is_comped = FALSE, comp_expires_at = NULL,
--          comp_reason = NULL WHERE family_id = '...';

-- Families that never had a row. COMPED from the start rather than TRIAL, so the
-- stored column is honest the moment this commits instead of on the next read.
INSERT INTO family_subscription (
    family_id, status, trial_started_at, trial_ends_at,
    is_comped, comp_expires_at, comp_reason, created_at, updated_at
)
SELECT f.id, 'COMPED', NOW(6), DATE_ADD(NOW(6), INTERVAL 3 MONTH),
       TRUE, NULL, 'betatestare', NOW(6), NOW(6)
  FROM family f
 WHERE f.created_at < '2026-09-10 00:00:00'
   AND NOT EXISTS (
       SELECT 1 FROM family_subscription s WHERE s.family_id = f.id
   );

-- Everyone else. comp_expires_at is set to NULL explicitly rather than left alone,
-- because a row carrying an old time-limited comp must not keep its expiry here.
UPDATE family_subscription fs
  JOIN family f ON f.id = fs.family_id
   SET fs.is_comped       = TRUE,
       fs.comp_expires_at = NULL,
       fs.comp_reason     = 'betatestare',
       fs.status          = 'COMPED',
       fs.updated_at      = NOW(6)
 WHERE f.created_at < '2026-09-10 00:00:00';

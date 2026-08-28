-- Remove menstrual cycle tracking.
--
-- The feature only ever existed in the web app -- it was never built for Android or
-- iOS -- and the web app is being retired now that both mobile apps are the product.
-- Rather than carry a feature nobody will reach, it goes.
--
-- The data is the reason this is a deliberate migration rather than a tidy-up. Period
-- start dates are health data: a special category under GDPR Article 9. Keeping rows
-- for a feature that no longer exists would mean holding special-category data with no
-- purpose to justify it, which is the opposite of what the regulation asks. Deleting
-- them is both the tidy and the correct outcome.
--
-- This cannot be undone. There is no export step and no soft delete: after this runs,
-- the rows are gone. That is intentional -- a soft-deleted health record is still a
-- health record you are storing.
--
-- The privacy policy's section on the feature is removed in the same change. The order
-- matters: the section may only go once the data has, or the document would be
-- describing a state that is not yet true.

DROP TABLE IF EXISTS menstrual_cycle;

ALTER TABLE family_member
    DROP COLUMN menstrual_cycle_enabled,
    DROP COLUMN menstrual_cycle_private;

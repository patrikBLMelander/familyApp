-- Add composite index on member_xp_progress(member_id, year, month).
-- The existing separate indexes on member_id and (year, month) require two index lookups;
-- a composite index covers the hot findByMemberIdAndYearAndMonth query in a single seek.
CREATE INDEX idx_member_xp_progress_member_year_month
    ON member_xp_progress (member_id, year, month);

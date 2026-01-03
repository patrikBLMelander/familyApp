-- Initial schema for FamilyApp
-- Intentionally minimal; real domain tables will be added in later migrations.

CREATE TABLE IF NOT EXISTS flyway_history_marker (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at  DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)
);




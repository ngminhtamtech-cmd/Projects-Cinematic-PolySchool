-- Deprecated: a fixed integration database is unsafe and no longer supported.
-- Use scripts/init-test-db.ps1, which creates a guarded CineBookIT_* database.
THROW 51000, 'Fixed test database creation is disabled. Use scripts/init-test-db.ps1.', 1;

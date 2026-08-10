SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;

BEGIN TRANSACTION;
IF COL_LENGTH('dbo.AuditLogs', 'BeforeJson') IS NULL
    ALTER TABLE dbo.AuditLogs ADD BeforeJson NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.AuditLogs', 'AfterJson') IS NULL
    ALTER TABLE dbo.AuditLogs ADD AfterJson NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.AuditLogs', 'IpAddress') IS NULL
    ALTER TABLE dbo.AuditLogs ADD IpAddress NVARCHAR(64) NULL;
IF COL_LENGTH('dbo.AuditLogs', 'UserAgent') IS NULL
    ALTER TABLE dbo.AuditLogs ADD UserAgent NVARCHAR(512) NULL;
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.AuditLogs') AND name = N'IX_AuditLogs_CreatedAt'
)
    CREATE INDEX IX_AuditLogs_CreatedAt ON dbo.AuditLogs(CreatedAt DESC);
COMMIT TRANSACTION;

-- ROLLBACK: DROP INDEX IX_AuditLogs_CreatedAt ON dbo.AuditLogs; then drop the four columns.

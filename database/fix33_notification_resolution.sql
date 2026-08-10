-- fix33_notification_resolution.sql
-- Distinguish a notification being read by one recipient from the business event being resolved.
SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;
    IF COL_LENGTH(N'dbo.AdminNotifications', N'ResolvedAt') IS NULL
        ALTER TABLE dbo.AdminNotifications ADD ResolvedAt DATETIME2(3) NULL;
    IF COL_LENGTH(N'dbo.AdminNotifications', N'ResolvedBy') IS NULL
        ALTER TABLE dbo.AdminNotifications ADD ResolvedBy INT NULL;
    IF COL_LENGTH(N'dbo.AdminNotifications', N'Resolution') IS NULL
        ALTER TABLE dbo.AdminNotifications ADD Resolution NVARCHAR(40) NULL;
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'FK_AdminNotifications_ResolvedBy')
        ALTER TABLE dbo.AdminNotifications ADD CONSTRAINT FK_AdminNotifications_ResolvedBy
            FOREIGN KEY (ResolvedBy) REFERENCES dbo.Users(Id);
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

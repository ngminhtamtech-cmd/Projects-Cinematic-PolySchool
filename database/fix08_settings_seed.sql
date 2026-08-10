SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;

BEGIN TRANSACTION;
MERGE dbo.SystemSettings AS target
USING (VALUES
    (N'payment.mode', N'simulated'),
    (N'counter.expiryMinutes', N'30'),
    (N'backup.directory', N'C:\tmp\cinebook-backups'),
    (N'backup.databaseName', N'CineBookDB'),
    (N'company.name', N'CineBook'),
    (N'company.taxCode', N''),
    (N'company.address', N''),
    (N'vat.rate', N'10'),
    (N'mail.mode', N'logfile'),
    (N'security.maxLoginAttempts', N'5'),
    (N'security.lockMinutes', N'15')
) AS source(SettingKey, SettingValue)
ON target.SettingKey = source.SettingKey
WHEN NOT MATCHED THEN
    INSERT (SettingKey, SettingValue) VALUES (source.SettingKey, source.SettingValue);
COMMIT TRANSACTION;

-- ROLLBACK: delete only keys created by this migration after checking they were not customized.

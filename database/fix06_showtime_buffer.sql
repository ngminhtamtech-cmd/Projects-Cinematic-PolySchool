SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;

BEGIN TRANSACTION;

MERGE dbo.SystemSettings AS target
USING (VALUES
    (N'booking.cutoffMinutes', N'15'),
    (N'showtime.cleanupBufferMinutes', N'15')
) AS source(SettingKey, SettingValue)
ON target.SettingKey = source.SettingKey
WHEN NOT MATCHED THEN
    INSERT (SettingKey, SettingValue) VALUES (source.SettingKey, source.SettingValue);

COMMIT TRANSACTION;

-- ROLLBACK:
-- DELETE FROM dbo.SystemSettings
-- WHERE SettingKey IN (N'booking.cutoffMinutes', N'showtime.cleanupBufferMinutes');

-- Add the refund-appeal deadline and repair sold-out notification deep links.
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'CineBookDB' AND DB_NAME() NOT LIKE N'CineBookIT[_]%'
    THROW 51800, 'fix38 only accepts CineBookDB or an ephemeral CineBookIT_* database.', 1;
IF OBJECT_ID(N'dbo.SystemSettings', N'U') IS NULL
   OR OBJECT_ID(N'dbo.AdminNotifications', N'U') IS NULL
    THROW 51801, 'Run the base schema and prior migrations before fix38.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    MERGE dbo.SystemSettings AS target
    USING (VALUES (N'refund.appealWindowHours', N'24')) AS source(SettingKey, SettingValue)
       ON target.SettingKey=source.SettingKey
    WHEN NOT MATCHED THEN
        INSERT (SettingKey, SettingValue) VALUES (source.SettingKey, source.SettingValue);

    UPDATE dbo.AdminNotifications
       SET ActionUrl=N'/admin/showtimes?focusShowtimeId=' + TargetId
     WHERE TargetType=N'Showtime_SoldOut'
       AND TRY_CONVERT(INT, TargetId) IS NOT NULL
       AND (ActionUrl IS NULL OR ActionUrl=N'/admin/showtimes');

    COMMIT TRANSACTION;
    PRINT 'fix38_refund_window_and_showtime_deeplink.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- ROLLBACK: restore the pre-migration backup; customized setting values are never overwritten.

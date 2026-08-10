-- fix32_seat_layout_versions.sql
-- Keep historical Seats rows while allowing safe future-layout changes.
SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH(N'dbo.Seats', N'IsActive') IS NULL
        ALTER TABLE dbo.Seats ADD IsActive BIT NOT NULL CONSTRAINT DF_Seats_IsActive DEFAULT 1;
    IF COL_LENGTH(N'dbo.Seats', N'LayoutVersion') IS NULL
        ALTER TABLE dbo.Seats ADD LayoutVersion INT NOT NULL CONSTRAINT DF_Seats_LayoutVersion DEFAULT 1;
    IF COL_LENGTH(N'dbo.Seats', N'RetiredAt') IS NULL
        ALTER TABLE dbo.Seats ADD RetiredAt DATETIME2(3) NULL;

    IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name=N'UQ_Seats_Room_SeatKey')
        ALTER TABLE dbo.Seats DROP CONSTRAINT UQ_Seats_Room_SeatKey;
    ELSE IF EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'UQ_Seats_Room_SeatKey' AND object_id=OBJECT_ID(N'dbo.Seats'))
        DROP INDEX UQ_Seats_Room_SeatKey ON dbo.Seats;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name=N'UX_Seats_Active_Room_SeatKey'
                     AND object_id=OBJECT_ID(N'dbo.Seats'))
        EXEC(N'CREATE UNIQUE INDEX UX_Seats_Active_Room_SeatKey ON dbo.Seats(RoomId, SeatKey) WHERE IsActive=1');

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name=N'IX_Seats_Room_Active'
                     AND object_id=OBJECT_ID(N'dbo.Seats'))
        EXEC(N'CREATE INDEX IX_Seats_Room_Active ON dbo.Seats(RoomId, IsActive, LayoutVersion)');

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

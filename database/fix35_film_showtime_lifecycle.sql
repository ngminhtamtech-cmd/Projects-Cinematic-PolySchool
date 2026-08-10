-- Durable film tombstones and two-phase showtime deletion.
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'CineBookDB' AND DB_NAME() NOT LIKE N'CineBookIT[_]%'
    THROW 51500, 'fix35 only accepts CineBookDB or an ephemeral CineBookIT_* database.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH(N'dbo.Films', N'DeletedAt') IS NULL
        ALTER TABLE dbo.Films ADD DeletedAt DATETIME2(7) NULL;
    IF COL_LENGTH(N'dbo.Films', N'DeletedByUserId') IS NULL
        ALTER TABLE dbo.Films ADD DeletedByUserId INT NULL;
    IF COL_LENGTH(N'dbo.Films', N'DeletionMode') IS NULL
        ALTER TABLE dbo.Films ADD DeletionMode NVARCHAR(30) NULL;

    IF COL_LENGTH(N'dbo.Showtimes', N'SaleStatus') IS NULL
        ALTER TABLE dbo.Showtimes ADD SaleStatus NVARCHAR(20) NOT NULL
            CONSTRAINT DF_Showtimes_SaleStatus DEFAULT N'ON_SALE';
    IF COL_LENGTH(N'dbo.Showtimes', N'DeleteRequestedAt') IS NULL
        ALTER TABLE dbo.Showtimes ADD DeleteRequestedAt DATETIME2(7) NULL;
    IF COL_LENGTH(N'dbo.Showtimes', N'DeleteNotBefore') IS NULL
        ALTER TABLE dbo.Showtimes ADD DeleteNotBefore DATETIME2(7) NULL;
    IF COL_LENGTH(N'dbo.Showtimes', N'DeleteRequestedByUserId') IS NULL
        ALTER TABLE dbo.Showtimes ADD DeleteRequestedByUserId INT NULL;

    -- Legacy databases do not have these columns when this batch is compiled.
    -- Defer every dependent FK/check/index statement until after the ALTERs above.
    EXEC sys.sp_executesql N'
        IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N''FK_Films_DeletedByUser'')
            ALTER TABLE dbo.Films ADD CONSTRAINT FK_Films_DeletedByUser
                FOREIGN KEY (DeletedByUserId) REFERENCES dbo.Users(Id);
        IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N''FK_Showtimes_DeleteRequestedByUser'')
            ALTER TABLE dbo.Showtimes ADD CONSTRAINT FK_Showtimes_DeleteRequestedByUser
                FOREIGN KEY (DeleteRequestedByUserId) REFERENCES dbo.Users(Id);

        IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N''CK_Films_DeletionMetadata'')
            ALTER TABLE dbo.Films ADD CONSTRAINT CK_Films_DeletionMetadata CHECK (
                (DeletedAt IS NULL AND DeletedByUserId IS NULL AND DeletionMode IS NULL)
                OR (DeletedAt IS NOT NULL AND DeletionMode IN (N''PURGE_COMMENTS'',N''PRESERVE_COMMENTS''))
            );

        IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N''CK_Showtimes_SaleStatus'')
            ALTER TABLE dbo.Showtimes ADD CONSTRAINT CK_Showtimes_SaleStatus
                CHECK (SaleStatus IN (N''ON_SALE'',N''SUSPENDED'',N''DELETED''));
        IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N''CK_Showtimes_DeleteMetadata'')
            ALTER TABLE dbo.Showtimes ADD CONSTRAINT CK_Showtimes_DeleteMetadata CHECK (
                (SaleStatus=N''ON_SALE'' AND DeleteRequestedAt IS NULL AND DeleteNotBefore IS NULL
                     AND DeleteRequestedByUserId IS NULL)
                OR (SaleStatus IN (N''SUSPENDED'',N''DELETED'') AND DeleteRequestedAt IS NOT NULL
                     AND DeleteNotBefore IS NOT NULL)
            );

        IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N''IX_Films_AdminLifecycle''
                       AND object_id=OBJECT_ID(N''dbo.Films''))
            CREATE INDEX IX_Films_AdminLifecycle ON dbo.Films(DeletedAt, EndDate, Status, CreatedAt);
        IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N''IX_Showtimes_DeletionQueue''
                       AND object_id=OBJECT_ID(N''dbo.Showtimes''))
            CREATE INDEX IX_Showtimes_DeletionQueue
                ON dbo.Showtimes(SaleStatus, DeleteNotBefore, CinemaId, StartTime)
                INCLUDE (FilmId, RoomId, DeleteRequestedByUserId);';

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

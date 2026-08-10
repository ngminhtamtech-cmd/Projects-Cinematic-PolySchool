-- fix45_cinema_contents.sql
-- Cinema-scoped marketing content with a lossless legacy settings backfill.
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'CineBookDB' AND DB_NAME() NOT LIKE N'CineBookIT[_]%'
    THROW 52065, 'fix45 only accepts CineBookDB or an ephemeral CineBookIT_* database.', 1;
IF OBJECT_ID(N'dbo.Cinemas', N'U') IS NULL OR OBJECT_ID(N'dbo.SystemSettings', N'U') IS NULL
    THROW 52066, 'Run the base schema and prior migrations before fix45.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.CinemaContents', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.CinemaContents (
            CinemaId INT NOT NULL,
            ContentKey NVARCHAR(50) NOT NULL,
            ContentJson NVARCHAR(MAX) NOT NULL,
            UpdatedByUserId INT NULL,
            UpdatedAt DATETIME2(3) NOT NULL CONSTRAINT DF_CinemaContents_UpdatedAt DEFAULT SYSDATETIME(),
            RowVersion ROWVERSION NOT NULL,
            CONSTRAINT PK_CinemaContents PRIMARY KEY (CinemaId, ContentKey),
            CONSTRAINT FK_CinemaContents_Cinema FOREIGN KEY (CinemaId) REFERENCES dbo.Cinemas(Id),
            CONSTRAINT FK_CinemaContents_UpdatedBy FOREIGN KEY (UpdatedByUserId) REFERENCES dbo.Users(Id),
            CONSTRAINT CK_CinemaContents_Key CHECK (ContentKey IN
                (N'cinetags_data',N'corner_items_data',N'events_data',N'special_cinemas_data')),
            CONSTRAINT CK_CinemaContents_Json CHECK (ISJSON(ContentJson)=1)
        );
    END;

    ;WITH ContentDefaults AS (
        SELECT N'cinetags_data' AS ContentKey,
               COALESCE((SELECT SettingValue FROM dbo.SystemSettings WHERE SettingKey=N'cinetags_data'), N'[]') AS ContentJson
        UNION ALL SELECT N'corner_items_data',
               COALESCE((SELECT SettingValue FROM dbo.SystemSettings WHERE SettingKey=N'corner_items_data'), N'[]')
        UNION ALL SELECT N'events_data',
               COALESCE((SELECT SettingValue FROM dbo.SystemSettings WHERE SettingKey=N'events_data'), N'[]')
        UNION ALL SELECT N'special_cinemas_data',
               COALESCE((SELECT SettingValue FROM dbo.SystemSettings WHERE SettingKey=N'special_cinemas_data'), N'[]')
    )
    INSERT INTO dbo.CinemaContents(CinemaId, ContentKey, ContentJson)
    SELECT c.Id, d.ContentKey,
           CASE WHEN ISJSON(d.ContentJson)=1 THEN d.ContentJson ELSE N'[]' END
    FROM dbo.Cinemas c
    CROSS JOIN ContentDefaults d
    WHERE ISNULL(c.Status,N'active')=N'active'
      AND NOT EXISTS (
          SELECT 1 FROM dbo.CinemaContents existing
          WHERE existing.CinemaId=c.Id AND existing.ContentKey=d.ContentKey
      );

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name=N'IX_CinemaContents_Key_Cinema'
                     AND object_id=OBJECT_ID(N'dbo.CinemaContents'))
        CREATE INDEX IX_CinemaContents_Key_Cinema
            ON dbo.CinemaContents(ContentKey, CinemaId) INCLUDE(UpdatedAt);

    COMMIT TRANSACTION;
    PRINT 'fix45_cinema_contents.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- Rollback: DROP TABLE dbo.CinemaContents after exporting any cinema-specific edits.

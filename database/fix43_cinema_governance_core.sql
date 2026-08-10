-- fix43_cinema_governance_core.sql
-- Core cinema-scoped governance constraints and ownership metadata.
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'CineBookDB' AND DB_NAME() NOT LIKE N'CineBookIT[_]%'
    THROW 52043, 'fix43 only accepts CineBookDB or an ephemeral CineBookIT_* database.', 1;

IF OBJECT_ID(N'dbo.Users', N'U') IS NULL
   OR OBJECT_ID(N'dbo.Cinemas', N'U') IS NULL
   OR OBJECT_ID(N'dbo.Rooms', N'U') IS NULL
   OR OBJECT_ID(N'dbo.Showtimes', N'U') IS NULL
   OR OBJECT_ID(N'dbo.CinemaFilms', N'U') IS NULL
   OR OBJECT_ID(N'dbo.Promotions', N'U') IS NULL
   OR OBJECT_ID(N'dbo.ComboFoods', N'U') IS NULL
   OR OBJECT_ID(N'dbo.UserAppeals', N'U') IS NULL
   OR OBJECT_ID(N'dbo.AdminNotifications', N'U') IS NULL
    THROW 52044, 'Run the base schema and fixes 00-42 before fix43.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.Showtimes s
    JOIN dbo.Rooms r ON r.Id=s.RoomId
    WHERE r.CinemaId<>s.CinemaId
)
    THROW 52045, 'A showtime references a room from another cinema.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.Showtimes s
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.CinemaFilms cf
        WHERE cf.CinemaId=s.CinemaId AND cf.FilmId=s.FilmId
    )
      AND (s.EndTime IS NULL OR s.EndTime>=SYSDATETIME())
)
    THROW 52046, 'A current or future showtime film is not assigned to its cinema.', 1;

IF EXISTS (
    SELECT CinemaId, LTRIM(RTRIM(Name))
    FROM dbo.Rooms
    WHERE ISNULL(Status, N'active')<>N'deleted'
    GROUP BY CinemaId, LTRIM(RTRIM(Name))
    HAVING COUNT(*)>1
)
    THROW 52047, 'Duplicate active room names exist in the same cinema.', 1;

IF EXISTS (
    SELECT 1 FROM dbo.Users
    WHERE Role IN (N'manager', N'staff') AND CinemaId IS NULL
)
    THROW 52048, 'Every manager and staff account must have a cinema before fix43.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH(N'dbo.CinemaFilms', N'Status') IS NULL
        ALTER TABLE dbo.CinemaFilms ADD Status NVARCHAR(20) NOT NULL
            CONSTRAINT DF_CinemaFilms_Status DEFAULT N'active';
    IF COL_LENGTH(N'dbo.CinemaFilms', N'AssignedAt') IS NULL
        ALTER TABLE dbo.CinemaFilms ADD AssignedAt DATETIME2(3) NOT NULL
            CONSTRAINT DF_CinemaFilms_AssignedAt DEFAULT SYSDATETIME();
    IF COL_LENGTH(N'dbo.CinemaFilms', N'AssignedByUserId') IS NULL
        ALTER TABLE dbo.CinemaFilms ADD AssignedByUserId INT NULL;
    IF COL_LENGTH(N'dbo.CinemaFilms', N'UnassignedAt') IS NULL
        ALTER TABLE dbo.CinemaFilms ADD UnassignedAt DATETIME2(3) NULL;
    IF COL_LENGTH(N'dbo.CinemaFilms', N'UnassignedByUserId') IS NULL
        ALTER TABLE dbo.CinemaFilms ADD UnassignedByUserId INT NULL;

    -- A legacy, already-finished showtime is durable evidence that the film once
    -- belonged to that cinema. Preserve that history with an inactive mapping.
    -- Current/future gaps are rejected by the preflight above and require an
    -- explicit business assignment instead of an automatic permission grant.
    EXEC(N'
        INSERT INTO dbo.CinemaFilms (
            CinemaId, FilmId, Status, AssignedAt, AssignedByUserId,
            UnassignedAt, UnassignedByUserId
        )
        SELECT DISTINCT
            s.CinemaId, s.FilmId, N''inactive'', SYSDATETIME(), NULL,
            SYSDATETIME(), NULL
        FROM dbo.Showtimes s
        WHERE s.EndTime<SYSDATETIME()
          AND NOT EXISTS (
              SELECT 1
              FROM dbo.CinemaFilms cf
              WHERE cf.CinemaId=s.CinemaId AND cf.FilmId=s.FilmId
          );
    ');

    IF EXISTS (
        SELECT 1
        FROM dbo.Showtimes s
        WHERE NOT EXISTS (
            SELECT 1 FROM dbo.CinemaFilms cf
            WHERE cf.CinemaId=s.CinemaId AND cf.FilmId=s.FilmId
        )
    )
        THROW 52046, 'A showtime film is not assigned to its cinema.', 1;

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE name=N'CK_CinemaFilms_Status'
                     AND parent_object_id=OBJECT_ID(N'dbo.CinemaFilms'))
        EXEC(N'ALTER TABLE dbo.CinemaFilms WITH CHECK ADD CONSTRAINT CK_CinemaFilms_Status
            CHECK (Status IN (N''active'', N''inactive''))');
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'FK_CinemaFilms_AssignedBy')
        EXEC(N'ALTER TABLE dbo.CinemaFilms ADD CONSTRAINT FK_CinemaFilms_AssignedBy
            FOREIGN KEY (AssignedByUserId) REFERENCES dbo.Users(Id)');
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'FK_CinemaFilms_UnassignedBy')
        EXEC(N'ALTER TABLE dbo.CinemaFilms ADD CONSTRAINT FK_CinemaFilms_UnassignedBy
            FOREIGN KEY (UnassignedByUserId) REFERENCES dbo.Users(Id)');
    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name=N'IX_CinemaFilms_Cinema_Status'
                     AND object_id=OBJECT_ID(N'dbo.CinemaFilms'))
        EXEC(N'CREATE INDEX IX_CinemaFilms_Cinema_Status
            ON dbo.CinemaFilms(CinemaId, Status, FilmId)');

    IF COL_LENGTH(N'dbo.Promotions', N'CreatedByUserId') IS NULL
        ALTER TABLE dbo.Promotions ADD CreatedByUserId INT NULL;
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'FK_Promotions_CreatedBy')
        EXEC(N'ALTER TABLE dbo.Promotions ADD CONSTRAINT FK_Promotions_CreatedBy
            FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users(Id)');
    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name=N'IX_Promotions_CreatedBy_Status'
                     AND object_id=OBJECT_ID(N'dbo.Promotions'))
        EXEC(N'CREATE INDEX IX_Promotions_CreatedBy_Status
            ON dbo.Promotions(CreatedByUserId, Status, CreatedAt DESC)');

    IF COL_LENGTH(N'dbo.UserAppeals', N'CinemaId') IS NULL
        ALTER TABLE dbo.UserAppeals ADD CinemaId INT NULL;
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'FK_UserAppeals_Cinema')
        EXEC(N'ALTER TABLE dbo.UserAppeals ADD CONSTRAINT FK_UserAppeals_Cinema
            FOREIGN KEY (CinemaId) REFERENCES dbo.Cinemas(Id)');
    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name=N'IX_UserAppeals_Cinema_Type_Status'
                     AND object_id=OBJECT_ID(N'dbo.UserAppeals'))
        EXEC(N'CREATE INDEX IX_UserAppeals_Cinema_Type_Status
            ON dbo.UserAppeals(CinemaId, AppealType, Status, CreatedAt DESC)');

    IF COL_LENGTH(N'dbo.AdminNotifications', N'CinemaId') IS NULL
        ALTER TABLE dbo.AdminNotifications ADD CinemaId INT NULL;
    IF COL_LENGTH(N'dbo.AdminNotifications', N'CreatedByUserId') IS NULL
        ALTER TABLE dbo.AdminNotifications ADD CreatedByUserId INT NULL;
    IF COL_LENGTH(N'dbo.AdminNotifications', N'EventType') IS NULL
        ALTER TABLE dbo.AdminNotifications ADD EventType NVARCHAR(50) NULL;
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'FK_AdminNotifications_Cinema')
        EXEC(N'ALTER TABLE dbo.AdminNotifications ADD CONSTRAINT FK_AdminNotifications_Cinema
            FOREIGN KEY (CinemaId) REFERENCES dbo.Cinemas(Id)');
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'FK_AdminNotifications_CreatedBy')
        EXEC(N'ALTER TABLE dbo.AdminNotifications ADD CONSTRAINT FK_AdminNotifications_CreatedBy
            FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users(Id)');
    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name=N'IX_AdminNotifications_Cinema_Event_CreatedAt'
                     AND object_id=OBJECT_ID(N'dbo.AdminNotifications'))
        EXEC(N'CREATE INDEX IX_AdminNotifications_Cinema_Event_CreatedAt
            ON dbo.AdminNotifications(CinemaId, EventType, CreatedAt DESC)');

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name=N'UX_Rooms_Id_CinemaId'
                     AND object_id=OBJECT_ID(N'dbo.Rooms'))
        CREATE UNIQUE INDEX UX_Rooms_Id_CinemaId ON dbo.Rooms(Id, CinemaId);

    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'FK_Showtimes_RoomCinema')
        ALTER TABLE dbo.Showtimes WITH CHECK ADD CONSTRAINT FK_Showtimes_RoomCinema
            FOREIGN KEY (RoomId, CinemaId) REFERENCES dbo.Rooms(Id, CinemaId);
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'FK_Showtimes_CinemaFilm')
        ALTER TABLE dbo.Showtimes WITH CHECK ADD CONSTRAINT FK_Showtimes_CinemaFilm
            FOREIGN KEY (CinemaId, FilmId) REFERENCES dbo.CinemaFilms(CinemaId, FilmId);

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE name=N'CK_Users_OperationalRoleCinema'
                     AND parent_object_id=OBJECT_ID(N'dbo.Users'))
        ALTER TABLE dbo.Users WITH CHECK ADD CONSTRAINT CK_Users_OperationalRoleCinema
            CHECK (Role NOT IN (N'manager', N'staff') OR CinemaId IS NOT NULL);

    COMMIT TRANSACTION;
    PRINT 'fix43_cinema_governance_core.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- Rollback requires dropping dependent approval/showtime constraints first.

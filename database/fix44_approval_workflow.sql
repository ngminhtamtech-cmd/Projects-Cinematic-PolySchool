-- fix44_approval_workflow.sql
-- Typed manager-to-admin approval workflow for films and rooms.
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'CineBookDB' AND DB_NAME() NOT LIKE N'CineBookIT[_]%'
    THROW 52054, 'fix44 only accepts CineBookDB or an ephemeral CineBookIT_* database.', 1;
IF OBJECT_ID(N'dbo.CinemaFilms', N'U') IS NULL
   OR COL_LENGTH(N'dbo.CinemaFilms', N'Status') IS NULL
    THROW 52055, 'Run fix43 before fix44.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.ApprovalRequests', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.ApprovalRequests (
            Id INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_ApprovalRequests PRIMARY KEY,
            RequestType NVARCHAR(30) NOT NULL,
            CinemaId INT NOT NULL,
            RequestedByUserId INT NOT NULL,
            RequestKey NVARCHAR(200) NOT NULL,
            Status NVARCHAR(20) NOT NULL CONSTRAINT DF_ApprovalRequests_Status DEFAULT N'PENDING',
            RequestedAt DATETIME2(3) NOT NULL CONSTRAINT DF_ApprovalRequests_RequestedAt DEFAULT SYSDATETIME(),
            ReviewedByUserId INT NULL,
            ReviewedAt DATETIME2(3) NULL,
            ReviewNote NVARCHAR(1000) NULL,
            ResolvedEntityType NVARCHAR(30) NULL,
            ResolvedEntityId INT NULL,
            RowVersion ROWVERSION NOT NULL,
            CONSTRAINT FK_ApprovalRequests_Cinema FOREIGN KEY (CinemaId) REFERENCES dbo.Cinemas(Id),
            CONSTRAINT FK_ApprovalRequests_RequestedBy FOREIGN KEY (RequestedByUserId) REFERENCES dbo.Users(Id),
            CONSTRAINT FK_ApprovalRequests_ReviewedBy FOREIGN KEY (ReviewedByUserId) REFERENCES dbo.Users(Id),
            CONSTRAINT CK_ApprovalRequests_Type CHECK (RequestType IN
                (N'FILM_ASSIGN',N'FILM_CREATE',N'FILM_UPDATE',N'FILM_UNASSIGN',N'ROOM_CREATE')),
            CONSTRAINT CK_ApprovalRequests_Status CHECK (Status IN
                (N'PENDING',N'APPROVED',N'REJECTED',N'CANCELLED')),
            CONSTRAINT CK_ApprovalRequests_Review CHECK (
                (Status=N'PENDING' AND ReviewedByUserId IS NULL AND ReviewedAt IS NULL)
                OR (Status=N'CANCELLED' AND ReviewedByUserId IS NULL)
                OR (Status IN (N'APPROVED',N'REJECTED') AND ReviewedByUserId IS NOT NULL AND ReviewedAt IS NOT NULL)
            ),
            CONSTRAINT CK_ApprovalRequests_RejectionNote CHECK
                (Status<>N'REJECTED' OR NULLIF(LTRIM(RTRIM(ReviewNote)),N'') IS NOT NULL)
        );
    END;

    IF OBJECT_ID(N'dbo.FilmRequestDetails', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.FilmRequestDetails (
            RequestId INT NOT NULL CONSTRAINT PK_FilmRequestDetails PRIMARY KEY,
            ExistingFilmId INT NULL,
            Title NVARCHAR(255) NULL,
            OtherTitles NVARCHAR(255) NULL,
            Actors NVARCHAR(500) NULL,
            Directors NVARCHAR(255) NULL,
            Rating FLOAT NULL,
            ReleaseDate DATE NULL,
            EndDate DATE NULL,
            DurationMinutes INT NULL,
            AgeRating NVARCHAR(10) NULL,
            TrailerUrl NVARCHAR(255) NULL,
            Thumbnail NVARCHAR(255) NULL,
            Banner NVARCHAR(255) NULL,
            Language NVARCHAR(50) NULL,
            Subtitles NVARCHAR(50) NULL,
            Description NVARCHAR(MAX) NULL,
            Country NVARCHAR(100) NULL,
            Format NVARCHAR(50) NULL,
            FilmStatus NVARCHAR(20) NULL,
            SourceFilmUpdatedAt DATETIME2(3) NULL,
            CONSTRAINT FK_FilmRequestDetails_Request FOREIGN KEY (RequestId)
                REFERENCES dbo.ApprovalRequests(Id) ON DELETE CASCADE,
            CONSTRAINT FK_FilmRequestDetails_ExistingFilm FOREIGN KEY (ExistingFilmId)
                REFERENCES dbo.Films(Id)
        );
    END;

    IF OBJECT_ID(N'dbo.FilmRequestCategories', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.FilmRequestCategories (
            RequestId INT NOT NULL,
            CategoryId INT NOT NULL,
            CONSTRAINT PK_FilmRequestCategories PRIMARY KEY (RequestId, CategoryId),
            CONSTRAINT FK_FilmRequestCategories_Request FOREIGN KEY (RequestId)
                REFERENCES dbo.ApprovalRequests(Id) ON DELETE CASCADE,
            CONSTRAINT FK_FilmRequestCategories_Category FOREIGN KEY (CategoryId)
                REFERENCES dbo.Categories(Id)
        );
    END;

    IF OBJECT_ID(N'dbo.RoomRequestDetails', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.RoomRequestDetails (
            RequestId INT NOT NULL CONSTRAINT PK_RoomRequestDetails PRIMARY KEY,
            RoomName NVARCHAR(50) NOT NULL,
            RoomType NVARCHAR(20) NOT NULL,
            LayoutRows INT NOT NULL,
            SeatsPerRow INT NOT NULL,
            CONSTRAINT FK_RoomRequestDetails_Request FOREIGN KEY (RequestId)
                REFERENCES dbo.ApprovalRequests(Id) ON DELETE CASCADE,
            CONSTRAINT CK_RoomRequestDetails_Dimensions CHECK
                (LayoutRows BETWEEN 1 AND 26 AND SeatsPerRow BETWEEN 1 AND 50)
        );
    END;

    IF OBJECT_ID(N'dbo.RoomRequestSeats', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.RoomRequestSeats (
            RequestId INT NOT NULL,
            SeatKey NVARCHAR(20) NOT NULL,
            RowLabel NVARCHAR(5) NOT NULL,
            SeatNumber INT NOT NULL,
            SeatType NVARCHAR(20) NOT NULL,
            PriceSurcharge DECIMAL(19,2) NOT NULL CONSTRAINT DF_RoomRequestSeats_Surcharge DEFAULT 0,
            CONSTRAINT PK_RoomRequestSeats PRIMARY KEY (RequestId, SeatKey),
            CONSTRAINT FK_RoomRequestSeats_Request FOREIGN KEY (RequestId)
                REFERENCES dbo.ApprovalRequests(Id) ON DELETE CASCADE,
            CONSTRAINT CK_RoomRequestSeats_Type CHECK (SeatType IN (N'standard',N'vip',N'couple')),
            CONSTRAINT CK_RoomRequestSeats_Number CHECK (SeatNumber>0),
            CONSTRAINT CK_RoomRequestSeats_Surcharge CHECK (PriceSurcharge>=0)
        );
    END;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name=N'UX_ApprovalRequests_PendingKey'
                     AND object_id=OBJECT_ID(N'dbo.ApprovalRequests'))
        CREATE UNIQUE INDEX UX_ApprovalRequests_PendingKey
            ON dbo.ApprovalRequests(CinemaId, RequestType, RequestKey)
            WHERE Status=N'PENDING';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name=N'IX_ApprovalRequests_AdminQueue'
                     AND object_id=OBJECT_ID(N'dbo.ApprovalRequests'))
        CREATE INDEX IX_ApprovalRequests_AdminQueue
            ON dbo.ApprovalRequests(Status, RequestedAt DESC, CinemaId, RequestType)
            INCLUDE(RequestedByUserId, ReviewedByUserId, ResolvedEntityId);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name=N'IX_ApprovalRequests_ManagerQueue'
                     AND object_id=OBJECT_ID(N'dbo.ApprovalRequests'))
        CREATE INDEX IX_ApprovalRequests_ManagerQueue
            ON dbo.ApprovalRequests(RequestedByUserId, Status, RequestedAt DESC);

    IF COL_LENGTH(N'dbo.AdminNotifications', N'ApprovalRequestId') IS NULL
        ALTER TABLE dbo.AdminNotifications ADD ApprovalRequestId INT NULL;
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
                   WHERE name=N'FK_AdminNotifications_ApprovalRequest')
        EXEC(N'ALTER TABLE dbo.AdminNotifications ADD CONSTRAINT FK_AdminNotifications_ApprovalRequest
            FOREIGN KEY (ApprovalRequestId) REFERENCES dbo.ApprovalRequests(Id)');

    COMMIT TRANSACTION;
    PRINT 'fix44_approval_workflow.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- Rollback: drop notification FK/column, then RoomRequestSeats, RoomRequestDetails,
-- FilmRequestCategories, FilmRequestDetails and ApprovalRequests.

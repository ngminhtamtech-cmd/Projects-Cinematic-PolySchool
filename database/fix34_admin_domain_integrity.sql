-- fix34_admin_domain_integrity.sql
-- Explicit cinema/room classification and indexes used by admin impact queues.
-- This migration intentionally does not invent legacy film end dates or VIP labels.
SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH(N'dbo.Cinemas', N'CinemaType') IS NULL
        ALTER TABLE dbo.Cinemas ADD CinemaType NVARCHAR(20) NULL;

    IF COL_LENGTH(N'dbo.Rooms', N'Status') IS NULL
        ALTER TABLE dbo.Rooms ADD Status NVARCHAR(20) NOT NULL
            CONSTRAINT DF_Rooms_Status DEFAULT N'active';

    IF COL_LENGTH(N'dbo.Rooms', N'RoomType') IS NULL
        ALTER TABLE dbo.Rooms ADD RoomType NVARCHAR(20) NULL;

    -- Preflight legacy rows.  A production database with an unclassified row
    -- must stop here so an administrator can classify it explicitly.
    -- Dynamic SQL is intentional: on a legacy database the columns were just
    -- added above, and SQL Server otherwise compiles the whole batch before
    -- those ALTER TABLE statements have taken effect.
    EXEC sys.sp_executesql N'
        IF EXISTS (SELECT 1 FROM dbo.Cinemas
                   WHERE CinemaType IS NULL OR UPPER(CinemaType) NOT IN (N''STANDARD'', N''VIP''))
        BEGIN
            SELECT Id, Name, CinemaType FROM dbo.Cinemas
            WHERE CinemaType IS NULL OR UPPER(CinemaType) NOT IN (N''STANDARD'', N''VIP'');
            THROW 50034, ''fix34 preflight failed: classify every cinema as STANDARD or VIP before migration.'', 1;
        END;';

    EXEC sys.sp_executesql N'
        IF EXISTS (SELECT 1 FROM dbo.Rooms
                   WHERE RoomType IS NULL OR UPPER(RoomType) NOT IN (N''STANDARD'', N''VIP''))
        BEGIN
            SELECT Id, CinemaId, Name, RoomType FROM dbo.Rooms
            WHERE RoomType IS NULL OR UPPER(RoomType) NOT IN (N''STANDARD'', N''VIP'');
            THROW 50035, ''fix34 preflight failed: classify every room as STANDARD or VIP before migration.'', 1;
        END;';

    EXEC sys.sp_executesql N'
        IF EXISTS (SELECT 1 FROM dbo.Rooms
                   WHERE Status IS NULL OR LOWER(Status) NOT IN (N''active'', N''inactive''))
        BEGIN
            SELECT Id, CinemaId, Name, Status FROM dbo.Rooms
            WHERE Status IS NULL OR LOWER(Status) NOT IN (N''active'', N''inactive'');
            THROW 50037, ''fix34 preflight failed: normalize every room status to active or inactive.'', 1;
        END;';

    IF COL_LENGTH(N'dbo.Films', N'EndDate') IS NULL
        THROW 50033, 'fix34 preflight failed: run the required film EndDate migration first.', 1;

    EXEC sys.sp_executesql N'
        IF EXISTS (SELECT 1 FROM dbo.Films WHERE EndDate IS NULL)
        BEGIN
            SELECT Id, Title, ReleaseDate, EndDate, Status FROM dbo.Films WHERE EndDate IS NULL;
            THROW 50036, ''fix34 preflight failed: enter EndDate for every legacy film before migration.'', 1;
        END;
        IF EXISTS (SELECT 1 FROM dbo.Films WHERE ReleaseDate IS NOT NULL AND EndDate < ReleaseDate)
        BEGIN
            SELECT Id, Title, ReleaseDate, EndDate, Status FROM dbo.Films WHERE ReleaseDate IS NOT NULL AND EndDate < ReleaseDate;
            THROW 50038, ''fix34 preflight failed: EndDate must not precede ReleaseDate.'', 1;
        END;';

    IF EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id=OBJECT_ID(N'dbo.Films') AND name=N'EndDate' AND is_nullable=1)
    BEGIN
        -- fix16 may already have indexed the nullable legacy column. SQL Server will not
        -- change nullability while that index exists, so rebuild it in this transaction.
        IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Films')
                   AND name=N'IX_Films_ReleaseDate_EndDate')
            DROP INDEX IX_Films_ReleaseDate_EndDate ON dbo.Films;
        ALTER TABLE dbo.Films ALTER COLUMN EndDate DATE NOT NULL;
        CREATE NONCLUSTERED INDEX IX_Films_ReleaseDate_EndDate
            ON dbo.Films(ReleaseDate,EndDate) INCLUDE(Title,Status);
    END;
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Films')
                   AND name=N'IX_Films_ReleaseDate_EndDate')
        CREATE NONCLUSTERED INDEX IX_Films_ReleaseDate_EndDate
            ON dbo.Films(ReleaseDate,EndDate) INCLUDE(Title,Status);
    IF EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE parent_object_id=OBJECT_ID(N'dbo.Films')
                 AND name=N'CK_Films_EndDate_After_ReleaseDate')
        ALTER TABLE dbo.Films WITH CHECK CHECK CONSTRAINT CK_Films_EndDate_After_ReleaseDate;

    IF NOT EXISTS (SELECT 1 FROM sys.default_constraints dc
                   WHERE dc.parent_object_id=OBJECT_ID(N'dbo.Cinemas')
                     AND dc.parent_column_id=COLUMNPROPERTY(OBJECT_ID(N'dbo.Cinemas'),N'CinemaType','ColumnId'))
        ALTER TABLE dbo.Cinemas ADD CONSTRAINT DF_Cinemas_CinemaType DEFAULT N'STANDARD' FOR CinemaType;
    IF NOT EXISTS (SELECT 1 FROM sys.default_constraints dc
                   WHERE dc.parent_object_id=OBJECT_ID(N'dbo.Rooms')
                     AND dc.parent_column_id=COLUMNPROPERTY(OBJECT_ID(N'dbo.Rooms'),N'RoomType','ColumnId'))
        ALTER TABLE dbo.Rooms ADD CONSTRAINT DF_Rooms_RoomType DEFAULT N'STANDARD' FOR RoomType;
    ALTER TABLE dbo.Cinemas ALTER COLUMN CinemaType NVARCHAR(20) NOT NULL;
    ALTER TABLE dbo.Rooms ALTER COLUMN RoomType NVARCHAR(20) NOT NULL;

    EXEC sys.sp_executesql N'
        IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N''CK_Cinemas_CinemaType'')
            ALTER TABLE dbo.Cinemas ADD CONSTRAINT CK_Cinemas_CinemaType
                CHECK (UPPER(CinemaType) IN (N''STANDARD'', N''VIP''));
        IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N''CK_Rooms_RoomType'')
            ALTER TABLE dbo.Rooms ADD CONSTRAINT CK_Rooms_RoomType
                CHECK (UPPER(RoomType) IN (N''STANDARD'', N''VIP''));
        IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N''CK_Rooms_Status'')
            ALTER TABLE dbo.Rooms ADD CONSTRAINT CK_Rooms_Status
                CHECK (LOWER(Status) IN (N''active'', N''inactive''));';

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'IX_UserAppeals_Type_Status_Order'
                   AND object_id=OBJECT_ID(N'dbo.UserAppeals'))
        CREATE INDEX IX_UserAppeals_Type_Status_Order
            ON dbo.UserAppeals(AppealType, Status, OrderId, CreatedAt);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'IX_Orders_Promotion_Status'
                   AND object_id=OBJECT_ID(N'dbo.Orders'))
        CREATE INDEX IX_Orders_Promotion_Status
            ON dbo.Orders(PromotionId, PaymentStatus, OrderStatus);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'IX_PromotionUsage_Promotion'
                   AND object_id=OBJECT_ID(N'dbo.PromotionUsage'))
        CREATE INDEX IX_PromotionUsage_Promotion ON dbo.PromotionUsage(PromotionId);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'IX_UserVouchers_Promotion'
                   AND object_id=OBJECT_ID(N'dbo.UserVouchers'))
        CREATE INDEX IX_UserVouchers_Promotion ON dbo.UserVouchers(PromotionId);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
-- ROLLBACK;

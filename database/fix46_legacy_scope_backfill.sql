-- fix46_legacy_scope_backfill.sql
-- Move legacy operational data into explicit cinema scope without losing history.
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'CineBookDB' AND DB_NAME() NOT LIKE N'CineBookIT[_]%'
    THROW 52076, 'fix46 only accepts CineBookDB or an ephemeral CineBookIT_* database.', 1;
IF OBJECT_ID(N'dbo.CinemaContents', N'U') IS NULL
   OR OBJECT_ID(N'dbo.ApprovalRequests', N'U') IS NULL
   OR COL_LENGTH(N'dbo.Promotions', N'CreatedByUserId') IS NULL
    THROW 52077, 'Run fixes 43-45 before fix46.', 1;

DECLARE @StaffCount INT=(SELECT COUNT(*) FROM dbo.Users WHERE Role=N'staff');
DECLARE @IsProduction BIT=CASE WHEN DB_NAME()=N'CineBookDB' THEN 1 ELSE 0 END;
DECLARE @BaseCinemaCount INT=(
    SELECT COUNT(*) FROM dbo.Cinemas
    WHERE Name=N'CineBook Cơ sở 1' AND ISNULL(Status,N'active')=N'active'
);
DECLARE @BaseCinemaId INT=(
    SELECT MIN(Id) FROM dbo.Cinemas
    WHERE Name=N'CineBook Cơ sở 1' AND ISNULL(Status,N'active')=N'active'
);
IF @IsProduction=0 AND @BaseCinemaId IS NULL
    SELECT @BaseCinemaId=MIN(Id) FROM dbo.Cinemas
    WHERE ISNULL(Status,N'active')=N'active';
DECLARE @LegacyPromotionCount INT=(SELECT COUNT(*) FROM dbo.Promotions WHERE CreatedByUserId IS NULL);
DECLARE @GlobalComboCount INT=(SELECT COUNT(*) FROM dbo.ComboFoods WHERE CinemaId IS NULL);
DECLARE @OwnerAdminId INT=(
    SELECT MIN(Id) FROM dbo.Users
    WHERE Role=N'admin' AND ISNULL(Deleted,0)=0 AND ISNULL(IsLocked,0)=0
);

IF @StaffCount>0 AND @BaseCinemaId IS NULL
    THROW 52078, 'An active base cinema is required to backfill staff.', 1;
IF @StaffCount>0 AND @IsProduction=1 AND @BaseCinemaCount<>1
    THROW 52078, 'Exactly one active cinema named CineBook Cơ sở 1 is required to backfill staff.', 1;
IF @StaffCount>0 AND @IsProduction=1 AND @BaseCinemaId<>7
    THROW 52079, 'Production CineBook Cơ sở 1 must resolve to CinemaId 7.', 1;
IF @LegacyPromotionCount>0 AND @OwnerAdminId IS NULL
    THROW 52080, 'An active admin is required to own legacy promotions.', 1;

PRINT CONCAT('fix46 preflight: staff=', @StaffCount,
             ', legacyPromotions=', @LegacyPromotionCount,
             ', globalCombos=', @GlobalComboCount);

BEGIN TRY
    BEGIN TRANSACTION;

    IF @StaffCount>0
        UPDATE dbo.Users SET CinemaId=@BaseCinemaId, UpdatedAt=GETDATE()
        WHERE Role=N'staff' AND (CinemaId IS NULL OR CinemaId<>@BaseCinemaId);

    UPDATE dbo.Promotions SET CreatedByUserId=@OwnerAdminId
    WHERE CreatedByUserId IS NULL;

    UPDATE a
       SET CinemaId=s.CinemaId
    FROM dbo.UserAppeals a
    JOIN dbo.Orders o ON o.Id=a.OrderId
    JOIN dbo.Showtimes s ON s.Id=o.ShowtimeId
    WHERE a.AppealType=N'refund'
      AND (a.CinemaId IS NULL OR a.CinemaId<>s.CinemaId);

    UPDATE a
       SET CinemaId=u.CinemaId
    FROM dbo.UserAppeals a
    JOIN dbo.Users u ON u.Id=a.UserId
    WHERE a.AppealType=N'account'
      AND u.Role IN (N'staff',N'manager')
      AND u.CinemaId IS NOT NULL
      AND (a.CinemaId IS NULL OR a.CinemaId<>u.CinemaId);

    IF COL_LENGTH(N'dbo.ComboFoods', N'LegacySourceComboId') IS NULL
        ALTER TABLE dbo.ComboFoods ADD LegacySourceComboId INT NULL;
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'FK_ComboFoods_LegacySource')
        EXEC(N'ALTER TABLE dbo.ComboFoods ADD CONSTRAINT FK_ComboFoods_LegacySource
            FOREIGN KEY (LegacySourceComboId) REFERENCES dbo.ComboFoods(Id)');

    EXEC(N'INSERT INTO dbo.ComboFoods(Name, Image, Price, Description, Status, CinemaId, LegacySourceComboId)
        SELECT source.Name, source.Image, source.Price, source.Description,
               CASE WHEN source.Status=N''deleted'' THEN N''inactive'' ELSE source.Status END,
               cinema.Id, source.Id
        FROM dbo.ComboFoods source
        CROSS JOIN dbo.Cinemas cinema
        WHERE source.CinemaId IS NULL
          AND source.LegacySourceComboId IS NULL
          AND ISNULL(cinema.Status,N''active'')=N''active''
          AND NOT EXISTS (
              SELECT 1 FROM dbo.ComboFoods clone
              WHERE clone.CinemaId=cinema.Id AND clone.LegacySourceComboId=source.Id
          )');

    UPDATE dbo.ComboFoods SET Status=N'inactive'
    WHERE CinemaId IS NULL AND Status=N'active';

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE name=N'CK_ComboFoods_ActiveCinema'
                     AND parent_object_id=OBJECT_ID(N'dbo.ComboFoods'))
        ALTER TABLE dbo.ComboFoods WITH CHECK ADD CONSTRAINT CK_ComboFoods_ActiveCinema
            CHECK (Status<>N'active' OR CinemaId IS NOT NULL);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name=N'UX_ComboFoods_LegacyClone'
                     AND object_id=OBJECT_ID(N'dbo.ComboFoods'))
        EXEC(N'CREATE UNIQUE INDEX UX_ComboFoods_LegacyClone
            ON dbo.ComboFoods(CinemaId, LegacySourceComboId)
            WHERE CinemaId IS NOT NULL AND LegacySourceComboId IS NOT NULL');

    COMMIT TRANSACTION;
    PRINT 'fix46_legacy_scope_backfill.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- Rollback is data-sensitive: restore staff cinema assignments and combo states only
-- from the pre-migration backup; cloned rows keep explicit LegacySourceComboId for review.

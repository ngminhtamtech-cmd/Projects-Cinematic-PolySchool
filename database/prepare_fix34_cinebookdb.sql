-- Curated production data required before fix34_admin_domain_integrity.sql.
-- Every value in this file was explicitly confirmed by the CineBook owner.
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'CineBookDB'
    THROW 50134, 'prepare_fix34_cinebookdb.sql may run only on CineBookDB.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH(N'dbo.Cinemas', N'CinemaType') IS NULL
        ALTER TABLE dbo.Cinemas ADD CinemaType NVARCHAR(20) NULL;
    IF COL_LENGTH(N'dbo.Rooms', N'RoomType') IS NULL
        ALTER TABLE dbo.Rooms ADD RoomType NVARCHAR(20) NULL;

    IF NOT EXISTS (SELECT 1 FROM dbo.Cinemas WHERE Id=7  AND Name=N'FPT Center - 138 Nguyễn Thị Thập')
        THROW 50135, 'Cinema 7 identity changed; refusing curated classification.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.Cinemas WHERE Id=8  AND Name=N'Galaxy FPT Cinema')
        THROW 50136, 'Cinema 8 identity changed; refusing curated classification.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.Cinemas WHERE Id=9  AND Name=N'Rạp Đặc Biệt IMAX AOEN MALL')
        THROW 50137, 'Cinema 9 identity changed; refusing curated classification.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.Cinemas WHERE Id=10 AND Name=N'rạp chạy 1')
        THROW 50138, 'Cinema 10 identity changed; refusing curated classification.', 1;

    IF NOT EXISTS (SELECT 1 FROM dbo.Rooms WHERE Id=2 AND CinemaId=8  AND Name=N'Phong Galaxy FPT Cinema')
        THROW 50139, 'Room 2 identity changed; refusing curated classification.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.Rooms WHERE Id=3 AND CinemaId=7  AND Name=N'QA Phong Rap7')
        THROW 50140, 'Room 3 identity changed; refusing curated classification.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.Rooms WHERE Id=4 AND CinemaId=10 AND Name=N'Phòng 02 - 2D')
        THROW 50141, 'Room 4 identity changed; refusing curated classification.', 1;

    EXEC sys.sp_executesql N'
        UPDATE dbo.Cinemas SET CinemaType=N''STANDARD'' WHERE Id IN (7,8);
        UPDATE dbo.Cinemas SET CinemaType=N''VIP''      WHERE Id IN (9,10);
        UPDATE dbo.Rooms   SET RoomType=N''STANDARD''   WHERE Id IN (2,3);
        UPDATE dbo.Rooms   SET RoomType=N''VIP''        WHERE Id=4;';

    -- Compile these checks only after the optional legacy columns exist.
    EXEC sys.sp_executesql N'
        IF EXISTS (SELECT 1 FROM dbo.Cinemas WHERE CinemaType IS NULL OR UPPER(CinemaType) NOT IN (N''STANDARD'',N''VIP''))
            THROW 50142, ''An unconfirmed cinema remains; classify it explicitly before fix34.'', 1;
        IF EXISTS (SELECT 1 FROM dbo.Rooms WHERE RoomType IS NULL OR UPPER(RoomType) NOT IN (N''STANDARD'',N''VIP''))
            THROW 50143, ''An unconfirmed room remains; classify it explicitly before fix34.'', 1;';

    IF NOT EXISTS (SELECT 1 FROM dbo.Films WHERE Id=21 AND Title=N'Kiểm thử lần 1')
        THROW 50144, 'Film 21 identity changed; refusing curated lifecycle update.', 1;
    IF (SELECT COUNT(*) FROM dbo.Showtimes WHERE FilmId=21) <> 3
        THROW 50145, 'Film 21 showtime count changed from the approved baseline (3).', 1;
    IF (SELECT COUNT(*) FROM dbo.Orders o JOIN dbo.Showtimes s ON s.Id=o.ShowtimeId WHERE s.FilmId=21) <> 7
        THROW 50146, 'Film 21 order count changed from the approved baseline (7).', 1;
    IF (SELECT COUNT(*) FROM dbo.Comments WHERE FilmId=21) <> 1
        THROW 50147, 'Film 21 comment count changed from the approved baseline (1).', 1;

    UPDATE dbo.Films
    SET EndDate=CONVERT(date,'2026-08-02'), Status=N'ended', UpdatedAt=GETDATE()
    WHERE Id=21;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

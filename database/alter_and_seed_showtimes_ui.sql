-- ============================================================================
-- SQL Script: Seed Suất chiếu phong phú nhiều Phòng & Phiên bản (Lồng tiếng / Thuyết minh / Phụ đề)
-- Database: CineBookDB
-- ============================================================================

USE CineBookDB;
GO

-- 1. Đảm bảo các cột Format, Version, Language sẵn sàng
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'Showtimes') AND name = 'Format')
    ALTER TABLE Showtimes ADD Format NVARCHAR(50) NULL DEFAULT '2D';
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'Showtimes') AND name = 'Version')
    ALTER TABLE Showtimes ADD Version NVARCHAR(50) NULL DEFAULT N'Lồng tiếng';
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'Showtimes') AND name = 'Language')
    ALTER TABLE Showtimes ADD Language NVARCHAR(50) NULL DEFAULT N'Tiếng Việt';
GO

-- 2. Tạo hoặc cập nhật Phòng 02 - 2D cho FPT Center
DECLARE @CinemaId INT;
SELECT TOP 1 @CinemaId = Id FROM Cinemas WHERE Name LIKE N'%FPT Center%';

IF @CinemaId IS NOT NULL
BEGIN
    DECLARE @Room01Id INT, @Room02Id INT;
    SELECT TOP 1 @Room01Id = Id FROM Rooms WHERE CinemaId = @CinemaId AND Name LIKE N'%Phòng 01%';
    SELECT TOP 1 @Room02Id = Id FROM Rooms WHERE CinemaId = @CinemaId AND Name LIKE N'%Phòng 02%';

    IF @Room02Id IS NULL
    BEGIN
        INSERT INTO Rooms (CinemaId, Name) VALUES (@CinemaId, N'Phòng 02 - 2D Standard');
        SET @Room02Id = SCOPE_IDENTITY();

        -- Sinh sơ đồ ghế cho Phòng 02
        DECLARE @r INT = 1, @c INT, @rowChar CHAR(1);
        WHILE @r <= 5
        BEGIN
            SET @rowChar = CHAR(64 + @r);
            SET @c = 1;
            WHILE @c <= 10
            BEGIN
                INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey)
                VALUES (@Room02Id, @rowChar, @c, 'standard', @rowChar + CAST(@c AS VARCHAR(5)));
                SET @c = @c + 1;
            END
            SET @r = @r + 1;
        END
    END

    -- Cập nhật các suất chiếu hiện tại của FPT Center phân bổ Format & Version rõ ràng
    DECLARE @FilmId INT;
    SELECT TOP 1 @FilmId = Id FROM Films WHERE Status = 'showing' OR Title LIKE N'%FPT%';

    IF @FilmId IS NOT NULL AND @Room01Id IS NOT NULL
    BEGIN
        -- Cập nhật Phòng 01: IMAX (2D Lồng tiếng)
        UPDATE Showtimes 
        SET Format = N'IMAX 2D', Version = N'Lồng tiếng', Language = N'Tiếng Việt'
        WHERE CinemaId = @CinemaId AND RoomId = @Room01Id;

        -- Bổ sung thêm suất chiếu cho Phòng 02: 2D Thuyết minh nếu chưa tồn tại
        IF NOT EXISTS (SELECT 1 FROM Showtimes WHERE CinemaId = @CinemaId AND RoomId = @Room02Id)
        BEGIN
            INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice, Format, Version, Language)
            VALUES 
            (@FilmId, @CinemaId, @Room02Id, DATEADD(HOUR, 3, GETDATE()), DATEADD(MINUTE, 320, GETDATE()), 90000.00, N'2D', N'Thuyết minh', N'Tiếng Việt'),
            (@FilmId, @CinemaId, @Room02Id, DATEADD(HOUR, 6, GETDATE()), DATEADD(MINUTE, 500, GETDATE()), 90000.00, N'2D', N'Thuyết minh', N'Tiếng Việt'),
            (@FilmId, @CinemaId, @Room02Id, DATEADD(DAY, 1, DATEADD(HOUR, 15, CAST(CAST(GETDATE() AS DATE) AS DATETIME))), DATEADD(DAY, 1, DATEADD(HOUR, 17, CAST(CAST(GETDATE() AS DATE) AS DATETIME))), 90000.00, N'2D', N'Thuyết minh', N'Tiếng Việt');

            -- Sinh ShowtimeSeats cho Phòng 02
            INSERT INTO ShowtimeSeats (ShowtimeId, SeatId, Status, ExtraFee)
            SELECT st.Id, s.Id, 'available', 0.00
            FROM Showtimes st
            CROSS JOIN Seats s
            WHERE st.CinemaId = @CinemaId AND st.RoomId = @Room02Id AND s.RoomId = @Room02Id
              AND st.Id NOT IN (SELECT DISTINCT ShowtimeId FROM ShowtimeSeats);
        END
    END
END
GO

PRINT N'--- HOÀN TẤT SEED DỮ LIỆU SUẤT CHIẾU NHIỀU PHÒNG & PHIÊN BẢN ---';
GO

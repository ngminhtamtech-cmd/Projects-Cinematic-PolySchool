-- ============================================================================
-- SQL Script: Seed Dữ Liệu Test FPT Center & Phim FPT chạy test
-- Mô tả: Dọn dẹp dữ liệu mẫu cũ và nạp 1 rạp FPT duy nhất, 1 phim FPT duy nhất cùng suất chiếu & sơ đồ ghế
-- Ngày thực thi: 2026-07-24
-- Database: CineBookDB
-- ============================================================================

USE CineBookDB;
GO

IF OBJECT_ID(N'OrderComboFoods', N'U') IS NOT NULL DELETE FROM OrderComboFoods;
IF OBJECT_ID(N'OrderSeats', N'U') IS NOT NULL DELETE FROM OrderSeats;
IF OBJECT_ID(N'OrderCombos', N'U') IS NOT NULL DELETE FROM OrderCombos;
IF OBJECT_ID(N'Orders', N'U') IS NOT NULL DELETE FROM Orders;
IF OBJECT_ID(N'ShowtimeSeats', N'U') IS NOT NULL DELETE FROM ShowtimeSeats;
IF OBJECT_ID(N'Showtimes', N'U') IS NOT NULL DELETE FROM Showtimes;
IF OBJECT_ID(N'Seats', N'U') IS NOT NULL DELETE FROM Seats;
IF OBJECT_ID(N'Rooms', N'U') IS NOT NULL DELETE FROM Rooms;
IF OBJECT_ID(N'Comments', N'U') IS NOT NULL DELETE FROM Comments;
IF OBJECT_ID(N'FilmCategories', N'U') IS NOT NULL DELETE FROM FilmCategories;
IF OBJECT_ID(N'Films', N'U') IS NOT NULL DELETE FROM Films;
DELETE FROM Cinemas WHERE Name NOT LIKE N'%FPT%';

GO

PRINT N'--- KHỞI TẠO RẠP FPT CENTER ---';

DECLARE @CityId INT;
SELECT TOP 1 @CityId = Id FROM Cities WHERE Name LIKE N'%Hồ Chí Minh%' OR Name LIKE N'%Đà Nẵng%' OR Name LIKE N'%Hà Nội%';
IF @CityId IS NULL SELECT TOP 1 @CityId = Id FROM Cities;

DECLARE @CinemaId INT;
SELECT TOP 1 @CinemaId = Id FROM Cinemas WHERE Name LIKE N'%FPT Center%';
IF @CinemaId IS NULL
BEGIN
    INSERT INTO Cinemas (CityId, Name, Address, Phone, Status, Avatar, BannerUrl, Description)
    VALUES (@CityId, N'FPT Center - 138 Nguyễn Thị Thập', N'138 Nguyễn Thị Thập, P. Bình Thuận, Q. 7, TP.HCM', N'1900 6600', N'active', N'/assets/img/cinemas/fpt-center.jpg', N'/assets/img/cinemas/fpt-center-banner.jpg', N'Cụm rạp chiếu chuẩn quốc tế FPT Center với công nghệ màn hình IMAX & hệ thống âm thanh Dolby Atmos.');
    SET @CinemaId = SCOPE_IDENTITY();
END
ELSE
BEGIN
    UPDATE Cinemas 
    SET Name = N'FPT Center - 138 Nguyễn Thị Thập', 
        Address = N'138 Nguyễn Thị Thập, P. Bình Thuận, Q. 7, TP.HCM',
        Phone = N'1900 6600', Status = N'active',
        BannerUrl = N'/assets/img/cinemas/fpt-center-banner.jpg'
    WHERE Id = @CinemaId;
END

PRINT N'--- KHỞI TẠO PHIM FPT CHẠY TEST ---';

DECLARE @FilmId INT;
SELECT TOP 1 @FilmId = Id FROM Films WHERE Title = N'FPT chạy test';
IF @FilmId IS NULL
BEGIN
    INSERT INTO Films (Title, OtherTitles, Directors, Actors, DurationMinutes, AgeRating, Language, Subtitles, Country, Format, Status, Description, ReleaseDate, Thumbnail, Banner, Rating, TrailerUrl)
    VALUES (N'FPT chạy test', N'FPT System Test Movie', N'FPT Education', N'Sinh viên FPT, Giảng viên FPT', 120, N'T16', N'Tiếng Việt', N'Phụ đề Tiếng Anh', N'Việt Nam', N'IMAX 2D', N'showing', N'Bộ phim thử nghiệm hệ thống đặt vé xem phim CineBook tại rạp FPT Center.', GETDATE(), N'/assets/img/films/fpt-test-poster.jpg', N'/assets/img/films/fpt-test-banner.jpg', 9.5, N'https://www.youtube.com/watch?v=8Qn_spdM5Zg');
    SET @FilmId = SCOPE_IDENTITY();
END

PRINT N'--- KHỞI TẠO PHÒNG CHIẾU & SƠ ĐỒ GHẾ ---';

DECLARE @RoomId INT;
SELECT TOP 1 @RoomId = Id FROM Rooms WHERE CinemaId = @CinemaId AND Name = N'Phòng 01 - IMAX';
IF @RoomId IS NULL
BEGIN
    INSERT INTO Rooms (CinemaId, Name) VALUES (@CinemaId, N'Phòng 01 - IMAX');
    SET @RoomId = SCOPE_IDENTITY();
END

-- Khởi tạo Ma trận Ghế chuẩn cho Phòng 01 (Hàng A-F: Thường, Hàng G-H: VIP, Hàng I-J: Ghế Đôi)
DELETE FROM Seats WHERE RoomId = @RoomId;

DECLARE @r INT = 1, @c INT;
DECLARE @rowChar CHAR(1);
WHILE @r <= 6
BEGIN
    SET @rowChar = CHAR(64 + @r);
    SET @c = 1;
    WHILE @c <= 12
    BEGIN
        INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey)
        VALUES (@RoomId, @rowChar, @c, 'standard', @rowChar + CAST(@c AS VARCHAR(5)));
        SET @c = @c + 1;
    END
    SET @r = @r + 1;
END

WHILE @r <= 8
BEGIN
    SET @rowChar = CHAR(64 + @r);
    SET @c = 1;
    WHILE @c <= 12
    BEGIN
        INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey)
        VALUES (@RoomId, @rowChar, @c, 'vip', @rowChar + CAST(@c AS VARCHAR(5)));
        SET @c = @c + 1;
    END
    SET @r = @r + 1;
END

WHILE @r <= 10
BEGIN
    SET @rowChar = CHAR(64 + @r);
    SET @c = 1;
    WHILE @c <= 11
    BEGIN
        INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey)
        VALUES (@RoomId, @rowChar, @c, 'couple', @rowChar + CAST(@c AS VARCHAR(5)) + '-' + CAST((@c+1) AS VARCHAR(5)));
        SET @c = @c + 2;
    END
    SET @r = @r + 1;
END

PRINT N'--- KHỞI TẠO SUẤT CHIẾU & GHẾ SUẤT CHIẾU ---';

-- Suất chiếu Hôm nay (sau thời điểm hiện tại 2 tiếng & 5 tiếng)
INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice)
VALUES 
(@FilmId, @CinemaId, @RoomId, DATEADD(HOUR, 2, GETDATE()), DATEADD(MINUTE, 260, GETDATE()), 100000.00),
(@FilmId, @CinemaId, @RoomId, DATEADD(HOUR, 5, GETDATE()), DATEADD(MINUTE, 440, GETDATE()), 100000.00);

-- Suất chiếu Ngày mai
INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice)
VALUES 
(@FilmId, @CinemaId, @RoomId, DATEADD(DAY, 1, DATEADD(HOUR, 10, CAST(CAST(GETDATE() AS DATE) AS DATETIME))), DATEADD(DAY, 1, DATEADD(HOUR, 12, CAST(CAST(GETDATE() AS DATE) AS DATETIME))), 100000.00),
(@FilmId, @CinemaId, @RoomId, DATEADD(DAY, 1, DATEADD(HOUR, 14, CAST(CAST(GETDATE() AS DATE) AS DATETIME))), DATEADD(DAY, 1, DATEADD(HOUR, 16, CAST(CAST(GETDATE() AS DATE) AS DATETIME))), 100000.00),
(@FilmId, @CinemaId, @RoomId, DATEADD(DAY, 1, DATEADD(HOUR, 19, CAST(CAST(GETDATE() AS DATE) AS DATETIME))), DATEADD(DAY, 1, DATEADD(HOUR, 21, CAST(CAST(GETDATE() AS DATE) AS DATETIME))), 100000.00);

-- Suất chiếu Ngày kia
INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice)
VALUES 
(@FilmId, @CinemaId, @RoomId, DATEADD(DAY, 2, DATEADD(HOUR, 15, CAST(CAST(GETDATE() AS DATE) AS DATETIME))), DATEADD(DAY, 2, DATEADD(HOUR, 17, CAST(CAST(GETDATE() AS DATE) AS DATETIME))), 100000.00),
(@FilmId, @CinemaId, @RoomId, DATEADD(DAY, 2, DATEADD(HOUR, 20, CAST(CAST(GETDATE() AS DATE) AS DATETIME))), DATEADD(DAY, 2, DATEADD(HOUR, 22, CAST(CAST(GETDATE() AS DATE) AS DATETIME))), 100000.00);

-- Tự động sinh ShowtimeSeats cho các suất chiếu vừa tạo
INSERT INTO ShowtimeSeats (ShowtimeId, SeatId, Status, ExtraFee)
SELECT st.Id, s.Id, 'available',
       CASE WHEN s.SeatType = 'vip' THEN 20000.00
            WHEN s.SeatType = 'couple' THEN 100000.00
            ELSE 0.00 END
FROM Showtimes st
CROSS JOIN Seats s
WHERE st.FilmId = @FilmId AND s.RoomId = @RoomId;

PRINT N'=== HOÀN TẤT SEED DỮ LIỆU FPT TEST THÀNH CÔNG ===';
GO

USE CineBookDB;
GO

-- Cleanup previous partial run
DELETE FROM ShowtimeSeats WHERE ShowtimeId IN (SELECT Id FROM Showtimes WHERE FilmId IN (SELECT Id FROM Films WHERE Title IN (N'Chiến Binh Ánh Sáng', N'Kẻ Săn Bóng Đêm', N'Biển Xanh Sâu Thẳm', N'Trận Chiến Cuối Cùng', N'Mật Vụ Hoàng Hôn', N'Thành Phố Sương Mù', N'Vũ Điệu Hoang Dã', N'Hành Tinh Băng Giá', N'Vũ Trụ Vô Tận', N'Kỷ Nguyên Mới', N'Đường Đua Rực Lửa')));
DELETE FROM Showtimes WHERE FilmId IN (SELECT Id FROM Films WHERE Title IN (N'Chiến Binh Ánh Sáng', N'Kẻ Săn Bóng Đêm', N'Biển Xanh Sâu Thẳm', N'Trận Chiến Cuối Cùng', N'Mật Vụ Hoàng Hôn', N'Thành Phố Sương Mù', N'Vũ Điệu Hoang Dã', N'Hành Tinh Băng Giá', N'Vũ Trụ Vô Tận', N'Kỷ Nguyên Mới', N'Đường Đua Rực Lửa'));
DELETE FROM Films WHERE Title IN (N'Chiến Binh Ánh Sáng', N'Kẻ Săn Bóng Đêm', N'Biển Xanh Sâu Thẳm', N'Trận Chiến Cuối Cùng', N'Mật Vụ Hoàng Hôn', N'Thành Phố Sương Mù', N'Vũ Điệu Hoang Dã', N'Hành Tinh Băng Giá', N'Vũ Trụ Vô Tận', N'Kỷ Nguyên Mới', N'Đường Đua Rực Lửa');
DELETE FROM Seats WHERE RoomId IN (SELECT Id FROM Rooms WHERE CinemaId IN (SELECT Id FROM Cinemas WHERE Name IN (N'CineBook Hà Đông', N'CineBook Cầu Giấy', N'CineBook Quận 7', N'CineBook Thủ Đức', N'CineBook Hải Châu')));
DELETE FROM Rooms WHERE CinemaId IN (SELECT Id FROM Cinemas WHERE Name IN (N'CineBook Hà Đông', N'CineBook Cầu Giấy', N'CineBook Quận 7', N'CineBook Thủ Đức', N'CineBook Hải Châu'));
DELETE FROM Cinemas WHERE Name IN (N'CineBook Hà Đông', N'CineBook Cầu Giấy', N'CineBook Quận 7', N'CineBook Thủ Đức', N'CineBook Hải Châu');
DELETE FROM Cities WHERE Name = N'Đà Nẵng';
GO

-- 1. Insert new city
IF NOT EXISTS (SELECT 1 FROM Cities WHERE Name = N'Đà Nẵng')
BEGIN
    INSERT INTO Cities (Name) VALUES (N'Đà Nẵng');
END
GO

-- 2. Insert new cinemas
DECLARE @HCMC INT = 1; -- TP. Hồ Chí Minh
DECLARE @HN INT = 2; -- Hà Nội
DECLARE @DN INT = (SELECT TOP 1 Id FROM Cities WHERE Name = N'Đà Nẵng' OR Name LIKE '%N%ng%');

IF NOT EXISTS (SELECT 1 FROM Cinemas WHERE Name = N'CineBook Hà Đông')
    INSERT INTO Cinemas (CityId, Name, Address, Description) VALUES (@HN, N'CineBook Hà Đông', N'15 Quang Trung, Hà Đông, Hà Nội', N'Phòng chiếu IMAX tiêu chuẩn quốc tế.');

IF NOT EXISTS (SELECT 1 FROM Cinemas WHERE Name = N'CineBook Cầu Giấy')
    INSERT INTO Cinemas (CityId, Name, Address, Description) VALUES (@HN, N'CineBook Cầu Giấy', N'233 Cầu Giấy, Hà Nội', N'Không gian hiện đại, trẻ trung.');

IF NOT EXISTS (SELECT 1 FROM Cinemas WHERE Name = N'CineBook Quận 7')
    INSERT INTO Cinemas (CityId, Name, Address, Description) VALUES (@HCMC, N'CineBook Quận 7', N'101 Tôn Dật Tiên, Quận 7, TP. HCM', N'Cụm rạp cao cấp có phòng Gold Class.');

IF NOT EXISTS (SELECT 1 FROM Cinemas WHERE Name = N'CineBook Thủ Đức')
    INSERT INTO Cinemas (CityId, Name, Address, Description) VALUES (@HCMC, N'CineBook Thủ Đức', N'Võ Văn Ngân, Thủ Đức, TP. HCM', N'Điểm hẹn điện ảnh của học sinh sinh viên.');

IF NOT EXISTS (SELECT 1 FROM Cinemas WHERE Name = N'CineBook Hải Châu')
    INSERT INTO Cinemas (CityId, Name, Address, Description) VALUES (@DN, N'CineBook Hải Châu', N'36 Trần Phú, Hải Châu, Đà Nẵng', N'Rạp phim trung tâm thành phố Đà Nẵng.');
GO

-- 3. Insert rooms and seats for new cinemas
DECLARE @C1 INT = (SELECT Id FROM Cinemas WHERE Name = N'CineBook Hà Đông');
DECLARE @C2 INT = (SELECT Id FROM Cinemas WHERE Name = N'CineBook Cầu Giấy');
DECLARE @C3 INT = (SELECT Id FROM Cinemas WHERE Name = N'CineBook Quận 7');
DECLARE @C4 INT = (SELECT Id FROM Cinemas WHERE Name = N'CineBook Thủ Đức');
DECLARE @C5 INT = (SELECT Id FROM Cinemas WHERE Name = N'CineBook Hải Châu');

-- Room and seats for Hà Đông
IF NOT EXISTS (SELECT 1 FROM Rooms WHERE CinemaId = @C1 AND Name = N'IMAX Room')
BEGIN
    INSERT INTO Rooms (CinemaId, Name) VALUES (@C1, N'IMAX Room');
    DECLARE @R1 INT = SCOPE_IDENTITY();
    INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey)
    SELECT @R1, RowLabel, SeatNumber, CASE WHEN RowLabel IN (N'D', N'E') THEN N'vip' ELSE N'standard' END, CONCAT(RowLabel, SeatNumber)
    FROM (VALUES (N'A'), (N'B'), (N'C'), (N'D'), (N'E')) r(RowLabel)
    CROSS JOIN (VALUES (1), (2), (3), (4), (5), (6), (7), (8)) s(SeatNumber);
END

-- Room and seats for Cầu Giấy
IF NOT EXISTS (SELECT 1 FROM Rooms WHERE CinemaId = @C2 AND Name = N'Room 01')
BEGIN
    INSERT INTO Rooms (CinemaId, Name) VALUES (@C2, N'Room 01');
    DECLARE @R2 INT = SCOPE_IDENTITY();
    INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey)
    SELECT @R2, RowLabel, SeatNumber, CASE WHEN RowLabel IN (N'D', N'E') THEN N'vip' ELSE N'standard' END, CONCAT(RowLabel, SeatNumber)
    FROM (VALUES (N'A'), (N'B'), (N'C'), (N'D'), (N'E')) r(RowLabel)
    CROSS JOIN (VALUES (1), (2), (3), (4), (5), (6), (7), (8)) s(SeatNumber);
END

-- Room and seats for Quận 7
IF NOT EXISTS (SELECT 1 FROM Rooms WHERE CinemaId = @C3 AND Name = N'Gold Class Room')
BEGIN
    INSERT INTO Rooms (CinemaId, Name) VALUES (@C3, N'Gold Class Room');
    DECLARE @R3 INT = SCOPE_IDENTITY();
    INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey)
    SELECT @R3, RowLabel, SeatNumber, CASE WHEN RowLabel IN (N'D', N'E') THEN N'vip' ELSE N'standard' END, CONCAT(RowLabel, SeatNumber)
    FROM (VALUES (N'A'), (N'B'), (N'C'), (N'D'), (N'E')) r(RowLabel)
    CROSS JOIN (VALUES (1), (2), (3), (4), (5), (6), (7), (8)) s(SeatNumber);
END

-- Room and seats for Thủ Đức
IF NOT EXISTS (SELECT 1 FROM Rooms WHERE CinemaId = @C4 AND Name = N'Room 01')
BEGIN
    INSERT INTO Rooms (CinemaId, Name) VALUES (@C4, N'Room 01');
    DECLARE @R4 INT = SCOPE_IDENTITY();
    INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey)
    SELECT @R4, RowLabel, SeatNumber, CASE WHEN RowLabel IN (N'D', N'E') THEN N'vip' ELSE N'standard' END, CONCAT(RowLabel, SeatNumber)
    FROM (VALUES (N'A'), (N'B'), (N'C'), (N'D'), (N'E')) r(RowLabel)
    CROSS JOIN (VALUES (1), (2), (3), (4), (5), (6), (7), (8)) s(SeatNumber);
END

-- Room and seats for Hải Châu
IF NOT EXISTS (SELECT 1 FROM Rooms WHERE CinemaId = @C5 AND Name = N'Room 01')
BEGIN
    INSERT INTO Rooms (CinemaId, Name) VALUES (@C5, N'Room 01');
    DECLARE @R5 INT = SCOPE_IDENTITY();
    INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey)
    SELECT @R5, RowLabel, SeatNumber, CASE WHEN RowLabel IN (N'D', N'E') THEN N'vip' ELSE N'standard' END, CONCAT(RowLabel, SeatNumber)
    FROM (VALUES (N'A'), (N'B'), (N'C'), (N'D'), (N'E')) r(RowLabel)
    CROSS JOIN (VALUES (1), (2), (3), (4), (5), (6), (7), (8)) s(SeatNumber);
END
GO

-- 4. Insert 11 new movies using requested directors
DECLARE @Today DATE = CONVERT(DATE, GETDATE());

-- Now Showing (Đang chiếu - Release date <= today)
IF NOT EXISTS (SELECT 1 FROM Films WHERE Title = N'Chiến Binh Ánh Sáng')
    INSERT INTO Films (Title, Actors, Directors, Rating, ReleaseDate, DurationMinutes, AgeRating, TrailerUrl, Thumbnail, Language, Subtitles, Description)
    VALUES (N'Chiến Binh Ánh Sáng', N'Thanh Sơn, Khả Ngân', N'Nguyễn Minh Tâm', 4.7, DATEADD(DAY, -5, @Today), 120, N'T13', N'https://example.com/trailer', N'https://images.unsplash.com/photo-1536440136628-849c177e76a1?auto=format&fit=crop&w=400&q=80', N'Tiếng Việt', N'Không', N'Hành trình giải cứu trái đất của các chiến binh dũng cảm.');

IF NOT EXISTS (SELECT 1 FROM Films WHERE Title = N'Kẻ Săn Bóng Đêm')
    INSERT INTO Films (Title, Actors, Directors, Rating, ReleaseDate, DurationMinutes, AgeRating, TrailerUrl, Thumbnail, Language, Subtitles, Description)
    VALUES (N'Kẻ Săn Bóng Đêm', N'Quốc Trường, Thu Quỳnh', N'Lương Hoàng Dũng', 4.5, DATEADD(DAY, -2, @Today), 110, N'T16', N'https://example.com/trailer', N'https://images.unsplash.com/photo-1594909122845-11baa439b7bf?auto=format&fit=crop&w=400&q=80', N'Tiếng Việt', N'EN', N'Cuộc đối đầu kịch tính giữa thiện và ác trong thế giới ngầm.');

IF NOT EXISTS (SELECT 1 FROM Films WHERE Title = N'Biển Xanh Sâu Thẳm')
    INSERT INTO Films (Title, Actors, Directors, Rating, ReleaseDate, DurationMinutes, AgeRating, TrailerUrl, Thumbnail, Language, Subtitles, Description)
    VALUES (N'Biển Xanh Sâu Thẳm', N'Lâm Vỹ Dạ, Hứa Minh Đạt', N'Nguyễn Vĩnh Đức', 4.2, DATEADD(DAY, -10, @Today), 95, N'P', N'https://example.com/trailer', N'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=400&q=80', N'Tiếng Việt', N'EN', N'Thám hiểm đại dương với những loài sinh vật kỳ thú.');

IF NOT EXISTS (SELECT 1 FROM Films WHERE Title = N'Trận Chiến Cuối Cùng')
    INSERT INTO Films (Title, Actors, Directors, Rating, ReleaseDate, DurationMinutes, AgeRating, TrailerUrl, Thumbnail, Language, Subtitles, Description)
    VALUES (N'Trận Chiến Cuối Cùng', N'Kiều Minh Tuấn, Kaity Nguyễn', N'Nguyễn Lâm Thi', 4.6, DATEADD(DAY, -3, @Today), 145, N'T18', N'https://example.com/trailer', N'https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?auto=format&fit=crop&w=400&q=80', N'Tiếng Việt', N'Không', N'Hành động nghẹt thở và kịch tính đến những phút cuối cùng.');

-- Upcoming (Sắp chiếu - Release date > today)
IF NOT EXISTS (SELECT 1 FROM Films WHERE Title = N'Mật Vụ Hoàng Hôn')
    INSERT INTO Films (Title, Actors, Directors, Rating, ReleaseDate, DurationMinutes, AgeRating, TrailerUrl, Thumbnail, Language, Subtitles, Description)
    VALUES (N'Mật Vụ Hoàng Hôn', N'Nhan Phúc Vinh, Diễm My', N'Nguyễn Minh Tâm', NULL, DATEADD(DAY, 10, @Today), 115, N'T16', N'https://example.com/trailer', N'https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?auto=format&fit=crop&w=400&q=80', N'Tiếng Việt', N'EN', N'Nhiệm vụ bí mật giải cứu con tin đầy nguy hiểm.');

IF NOT EXISTS (SELECT 1 FROM Films WHERE Title = N'Thành Phố Sương Mù')
    INSERT INTO Films (Title, Actors, Directors, Rating, ReleaseDate, DurationMinutes, AgeRating, TrailerUrl, Thumbnail, Language, Subtitles, Description)
    VALUES (N'Thành Phố Sương Mù', N'Mạnh Trường, Phương Oanh', N'Lương Hoàng Dũng', NULL, DATEADD(DAY, 15, @Today), 105, N'T13', N'https://example.com/trailer', N'https://images.unsplash.com/photo-1520038410233-7141be7e6f97?auto=format&fit=crop&w=400&q=80', N'Tiếng Việt', N'EN', N'Câu chuyện tình yêu đầy trắc trở tại Đà Lạt mộng mơ.');

IF NOT EXISTS (SELECT 1 FROM Films WHERE Title = N'Vũ Điệu Hoang Dã')
    INSERT INTO Films (Title, Actors, Directors, Rating, ReleaseDate, DurationMinutes, AgeRating, TrailerUrl, Thumbnail, Language, Subtitles, Description)
    VALUES (N'Vũ Điệu Hoang Dã', N'Dàn nghệ sĩ múa Việt Nam', N'Nguyễn Vĩnh Đức', NULL, DATEADD(DAY, 20, @Today), 90, N'P', N'https://example.com/trailer', N'https://images.unsplash.com/photo-1566698621401-f8829751412b?auto=format&fit=crop&w=400&q=80', N'Tiếng Việt', N'Không', N'Những vũ điệu dân gian kết hợp hiện đại đầy sức sống.');

IF NOT EXISTS (SELECT 1 FROM Films WHERE Title = N'Hành Tinh Băng Giá')
    INSERT INTO Films (Title, Actors, Directors, Rating, ReleaseDate, DurationMinutes, AgeRating, TrailerUrl, Thumbnail, Language, Subtitles, Description)
    VALUES (N'Hành Tinh Băng Giá', N'Lồng tiếng Việt', N'Nguyễn Lâm Thi', NULL, DATEADD(DAY, 25, @Today), 130, N'P', N'https://example.com/trailer', N'https://images.unsplash.com/photo-1559251606-c623743a6d76?auto=format&fit=crop&w=400&q=80', N'Tiếng Việt', N'EN', N'Cuộc phiêu lưu tìm kiếm sự sống trên hành tinh lạnh giá.');

-- IMAX (Phim IMAX đang chiếu)
IF NOT EXISTS (SELECT 1 FROM Films WHERE Title = N'Vũ Trụ Vô Tận')
    INSERT INTO Films (Title, Actors, Directors, Rating, ReleaseDate, DurationMinutes, AgeRating, TrailerUrl, Thumbnail, Language, Subtitles, Description)
    VALUES (N'Vũ Trụ Vô Tận', N'Đình Tú, Huyền Lizzie', N'Nguyễn Minh Tâm', 4.9, DATEADD(DAY, -1, @Today), 140, N'P', N'https://example.com/trailer', N'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=400&q=80', N'Tiếng Việt', N'EN', N'Được quay bằng máy quay IMAX chuyên dụng, mang lại trải nghiệm vũ trụ sống động.');

IF NOT EXISTS (SELECT 1 FROM Films WHERE Title = N'Kỷ Nguyên Mới')
    INSERT INTO Films (Title, Actors, Directors, Rating, ReleaseDate, DurationMinutes, AgeRating, TrailerUrl, Thumbnail, Language, Subtitles, Description)
    VALUES (N'Kỷ Nguyên Mới', N'Việt Anh, Lương Thu Trang', N'Lương Hoàng Dũng', 4.8, DATEADD(DAY, -4, @Today), 125, N'T13', N'https://example.com/trailer', N'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80', N'Tiếng Việt', N'EN', N'Tương lai của nhân loại dưới sự phát triển của trí tuệ nhân tạo.');

IF NOT EXISTS (SELECT 1 FROM Films WHERE Title = N'Đường Đua Rực Lửa')
    INSERT INTO Films (Title, Actors, Directors, Rating, ReleaseDate, DurationMinutes, AgeRating, TrailerUrl, Thumbnail, Language, Subtitles, Description)
    VALUES (N'Đường Đua Rực Lửa', N'Song Luân, Rima Thanh Vy', N'Nguyễn Vĩnh Đức', 4.6, DATEADD(DAY, -7, @Today), 118, N'T16', N'https://example.com/trailer', N'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=400&q=80', N'Tiếng Việt', N'Không', N'Những màn đua xe nghẹt thở trên định dạng màn hình cực đại.');
GO

-- 5. Insert showtimes and showtime seats for now showing and IMAX films
DECLARE @C1 INT = (SELECT Id FROM Cinemas WHERE Name = N'CineBook Hà Đông');
DECLARE @C2 INT = (SELECT Id FROM Cinemas WHERE Name = N'CineBook Cầu Giấy');
DECLARE @C3 INT = (SELECT Id FROM Cinemas WHERE Name = N'CineBook Quận 7');
DECLARE @C4 INT = (SELECT Id FROM Cinemas WHERE Name = N'CineBook Thủ Đức');
DECLARE @C5 INT = (SELECT Id FROM Cinemas WHERE Name = N'CineBook Hải Châu');
DECLARE @C0 INT = (SELECT Id FROM Cinemas WHERE Name = N'CineBook Quận 1');

-- Get room IDs
DECLARE @R1 INT = (SELECT TOP 1 Id FROM Rooms WHERE CinemaId = @C1);
DECLARE @R2 INT = (SELECT TOP 1 Id FROM Rooms WHERE CinemaId = @C2);
DECLARE @R3 INT = (SELECT TOP 1 Id FROM Rooms WHERE CinemaId = @C3);
DECLARE @R4 INT = (SELECT TOP 1 Id FROM Rooms WHERE CinemaId = @C4);
DECLARE @R5 INT = (SELECT TOP 1 Id FROM Rooms WHERE CinemaId = @C5);
DECLARE @R0 INT = (SELECT TOP 1 Id FROM Rooms WHERE CinemaId = @C0);

DECLARE @Film1 INT = (SELECT Id FROM Films WHERE Title = N'Chiến Binh Ánh Sáng');
DECLARE @Film2 INT = (SELECT Id FROM Films WHERE Title = N'Kẻ Săn Bóng Đêm');
DECLARE @Film3 INT = (SELECT Id FROM Films WHERE Title = N'Biển Xanh Sâu Thẳm');
DECLARE @Film4 INT = (SELECT Id FROM Films WHERE Title = N'Trận Chiến Cuối Cùng');
DECLARE @Film9 INT = (SELECT Id FROM Films WHERE Title = N'Vũ Trụ Vô Tận');
DECLARE @Film10 INT = (SELECT Id FROM Films WHERE Title = N'Kỷ Nguyên Mới');
DECLARE @Film11 INT = (SELECT Id FROM Films WHERE Title = N'Đường Đua Rực Lửa');

DECLARE @TodayDate DATE = CONVERT(DATE, GETDATE());

-- Helper table to cross join cinemas, rooms and films
DECLARE @ShowtimeQueue TABLE (
    FilmId INT,
    CinemaId INT,
    RoomId INT,
    StartHour INT
);

INSERT INTO @ShowtimeQueue (FilmId, CinemaId, RoomId, StartHour)
VALUES
(@Film1, @C1, @R1, 10), (@Film1, @C3, @R3, 14), (@Film1, @C5, @R5, 18),
(@Film2, @C2, @R2, 11), (@Film2, @C4, @R4, 15), (@Film2, @C0, @R0, 19),
(@Film3, @C1, @R1, 13), (@Film3, @C4, @R4, 17), (@Film3, @C5, @R5, 20),
(@Film4, @C3, @R3, 11), (@Film4, @C2, @R2, 16), (@Film4, @C0, @R0, 21),
-- IMAX films
(@Film9, @C1, @R1, 19), (@Film9, @C3, @R3, 21),
(@Film10, @C2, @R2, 14), (@Film10, @C4, @R4, 20),
(@Film11, @C5, @R5, 15), (@Film11, @C0, @R0, 16);

-- Loop today and tomorrow
DECLARE @DayOffset INT = 0;
WHILE @DayOffset <= 1
BEGIN
    DECLARE @TargetDate DATE = DATEADD(DAY, @DayOffset, @TodayDate);
    
    DECLARE @fId INT, @cId INT, @rId INT, @sh INT;
    DECLARE st_cursor CURSOR FOR
    SELECT FilmId, CinemaId, RoomId, StartHour FROM @ShowtimeQueue;
    
    OPEN st_cursor;
    FETCH NEXT FROM st_cursor INTO @fId, @cId, @rId, @sh;
    
    WHILE @@FETCH_STATUS = 0
    BEGIN
        IF @fId IS NOT NULL AND @cId IS NOT NULL AND @rId IS NOT NULL
        BEGIN
            DECLARE @StartDT DATETIME = DATEADD(HOUR, @sh, CONVERT(DATETIME, @TargetDate));
            DECLARE @EndDT DATETIME = DATEADD(MINUTE, 120, @StartDT);
            
            -- Check if showtime already exists
            IF NOT EXISTS (SELECT 1 FROM Showtimes WHERE FilmId = @fId AND CinemaId = @cId AND RoomId = @rId AND StartTime = @StartDT)
            BEGIN
                INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice)
                VALUES (@fId, @cId, @rId, @StartDT, @EndDT, 90000);
                
                DECLARE @NewStId INT = SCOPE_IDENTITY();
                
                -- Seed showtime seats
                INSERT INTO ShowtimeSeats (ShowtimeId, SeatId, Status, ExtraFee)
                SELECT @NewStId, Id, N'available', CASE WHEN SeatType = N'vip' THEN 20000 ELSE 0 END
                FROM Seats
                WHERE RoomId = @rId;
            END
        END
        FETCH NEXT FROM st_cursor INTO @fId, @cId, @rId, @sh;
    END
    
    CLOSE st_cursor;
    DEALLOCATE st_cursor;
    
    SET @DayOffset = @DayOffset + 1;
END
GO

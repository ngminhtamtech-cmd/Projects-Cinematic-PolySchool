USE CineBookDB;
GO

-- Demo admin account:
-- email: admin@cinebook.local
-- password: 123456
-- Change this password immediately after first login in a real deployment.
IF NOT EXISTS (SELECT 1 FROM Users WHERE Email = N'admin@cinebook.local')
BEGIN
    INSERT INTO Users (Username, FullName, Email, PasswordHash, Role)
    VALUES (
        N'admin',
        N'CineBook Admin',
        N'admin@cinebook.local',
        N'$2a$10$9IqXSwX.sWmcdDgNK1nbZOeqWl7nsj9evEcePEbmFFHl21.BGwiZW',
        N'admin'
    );
END

INSERT INTO Categories (Title)
SELECT v.Title
FROM (VALUES (N'Hành động'), (N'Phiêu lưu'), (N'Tình cảm'), (N'Hoạt hình')) v(Title)
WHERE NOT EXISTS (SELECT 1 FROM Categories c WHERE c.Title = v.Title);

IF NOT EXISTS (SELECT 1 FROM Films)
BEGIN
    INSERT INTO Films (Title, Actors, Directors, Rating, ReleaseDate, DurationMinutes, AgeRating, TrailerUrl, Thumbnail, Language, Subtitles, Description)
    VALUES
    (N'Đêm Thành Phố', N'Nguyễn An, Lê Vy', N'Trần Minh', 4.6, '2026-07-01', 118, N'T13', N'https://example.com/trailer-1', NULL, N'Tiếng Việt', N'Không', N'Một bộ phim hành động đô thị với nhịp dựng nhanh.'),
    (N'Ga Cuối Mùa Hè', N'Hoàng Nam, Mai Chi', N'Phạm Khoa', 4.3, '2026-07-15', 104, N'T16', N'https://example.com/trailer-2', NULL, N'Tiếng Việt', N'EN', N'Câu chuyện tình cảm nhẹ nhàng trong những ngày cuối hè.'),
    (N'Vũ Trụ Nhỏ', N'Lồng tiếng bởi CineBook Cast', N'Lê Duy', 4.8, '2026-08-02', 96, N'P', N'https://example.com/trailer-3', NULL, N'Tiếng Việt', N'EN', N'Hoạt hình phiêu lưu dành cho gia đình.');
END

IF NOT EXISTS (SELECT 1 FROM Cities WHERE Name = N'TP. Hồ Chí Minh')
BEGIN
    INSERT INTO Cities (Name) VALUES (N'TP. Hồ Chí Minh'), (N'Hà Nội');
END

IF NOT EXISTS (SELECT 1 FROM Cinemas)
BEGIN
    DECLARE @CityId INT = (SELECT TOP 1 Id FROM Cities WHERE Name = N'TP. Hồ Chí Minh');
    INSERT INTO Cinemas (CityId, Name, Address, Description)
    VALUES (@CityId, N'CineBook Quận 1', N'01 Nguyễn Huệ, Quận 1', N'Rạp trung tâm với phòng chiếu hiện đại.');
END

IF NOT EXISTS (SELECT 1 FROM Rooms)
BEGIN
    DECLARE @CinemaId INT = (SELECT TOP 1 Id FROM Cinemas);
    INSERT INTO Rooms (CinemaId, Name) VALUES (@CinemaId, N'Room 01');

    DECLARE @RoomId INT = SCOPE_IDENTITY();
    INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey)
    SELECT @RoomId, RowLabel, SeatNumber,
           CASE WHEN RowLabel IN (N'D', N'E') THEN N'vip' ELSE N'standard' END,
           CONCAT(RowLabel, SeatNumber)
    FROM (VALUES (N'A'), (N'B'), (N'C'), (N'D'), (N'E')) r(RowLabel)
    CROSS JOIN (VALUES (1), (2), (3), (4), (5), (6), (7), (8)) s(SeatNumber);
END

IF NOT EXISTS (SELECT 1 FROM Showtimes)
BEGIN
    DECLARE @FilmId INT = (SELECT TOP 1 Id FROM Films ORDER BY Id);
    DECLARE @CinemaId2 INT = (SELECT TOP 1 Id FROM Cinemas ORDER BY Id);
    DECLARE @RoomId2 INT = (SELECT TOP 1 Id FROM Rooms ORDER BY Id);
    INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice)
    VALUES (@FilmId, @CinemaId2, @RoomId2, DATEADD(DAY, 1, DATEADD(HOUR, 19, CONVERT(DATETIME, CONVERT(DATE, GETDATE())))), DATEADD(DAY, 1, DATEADD(HOUR, 21, CONVERT(DATETIME, CONVERT(DATE, GETDATE())))), 90000);

    DECLARE @ShowtimeId INT = SCOPE_IDENTITY();
    INSERT INTO ShowtimeSeats (ShowtimeId, SeatId, Status, ExtraFee)
    SELECT @ShowtimeId, Id, N'available', CASE WHEN SeatType = N'vip' THEN 20000 ELSE 0 END
    FROM Seats
    WHERE RoomId = @RoomId2;
END

IF NOT EXISTS (SELECT 1 FROM ComboFoods)
BEGIN
    INSERT INTO ComboFoods (Name, Price, Description, Status)
    VALUES (N'Combo Bắp Nước', 69000, N'1 bắp lớn + 1 nước lớn', N'active'),
           (N'Combo Couple', 119000, N'1 bắp lớn + 2 nước', N'active');
END

IF NOT EXISTS (SELECT 1 FROM Promotions WHERE Code = N'CINE10')
BEGIN
    INSERT INTO Promotions (Code, Description, DiscountPercent, MaxDiscount, StartDate, EndDate, UsageLimit, Status)
    VALUES (N'CINE10', N'Giảm 10% cho đơn đầu tiên', 10, 50000, CONVERT(DATE, GETDATE()), DATEADD(MONTH, 3, CONVERT(DATE, GETDATE())), 1000, N'active');
END

-- Demo customer accounts:
-- password for all demo customers: 123456
INSERT INTO Users (Username, FullName, Email, PasswordHash, Phone, Address, Avatar, Role)
SELECT v.Username, v.FullName, v.Email, v.PasswordHash, v.Phone, v.Address, NULL, N'member'
FROM (VALUES
    (N'khanhlinh', N'Nguyễn Khánh Linh', N'khanh.linh@cinebook.local', N'$2a$10$9IqXSwX.sWmcdDgNK1nbZOeqWl7nsj9evEcePEbmFFHl21.BGwiZW', N'0901000001', N'12 Nguyễn Trãi, Quận 5, TP. Hồ Chí Minh'),
    (N'minhanh', N'Trần Minh Anh', N'minh.anh@cinebook.local', N'$2a$10$9IqXSwX.sWmcdDgNK1nbZOeqWl7nsj9evEcePEbmFFHl21.BGwiZW', N'0901000002', N'45 Lê Lợi, Quận 1, TP. Hồ Chí Minh'),
    (N'hoangphuc', N'Lê Hoàng Phúc', N'hoang.phuc@cinebook.local', N'$2a$10$9IqXSwX.sWmcdDgNK1nbZOeqWl7nsj9evEcePEbmFFHl21.BGwiZW', N'0901000003', N'88 Hai Bà Trưng, Quận 3, TP. Hồ Chí Minh'),
    (N'thuyduong', N'Phạm Thùy Dương', N'thuy.duong@cinebook.local', N'$2a$10$9IqXSwX.sWmcdDgNK1nbZOeqWl7nsj9evEcePEbmFFHl21.BGwiZW', N'0901000004', N'21 Phan Đình Phùng, Ba Đình, Hà Nội'),
    (N'quochuy', N'Võ Quốc Huy', N'quoc.huy@cinebook.local', N'$2a$10$9IqXSwX.sWmcdDgNK1nbZOeqWl7nsj9evEcePEbmFFHl21.BGwiZW', N'0901000005', N'09 Bạch Đằng, Hải Châu, Đà Nẵng'),
    (N'ngocmai', N'Đặng Ngọc Mai', N'ngoc.mai@cinebook.local', N'$2a$10$9IqXSwX.sWmcdDgNK1nbZOeqWl7nsj9evEcePEbmFFHl21.BGwiZW', N'0901000006', N'67 Nguyễn Văn Cừ, Ninh Kiều, Cần Thơ'),
    (N'anhkhoa', N'Bùi Anh Khoa', N'anh.khoa@cinebook.local', N'$2a$10$9IqXSwX.sWmcdDgNK1nbZOeqWl7nsj9evEcePEbmFFHl21.BGwiZW', N'0901000007', N'30 Trần Hưng Đạo, Hoàn Kiếm, Hà Nội'),
    (N'baotran', N'Hoàng Bảo Trân', N'bao.tran@cinebook.local', N'$2a$10$9IqXSwX.sWmcdDgNK1nbZOeqWl7nsj9evEcePEbmFFHl21.BGwiZW', N'0901000008', N'19 Pasteur, Quận 1, TP. Hồ Chí Minh')
) v(Username, FullName, Email, PasswordHash, Phone, Address)
WHERE NOT EXISTS (SELECT 1 FROM Users u WHERE u.Email = v.Email);

DECLARE @DemoFilmCity INT = (SELECT TOP 1 Id FROM Films WHERE Title = N'Đêm Thành Phố');
DECLARE @DemoFilmSummer INT = (SELECT TOP 1 Id FROM Films WHERE Title = N'Ga Cuối Mùa Hè');
DECLARE @DemoFilmUniverse INT = (SELECT TOP 1 Id FROM Films WHERE Title = N'Vũ Trụ Nhỏ');
DECLARE @DemoUserLinh INT = (SELECT TOP 1 Id FROM Users WHERE Email = N'khanh.linh@cinebook.local');
DECLARE @DemoUserMinhAnh INT = (SELECT TOP 1 Id FROM Users WHERE Email = N'minh.anh@cinebook.local');
DECLARE @DemoUserPhuc INT = (SELECT TOP 1 Id FROM Users WHERE Email = N'hoang.phuc@cinebook.local');
DECLARE @DemoUserDuong INT = (SELECT TOP 1 Id FROM Users WHERE Email = N'thuy.duong@cinebook.local');
DECLARE @DemoUserHuy INT = (SELECT TOP 1 Id FROM Users WHERE Email = N'quoc.huy@cinebook.local');
DECLARE @DemoUserMai INT = (SELECT TOP 1 Id FROM Users WHERE Email = N'ngoc.mai@cinebook.local');

INSERT INTO Comments (UserId, FilmId, Rate, Content, Report)
SELECT v.UserId, v.FilmId, v.Rate, v.Content, v.Report
FROM (VALUES
    (@DemoUserLinh, @DemoFilmCity, 5, N'Phim cuốn từ đầu đến cuối, phần âm thanh trong rạp rất đã.', CAST(0 AS BIT)),
    (@DemoUserMinhAnh, @DemoFilmCity, 4, N'Cảnh đêm thành phố lên hình đẹp, nhịp phim nhanh và dễ theo dõi.', CAST(0 AS BIT)),
    (@DemoUserPhuc, @DemoFilmSummer, 5, N'Câu chuyện nhẹ nhàng, hợp đi xem cùng bạn bè cuối tuần.', CAST(0 AS BIT)),
    (@DemoUserDuong, @DemoFilmSummer, 4, N'Nhạc phim hay, đoạn cuối để lại cảm xúc rất Việt Nam.', CAST(0 AS BIT)),
    (@DemoUserHuy, @DemoFilmUniverse, 5, N'Hoạt hình dễ thương, trẻ em và người lớn đều xem ổn.', CAST(0 AS BIT)),
    (@DemoUserMai, @DemoFilmUniverse, 3, N'Nội dung an toàn cho gia đình, màu sắc tươi và vui.', CAST(1 AS BIT))
) v(UserId, FilmId, Rate, Content, Report)
WHERE v.UserId IS NOT NULL
  AND v.FilmId IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM Comments c
      WHERE c.UserId = v.UserId
        AND c.FilmId = v.FilmId
        AND c.Content = v.Content
  );

DECLARE @DemoShowtimeId INT = (SELECT TOP 1 Id FROM Showtimes ORDER BY StartTime);
DECLARE @DemoBasePrice DECIMAL(10,2) = (SELECT TOP 1 BasePrice FROM Showtimes WHERE Id = @DemoShowtimeId);
DECLARE @DemoComboPopcorn INT = (SELECT TOP 1 Id FROM ComboFoods WHERE Name = N'Combo Bắp Nước');
DECLARE @DemoComboCouple INT = (SELECT TOP 1 Id FROM ComboFoods WHERE Name = N'Combo Couple');
DECLARE @DemoPromoId INT = (SELECT TOP 1 Id FROM Promotions WHERE Code = N'CINE10');

IF @DemoShowtimeId IS NOT NULL
   AND @DemoUserLinh IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM Orders WHERE TicketCode = N'CB-DEMO-0001')
   AND 2 = (
       SELECT COUNT(*)
       FROM ShowtimeSeats ss
       JOIN Seats s ON s.Id = ss.SeatId
       WHERE ss.ShowtimeId = @DemoShowtimeId
         AND s.SeatKey IN (N'A1', N'A2')
         AND ss.Status = N'available'
   )
BEGIN
    DECLARE @DemoSeatSubtotal1 DECIMAL(10,2) = (
        SELECT SUM(@DemoBasePrice + ss.ExtraFee)
        FROM ShowtimeSeats ss
        JOIN Seats s ON s.Id = ss.SeatId
        WHERE ss.ShowtimeId = @DemoShowtimeId
          AND s.SeatKey IN (N'A1', N'A2')
    );
    DECLARE @DemoComboSubtotal1 DECIMAL(10,2) = ISNULL((SELECT Price FROM ComboFoods WHERE Id = @DemoComboPopcorn), 0);
    DECLARE @DemoOrderId1 INT;

    INSERT INTO Orders (
        UserId, ShowtimeId, PromotionId, SeatSubtotal, ComboSubtotal, DiscountAmount, TotalAmount,
        TicketCode, TicketQrUrl, PaymentMethod, PaymentStatus, TransactionId, PayRedirectUrl, OrderStatus
    )
    VALUES (
        @DemoUserLinh, @DemoShowtimeId, NULL, @DemoSeatSubtotal1, @DemoComboSubtotal1, 0, @DemoSeatSubtotal1 + @DemoComboSubtotal1,
        N'CB-DEMO-0001', N'/tickets/qr/CB-DEMO-0001', N'card', N'paid', N'DEMO-TXN-0001', NULL, N'confirmed'
    );

    SET @DemoOrderId1 = SCOPE_IDENTITY();

    INSERT INTO OrderSeats (OrderId, ShowtimeSeatId, SeatKey, SeatType, UnitPrice)
    SELECT @DemoOrderId1, ss.Id, s.SeatKey, s.SeatType, @DemoBasePrice + ss.ExtraFee
    FROM ShowtimeSeats ss
    JOIN Seats s ON s.Id = ss.SeatId
    WHERE ss.ShowtimeId = @DemoShowtimeId
      AND s.SeatKey IN (N'A1', N'A2');

    IF @DemoComboPopcorn IS NOT NULL
    BEGIN
        INSERT INTO OrderComboFoods (OrderId, ComboFoodId, Quantity, UnitPrice)
        SELECT @DemoOrderId1, Id, 1, Price
        FROM ComboFoods
        WHERE Id = @DemoComboPopcorn;
    END

    UPDATE ss
    SET Status = N'booked',
        HeldByUserId = NULL,
        HeldAt = NULL,
        HeldUntil = NULL
    FROM ShowtimeSeats ss
    JOIN Seats s ON s.Id = ss.SeatId
    WHERE ss.ShowtimeId = @DemoShowtimeId
      AND s.SeatKey IN (N'A1', N'A2');
END

IF @DemoShowtimeId IS NOT NULL
   AND @DemoUserMinhAnh IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM Orders WHERE TicketCode = N'CB-DEMO-0002')
   AND 2 = (
       SELECT COUNT(*)
       FROM ShowtimeSeats ss
       JOIN Seats s ON s.Id = ss.SeatId
       WHERE ss.ShowtimeId = @DemoShowtimeId
         AND s.SeatKey IN (N'D1', N'D2')
         AND ss.Status = N'available'
   )
BEGIN
    DECLARE @DemoSeatSubtotal2 DECIMAL(10,2) = (
        SELECT SUM(@DemoBasePrice + ss.ExtraFee)
        FROM ShowtimeSeats ss
        JOIN Seats s ON s.Id = ss.SeatId
        WHERE ss.ShowtimeId = @DemoShowtimeId
          AND s.SeatKey IN (N'D1', N'D2')
    );
    DECLARE @DemoComboSubtotal2 DECIMAL(10,2) = ISNULL((SELECT Price FROM ComboFoods WHERE Id = @DemoComboCouple), 0);
    DECLARE @DemoDiscount2 DECIMAL(10,2) = CASE
        WHEN @DemoPromoId IS NULL THEN 0
        WHEN (@DemoSeatSubtotal2 + @DemoComboSubtotal2) * 0.10 > 50000 THEN 50000
        ELSE (@DemoSeatSubtotal2 + @DemoComboSubtotal2) * 0.10
    END;
    DECLARE @DemoOrderId2 INT;

    INSERT INTO Orders (
        UserId, ShowtimeId, PromotionId, SeatSubtotal, ComboSubtotal, DiscountAmount, TotalAmount,
        TicketCode, TicketQrUrl, PaymentMethod, PaymentStatus, TransactionId, PayRedirectUrl, OrderStatus
    )
    VALUES (
        @DemoUserMinhAnh, @DemoShowtimeId, @DemoPromoId, @DemoSeatSubtotal2, @DemoComboSubtotal2, @DemoDiscount2,
        @DemoSeatSubtotal2 + @DemoComboSubtotal2 - @DemoDiscount2,
        N'CB-DEMO-0002', N'/tickets/qr/CB-DEMO-0002', N'card', N'paid', N'DEMO-TXN-0002', NULL, N'confirmed'
    );

    SET @DemoOrderId2 = SCOPE_IDENTITY();

    INSERT INTO OrderSeats (OrderId, ShowtimeSeatId, SeatKey, SeatType, UnitPrice)
    SELECT @DemoOrderId2, ss.Id, s.SeatKey, s.SeatType, @DemoBasePrice + ss.ExtraFee
    FROM ShowtimeSeats ss
    JOIN Seats s ON s.Id = ss.SeatId
    WHERE ss.ShowtimeId = @DemoShowtimeId
      AND s.SeatKey IN (N'D1', N'D2');

    IF @DemoComboCouple IS NOT NULL
    BEGIN
        INSERT INTO OrderComboFoods (OrderId, ComboFoodId, Quantity, UnitPrice)
        SELECT @DemoOrderId2, Id, 1, Price
        FROM ComboFoods
        WHERE Id = @DemoComboCouple;
    END

    UPDATE ss
    SET Status = N'booked',
        HeldByUserId = NULL,
        HeldAt = NULL,
        HeldUntil = NULL
    FROM ShowtimeSeats ss
    JOIN Seats s ON s.Id = ss.SeatId
    WHERE ss.ShowtimeId = @DemoShowtimeId
      AND s.SeatKey IN (N'D1', N'D2');

    IF @DemoPromoId IS NOT NULL
    BEGIN
        UPDATE Promotions
        SET UsedCount = UsedCount + 1,
            UpdatedAt = GETDATE()
        WHERE Id = @DemoPromoId;
    END
END
GO

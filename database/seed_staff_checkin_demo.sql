-- ============================================================================
-- Du lieu demo cho tinh nang quay ve (soat ve + thu tien tai quay).
--
-- CHI PHUC VU KIEM THU. Khong chay tren moi truong that.
-- Co script don kem o cuoi file (phan CLEANUP, dang comment).
--
-- Ly do can file nay: DB hien khong co du lieu nao chay duoc 2 luong tren
--   - 0 don PaymentMethod='counter'      -> khong co gi de thu tien
--   - 0 don paid + confirmed             -> khong co gi de check-in
--   - 0 suat chieu nam trong khung gio   -> khong test duoc luat thoi gian
--
-- Chay:
--   sqlcmd -S localhost -U sa -P <pw> -C -d CineBookDB -f 65001 ^
--          -i database/seed_staff_checkin_demo.sql
--
-- Script idempotent: tu xoa du lieu demo cu truoc khi tao lai.
-- ============================================================================
USE CineBookDB;
GO

SET NOCOUNT ON;
GO

-- ---------------------------------------------------------------------------
-- Don du lieu demo cu (neu co). Thu tu xoa theo rang buoc khoa ngoai.
-- Moc nhan dien: TicketCode bat dau bang 'DEMO-' va Showtimes.Version='STAFF_DEMO'.
-- ---------------------------------------------------------------------------
DELETE FROM OrderComboFoods WHERE OrderId IN (SELECT Id FROM Orders WHERE TicketCode LIKE 'DEMO-%');
DELETE FROM OrderSeats      WHERE OrderId IN (SELECT Id FROM Orders WHERE TicketCode LIKE 'DEMO-%');
DELETE FROM Orders          WHERE TicketCode LIKE 'DEMO-%';
DELETE FROM ShowtimeSeats   WHERE ShowtimeId IN (SELECT Id FROM Showtimes WHERE Version = N'STAFF_DEMO');
DELETE FROM Showtimes       WHERE Version = N'STAFF_DEMO';
GO

-- ---------------------------------------------------------------------------
-- Chon phong chieu con hoat dong va co du ghe, cung mot phim + mot thanh vien.
-- ---------------------------------------------------------------------------
DECLARE @roomId INT, @cinemaId INT, @filmId INT, @userId INT;

SELECT TOP 1 @roomId = r.Id, @cinemaId = r.CinemaId
FROM Rooms r
JOIN Seats s ON s.RoomId = r.Id
WHERE ISNULL(r.Status, 'active') = 'active'
GROUP BY r.Id, r.CinemaId
HAVING COUNT(s.Id) >= 10
ORDER BY r.Id;

SELECT TOP 1 @filmId = Id FROM Films ORDER BY Id;
SELECT TOP 1 @userId = Id FROM Users WHERE Role = 'member' AND Deleted = 0 ORDER BY Id;

IF @roomId IS NULL OR @filmId IS NULL OR @userId IS NULL
BEGIN
    RAISERROR (N'Thieu du lieu nen (phong/phim/thanh vien). Hay chay schema.sql + seed.sql truoc.', 16, 1);
    RETURN;
END

-- ---------------------------------------------------------------------------
-- Suat chieu A: bat dau sau 20 phut -> nam TRONG khung check-in (READY).
-- Suat chieu B: da chieu cach day 3 tieng -> NGOAI khung (TOO_LATE).
-- ---------------------------------------------------------------------------
DECLARE @soonStart DATETIME = DATEADD(MINUTE, 20, GETDATE());
DECLARE @pastStart DATETIME = DATEADD(HOUR, -3, GETDATE());

INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice, Format, Version, Language)
VALUES (@filmId, @cinemaId, @roomId, @soonStart, DATEADD(MINUTE, 110, @soonStart), 90000, '2D', N'STAFF_DEMO', N'Tiếng Việt');
DECLARE @soonShowtimeId INT = SCOPE_IDENTITY();

INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice, Format, Version, Language)
VALUES (@filmId, @cinemaId, @roomId, @pastStart, DATEADD(MINUTE, 110, @pastStart), 90000, '2D', N'STAFF_DEMO', N'Tiếng Việt');
DECLARE @pastShowtimeId INT = SCOPE_IDENTITY();

-- Sinh so do ghe cho ca hai suat tu so do ghe cua phong.
INSERT INTO ShowtimeSeats (ShowtimeId, SeatId, Status, ExtraFee)
SELECT @soonShowtimeId, s.Id, 'available', ISNULL(s.PriceSurcharge, 0)
FROM Seats s WHERE s.RoomId = @roomId AND s.SeatType <> 'maintenance';

INSERT INTO ShowtimeSeats (ShowtimeId, SeatId, Status, ExtraFee)
SELECT @pastShowtimeId, s.Id, 'available', ISNULL(s.PriceSurcharge, 0)
FROM Seats s WHERE s.RoomId = @roomId AND s.SeatType <> 'maintenance';

-- ---------------------------------------------------------------------------
-- Ba don demo. Ma ve dat co dinh de walkthrough go/quet cho de.
-- ---------------------------------------------------------------------------
DECLARE @orderId INT;

-- ===== Don A: da thanh toan, dung gio  -> ky vong verdict READY =====
INSERT INTO Orders (UserId, ShowtimeId, SeatSubtotal, ComboSubtotal, DiscountAmount, TotalAmount,
                    TicketCode, TicketQrUrl, PaymentMethod, PaymentStatus, TransactionId, OrderStatus)
VALUES (@userId, @soonShowtimeId, 180000, 0, 0, 180000,
        'DEMO-READY-001', '/tickets/qr/DEMO-READY-001', 'card', 'paid', 'TX-DEMOREADY001', 'confirmed');
SET @orderId = SCOPE_IDENTITY();

INSERT INTO OrderSeats (OrderId, ShowtimeSeatId, SeatKey, SeatType, UnitPrice)
SELECT TOP 2 @orderId, ss.Id, s.SeatKey, s.SeatType, 90000 + ISNULL(s.PriceSurcharge, 0)
FROM ShowtimeSeats ss JOIN Seats s ON s.Id = ss.SeatId
WHERE ss.ShowtimeId = @soonShowtimeId AND ss.Status = 'available'
ORDER BY s.RowLabel, s.SeatNumber;

UPDATE ss SET ss.Status = 'booked'
FROM ShowtimeSeats ss
WHERE ss.Id IN (SELECT ShowtimeSeatId FROM OrderSeats WHERE OrderId = @orderId);

-- ===== Don B: thanh toan tai quay, CHUA thu tien -> ky vong NEEDS_PAYMENT =====
--
-- CounterExpiresAt BAT BUOC phai co va con han. StaffService.lookupTicket() coi
-- `expiresAt == null` la DA HET HAN (fail-closed), nen ban cu — khong ghi cot nay — luon cho ra
-- COUNTER_EXPIRED chu khong phai NEEDS_PAYMENT nhu chinh comment nay hua. Do la mot fixture
-- khong bao gio dung duoc voi ky vong cua no.
INSERT INTO Orders (UserId, ShowtimeId, SeatSubtotal, ComboSubtotal, DiscountAmount, TotalAmount,
                    TicketCode, TicketQrUrl, PaymentMethod, PaymentStatus, OrderStatus, CounterExpiresAt)
VALUES (@userId, @soonShowtimeId, 180000, 65000, 0, 245000,
        'DEMO-COUNTER-001', '/tickets/qr/DEMO-COUNTER-001', 'counter', 'pending', 'confirmed',
        DATEADD(MINUTE, 30, GETDATE()));
SET @orderId = SCOPE_IDENTITY();

INSERT INTO OrderSeats (OrderId, ShowtimeSeatId, SeatKey, SeatType, UnitPrice)
SELECT TOP 2 @orderId, ss.Id, s.SeatKey, s.SeatType, 90000 + ISNULL(s.PriceSurcharge, 0)
FROM ShowtimeSeats ss JOIN Seats s ON s.Id = ss.SeatId
WHERE ss.ShowtimeId = @soonShowtimeId AND ss.Status = 'available'
ORDER BY s.RowLabel, s.SeatNumber;

UPDATE ss SET ss.Status = 'booked'
FROM ShowtimeSeats ss
WHERE ss.Id IN (SELECT ShowtimeSeatId FROM OrderSeats WHERE OrderId = @orderId);

-- Them mot combo de kiem tra hien thi "Bap nuoc" tren man hinh quay.
INSERT INTO OrderComboFoods (OrderId, ComboFoodId, Quantity, UnitPrice)
SELECT TOP 1 @orderId, cf.Id, 1, cf.Price FROM ComboFoods cf ORDER BY cf.Id;

-- ===== Don C: da thanh toan nhung suat da qua 3 tieng -> ky vong TOO_LATE =====
INSERT INTO Orders (UserId, ShowtimeId, SeatSubtotal, ComboSubtotal, DiscountAmount, TotalAmount,
                    TicketCode, TicketQrUrl, PaymentMethod, PaymentStatus, TransactionId, OrderStatus)
VALUES (@userId, @pastShowtimeId, 90000, 0, 0, 90000,
        'DEMO-LATE-001', '/tickets/qr/DEMO-LATE-001', 'card', 'paid', 'TX-DEMOLATE001', 'confirmed');
SET @orderId = SCOPE_IDENTITY();

INSERT INTO OrderSeats (OrderId, ShowtimeSeatId, SeatKey, SeatType, UnitPrice)
SELECT TOP 1 @orderId, ss.Id, s.SeatKey, s.SeatType, 90000 + ISNULL(s.PriceSurcharge, 0)
FROM ShowtimeSeats ss JOIN Seats s ON s.Id = ss.SeatId
WHERE ss.ShowtimeId = @pastShowtimeId AND ss.Status = 'available'
ORDER BY s.RowLabel, s.SeatNumber;

UPDATE ss SET ss.Status = 'booked'
FROM ShowtimeSeats ss
WHERE ss.Id IN (SELECT ShowtimeSeatId FROM OrderSeats WHERE OrderId = @orderId);

PRINT N'Da tao 3 don demo tren phong ' + CAST(@roomId AS NVARCHAR(10)) + N'.';
GO

-- ---------------------------------------------------------------------------
-- Xac minh
-- ---------------------------------------------------------------------------
SELECT o.TicketCode,
       o.PaymentMethod,
       o.PaymentStatus,
       o.OrderStatus,
       CONVERT(VARCHAR(19), s.StartTime, 120) AS StartTime,
       DATEDIFF(MINUTE, GETDATE(), s.StartTime) AS MinutesFromNow,
       (SELECT COUNT(*) FROM OrderSeats os WHERE os.OrderId = o.Id) AS SeatCount
FROM Orders o
JOIN Showtimes s ON s.Id = o.ShowtimeId
WHERE o.TicketCode LIKE 'DEMO-%'
ORDER BY o.TicketCode;
GO

-- ============================================================================
-- CLEANUP - bo comment va chay khi muon xoa sach du lieu demo:
--
-- DELETE FROM OrderComboFoods WHERE OrderId IN (SELECT Id FROM Orders WHERE TicketCode LIKE 'DEMO-%');
-- DELETE FROM OrderSeats      WHERE OrderId IN (SELECT Id FROM Orders WHERE TicketCode LIKE 'DEMO-%');
-- DELETE FROM Orders          WHERE TicketCode LIKE 'DEMO-%';
-- DELETE FROM ShowtimeSeats   WHERE ShowtimeId IN (SELECT Id FROM Showtimes WHERE Version = N'STAFF_DEMO');
-- DELETE FROM Showtimes       WHERE Version = N'STAFF_DEMO';
-- ============================================================================

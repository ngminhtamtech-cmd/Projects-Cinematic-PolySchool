-- Script: seed_test_fixtures.sql
-- Goal: Seed an ephemeral CineBookIT_* database for integration testing.
-- The runner selects the database with sqlcmd -d; this script never switches databases.

IF DB_NAME() NOT LIKE N'CineBookIT[_]%'
    THROW 51000, 'seed_test_fixtures.sql only accepts an ephemeral CineBookIT_* database.', 1;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

-- Clear existing data in reverse dependency order
-- NotificationRecipients / UserNotifications / RefreshTokens tham chieu Users va Cinemas,
-- nen phai xoa truoc hai bang do (fix19, fix21).
DELETE FROM NotificationRecipients;
DELETE FROM UserNotifications;
DELETE FROM RefreshTokens;
DELETE FROM AdminNotifications;
DELETE FROM RoomRequestSeats;
DELETE FROM RoomRequestDetails;
DELETE FROM FilmRequestCategories;
DELETE FROM FilmRequestDetails;
DELETE FROM ApprovalRequests;
DELETE FROM CinemaContents;
DELETE FROM Invoices;
DELETE FROM RefundTransactions;
DELETE FROM OrderSeats;
DELETE FROM OrderComboFoods;
DELETE FROM ComboFoods WHERE LegacySourceComboId IS NOT NULL;
DELETE FROM ComboFoods;
DELETE FROM PromotionUsage;
DELETE FROM Orders;
DELETE FROM ShowtimeSeats;
DELETE FROM Showtimes;
DELETE FROM Seats;
DELETE FROM Rooms;
DELETE FROM CinemaFilms;
DELETE FROM UserVouchers;
DELETE FROM PointTransactions;
DELETE FROM Promotions;
DELETE FROM PasswordResetTokens;
DELETE FROM UserAppeals;
DELETE FROM CommentReports;
DELETE FROM Comments;
DELETE FROM AuditLogs;
DELETE FROM LoginAttempts;
DELETE FROM FilmCategories;
DELETE FROM SystemSettings;
DELETE FROM Users;
DELETE FROM Cinemas;
DELETE FROM Cities;
DELETE FROM Films;

-- Reset Identity Seeds
DBCC CHECKIDENT ('Users', RESEED, 0);
DBCC CHECKIDENT ('Cinemas', RESEED, 0);
DBCC CHECKIDENT ('Rooms', RESEED, 0);
DBCC CHECKIDENT ('Seats', RESEED, 0);
DBCC CHECKIDENT ('Films', RESEED, 0);
DBCC CHECKIDENT ('Showtimes', RESEED, 0);
DBCC CHECKIDENT ('ShowtimeSeats', RESEED, 0);
-- Orders is intentionally left empty by this fixture. SQL Server uses the
-- reseed value itself (rather than value + increment) for the first insert
-- into a never-populated/empty identity table, so RESEED 0 produced order #0
-- and violated the application's positive-ID contract. Starting at 1 keeps
-- both the first-run and re-run cases safely positive.
DBCC CHECKIDENT ('Orders', RESEED, 1);
DBCC CHECKIDENT ('Promotions', RESEED, 0);

-- Ensure ShowtimeSeats status constraint includes maintenance
DECLARE @chkName NVARCHAR(256);
WHILE EXISTS (SELECT * FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('ShowtimeSeats'))
BEGIN
    SELECT TOP 1 @chkName = name FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('ShowtimeSeats');
    EXEC('ALTER TABLE ShowtimeSeats DROP CONSTRAINT ' + @chkName);
END;
ALTER TABLE ShowtimeSeats ADD CONSTRAINT CK_ShowtimeSeats_Status CHECK (Status IN ('available','held','booked','maintenance'));

-- 1. Cities & Cinemas
SET IDENTITY_INSERT Cities ON;
IF NOT EXISTS (SELECT * FROM Cities WHERE Id = 1)
    INSERT INTO Cities (Id, Name) VALUES (1, N'TP. Hồ Chí Minh');
SET IDENTITY_INSERT Cities OFF;

SET IDENTITY_INSERT Cinemas ON;
INSERT INTO Cinemas (Id, CityId, Name, Address, Phone, Status) VALUES 
(1, 1, N'CineBook Quận 1 Test', N'123 Lê Lợi, Q.1', '0901234567', 'active'),
(2, 1, N'CineBook Quận 7 Test', N'456 Nguyễn Văn Linh, Q.7', '0907654321', 'active');
SET IDENTITY_INSERT Cinemas OFF;

-- 2. Rooms
SET IDENTITY_INSERT Rooms ON;
INSERT INTO Rooms (Id, CinemaId, Name, Status) VALUES
(1, 1, N'Phòng 01 - Standard', 'active'),
(2, 2, N'Phòng 02 - VIP', 'active');
SET IDENTITY_INSERT Rooms OFF;

-- 3. Seats (Room 1: Standard A1-A2, Couple E1-E2, Maintenance M1)
--
-- PHAI CO HAI GHE STANDARD DAT DUOC. Ban cu chi co A1: moi kich ban can hai lan dat
-- lien tiep (nguoi A giu ve roi nguoi B dat tiep — Bug02PayIdempotencyIT) chi xanh nho
-- ghe THUA do test khac de lai tren mot DB song lau (Bug07/ReportReconciliation/
-- TransactionRollback deu INSERT Seats ma khong don). Dung lai DB tu dau la lo ngay.
-- Ghe couple khong thay the duoc: luat ep dat ca cap nen chung bi loai khoi moi
-- kich ban "lay mot ghe bat ky".
SET IDENTITY_INSERT Seats ON;
INSERT INTO Seats (Id, RoomId, RowLabel, SeatNumber, SeatType, SeatKey, PriceSurcharge) VALUES
(1, 1, 'A', 1, 'standard', 'A1', 0.00),
(2, 1, 'E', 1, 'couple', 'E1', 20000.00),
(3, 1, 'E', 2, 'couple', 'E2', 20000.00),
(4, 1, 'M', 1, 'standard', 'M1', 0.00),
(5, 1, 'A', 2, 'standard', 'A2', 0.00);
SET IDENTITY_INSERT Seats OFF;

-- 4. Films
SET IDENTITY_INSERT Films ON;
INSERT INTO Films (Id, Title, Description, DurationMinutes, ReleaseDate, EndDate, Thumbnail, Status) VALUES
(1, N'Phim Test Hành Động', N'Mô tả phim test', 120, GETDATE(), DATEADD(DAY, 60, CAST(GETDATE() AS date)), '/assets/img/default-film.jpg', 'showing');
SET IDENTITY_INSERT Films OFF;

-- Every seeded showtime must belong to the cinema's assigned film catalogue.
INSERT INTO CinemaFilms (CinemaId, FilmId) VALUES (1, 1);

-- 5. Users (5 roles: member BRONZE, member DIAMOND, staff, manager, admin)
-- Password for all test users is: password123 (BCrypt hash)
SET IDENTITY_INSERT Users ON;
INSERT INTO Users (Id, FullName, Email, PasswordHash, Phone, Role, MembershipTier, TotalSpent, CinemaId, IsLocked) VALUES
(1, N'Member Bronze Test', 'member_bronze@test.com', '$2a$12$Cja7B.jV5kPjNnjZfPWAR.5lZPcgJ9Z/mTRrIUTaSBxpO6iTyfoBm', '0911111111', 'member', 'BRONZE', 0.00, NULL, 0),
(2, N'Member Diamond Test', 'member_diamond@test.com', '$2a$12$Cja7B.jV5kPjNnjZfPWAR.5lZPcgJ9Z/mTRrIUTaSBxpO6iTyfoBm', '0922222222', 'member', 'DIAMOND', 2000000.00, NULL, 0),
(3, N'Staff Cinema 1 Test', 'staff@test.com', '$2a$12$Cja7B.jV5kPjNnjZfPWAR.5lZPcgJ9Z/mTRrIUTaSBxpO6iTyfoBm', '0933333333', 'staff', 'BRONZE', 0.00, 1, 0),
(4, N'Manager Cinema 1 Test', 'manager@test.com', '$2a$12$Cja7B.jV5kPjNnjZfPWAR.5lZPcgJ9Z/mTRrIUTaSBxpO6iTyfoBm', '0944444444', 'manager', 'BRONZE', 0.00, 1, 0),
(5, N'Admin System Test', 'admin@test.com', '$2a$12$Cja7B.jV5kPjNnjZfPWAR.5lZPcgJ9Z/mTRrIUTaSBxpO6iTyfoBm', '0955555555', 'admin', 'BRONZE', 0.00, NULL, 0);
SET IDENTITY_INSERT Users OFF;

-- 6. Showtimes (3 suất: past, present, future)
SET IDENTITY_INSERT Showtimes ON;
INSERT INTO Showtimes (Id, FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice) VALUES
(1, 1, 1, 1, DATEADD(DAY, -1, GETDATE()), DATEADD(MINUTE, 120, DATEADD(DAY, -1, GETDATE())), 90000.00),
(2, 1, 1, 1, DATEADD(MINUTE, -30, GETDATE()), DATEADD(MINUTE, 90, GETDATE()), 90000.00),
(3, 1, 1, 1, DATEADD(DAY, 30, GETDATE()), DATEADD(MINUTE, 120, DATEADD(DAY, 30, GETDATE())), 100000.00);
SET IDENTITY_INSERT Showtimes OFF;

-- 7. ShowtimeSeats for Showtime 3 (Future)
INSERT INTO ShowtimeSeats (ShowtimeId, SeatId, Status, ExtraFee, HeldByUserId, HeldUntil) VALUES
(3, 1, 'available', 0.00, NULL, NULL),
(3, 2, 'available', 20000.00, NULL, NULL),
(3, 3, 'available', 20000.00, NULL, NULL),
(3, 4, 'maintenance', 0.00, NULL, NULL),
(3, 5, 'available', 0.00, NULL, NULL);

-- 8. Promotions (3 loại: public, exhausted, TIER_RESTRICTED)
SET IDENTITY_INSERT Promotions ON;
INSERT INTO Promotions (Id, Code, Description, DiscountPercent, MaxDiscount, StartDate, EndDate, UsageLimit, UsedCount, Status, VoucherType, TargetTier, CreatedByUserId) VALUES
(1, 'PUBLIC10', N'Giảm 10% công khai', 10, 50000.00, DATEADD(DAY, -30, CAST(GETDATE() AS DATE)), DATEADD(DAY, 365, CAST(GETDATE() AS DATE)), 100, 0, 'active', 'PUBLIC', NULL, 5),
(2, 'EXHAUSTED', N'Khuyến mãi hết lượt', 20, 50000.00, DATEADD(DAY, -30, CAST(GETDATE() AS DATE)), DATEADD(DAY, 365, CAST(GETDATE() AS DATE)), 5, 5, 'active', 'PUBLIC', NULL, 5),
(3, 'DIAMOND50', N'Giảm 50% cho DIAMOND', 50, 200000.00, DATEADD(DAY, -30, CAST(GETDATE() AS DATE)), DATEADD(DAY, 365, CAST(GETDATE() AS DATE)), 100, 0, 'active', 'TIER_RESTRICTED', 'DIAMOND', 5);
SET IDENTITY_INSERT Promotions OFF;

-- 9. SystemSettings
--
-- Khoi DELETE o dau file xoa sach SystemSettings, ke ca cac dong do chuoi migration
-- seed vao (fix08, fix27). Phan lon cau hinh co mac dinh trong ma nen bo trong van
-- chay duoc, nhung booking.maxOpenDraftsPerShowtime thi khac: C.4 dua no len man hinh
-- /system/config chinh vi "chay bang mac dinh ma khong ai nhin thay" la lop loi INV-6.
-- Neu DB test khong co dong nay thi khong con gi kiem chung duoc dieu do.
--
-- Gia tri phai bang BookingService.DEFAULT_MAX_OPEN_DRAFTS.
INSERT INTO SystemSettings (SettingKey, SettingValue, UpdatedAt) VALUES
(N'booking.maxOpenDraftsPerShowtime', N'3', GETDATE()),
(N'booking.stateContractVersion', N'1', GETDATE());

INSERT INTO CinemaContents (CinemaId, ContentKey, ContentJson, UpdatedByUserId)
SELECT c.Id, content.ContentKey, N'[]', 5
FROM Cinemas c
CROSS JOIN (VALUES
    (N'cinetags_data'),
    (N'corner_items_data'),
    (N'events_data'),
    (N'special_cinemas_data')
) content(ContentKey)
WHERE c.Status=N'active';

COMMIT TRANSACTION;
PRINT 'Seeded test fixtures for the ephemeral integration database successfully.';
GO

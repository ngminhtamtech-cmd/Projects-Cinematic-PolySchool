IF DB_ID(N'CineBookDB') IS NULL
BEGIN
    CREATE DATABASE CineBookDB;
END
GO

USE CineBookDB;
GO

-- Bang phu thuoc Users/Cinemas phai xoa truoc hai bang do.
IF OBJECT_ID('NotificationRecipients', 'U') IS NOT NULL DROP TABLE NotificationRecipients;
IF OBJECT_ID('UserNotifications', 'U') IS NOT NULL DROP TABLE UserNotifications;
IF OBJECT_ID('RefreshTokens', 'U') IS NOT NULL DROP TABLE RefreshTokens;
IF OBJECT_ID('OrderComboFoods', 'U') IS NOT NULL DROP TABLE OrderComboFoods;
IF OBJECT_ID('ComboFoods', 'U') IS NOT NULL DROP TABLE ComboFoods;
IF OBJECT_ID('OrderSeats', 'U') IS NOT NULL DROP TABLE OrderSeats;
IF OBJECT_ID('Orders', 'U') IS NOT NULL DROP TABLE Orders;
IF OBJECT_ID('Promotions', 'U') IS NOT NULL DROP TABLE Promotions;
IF OBJECT_ID('Comments', 'U') IS NOT NULL DROP TABLE Comments;
IF OBJECT_ID('ShowtimeSeats', 'U') IS NOT NULL DROP TABLE ShowtimeSeats;
IF OBJECT_ID('Showtimes', 'U') IS NOT NULL DROP TABLE Showtimes;
IF OBJECT_ID('Seats', 'U') IS NOT NULL DROP TABLE Seats;
IF OBJECT_ID('Rooms', 'U') IS NOT NULL DROP TABLE Rooms;
IF OBJECT_ID('Cinemas', 'U') IS NOT NULL DROP TABLE Cinemas;
IF OBJECT_ID('Cities', 'U') IS NOT NULL DROP TABLE Cities;
IF OBJECT_ID('FilmCategories', 'U') IS NOT NULL DROP TABLE FilmCategories;
IF OBJECT_ID('Categories', 'U') IS NOT NULL DROP TABLE Categories;
IF OBJECT_ID('Films', 'U') IS NOT NULL DROP TABLE Films;
IF OBJECT_ID('AuditLogs', 'U') IS NOT NULL DROP TABLE AuditLogs;
IF OBJECT_ID('SystemSettings', 'U') IS NOT NULL DROP TABLE SystemSettings;
IF OBJECT_ID('Users', 'U') IS NOT NULL DROP TABLE Users;
GO

CREATE TABLE Users (
    Id INT IDENTITY PRIMARY KEY,
    Username NVARCHAR(50) NULL,
    FullName NVARCHAR(100) NULL,
    Email NVARCHAR(100) NOT NULL UNIQUE,
    PasswordHash NVARCHAR(255) NOT NULL,
    Phone NVARCHAR(20) NULL,
    Address NVARCHAR(255) NULL,
    Avatar NVARCHAR(255) NULL,
    Role NVARCHAR(20) NOT NULL DEFAULT 'member'
        CONSTRAINT CK_Users_Role CHECK (Role IN ('member','staff','manager','admin')),
    Deleted BIT NOT NULL DEFAULT 0,
    CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
    UpdatedAt DATETIME NOT NULL DEFAULT GETDATE()
);

CREATE TABLE Categories (
    Id INT IDENTITY PRIMARY KEY,
    Title NVARCHAR(100) NOT NULL
);

CREATE TABLE Films (
    Id INT IDENTITY PRIMARY KEY,
    Title NVARCHAR(255) NOT NULL,
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
    Language NVARCHAR(50) NULL,
    Subtitles NVARCHAR(50) NULL,
    Description NVARCHAR(MAX) NULL,
    Country NVARCHAR(100) NULL,
    Format NVARCHAR(50) NULL,
    Status NVARCHAR(20) NULL DEFAULT 'showing',
    Banner NVARCHAR(255) NULL,
    DeletedAt DATETIME2(7) NULL,
    DeletedByUserId INT NULL FOREIGN KEY REFERENCES Users(Id),
    DeletionMode NVARCHAR(30) NULL,
    CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
    UpdatedAt DATETIME NOT NULL DEFAULT GETDATE()
);

CREATE TABLE FilmCategories (
    FilmId INT NOT NULL FOREIGN KEY REFERENCES Films(Id),
    CategoryId INT NOT NULL FOREIGN KEY REFERENCES Categories(Id),
    PRIMARY KEY (FilmId, CategoryId)
);

CREATE TABLE Cities (
    Id INT IDENTITY PRIMARY KEY,
    Name NVARCHAR(100) NOT NULL
);

CREATE TABLE Cinemas (
    Id INT IDENTITY PRIMARY KEY,
    CityId INT NOT NULL FOREIGN KEY REFERENCES Cities(Id),
    Name NVARCHAR(150) NOT NULL,
    Address NVARCHAR(255) NOT NULL,
    Avatar NVARCHAR(255) NULL,
    Description NVARCHAR(MAX) NULL,
    Phone NVARCHAR(30) NULL,
    Status NVARCHAR(20) NULL DEFAULT 'active',
    CinemaType NVARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
    UpdatedAt DATETIME NOT NULL DEFAULT GETDATE()
);

CREATE TABLE Rooms (
    Id INT IDENTITY PRIMARY KEY,
    CinemaId INT NOT NULL FOREIGN KEY REFERENCES Cinemas(Id),
    Name NVARCHAR(50) NOT NULL,
    Status NVARCHAR(20) NOT NULL DEFAULT 'active',
    RoomType NVARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
    UpdatedAt DATETIME NOT NULL DEFAULT GETDATE()
);

CREATE TABLE Seats (
    Id INT IDENTITY PRIMARY KEY,
    RoomId INT NOT NULL FOREIGN KEY REFERENCES Rooms(Id),
    RowLabel NVARCHAR(5) NOT NULL,
    SeatNumber INT NOT NULL,
    SeatType NVARCHAR(20) NOT NULL DEFAULT 'standard'
        CHECK (SeatType IN ('standard','vip','couple')),
    SeatKey NVARCHAR(20) NOT NULL,
    CONSTRAINT UQ_Seats_Room_SeatKey UNIQUE (RoomId, SeatKey)
);

CREATE TABLE Showtimes (
    Id INT IDENTITY PRIMARY KEY,
    FilmId INT NOT NULL FOREIGN KEY REFERENCES Films(Id),
    CinemaId INT NOT NULL FOREIGN KEY REFERENCES Cinemas(Id),
    RoomId INT NOT NULL FOREIGN KEY REFERENCES Rooms(Id),
    StartTime DATETIME NOT NULL,
    EndTime DATETIME NOT NULL,
    BasePrice DECIMAL(10,2) NOT NULL,
    SaleStatus NVARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
    DeleteRequestedAt DATETIME2(7) NULL,
    DeleteNotBefore DATETIME2(7) NULL,
    DeleteRequestedByUserId INT NULL FOREIGN KEY REFERENCES Users(Id),
    CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
    UpdatedAt DATETIME NOT NULL DEFAULT GETDATE()
);

CREATE TABLE ShowtimeSeats (
    Id INT IDENTITY PRIMARY KEY,
    ShowtimeId INT NOT NULL FOREIGN KEY REFERENCES Showtimes(Id),
    SeatId INT NOT NULL FOREIGN KEY REFERENCES Seats(Id),
    Status NVARCHAR(20) NOT NULL DEFAULT 'available'
        CHECK (Status IN ('available','held','booked')),
    ExtraFee DECIMAL(10,2) NOT NULL DEFAULT 0,
    HeldByUserId INT NULL FOREIGN KEY REFERENCES Users(Id),
    HeldAt DATETIME NULL,
    HeldUntil DATETIME NULL,
    RowVersion ROWVERSION,
    CONSTRAINT UQ_ShowtimeSeats_Showtime_Seat UNIQUE (ShowtimeId, SeatId)
);

CREATE TABLE Comments (
    Id INT IDENTITY PRIMARY KEY,
    UserId INT NOT NULL FOREIGN KEY REFERENCES Users(Id),
    FilmId INT NOT NULL FOREIGN KEY REFERENCES Films(Id),
    Rate INT NOT NULL CHECK (Rate BETWEEN 1 AND 5),
    Content NVARCHAR(MAX) NULL,
    Report BIT NOT NULL DEFAULT 0,
    CreatedAt DATETIME NOT NULL DEFAULT GETDATE()
);

CREATE TABLE Promotions (
    Id INT IDENTITY PRIMARY KEY,
    Code NVARCHAR(30) NOT NULL UNIQUE,
    Description NVARCHAR(255) NULL,
    DiscountPercent FLOAT NULL,
    MaxDiscount DECIMAL(10,2) NULL,
    StartDate DATE NOT NULL,
    EndDate DATE NOT NULL,
    ConditionsJson NVARCHAR(MAX) NULL,
    UsageLimit INT NULL,
    UsedCount INT NOT NULL DEFAULT 0,
    Status NVARCHAR(20) NOT NULL DEFAULT 'active',
    CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
    UpdatedAt DATETIME NOT NULL DEFAULT GETDATE()
);

CREATE TABLE Orders (
    Id INT IDENTITY PRIMARY KEY,
    UserId INT NOT NULL FOREIGN KEY REFERENCES Users(Id),
    ShowtimeId INT NOT NULL FOREIGN KEY REFERENCES Showtimes(Id),
    PromotionId INT NULL FOREIGN KEY REFERENCES Promotions(Id),
    SeatSubtotal DECIMAL(10,2) NOT NULL,
    ComboSubtotal DECIMAL(10,2) NOT NULL DEFAULT 0,
    DiscountAmount DECIMAL(10,2) NOT NULL DEFAULT 0,
    TotalAmount DECIMAL(10,2) NOT NULL,
    TicketCode NVARCHAR(30) NOT NULL UNIQUE,
    TicketQrUrl NVARCHAR(255) NULL,
    PaymentMethod NVARCHAR(30) NOT NULL,
    PaymentStatus NVARCHAR(20) NOT NULL DEFAULT 'pending'
        CHECK (PaymentStatus IN ('pending','paid','failed')),
    TransactionId NVARCHAR(100) NULL,
    PayRedirectUrl NVARCHAR(255) NULL,
    OrderStatus NVARCHAR(20) NOT NULL DEFAULT 'pending'
        CHECK (OrderStatus IN ('pending','confirmed','cancelled','redeemed')),
    RedeemedAt DATETIME NULL,
    CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
    UpdatedAt DATETIME NOT NULL DEFAULT GETDATE()
);

-- N-05: khoa kep, KHONG co cot Id IDENTITY.
--
-- Ban cu khai `Id INT IDENTITY PRIMARY KEY` o day trong khi
-- fix00_create_missing_tables_cinebookdb.sql — script dung production — khai khoa kep.
-- CineBookDB_Test dung tu file nay nen CO cot Id, production thi KHONG, va bo test vi
-- vay do mot schema khac schema that: do la goc cua RP-01 (COUNT(os.Id) vo tren
-- production trong khi test bao xanh).
--
-- Chon khoa kep vi ba ly do:
--   1. khong dong code nao dung OrderSeats.Id / OrderComboFoods.Id — moi truy van di qua
--      OrderId + ShowtimeSeatId / ComboFoodId, va JdbcOrderDAO INSERT khong dung
--      RETURN_GENERATED_KEYS;
--   2. khoa kep dung nghiep vu hon — no chan mot ghe vao cung mot don hai lan, dieu ma
--      cot Id IDENTITY khong chan duoc;
--   3. huong nguoc lai phai ALTER TABLE tren bang da co du lieu ban ve va rebuild
--      clustered index, doi lai khong duoc gi.
--
-- Kieu tien va do dai Name lay theo hinh dang production sau fix01 (DECIMAL(19,2),
-- NVARCHAR(150)) de hai script tao ra dung mot bang.
CREATE TABLE OrderSeats (
    OrderId INT NOT NULL FOREIGN KEY REFERENCES Orders(Id),
    ShowtimeSeatId INT NOT NULL FOREIGN KEY REFERENCES ShowtimeSeats(Id),
    SeatKey NVARCHAR(20) NOT NULL,
    SeatType NVARCHAR(20) NOT NULL,
    UnitPrice DECIMAL(19,2) NOT NULL,
    CONSTRAINT PK_OrderSeats PRIMARY KEY (OrderId, ShowtimeSeatId)
);

CREATE TABLE ComboFoods (
    Id INT IDENTITY PRIMARY KEY,
    Name NVARCHAR(150) NOT NULL,
    Image NVARCHAR(255) NULL,
    Price DECIMAL(19,2) NOT NULL,
    Description NVARCHAR(255) NULL,
    Status NVARCHAR(20) NOT NULL DEFAULT 'active'
);

CREATE TABLE OrderComboFoods (
    OrderId INT NOT NULL FOREIGN KEY REFERENCES Orders(Id),
    ComboFoodId INT NOT NULL FOREIGN KEY REFERENCES ComboFoods(Id),
    Quantity INT NOT NULL DEFAULT 1,
    UnitPrice DECIMAL(19,2) NOT NULL,
    CONSTRAINT PK_OrderComboFoods PRIMARY KEY (OrderId, ComboFoodId)
);

CREATE TABLE AuditLogs (
    Id INT IDENTITY PRIMARY KEY,
    ActorUserId INT NULL FOREIGN KEY REFERENCES Users(Id),
    Action NVARCHAR(100) NOT NULL,
    TargetType NVARCHAR(100) NULL,
    TargetId NVARCHAR(100) NULL,
    DetailJson NVARCHAR(MAX) NULL,
    CreatedAt DATETIME NOT NULL DEFAULT GETDATE()
);

CREATE TABLE SystemSettings (
    SettingKey NVARCHAR(100) NOT NULL PRIMARY KEY,
    SettingValue NVARCHAR(MAX) NULL,
    UpdatedAt DATETIME NOT NULL DEFAULT GETDATE()
);
GO

CREATE INDEX IX_ShowtimeSeats_ShowtimeId ON ShowtimeSeats(ShowtimeId);
CREATE INDEX IX_ShowtimeSeats_HeldUntil ON ShowtimeSeats(HeldUntil);
CREATE INDEX IX_Showtimes_FilmId_StartTime ON Showtimes(FilmId, StartTime);
CREATE INDEX IX_Orders_UserId ON Orders(UserId);
CREATE INDEX IX_AuditLogs_CreatedAt ON AuditLogs(CreatedAt);
GO

-- ============================================================================
-- Xoay vong refresh token va thong bao nguoi dung.
--
-- Ba bang duoi day chi phu thuoc Users/Cinemas nen dat duoc ngay trong schema goc.
-- Dinh nghia day du, ly do thiet ke va duong rollback nam o hai migration tuong ung:
--   database/fix19_refresh_tokens.sql
--   database/fix21_user_notifications.sql
-- Cac thay doi cua loyalty (Users.LifetimeEarnedPoints, PointTransactions.OrderId/
-- VoucherId/IdempotencyKey, CHECK LoyaltyPoints >= 0) nam o fix20_loyalty_ledger.sql:
-- chung sua nhung bang do chinh migration truoc tao ra, khong co trong schema goc.
--
-- Thu tu chay: schema.sql -> alter_*/migration_* -> fix00..fix21 (theo so thu tu).
-- ============================================================================

CREATE TABLE RefreshTokens (
    Id INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    UserId INT NOT NULL FOREIGN KEY REFERENCES Users(Id),
    FamilyId UNIQUEIDENTIFIER NOT NULL,
    TokenHash CHAR(64) NOT NULL,
    IssuedAt DATETIME2(3) NOT NULL DEFAULT SYSDATETIME(),
    ExpiresAt DATETIME2(3) NOT NULL,
    ConsumedAt DATETIME2(3) NULL,
    RevokedAt DATETIME2(3) NULL,
    ReplacedById INT NULL,
    RevocationReason NVARCHAR(100) NULL,
    CreatedByIp NVARCHAR(64) NULL
);

CREATE TABLE UserNotifications (
    Id INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    Title NVARCHAR(200) NOT NULL,
    Message NVARCHAR(MAX) NOT NULL,
    Severity NVARCHAR(20) NOT NULL DEFAULT 'info',
    TargetType NVARCHAR(30) NOT NULL DEFAULT 'ALL',
    TargetId NVARCHAR(50) NULL,
    CinemaId INT NULL FOREIGN KEY REFERENCES Cinemas(Id),
    ActionUrl NVARCHAR(255) NULL,
    VisibleFrom DATETIME2(3) NOT NULL DEFAULT SYSDATETIME(),
    VisibleUntil DATETIME2(3) NULL,
    Status NVARCHAR(20) NOT NULL DEFAULT 'active',
    CreatedByUserId INT NOT NULL FOREIGN KEY REFERENCES Users(Id),
    CreatedAt DATETIME2(3) NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE NotificationRecipients (
    Id INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    SourceType NVARCHAR(20) NOT NULL DEFAULT 'user'
        CONSTRAINT CK_NotificationRecipients_Source CHECK (SourceType IN ('user','admin')),
    NotificationId INT NOT NULL,
    UserId INT NOT NULL FOREIGN KEY REFERENCES Users(Id),
    DeliveredAt DATETIME2(3) NOT NULL DEFAULT SYSDATETIME(),
    ReadAt DATETIME2(3) NULL
);
GO

CREATE UNIQUE INDEX UX_RefreshTokens_TokenHash ON RefreshTokens(TokenHash);
CREATE INDEX IX_RefreshTokens_FamilyId ON RefreshTokens(FamilyId);
CREATE INDEX IX_RefreshTokens_UserId_ExpiresAt ON RefreshTokens(UserId, ExpiresAt);
CREATE UNIQUE INDEX UX_NotificationRecipients_Receipt
    ON NotificationRecipients(SourceType, NotificationId, UserId);
CREATE INDEX IX_NotificationRecipients_Inbox
    ON NotificationRecipients(UserId, ReadAt) INCLUDE (SourceType, NotificationId, DeliveredAt);
CREATE INDEX IX_UserNotifications_Visibility
    ON UserNotifications(Status, VisibleFrom, VisibleUntil);
GO

USE CineBookDB;
GO

SET NOCOUNT ON;

-- 1. Categories
IF OBJECT_ID('Categories', 'U') IS NULL
BEGIN
    CREATE TABLE Categories (
        Id INT IDENTITY PRIMARY KEY,
        Title NVARCHAR(100) NOT NULL
    );
END

-- 2. FilmCategories
IF OBJECT_ID('FilmCategories', 'U') IS NULL
BEGIN
    CREATE TABLE FilmCategories (
        FilmId INT NOT NULL FOREIGN KEY REFERENCES Films(Id),
        CategoryId INT NOT NULL FOREIGN KEY REFERENCES Categories(Id),
        PRIMARY KEY (FilmId, CategoryId)
    );
END

-- 3. Rooms
IF OBJECT_ID('Rooms', 'U') IS NULL
BEGIN
    CREATE TABLE Rooms (
        Id INT IDENTITY PRIMARY KEY,
        CinemaId INT NOT NULL FOREIGN KEY REFERENCES Cinemas(Id),
        Name NVARCHAR(100) NOT NULL,
        Status NVARCHAR(20) NULL DEFAULT 'active',
        CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
        UpdatedAt DATETIME NOT NULL DEFAULT GETDATE()
    );
END

-- 4. Seats
IF OBJECT_ID('Seats', 'U') IS NULL
BEGIN
    CREATE TABLE Seats (
        Id INT IDENTITY PRIMARY KEY,
        RoomId INT NOT NULL FOREIGN KEY REFERENCES Rooms(Id),
        RowLabel NVARCHAR(10) NOT NULL,
        SeatNumber INT NOT NULL,
        SeatType NVARCHAR(20) NOT NULL DEFAULT 'standard'
            CONSTRAINT CK_Seats_Type CHECK (SeatType IN ('standard','vip','couple')),
        SeatKey NVARCHAR(20) NOT NULL,
        PriceSurcharge DECIMAL(12,2) NOT NULL DEFAULT 0.00,
        CONSTRAINT UQ_Seats_Room_Key UNIQUE (RoomId, SeatKey)
    );
END

-- 5. Showtimes
IF OBJECT_ID('Showtimes', 'U') IS NULL
BEGIN
    CREATE TABLE Showtimes (
        Id INT IDENTITY PRIMARY KEY,
        FilmId INT NOT NULL FOREIGN KEY REFERENCES Films(Id),
        CinemaId INT NOT NULL FOREIGN KEY REFERENCES Cinemas(Id),
        RoomId INT NOT NULL FOREIGN KEY REFERENCES Rooms(Id),
        StartTime DATETIME NOT NULL,
        EndTime DATETIME NOT NULL,
        BasePrice DECIMAL(12,2) NOT NULL,
        CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
        UpdatedAt DATETIME NOT NULL DEFAULT GETDATE()
    );
END

-- 6. ShowtimeSeats
IF OBJECT_ID('ShowtimeSeats', 'U') IS NULL
BEGIN
    CREATE TABLE ShowtimeSeats (
        Id INT IDENTITY PRIMARY KEY,
        ShowtimeId INT NOT NULL FOREIGN KEY REFERENCES Showtimes(Id),
        SeatId INT NOT NULL FOREIGN KEY REFERENCES Seats(Id),
        Status NVARCHAR(20) NOT NULL DEFAULT 'available'
            CONSTRAINT CK_ShowtimeSeats_Status CHECK (Status IN ('available','held','booked','maintenance')),
        ExtraFee DECIMAL(12,2) NOT NULL DEFAULT 0.00,
        HeldByUserId INT NULL FOREIGN KEY REFERENCES Users(Id),
        HeldAt DATETIME NULL,
        HeldUntil DATETIME NULL,
        RowVersion ROWVERSION NOT NULL,
        CONSTRAINT UQ_ShowtimeSeats_Showtime_Seat UNIQUE (ShowtimeId, SeatId)
    );
END

-- 7. Orders
IF OBJECT_ID('Orders', 'U') IS NULL
BEGIN
    CREATE TABLE Orders (
        Id INT IDENTITY PRIMARY KEY,
        OrderCode NVARCHAR(50) NOT NULL UNIQUE,
        UserId INT NOT NULL FOREIGN KEY REFERENCES Users(Id),
        ShowtimeId INT NOT NULL FOREIGN KEY REFERENCES Showtimes(Id),
        PromotionId INT NULL FOREIGN KEY REFERENCES Promotions(Id),
        OriginalPrice DECIMAL(12,2) NOT NULL,
        DiscountAmount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
        FinalPrice DECIMAL(12,2) NOT NULL,
        PaymentMethod NVARCHAR(50) NULL,
        PaymentStatus NVARCHAR(30) NOT NULL DEFAULT 'pending'
            CONSTRAINT CK_Orders_PaymentStatus CHECK (PaymentStatus IN ('pending','paid','failed','refunded')),
        OrderStatus NVARCHAR(30) NOT NULL DEFAULT 'created'
            CONSTRAINT CK_Orders_OrderStatus CHECK (OrderStatus IN ('created','confirmed','cancelled','completed')),
        CheckinStatus NVARCHAR(30) NOT NULL DEFAULT 'not_checked_in'
            CONSTRAINT CK_Orders_CheckinStatus CHECK (CheckinStatus IN ('not_checked_in','checked_in')),
        CheckinTime DATETIME NULL,
        CheckinByStaffId INT NULL FOREIGN KEY REFERENCES Users(Id),
        CancelReason NVARCHAR(255) NULL,
        CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
        UpdatedAt DATETIME NOT NULL DEFAULT GETDATE()
    );
END

-- 8. OrderSeats
--
-- N-05: ba bang duoi day phai tao ra DUNG hinh dang ma database\schema.sql tao ra.
-- Ban cu lech o ba diem, va do la goc cua RP-01 lan DB-01:
--   * cot tien ten `Price` o day nhung `UnitPrice` trong schema.sql — moi truy van cua
--     ma nguon doc UnitPrice, nen DB moi dung tu file nay se hong ngay cau dau tien;
--   * OrderSeats thieu han SeatKey/SeatType ma JdbcOrderDAO co ghi;
--   * ComboFoods thieu Image, lam /admin/combos tra 500 tren production.
-- Ca hai script deu co IF NOT EXISTS nen sua o day chi anh huong DB tao moi.
IF OBJECT_ID('OrderSeats', 'U') IS NULL
BEGIN
    CREATE TABLE OrderSeats (
        OrderId INT NOT NULL FOREIGN KEY REFERENCES Orders(Id),
        ShowtimeSeatId INT NOT NULL FOREIGN KEY REFERENCES ShowtimeSeats(Id),
        SeatKey NVARCHAR(20) NOT NULL,
        SeatType NVARCHAR(20) NOT NULL,
        UnitPrice DECIMAL(19,2) NOT NULL,
        CONSTRAINT PK_OrderSeats PRIMARY KEY (OrderId, ShowtimeSeatId)
    );
END

-- 9. ComboFoods
IF OBJECT_ID('ComboFoods', 'U') IS NULL
BEGIN
    CREATE TABLE ComboFoods (
        Id INT IDENTITY PRIMARY KEY,
        Name NVARCHAR(150) NOT NULL,
        Image NVARCHAR(255) NULL,
        Price DECIMAL(19,2) NOT NULL,
        Description NVARCHAR(255) NULL,
        Status NVARCHAR(20) NOT NULL DEFAULT 'active'
    );
END

-- 10. OrderComboFoods
IF OBJECT_ID('OrderComboFoods', 'U') IS NULL
BEGIN
    CREATE TABLE OrderComboFoods (
        OrderId INT NOT NULL FOREIGN KEY REFERENCES Orders(Id),
        ComboFoodId INT NOT NULL FOREIGN KEY REFERENCES ComboFoods(Id),
        Quantity INT NOT NULL DEFAULT 1,
        UnitPrice DECIMAL(19,2) NOT NULL,
        CONSTRAINT PK_OrderComboFoods PRIMARY KEY (OrderId, ComboFoodId)
    );
END

-- 11. AuditLogs
IF OBJECT_ID('AuditLogs', 'U') IS NULL
BEGIN
    CREATE TABLE AuditLogs (
        Id INT IDENTITY PRIMARY KEY,
        ActorUserId INT NULL FOREIGN KEY REFERENCES Users(Id),
        Action NVARCHAR(100) NOT NULL,
        TargetType NVARCHAR(50) NULL,
        TargetId NVARCHAR(100) NULL,
        Detail NVARCHAR(MAX) NULL,
        CreatedAt DATETIME NOT NULL DEFAULT GETDATE()
    );
END

-- 12. SystemSettings
IF OBJECT_ID('SystemSettings', 'U') IS NULL
BEGIN
    CREATE TABLE SystemSettings (
        SettingKey NVARCHAR(100) PRIMARY KEY,
        SettingValue NVARCHAR(MAX) NULL,
        Description NVARCHAR(255) NULL,
        UpdatedAt DATETIME NOT NULL DEFAULT GETDATE()
    );
END

PRINT 'Missing tables in CineBookDB created successfully.';
GO

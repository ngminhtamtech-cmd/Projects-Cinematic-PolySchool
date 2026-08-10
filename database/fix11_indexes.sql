SET XACT_ABORT ON;

-- Production cũ dùng tên Price và không snapshot nhãn/loại ghế. Chuẩn hóa trước khi tạo
-- index và trước khi DAO phân trang đọc batch.
IF COL_LENGTH('dbo.OrderSeats','SeatKey') IS NULL
    EXEC(N'ALTER TABLE dbo.OrderSeats ADD SeatKey NVARCHAR(20) NULL;');
IF COL_LENGTH('dbo.OrderSeats','SeatType') IS NULL
    EXEC(N'ALTER TABLE dbo.OrderSeats ADD SeatType NVARCHAR(20) NULL;');
IF COL_LENGTH('dbo.OrderSeats','UnitPrice') IS NULL
    EXEC(N'ALTER TABLE dbo.OrderSeats ADD UnitPrice DECIMAL(19,2) NULL;');
IF COL_LENGTH('dbo.OrderSeats','Price') IS NOT NULL
    EXEC(N'
        UPDATE os SET SeatKey=COALESCE(os.SeatKey,se.SeatKey),
          SeatType=COALESCE(os.SeatType,se.SeatType,N''standard''),
          UnitPrice=COALESCE(os.UnitPrice,os.Price,0)
        FROM dbo.OrderSeats os LEFT JOIN dbo.ShowtimeSeats ss ON ss.Id=os.ShowtimeSeatId
        LEFT JOIN dbo.Seats se ON se.Id=ss.SeatId;');
ELSE
    EXEC(N'
        UPDATE os SET SeatKey=COALESCE(os.SeatKey,se.SeatKey),
          SeatType=COALESCE(os.SeatType,se.SeatType,N''standard''),
          UnitPrice=COALESCE(os.UnitPrice,0)
        FROM dbo.OrderSeats os LEFT JOIN dbo.ShowtimeSeats ss ON ss.Id=os.ShowtimeSeatId
        LEFT JOIN dbo.Seats se ON se.Id=ss.SeatId;');
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.OrderSeats')
           AND name='SeatKey' AND is_nullable=1)
    EXEC(N'ALTER TABLE dbo.OrderSeats ALTER COLUMN SeatKey NVARCHAR(20) NOT NULL;');
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.OrderSeats')
           AND name='SeatType' AND is_nullable=1)
    EXEC(N'ALTER TABLE dbo.OrderSeats ALTER COLUMN SeatType NVARCHAR(20) NOT NULL;');
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.OrderSeats')
           AND name='UnitPrice' AND is_nullable=1)
    EXEC(N'ALTER TABLE dbo.OrderSeats ALTER COLUMN UnitPrice DECIMAL(19,2) NOT NULL;');

IF COL_LENGTH('dbo.OrderComboFoods','UnitPrice') IS NULL
    EXEC(N'ALTER TABLE dbo.OrderComboFoods ADD UnitPrice DECIMAL(19,2) NULL;');
IF COL_LENGTH('dbo.OrderComboFoods','Price') IS NOT NULL
    EXEC(N'UPDATE dbo.OrderComboFoods SET UnitPrice=COALESCE(UnitPrice,Price,0);');
ELSE
    EXEC(N'UPDATE dbo.OrderComboFoods SET UnitPrice=COALESCE(UnitPrice,0);');
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.OrderComboFoods')
           AND name='UnitPrice' AND is_nullable=1)
    EXEC(N'ALTER TABLE dbo.OrderComboFoods ALTER COLUMN UnitPrice DECIMAL(19,2) NOT NULL;');

IF COL_LENGTH('dbo.AuditLogs','DetailJson') IS NULL
    EXEC(N'ALTER TABLE dbo.AuditLogs ADD DetailJson NVARCHAR(MAX) NULL;');
IF COL_LENGTH('dbo.AuditLogs','Detail') IS NOT NULL
    EXEC(N'UPDATE dbo.AuditLogs SET DetailJson=COALESCE(DetailJson,Detail);');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_Orders_CreatedAt_Id' AND object_id=OBJECT_ID('dbo.Orders'))
    CREATE INDEX IX_Orders_CreatedAt_Id ON dbo.Orders(CreatedAt DESC, Id DESC)
        INCLUDE (UserId, ShowtimeId, OrderStatus, PaymentStatus, TicketCode, TotalAmount);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_Orders_Statuses' AND object_id=OBJECT_ID('dbo.Orders'))
    CREATE INDEX IX_Orders_Statuses ON dbo.Orders(OrderStatus, PaymentStatus, CreatedAt DESC)
        INCLUDE (ShowtimeId, UserId, TicketCode, TotalAmount);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_OrderSeats_OrderId' AND object_id=OBJECT_ID('dbo.OrderSeats'))
    CREATE INDEX IX_OrderSeats_OrderId ON dbo.OrderSeats(OrderId) INCLUDE (ShowtimeSeatId, SeatKey, SeatType, UnitPrice);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_OrderComboFoods_OrderId' AND object_id=OBJECT_ID('dbo.OrderComboFoods'))
    CREATE INDEX IX_OrderComboFoods_OrderId ON dbo.OrderComboFoods(OrderId) INCLUDE (ComboFoodId, Quantity, UnitPrice);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_ShowtimeSeats_Showtime_Status' AND object_id=OBJECT_ID('dbo.ShowtimeSeats'))
    CREATE INDEX IX_ShowtimeSeats_Showtime_Status ON dbo.ShowtimeSeats(ShowtimeId, Status)
        INCLUDE (HeldByUserId, HeldUntil);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_ShowtimeSeats_HeldUntil' AND object_id=OBJECT_ID('dbo.ShowtimeSeats'))
    CREATE INDEX IX_ShowtimeSeats_HeldUntil ON dbo.ShowtimeSeats(HeldUntil)
        WHERE Status='held' AND HeldUntil IS NOT NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_AuditLogs_CreatedAt_Id' AND object_id=OBJECT_ID('dbo.AuditLogs'))
    CREATE INDEX IX_AuditLogs_CreatedAt_Id ON dbo.AuditLogs(CreatedAt DESC, Id DESC);

IF OBJECT_ID('dbo.PromotionUsage','U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_PromotionUsage_UserId' AND object_id=OBJECT_ID('dbo.PromotionUsage'))
    CREATE INDEX IX_PromotionUsage_UserId ON dbo.PromotionUsage(UserId, PromotionId);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_LoginAttempts_AttemptAt' AND object_id=OBJECT_ID('dbo.LoginAttempts'))
    CREATE INDEX IX_LoginAttempts_AttemptAt ON dbo.LoginAttempts(AttemptAt);

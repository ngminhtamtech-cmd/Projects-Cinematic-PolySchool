-- Migration script fix02_refund.sql (Idempotent)
-- Phase P02: Huỷ đơn, doanh thu, hoàn tiền (A3, A4, A5, A6)

-- 1. Bổ sung các cột vào bảng Orders nếu chưa tồn tại
IF COL_LENGTH('Orders', 'RefundedAt') IS NULL
BEGIN
    ALTER TABLE Orders ADD RefundedAt DATETIME2 NULL;
END
GO

IF COL_LENGTH('Orders', 'RefundAmount') IS NULL
BEGIN
    ALTER TABLE Orders ADD RefundAmount DECIMAL(19,2) NULL;
END
GO

IF COL_LENGTH('Orders', 'RefundReason') IS NULL
BEGIN
    ALTER TABLE Orders ADD RefundReason NVARCHAR(500) NULL;
END
GO

IF COL_LENGTH('Orders', 'RefundedBy') IS NULL
BEGIN
    ALTER TABLE Orders ADD RefundedBy INT NULL;
END
GO

IF COL_LENGTH('Orders', 'CancelledAt') IS NULL
BEGIN
    ALTER TABLE Orders ADD CancelledAt DATETIME2 NULL;
END
GO

IF COL_LENGTH('Orders', 'CancelReason') IS NULL
BEGIN
    ALTER TABLE Orders ADD CancelReason NVARCHAR(500) NULL;
END
GO

-- 2. Cập nhật CHECK constraint của PaymentStatus để hỗ trợ 'refunded' và 'cancelled'
DECLARE @chkName NVARCHAR(256);
WHILE EXISTS (
    SELECT * FROM sys.check_constraints cc
    JOIN sys.columns c ON c.object_id = cc.parent_object_id AND c.column_id = cc.parent_column_id
    WHERE cc.parent_object_id = OBJECT_ID('Orders') AND c.name = 'PaymentStatus'
)
BEGIN
    SELECT TOP 1 @chkName = cc.name
    FROM sys.check_constraints cc
    JOIN sys.columns c ON c.object_id = cc.parent_object_id AND c.column_id = cc.parent_column_id
    WHERE cc.parent_object_id = OBJECT_ID('Orders') AND c.name = 'PaymentStatus';
    EXEC('ALTER TABLE Orders DROP CONSTRAINT [' + @chkName + ']');
END;
IF NOT EXISTS (SELECT * FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('Orders') AND name = 'CK_Orders_PaymentStatus')
BEGIN
    ALTER TABLE Orders ADD CONSTRAINT CK_Orders_PaymentStatus CHECK (PaymentStatus IN ('pending', 'paid', 'failed', 'cancelled', 'refunded'));
END
GO

-- 2. Tạo bảng RefundTransactions lưu lịch sử hoàn tiền
IF OBJECT_ID('RefundTransactions', 'U') IS NULL
BEGIN
    CREATE TABLE RefundTransactions (
        Id INT IDENTITY(1,1) PRIMARY KEY,
        OrderId INT NOT NULL,
        Amount DECIMAL(19,2) NOT NULL,
        Reason NVARCHAR(500) NULL,
        RefundedBy INT NOT NULL,
        RefundedAt DATETIME2 DEFAULT GETDATE(),
        CONSTRAINT FK_RefundTransactions_Orders FOREIGN KEY (OrderId) REFERENCES Orders(Id),
        CONSTRAINT FK_RefundTransactions_Users FOREIGN KEY (RefundedBy) REFERENCES Users(Id)
    );
END
GO

/*
-- ROLLBACK SCRIPT:
IF OBJECT_ID('RefundTransactions', 'U') IS NOT NULL DROP TABLE RefundTransactions;
IF COL_LENGTH('Orders', 'CancelReason') IS NOT NULL ALTER TABLE Orders DROP COLUMN CancelReason;
IF COL_LENGTH('Orders', 'CancelledAt') IS NOT NULL ALTER TABLE Orders DROP COLUMN CancelledAt;
IF COL_LENGTH('Orders', 'RefundedBy') IS NOT NULL ALTER TABLE Orders DROP COLUMN RefundedBy;
IF COL_LENGTH('Orders', 'RefundReason') IS NOT NULL ALTER TABLE Orders DROP COLUMN RefundReason;
IF COL_LENGTH('Orders', 'RefundAmount') IS NOT NULL ALTER TABLE Orders DROP COLUMN RefundAmount;
IF COL_LENGTH('Orders', 'RefundedAt') IS NOT NULL ALTER TABLE Orders DROP COLUMN RefundedAt;
*/

-- =========================================================
-- SQL MIGRATION: MEMBERSHIP, LOYALTY POINTS & TIERED VOUCHERS
-- =========================================================

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Users') AND name = 'LoyaltyPoints')
    ALTER TABLE Users ADD LoyaltyPoints INT DEFAULT 0;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Users') AND name = 'TotalSpent')
    ALTER TABLE Users ADD TotalSpent DECIMAL(18,2) DEFAULT 0;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Users') AND name = 'MembershipTier')
    ALTER TABLE Users ADD MembershipTier NVARCHAR(20) DEFAULT 'BRONZE';

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Promotions') AND name = 'VoucherType')
    ALTER TABLE Promotions ADD VoucherType NVARCHAR(30) DEFAULT 'PUBLIC';

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Promotions') AND name = 'TargetTier')
    ALTER TABLE Promotions ADD TargetTier NVARCHAR(20) DEFAULT 'ALL';

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Promotions') AND name = 'PointsRequired')
    ALTER TABLE Promotions ADD PointsRequired INT DEFAULT 0;

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'UserVouchers')
    CREATE TABLE UserVouchers (
        Id INT IDENTITY(1,1) PRIMARY KEY,
        UserId INT NOT NULL FOREIGN KEY REFERENCES Users(Id),
        PromotionId INT NOT NULL FOREIGN KEY REFERENCES Promotions(Id),
        Code NVARCHAR(50) NOT NULL,
        IsUsed BIT NOT NULL DEFAULT 0,
        UsedAt DATETIME NULL,
        CreatedAt DATETIME NOT NULL DEFAULT GETDATE()
    );

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'PointTransactions')
    CREATE TABLE PointTransactions (
        Id INT IDENTITY(1,1) PRIMARY KEY,
        UserId INT NOT NULL FOREIGN KEY REFERENCES Users(Id),
        Points INT NOT NULL,
        Type NVARCHAR(30) NOT NULL,
        Description NVARCHAR(255) NULL,
        CreatedAt DATETIME NOT NULL DEFAULT GETDATE()
    );

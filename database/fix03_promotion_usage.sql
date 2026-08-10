SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;

BEGIN TRANSACTION;

IF COL_LENGTH('dbo.Promotions', 'PerUserLimit') IS NULL
    ALTER TABLE dbo.Promotions ADD PerUserLimit INT NOT NULL
        CONSTRAINT DF_Promotions_PerUserLimit DEFAULT 0;

IF COL_LENGTH('dbo.UserVouchers', 'Code') IS NULL
    ALTER TABLE dbo.UserVouchers ADD Code NVARCHAR(50) NULL;

IF COL_LENGTH('dbo.UserVouchers', 'UsedAt') IS NULL
    ALTER TABLE dbo.UserVouchers ADD UsedAt DATETIME NULL;

IF COL_LENGTH('dbo.UserVouchers', 'UsedOrderId') IS NULL
    ALTER TABLE dbo.UserVouchers ADD UsedOrderId INT NULL;

IF OBJECT_ID(N'dbo.PromotionUsage', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PromotionUsage (
        Id INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_PromotionUsage PRIMARY KEY,
        PromotionId INT NOT NULL,
        UserId INT NOT NULL,
        OrderId INT NOT NULL,
        UsedAt DATETIME NOT NULL CONSTRAINT DF_PromotionUsage_UsedAt DEFAULT GETDATE(),
        CONSTRAINT FK_PromotionUsage_Promotion FOREIGN KEY (PromotionId) REFERENCES dbo.Promotions(Id),
        CONSTRAINT FK_PromotionUsage_User FOREIGN KEY (UserId) REFERENCES dbo.Users(Id),
        CONSTRAINT FK_PromotionUsage_Order FOREIGN KEY (OrderId) REFERENCES dbo.Orders(Id),
        CONSTRAINT UX_PromotionUsage_Promotion_User UNIQUE (PromotionId, UserId)
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.UserVouchers') AND name = N'UX_UserVouchers_Code'
)
    CREATE UNIQUE INDEX UX_UserVouchers_Code ON dbo.UserVouchers(Code) WHERE Code IS NOT NULL;

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'dbo.Promotions') AND name = N'CK_Promotions_DiscountPercent'
)
    ALTER TABLE dbo.Promotions WITH CHECK ADD CONSTRAINT CK_Promotions_DiscountPercent
        CHECK (DiscountPercent IS NULL OR DiscountPercent BETWEEN 0 AND 100);

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'dbo.Promotions') AND name = N'CK_Promotions_PerUserLimit'
)
    EXEC(N'ALTER TABLE dbo.Promotions WITH CHECK ADD CONSTRAINT CK_Promotions_PerUserLimit
        CHECK (PerUserLimit IN (0, 1));');

COMMIT TRANSACTION;

-- ROLLBACK:
-- DROP TABLE dbo.PromotionUsage; DROP INDEX UX_UserVouchers_Code ON dbo.UserVouchers;
-- ALTER TABLE dbo.Promotions DROP CONSTRAINT CK_Promotions_DiscountPercent, CK_Promotions_PerUserLimit;

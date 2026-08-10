-- ============================================================================
-- fix20_loyalty_ledger.sql
-- FLOW-LOY-001/002/003: tach so du tieu duoc khoi tong diem tich luy suot doi,
-- gan moi bien dong diem vao su kien nghiep vu goc, va chan so du am o tang DB.
--
-- Vi sao can ca ba:
--   * LoyaltyPoints la SO DU (giam khi doi voucher). Neu hang thanh vien cung doc
--     tu cot nay thi tieu diem se ha hang — nen tong tich luy phai co cot rieng.
--   * PointTransactions khong co OrderId/VoucherId thi khong the chung minh mot don
--     da cong diem hay chua; retry/callback lap se cong hai lan.
--   * Kiem tra "du diem" o tang Java la hang rao thu nhat, CHECK constraint la hang
--     rao thu hai. Chi co hang rao thu hai moi song sot qua sua tay bang sqlcmd.
--
-- Script idempotent: chay lai nhieu lan khong loi.
-- ============================================================================

-- ---------------------------------------------------------------- FLOW-LOY-001
IF COL_LENGTH('dbo.Users', 'LifetimeEarnedPoints') IS NULL
    ALTER TABLE dbo.Users ADD LifetimeEarnedPoints INT NOT NULL
        CONSTRAINT DF_Users_LifetimeEarnedPoints DEFAULT 0;
GO

-- Backfill mot lan. Tong tich luy = so du hien tai + tat ca diem da tieu (Points < 0).
-- Cach nay khong dung toi LoyaltyPoints, nen so du khong the bi mat.
-- WHERE LifetimeEarnedPoints = 0 giu tinh idempotent: lan chay thu hai khong cong don.
UPDATE u
SET u.LifetimeEarnedPoints =
        CASE WHEN ISNULL(u.LoyaltyPoints, 0) < 0 THEN 0 ELSE ISNULL(u.LoyaltyPoints, 0) END
        + ISNULL(spent.Redeemed, 0)
FROM dbo.Users u
OUTER APPLY (
    SELECT SUM(-pt.Points) AS Redeemed
    FROM dbo.PointTransactions pt
    WHERE pt.UserId = u.Id AND pt.Points < 0
) spent
WHERE u.LifetimeEarnedPoints = 0;
GO

-- ---------------------------------------------------------------- FLOW-LOY-002
IF COL_LENGTH('dbo.PointTransactions', 'OrderId') IS NULL
    ALTER TABLE dbo.PointTransactions ADD OrderId INT NULL;
GO
IF COL_LENGTH('dbo.PointTransactions', 'VoucherId') IS NULL
    ALTER TABLE dbo.PointTransactions ADD VoucherId INT NULL;
GO
IF COL_LENGTH('dbo.PointTransactions', 'IdempotencyKey') IS NULL
    ALTER TABLE dbo.PointTransactions ADD IdempotencyKey NVARCHAR(100) NULL;
GO

-- OrderId/VoucherId la THAM CHIEU MEM, co y khong khai FOREIGN KEY. Ba ly do:
--   1. So diem la so cai: no ghi lai mot su kien da xay ra. Mot FK chan xoa se bien no thanh
--      vat can cho moi thao tac don dep don hang, va khi do nguoi ta se xoa dong so cai truoc
--      — dung thu ma so cai sinh ra de bao ve.
--   2. ON DELETE CASCADE khong dung duoc: Users -> Orders -> PointTransactions va
--      Users -> PointTransactions tao hai duong cascade, SQL Server tu choi.
--   3. Gia tri cua hai cot nay la truy vet va chong lap (cung voi IdempotencyKey), khong phai
--      rang buoc ton tai.
-- Neu ban chay lai script sau khi da tung tao FK o phien ban truoc, hai lenh duoi go chung ra.
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_PointTransactions_Orders')
    ALTER TABLE dbo.PointTransactions DROP CONSTRAINT FK_PointTransactions_Orders;
GO
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_PointTransactions_UserVouchers')
    ALTER TABLE dbo.PointTransactions DROP CONSTRAINT FK_PointTransactions_UserVouchers;
GO

-- Index loc: chi rang buoc duy nhat tren cac dong CO khoa idempotency. Cac dong lich su
-- (truoc migration) va cac but toan chinh tay khong co khoa van ghi duoc binh thuong.
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'UX_PointTransactions_IdempotencyKey'
                 AND object_id = OBJECT_ID('dbo.PointTransactions'))
    CREATE UNIQUE INDEX UX_PointTransactions_IdempotencyKey
        ON dbo.PointTransactions(IdempotencyKey)
        WHERE IdempotencyKey IS NOT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IX_PointTransactions_OrderId'
                 AND object_id = OBJECT_ID('dbo.PointTransactions'))
    CREATE INDEX IX_PointTransactions_OrderId ON dbo.PointTransactions(OrderId)
        WHERE OrderId IS NOT NULL;
GO

-- ---------------------------------------------------------------- FLOW-LOY-003
-- CHECK chi tao duoc khi khong con dong vi pham. Neu ton tai so du am thi do la hau qua
-- cua chinh loi dang sua (tru diem khong co predicate), nen dua ve 0 va ghi lai but toan
-- dieu chinh de so sach van khop.
INSERT INTO dbo.PointTransactions (UserId, Points, Type, Description)
SELECT Id, -LoyaltyPoints, 'ADJUST_NONNEGATIVE',
       N'Dieu chinh so du am ve 0 khi ap CHECK CK_Users_LoyaltyPoints_NonNegative (fix20)'
FROM dbo.Users
WHERE LoyaltyPoints < 0;
GO

UPDATE dbo.Users SET LoyaltyPoints = 0 WHERE LoyaltyPoints < 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = 'CK_Users_LoyaltyPoints_NonNegative'
                 AND parent_object_id = OBJECT_ID('dbo.Users'))
    ALTER TABLE dbo.Users ADD CONSTRAINT CK_Users_LoyaltyPoints_NonNegative
        CHECK (LoyaltyPoints >= 0);
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = 'CK_Users_LifetimeEarnedPoints_NonNegative'
                 AND parent_object_id = OBJECT_ID('dbo.Users'))
    ALTER TABLE dbo.Users ADD CONSTRAINT CK_Users_LifetimeEarnedPoints_NonNegative
        CHECK (LifetimeEarnedPoints >= 0);
GO

-- ============================================================================
-- ROLLBACK
--   ALTER TABLE dbo.Users DROP CONSTRAINT CK_Users_LifetimeEarnedPoints_NonNegative;
--   ALTER TABLE dbo.Users DROP CONSTRAINT CK_Users_LoyaltyPoints_NonNegative;
--   DROP INDEX IX_PointTransactions_OrderId ON dbo.PointTransactions;
--   DROP INDEX UX_PointTransactions_IdempotencyKey ON dbo.PointTransactions;
--   ALTER TABLE dbo.PointTransactions DROP COLUMN IdempotencyKey, VoucherId, OrderId;
--   ALTER TABLE dbo.Users DROP CONSTRAINT DF_Users_LifetimeEarnedPoints;
--   ALTER TABLE dbo.Users DROP COLUMN LifetimeEarnedPoints;
-- Gian doan: khong co CHECK thi phai chay lai kiem ke so du am truoc khi mo lai doi voucher.
-- ============================================================================

-- ============================================================================
--  fix24_refund_rejection.sql — luu quyet dinh TU CHOI hoan tien (BUG-10, INV-9).
--
--  VAN DE GOC
--    Nut "Tu choi" o man hinh /admin/orders chi hien mot flash message
--    ("Da ghi nhan tu choi hoan tien") roi khong lam gi ca: khong doi trang thai,
--    khong luu ly do, khong ghi audit. Don van nam nguyen trong tab "Cho hoan tien"
--    nen lan sau vao lai van thay, va khi khach khieu nai thi khong co gi doi chat.
--
--    Khong the tai dung OrderStatus='cancelled' cho viec nay: ba hang so doanh thu
--    (REVENUE_ORDER_PREDICATE, SOLD_SEAT_ORDER_PREDICATE) loai don 'cancelled',
--    nen danh dau nham se lam bao cao hut di mot ve DA BAN va DA THU TIEN.
--    Vi vay dung ba cot rieng.
--
--  Idempotent: chay lai nhieu lan khong doi ket qua.
-- ============================================================================
SET XACT_ABORT ON;
SET NOCOUNT ON;
BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH('dbo.Orders', 'RefundRejectedAt') IS NULL
    BEGIN
        ALTER TABLE dbo.Orders ADD RefundRejectedAt DATETIME NULL;
    END;

    IF COL_LENGTH('dbo.Orders', 'RefundRejectReason') IS NULL
    BEGIN
        ALTER TABLE dbo.Orders ADD RefundRejectReason NVARCHAR(500) NULL;
    END;

    IF COL_LENGTH('dbo.Orders', 'RefundRejectedBy') IS NULL
    BEGIN
        ALTER TABLE dbo.Orders ADD RefundRejectedBy INT NULL;
    END;

    COMMIT TRANSACTION;
    PRINT 'fix24_refund_rejection.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- ROLLBACK:
--   ALTER TABLE dbo.Orders DROP COLUMN RefundRejectedAt;
--   ALTER TABLE dbo.Orders DROP COLUMN RefundRejectReason;
--   ALTER TABLE dbo.Orders DROP COLUMN RefundRejectedBy;

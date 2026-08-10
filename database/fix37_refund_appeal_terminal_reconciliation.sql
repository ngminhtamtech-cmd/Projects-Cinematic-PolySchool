-- Reconcile legacy refund appeals that remained pending after their order reached
-- a terminal state. This migration never changes money, seats, loyalty or Orders.
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'CineBookDB' AND DB_NAME() NOT LIKE N'CineBookIT[_]%'
    THROW 51700, 'fix37 only accepts CineBookDB or an ephemeral CineBookIT_* database.', 1;
IF OBJECT_ID(N'dbo.UserAppeals', N'U') IS NULL OR OBJECT_ID(N'dbo.Orders', N'U') IS NULL
    THROW 51701, 'Run the base schema and fix36 before fix37.', 1;
IF COL_LENGTH(N'dbo.UserAppeals', N'AppealType') IS NULL
   OR COL_LENGTH(N'dbo.UserAppeals', N'OrderId') IS NULL
   OR COL_LENGTH(N'dbo.Orders', N'RefundedAt') IS NULL
    THROW 51702, 'Required refund appeal/order columns are missing.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    -- A refund is considered complete only when all canonical terminal markers exist.
    UPDATE appeal
       SET Status=N'approved',
           AdminResponse=COALESCE(NULLIF(appeal.AdminResponse, N''),
               N'Đơn đã được hoàn tiền trước khi yêu cầu được đối soát; hệ thống đóng yêu cầu và không hoàn tiền lần hai.'),
           ResolvedAt=COALESCE(appeal.ResolvedAt, GETDATE())
    FROM dbo.UserAppeals appeal
    JOIN dbo.Orders orders ON orders.Id=appeal.OrderId
    WHERE appeal.AppealType=N'refund' AND appeal.Status=N'pending'
      AND orders.PaymentStatus=N'refunded'
      AND orders.OrderStatus=N'cancelled'
      AND orders.RefundedAt IS NOT NULL;

    -- Terminal orders that were not refunded are not financially actionable.
    UPDATE appeal
       SET Status=N'rejected',
           AdminResponse=COALESCE(NULLIF(appeal.AdminResponse, N''),
               N'Đơn đã kết thúc hoặc bị hủy mà không ở trạng thái có thể hoàn tiền; yêu cầu được đóng để đối soát.'),
           ResolvedAt=COALESCE(appeal.ResolvedAt, GETDATE())
    FROM dbo.UserAppeals appeal
    JOIN dbo.Orders orders ON orders.Id=appeal.OrderId
    WHERE appeal.AppealType=N'refund' AND appeal.Status=N'pending'
      AND NOT (orders.PaymentStatus=N'paid' AND orders.OrderStatus=N'confirmed')
      AND NOT (orders.PaymentStatus=N'refunded' AND orders.OrderStatus=N'cancelled'
               AND orders.RefundedAt IS NOT NULL);

    COMMIT TRANSACTION;
    PRINT 'fix37_refund_appeal_terminal_reconciliation.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- ROLLBACK: restore the database backup taken before applying this migration.

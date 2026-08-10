-- fix29_booking_command_views.sql
-- Read-model predicates for reporting and invariant inspection.  No write
-- behaviour is changed and the views are safe to recreate.
SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    EXEC(N'CREATE OR ALTER VIEW dbo.vw_OrderLifecycle
    AS
    SELECT o.Id AS OrderId, o.UserId, o.ShowtimeId, o.PaymentMethod,
           o.OrderStatus, o.PaymentStatus, o.TicketCode, o.CounterExpiresAt,
           o.RefundedAt, o.RefundAmount, o.RedeemedAt,
           CASE
             WHEN o.OrderStatus IN (''created'',''pending'') AND o.PaymentStatus=''pending'' THEN ''DRAFT_HELD''
             WHEN o.OrderStatus IN (''created'',''pending'') AND o.PaymentStatus=''failed'' THEN ''PAYMENT_FAILED_RETRYABLE''
             WHEN o.OrderStatus=''confirmed'' AND o.PaymentStatus=''pending'' AND o.PaymentMethod=''counter'' THEN ''COUNTER_AWAITING_PAYMENT''
             WHEN o.OrderStatus=''confirmed'' AND o.PaymentStatus=''paid'' THEN ''PAID_CONFIRMED''
             WHEN o.OrderStatus=''redeemed'' AND o.PaymentStatus=''paid'' THEN ''PAID_REDEEMED''
             WHEN o.OrderStatus=''cancelled'' AND o.PaymentStatus=''cancelled'' THEN ''CANCELLED_UNPAID''
             WHEN o.OrderStatus=''cancelled'' AND o.PaymentStatus=''refunded'' THEN ''FULLY_REFUNDED''
             WHEN o.OrderStatus=''completed'' AND o.PaymentStatus=''paid'' THEN ''COMPLETED_LEGACY''
             ELSE ''UNKNOWN''
           END AS LifecycleState
    FROM dbo.Orders o;');

    EXEC(N'CREATE OR ALTER VIEW dbo.vw_ShowtimeAvailability
    AS
    SELECT ShowtimeId,
           SUM(CASE WHEN Status <> ''maintenance'' THEN 1 ELSE 0 END) AS BookableSeats,
           SUM(CASE WHEN Status = ''available'' THEN 1 ELSE 0 END) AS AvailableSeats,
           SUM(CASE WHEN Status = ''held'' THEN 1 ELSE 0 END) AS HeldSeats,
           SUM(CASE WHEN Status = ''booked'' THEN 1 ELSE 0 END) AS BookedSeats,
           SUM(CASE WHEN Status = ''maintenance'' THEN 1 ELSE 0 END) AS MaintenanceSeats
    FROM dbo.ShowtimeSeats
    GROUP BY ShowtimeId;');

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = N'IX_BookingCommandExecutions_Order_Status'
                     AND object_id = OBJECT_ID(N'dbo.BookingCommandExecutions'))
        CREATE INDEX IX_BookingCommandExecutions_Order_Status
            ON dbo.BookingCommandExecutions(OrderId, Status, CreatedAt DESC);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = N'IX_BookingOutbox_Status_NextAttempt'
                     AND object_id = OBJECT_ID(N'dbo.BookingOutbox'))
        CREATE INDEX IX_BookingOutbox_Status_NextAttempt
            ON dbo.BookingOutbox(Status, NextAttemptAt, CreatedAt, Id);

    COMMIT TRANSACTION;
    PRINT 'fix29_booking_command_views.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- ROLLBACK:
-- DROP VIEW dbo.vw_OrderLifecycle;
-- DROP VIEW dbo.vw_ShowtimeAvailability;

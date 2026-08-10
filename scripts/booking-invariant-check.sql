-- Read-only, fail-closed invariant query for an ephemeral CineBookIT_* database.
-- The PowerShell wrapper converts the two columns below to JSON and fails when
-- any ViolationCount is non-zero.  Do not add a data-fixing statement here.
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DB_NAME() NOT LIKE N'CineBookIT[_]%'
    THROW 51500, 'booking invariant gate must run only on CineBookIT_*', 1;

;WITH checks AS (
    SELECT CAST(N'INV-ORDER-STATE' AS NVARCHAR(80)) AS InvariantId,
           COUNT_BIG(*) AS ViolationCount
    FROM dbo.vw_OrderLifecycle
    WHERE LifecycleState = N'UNKNOWN'

    UNION ALL
    SELECT N'INV-ORDER-METADATA', COUNT_BIG(*)
    FROM dbo.Orders o
    WHERE (o.OrderStatus = N'cancelled'
           AND (o.CancelledAt IS NULL OR NULLIF(LTRIM(RTRIM(o.CancelReason)), N'') IS NULL))
       OR (o.OrderStatus = N'redeemed' AND o.PaymentStatus = N'paid' AND o.RedeemedAt IS NULL)
       OR (o.OrderStatus = N'confirmed' AND o.PaymentStatus = N'pending'
           AND (o.PaymentMethod <> N'counter' OR o.PaymentProvider <> N'counter'
                OR o.CounterExpiresAt IS NULL))
       OR (o.OrderStatus = N'cancelled' AND o.PaymentStatus = N'refunded'
           AND (o.RefundAmount IS NULL OR o.TotalAmount IS NULL
                OR o.RefundAmount <> o.TotalAmount OR o.RefundedAt IS NULL
                OR o.RefundedBy IS NULL OR NULLIF(LTRIM(RTRIM(o.RefundReason)), N'') IS NULL))

    UNION ALL
    SELECT N'INV-PAYMENT', COUNT_BIG(*)
    FROM dbo.Orders o
    WHERE o.PaymentStatus NOT IN (N'pending',N'paid',N'failed',N'cancelled',N'refunded')
       OR (o.PaymentStatus = N'refunded' AND o.OrderStatus <> N'cancelled')
       OR (o.PaymentStatus = N'paid' AND o.OrderStatus NOT IN (N'confirmed',N'redeemed',N'completed'))

    UNION ALL
    SELECT N'INV-COUNTER', COUNT_BIG(*)
    FROM dbo.Orders o
    WHERE o.OrderStatus = N'confirmed' AND o.PaymentStatus = N'pending'
      AND o.PaymentMethod = N'counter'
      AND (o.CounterExpiresAt IS NULL OR o.CounterExpiresAt <= GETDATE())

    UNION ALL
    SELECT N'INV-SEAT-STATUS', COUNT_BIG(*)
    FROM dbo.ShowtimeSeats s
    WHERE s.Status NOT IN (N'available',N'held',N'booked',N'maintenance')
           OR (s.Status IN (N'available',N'maintenance')
            AND (s.ClaimedByOrderId IS NOT NULL OR s.HeldByUserId IS NOT NULL
                OR s.HeldUntil IS NOT NULL))

    UNION ALL
    SELECT N'INV-SEAT-CLAIM', COUNT_BIG(*)
    FROM dbo.ShowtimeSeats s
    LEFT JOIN dbo.Orders o ON o.Id = s.ClaimedByOrderId
    WHERE (s.Status IN (N'held',N'booked') AND s.ClaimedByOrderId IS NULL)
       OR (s.Status = N'held' AND (s.HeldByUserId IS NULL OR s.HeldAt IS NULL
                                   OR s.HeldUntil IS NULL OR s.HeldUntil <= GETDATE()))
       OR (s.Status = N'held' AND (o.Id IS NULL OR o.OrderStatus NOT IN (N'created',N'pending')))
       OR (s.Status = N'booked' AND (o.Id IS NULL OR o.OrderStatus NOT IN (N'confirmed',N'redeemed',N'completed')))

    UNION ALL
    SELECT N'INV-SEAT-SHOWTIME', COUNT_BIG(*)
    FROM dbo.OrderSeats os
    JOIN dbo.Orders o ON o.Id = os.OrderId
    JOIN dbo.ShowtimeSeats s ON s.Id = os.ShowtimeSeatId
    WHERE o.OrderStatus IN (N'created',N'pending',N'confirmed',N'redeemed',N'completed')
      AND o.ShowtimeId <> s.ShowtimeId

    UNION ALL
    SELECT N'INV-SEAT-DUPLICATE-ACTIVE', COUNT_BIG(*)
    FROM (
        SELECT os.ShowtimeSeatId
        FROM dbo.OrderSeats os
        JOIN dbo.Orders o ON o.Id = os.OrderId
        WHERE o.OrderStatus IN (N'created',N'pending',N'confirmed',N'redeemed',N'completed')
        GROUP BY os.ShowtimeSeatId
        HAVING COUNT(DISTINCT os.OrderId) > 1
    ) duplicates

    UNION ALL
    SELECT N'INV-MONEY', COUNT_BIG(*)
    FROM dbo.Orders o
    WHERE o.TotalAmount IS NULL OR o.TotalAmount < 0
       OR o.SeatSubtotal IS NULL OR o.SeatSubtotal < 0
       OR o.ComboSubtotal IS NULL OR o.ComboSubtotal < 0
       OR o.DiscountAmount IS NULL OR o.DiscountAmount < 0
       OR (o.RefundAmount IS NOT NULL AND (o.RefundAmount < 0
                                           OR o.TotalAmount IS NULL
                                           OR o.RefundAmount > o.TotalAmount))

    UNION ALL
    SELECT N'INV-REFUND', COUNT_BIG(*)
    FROM dbo.RefundTransactions r
    LEFT JOIN dbo.Orders o ON o.Id = r.OrderId
    WHERE o.Id IS NULL OR r.Amount IS NULL OR r.Amount <= 0
       OR o.PaymentStatus <> N'refunded' OR o.OrderStatus <> N'cancelled'
       OR r.Amount <> o.TotalAmount

    UNION ALL
    SELECT N'INV-INVOICE', COUNT_BIG(*)
    FROM (
        SELECT OrderId, InvoiceType
        FROM dbo.Invoices
        GROUP BY OrderId, InvoiceType
        HAVING COUNT(*) > 1
    ) d

    UNION ALL
    SELECT N'INV-PROMOTION', COUNT_BIG(*)
    FROM dbo.Promotions p
    WHERE p.UsedCount < 0
       OR (p.UsageLimit IS NOT NULL AND p.UsedCount > p.UsageLimit)
       OR EXISTS (
            SELECT 1
            FROM dbo.PromotionUsage pu
            GROUP BY pu.PromotionId, pu.OrderId
            HAVING pu.PromotionId = p.Id AND COUNT(*) > 1
       )

    UNION ALL
    SELECT N'INV-VOUCHER', COUNT_BIG(*)
    FROM dbo.UserVouchers v
    LEFT JOIN dbo.Orders o ON o.Id = v.UsedOrderId
    WHERE (v.IsUsed = 1 AND (v.UsedOrderId IS NULL OR o.UserId <> v.UserId
                             OR o.PromotionId <> v.PromotionId))
       OR (v.IsUsed = 0 AND v.UsedOrderId IS NOT NULL)

    UNION ALL
    SELECT N'INV-LOYALTY', COUNT_BIG(*)
    FROM dbo.Users u
    WHERE u.LoyaltyPoints < 0 OR u.LifetimeEarnedPoints < 0

    UNION ALL
    SELECT N'INV-POINT-DEBT', COUNT_BIG(*)
    FROM dbo.Users u
    WHERE u.LoyaltyPointDebt < 0
       OR EXISTS (SELECT 1 FROM dbo.LoyaltyPointDebtLedger d
                  WHERE d.UserId = u.Id AND d.Points <= 0)

    UNION ALL
    SELECT N'INV-APPEAL', COUNT_BIG(*)
    FROM dbo.UserAppeals a
    WHERE a.Status NOT IN (N'pending',N'approved',N'rejected')
       OR a.AppealType NOT IN (N'account',N'refund')
       OR (a.AppealType = N'refund' AND a.Status = N'pending' AND a.OrderId IS NULL)
       OR (a.AppealType = N'account' AND a.Status = N'pending' AND a.OrderId IS NOT NULL)

    UNION ALL
    SELECT N'INV-CHECKIN', COUNT_BIG(*)
    FROM dbo.Orders o
    WHERE o.OrderStatus = N'redeemed'
      AND (o.PaymentStatus <> N'paid' OR o.RedeemedAt IS NULL)

    UNION ALL
    SELECT N'INV-TENANT', COUNT_BIG(*)
    FROM dbo.Users u
    WHERE u.Role = N'staff' AND u.CinemaId IS NULL

    UNION ALL
    SELECT N'INV-COMMAND-LEDGER', COUNT_BIG(*)
    FROM dbo.BookingCommandExecutions c
    WHERE LEN(c.RequestHash) <> 64
       OR c.Status NOT IN (N'processing',N'succeeded',N'failed')

    UNION ALL
    SELECT N'INV-OUTBOX', COUNT_BIG(*)
    FROM dbo.BookingOutbox b
    WHERE b.Status IN (N'pending',N'processing',N'failed')
       OR b.Attempts < 0

    UNION ALL
    SELECT N'INV-SCHEMA-TRUST', COUNT_BIG(*)
    FROM (VALUES
        (CASE WHEN COL_LENGTH(N'dbo.Orders',N'StateVersion') IS NOT NULL THEN 0 ELSE 1 END),
        (CASE WHEN COL_LENGTH(N'dbo.ShowtimeSeats',N'ClaimedByOrderId') IS NOT NULL THEN 0 ELSE 1 END),
        (CASE WHEN OBJECT_ID(N'dbo.BookingCommandExecutions',N'U') IS NOT NULL THEN 0 ELSE 1 END),
        (CASE WHEN OBJECT_ID(N'dbo.BookingStateTransitions',N'U') IS NOT NULL THEN 0 ELSE 1 END),
        (CASE WHEN OBJECT_ID(N'dbo.BookingOutbox',N'U') IS NOT NULL THEN 0 ELSE 1 END),
        (CASE WHEN OBJECT_ID(N'dbo.LoyaltyPointDebtLedger',N'U') IS NOT NULL THEN 0 ELSE 1 END),
        (CASE WHEN EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'FK_ShowtimeSeats_ClaimedByOrder'
                           AND is_disabled=0 AND is_not_trusted=0) THEN 0 ELSE 1 END),
        (CASE WHEN EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'FK_UserAppeals_Order'
                           AND is_disabled=0 AND is_not_trusted=0) THEN 0 ELSE 1 END),
        (CASE WHEN EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'CK_Users_LoyaltyPointDebt_NonNegative'
                           AND is_disabled=0 AND is_not_trusted=0) THEN 0 ELSE 1 END),
        (CASE WHEN EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'UX_BookingCommandExecutions_Key'
                           AND is_disabled=0) THEN 0 ELSE 1 END),
        (CASE WHEN EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'UX_BookingOutbox_DedupeKey'
                           AND is_disabled=0) THEN 0 ELSE 1 END),
        (CASE WHEN EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'UX_RefundTransactions_Order'
                           AND is_disabled=0) THEN 0 ELSE 1 END)
    ) missing(Flag)
    WHERE Flag = 1
)
SELECT InvariantId, ViolationCount
FROM checks
ORDER BY InvariantId;

SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.Orders','CounterReminderSentAt') IS NULL
    ALTER TABLE dbo.Orders ADD CounterReminderSentAt DATETIME2(0) NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_Orders_CounterReminder'
               AND object_id=OBJECT_ID('dbo.Orders'))
    CREATE INDEX IX_Orders_CounterReminder
        ON dbo.Orders(CounterExpiresAt, CounterReminderSentAt)
        INCLUDE (UserId, TicketCode, PaymentStatus, OrderStatus)
        WHERE PaymentMethod='counter';

COMMIT TRANSACTION;

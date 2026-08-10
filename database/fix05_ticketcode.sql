SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;

BEGIN TRANSACTION;

IF COL_LENGTH('dbo.Orders', 'TicketCode') IS NOT NULL
BEGIN
    ALTER TABLE dbo.Orders ALTER COLUMN TicketCode NVARCHAR(32) NULL;
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.Orders') AND name = N'UX_Orders_TicketCode'
)
BEGIN
    CREATE UNIQUE INDEX UX_Orders_TicketCode
        ON dbo.Orders(TicketCode)
        WHERE TicketCode IS NOT NULL;
END;

COMMIT TRANSACTION;

-- ROLLBACK:
-- DROP INDEX UX_Orders_TicketCode ON dbo.Orders;
-- Only shrink TicketCode after proving every stored value fits the previous width.

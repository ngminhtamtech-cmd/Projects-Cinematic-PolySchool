-- Script: fix01_money_and_orders.sql
-- Goal: Upgrade monetary columns to DECIMAL(19,2) and add PaymentProvider, IdempotencyKey, CounterExpiresAt to Orders
-- Idempotent script for CineBookDB & CineBookDB_Test

SET NUMERIC_ROUNDABORT OFF;
SET ANSI_PADDING, ANSI_WARNINGS, CONCAT_NULL_YIELDS_NULL, ARITHABORT, QUOTED_IDENTIFIER, ANSI_NULLS ON;
GO

IF COL_LENGTH('Orders', 'PaymentProvider') IS NULL
BEGIN
    ALTER TABLE Orders ADD PaymentProvider NVARCHAR(50) NULL;
END
GO

IF COL_LENGTH('Orders', 'IdempotencyKey') IS NULL
BEGIN
    ALTER TABLE Orders ADD IdempotencyKey NVARCHAR(100) NULL;
END
GO

IF COL_LENGTH('Orders', 'CounterExpiresAt') IS NULL
BEGIN
    ALTER TABLE Orders ADD CounterExpiresAt DATETIME NULL;
END
GO

IF COL_LENGTH('Orders', 'SeatSubtotal') IS NULL
BEGIN
    ALTER TABLE Orders ADD SeatSubtotal DECIMAL(19,2) NOT NULL DEFAULT 0.00;
END
GO

IF COL_LENGTH('Orders', 'ComboSubtotal') IS NULL
BEGIN
    ALTER TABLE Orders ADD ComboSubtotal DECIMAL(19,2) NOT NULL DEFAULT 0.00;
END
GO

IF COL_LENGTH('Orders', 'DiscountAmount') IS NULL
BEGIN
    ALTER TABLE Orders ADD DiscountAmount DECIMAL(19,2) NOT NULL DEFAULT 0.00;
END
GO

IF COL_LENGTH('Orders', 'TotalAmount') IS NULL
BEGIN
    ALTER TABLE Orders ADD TotalAmount DECIMAL(19,2) NOT NULL DEFAULT 0.00;
END
GO

IF COL_LENGTH('Orders', 'TicketCode') IS NULL
BEGIN
    ALTER TABLE Orders ADD TicketCode NVARCHAR(50) NULL;
END
GO

IF COL_LENGTH('Orders', 'TicketQrUrl') IS NULL
BEGIN
    ALTER TABLE Orders ADD TicketQrUrl NVARCHAR(255) NULL;
END
GO

IF COL_LENGTH('Orders', 'TransactionId') IS NULL
BEGIN
    ALTER TABLE Orders ADD TransactionId NVARCHAR(100) NULL;
END
GO

IF COL_LENGTH('Orders', 'PayRedirectUrl') IS NULL
BEGIN
    ALTER TABLE Orders ADD PayRedirectUrl NVARCHAR(255) NULL;
END
GO

IF COL_LENGTH('Orders', 'RedeemedAt') IS NULL
BEGIN
    ALTER TABLE Orders ADD RedeemedAt DATETIME NULL;
END
GO

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'UQ_Orders_IdempotencyKey' AND object_id = OBJECT_ID('Orders'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UQ_Orders_IdempotencyKey
    ON Orders(IdempotencyKey)
    WHERE IdempotencyKey IS NOT NULL;
END
GO

-- Upgrade monetary columns to DECIMAL(19,2)
ALTER TABLE Orders ALTER COLUMN SeatSubtotal DECIMAL(19,2) NOT NULL;
ALTER TABLE Orders ALTER COLUMN ComboSubtotal DECIMAL(19,2) NOT NULL;
ALTER TABLE Orders ALTER COLUMN DiscountAmount DECIMAL(19,2) NOT NULL;
ALTER TABLE Orders ALTER COLUMN TotalAmount DECIMAL(19,2) NOT NULL;

IF COL_LENGTH('OrderSeats', 'UnitPrice') IS NOT NULL
BEGIN
    ALTER TABLE OrderSeats ALTER COLUMN UnitPrice DECIMAL(19,2) NOT NULL;
END
IF COL_LENGTH('OrderSeats', 'Price') IS NOT NULL
BEGIN
    ALTER TABLE OrderSeats ALTER COLUMN Price DECIMAL(19,2) NOT NULL;
END

IF COL_LENGTH('OrderComboFoods', 'UnitPrice') IS NOT NULL
BEGIN
    ALTER TABLE OrderComboFoods ALTER COLUMN UnitPrice DECIMAL(19,2) NOT NULL;
END
IF COL_LENGTH('OrderComboFoods', 'Price') IS NOT NULL
BEGIN
    ALTER TABLE OrderComboFoods ALTER COLUMN Price DECIMAL(19,2) NOT NULL;
END

IF COL_LENGTH('Showtimes', 'BasePrice') IS NOT NULL
BEGIN
    ALTER TABLE Showtimes ALTER COLUMN BasePrice DECIMAL(19,2) NOT NULL;
END

IF COL_LENGTH('Seats', 'PriceSurcharge') IS NOT NULL
BEGIN
    ALTER TABLE Seats ALTER COLUMN PriceSurcharge DECIMAL(19,2) NOT NULL;
END

IF COL_LENGTH('ComboFoods', 'Price') IS NOT NULL
BEGIN
    ALTER TABLE ComboFoods ALTER COLUMN Price DECIMAL(19,2) NOT NULL;
END

IF COL_LENGTH('Promotions', 'MaxDiscount') IS NOT NULL
BEGIN
    ALTER TABLE Promotions ALTER COLUMN MaxDiscount DECIMAL(19,2) NULL;
END

PRINT 'Completed fix01_money_and_orders.sql migration.';
GO

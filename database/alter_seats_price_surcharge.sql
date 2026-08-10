-- Alter Seats table to support customizable seat price surcharges
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Seats') AND name = 'PriceSurcharge')
BEGIN
    ALTER TABLE Seats ADD PriceSurcharge DECIMAL(10,2) NOT NULL DEFAULT 0.00;
END
GO

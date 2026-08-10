-- Migration script: Add Format, Version, Language columns to Showtimes table

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'Showtimes') AND name = 'Format')
BEGIN
    ALTER TABLE Showtimes ADD Format NVARCHAR(50) NULL DEFAULT '2D';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'Showtimes') AND name = 'Version')
BEGIN
    ALTER TABLE Showtimes ADD Version NVARCHAR(50) NULL DEFAULT N'Lồng tiếng';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'Showtimes') AND name = 'Language')
BEGIN
    ALTER TABLE Showtimes ADD Language NVARCHAR(50) NULL DEFAULT N'Tiếng Việt';
END
GO

-- Update existing records to have non-null default values
UPDATE Showtimes SET Format = '2D' WHERE Format IS NULL;
UPDATE Showtimes SET Version = N'Lồng tiếng' WHERE Version IS NULL;
UPDATE Showtimes SET Language = N'Tiếng Việt' WHERE Language IS NULL;
GO

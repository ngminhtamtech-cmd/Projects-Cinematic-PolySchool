-- Migration script for Rooms Status and AdminNotifications table
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Rooms') AND name = 'Status')
BEGIN
    ALTER TABLE Rooms ADD Status NVARCHAR(20) NOT NULL DEFAULT 'active';
END;

IF OBJECT_ID('AdminNotifications', 'U') IS NULL
BEGIN
    CREATE TABLE AdminNotifications (
        Id INT IDENTITY PRIMARY KEY,
        Title NVARCHAR(200) NOT NULL,
        Message NVARCHAR(MAX) NOT NULL,
        Category NVARCHAR(50) NOT NULL DEFAULT 'room',
        Severity NVARCHAR(20) NOT NULL DEFAULT 'info',
        TargetType NVARCHAR(50) NULL,
        TargetId NVARCHAR(50) NULL,
        ActionUrl NVARCHAR(255) NULL,
        IsRead BIT NOT NULL DEFAULT 0,
        CreatedAt DATETIME NOT NULL DEFAULT GETDATE()
    );
    CREATE INDEX IX_AdminNotifications_IsRead_CreatedAt ON AdminNotifications(IsRead, CreatedAt DESC);
END;

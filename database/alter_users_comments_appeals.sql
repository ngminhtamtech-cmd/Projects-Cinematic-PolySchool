-- =========================================================
-- SQL MIGRATION: ACCOUNT LOCKING, WARNING SYSTEM, COMMENT REPORTS & APPEALS
-- =========================================================

-- 1. Bổ sung các cột kiểm soát khóa & cảnh cáo vào bảng Users
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Users') AND name = 'IsLocked')
    ALTER TABLE Users ADD IsLocked BIT NOT NULL DEFAULT 0;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Users') AND name = 'LockReason')
    ALTER TABLE Users ADD LockReason NVARCHAR(255) NULL;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Users') AND name = 'WarningCount')
    ALTER TABLE Users ADD WarningCount INT NOT NULL DEFAULT 0;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Users') AND name = 'LockedAt')
    ALTER TABLE Users ADD LockedAt DATETIME NULL;

-- 2. Tạo bảng CommentReports lưu lịch sử report bình luận
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'CommentReports')
    CREATE TABLE CommentReports (
        Id INT IDENTITY(1,1) PRIMARY KEY,
        CommentId INT NOT NULL FOREIGN KEY REFERENCES Comments(Id) ON DELETE CASCADE,
        ReporterUserId INT NULL FOREIGN KEY REFERENCES Users(Id),
        Reason NVARCHAR(255) NOT NULL,
        Status NVARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (Status IN ('pending', 'warned', 'locked', 'dismissed')),
        CreatedAt DATETIME NOT NULL DEFAULT GETDATE()
    );

-- 3. Tạo bảng UserAppeals lưu đơn kháng cáo của người dùng bị khóa
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'UserAppeals')
    CREATE TABLE UserAppeals (
        Id INT IDENTITY(1,1) PRIMARY KEY,
        UserId INT NOT NULL FOREIGN KEY REFERENCES Users(Id),
        Email NVARCHAR(100) NOT NULL,
        Reason NVARCHAR(MAX) NOT NULL,
        Status NVARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (Status IN ('pending', 'approved', 'rejected')),
        AdminResponse NVARCHAR(MAX) NULL,
        ResolvedByUserId INT NULL FOREIGN KEY REFERENCES Users(Id),
        CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
        ResolvedAt DATETIME NULL
    );

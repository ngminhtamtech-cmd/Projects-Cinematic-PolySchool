-- ============================================================================
-- fix21_user_notifications.sql
-- FLOW-NOTIFY-USER-001 + FLOW-NOTIFY-SOLDOUT-004.
--
-- Hai bang, hai vai tro khac nhau:
--
--   UserNotifications      — thong bao do manager/admin soan gui toi nguoi dung.
--                            Co lich hien thi (VisibleFrom/VisibleUntil), pham vi
--                            (TargetType/TargetId, CinemaId) va tac gia.
--
--   NotificationRecipients — SO NHAN. Mot dong = "thong bao X da den user Y",
--                            kem moc da doc rieng cua user do.
--
-- Vi sao NotificationRecipients dung chung cho ca AdminNotifications:
--   AdminNotifications luu trang thai da doc bang mot cot IsRead DUY NHAT tren dong
--   thong bao. Manager va admin cung nhin mot dong do, nen manager bam "da doc" la
--   admin mat luon dau chua doc (FLOW-NOTIFY-SOLDOUT-004). Trang thai da doc la
--   thuoc tinh cua CAP (thong bao, nguoi nhan) chu khong phai cua thong bao, nen no
--   phai nam o bang so nhan. SourceType phan biet nguon: 'admin' | 'user'.
--
-- Ghi chu FK: KHONG khai FK tu NotificationId vi cot nay tro toi hai bang khac nhau
-- tuy SourceType. Doi lai, don dep duoc lam tuong minh: xoa thong bao thi xoa ca so
-- nhan cua no (xem AdminService.deleteNotification / resolveSoldOutAlerts).
--
-- Script idempotent: chay lai nhieu lan khong loi.
-- ============================================================================

IF OBJECT_ID('dbo.UserNotifications', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.UserNotifications (
        Id              INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        Title           NVARCHAR(200) NOT NULL,
        Message         NVARCHAR(MAX) NOT NULL,
        Severity        NVARCHAR(20) NOT NULL CONSTRAINT DF_UserNotifications_Severity DEFAULT 'info',
        -- Pham vi nguoi nhan: ALL | CINEMA | TIER | USER
        TargetType      NVARCHAR(30) NOT NULL CONSTRAINT DF_UserNotifications_TargetType DEFAULT 'ALL',
        TargetId        NVARCHAR(50) NULL,
        CinemaId        INT NULL,
        ActionUrl       NVARCHAR(255) NULL,
        VisibleFrom     DATETIME2(3) NOT NULL CONSTRAINT DF_UserNotifications_VisibleFrom DEFAULT SYSDATETIME(),
        VisibleUntil    DATETIME2(3) NULL,
        -- active | disabled
        Status          NVARCHAR(20) NOT NULL CONSTRAINT DF_UserNotifications_Status DEFAULT 'active',
        CreatedByUserId INT NOT NULL,
        CreatedAt       DATETIME2(3) NOT NULL CONSTRAINT DF_UserNotifications_CreatedAt DEFAULT SYSDATETIME(),
        CONSTRAINT FK_UserNotifications_Author FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users(Id),
        CONSTRAINT FK_UserNotifications_Cinemas FOREIGN KEY (CinemaId) REFERENCES dbo.Cinemas(Id)
    );
END;
GO

IF OBJECT_ID('dbo.NotificationRecipients', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.NotificationRecipients (
        Id             INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        -- 'user'  -> NotificationId tro toi UserNotifications.Id
        -- 'admin' -> NotificationId tro toi AdminNotifications.Id
        SourceType     NVARCHAR(20) NOT NULL CONSTRAINT DF_NotificationRecipients_Source DEFAULT 'user',
        NotificationId INT NOT NULL,
        UserId         INT NOT NULL,
        DeliveredAt    DATETIME2(3) NOT NULL CONSTRAINT DF_NotificationRecipients_Delivered DEFAULT SYSDATETIME(),
        ReadAt         DATETIME2(3) NULL,
        CONSTRAINT FK_NotificationRecipients_Users FOREIGN KEY (UserId) REFERENCES dbo.Users(Id),
        CONSTRAINT CK_NotificationRecipients_Source CHECK (SourceType IN ('user', 'admin'))
    );
END;
GO

-- Mot nguoi nhan chi co dung mot so nhan cho mot thong bao: danh dau da doc hai lan
-- khong duoc tao hai dong, va gui trung khong nhan doi hop thu.
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'UX_NotificationRecipients_Receipt'
                 AND object_id = OBJECT_ID('dbo.NotificationRecipients'))
    CREATE UNIQUE INDEX UX_NotificationRecipients_Receipt
        ON dbo.NotificationRecipients(SourceType, NotificationId, UserId);
GO

-- Hop thu cua mot user: loc chua doc roi sap xep theo thoi diem nhan.
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IX_NotificationRecipients_Inbox'
                 AND object_id = OBJECT_ID('dbo.NotificationRecipients'))
    CREATE INDEX IX_NotificationRecipients_Inbox
        ON dbo.NotificationRecipients(UserId, ReadAt) INCLUDE (SourceType, NotificationId, DeliveredAt);
GO

-- Danh sach thong bao dang hien cho user: loc theo cua so thoi gian va trang thai.
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IX_UserNotifications_Visibility'
                 AND object_id = OBJECT_ID('dbo.UserNotifications'))
    CREATE INDEX IX_UserNotifications_Visibility
        ON dbo.UserNotifications(Status, VisibleFrom, VisibleUntil);
GO

-- ============================================================================
-- ROLLBACK
--   DROP INDEX IX_UserNotifications_Visibility ON dbo.UserNotifications;
--   DROP INDEX IX_NotificationRecipients_Inbox ON dbo.NotificationRecipients;
--   DROP INDEX UX_NotificationRecipients_Receipt ON dbo.NotificationRecipients;
--   DROP TABLE dbo.NotificationRecipients;
--   DROP TABLE dbo.UserNotifications;
-- Sau rollback, trang thai da doc quay ve AdminNotifications.IsRead dung chung —
-- tuc la loi FLOW-NOTIFY-SOLDOUT-004 tro lai.
-- ============================================================================

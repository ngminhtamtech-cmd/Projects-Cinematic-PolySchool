-- =====================================================================================
-- fix23_orphan_notification_recipients.sql   (N-09)
--
-- MUC DICH
--   Don cac dong NotificationRecipients tro toi mot thong bao KHONG CON TON TAI.
--
--   fix18_orphan_notifications.sql chi don bang AdminNotifications. Nguon sinh ra ban ghi
--   mo coi o tang duoi da duoc sua trong ma nguon (JdbcAdminNotificationDAO.deleteByTarget
--   nay xoa ca so nhan trong cung transaction), nhung cac dong tao ra TRUOC ban sua thi van
--   con.
--
-- VI SAO PHAI DON
--   NotificationId khong co FOREIGN KEY — cot nay tro toi AdminNotifications hay
--   UserNotifications tuy SourceType, nen khong khai FK duoc va cung khong co cascade
--   (xem ghi chu trong fix21_user_notifications.sql). Ca hai bang thong bao deu dung
--   IDENTITY, nen mot thong bao MOI co the trung Id voi thong bao da xoa; so nhan cu con
--   lai lam thong bao moi bi coi la "da doc" ngay tu luc sinh ra — nguoi nhan khong bao gio
--   thay dau chua doc.
--
-- PHAM VI — CHI xoa dong that su mo coi:
--     SourceType = 'admin' va NotificationId khong con trong AdminNotifications
--     SourceType = 'user'  va NotificationId khong con trong UserNotifications
--   Dong tro toi thong bao hop le KHONG bi dung toi. Moc da doc cua chung giu nguyen.
--
-- KHONG DUNG TOI: Orders, OrderSeats, Tickets, Invoices, AuditLogs, PromotionUsage.
--
-- IDEMPOTENT: chay lai lan hai se khong con dong nao de xoa.
-- CHAY:  sqlcmd -S localhost -U sa -P <pw> -C -d CineBookDB -f 65001 -I -b -i database\fix23_orphan_notification_recipients.sql
-- =====================================================================================

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF OBJECT_ID('dbo.NotificationRecipients', 'U') IS NULL
BEGIN
    PRINT 'fix23: chua co bang NotificationRecipients (chay fix21 truoc), bo qua.';
END
ELSE
BEGIN
    -- Bao cao truoc khi xoa, de doi chieu.
    PRINT '--- So nhan mo coi truoc khi don ---';
    SELECT nr.SourceType, COUNT(*) AS SoDong
    FROM dbo.NotificationRecipients nr
    WHERE (nr.SourceType = 'admin'
            AND NOT EXISTS (SELECT 1 FROM dbo.AdminNotifications n WHERE n.Id = nr.NotificationId))
       OR (nr.SourceType = 'user'
            AND NOT EXISTS (SELECT 1 FROM dbo.UserNotifications n WHERE n.Id = nr.NotificationId))
    GROUP BY nr.SourceType;

    BEGIN TRANSACTION fix23Orphans;

    DELETE nr
    FROM dbo.NotificationRecipients nr
    WHERE nr.SourceType = 'admin'
      AND NOT EXISTS (SELECT 1 FROM dbo.AdminNotifications n WHERE n.Id = nr.NotificationId);
    PRINT 'Da xoa ' + CAST(@@ROWCOUNT AS VARCHAR) + ' so nhan tro toi canh bao quan tri khong con ton tai.';

    DELETE nr
    FROM dbo.NotificationRecipients nr
    WHERE nr.SourceType = 'user'
      AND NOT EXISTS (SELECT 1 FROM dbo.UserNotifications n WHERE n.Id = nr.NotificationId);
    PRINT 'Da xoa ' + CAST(@@ROWCOUNT AS VARCHAR) + ' so nhan tro toi thong bao nguoi dung khong con ton tai.';

    COMMIT TRANSACTION fix23Orphans;
    PRINT '=== fix23: hoan tat ===';
END
GO

-- =====================================================================================
-- ROLLBACK
--   Cac dong bi xoa la so nhan cua thong bao da khong con ton tai — chung khong hien thi
--   duoc o bat ky man hinh nao va chi gay hai (lam thong bao moi trung Id bi coi la da doc).
--   Neu van can khoi phuc, dung ban backup truoc migration:
--   scripts\backup-cinebook.bat da tao ban .bak co CHECKSUM va da RESTORE VERIFYONLY.
-- =====================================================================================

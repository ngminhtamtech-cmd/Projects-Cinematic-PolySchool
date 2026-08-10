-- =====================================================================================
-- fix18_orphan_notifications.sql   (RM-01)
--
-- MUC DICH
--   Don cac thong bao quan tri dang tro toi doi tuong KHONG CON TON TAI.
--
--   Nguyen nhan sinh ra chung da duoc sua trong ma nguon: xoa phong / xoa suat chieu bay
--   gio don thong bao lien quan trong CUNG transaction (AdminNotificationDAO.deleteByTarget).
--   Nhung cac ban ghi mo coi tao ra TRUOC ban sua thi van con, va do la thu nguoi dung nhin
--   thay: card "Xoa vinh vien phong ..." khong bao gio bien mat, bam vao chi bao loi.
--
-- PHAM VI — CHI xoa dong that su mo coi:
--     TargetType = 'Room'              va Room do khong con trong bang Rooms
--     TargetType = 'Showtime'          va Showtime do khong con
--     TargetType = 'Showtime_SoldOut'  va Showtime do khong con
--     TargetType = 'Film'              va Film do khong con
--   Thong bao con tro toi doi tuong hop le KHONG bi dung toi.
--
-- KHONG DUNG TOI: AuditLogs, Orders, OrderSeats, Invoices, PromotionUsage — toan bo lich su
-- nghiep vu giu nguyen. Chi bang AdminNotifications bi anh huong, va chi dung cac dong hong.
--
-- IDEMPOTENT: chay lai lan hai se khong con dong nao de xoa.
-- CHAY:  sqlcmd -S localhost -U sa -P <pw> -C -d CineBookDB -f 65001 -I -i database\fix18_orphan_notifications.sql
-- =====================================================================================

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

-- Bao cao truoc khi xoa, de doi chieu.
PRINT '--- Thong bao mo coi truoc khi don ---';
SELECT n.Id, n.TargetType, n.TargetId, LEFT(n.Title, 60) AS Title
FROM AdminNotifications n
WHERE (n.TargetType = 'Room'
        AND TRY_CAST(n.TargetId AS INT) IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM Rooms r WHERE r.Id = TRY_CAST(n.TargetId AS INT)))
   OR (n.TargetType IN ('Showtime', 'Showtime_SoldOut')
        AND TRY_CAST(n.TargetId AS INT) IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM Showtimes s WHERE s.Id = TRY_CAST(n.TargetId AS INT)))
   OR (n.TargetType = 'Film'
        AND TRY_CAST(n.TargetId AS INT) IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM Films f WHERE f.Id = TRY_CAST(n.TargetId AS INT)));
GO

BEGIN TRANSACTION;

DELETE n
FROM AdminNotifications n
WHERE n.TargetType = 'Room'
  AND TRY_CAST(n.TargetId AS INT) IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Rooms r WHERE r.Id = TRY_CAST(n.TargetId AS INT));
PRINT 'Da xoa ' + CAST(@@ROWCOUNT AS VARCHAR) + ' thong bao tro toi phong khong con ton tai.';

DELETE n
FROM AdminNotifications n
WHERE n.TargetType IN ('Showtime', 'Showtime_SoldOut')
  AND TRY_CAST(n.TargetId AS INT) IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Showtimes s WHERE s.Id = TRY_CAST(n.TargetId AS INT));
PRINT 'Da xoa ' + CAST(@@ROWCOUNT AS VARCHAR) + ' thong bao tro toi suat chieu khong con ton tai.';

DELETE n
FROM AdminNotifications n
WHERE n.TargetType = 'Film'
  AND TRY_CAST(n.TargetId AS INT) IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Films f WHERE f.Id = TRY_CAST(n.TargetId AS INT));
PRINT 'Da xoa ' + CAST(@@ROWCOUNT AS VARCHAR) + ' thong bao tro toi phim khong con ton tai.';

COMMIT TRANSACTION;
GO

PRINT '=== fix18: hoan tat ===';
GO

-- =====================================================================================
-- ROLLBACK
--   Cac dong bi xoa la thong bao mo coi — khong con y nghia nghiep vu va khong the thao tac
--   duoc nua (doi tuong dich da mat). Neu can khoi phuc, dung ban backup truoc migration:
--   scripts\backup-cinebook.bat da tao ban .bak co CHECKSUM va da RESTORE VERIFYONLY.
-- =====================================================================================

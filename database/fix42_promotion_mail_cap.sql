-- fix42_promotion_mail_cap.sql
--
-- MUC DICH
--   Seed 'mail.promotionMaxRecipients' — tran so nguoi nhan cho MOT lan tao uu dai.
--
-- VI SAO CAN TRAN
--   AdminService.savePromotion gio gui thu cho toan bo member thuoc dung doi tuong. Do la thao
--   tac gui hang loat DUY NHAT trong ung dung: moi thu khac chi gui cho mot nguoi.
--
--   Hien co 13 member nen mot lan tao uu dai la 13 thu — chua thanh van de. Nhung tai khoan gui
--   la Gmail App Password, gioi han ~500 thu/ngay va bi KHOA khi bi coi la lam dung. Khi danh
--   sach member lon len, mot lan bam Luu se dot sach han ngach cua ca ngay, keo theo ca ba luong
--   ha tang (xac thuc email, quen mat khau, dat lai mat khau) chet cung.
--
--   Tran nay ton tai cho luc do, khong phai cho hom nay. Vuot tran thi ung dung gui toi tran roi
--   ghi WARNING kem so bi bo — khong im lang cat bot.
--
-- KHONG CHAN NHAM: chi ap cho thu uu dai. Thu giao dich (ve, check-in, hoan tien, khang cao)
-- gui cho dung mot nguoi nen khong di qua tran nay.
--
-- IDEMPOTENT: chay lai nhieu lan khong doi ket qua.
-- CHAY:
--   sqlcmd -S localhost -U sa -P <pw> -C -d CineBookDB -f 65001 -I -i database\fix42_promotion_mail_cap.sql
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'CineBookDB' AND DB_NAME() NOT LIKE N'CineBookIT[_]%'
    THROW 51940, 'fix42 only accepts CineBookDB or an ephemeral CineBookIT_* database.', 1;
IF OBJECT_ID(N'dbo.SystemSettings', N'U') IS NULL
    THROW 51941, 'Run the base schema and prior migrations before fix42.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF NOT EXISTS (SELECT 1 FROM dbo.SystemSettings
                   WHERE SettingKey = N'mail.promotionMaxRecipients')
    BEGIN
        INSERT INTO dbo.SystemSettings (SettingKey, SettingValue)
        VALUES (N'mail.promotionMaxRecipients', N'200');
        PRINT 'fix42: da seed mail.promotionMaxRecipients = 200';
    END
    ELSE
        PRINT 'fix42: mail.promotionMaxRecipients da co, bo qua';

    COMMIT TRANSACTION;
    PRINT 'fix42_promotion_mail_cap.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- ROLLBACK: DELETE FROM dbo.SystemSettings WHERE SettingKey = N'mail.promotionMaxRecipients';
--           (bo ban ghi thi ung dung roi ve mac dinh 200 trong
--            AdminService.DEFAULT_PROMOTION_MAX_RECIPIENTS, khong phai bo tran)

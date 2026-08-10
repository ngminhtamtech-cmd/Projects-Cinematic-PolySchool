-- ============================================================================
--  fix27_max_open_drafts_setting.sql — dua nguong so don nhap dang mo len
--  man hinh cau hinh (C.4, INV-6).
--
--  VAN DE GOC
--    booking.maxOpenDraftsPerShowtime dang chay bang hang so mac dinh trong ma
--    (BookingService.DEFAULT_MAX_OPEN_DRAFTS = 3) vi khong co dong nao trong
--    SystemSettings. Man hinh /system/config liet ke moi dong cua bang do, nen
--    khong co dong seed = nguong khong xuat hien = nguoi van hanh khong biet no
--    ton tai va khong doi duoc. Day dung la lop loi INV-6 ma BUG-01 vua sua cho
--    seat_hold_minutes.
--
--  GIA TRI SEED PHAI BANG MAC DINH TRONG MA
--    Seed mot gia tri khac nghia la lan chay script nay am tham doi hanh vi cua
--    he thong. Muon doi thi doi tren man hinh, co nguoi bam va co dau vet.
--
--  KHOANG HOP LE
--    BookingService.clampMaxOpenDrafts kep ve [1, 20]; ngoai khoang thi ve mac
--    dinh 3 kem canh bao trong log. 0 hay so am = khong ai dat duoc ve nao; so
--    qua lon = tran chong lam dung (BUG-04b) coi nhu khong con.
--
--  Idempotent: chay lai nhieu lan khong doi ket qua (khong ghi de gia tri ma
--  nguoi van hanh da chinh tay).
-- ============================================================================
SET XACT_ABORT ON;
SET NOCOUNT ON;
BEGIN TRY
    BEGIN TRANSACTION;

    IF NOT EXISTS (SELECT 1 FROM dbo.SystemSettings
                   WHERE SettingKey = N'booking.maxOpenDraftsPerShowtime')
    BEGIN
        INSERT INTO dbo.SystemSettings (SettingKey, SettingValue, UpdatedAt)
        VALUES (N'booking.maxOpenDraftsPerShowtime', N'3', GETDATE());
        PRINT 'fix27: da them booking.maxOpenDraftsPerShowtime = 3.';
    END
    ELSE
    BEGIN
        PRINT 'fix27: booking.maxOpenDraftsPerShowtime da ton tai — giu nguyen gia tri hien tai.';
    END;

    COMMIT TRANSACTION;
    PRINT 'fix27_max_open_drafts_setting.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- ROLLBACK:
--   DELETE FROM dbo.SystemSettings WHERE SettingKey = N'booking.maxOpenDraftsPerShowtime';
--   (Ung dung quay ve dung DEFAULT_MAX_OPEN_DRAFTS = 3 trong ma.)

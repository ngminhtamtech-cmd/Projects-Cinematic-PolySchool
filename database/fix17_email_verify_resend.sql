-- =====================================================================================
-- fix17_email_verify_resend.sql   (EM-01)
--
-- MUC DICH
--   Ho tro nut "Xac thuc ngay" — gui lai email xac thuc — mot cach an toan.
--
-- VI SAO CAN MOT COT RIENG
--   Chong spam gui lai bat buoc phai co moc "lan gui gan nhat". Ba lua chon khac deu sai:
--     * dem trong session  -> mat khi dang xuat / mo trinh duyet khac, va khong dung khi chay
--                             nhieu instance;
--     * dua vao Users.UpdatedAt -> cot nay doi vi nhieu ly do khac (doi ten, doi mat khau),
--                             nen se chan nham nguoi dung vua sua ho so;
--     * bien dem trong bo nho -> mat khi restart Tomcat.
--   Vi vay them mot cot chuyen dung, do chinh SQL Server ghi bang GETDATE().
--
-- IDEMPOTENT: chay lai nhieu lan khong doi ket qua.
-- CHAY:  sqlcmd -S localhost -U sa -P <pw> -C -d CineBookDB -f 65001 -I -i database\fix17_email_verify_resend.sql
-- =====================================================================================

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF COL_LENGTH('Users', 'EmailVerifySentAt') IS NULL
BEGIN
    ALTER TABLE Users ADD EmailVerifySentAt DATETIME NULL;
    PRINT 'fix17: da them Users.EmailVerifySentAt';
END
ELSE
    PRINT 'fix17: Users.EmailVerifySentAt da co, bo qua';
GO

-- Khoang cho toi thieu giua hai lan gui lai email xac thuc, tinh bang giay.
IF NOT EXISTS (SELECT 1 FROM SystemSettings WHERE SettingKey = 'security.verifyResendCooldownSeconds')
BEGIN
    INSERT INTO SystemSettings (SettingKey, SettingValue)
    VALUES ('security.verifyResendCooldownSeconds', '120');
    PRINT 'fix17: da seed security.verifyResendCooldownSeconds = 120';
END
ELSE
    PRINT 'fix17: security.verifyResendCooldownSeconds da co, bo qua';
GO

PRINT '=== fix17: hoan tat ===';
GO

-- =====================================================================================
-- ROLLBACK
--   IF COL_LENGTH('Users','EmailVerifySentAt') IS NOT NULL
--       ALTER TABLE Users DROP COLUMN EmailVerifySentAt;
--   DELETE FROM SystemSettings WHERE SettingKey='security.verifyResendCooldownSeconds';
-- =====================================================================================

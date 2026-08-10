-- fix41_reset_request_cooldown.sql
--
-- MUC DICH
--   Seed 'security.resetRequestCooldownSeconds' — khoang cho toi thieu giua hai lan xin phieu
--   dat lai mat khau cua cung mot tai khoan.
--
-- VAN DE GOC
--   PasswordResetService.requestReset() khong co bat ky gioi han tan suat nao, trong khi route
--   POST /forgot-password khong can dang nhap. Moi lan POST la mot lan sinh phieu va gui thu.
--
--   Doi chung: luong xac thuc email DA co cooldown tu fix17
--   ('security.verifyResendCooldownSeconds'), luong quen mat khau thi khong. Su khong doi xung
--   nay khong gay hau qua khi mail.mode con la 'logfile' — thu chi duoc ghi vao
--   ${catalina.base}/logs/cinebook-mail.log nen khong ai bi lam phien. Bat SMTP that thi cung
--   doan ma do tro thanh:
--     * may doi bom hop thu bat ky ai co tai khoan trong he thong;
--     * may dot han ngach gui (Gmail App Password ~500 thu/ngay);
--     * duong nhanh nhat de nha cung cap khoa tai khoan gui — mat luon ca ba luong email.
--
-- VI SAO KHONG CAN COT MOI
--   PasswordResetTokens da co UserId va CreatedAt, va da co index
--   IX_PasswordResetTokens_UserId (UserId, UsedAt). Dieu kien cooldown chi la mot menh de
--   NOT EXISTS tren CreatedAt, nam trong chinh cau INSERT (xem
--   JdbcPasswordResetTokenDAO.createIfOutsideCooldown) nen khong co khe hoi dua.
--
--   Moc so sanh la CreatedAt chu khong phai UsedAt: mot phieu da dung van la mot lan gui thu,
--   nen van phai chiu cooldown.
--
-- IDEMPOTENT: chay lai nhieu lan khong doi ket qua.
-- CHAY:
--   sqlcmd -S localhost -U sa -P <pw> -C -d CineBookDB -f 65001 -I -i database\fix41_reset_request_cooldown.sql
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'CineBookDB' AND DB_NAME() NOT LIKE N'CineBookIT[_]%'
    THROW 51930, 'fix41 only accepts CineBookDB or an ephemeral CineBookIT_* database.', 1;
IF OBJECT_ID(N'dbo.SystemSettings', N'U') IS NULL
    THROW 51931, 'Run the base schema and prior migrations before fix41.', 1;
IF OBJECT_ID(N'dbo.PasswordResetTokens', N'U') IS NULL
    THROW 51932, 'fix04_security.sql must run before fix41.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF NOT EXISTS (SELECT 1 FROM dbo.SystemSettings
                   WHERE SettingKey = N'security.resetRequestCooldownSeconds')
    BEGIN
        INSERT INTO dbo.SystemSettings (SettingKey, SettingValue)
        VALUES (N'security.resetRequestCooldownSeconds', N'120');
        PRINT 'fix41: da seed security.resetRequestCooldownSeconds = 120';
    END
    ELSE
        PRINT 'fix41: security.resetRequestCooldownSeconds da co, bo qua';

    COMMIT TRANSACTION;
    PRINT 'fix41_reset_request_cooldown.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- ROLLBACK: DELETE FROM dbo.SystemSettings WHERE SettingKey = N'security.resetRequestCooldownSeconds';
--           (bo ban ghi thi ung dung roi ve mac dinh 120 giay trong
--            PasswordResetService.DEFAULT_REQUEST_COOLDOWN_SECONDS, khong phai bo gioi han)

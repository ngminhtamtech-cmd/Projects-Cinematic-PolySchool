-- =============================================================================
-- fix04_security.sql  (idempotent)
-- Phase P10 — Xác thực & vòng đời tài khoản (D5, D6, D10, D11, D14)
--
-- Chạy bằng:  sqlcmd -S localhost -U sa -P <pw> -C -d CineBookDB -f 65001 -i database\fix04_security.sql
-- Thiếu -f 65001 thì tiếng Việt trong phần seed sẽ hỏng thành mojibake.
--
-- Mọi mốc thời gian đều do SQL Server sinh (SYSDATETIME()) — quy tắc chốt ở P03:
-- giờ DB là nguồn thời gian duy nhất, tuyệt đối không nhận DATETIME từ tầng Java.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. LoginAttempts — nhật ký mọi lần thử đăng nhập (D6: chống dò mật khẩu)
--    Ghi cả lần thành công lẫn thất bại: bộ đếm chỉ tính các lần thất bại
--    SAU lần thành công gần nhất, nên đăng nhập đúng một lần là tự reset.
-- -----------------------------------------------------------------------------
IF OBJECT_ID('LoginAttempts', 'U') IS NULL
BEGIN
    CREATE TABLE LoginAttempts (
        Id          INT IDENTITY(1,1) PRIMARY KEY,
        Email       NVARCHAR(255) NOT NULL,
        IpAddress   NVARCHAR(64)  NULL,
        AttemptAt   DATETIME2     NOT NULL CONSTRAINT DF_LoginAttempts_AttemptAt DEFAULT SYSDATETIME(),
        Success     BIT           NOT NULL CONSTRAINT DF_LoginAttempts_Success   DEFAULT 0
    );
END
GO

-- Truy vấn nóng: "trong N phút qua, email này sai bao nhiêu lần" → lọc Email rồi sắp theo thời gian.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_LoginAttempts_Email_AttemptAt' AND object_id = OBJECT_ID('LoginAttempts'))
BEGIN
    CREATE INDEX IX_LoginAttempts_Email_AttemptAt ON LoginAttempts (Email, AttemptAt DESC) INCLUDE (Success);
END
GO

-- Cùng câu hỏi nhưng theo IP, để một kẻ dò trên nhiều email khác nhau vẫn bị chặn.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_LoginAttempts_Ip_AttemptAt' AND object_id = OBJECT_ID('LoginAttempts'))
BEGIN
    CREATE INDEX IX_LoginAttempts_Ip_AttemptAt ON LoginAttempts (IpAddress, AttemptAt DESC) INCLUDE (Success);
END
GO

-- -----------------------------------------------------------------------------
-- 2. PasswordResetTokens — quên mật khẩu thật (D11)
--    CHỈ lưu SHA-256 của token, không bao giờ lưu token trần: đọc được DB
--    không đồng nghĩa với chiếm được tài khoản.
-- -----------------------------------------------------------------------------
IF OBJECT_ID('PasswordResetTokens', 'U') IS NULL
BEGIN
    CREATE TABLE PasswordResetTokens (
        Id         INT IDENTITY(1,1) PRIMARY KEY,
        TokenHash  CHAR(64)   NOT NULL,          -- SHA-256 hex thường (64 ký tự)
        UserId     INT        NOT NULL,
        ExpiresAt  DATETIME2  NOT NULL,
        UsedAt     DATETIME2  NULL,              -- khác NULL = đã tiêu, không dùng lại được
        CreatedAt  DATETIME2  NOT NULL CONSTRAINT DF_PasswordResetTokens_CreatedAt DEFAULT SYSDATETIME(),
        RequestIp  NVARCHAR(64) NULL,
        CONSTRAINT UQ_PasswordResetTokens_TokenHash UNIQUE (TokenHash),
        CONSTRAINT FK_PasswordResetTokens_Users FOREIGN KEY (UserId) REFERENCES Users(Id)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_PasswordResetTokens_UserId' AND object_id = OBJECT_ID('PasswordResetTokens'))
BEGIN
    CREATE INDEX IX_PasswordResetTokens_UserId ON PasswordResetTokens (UserId, UsedAt);
END
GO

-- -----------------------------------------------------------------------------
-- 3. Users — chỗ chứa trạng thái xác thực email (G2 ở P18 sẽ dùng tới).
--    P10 chỉ tạo cột; chưa có luồng gửi mail nên chưa ai ghi vào đây.
-- -----------------------------------------------------------------------------
IF COL_LENGTH('Users', 'EmailVerifiedAt') IS NULL
BEGIN
    ALTER TABLE Users ADD EmailVerifiedAt DATETIME2 NULL;
END
GO

IF COL_LENGTH('Users', 'EmailVerifyTokenHash') IS NULL
BEGIN
    ALTER TABLE Users ADD EmailVerifyTokenHash CHAR(64) NULL;
END
GO

-- -----------------------------------------------------------------------------
-- 4. Seed SystemSettings — không hardcode ngưỡng trong Java.
--    MERGE để chạy lại nhiều lần không ghi đè giá trị quản trị viên đã chỉnh.
--
--    CineBookDB có cột Description, CineBookDB_Test thì không. Phần bắt buộc
--    (key + value) chạy tĩnh cho cả hai; phần mô tả chỉ chạy khi cột có thật,
--    và phải qua EXEC vì trình biên dịch T-SQL kiểm tên cột trước khi chạy IF.
-- -----------------------------------------------------------------------------
MERGE SystemSettings AS target
USING (VALUES
    (N'security.maxLoginAttempts',  N'5'),
    (N'security.lockMinutes',       N'15'),
    (N'security.passwordMinLength', N'10'),
    (N'security.resetTokenMinutes', N'30')
) AS source (SettingKey, SettingValue)
ON target.SettingKey = source.SettingKey
WHEN NOT MATCHED THEN
    INSERT (SettingKey, SettingValue, UpdatedAt)
    VALUES (source.SettingKey, source.SettingValue, GETDATE());
GO

IF COL_LENGTH('SystemSettings', 'Description') IS NOT NULL
BEGIN
    EXEC(N'
        UPDATE SystemSettings SET Description = N''Số lần đăng nhập sai liên tiếp trước khi bị chặn tạm thời''
            WHERE SettingKey = N''security.maxLoginAttempts''  AND Description IS NULL;
        UPDATE SystemSettings SET Description = N''Số phút chặn đăng nhập sau khi vượt ngưỡng, cũng là độ dài cửa sổ đếm''
            WHERE SettingKey = N''security.lockMinutes''       AND Description IS NULL;
        UPDATE SystemSettings SET Description = N''Độ dài mật khẩu tối thiểu khi đăng ký / đổi / đặt lại mật khẩu''
            WHERE SettingKey = N''security.passwordMinLength'' AND Description IS NULL;
        UPDATE SystemSettings SET Description = N''Hạn dùng của link đặt lại mật khẩu, tính bằng phút''
            WHERE SettingKey = N''security.resetTokenMinutes'' AND Description IS NULL;
    ');
END
GO

PRINT 'fix04_security.sql: hoan tat.';
GO

-- =============================================================================
-- ROLLBACK (chạy tay khi cần quay lui — sẽ mất nhật ký đăng nhập và token reset)
--
--   IF OBJECT_ID('PasswordResetTokens','U') IS NOT NULL DROP TABLE PasswordResetTokens;
--   IF OBJECT_ID('LoginAttempts','U')       IS NOT NULL DROP TABLE LoginAttempts;
--   IF COL_LENGTH('Users','EmailVerifyTokenHash') IS NOT NULL ALTER TABLE Users DROP COLUMN EmailVerifyTokenHash;
--   IF COL_LENGTH('Users','EmailVerifiedAt')      IS NOT NULL ALTER TABLE Users DROP COLUMN EmailVerifiedAt;
--   DELETE FROM SystemSettings WHERE SettingKey IN
--       (N'security.maxLoginAttempts', N'security.lockMinutes',
--        N'security.passwordMinLength', N'security.resetTokenMinutes');
-- =============================================================================

-- ============================================================================
-- fix19_refresh_tokens.sql
-- FLOW-AUTH-REF-001..003: bang luu trang thai xoay vong refresh token.
--
-- Refresh token la mot chuoi ngau nhien 256 bit (opaque), KHONG phai JWT. DB chi
-- luu SHA-256 hash cua no — lo DB khong dong nghia lo credential. Moi token thuoc
-- mot "family": khi phat hien replay (dung lai token da consumed/revoked) thi ca
-- family bi thu hoi chu khong chi rieng token do.
--
-- Ghi chu FK: ReplacedById tro ve chinh bang nay nhung CO Y khong khai FK. Vong
-- tham chieu tu tro lam ke hoach xoa (cleanup token het han, xoa fixture cua test)
-- phai xu ly theo thu tu topo; gia tri o day chi dung de truy vet nen rang buoc
-- tham chieu khong bu duoc chi phi van hanh.
--
-- Script idempotent: chay lai nhieu lan khong loi.
-- ============================================================================

IF OBJECT_ID('dbo.RefreshTokens', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.RefreshTokens (
        Id                INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        UserId            INT NOT NULL,
        FamilyId          UNIQUEIDENTIFIER NOT NULL,
        TokenHash         CHAR(64) NOT NULL,
        IssuedAt          DATETIME2(3) NOT NULL CONSTRAINT DF_RefreshTokens_IssuedAt DEFAULT SYSDATETIME(),
        ExpiresAt         DATETIME2(3) NOT NULL,
        ConsumedAt        DATETIME2(3) NULL,
        RevokedAt         DATETIME2(3) NULL,
        ReplacedById      INT NULL,
        RevocationReason  NVARCHAR(100) NULL,
        CreatedByIp       NVARCHAR(64) NULL,
        CONSTRAINT FK_RefreshTokens_Users FOREIGN KEY (UserId) REFERENCES dbo.Users(Id)
    );
END;
GO

-- Cot bo sung khi bang da ton tai tu ban chay truoc.
IF COL_LENGTH('dbo.RefreshTokens', 'RevocationReason') IS NULL
    ALTER TABLE dbo.RefreshTokens ADD RevocationReason NVARCHAR(100) NULL;
GO
IF COL_LENGTH('dbo.RefreshTokens', 'ReplacedById') IS NULL
    ALTER TABLE dbo.RefreshTokens ADD ReplacedById INT NULL;
GO
IF COL_LENGTH('dbo.RefreshTokens', 'CreatedByIp') IS NULL
    ALTER TABLE dbo.RefreshTokens ADD CreatedByIp NVARCHAR(64) NULL;
GO

-- FLOW-AUTH-REF-003: mot hash chi ton tai dung mot lan trong he thong.
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'UX_RefreshTokens_TokenHash'
                 AND object_id = OBJECT_ID('dbo.RefreshTokens'))
    CREATE UNIQUE INDEX UX_RefreshTokens_TokenHash ON dbo.RefreshTokens(TokenHash);
GO

-- Thu hoi ca family va liet ke phien dang mo cua mot user deu di qua hai index nay.
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IX_RefreshTokens_FamilyId'
                 AND object_id = OBJECT_ID('dbo.RefreshTokens'))
    CREATE INDEX IX_RefreshTokens_FamilyId ON dbo.RefreshTokens(FamilyId);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IX_RefreshTokens_UserId_ExpiresAt'
                 AND object_id = OBJECT_ID('dbo.RefreshTokens'))
    CREATE INDEX IX_RefreshTokens_UserId_ExpiresAt ON dbo.RefreshTokens(UserId, ExpiresAt);
GO

-- ============================================================================
-- ROLLBACK
--   DROP INDEX IX_RefreshTokens_UserId_ExpiresAt ON dbo.RefreshTokens;
--   DROP INDEX IX_RefreshTokens_FamilyId ON dbo.RefreshTokens;
--   DROP INDEX UX_RefreshTokens_TokenHash ON dbo.RefreshTokens;
--   DROP TABLE dbo.RefreshTokens;
-- Sau rollback, /api/v1/auth/refresh va /logout-all phai duoc go khoi AuthApiServlet:
-- khong co bang nay thi khong the chung minh mot token chi dung duoc mot lan.
-- ============================================================================

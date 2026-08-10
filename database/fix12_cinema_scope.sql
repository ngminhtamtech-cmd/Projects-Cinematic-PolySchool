SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.Users','CinemaId') IS NULL
    ALTER TABLE dbo.Users ADD CinemaId INT NULL;

-- Một số schema gốc đã có FK cùng cặp cột nhưng tên FK_Users_Cinemas.
-- Chỉ tạo khi thực sự chưa có quan hệ Users.CinemaId -> Cinemas.Id.
IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_key_columns fkc
    WHERE fkc.parent_object_id = OBJECT_ID('dbo.Users')
      AND fkc.parent_column_id = COLUMNPROPERTY(OBJECT_ID('dbo.Users'), 'CinemaId', 'ColumnId')
      AND fkc.referenced_object_id = OBJECT_ID('dbo.Cinemas')
      AND fkc.referenced_column_id = COLUMNPROPERTY(OBJECT_ID('dbo.Cinemas'), 'Id', 'ColumnId')
)
    ALTER TABLE dbo.Users ADD CONSTRAINT FK_Users_Cinema
        FOREIGN KEY (CinemaId) REFERENCES dbo.Cinemas(Id);

-- Dọn FK trùng do bản P17 đầu tiên chỉ kiểm tra theo tên constraint.
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_Users_Cinema')
AND EXISTS (
    SELECT 1
    FROM sys.foreign_key_columns fkc
    JOIN sys.foreign_keys fk ON fk.object_id=fkc.constraint_object_id
    WHERE fkc.parent_object_id = OBJECT_ID('dbo.Users')
      AND fkc.parent_column_id = COLUMNPROPERTY(OBJECT_ID('dbo.Users'), 'CinemaId', 'ColumnId')
      AND fkc.referenced_object_id = OBJECT_ID('dbo.Cinemas')
      AND fk.name <> 'FK_Users_Cinema'
)
    ALTER TABLE dbo.Users DROP CONSTRAINT FK_Users_Cinema;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_Users_CinemaId_Role' AND object_id=OBJECT_ID('dbo.Users'))
    CREATE INDEX IX_Users_CinemaId_Role ON dbo.Users(CinemaId, Role) INCLUDE (Deleted, IsLocked);

-- Tài khoản manager cũ có trước P17 chưa có phạm vi. Gán tạm cụm rạp active đầu tiên để
-- migration fail-closed; system admin có thể đổi lại ngay trên màn hình managers.
UPDATE dbo.Users
SET CinemaId=(SELECT TOP(1) Id FROM dbo.Cinemas WHERE Status='active' ORDER BY Id),
    UpdatedAt=GETDATE()
WHERE Role='manager' AND CinemaId IS NULL;

IF EXISTS (SELECT 1 FROM dbo.Users WHERE Role='manager' AND CinemaId IS NULL)
    THROW 51012, 'Khong co cum rap active de gan cho manager cu.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='CK_Users_ManagerCinema')
    ALTER TABLE dbo.Users ADD CONSTRAINT CK_Users_ManagerCinema
        CHECK (Role <> 'manager' OR CinemaId IS NOT NULL);

COMMIT TRANSACTION;

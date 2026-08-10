-- ============================================================================
-- Them vai tro 'staff' (Nhan vien quay ve) vao rang buoc CHECK cua Users.Role.
--
-- Chay bang sqlcmd voi UTF-8 (neu khong tieng Viet se hong thanh mojibake):
--   sqlcmd -S localhost -U sa -P <pw> -C -d CineBookDB -f 65001 ^
--          -i database/alter_users_add_staff_role.sql
--
-- Script idempotent: chay lai nhieu lan khong loi.
-- ============================================================================
USE CineBookDB;
GO

SET NOCOUNT ON;
GO

-- ---------------------------------------------------------------------------
-- Buoc 1: go rang buoc CHECK hien co tren cot Role.
--
-- QUAN TRONG: schema.sql goc khai bao CHECK kieu inline khong dat ten, nen
-- SQL Server tu sinh ten dang CK__Users__Role__4CA06362. Hau to hash nay
-- KHAC NHAU tren tung may da chay schema.sql, vi vay tuyet doi khong duoc
-- hardcode ten - phai tra dong tu sys.check_constraints.
-- ---------------------------------------------------------------------------
DECLARE @constraintName NVARCHAR(200);
DECLARE @sql NVARCHAR(MAX);

SELECT TOP 1 @constraintName = cc.name
FROM sys.check_constraints cc
LEFT JOIN sys.columns c
       ON c.object_id = cc.parent_object_id
      AND c.column_id = cc.parent_column_id
WHERE cc.parent_object_id = OBJECT_ID(N'dbo.Users')
  -- Rang buoc cap cot: doi chieu qua parent_column_id (chinh xac nhat).
  -- Rang buoc cap bang: parent_column_id = 0 nen phai do trong definition.
  -- ESCAPE la bat buoc: trong LIKE cua T-SQL, [Role] la character class
  -- (khop 1 trong cac ky tu R/o/l/e) chu khong phai chuoi "[Role]".
  AND (c.name = N'Role' OR cc.definition LIKE N'%\[Role\]%' ESCAPE N'\');

IF @constraintName IS NOT NULL
BEGIN
    PRINT N'Dang go rang buoc cu: ' + @constraintName;
    SET @sql = N'ALTER TABLE dbo.Users DROP CONSTRAINT ' + QUOTENAME(@constraintName);
    EXEC sp_executesql @sql;
END
ELSE
BEGIN
    PRINT N'Khong tim thay rang buoc CHECK nao tren Users.Role (co the da go tu truoc).';
END
GO

-- ---------------------------------------------------------------------------
-- Buoc 2: tao lai voi TEN CO DINH de cac migration sau xac dinh duoc.
-- ---------------------------------------------------------------------------
IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = N'CK_Users_Role'
      AND parent_object_id = OBJECT_ID(N'dbo.Users')
)
BEGIN
    ALTER TABLE dbo.Users
        ADD CONSTRAINT CK_Users_Role
        CHECK (Role IN ('member', 'staff', 'manager', 'admin'));
    PRINT N'Da tao rang buoc CK_Users_Role gom 4 vai tro.';
END
ELSE
BEGIN
    PRINT N'Rang buoc CK_Users_Role da ton tai, bo qua.';
END
GO

-- ---------------------------------------------------------------------------
-- Buoc 3: Them cot CinemaId cho Users va tao FK/Check constraints & Index
-- ---------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.Users') AND name = N'CinemaId')
BEGIN
    ALTER TABLE dbo.Users ADD CinemaId INT NULL;
    PRINT N'Da them cot Users.CinemaId.';
END
GO

-- Gan mac dinh CinemaId cho cac tai khoan staff cu chua co CinemaId
UPDATE dbo.Users
SET CinemaId = (SELECT MIN(Id) FROM dbo.Cinemas)
WHERE Role = 'staff' AND CinemaId IS NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Users_Cinemas')
BEGIN
    ALTER TABLE dbo.Users ADD CONSTRAINT FK_Users_Cinemas FOREIGN KEY (CinemaId) REFERENCES dbo.Cinemas(Id);
    PRINT N'Da tao khoa ngoai FK_Users_Cinemas.';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Users_StaffCinema')
BEGIN
    ALTER TABLE dbo.Users ADD CONSTRAINT CK_Users_StaffCinema CHECK (Role <> 'staff' OR CinemaId IS NOT NULL);
    PRINT N'Da tao rang buoc CK_Users_StaffCinema.';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Users_CinemaId')
BEGIN
    CREATE INDEX IX_Users_CinemaId ON dbo.Users(CinemaId) WHERE CinemaId IS NOT NULL;
    PRINT N'Da tao index IX_Users_CinemaId.';
END
GO

-- ---------------------------------------------------------------------------
-- Buoc 4: xac minh ket qua.
-- ---------------------------------------------------------------------------
SELECT cc.name AS ConstraintName, cc.definition AS Definition
FROM sys.check_constraints cc
WHERE cc.parent_object_id = OBJECT_ID(N'dbo.Users');
GO

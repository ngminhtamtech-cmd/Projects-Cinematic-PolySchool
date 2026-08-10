-- =====================================================================================
-- fix15_combo_cinema_scope.sql   (CB-01)
--
-- MUC DICH
--   Cho manager quan ly combo cua chinh cum rap minh, thay vi bi tra danh sach rong va
--   bi chan moi thao tac ghi nhu truoc.
--
-- MO HINH DA CHOT
--   ComboFoods.CinemaId NULL:
--     * NULL          = combo dung chung toan he thong. Chi admin duoc tao/sua/xoa.
--                       Moi rap deu ban duoc.
--     * co gia tri    = combo rieng cua cum rap do. Manager cua dung rap do co toan quyen;
--                       admin cung co. Manager rap khac khong thay va khong sua duoc.
--
--   Trang dat ve hien combo theo suat chieu: global + combo cua dung rap chieu suat do.
--
-- VI SAO KHONG MO QUYEN GLOBAL CHO MANAGER
--   Combo global anh huong moi rap; cho manager sua se khien mot nguoi doi gia lam thay doi
--   thuc don ca he thong. Pham vi theo CinemaId dung dung co che scope san co cua du an
--   (Users.CinemaId + ScopeUtil), khong sinh khai niem quyen moi.
--
-- AN TOAN DU LIEU
--   Cot moi mac dinh NULL nen toan bo combo dang co tro thanh combo global — dung hanh vi
--   truoc day. Khong dong OrderComboFoods nao bi dung toi: lich su don hang giu nguyen.
--
-- IDEMPOTENT: chay lai nhieu lan khong doi ket qua.
-- CHAY:  sqlcmd -S localhost -U sa -P <pw> -C -d CineBookDB -f 65001 -I -i database\fix15_combo_cinema_scope.sql
-- =====================================================================================

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF COL_LENGTH('ComboFoods', 'CinemaId') IS NULL
BEGIN
    ALTER TABLE ComboFoods ADD CinemaId INT NULL;
    PRINT 'fix15: da them ComboFoods.CinemaId (NULL = combo dung chung)';
END
ELSE
    PRINT 'fix15: ComboFoods.CinemaId da co, bo qua';
GO

-- Khoa ngoai: combo tro toi rap khong ton tai la du lieu hong.
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_ComboFoods_Cinemas')
   AND COL_LENGTH('ComboFoods', 'CinemaId') IS NOT NULL
BEGIN
    ALTER TABLE ComboFoods WITH CHECK
        ADD CONSTRAINT FK_ComboFoods_Cinemas FOREIGN KEY (CinemaId) REFERENCES Cinemas(Id);
    PRINT 'fix15: da them FK_ComboFoods_Cinemas';
END
ELSE
    PRINT 'fix15: FK_ComboFoods_Cinemas da co hoac cot chua san sang, bo qua';
GO

-- Trang dat ve loc "CinemaId IS NULL OR CinemaId = @cinema" tren moi lan tai sơ do ghe.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_ComboFoods_CinemaId_Status')
   AND COL_LENGTH('ComboFoods', 'CinemaId') IS NOT NULL
BEGIN
    CREATE NONCLUSTERED INDEX IX_ComboFoods_CinemaId_Status
        ON ComboFoods (CinemaId, Status) INCLUDE (Name, Price);
    PRINT 'fix15: da tao IX_ComboFoods_CinemaId_Status';
END
ELSE
    PRINT 'fix15: IX_ComboFoods_CinemaId_Status da co, bo qua';
GO

PRINT '=== fix15: hoan tat ===';
GO

-- =====================================================================================
-- ROLLBACK
--   IF EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_ComboFoods_CinemaId_Status')
--       DROP INDEX IX_ComboFoods_CinemaId_Status ON ComboFoods;
--   IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_ComboFoods_Cinemas')
--       ALTER TABLE ComboFoods DROP CONSTRAINT FK_ComboFoods_Cinemas;
--   IF COL_LENGTH('ComboFoods','CinemaId') IS NOT NULL
--       ALTER TABLE ComboFoods DROP COLUMN CinemaId;
--   Sau rollback moi combo tro lai la combo dung chung; khong mat dong du lieu nao.
-- =====================================================================================

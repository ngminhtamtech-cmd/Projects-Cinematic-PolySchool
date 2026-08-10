-- =====================================================================================
-- fix16_film_enddate.sql   (EX-01)
--
-- MUC DICH
--   Them vong doi "ngay ket thuc chieu" cho phim. Truoc ban nay he thong chi biet
--   ReleaseDate, nen mot phim da het chieu van hien tren trang chu, trang tim kiem va
--   van chon duoc trong luong dat ve.
--
-- QUY UOC TRANG THAI (tinh tu ngay CUA DB, khong luu san trong cot Status)
--     coming    : today <  ReleaseDate
--     showing   : ReleaseDate <= today <= EndDate
--     expiring  : con 0..N ngay toi EndDate (N doc tu SystemSettings), van dat ve duoc
--     expired   : today >  EndDate  -> an khoi moi be mat public
--   Status bien tap ('ended', 'inactive', 'draft') van duoc uu tien chan public truoc.
--
--   Khong co job chay dem doi Status. Trang thai duoc suy ra moi lan doc nen khong bao gio
--   lech ngay — day la ly do cot Status khong duoc dung lam nguon su that cho viec an/hien.
--
-- DU LIEU CU
--   EndDate NULL = chua gioi han thoi gian chieu (hanh vi y het truoc day). Phim cu vi vay
--   khong bi an di sau khi migration chay. Chi phim moi tao moi bat buoc nhap EndDate,
--   viec do enforce o tang service chu khong phai o DB, de khong lam vo du lieu san co.
--
-- IDEMPOTENT: chay lai nhieu lan khong doi ket qua.
-- CHAY:  sqlcmd -S localhost -U sa -P <pw> -C -d CineBookDB -f 65001 -I -i database\fix16_film_enddate.sql
-- =====================================================================================

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF COL_LENGTH('Films', 'EndDate') IS NULL
BEGIN
    ALTER TABLE Films ADD EndDate DATE NULL;
    PRINT 'fix16: da them Films.EndDate';
END
ELSE
    PRINT 'fix16: Films.EndDate da co, bo qua';
GO

-- Hang rao thu hai o tang DB: service da validate nhung constraint moi la thu chan duoc
-- ca duong ghi truc tiep bang sqlcmd hoac script seed.
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Films_EndDate_After_ReleaseDate')
   AND COL_LENGTH('Films', 'EndDate') IS NOT NULL
BEGIN
    -- WITH NOCHECK: du lieu cu co the da vi pham; khong tu sua du lieu that o day.
    -- Moi INSERT/UPDATE tu bay gio deu bi kiem tra.
    ALTER TABLE Films WITH NOCHECK
        ADD CONSTRAINT CK_Films_EndDate_After_ReleaseDate
        CHECK (EndDate IS NULL OR ReleaseDate IS NULL OR EndDate >= ReleaseDate);
    PRINT 'fix16: da them CK_Films_EndDate_After_ReleaseDate';
END
ELSE
    PRINT 'fix16: CK_Films_EndDate_After_ReleaseDate da co, bo qua';
GO

-- Trang public loc theo (ReleaseDate, EndDate) tren moi request.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_Films_ReleaseDate_EndDate')
   AND COL_LENGTH('Films', 'EndDate') IS NOT NULL
BEGIN
    CREATE NONCLUSTERED INDEX IX_Films_ReleaseDate_EndDate
        ON Films (ReleaseDate, EndDate) INCLUDE (Title, Status);
    PRINT 'fix16: da tao IX_Films_ReleaseDate_EndDate';
END
ELSE
    PRINT 'fix16: IX_Films_ReleaseDate_EndDate da co, bo qua';
GO

-- Cua so canh bao "sap het chieu". De o SystemSettings de doi duoc ma khong phai build lai.
IF NOT EXISTS (SELECT 1 FROM SystemSettings WHERE SettingKey = 'film.expiringSoonDays')
BEGIN
    INSERT INTO SystemSettings (SettingKey, SettingValue)
    VALUES ('film.expiringSoonDays', '3');
    PRINT 'fix16: da seed film.expiringSoonDays = 3';
END
ELSE
    PRINT 'fix16: film.expiringSoonDays da co, bo qua';
GO

PRINT '=== fix16: hoan tat ===';
GO

-- =====================================================================================
-- ROLLBACK
--   IF EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_Films_ReleaseDate_EndDate')
--       DROP INDEX IX_Films_ReleaseDate_EndDate ON Films;
--   IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='CK_Films_EndDate_After_ReleaseDate')
--       ALTER TABLE Films DROP CONSTRAINT CK_Films_EndDate_After_ReleaseDate;
--   IF COL_LENGTH('Films','EndDate') IS NOT NULL ALTER TABLE Films DROP COLUMN EndDate;
--   DELETE FROM SystemSettings WHERE SettingKey='film.expiringSoonDays';
--   Sau rollback moi phim tro lai trang thai "khong gioi han ngay ket thuc".
-- =====================================================================================

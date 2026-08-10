-- =====================================================================================
-- fix14_schema_drift_alignment.sql
--
-- MUC DICH
--   Dua schema cua CineBookDB (va bat ky DB nao dung ban cu) ve dung hop dong ma nguon
--   dang truy van. Truoc ban va nay, DB production lech voi `schema.sql` o hai chieu:
--
--   (A) THIEU cot ma code CO doc  -> cau query nem "Invalid column name" -> trang 500:
--         ComboFoods.Image            (lam /admin/combos loi 500 — DB-01)
--         Showtimes.Format/Version/Language (JdbcShowtimeDAO.mapShowtime doc 3 cot nay;
--                                     chua no vi production dang co 0 suat chieu, nhung
--                                     suat dau tien duoc tao se lam vo trang lich chieu)
--         CommentReports.Status
--
--   (B) THUA cot NOT NULL khong co DEFAULT ma code KHONG BAO GIO ghi -> moi INSERT deu
--       that bai "Cannot insert the value NULL":
--         Orders.OrderCode      (con kem UNIQUE constraint)
--         Orders.OriginalPrice
--         Orders.FinalPrice
--         OrderSeats.Price
--         OrderComboFoods.Price
--       Day la di san cua ban schema cu; ban hien tai dung TicketCode / SeatSubtotal +
--       ComboSubtotal / TotalAmount / UnitPrice. Hau qua: KHONG dat duoc bat ky ve nao
--       tren production (Orders = 0 dong tai thoi diem migration nay).
--
-- AN TOAN DU LIEU
--   Script KHONG xoa du lieu nghiep vu. Toan bo PHAN B chay trong MOT transaction duy
--   nhat theo ba giai doan tach bach:
--     1. backfill cot chinh tac tu cot di san khi cot chinh tac con NULL/rong;
--     2. quet DU CA NAM cot va gom moi diem chan vao mot danh sach;
--     3. chi khi danh sach rong moi go cot — con lai thi ROLLBACK, khong cot nao bi go.
--   Nho vay script chay duoc ca tren DB rong lan DB da co du lieu that.
--
-- LOI DA SUA (N-03) — ban truoc 31/07/2026
--   (a) Khong co transaction nao, va moi khoi B1..B4 nam trong mot batch GO rieng.
--       RAISERROR muc 16 KHONG ket thuc script (sqlcmd khong chay -b), nen B1 bao loi
--       xong thi B2a, B2b, B3, B4 VAN tiep tuc go cot cua chung — dung y "DUNG HAN,
--       rollback" ghi o dau file chua bao gio dung. Nay ca PHAN B la mot batch, mot
--       transaction, dung THROW trong TRY/CATCH nen loi that su dung script.
--   (b) B1 go moi index/constraint chua cot, nhung B3/B4 lai loc
--       "is_primary_key = 0 AND is_unique_constraint = 0" — cot nam trong unique
--       constraint thi khong duoc go va DROP COLUMN se loi. Nay ca nam cot dung chung
--       mot thu tuc go, khong con bo loc do.
--   (c) Khong khoi nao xu ly CHECK constraint tham chieu cot bi go. Nay co, ca
--       column-level (parent_column_id) lan table-level (qua sys.sql_expression_dependencies).
--   (d) Backfill gia dinh cot chinh tac da ton tai; DB tao boi fix00 cu chua co
--       UnitPrice nen "UPDATE OrderComboFoods SET UnitPrice = Price" loi Invalid column
--       name. Nay cot chinh tac thieu duoc tinh la diem chan, khong phai loi runtime.
--
-- IDEMPOTENT: chay lai nhieu lan khong doi ket qua.
-- CHAY:  sqlcmd -S localhost -U sa -P <pw> -C -d CineBookDB -f 65001 -I -b -i database\fix14_schema_drift_alignment.sql
--        (-b bat buoc: tra exit code khac 0 khi script dung, de quy trinh goi biet)
-- =====================================================================================

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

PRINT '=== fix14: bat dau can chinh schema ===';
GO

-- -------------------------------------------------------------------------------------
-- PHAN A — them cac cot ma code dang doc nhung DB con thieu
--
-- Phan nay chi THEM, khong go gi, nen moi buoc doc lap va an toan khi chay rieng batch.
-- -------------------------------------------------------------------------------------

IF COL_LENGTH('ComboFoods', 'Image') IS NULL
BEGIN
    ALTER TABLE ComboFoods ADD Image NVARCHAR(255) NULL;
    PRINT 'A1: da them ComboFoods.Image';
END
ELSE
    PRINT 'A1: ComboFoods.Image da co, bo qua';
GO

IF COL_LENGTH('Showtimes', 'Format') IS NULL
BEGIN
    ALTER TABLE Showtimes ADD Format NVARCHAR(50) NULL CONSTRAINT DF_Showtimes_Format DEFAULT '2D';
    PRINT 'A2: da them Showtimes.Format';
END
ELSE
    PRINT 'A2: Showtimes.Format da co, bo qua';
GO

IF COL_LENGTH('Showtimes', 'Version') IS NULL
BEGIN
    ALTER TABLE Showtimes ADD Version NVARCHAR(50) NULL CONSTRAINT DF_Showtimes_Version DEFAULT N'Lồng tiếng';
    PRINT 'A3: da them Showtimes.Version';
END
ELSE
    PRINT 'A3: Showtimes.Version da co, bo qua';
GO

IF COL_LENGTH('Showtimes', 'Language') IS NULL
BEGIN
    ALTER TABLE Showtimes ADD Language NVARCHAR(50) NULL CONSTRAINT DF_Showtimes_Language DEFAULT N'Tiếng Việt';
    PRINT 'A4: da them Showtimes.Language';
END
ELSE
    PRINT 'A4: Showtimes.Language da co, bo qua';
GO

UPDATE Showtimes SET Format   = '2D'            WHERE Format   IS NULL;
UPDATE Showtimes SET Version  = N'Lồng tiếng'   WHERE Version  IS NULL;
UPDATE Showtimes SET Language = N'Tiếng Việt'   WHERE Language IS NULL;
GO

IF OBJECT_ID('CommentReports', 'U') IS NOT NULL AND COL_LENGTH('CommentReports', 'Status') IS NULL
BEGIN
    ALTER TABLE CommentReports ADD Status NVARCHAR(20) NOT NULL CONSTRAINT DF_CommentReports_Status DEFAULT 'pending';
    PRINT 'A5: da them CommentReports.Status';
END
ELSE
    PRINT 'A5: CommentReports.Status da co hoac bang chua ton tai, bo qua';
GO

-- -------------------------------------------------------------------------------------
-- PHAN B — go cac cot di san NOT NULL khong DEFAULT dang chan moi INSERT
--
-- MOT BATCH, MOT TRANSACTION. Khong duoc tach GO o giua: tach ra la mat tinh nguyen tu
-- va tai hien dung loi N-03 (khoi sau van chay khi khoi truoc da bao loi).
--
-- LUU Y KY THUAT
--   Moi cau lenh cham vao cot di san phai chay qua sp_executesql. SQL Server bien dich
--   ca batch truoc khi thuc thi, nen mot cau lenh tham chieu cot da bi go se bao
--   "Invalid column name" ngay ca khi nhanh IF khong bao gio chay. Dung dynamic SQL la
--   cach duy nhat de script vua idempotent vua chay lai duoc sau khi cot da bien mat.
-- -------------------------------------------------------------------------------------

BEGIN TRY
    DECLARE @blockers NVARCHAR(MAX) = N'';
    DECLARE @conflict INT;
    DECLARE @nl NCHAR(2) = CHAR(13) + CHAR(10);

    BEGIN TRANSACTION fix14PhanB;

    -- === B-I. BACKFILL — chi lap vao cho cot chinh tac con trong, khong ghi de ==========

    IF COL_LENGTH('Orders', 'OrderCode') IS NOT NULL AND COL_LENGTH('Orders', 'TicketCode') IS NOT NULL
        EXEC sp_executesql N'
            UPDATE Orders SET TicketCode = OrderCode
            WHERE (TicketCode IS NULL OR TicketCode = '''')
              AND OrderCode IS NOT NULL AND OrderCode <> '''';';

    IF COL_LENGTH('Orders', 'FinalPrice') IS NOT NULL AND COL_LENGTH('Orders', 'TotalAmount') IS NOT NULL
        EXEC sp_executesql N'
            UPDATE Orders SET TotalAmount = FinalPrice
            WHERE (TotalAmount IS NULL OR TotalAmount = 0) AND FinalPrice <> 0;';

    IF COL_LENGTH('OrderSeats', 'Price') IS NOT NULL AND COL_LENGTH('OrderSeats', 'UnitPrice') IS NOT NULL
        EXEC sp_executesql N'
            UPDATE OrderSeats SET UnitPrice = Price
            WHERE (UnitPrice IS NULL OR UnitPrice = 0) AND Price <> 0;';

    IF COL_LENGTH('OrderComboFoods', 'Price') IS NOT NULL AND COL_LENGTH('OrderComboFoods', 'UnitPrice') IS NOT NULL
        EXEC sp_executesql N'
            UPDATE OrderComboFoods SET UnitPrice = Price
            WHERE (UnitPrice IS NULL OR UnitPrice = 0) AND Price <> 0;';

    -- Orders.OriginalPrice khong co backfill: cot chinh tac cua no la TONG cua hai cot
    -- (SeatSubtotal + ComboSubtotal), khong the tach nguoc mot cach xac dinh.

    -- === B-II. QUET DIEM CHAN — quet DU ca nam cot roi moi ket luan ====================
    --
    -- Day la cho ban cu sai: no ket luan va go ngay tai tung khoi. Vong nay chi GOM,
    -- khong go gi, nen mot cot ban khong the de cac cot khac bi go.

    -- B1. Orders.OrderCode (di san cua TicketCode, con kem UNIQUE constraint)
    IF COL_LENGTH('Orders', 'OrderCode') IS NOT NULL
    BEGIN
        IF COL_LENGTH('Orders', 'TicketCode') IS NULL
            SET @blockers = @blockers + N'  - Orders.OrderCode: cot chinh tac Orders.TicketCode chua ton tai (chay fix00/fix01 truoc).' + @nl;
        ELSE
        BEGIN
            EXEC sp_executesql N'
                SELECT @out = COUNT(*) FROM Orders
                WHERE OrderCode IS NOT NULL AND OrderCode <> ''''
                  AND (TicketCode IS NULL OR TicketCode <> OrderCode);',
                N'@out INT OUTPUT', @out = @conflict OUTPUT;
            IF @conflict > 0
                SET @blockers = @blockers + N'  - Orders.OrderCode: ' + CAST(@conflict AS NVARCHAR(20))
                              + N' dong khac TicketCode.' + @nl;
        END
    END

    -- B2a. Orders.FinalPrice (di san cua TotalAmount)
    IF COL_LENGTH('Orders', 'FinalPrice') IS NOT NULL
    BEGIN
        IF COL_LENGTH('Orders', 'TotalAmount') IS NULL
            SET @blockers = @blockers + N'  - Orders.FinalPrice: cot chinh tac Orders.TotalAmount chua ton tai (chay fix00/fix01 truoc).' + @nl;
        ELSE
        BEGIN
            EXEC sp_executesql N'
                SELECT @out = COUNT(*) FROM Orders
                WHERE FinalPrice <> 0 AND (TotalAmount IS NULL OR TotalAmount <> FinalPrice);',
                N'@out INT OUTPUT', @out = @conflict OUTPUT;
            IF @conflict > 0
                SET @blockers = @blockers + N'  - Orders.FinalPrice: ' + CAST(@conflict AS NVARCHAR(20))
                              + N' dong khac TotalAmount.' + @nl;
        END
    END

    -- B2b. Orders.OriginalPrice (di san cua SeatSubtotal + ComboSubtotal)
    IF COL_LENGTH('Orders', 'OriginalPrice') IS NOT NULL
    BEGIN
        IF COL_LENGTH('Orders', 'SeatSubtotal') IS NULL OR COL_LENGTH('Orders', 'ComboSubtotal') IS NULL
            SET @blockers = @blockers + N'  - Orders.OriginalPrice: thieu Orders.SeatSubtotal hoac Orders.ComboSubtotal (chay fix00/fix01 truoc).' + @nl;
        ELSE
        BEGIN
            EXEC sp_executesql N'
                SELECT @out = COUNT(*) FROM Orders
                WHERE OriginalPrice <> 0
                  AND OriginalPrice <> ISNULL(SeatSubtotal, 0) + ISNULL(ComboSubtotal, 0);',
                N'@out INT OUTPUT', @out = @conflict OUTPUT;
            IF @conflict > 0
                SET @blockers = @blockers + N'  - Orders.OriginalPrice: ' + CAST(@conflict AS NVARCHAR(20))
                              + N' dong khac SeatSubtotal+ComboSubtotal.' + @nl;
        END
    END

    -- B3. OrderSeats.Price (di san cua UnitPrice)
    IF COL_LENGTH('OrderSeats', 'Price') IS NOT NULL
    BEGIN
        IF COL_LENGTH('OrderSeats', 'UnitPrice') IS NULL
            SET @blockers = @blockers + N'  - OrderSeats.Price: cot chinh tac OrderSeats.UnitPrice chua ton tai (chay fix00/fix01 truoc).' + @nl;
        ELSE
        BEGIN
            EXEC sp_executesql N'
                SELECT @out = COUNT(*) FROM OrderSeats
                WHERE Price <> 0 AND (UnitPrice IS NULL OR UnitPrice <> Price);',
                N'@out INT OUTPUT', @out = @conflict OUTPUT;
            IF @conflict > 0
                SET @blockers = @blockers + N'  - OrderSeats.Price: ' + CAST(@conflict AS NVARCHAR(20))
                              + N' dong khac UnitPrice.' + @nl;
        END
    END

    -- B4. OrderComboFoods.Price (di san cua UnitPrice)
    IF COL_LENGTH('OrderComboFoods', 'Price') IS NOT NULL
    BEGIN
        IF COL_LENGTH('OrderComboFoods', 'UnitPrice') IS NULL
            SET @blockers = @blockers + N'  - OrderComboFoods.Price: cot chinh tac OrderComboFoods.UnitPrice chua ton tai (chay fix00/fix01 truoc).' + @nl;
        ELSE
        BEGIN
            EXEC sp_executesql N'
                SELECT @out = COUNT(*) FROM OrderComboFoods
                WHERE Price <> 0 AND (UnitPrice IS NULL OR UnitPrice <> Price);',
                N'@out INT OUTPUT', @out = @conflict OUTPUT;
            IF @conflict > 0
                SET @blockers = @blockers + N'  - OrderComboFoods.Price: ' + CAST(@conflict AS NVARCHAR(20))
                              + N' dong khac UnitPrice.' + @nl;
        END
    END

    -- === B-III. CONG CHAN — mot diem chan la KHONG cot nao duoc go =====================

    IF @blockers <> N''
    BEGIN
        PRINT 'fix14 DUNG — khong go bat ky cot nao. Cac diem chan:';
        PRINT @blockers;
        DECLARE @msg NVARCHAR(2048) = N'fix14 DUNG: con du lieu di san khac cot chinh tac, '
                                    + N'go cot se lam mat thong tin. Xem danh sach diem chan o tren, '
                                    + N'doi soat thu cong roi chay lai script.';
        THROW 51414, @msg, 1;
    END

    -- === B-IV. GO COT — chi chay khi ca nam cot deu sach ==============================
    --
    -- Mot thu tuc go dung chung cho ca nam cot. Go theo dung thu tu phu thuoc:
    -- CHECK constraint -> DEFAULT constraint -> index / unique / primary key -> cot.
    -- KHONG loc bo primary key hay unique constraint (loi (b) cua ban cu): cot nam trong
    -- unique constraint ma khong duoc go thi DROP COLUMN chac chan loi.

    DECLARE @legacy TABLE (
        Seq        INT PRIMARY KEY,
        TableName  SYSNAME,
        ColumnName SYSNAME,
        Label      NVARCHAR(20),
        Note       NVARCHAR(100));
    INSERT INTO @legacy (Seq, TableName, ColumnName, Label, Note) VALUES
        (1, 'Orders',          'OrderCode',     N'B1',  N'di san cua TicketCode'),
        (2, 'Orders',          'FinalPrice',    N'B2a', N'di san cua TotalAmount'),
        (3, 'Orders',          'OriginalPrice', N'B2b', N'di san cua SeatSubtotal+ComboSubtotal'),
        (4, 'OrderSeats',      'Price',         N'B3',  N'di san cua UnitPrice'),
        (5, 'OrderComboFoods', 'Price',         N'B4',  N'di san cua UnitPrice');

    DECLARE @seq INT = 0, @cur INT;
    DECLARE @tbl SYSNAME, @col SYSNAME, @label NVARCHAR(20), @note NVARCHAR(100);
    DECLARE @objId INT, @colId INT, @sql NVARCHAR(MAX);

    WHILE 1 = 1
    BEGIN
        SET @cur = NULL;
        SELECT TOP 1 @cur = Seq FROM @legacy WHERE Seq > @seq ORDER BY Seq;
        IF @cur IS NULL BREAK;
        SET @seq = @cur;
        SELECT @tbl = TableName, @col = ColumnName, @label = Label, @note = Note
        FROM @legacy WHERE Seq = @cur;

        IF COL_LENGTH(@tbl, @col) IS NULL
        BEGIN
            PRINT @label + ': ' + @tbl + '.' + @col + ' khong ton tai, bo qua';
            CONTINUE;
        END

        SET @objId = OBJECT_ID(@tbl);
        SET @colId = COLUMNPROPERTY(@objId, @col, 'ColumnId');
        SET @sql = N'';

        -- 1) CHECK constraint tham chieu cot — ca column-level lan table-level.
        SELECT @sql = @sql + N'ALTER TABLE ' + QUOTENAME(@tbl)
                           + N' DROP CONSTRAINT ' + QUOTENAME(cc.name) + N';'
        FROM sys.check_constraints cc
        WHERE cc.parent_object_id = @objId
          AND (cc.parent_column_id = @colId
               OR EXISTS (SELECT 1
                          FROM sys.sql_expression_dependencies d
                          WHERE d.referencing_id = cc.object_id
                            AND d.referenced_id = @objId
                            AND d.referenced_minor_id = @colId));

        -- 2) DEFAULT constraint cua cot.
        SELECT @sql = @sql + N'ALTER TABLE ' + QUOTENAME(@tbl)
                           + N' DROP CONSTRAINT ' + QUOTENAME(dc.name) + N';'
        FROM sys.default_constraints dc
        WHERE dc.parent_object_id = @objId AND dc.parent_column_id = @colId;

        -- 3) Moi index chua cot (ke ca cot INCLUDE), unique constraint va primary key.
        SELECT @sql = @sql + CASE WHEN kc.name IS NOT NULL
                                  THEN N'ALTER TABLE ' + QUOTENAME(@tbl)
                                       + N' DROP CONSTRAINT ' + QUOTENAME(kc.name) + N';'
                                  ELSE N'DROP INDEX ' + QUOTENAME(i.name)
                                       + N' ON ' + QUOTENAME(@tbl) + N';' END
        FROM sys.indexes i
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
        LEFT JOIN sys.key_constraints kc ON kc.parent_object_id = i.object_id
                                        AND kc.unique_index_id = i.index_id
        WHERE i.object_id = @objId AND ic.column_id = @colId AND i.type <> 0;

        SET @sql = @sql + N'ALTER TABLE ' + QUOTENAME(@tbl) + N' DROP COLUMN ' + QUOTENAME(@col) + N';';
        EXEC sp_executesql @sql;
        PRINT @label + ': da go ' + @tbl + '.' + @col + ' (' + @note + ')';
    END

    COMMIT TRANSACTION fix14PhanB;
    PRINT '=== fix14: hoan tat ===';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
    BEGIN
        ROLLBACK TRANSACTION;
        PRINT 'fix14: da ROLLBACK toan bo PHAN B — khong cot nao bi go.';
    END;
    -- Dau ';' o dong tren la bat buoc: THROW yeu cau cau lenh truoc no phai ket thuc
    -- bang dau cham phay, neu khong parser bao "Incorrect syntax near 'THROW'".
    THROW;
END CATCH
GO

-- =====================================================================================
-- ROLLBACK
--   Phan A (them cot) — go lai neu can:
--     ALTER TABLE ComboFoods     DROP COLUMN Image;
--     ALTER TABLE Showtimes      DROP CONSTRAINT DF_Showtimes_Format;   ALTER TABLE Showtimes DROP COLUMN Format;
--     ALTER TABLE Showtimes      DROP CONSTRAINT DF_Showtimes_Version;  ALTER TABLE Showtimes DROP COLUMN Version;
--     ALTER TABLE Showtimes      DROP CONSTRAINT DF_Showtimes_Language; ALTER TABLE Showtimes DROP COLUMN Language;
--     ALTER TABLE CommentReports DROP CONSTRAINT DF_CommentReports_Status; ALTER TABLE CommentReports DROP COLUMN Status;
--
--   Phan B (go cot di san) — script tu rollback khi gap loi, nen khong co trang thai
--   nua voi de don. Neu da COMMIT thanh cong va van muon quay lai thi chi khoi phuc
--   duoc tu ban backup truoc migration (fix00_backup_and_testdb.sql /
--   scripts\backup-cinebook.bat). Cac cot nay la ban sao cua cot chinh tac nen viec
--   dung lai chung se lam moi INSERT that bai tro lai:
--     ALTER TABLE Orders          ADD OrderCode NVARCHAR(50) NULL;
--     ALTER TABLE Orders          ADD OriginalPrice DECIMAL(19,2) NULL, FinalPrice DECIMAL(19,2) NULL;
--     ALTER TABLE OrderSeats      ADD Price DECIMAL(19,2) NULL;
--     ALTER TABLE OrderComboFoods ADD Price DECIMAL(19,2) NULL;
-- =====================================================================================

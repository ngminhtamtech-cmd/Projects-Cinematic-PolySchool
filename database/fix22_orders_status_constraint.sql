-- =====================================================================================
-- fix22_orders_status_constraint.sql   (N-06)
--
-- MUC DICH
--   Nhat nhat check constraint trang thai don ve mot ten va mot danh sach gia tri:
--     CK_Orders_OrderStatus CHECK (OrderStatus IN
--        ('created', 'pending', 'confirmed', 'cancelled', 'completed', 'redeemed'))
--
-- DOI SO HIEU — truoc day file nay ten la fix18_orders_status_constraint.sql, trung so
--   voi fix18_orphan_notifications.sql. CLAUDE.md quy dinh migration chay "theo so thu
--   tu", nen hai file cung so lam thu tu khong xac dinh. File nay duoc doi thanh fix22
--   (fix19/20/21 da dung); fix18_orphan_notifications.sql giu nguyen so vi da chay tren
--   Test va Dev, doi so cua no se lam sai lich su van hanh.
--
-- LOI DA SUA (N-06) — ban truoc 31/07/2026
--   (a) Script DROP moi check constraint roi moi ADD lai, khong transaction. Mot don
--       mang gia tri cu ngoai danh sach (vi du OrderStatus = 'paid') lam buoc ADD loi
--       Msg 547 — va bang Orders MAT HAN rang buoc trang thai, khong ai biet. Nay:
--       kiem tra truoc, va toan bo DROP + ADD nam trong mot transaction.
--   (b) Bo loc "definition LIKE '%OrderStatus%'" go luon mot constraint tong hop co
--       nhac OrderStatus kem quy tac khac. Nay chi go constraint tham chieu DUY NHAT cot
--       OrderStatus; constraint tong hop se lam script dung lai va bao ten cu the.
--   (c) Thieu SET XACT_ABORT ON va thieu ghi chu "-- ROLLBACK" (vi pham CLAUDE.md).
--
-- VI SAO KHONG DUNG "WITH NOCHECK"
--   WITH NOCHECK lam buoc ADD khong bao gio that bai, nhung de lai mot constraint
--   "untrusted": cac dong dang vi pham van nam do, va SQL Server bo qua constraint khi
--   toi uu truy van. Nhu vay la doi mot loi to thanh mot loi im lang. Script chon cach
--   nguoc lai: kiem tra truoc, bao ten gia tri la, va DUNG HAN de nguoi van hanh doi
--   soat — dung tinh than "khong noi long de chay cho qua".
--
-- IDEMPOTENT: chay lai nhieu lan cho ra dung mot trang thai.
-- CHAY:  sqlcmd -S localhost -U sa -P <pw> -C -d CineBookDB -f 65001 -I -b -i database\fix22_orders_status_constraint.sql
--        (-b bat buoc: tra exit code khac 0 khi script dung)
-- =====================================================================================

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

PRINT '=== fix22: nhat nhat rang buoc trang thai don ===';
GO

BEGIN TRY
    DECLARE @allowedCsv NVARCHAR(400) =
        N'''created'', ''pending'', ''confirmed'', ''cancelled'', ''completed'', ''redeemed''';
    DECLARE @stmt NVARCHAR(MAX);
    DECLARE @bad INT = 0;
    DECLARE @badList NVARCHAR(1000);
    DECLARE @msg NVARCHAR(2048);
    DECLARE @statusColId INT = COLUMNPROPERTY(OBJECT_ID('Orders'), 'OrderStatus', 'ColumnId');

    IF @statusColId IS NULL
        THROW 51418, N'fix22 DUNG: khong tim thay cot Orders.OrderStatus.', 1;

    -- === CONG CHAN 1 — con gia tri nam ngoai danh sach cho phep? ======================
    --
    -- Phai kiem TRUOC khi go constraint cu. Ban cu go truoc roi moi biet khong them lai
    -- duoc, va luc do bang da khong con rang buoc nao.

    SET @stmt = N'SELECT @outCount = COUNT(*) FROM Orders
                  WHERE OrderStatus IS NULL OR OrderStatus NOT IN (' + @allowedCsv + N');';
    EXEC sp_executesql @stmt, N'@outCount INT OUTPUT', @outCount = @bad OUTPUT;

    IF @bad > 0
    BEGIN
        SET @stmt = N'SELECT @outList = STUFF((
                          SELECT DISTINCT N'', '' + ISNULL(OrderStatus, N''(NULL)'')
                          FROM Orders
                          WHERE OrderStatus IS NULL OR OrderStatus NOT IN (' + @allowedCsv + N')
                          FOR XML PATH(''''), TYPE).value(''.'', ''NVARCHAR(1000)''), 1, 2, N'''');';
        EXEC sp_executesql @stmt, N'@outList NVARCHAR(1000) OUTPUT', @outList = @badList OUTPUT;

        SET @msg = N'fix22 DUNG: Orders con ' + CAST(@bad AS NVARCHAR(20))
                 + N' dong mang trang thai ngoai danh sach cho phep (' + ISNULL(@badList, N'?')
                 + N'). Rang buoc cu duoc GIU NGUYEN. Hay doi soat/chuan hoa cac dong do roi chay lai.';
        PRINT @msg;
        THROW 51418, @msg, 1;
    END

    -- === CONG CHAN 2 — constraint tong hop nhac OrderStatus kem cot khac =============
    --
    -- Loai constraint nay mang them quy tac khong thuoc pham vi script; go no la am tham
    -- lam mat mot rang buoc nghiep vu khac. Script dung lai va bao ten de nguoi van hanh
    -- quyet dinh.

    -- "Tham chieu OrderStatus" xac dinh bang metadata, khong bang LIKE tren definition:
    -- check constraint muc cot ghi o parent_column_id, muc bang ghi o
    -- sys.sql_expression_dependencies. Dung LIKE '%OrderStatus%' la cach ban cu bat nham
    -- ca constraint chi tinh co nhac ten cot trong mot quy tac khac.
    DECLARE @statusConstraints TABLE (Name SYSNAME, OtherColumns INT);
    INSERT INTO @statusConstraints (Name, OtherColumns)
    SELECT cc.name,
           (SELECT COUNT(*)
            FROM sys.sql_expression_dependencies d
            WHERE d.referencing_id = cc.object_id
              AND d.referenced_id = OBJECT_ID('Orders')
              AND d.referenced_minor_id NOT IN (0, @statusColId))
    FROM sys.check_constraints cc
    WHERE cc.parent_object_id = OBJECT_ID('Orders')
      AND (cc.parent_column_id = @statusColId
           OR EXISTS (SELECT 1
                      FROM sys.sql_expression_dependencies d
                      WHERE d.referencing_id = cc.object_id
                        AND d.referenced_id = OBJECT_ID('Orders')
                        AND d.referenced_minor_id = @statusColId));

    DECLARE @composite NVARCHAR(1000);
    SELECT @composite = STUFF((
        SELECT N', ' + Name FROM @statusConstraints WHERE OtherColumns > 0
        FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(1000)'), 1, 2, N'');

    IF @composite IS NOT NULL
    BEGIN
        SET @msg = N'fix22 DUNG: cac check constraint sau nhac OrderStatus KEM cot khac ('
                 + @composite + N'). Go chung se lam mat mot quy tac nghiep vu ngoai pham vi '
                 + N'script. Hay tach quy tac ra roi chay lai.';
        PRINT @msg;
        THROW 51418, @msg, 1;
    END

    -- === THAY RANG BUOC — mot transaction, go truoc them sau ========================

    BEGIN TRANSACTION fix22Status;

    DECLARE @drop NVARCHAR(MAX) = N'';
    SELECT @drop = @drop + N'ALTER TABLE Orders DROP CONSTRAINT ' + QUOTENAME(Name) + N';'
    FROM @statusConstraints;

    IF @drop <> N''
        EXEC sp_executesql @drop;

    SET @stmt = N'ALTER TABLE Orders ADD CONSTRAINT CK_Orders_OrderStatus
                      CHECK (OrderStatus IN (' + @allowedCsv + N'));';
    EXEC sp_executesql @stmt;

    COMMIT TRANSACTION fix22Status;
    PRINT '=== fix22: hoan tat — CK_Orders_OrderStatus da duoc ap lai ===';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
    BEGIN
        ROLLBACK TRANSACTION;
        PRINT 'fix22: da ROLLBACK — Orders giu nguyen rang buoc trang thai cu.';
    END;
    -- Dau ';' o dong tren la bat buoc: THROW yeu cau cau lenh truoc no ket thuc bang
    -- dau cham phay, neu khong parser bao "Incorrect syntax near 'THROW'".
    THROW;
END CATCH
GO

-- =====================================================================================
-- ROLLBACK
--   Script tu ROLLBACK khi gap chan, nen khong de lai trang thai nua voi.
--   Neu da COMMIT va muon quay lai rang buoc cu:
--     ALTER TABLE Orders DROP CONSTRAINT CK_Orders_OrderStatus;
--     ALTER TABLE Orders ADD CONSTRAINT CK_Orders_OrderStatus
--         CHECK (OrderStatus IN ('created','pending','confirmed','cancelled','completed','redeemed'));
--   Go han rang buoc (khong khuyen khich — day chinh la trang thai loi ma N-06 mo ta):
--     ALTER TABLE Orders DROP CONSTRAINT CK_Orders_OrderStatus;
-- =====================================================================================

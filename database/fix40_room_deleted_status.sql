-- Cho phep Rooms.Status = 'deleted' — mo khoa nhanh soft delete cua deleteRoom().
--
-- VAN DE GOC
--   fix34 dat CK_Rooms_Status CHECK (LOWER(Status) IN ('active','inactive')), trong khi
--   AdminService.deleteRoom() lai chay UPDATE Rooms SET Status = 'deleted' cho phong con
--   lich su. Hai thu nay mau thuan tuyet doi: nhanh soft delete CHUA BAO GIO chay duoc.
--
--   Hau qua tren he thong that: SQLException bi khoi catch cua deleteRoom bat lai va doi
--   thanh "Không thể xóa phòng chiếu '<ten>'. Vui lòng kiểm tra dữ liệu liên quan." —
--   dung thong bao lam chu du an tuong la ghe con bi giu. Thuc te la mot rang buoc CHECK
--   khong cho ghi trang thai ma chinh ung dung sinh ra.
--
--   Doi chung: Cinemas.Status khong co CHECK constraint nao, nen soft delete rap chay duoc.
--
-- DO TRUOC KHI SUA (CineBookDB, 07/08/2026)
--   UPDATE Rooms SET Status='deleted' WHERE Id=3;
--   -> Msg 547: The UPDATE statement conflicted with the CHECK constraint "CK_Rooms_Status".
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'CineBookDB' AND DB_NAME() NOT LIKE N'CineBookIT[_]%'
    THROW 51920, 'fix40 only accepts CineBookDB or an ephemeral CineBookIT_* database.', 1;
IF OBJECT_ID(N'dbo.Rooms', N'U') IS NULL
    THROW 51921, 'Run the base schema and prior migrations before fix40.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = N'CK_Rooms_Status'
                 AND parent_object_id = OBJECT_ID(N'dbo.Rooms'))
        ALTER TABLE dbo.Rooms DROP CONSTRAINT CK_Rooms_Status;

    -- WITH CHECK: neu con dong nao ngoai ba trang thai nay thi phai vo ngay tai day, dung de
    -- rang buoc o trang thai NOT TRUSTED roi hong am tham nhu lan truoc.
    ALTER TABLE dbo.Rooms WITH CHECK ADD CONSTRAINT CK_Rooms_Status
        CHECK (LOWER(Status) IN (N'active', N'inactive', N'deleted'));

    COMMIT TRANSACTION;
    PRINT 'fix40_room_deleted_status.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- ROLLBACK: ALTER TABLE dbo.Rooms DROP CONSTRAINT CK_Rooms_Status;
--           ALTER TABLE dbo.Rooms WITH CHECK ADD CONSTRAINT CK_Rooms_Status
--               CHECK (LOWER(Status) IN (N'active', N'inactive'));
--           (chi an toan khi khong con phong nao dang o Status='deleted';
--            neu con, khoi phuc chung ve 'inactive' truoc)

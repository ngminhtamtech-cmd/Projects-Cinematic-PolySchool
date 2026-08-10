-- Capture Orders.IsUserHidden, the "an don khoi lich su cua toi" flag.
--
-- VAN DE GOC
--   Cot nay da co that tren CineBookDB nhung KHONG script nao trong database/ tao ra no —
--   ai do them tay bang SSMS roi khong ghi lai thanh migration. JdbcOrderDAO.findHistoryByUserId
--   lai doc thang cot do, nen moi database dung tu dau (init-test-db.ps1, may dev moi, khoi phuc
--   tu script) deu lam trang "Lich su dat ve" cua thanh vien do 500 voi
--   'Invalid column name IsUserHidden'. OrderHistoryBatchIT chinh la cho no lo ra.
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'CineBookDB' AND DB_NAME() NOT LIKE N'CineBookIT[_]%'
    THROW 51900, 'fix39 only accepts CineBookDB or an ephemeral CineBookIT_* database.', 1;
IF OBJECT_ID(N'dbo.Orders', N'U') IS NULL
    THROW 51901, 'Run the base schema and prior migrations before fix39.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH('dbo.Orders', 'IsUserHidden') IS NULL
        ALTER TABLE dbo.Orders
            ADD IsUserHidden BIT NOT NULL
                CONSTRAINT DF_Orders_IsUserHidden DEFAULT (0);

    COMMIT TRANSACTION;
    PRINT 'fix39_orders_user_hidden.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- ROLLBACK: ALTER TABLE dbo.Orders DROP CONSTRAINT DF_Orders_IsUserHidden;
--           ALTER TABLE dbo.Orders DROP COLUMN IsUserHidden;
--           (chi an toan khi khong con ban ghi nao dang duoc an khoi lich su)

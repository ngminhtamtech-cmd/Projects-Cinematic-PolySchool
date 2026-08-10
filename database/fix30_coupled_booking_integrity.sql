-- fix30_coupled_booking_integrity.sql
-- Adds explicit appeal ownership and safe, idempotent uniqueness guards.
SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH('dbo.UserAppeals', 'AppealType') IS NULL
        ALTER TABLE dbo.UserAppeals ADD AppealType NVARCHAR(20) NULL;
    IF COL_LENGTH('dbo.UserAppeals', 'OrderId') IS NULL
        ALTER TABLE dbo.UserAppeals ADD OrderId INT NULL;
    -- Older installs relied on DBConnection's runtime bootstrap for these fields.
    -- A deterministic migration chain must not require starting the application first.
    IF COL_LENGTH('dbo.UserAppeals', 'TicketCode') IS NULL
        ALTER TABLE dbo.UserAppeals ADD TicketCode NVARCHAR(50) NULL;
    IF COL_LENGTH('dbo.UserAppeals', 'BankAccountInfo') IS NULL
        ALTER TABLE dbo.UserAppeals ADD BankAccountInfo NVARCHAR(255) NULL;

    -- Keep all references to the newly-added columns in a deferred batch.
    -- SQL Server compiles a whole static batch before executing conditional
    -- ALTER TABLE statements, which would otherwise report column 207.
    EXEC(N'
        -- Preserve legacy refund appeals: pre-fix rows were distinguishable by
        -- their ticket code even though they lacked AppealType/OrderId.  Do
        -- not silently downgrade those rows to account appeals.
        IF EXISTS (
            SELECT 1
            FROM dbo.UserAppeals a
            LEFT JOIN dbo.Orders o ON o.TicketCode = a.TicketCode
            WHERE NULLIF(LTRIM(RTRIM(a.TicketCode)), '''') IS NOT NULL
              AND o.Id IS NULL
        )
            THROW 51433, ''fix30 DUNG: refund appeal co TicketCode khong doi chieu duoc Order.'', 1;

        UPDATE a
           SET AppealType = CASE
                                WHEN NULLIF(LTRIM(RTRIM(a.TicketCode)), '''') IS NULL THEN ''account''
                                ELSE ''refund''
                            END,
               OrderId = o.Id
        FROM dbo.UserAppeals a
        LEFT JOIN dbo.Orders o ON o.TicketCode = a.TicketCode
        WHERE a.AppealType IS NULL;

        IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
                       WHERE name = N''FK_UserAppeals_Order''
                         AND parent_object_id = OBJECT_ID(N''dbo.UserAppeals''))
            ALTER TABLE dbo.UserAppeals ADD CONSTRAINT FK_UserAppeals_Order
                FOREIGN KEY (OrderId) REFERENCES dbo.Orders(Id) ON DELETE SET NULL;

        IF EXISTS (SELECT 1 FROM dbo.UserAppeals
                   WHERE AppealType IS NULL OR AppealType NOT IN (''account'',''refund''))
            THROW 51430, ''fix30 DUNG: UserAppeals co AppealType khong hop le.'', 1;

        IF NOT EXISTS (SELECT 1 FROM sys.indexes
                       WHERE name = N''UX_UserAppeals_Pending_Order''
                         AND object_id = OBJECT_ID(N''dbo.UserAppeals''))
        BEGIN
            IF EXISTS (SELECT 1 FROM dbo.UserAppeals
                       WHERE Status=''pending'' AND AppealType=''refund'' AND OrderId IS NOT NULL
                       GROUP BY OrderId HAVING COUNT(*) > 1)
                THROW 51431, ''fix30 DUNG: nhieu refund appeal pending cung OrderId.'', 1;
            CREATE UNIQUE INDEX UX_UserAppeals_Pending_Order
                ON dbo.UserAppeals(OrderId)
                WHERE Status=''pending'' AND AppealType=''refund'' AND OrderId IS NOT NULL;
        END;

        IF NOT EXISTS (SELECT 1 FROM sys.indexes
                       WHERE name = N''UX_UserAppeals_Pending_Account''
                         AND object_id = OBJECT_ID(N''dbo.UserAppeals''))
        BEGIN
            IF EXISTS (SELECT 1 FROM dbo.UserAppeals
                       WHERE Status=''pending'' AND AppealType=''account''
                       GROUP BY UserId HAVING COUNT(*) > 1)
                THROW 51432, ''fix30 DUNG: nhieu account appeal pending cung UserId.'', 1;
            CREATE UNIQUE INDEX UX_UserAppeals_Pending_Account
                ON dbo.UserAppeals(UserId)
                WHERE Status=''pending'' AND AppealType=''account'';
        END;
    ');

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = N'UX_RefundTransactions_Order'
                     AND object_id = OBJECT_ID(N'dbo.RefundTransactions'))
        CREATE UNIQUE INDEX UX_RefundTransactions_Order
            ON dbo.RefundTransactions(OrderId);

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE name = N'CK_RefundTransactions_Amount_Positive'
                     AND parent_object_id = OBJECT_ID(N'dbo.RefundTransactions'))
        ALTER TABLE dbo.RefundTransactions ADD CONSTRAINT CK_RefundTransactions_Amount_Positive
            CHECK (Amount > 0);

    IF NOT EXISTS (SELECT 1 FROM dbo.SystemSettings WHERE SettingKey=N'booking.stateContractVersion')
        INSERT INTO dbo.SystemSettings(SettingKey, SettingValue, UpdatedAt)
        VALUES (N'booking.stateContractVersion', N'1', GETDATE());

    COMMIT TRANSACTION;
    PRINT 'fix30_coupled_booking_integrity.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- ROLLBACK:
-- DROP INDEX UX_RefundTransactions_Order ON dbo.RefundTransactions;
-- ALTER TABLE dbo.UserAppeals DROP CONSTRAINT FK_UserAppeals_Order;
-- ALTER TABLE dbo.UserAppeals DROP COLUMN AppealType;
-- ALTER TABLE dbo.UserAppeals DROP COLUMN OrderId;

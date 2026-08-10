-- Canonical contract: account appeals are resolved in /admin/appeals;
-- refund appeals are linked to Orders and resolved in /admin/orders.
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'CineBookDB' AND DB_NAME() NOT LIKE N'CineBookIT[_]%'
    THROW 51600, 'fix36 only accepts CineBookDB or an ephemeral CineBookIT_* database.', 1;
IF OBJECT_ID(N'dbo.UserAppeals', N'U') IS NULL OR OBJECT_ID(N'dbo.Orders', N'U') IS NULL
    THROW 51601, 'Run the base schema and fix30 before fix36.', 1;
IF COL_LENGTH(N'dbo.UserAppeals', N'AppealType') IS NULL
   OR COL_LENGTH(N'dbo.UserAppeals', N'OrderId') IS NULL
   OR COL_LENGTH(N'dbo.UserAppeals', N'TicketCode') IS NULL
    THROW 51602, 'UserAppeals is missing AppealType, OrderId or TicketCode; run fix30 first.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF EXISTS (
        SELECT 1 FROM dbo.UserAppeals a
        WHERE NULLIF(LTRIM(RTRIM(a.TicketCode)), N'') IS NOT NULL
          AND (SELECT COUNT(*) FROM dbo.Orders o WHERE o.TicketCode=a.TicketCode) <> 1
    )
        THROW 51603, 'A refund appeal TicketCode does not identify exactly one order.', 1;

    IF EXISTS (
        SELECT 1 FROM dbo.UserAppeals a
        JOIN dbo.Orders o ON o.TicketCode=a.TicketCode
        WHERE NULLIF(LTRIM(RTRIM(a.TicketCode)), N'') IS NOT NULL
          AND o.UserId<>a.UserId
    )
        THROW 51604, 'A refund appeal belongs to a different user than its order.', 1;

    IF EXISTS (
        SELECT 1 FROM dbo.UserAppeals a
        JOIN dbo.Orders ticketOrder ON ticketOrder.TicketCode=a.TicketCode
        WHERE a.OrderId IS NOT NULL AND a.OrderId<>ticketOrder.Id
    )
        THROW 51605, 'Appeal OrderId conflicts with the order selected by TicketCode.', 1;

    IF EXISTS (
        SELECT 1 FROM dbo.UserAppeals
        WHERE AppealType IS NOT NULL
          AND (
              (AppealType=N'account' AND NULLIF(LTRIM(RTRIM(TicketCode)), N'') IS NOT NULL)
              OR (AppealType=N'refund' AND NULLIF(LTRIM(RTRIM(TicketCode)), N'') IS NULL)
              OR AppealType NOT IN (N'account',N'refund')
          )
    )
        THROW 51606, 'Existing AppealType conflicts with TicketCode metadata.', 1;

    UPDATE a
       SET TicketCode=NULL, AppealType=N'account', OrderId=NULL
    FROM dbo.UserAppeals a
    WHERE NULLIF(LTRIM(RTRIM(a.TicketCode)), N'') IS NULL;

    UPDATE a
       SET AppealType=N'refund', OrderId=o.Id, TicketCode=o.TicketCode
    FROM dbo.UserAppeals a
    JOIN dbo.Orders o ON o.TicketCode=a.TicketCode
    WHERE NULLIF(LTRIM(RTRIM(a.TicketCode)), N'') IS NOT NULL;

    IF EXISTS (
        SELECT OrderId FROM dbo.UserAppeals
        WHERE Status=N'pending' AND AppealType=N'refund'
        GROUP BY OrderId HAVING COUNT(*)>1
    )
        THROW 51607, 'Multiple pending refund appeals reference the same order.', 1;

    IF EXISTS (
        SELECT UserId FROM dbo.UserAppeals
        WHERE Status=N'pending' AND AppealType=N'account'
        GROUP BY UserId HAVING COUNT(*)>1
    )
        THROW 51608, 'Multiple pending account appeals reference the same user.', 1;

    IF EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name=N'CK_UserAppeals_TypeMetadata'
                 AND parent_object_id=OBJECT_ID(N'dbo.UserAppeals'))
        ALTER TABLE dbo.UserAppeals DROP CONSTRAINT CK_UserAppeals_TypeMetadata;

    IF EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'UX_UserAppeals_Pending_Order'
               AND object_id=OBJECT_ID(N'dbo.UserAppeals'))
        DROP INDEX UX_UserAppeals_Pending_Order ON dbo.UserAppeals;
    IF EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'UX_UserAppeals_Pending_Account'
               AND object_id=OBJECT_ID(N'dbo.UserAppeals'))
        DROP INDEX UX_UserAppeals_Pending_Account ON dbo.UserAppeals;
    IF EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'IX_UserAppeals_Type_Status_Order'
               AND object_id=OBJECT_ID(N'dbo.UserAppeals'))
        DROP INDEX IX_UserAppeals_Type_Status_Order ON dbo.UserAppeals;
    -- fix36 normally runs before fix43. A maintenance rerun after fix43 must also
    -- release the newer index that depends on AppealType, then restore it below.
    IF EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'IX_UserAppeals_Cinema_Type_Status'
               AND object_id=OBJECT_ID(N'dbo.UserAppeals'))
        DROP INDEX IX_UserAppeals_Cinema_Type_Status ON dbo.UserAppeals;

    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.UserAppeals')
               AND name=N'AppealType' AND is_nullable=1)
        ALTER TABLE dbo.UserAppeals ALTER COLUMN AppealType NVARCHAR(20) NOT NULL;

    ALTER TABLE dbo.UserAppeals WITH CHECK ADD CONSTRAINT CK_UserAppeals_TypeMetadata CHECK (
        (AppealType=N'account' AND TicketCode IS NULL AND OrderId IS NULL)
        OR
        (AppealType=N'refund' AND NULLIF(LTRIM(RTRIM(TicketCode)), N'') IS NOT NULL
             AND OrderId IS NOT NULL)
    );

    CREATE UNIQUE INDEX UX_UserAppeals_Pending_Order
        ON dbo.UserAppeals(OrderId)
        WHERE Status=N'pending' AND AppealType=N'refund' AND OrderId IS NOT NULL;
    CREATE UNIQUE INDEX UX_UserAppeals_Pending_Account
        ON dbo.UserAppeals(UserId)
        WHERE Status=N'pending' AND AppealType=N'account';

    CREATE INDEX IX_UserAppeals_Type_Status_Order
        ON dbo.UserAppeals(AppealType,Status,OrderId,CreatedAt);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'IX_UserAppeals_Type_Status_CreatedAt'
                   AND object_id=OBJECT_ID(N'dbo.UserAppeals'))
        CREATE INDEX IX_UserAppeals_Type_Status_CreatedAt
            ON dbo.UserAppeals(AppealType,Status,CreatedAt DESC)
            INCLUDE(UserId,OrderId,TicketCode,ResolvedAt);

    IF COL_LENGTH(N'dbo.UserAppeals', N'CinemaId') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM sys.indexes
                       WHERE name=N'IX_UserAppeals_Cinema_Type_Status'
                         AND object_id=OBJECT_ID(N'dbo.UserAppeals'))
        CREATE INDEX IX_UserAppeals_Cinema_Type_Status
            ON dbo.UserAppeals(CinemaId,AppealType,Status,CreatedAt DESC);

    COMMIT TRANSACTION;
    PRINT 'fix36_user_appeal_contract.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- fix28_booking_state_core.sql
-- Canonical booking state support.  Additive and idempotent: legacy status
-- strings remain unchanged while the application state machine gains durable
-- versioning, seat ownership and command/audit/outbox storage.
SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH('dbo.Orders', 'StateVersion') IS NULL
        ALTER TABLE dbo.Orders ADD StateVersion ROWVERSION;

    IF COL_LENGTH('dbo.ShowtimeSeats', 'ClaimedByOrderId') IS NULL
        ALTER TABLE dbo.ShowtimeSeats ADD ClaimedByOrderId INT NULL;

    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
                   WHERE name = N'FK_ShowtimeSeats_ClaimedByOrder'
                     AND parent_object_id = OBJECT_ID(N'dbo.ShowtimeSeats'))
        ALTER TABLE dbo.ShowtimeSeats ADD CONSTRAINT FK_ShowtimeSeats_ClaimedByOrder
            FOREIGN KEY (ClaimedByOrderId) REFERENCES dbo.Orders(Id) ON DELETE SET NULL;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = N'IX_ShowtimeSeats_ClaimedByOrderId'
                     AND object_id = OBJECT_ID(N'dbo.ShowtimeSeats'))
        CREATE INDEX IX_ShowtimeSeats_ClaimedByOrderId
            ON dbo.ShowtimeSeats(ClaimedByOrderId)
            INCLUDE (ShowtimeId, Status, HeldByUserId, HeldUntil);

    IF COL_LENGTH('dbo.Users', 'LoyaltyPointDebt') IS NULL
        ALTER TABLE dbo.Users ADD LoyaltyPointDebt INT NOT NULL CONSTRAINT DF_Users_LoyaltyPointDebt DEFAULT 0;

    IF OBJECT_ID(N'dbo.BookingCommandExecutions', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.BookingCommandExecutions (
            Id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_BookingCommandExecutions PRIMARY KEY,
            ActorScope NVARCHAR(150) NOT NULL,
            CommandType NVARCHAR(50) NOT NULL,
            IdempotencyKey NVARCHAR(150) NOT NULL,
            RequestHash CHAR(64) NOT NULL,
            OrderId INT NULL,
            Status NVARCHAR(20) NOT NULL CONSTRAINT DF_BookingCommandExecutions_Status DEFAULT 'succeeded',
            ResponseStatus INT NULL,
            ResponseJson NVARCHAR(MAX) NULL,
            CreatedAt DATETIME2(3) NOT NULL CONSTRAINT DF_BookingCommandExecutions_CreatedAt DEFAULT SYSDATETIME(),
            CompletedAt DATETIME2(3) NULL,
            CONSTRAINT FK_BookingCommandExecutions_Order FOREIGN KEY (OrderId) REFERENCES dbo.Orders(Id)
                ON DELETE SET NULL,
            CONSTRAINT CK_BookingCommandExecutions_Status CHECK (Status IN ('processing','succeeded','failed'))
        );
    END;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = N'UX_BookingCommandExecutions_Key'
                     AND object_id = OBJECT_ID(N'dbo.BookingCommandExecutions'))
        CREATE UNIQUE INDEX UX_BookingCommandExecutions_Key
            ON dbo.BookingCommandExecutions(ActorScope, CommandType, IdempotencyKey);

    IF OBJECT_ID(N'dbo.BookingStateTransitions', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.BookingStateTransitions (
            Id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_BookingStateTransitions PRIMARY KEY,
            OrderId INT NULL,
            CommandExecutionId BIGINT NULL,
            CommandType NVARCHAR(50) NOT NULL,
            BeforeState NVARCHAR(40) NULL,
            AfterState NVARCHAR(40) NULL,
            BeforeOrderStatus NVARCHAR(20) NULL,
            BeforePaymentStatus NVARCHAR(20) NULL,
            AfterOrderStatus NVARCHAR(20) NULL,
            AfterPaymentStatus NVARCHAR(20) NULL,
            ActorScope NVARCHAR(150) NULL,
            Reason NVARCHAR(500) NULL,
            CreatedAt DATETIME2(3) NOT NULL CONSTRAINT DF_BookingStateTransitions_CreatedAt DEFAULT SYSDATETIME(),
            CONSTRAINT FK_BookingStateTransitions_Order FOREIGN KEY (OrderId) REFERENCES dbo.Orders(Id)
                ON DELETE SET NULL,
            CONSTRAINT FK_BookingStateTransitions_Command FOREIGN KEY (CommandExecutionId)
                REFERENCES dbo.BookingCommandExecutions(Id) ON DELETE SET NULL
        );
    END;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = N'IX_BookingStateTransitions_Order'
                     AND object_id = OBJECT_ID(N'dbo.BookingStateTransitions'))
        CREATE INDEX IX_BookingStateTransitions_Order
            ON dbo.BookingStateTransitions(OrderId, CreatedAt DESC, Id DESC);

    IF OBJECT_ID(N'dbo.BookingOutbox', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.BookingOutbox (
            Id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_BookingOutbox PRIMARY KEY,
            AggregateType NVARCHAR(40) NOT NULL,
            AggregateId INT NULL,
            EventType NVARCHAR(80) NOT NULL,
            DedupeKey NVARCHAR(220) NOT NULL,
            PayloadJson NVARCHAR(MAX) NOT NULL,
            Status NVARCHAR(20) NOT NULL CONSTRAINT DF_BookingOutbox_Status DEFAULT 'pending',
            Attempts INT NOT NULL CONSTRAINT DF_BookingOutbox_Attempts DEFAULT 0,
            NextAttemptAt DATETIME2(3) NULL,
            LastError NVARCHAR(1000) NULL,
            CreatedAt DATETIME2(3) NOT NULL CONSTRAINT DF_BookingOutbox_CreatedAt DEFAULT SYSDATETIME(),
            CompletedAt DATETIME2(3) NULL,
            CONSTRAINT CK_BookingOutbox_Status CHECK (Status IN ('pending','processing','completed','failed'))
        );
    END;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = N'UX_BookingOutbox_DedupeKey'
                     AND object_id = OBJECT_ID(N'dbo.BookingOutbox'))
        CREATE UNIQUE INDEX UX_BookingOutbox_DedupeKey ON dbo.BookingOutbox(DedupeKey);

    IF OBJECT_ID(N'dbo.LoyaltyPointDebtLedger', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.LoyaltyPointDebtLedger (
            Id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_LoyaltyPointDebtLedger PRIMARY KEY,
            UserId INT NOT NULL,
            OrderId INT NULL,
            Points INT NOT NULL,
            Type NVARCHAR(30) NOT NULL,
            IdempotencyKey NVARCHAR(150) NULL,
            CreatedAt DATETIME2(3) NOT NULL CONSTRAINT DF_LoyaltyPointDebtLedger_CreatedAt DEFAULT SYSDATETIME(),
            CONSTRAINT FK_LoyaltyPointDebtLedger_User FOREIGN KEY (UserId) REFERENCES dbo.Users(Id),
            CONSTRAINT FK_LoyaltyPointDebtLedger_Order FOREIGN KEY (OrderId) REFERENCES dbo.Orders(Id)
                ON DELETE SET NULL,
            CONSTRAINT CK_LoyaltyPointDebtLedger_Points_Positive CHECK (Points > 0)
        );
    END;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = N'UX_LoyaltyPointDebtLedger_Key'
                     AND object_id = OBJECT_ID(N'dbo.LoyaltyPointDebtLedger'))
        CREATE UNIQUE INDEX UX_LoyaltyPointDebtLedger_Key
            ON dbo.LoyaltyPointDebtLedger(IdempotencyKey)
            WHERE IdempotencyKey IS NOT NULL;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = N'IX_LoyaltyPointDebtLedger_User'
                     AND object_id = OBJECT_ID(N'dbo.LoyaltyPointDebtLedger'))
        CREATE INDEX IX_LoyaltyPointDebtLedger_User
            ON dbo.LoyaltyPointDebtLedger(UserId, CreatedAt, Id);

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE name = N'CK_Users_LoyaltyPointDebt_NonNegative'
                     AND parent_object_id = OBJECT_ID(N'dbo.Users'))
        EXEC(N'ALTER TABLE dbo.Users ADD CONSTRAINT CK_Users_LoyaltyPointDebt_NonNegative
            CHECK (LoyaltyPointDebt >= 0)');

    COMMIT TRANSACTION;
    PRINT 'fix28_booking_state_core.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- ROLLBACK (additive objects only; use the verified database backup first):
-- ALTER TABLE dbo.ShowtimeSeats DROP CONSTRAINT FK_ShowtimeSeats_ClaimedByOrder;
-- ALTER TABLE dbo.ShowtimeSeats DROP COLUMN ClaimedByOrderId;
-- ALTER TABLE dbo.Orders DROP COLUMN StateVersion;

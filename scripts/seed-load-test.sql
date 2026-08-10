IF DB_NAME() NOT LIKE N'CineBookIT[_]%'
    THROW 51000, 'Chi duoc seed tai database tam CineBookIT_*.', 1;

DECLARE @UserId INT=(SELECT TOP(1) Id FROM Users WHERE Deleted=0 ORDER BY Id);
DECLARE @ShowtimeId INT=(SELECT TOP(1) Id FROM Showtimes ORDER BY Id);
IF @UserId IS NULL OR @ShowtimeId IS NULL
    THROW 51001, 'Can it nhat mot user va mot showtime fixture.', 1;

;WITH n AS (
    SELECT TOP (50000) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS rn
    FROM sys.all_objects a CROSS JOIN sys.all_objects b
)
INSERT INTO Orders(UserId, ShowtimeId, SeatSubtotal, ComboSubtotal, DiscountAmount, TotalAmount,
                   TicketCode, PaymentMethod, PaymentStatus, OrderStatus, CreatedAt, UpdatedAt)
SELECT @UserId, @ShowtimeId, 100000, 0, 0, 100000,
       CONCAT('LT', RIGHT(REPLICATE('0',30)+CAST(rn AS VARCHAR(30)),30)),
       'card', 'paid', 'confirmed', DATEADD(SECOND,-rn,GETDATE()), GETDATE()
FROM n;

DECLARE @ActorId INT=(SELECT TOP(1) Id FROM Users WHERE Role='admin' ORDER BY Id);
;WITH n AS (
    SELECT TOP (200000) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS rn
    FROM sys.all_objects a CROSS JOIN sys.all_objects b
)
INSERT INTO AuditLogs(ActorUserId, Action, TargetType, TargetId, DetailJson, CreatedAt)
SELECT @ActorId, 'LOAD_TEST', 'Order', CAST(rn AS VARCHAR(30)), N'{"source":"P16"}',
       DATEADD(SECOND,-rn,GETDATE())
FROM n;

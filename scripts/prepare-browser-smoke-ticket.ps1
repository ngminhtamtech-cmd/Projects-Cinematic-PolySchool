<#
.SYNOPSIS
    Prepare or remove the deterministic paid ticket used by the live browser gate.

.DESCRIPTION
    This helper is deliberately hard-bound to ephemeral CineBookIT_* databases. It refuses any
    properties file whose JDBC URL names another database and repeats the same
    assertion inside SQL before it writes. Production data is never a valid target.
#>
[CmdletBinding()]
param(
    [ValidateSet('Prepare', 'Cleanup')]
    [string]$Mode = 'Prepare',
    [string]$PropertiesPath
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $PropertiesPath) {
    $PropertiesPath = Join-Path $root 'target\db.it.properties'
}
$PropertiesPath = [IO.Path]::GetFullPath($PropertiesPath)
if (-not (Test-Path -LiteralPath $PropertiesPath -PathType Leaf)) {
    throw "Missing test database properties: $PropertiesPath"
}

$properties = @{}
foreach ($line in Get-Content -LiteralPath $PropertiesPath -Encoding UTF8) {
    if ($line -match '^\s*([^#=]+?)\s*=\s*(.*?)\s*$') {
        $properties[$matches[1]] = $matches[2]
    }
}

$jdbcUrl = $properties['db.url']
$username = $properties['db.username']
$password = $properties['db.password']
if (-not $jdbcUrl -or -not $username -or $null -eq $password) {
    throw 'The runtime integration properties must define db.url, db.username and db.password.'
}
if ($jdbcUrl -notmatch '(?i)^jdbc:sqlserver://([^;]+).*;databaseName=([^;]+)') {
    throw 'Unsupported SQL Server JDBC URL in the runtime integration properties.'
}
$server = $matches[1] -replace ':', ','
$database = $matches[2]
if ($database -notmatch '^CineBookIT_[0-9]{14}_[0-9a-fA-F]{8}$') {
    throw "Refusing browser fixture database '$database'; expected CineBookIT_<timestamp>_<random>."
}

$sqlcmd = 'C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\sqlcmd.exe'
if (-not (Test-Path -LiteralPath $sqlcmd)) {
    $sqlcmd = (Get-Command sqlcmd -ErrorAction Stop).Source
}

$ticketCode = 'CBROWSERTOOEARLY20260801'
$cancelTicketCode = 'CBROWSERCANCEL20260801'
$cleanupSql = @"
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME() <> N'$database'
    THROW 51000, 'Browser smoke fixture database mismatch.', 1;
BEGIN TRANSACTION;
DECLARE @CleanupTicketCodes table (TicketCode nvarchar(32) PRIMARY KEY);
INSERT INTO @CleanupTicketCodes (TicketCode)
VALUES (N'$ticketCode'), (N'$cancelTicketCode');
DECLARE @Seats table (Id int PRIMARY KEY);
INSERT INTO @Seats (Id)
SELECT ShowtimeSeatId FROM OrderSeats
WHERE OrderId IN (
    SELECT Id FROM Orders
    WHERE TicketCode IN (SELECT TicketCode FROM @CleanupTicketCodes)
);
DELETE FROM OrderSeats
WHERE OrderId IN (
    SELECT Id FROM Orders
    WHERE TicketCode IN (SELECT TicketCode FROM @CleanupTicketCodes)
);
DELETE FROM Orders
WHERE TicketCode IN (SELECT TicketCode FROM @CleanupTicketCodes);
UPDATE ss SET Status = 'available', HeldByUserId = NULL, HeldUntil = NULL
FROM ShowtimeSeats ss
JOIN @Seats used ON used.Id = ss.Id
WHERE NOT EXISTS (SELECT 1 FROM OrderSeats os WHERE os.ShowtimeSeatId = ss.Id)
  AND ss.Status IN ('booked', 'held');
DELETE FROM FilmCategories
WHERE FilmId IN (SELECT Id FROM Films WHERE Title LIKE 'BROWSER-UPLOAD-%');
DELETE FROM CinemaFilms
WHERE FilmId IN (SELECT Id FROM Films WHERE Title LIKE 'BROWSER-UPLOAD-%');
DELETE FROM Films
WHERE Title LIKE 'BROWSER-UPLOAD-%'
  AND NOT EXISTS (SELECT 1 FROM Showtimes s WHERE s.FilmId = Films.Id);
DELETE FROM Users
WHERE Email LIKE 'manager-browser-%@test.com'
  AND Role = 'member'
  AND NOT EXISTS (SELECT 1 FROM Orders o WHERE o.UserId = Users.Id);
COMMIT TRANSACTION;
"@

$prepareSql = @"
$cleanupSql
SET XACT_ABORT ON;
IF DB_NAME() <> N'$database'
    THROW 51000, 'Browser smoke fixture database mismatch.', 1;
BEGIN TRANSACTION;
DECLARE @PreparedTicketCode nvarchar(32) = N'$ticketCode';
DECLARE @PreparedCancelTicketCode nvarchar(32) = N'$cancelTicketCode';
DECLARE @ShowtimeId int = (
    SELECT TOP (1) s.Id
    FROM Showtimes s
    JOIN Rooms r ON r.Id = s.RoomId AND r.Status = 'active'
    JOIN Films f ON f.Id = s.FilmId AND f.Status = 'showing'
    WHERE s.CinemaId = 1
      AND s.StartTime > DATEADD(DAY, 1, GETDATE())
    ORDER BY s.StartTime, s.Id
);
IF @ShowtimeId IS NULL
    THROW 51001, 'No future Cinema 1 showtime exists for browser smoke.', 1;
DECLARE @ShowtimeSeatId int = (
    SELECT TOP (1) ss.Id
    FROM ShowtimeSeats ss
    WHERE ss.ShowtimeId = @ShowtimeId
      AND ss.Status <> 'maintenance'
      AND NOT EXISTS (SELECT 1 FROM OrderSeats os WHERE os.ShowtimeSeatId = ss.Id)
    ORDER BY ss.Id
);
IF @ShowtimeSeatId IS NULL
    THROW 51002, 'No unassigned future seat exists for browser smoke.', 1;
DECLARE @CancelShowtimeSeatId int = (
    SELECT TOP (1) ss.Id
    FROM ShowtimeSeats ss
    WHERE ss.ShowtimeId = @ShowtimeId
      AND ss.Id <> @ShowtimeSeatId
      AND ss.Status <> 'maintenance'
      AND NOT EXISTS (SELECT 1 FROM OrderSeats os WHERE os.ShowtimeSeatId = ss.Id)
    ORDER BY ss.Id
);
IF @CancelShowtimeSeatId IS NULL
    THROW 51003, 'No second unassigned future seat exists for browser cancellation smoke.', 1;
DECLARE @OrderId table (Id int PRIMARY KEY);
INSERT INTO Orders (
    UserId, ShowtimeId, SeatSubtotal, ComboSubtotal, DiscountAmount,
    TotalAmount, TicketCode, TicketQrUrl, PaymentMethod, PaymentStatus, OrderStatus
)
OUTPUT inserted.Id INTO @OrderId (Id)
SELECT 1, s.Id, s.BasePrice + ss.ExtraFee, 0, 0,
       s.BasePrice + ss.ExtraFee, @PreparedTicketCode,
       N'/tickets/qr/' + @PreparedTicketCode, 'card', 'paid', 'confirmed'
FROM Showtimes s
JOIN ShowtimeSeats ss ON ss.Id = @ShowtimeSeatId
WHERE s.Id = @ShowtimeId;
INSERT INTO OrderSeats (OrderId, ShowtimeSeatId, SeatKey, SeatType, UnitPrice)
SELECT oid.Id, ss.Id, seat.SeatKey, seat.SeatType, s.BasePrice + ss.ExtraFee
FROM @OrderId oid
CROSS JOIN ShowtimeSeats ss
JOIN Seats seat ON seat.Id = ss.SeatId
JOIN Showtimes s ON s.Id = ss.ShowtimeId
WHERE ss.Id = @ShowtimeSeatId;
UPDATE ShowtimeSeats
SET Status = 'booked', HeldByUserId = NULL, HeldUntil = NULL
WHERE Id = @ShowtimeSeatId;
DECLARE @CancelOrderId table (Id int PRIMARY KEY);
INSERT INTO Orders (
    UserId, ShowtimeId, SeatSubtotal, ComboSubtotal, DiscountAmount,
    TotalAmount, TicketCode, TicketQrUrl, PaymentMethod, PaymentStatus, OrderStatus
)
OUTPUT inserted.Id INTO @CancelOrderId (Id)
SELECT 1, s.Id, s.BasePrice + ss.ExtraFee, 0, 0,
       s.BasePrice + ss.ExtraFee, @PreparedCancelTicketCode,
       N'/tickets/qr/' + @PreparedCancelTicketCode, 'card', 'pending', 'created'
FROM Showtimes s
JOIN ShowtimeSeats ss ON ss.Id = @CancelShowtimeSeatId
WHERE s.Id = @ShowtimeId;
INSERT INTO OrderSeats (OrderId, ShowtimeSeatId, SeatKey, SeatType, UnitPrice)
SELECT oid.Id, ss.Id, seat.SeatKey, seat.SeatType, s.BasePrice + ss.ExtraFee
FROM @CancelOrderId oid
CROSS JOIN ShowtimeSeats ss
JOIN Seats seat ON seat.Id = ss.SeatId
JOIN Showtimes s ON s.Id = ss.ShowtimeId
WHERE ss.Id = @CancelShowtimeSeatId;
UPDATE ShowtimeSeats
SET Status = 'held', HeldByUserId = 1, HeldUntil = DATEADD(MINUTE, 10, GETDATE())
WHERE Id = @CancelShowtimeSeatId;
COMMIT TRANSACTION;
"@

$sql = if ($Mode -eq 'Prepare') { $prepareSql } else { $cleanupSql }
$oldPassword = $env:SQLCMDPASSWORD
$env:SQLCMDPASSWORD = $password
try {
    & $sqlcmd -S $server -U $username -C -I -b -d $database -Q $sql
    if ($LASTEXITCODE -ne 0) {
        throw "Browser smoke ticket $Mode failed with exit code $LASTEXITCODE."
    }
} finally {
    if ($null -eq $oldPassword) {
        Remove-Item Env:SQLCMDPASSWORD -ErrorAction SilentlyContinue
    } else {
        $env:SQLCMDPASSWORD = $oldPassword
    }
}

Write-Host "Browser smoke ticket $Mode complete on ${database}: $ticketCode, $cancelTicketCode"

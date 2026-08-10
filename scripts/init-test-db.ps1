[Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSAvoidUsingPlainTextForPassword', '')]
param(
    [string]$Server = "localhost",
    [string]$User = "sa",
    [Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSAvoidUsingPlainTextForPassword', '')]
    [string]$Password = $env:CINEBOOK_DB_PASSWORD,
    [string]$DatabaseName = ("CineBookIT_{0}_{1}" -f (Get-Date -Format 'yyyyMMddHHmmss'),
        ([Guid]::NewGuid().ToString('N').Substring(0, 8))),
    [string]$OutputProperties = "target\db.it.properties"
)

$ErrorActionPreference = "Stop"
$loopbackHosts = @('localhost', '127.0.0.1', '::1', '(local)')
$serverHost = ($Server -split '[\\,]')[0]
if ($loopbackHosts -notcontains $serverHost) {
    throw "Integration database runner refuses non-loopback server '$Server'."
}
if ($DatabaseName -notmatch '^CineBookIT_[0-9]{14}_[0-9a-fA-F]{8}$') {
    throw "Integration database name must match CineBookIT_<timestamp>_<random>."
}
if ($DatabaseName -ceq 'CineBookDB') {
    throw "Integration runner absolutely refuses CineBookDB."
}
if ([string]::IsNullOrWhiteSpace($Password)) {
    throw "Set CINEBOOK_DB_PASSWORD or pass -Password explicitly."
}

$sqlCmd = "C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\sqlcmd.exe"
if (-not (Test-Path -LiteralPath $sqlCmd)) { $sqlCmd = (Get-Command sqlcmd).Source }
$root = Split-Path -Parent $PSScriptRoot
$outputPath = if ([System.IO.Path]::IsPathRooted($OutputProperties)) {
    [System.IO.Path]::GetFullPath($OutputProperties)
} else {
    Join-Path $root $OutputProperties
}
$outputDirectory = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

& $sqlCmd -S $Server -U $User -P $Password -C -I -b -d master -f 65001 -Q `
    "IF DB_ID(N'$DatabaseName') IS NOT NULL THROW 51000, 'Refusing to reuse integration database', 1; CREATE DATABASE [$DatabaseName];"
if ($LASTEXITCODE -ne 0) { throw "Could not create $DatabaseName (exit $LASTEXITCODE)." }

$migrations = @(
    "database\schema.sql", "database\alter_cinema_films.sql", "database\alter_cinemas_add_banner.sql",
    "database\alter_rooms_status.sql", "database\alter_seats_add_maintenance.sql",
    "database\alter_seats_price_surcharge.sql", "database\alter_showtimes_format_version.sql",
    "database\alter_users_add_staff_role.sql", "database\alter_users_comments_appeals.sql",
    "database\alter_users_promotions_loyalty.sql", "database\migration_update_cinemas.sql",
    "database\migration_update_films.sql", "database\migration_v2_films_and_cinemas.sql",
    "database\alter_and_seed_showtimes_ui.sql", "database\fix00_create_missing_tables_cinebookdb.sql",
    "database\fix01_money_and_orders.sql", "database\fix02_refund.sql", "database\fix03_promotion_usage.sql",
    "database\fix04_security.sql", "database\fix05_ticketcode.sql", "database\fix06_showtime_buffer.sql",
    "database\fix07_audit_before_after.sql", "database\fix08_settings_seed.sql", "database\fix09_invoices.sql",
    "database\fix10_personal_data.sql", "database\fix11_indexes.sql", "database\fix12_cinema_scope.sql",
    "database\fix13_operations.sql", "database\fix14_schema_drift_alignment.sql",
    "database\fix15_combo_cinema_scope.sql", "database\fix16_film_enddate.sql",
    "database\fix17_email_verify_resend.sql", "database\fix18_orphan_notifications.sql",
    "database\fix19_refresh_tokens.sql", "database\fix20_loyalty_ledger.sql",
    "database\fix21_user_notifications.sql", "database\fix22_orders_status_constraint.sql",
    "database\fix23_orphan_notification_recipients.sql", "database\fix24_refund_rejection.sql",
    "database\fix25_comments_one_review_per_film.sql", "database\fix26_comments_duplicate_reviews.sql",
    "database\fix27_max_open_drafts_setting.sql", "database\fix28_booking_state_core.sql",
    "database\fix29_booking_command_views.sql", "database\fix30_coupled_booking_integrity.sql",
    "database\fix31_policy_documents.sql", "database\fix32_seat_layout_versions.sql",
    "database\fix33_notification_resolution.sql", "database\fix34_admin_domain_integrity.sql",
    "database\fix35_film_showtime_lifecycle.sql", "database\fix36_user_appeal_contract.sql",
    "database\fix37_refund_appeal_terminal_reconciliation.sql",
    "database\fix38_refund_window_and_showtime_deeplink.sql",
    "database\fix39_orders_user_hidden.sql",
    "database\fix40_room_deleted_status.sql",
    "database\fix41_reset_request_cooldown.sql",
    "database\fix42_promotion_mail_cap.sql",
    "database\fix43_cinema_governance_core.sql",
    "database\fix44_approval_workflow.sql",
    "database\fix45_cinema_contents.sql",
    "database\fix46_legacy_scope_backfill.sql",
    "database\seed_test_fixtures.sql"
)

try {
    foreach ($file in $migrations) {
        $path = Join-Path $root $file
        Write-Host "Running $file on $DatabaseName..."
        $content = Get-Content -LiteralPath $path -Raw -Encoding UTF8
        $content = $content -replace '(?i)USE\s+\[?CineBookDB(?:_Test)?\]?', "USE [$DatabaseName]"
        $tempFile = [System.IO.Path]::GetTempFileName() + ".sql"
        try {
            [System.IO.File]::WriteAllText($tempFile, $content, [System.Text.Encoding]::UTF8)
            & $sqlCmd -S $Server -U $User -P $Password -C -I -b -d $DatabaseName -f 65001 -i $tempFile
            if ($LASTEXITCODE -ne 0) { throw "$file failed (exit $LASTEXITCODE)." }
        } finally {
            Remove-Item -LiteralPath $tempFile -ErrorAction SilentlyContinue
        }
    }

    $properties = @"
db.driver=com.microsoft.sqlserver.jdbc.SQLServerDriver
db.url=jdbc:sqlserver://$Server`:1433;databaseName=$DatabaseName;encrypt=true;trustServerCertificate=true
db.username=$User
db.password=$Password
db.pool.maxSize=5
db.pool.minIdle=1
db.pool.connectionTimeoutMs=10000
db.pool.leakDetectionThresholdMs=20000
db.pool.name=CineBookITPool
mail.mode=logfile
ticket.hmac.secret=test-only-4qH1Wc8nPz2Bv7Lm5Rx9Kd3Yt6Fs0UaE
"@
    [System.IO.File]::WriteAllText($outputPath, $properties, [System.Text.Encoding]::UTF8)
    Write-Output $DatabaseName
} catch {
    & $sqlCmd -S $Server -U $User -P $Password -C -I -b -d master -Q `
        "IF DB_ID(N'$DatabaseName') IS NOT NULL BEGIN ALTER DATABASE [$DatabaseName] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$DatabaseName]; END"
    throw
}

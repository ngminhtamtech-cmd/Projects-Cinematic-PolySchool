param(
    [string]$Server = 'localhost',
    [string]$User = 'sa',
    [string]$Password = $env:CINEBOOK_DB_PASSWORD,
    [string]$BackupDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'target\backups'),
    [switch]$ConfirmDrop
)

$ErrorActionPreference = 'Stop'
$database = 'CineBookDB_Test'
if ($Server -notmatch '^(?i)(localhost|127\.0\.0\.1|\.)([\\,;:].*)?$') {
    throw "Refusing non-loopback SQL Server '$Server'."
}
if (-not $ConfirmDrop) {
    throw 'Destructive operation refused. Re-run with -ConfirmDrop after the release gate succeeds.'
}
if ([string]::IsNullOrWhiteSpace($Password)) {
    throw 'SQL Server password is required.'
}

$sqlCmd = 'C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\sqlcmd.exe'
if (-not (Test-Path -LiteralPath $sqlCmd)) { $sqlCmd = (Get-Command sqlcmd).Source }

New-Item -ItemType Directory -Path $BackupDirectory -Force | Out-Null
$resolvedDirectory = (Resolve-Path -LiteralPath $BackupDirectory).Path
$backupPath = Join-Path $resolvedDirectory ("{0}_{1}.bak" -f $database, (Get-Date -Format 'yyyyMMdd_HHmmss'))
$escapedBackupPath = $backupPath.Replace("'", "''")

$exists = (& $sqlCmd -S $Server -U $User -P $Password -C -h -1 -W -d master `
    -Q "SET NOCOUNT ON; SELECT CASE WHEN DB_ID(N'$database') IS NULL THEN 0 ELSE 1 END").Trim()
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the fixed test database.' }
if ($exists -eq '0') {
    Write-Host "$database does not exist; nothing to archive or drop."
    return
}

$completed = $false
try {
    & $sqlCmd -S $Server -U $User -P $Password -C -I -b -d master -Q @"
ALTER DATABASE [$database] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
BACKUP DATABASE [$database] TO DISK=N'$escapedBackupPath' WITH COPY_ONLY, INIT, CHECKSUM;
RESTORE HEADERONLY FROM DISK=N'$escapedBackupPath';
RESTORE VERIFYONLY FROM DISK=N'$escapedBackupPath' WITH CHECKSUM;
DROP DATABASE [$database];
"@
    if ($LASTEXITCODE -ne 0) { throw "Backup/header verification/drop failed (exit $LASTEXITCODE)." }
    $completed = $true
    Write-Host "Archived and dropped $database. Backup: $backupPath"
} finally {
    if (-not $completed) {
        & $sqlCmd -S $Server -U $User -P $Password -C -d master `
            -Q "IF DB_ID(N'$database') IS NOT NULL ALTER DATABASE [$database] SET MULTI_USER;" | Out-Null
    }
}

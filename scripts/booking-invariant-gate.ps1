<##
.SYNOPSIS
    Fail-closed business invariant gate for the test database.

.DESCRIPTION
    The gate is intentionally read-only. It accepts only a runtime config
    targeting CineBookIT_* on loopback, runs the SQL invariant contract, and
    writes a machine-readable JSON evidence file under target/invariants.
##>
[CmdletBinding()]
param(
    [string]$ConfigPath,
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $root 'target\db.it.properties'
}
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $root 'target\invariants\booking-invariants.json'
}
$config = [IO.Path]::GetFullPath($ConfigPath)
$sqlFile = Join-Path $root 'scripts\booking-invariant-check.sql'

function Read-Properties([string]$path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing database config: $path"
    }
    $result = @{}
    foreach ($line in Get-Content -LiteralPath $path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
        $at = $trimmed.IndexOf('=')
        if ($at -le 0) { throw "Invalid property in ${path}: $trimmed" }
        $result[$trimmed.Substring(0, $at).Trim()] = $trimmed.Substring($at + 1).Trim()
    }
    return $result
}

function Invoke-Sql([string]$sqlcmd, [hashtable]$props, [string]$database, [string]$query) {
    $url = [string]$props['db.url']
    if ($url -notmatch '(?i)^jdbc:sqlserver://([^;]+)') { throw 'Unsupported db.url.' }
    $server = $Matches[1]
    if ($server -match '^(.+):(\d+)$') { $server = "$($Matches[1]),$($Matches[2])" }
    $hadPassword = Test-Path Env:SQLCMDPASSWORD
    $oldPassword = $env:SQLCMDPASSWORD
    try {
        $env:SQLCMDPASSWORD = [string]$props['db.password']
        $output = & $sqlcmd -S $server -U ([string]$props['db.username']) -C -I -b `
            -h -1 -W -s '|' -f 65001 -d $database -Q $query 2>&1
        $code = $LASTEXITCODE
    } finally {
        if ($hadPassword) { $env:SQLCMDPASSWORD = $oldPassword }
        else { Remove-Item Env:SQLCMDPASSWORD -ErrorAction SilentlyContinue }
    }
    if ($code -ne 0) { throw "sqlcmd failed with exit code ${code}: $($output -join ' ')" }
    return @($output | ForEach-Object { "$($_)".Trim() } | Where-Object { $_ -ne '' })
}

$props = Read-Properties $config
$url = [string]$props['db.url']
if ($url -notmatch '(?i)(?:^|;)databaseName=([^;]+)') { throw 'db.url is missing databaseName.' }
$databaseName = $Matches[1]
if ($databaseName -notmatch '^CineBookIT_[0-9]{14}_[0-9a-fA-F]{8}$') {
    throw "Invariant gate refuses database $databaseName."
}
if ($url -notmatch '(?i)^jdbc:sqlserver://([^;]+)') { throw 'db.url must target SQL Server.' }
$serverHost = (($Matches[1] -split ':')[0]).ToLowerInvariant()
if (@('localhost','127.0.0.1','::1') -notcontains $serverHost) { throw "Invariant gate requires loopback, got $serverHost." }
if (-not (Test-Path -LiteralPath $sqlFile -PathType Leaf)) { throw "Missing invariant SQL: $sqlFile" }
$sqlcmd = 'C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\sqlcmd.exe'
if (-not (Test-Path -LiteralPath $sqlcmd -PathType Leaf)) {
    $command = Get-Command sqlcmd -ErrorAction SilentlyContinue
    if (-not $command) { throw 'sqlcmd is required for the invariant gate.' }
    $sqlcmd = $command.Source
}

$sqlOutput = Invoke-Sql $sqlcmd $props $databaseName (Get-Content -LiteralPath $sqlFile -Raw -Encoding UTF8)
$rows = @()
foreach ($line in $sqlOutput) {
    if ($line -notmatch '^(INV-[A-Z0-9-]+)\|([0-9]+)$') {
        throw "Unexpected invariant output: $line"
    }
    $rows += [pscustomobject]@{ InvariantId = $Matches[1]; ViolationCount = [long]$Matches[2] }
}
if ($rows.Count -eq 0) { throw 'Invariant query returned no rows.' }
$violations = [long](($rows | Measure-Object -Property ViolationCount -Sum).Sum)
$outboxPending = [long](($rows | Where-Object { $_.InvariantId -eq 'INV-OUTBOX' } | Select-Object -First 1).ViolationCount)
$contractVersionLines = @(Invoke-Sql $sqlcmd $props $databaseName `
    "SET NOCOUNT ON; SELECT COALESCE((SELECT TOP(1) SettingValue FROM dbo.SystemSettings WHERE SettingKey=N'booking.stateContractVersion'), N'0');"
)
if ($contractVersionLines.Count -ne 1 -or $contractVersionLines[0] -notmatch '^\d+$') {
    throw 'Could not read booking.stateContractVersion.'
}
$contractVersion = [int]$contractVersionLines[0]

$evidence = [ordered]@{
    database = $databaseName
    stateContractVersion = $contractVersion
    invariantCount = $rows.Count
    violations = $violations
    outboxPending = $outboxPending
    checkedAtUtc = [DateTime]::UtcNow.ToString('o')
    checks = $rows
}
$parent = Split-Path -Parent ([IO.Path]::GetFullPath($OutputPath))
New-Item -ItemType Directory -Path $parent -Force | Out-Null
$evidence | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
Write-Output ($evidence | ConvertTo-Json -Depth 5 -Compress)
if ($contractVersion -lt 1 -or $violations -ne 0 -or $outboxPending -ne 0) {
    exit 1
}

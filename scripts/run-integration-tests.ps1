param(
    [string]$Server = "localhost",
    [string]$User = "sa",
    [string]$Password = $env:CINEBOOK_DB_PASSWORD,
    [string]$Maven = "mvn",
    [string]$IntegrationTest
)

$ErrorActionPreference = "Stop"
$databaseName = "CineBookIT_{0}_{1}" -f (Get-Date -Format 'yyyyMMddHHmmss'),
    ([Guid]::NewGuid().ToString('N').Substring(0, 8))
$root = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $root ("target\db.it.{0}.properties" -f $databaseName)
$sqlCmd = "C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\sqlcmd.exe"
if (-not (Test-Path -LiteralPath $sqlCmd)) { $sqlCmd = (Get-Command sqlcmd).Source }

try {
    & (Join-Path $PSScriptRoot 'init-test-db.ps1') -Server $Server -User $User -Password $Password `
        -DatabaseName $databaseName -OutputProperties $configPath | Out-Host
    $mavenArgs = @("-Dcinebook.it.config=$configPath", "-Dcinebook.it.database=$databaseName")
    if ([string]::IsNullOrWhiteSpace($IntegrationTest)) {
        $mavenArgs += 'verify'
    } else {
        $mavenArgs += "-Dit.test=$IntegrationTest"
        # Direct failsafe goals do not enter Maven's test-compile phase. Compile
        # first so a targeted run can never execute stale integration classes.
        $mavenArgs += 'test-compile'
        $mavenArgs += 'failsafe:integration-test'
        $mavenArgs += 'failsafe:verify'
    }
    & $Maven @mavenArgs
    if ($LASTEXITCODE -ne 0) { throw "Maven verification failed (exit $LASTEXITCODE)." }
} finally {
    if ($databaseName -match '^CineBookIT_[0-9]{14}_[0-9a-fA-F]{8}$') {
        & $sqlCmd -S $Server -U $User -P $Password -C -I -b -d master -Q `
            "IF DB_ID(N'$databaseName') IS NOT NULL BEGIN ALTER DATABASE [$databaseName] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$databaseName]; END"
    }
    Remove-Item -LiteralPath $configPath -ErrorAction SilentlyContinue
}

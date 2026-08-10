<#
.SYNOPSIS
    Repeatable two-stage release gate on one ephemeral CineBookIT_* database.

.DESCRIPTION
    Run Offline first. Deploy/start the freshly built WAR and Next output against
    the database recorded by Offline, then run Live. Live cleans it in finally.

.EXAMPLE
    powershell -File scripts\release-gate.ps1 -Stage Offline
    powershell -File scripts\release-gate.ps1 -Stage Live -TestPassword '<fixture password>'
#>
[CmdletBinding()]
param(
    [ValidateSet('Offline', 'Live')]
    [string]$Stage = 'Offline',
    [string]$BaseUrl = 'http://localhost:8080/Website-ban-ve-xem-phim',
    [string]$NextBaseUrl = 'http://localhost:3000',
    [string]$MemberEmail = 'member_bronze@test.com',
    [string]$AdminEmail = 'admin@test.com',
    [string]$TestPassword = $env:CINEBOOK_TEST_PASSWORD,
    [string]$TomcatBase = 'D:\App-download\CODE\tomcat9\apache-tomcat-9.0.117-windows-x64\apache-tomcat-9.0.117'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$markerDir = Join-Path $root 'target\release-gate'
$offlineMarker = Join-Path $markerDir 'offline.json'
$liveMarker = Join-Path $markerDir 'live.json'
$invariantEvidence = Join-Path $root 'target\invariants\booking-invariants.json'
$testDbConfig = [IO.Path]::GetFullPath((Join-Path $root 'target\db.it.properties'))
$script:testDatabaseName = $null
$warArtifact = Join-Path $root 'target\Website-ban-ve-xem-phim-1.0-SNAPSHOT.war'
$explodedArtifact = Join-Path $root 'target\Website-ban-ve-xem-phim-1.0-SNAPSHOT'
$nextArtifact = Join-Path $root 'web\.next'
$expectedUploadDir = [IO.Path]::GetFullPath((Join-Path $TomcatBase 'cinebook-uploads'))

function Invoke-Gate {
    param([string]$Name, [scriptblock]$Action)
    Write-Host ''
    Write-Host "=== $Name ===" -ForegroundColor Cyan
    & $Action
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE"
    }
}

function Get-ManifestSha256 {
    param([string[]]$Manifest)

    $bytes = [Text.Encoding]::UTF8.GetBytes(($Manifest -join [Environment]::NewLine))
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Test-IsExcludedPath {
    param([string]$RelativePath, [string[]]$Prefixes)

    foreach ($prefix in $Prefixes) {
        if ($RelativePath.Equals($prefix, [StringComparison]::OrdinalIgnoreCase) -or
                $RelativePath.StartsWith($prefix + '\', [StringComparison]::OrdinalIgnoreCase)) {
            return $true
        }
    }
    return $false
}

function Get-DirectoryFingerprint {
    param([string]$Path, [string[]]$ExcludedRelativePaths = @())

    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        throw "Artifact directory is missing: $Path"
    }
    $resolvedRoot = [IO.Path]::GetFullPath($Path).TrimEnd('\')
    $manifest = Get-ChildItem -LiteralPath $resolvedRoot -File -Recurse | ForEach-Object {
        $relative = $_.FullName.Substring($resolvedRoot.Length).TrimStart('\')
        if (-not (Test-IsExcludedPath -RelativePath $relative -Prefixes $ExcludedRelativePaths)) {
            $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            "$relative=$hash"
        }
    } | Sort-Object
    return Get-ManifestSha256 -Manifest @($manifest)
}

function Clear-ReleaseMarkers {
    $targetRoot = [IO.Path]::GetFullPath((Join-Path $root 'target')).TrimEnd('\')
    $resolvedMarkerDir = [IO.Path]::GetFullPath($markerDir)
    if (-not $resolvedMarkerDir.StartsWith(
            $targetRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw "Unsafe release marker directory: $resolvedMarkerDir"
    }
    foreach ($marker in @($offlineMarker, $liveMarker)) {
        if (Test-Path -LiteralPath $marker -PathType Leaf) {
            Remove-Item -LiteralPath $marker -Force
        }
    }
}

function Get-SourceFingerprint {
    $inputs = @(
        'pom.xml', 'checkstyle.xml',
        'db.test.properties.example', 'db.properties.example',
        'src', 'database', 'scripts', 'web'
    )
    $generatedPrefixes = @(
        'web\.next', 'web\node_modules', 'web\coverage', 'web\out',
        'web\build', 'web\.vercel', 'web\playwright-report', 'web\test-results'
    )
    $files = foreach ($input in $inputs) {
        $path = Join-Path $root $input
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            Get-Item -LiteralPath $path
        } elseif (Test-Path -LiteralPath $path -PathType Container) {
            Get-ChildItem -LiteralPath $path -File -Recurse
        }
    }
    $manifest = $files | ForEach-Object {
        $relative = $_.FullName.Substring($root.Length).TrimStart('\')
        if (-not (Test-IsExcludedPath -RelativePath $relative -Prefixes $generatedPrefixes)) {
            $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            "$relative=$hash"
        }
    } | Sort-Object
    return Get-ManifestSha256 -Manifest @($manifest)
}

function Read-PropertiesFile {
    param([string]$Path)

    $properties = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -le 0) { throw "Invalid property in ${Path}: $trimmed" }
        $properties[$trimmed.Substring(0, $separator).Trim()] =
                $trimmed.Substring($separator + 1).Trim()
    }
    return $properties
}

function Assert-TestDatabaseConfiguration {
    param([string]$ConfigPath, [switch]$VerifyConnection)

    $resolvedConfig = [IO.Path]::GetFullPath($ConfigPath)
    if (-not [string]::Equals(
            $resolvedConfig, $testDbConfig, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Release gate requires the generated runtime config: $testDbConfig"
    }
    if (-not (Test-Path -LiteralPath $resolvedConfig -PathType Leaf)) {
        throw "Missing test database config: $resolvedConfig"
    }
    $properties = Read-PropertiesFile -Path $resolvedConfig
    $url = [string]$properties['db.url']
    if ($url -notmatch '(?i)(?:^|;)databaseName=([^;]+)') {
        throw 'Runtime integration properties are missing databaseName.'
    }
    $databaseName = $Matches[1]
    if ($databaseName -notmatch '^CineBookIT_[0-9]{14}_[0-9a-fA-F]{8}$') {
        throw "Test database guard expected CineBookIT_<timestamp>_<random> but config targets $databaseName."
    }
    if ($url -notmatch '(?i)^jdbc:sqlserver://([^;]+)') {
        throw 'Runtime integration properties have an unsupported SQL Server URL.'
    }
    $serverAuthority = $Matches[1]
    $serverHost = ($serverAuthority -split '[:\\]')[0].ToLowerInvariant()
    if (@('localhost', '127.0.0.1', '::1') -notcontains $serverHost) {
        throw "Test database must be loopback; config targets $serverAuthority."
    }

    if (-not $VerifyConnection) { return }

    $sqlCmdPath = 'C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\sqlcmd.exe'
    if (-not (Test-Path -LiteralPath $sqlCmdPath -PathType Leaf)) {
        $sqlCmd = Get-Command sqlcmd -ErrorAction SilentlyContinue
        if (-not $sqlCmd) { throw 'sqlcmd is required to verify DB_NAME().' }
        $sqlCmdPath = $sqlCmd.Source
    }
    $username = [string]$properties['db.username']
    $password = [string]$properties['db.password']
    if ([string]::IsNullOrWhiteSpace($username) -or [string]::IsNullOrWhiteSpace($password)) {
        throw 'Runtime integration properties must provide db.username and db.password.'
    }
    $sqlServer = $serverAuthority
    if ($sqlServer -match '^(.+):(\d+)$') {
        $sqlServer = "$($Matches[1]),$($Matches[2])"
    }

    $hadSqlCmdPassword = Test-Path Env:SQLCMDPASSWORD
    $oldSqlCmdPassword = $env:SQLCMDPASSWORD
    try {
        $env:SQLCMDPASSWORD = $password
        $queryOutput = & $sqlCmdPath -S $sqlServer -U $username -C -I -b -h -1 -W `
                -d $databaseName -Q 'SET NOCOUNT ON; SELECT DB_NAME();' 2>&1
        $queryExitCode = $LASTEXITCODE
    } finally {
        if ($hadSqlCmdPassword) {
            $env:SQLCMDPASSWORD = $oldSqlCmdPassword
        } else {
            Remove-Item Env:SQLCMDPASSWORD -ErrorAction SilentlyContinue
        }
    }
    if ($queryExitCode -ne 0) {
        throw "DB_NAME() probe failed with exit code $queryExitCode."
    }
    $databaseLines = @($queryOutput | ForEach-Object { "$($_)".Trim() } |
            Where-Object { $_ -ne '' })
    if ($databaseLines.Count -ne 1 -or $databaseLines[0] -cne $databaseName) {
        throw "DB_NAME() probe expected $databaseName; received: $($databaseLines -join ', ')"
    }
    $script:testDatabaseName = $databaseName
}

function Assert-LoopbackUrl {
    param([string]$Url, [string]$Name)

    try { $uri = [Uri]$Url } catch { throw "$Name is not a valid absolute URL: $Url" }
    if (-not $uri.IsAbsoluteUri -or @('http', 'https') -notcontains $uri.Scheme.ToLowerInvariant()) {
        throw "$Name must be an absolute HTTP(S) URL."
    }
    if (@('localhost', '127.0.0.1', '::1') -notcontains $uri.Host.ToLowerInvariant()) {
        throw "$Name must use a loopback host; refusing target $Url."
    }
}

function Remove-EphemeralTestDatabase {
    if (-not (Test-Path -LiteralPath $testDbConfig -PathType Leaf)) { return }
    $properties = Read-PropertiesFile -Path $testDbConfig
    $url = [string]$properties['db.url']
    if ($url -notmatch '(?i)^jdbc:sqlserver://([^;]+).*;databaseName=([^;]+)') { return }
    $serverAuthority = $Matches[1]
    $databaseName = $Matches[2]
    $serverHost = ($serverAuthority -split '[:\\]')[0].ToLowerInvariant()
    if (@('localhost','127.0.0.1','::1') -notcontains $serverHost -or
            $databaseName -notmatch '^CineBookIT_[0-9]{14}_[0-9a-fA-F]{8}$') {
        throw "Refusing unsafe integration database cleanup: $serverAuthority / $databaseName"
    }
    $sqlCmdPath = 'C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\sqlcmd.exe'
    if (-not (Test-Path -LiteralPath $sqlCmdPath)) { $sqlCmdPath = (Get-Command sqlcmd).Source }
    $sqlServer = $serverAuthority
    if ($sqlServer -match '^(.+):(\d+)$') { $sqlServer = "$($Matches[1]),$($Matches[2])" }
    $oldPassword = $env:SQLCMDPASSWORD
    try {
        $env:SQLCMDPASSWORD = [string]$properties['db.password']
        & $sqlCmdPath -S $sqlServer -U ([string]$properties['db.username']) -C -I -b -d master -Q `
            "IF DB_ID(N'$databaseName') IS NOT NULL BEGIN ALTER DATABASE [$databaseName] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$databaseName]; END"
        if ($LASTEXITCODE -ne 0) { throw "Could not clean integration database $databaseName." }
    } finally {
        if ($null -eq $oldPassword) { Remove-Item Env:SQLCMDPASSWORD -ErrorAction SilentlyContinue }
        else { $env:SQLCMDPASSWORD = $oldPassword }
    }
}

function Assert-LiveTomcatUsesTestDatabase {
    Assert-TestDatabaseConfiguration -ConfigPath $testDbConfig -VerifyConnection

    $baseUri = [Uri]$BaseUrl
    $processIds = @(Get-NetTCPConnection -State Listen -LocalPort $baseUri.Port |
            Select-Object -ExpandProperty OwningProcess -Unique)
    if ($processIds.Count -ne 1) {
        throw "Expected exactly one process listening on Tomcat port $($baseUri.Port); found $($processIds.Count)."
    }
    $tomcatProcessId = [int]$processIds[0]
    $tomcatProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $tomcatProcessId"
    if (-not $tomcatProcess -or [string]::IsNullOrWhiteSpace($tomcatProcess.CommandLine)) {
        throw "Cannot inspect the JVM listening on Tomcat port $($baseUri.Port)."
    }
    $commandLine = [string]$tomcatProcess.CommandLine
    if ($commandLine -notmatch '(?i)org\.apache\.catalina\.startup\.Bootstrap') {
        throw "Port $($baseUri.Port) is not owned by a Tomcat Bootstrap JVM."
    }
    $configMatch = [regex]::Match(
            $commandLine,
            '(?i)-Dcinebook\.db\.config=(?:"([^"]+)"|([^\s"]+))')
    if (-not $configMatch.Success) {
        throw 'Tomcat JVM is missing -Dcinebook.db.config.'
    }
    $actualConfig = $configMatch.Groups[1].Value
    if ([string]::IsNullOrWhiteSpace($actualConfig)) {
        $actualConfig = $configMatch.Groups[2].Value
    }
    $resolvedActualConfig = [IO.Path]::GetFullPath($actualConfig)
    if (-not [string]::Equals(
            $resolvedActualConfig, $testDbConfig, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Tomcat JVM targets $resolvedActualConfig instead of $testDbConfig."
    }
    $resolvedTomcatBase = [IO.Path]::GetFullPath($TomcatBase)
    $catalinaBaseMatch = [regex]::Match(
            $commandLine,
            '(?i)-Dcatalina\.base=(?:"([^"]+)"|([^\s"]+))')
    if (-not $catalinaBaseMatch.Success) {
        throw 'Tomcat JVM is missing -Dcatalina.base.'
    }
    $actualTomcatBase = $catalinaBaseMatch.Groups[1].Value
    if ([string]::IsNullOrWhiteSpace($actualTomcatBase)) {
        $actualTomcatBase = $catalinaBaseMatch.Groups[2].Value
    }
    $resolvedActualTomcatBase = [IO.Path]::GetFullPath($actualTomcatBase)
    if (-not [string]::Equals(
            $resolvedActualTomcatBase, $resolvedTomcatBase,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw "Tomcat JVM uses $resolvedActualTomcatBase instead of $resolvedTomcatBase."
    }
    $uploadMatch = [regex]::Match(
            $commandLine,
            '(?i)-Dcinebook\.upload\.dir=(?:"([^"]+)"|([^\s"]+))')
    if (-not $uploadMatch.Success) {
        throw 'Tomcat JVM is missing -Dcinebook.upload.dir=<expected>.'
    }
    $actualUploadDir = $uploadMatch.Groups[1].Value
    if ([string]::IsNullOrWhiteSpace($actualUploadDir)) {
        $actualUploadDir = $uploadMatch.Groups[2].Value
    }
    $resolvedUploadDir = [IO.Path]::GetFullPath($actualUploadDir)
    if (-not [string]::Equals(
            $resolvedUploadDir, $expectedUploadDir, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Tomcat JVM upload dir is $resolvedUploadDir instead of $expectedUploadDir."
    }
}

function Assert-HttpOk {
    param([string]$Url, [string]$Name)
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 20
    } catch {
        throw "$Name is not ready at $Url. $($_.Exception.Message)"
    }
    if ($response.StatusCode -ne 200) {
        throw "$Name returned HTTP $($response.StatusCode) at $Url"
    }
}

function Assert-OfflineArtifactIdentity {
    param($OfflineEvidence)

    if (-not (Test-Path -LiteralPath $warArtifact -PathType Leaf)) {
        throw "Offline WAR is missing: $warArtifact"
    }
    $warSha256 = (Get-FileHash -LiteralPath $warArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($warSha256 -cne [string]$OfflineEvidence.warSha256) {
        throw 'WAR identity differs from the successful Offline artifact.'
    }
    $explodedSha256 = Get-DirectoryFingerprint -Path $explodedArtifact
    if ($explodedSha256 -cne [string]$OfflineEvidence.explodedSha256) {
        throw 'Exploded WAR identity differs from the successful Offline artifact.'
    }

    $nextBuildIdFile = Join-Path $nextArtifact 'BUILD_ID'
    if (-not (Test-Path -LiteralPath $nextBuildIdFile -PathType Leaf)) {
        throw "Next BUILD_ID is missing: $nextBuildIdFile"
    }
    $nextBuildId = (Get-Content -Raw -LiteralPath $nextBuildIdFile).Trim()
    if ($nextBuildId -cne [string]$OfflineEvidence.nextBuildId) {
        throw 'Next BUILD_ID differs from the successful Offline artifact.'
    }
    $nextArtifactSha256 = Get-DirectoryFingerprint -Path $nextArtifact `
            -ExcludedRelativePaths @('cache', 'diagnostics', 'trace', 'trace-build')
    if ($nextArtifactSha256 -cne [string]$OfflineEvidence.nextArtifactSha256) {
        throw 'Next output identity differs from the successful Offline artifact.'
    }

    $baseUri = [Uri]$BaseUrl
    $contextName = $baseUri.AbsolutePath.Trim('/').Split('/')[0]
    if ([string]::IsNullOrWhiteSpace($contextName)) {
        throw 'BaseUrl must include the deployed Tomcat context name.'
    }
    $contextFile = Join-Path $TomcatBase "conf\Catalina\localhost\$contextName.xml"
    if (-not (Test-Path -LiteralPath $contextFile -PathType Leaf)) {
        throw "Tomcat context file is required to prove deployed docBase: $contextFile"
    }
    [xml]$context = Get-Content -Raw -LiteralPath $contextFile
    $docBase = [string]$context.Context.docBase
    if ([string]::IsNullOrWhiteSpace($docBase)) {
        throw "Tomcat context has no docBase: $contextFile"
    }
    if ([IO.Path]::IsPathRooted($docBase)) {
        $deployedDocBase = [IO.Path]::GetFullPath($docBase)
    } else {
        $deployedDocBase = [IO.Path]::GetFullPath((Join-Path $TomcatBase "webapps\$docBase"))
    }
    $expectedDocBase = [IO.Path]::GetFullPath($explodedArtifact)
    if (-not [string]::Equals(
            $deployedDocBase, $expectedDocBase, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Tomcat deploys $deployedDocBase instead of Offline exploded artifact $expectedDocBase."
    }

    $nextManifestUrl = "$($NextBaseUrl.TrimEnd('/'))/_next/static/$nextBuildId/_buildManifest.js"
    Assert-HttpOk $nextManifestUrl 'Offline Next build identity'
}

function Read-MavenTestStats {
    $reports = @('target\surefire-reports', 'target\failsafe-reports') | ForEach-Object {
        $reportDir = Join-Path $root $_
        if (Test-Path -LiteralPath $reportDir) {
            Get-ChildItem $reportDir -Filter 'TEST-*.xml'
        }
    }
    if (@($reports).Count -eq 0) {
        throw 'Maven did not produce Surefire/Failsafe XML reports.'
    }
    $suites = $reports | ForEach-Object { ([xml](Get-Content -Raw $_.FullName)).testsuite }
    return [pscustomobject]@{
        Tests = [int](($suites | Measure-Object -Property tests -Sum).Sum)
        Failures = [int](($suites | Measure-Object -Property failures -Sum).Sum)
        Errors = [int](($suites | Measure-Object -Property errors -Sum).Sum)
        Skipped = [int](($suites | Measure-Object -Property skipped -Sum).Sum)
        Suites = $suites
    }
}

function Invoke-OfflineGates {
    Clear-ReleaseMarkers
    $offlineSucceeded = $false
    Push-Location $root
    try {
        # Maven clean removes target/, including the runtime DB properties. Clean first so the
        # configuration created below remains available to unit tests, integration tests and Live.
        Invoke-Gate 'Clean Java workspace' { & scripts\build.cmd clean }
        Invoke-Gate 'Create ephemeral integration database' {
            & powershell.exe -NoProfile -File scripts\init-test-db.ps1 `
                -OutputProperties target\db.it.properties
        }
        Assert-TestDatabaseConfiguration -ConfigPath $testDbConfig -VerifyConnection
        Invoke-Gate 'Full Java tests' {
            & scripts\build.cmd "-Dcinebook.it.config=$testDbConfig" `
                "-Dcinebook.it.database=$script:testDatabaseName" verify
        }
        Assert-TestDatabaseConfiguration -ConfigPath $testDbConfig -VerifyConnection

        $stats = Read-MavenTestStats
        if ($stats.Failures -ne 0 -or $stats.Errors -ne 0) {
            throw "Java tests contain $($stats.Failures) failures and $($stats.Errors) errors."
        }
        $unexpectedSkipped = @($stats.Suites | Where-Object {
            [int]$_.skipped -gt 0 -and $_.name -notlike '*SessionFixationIT'
        })
        if ($unexpectedSkipped.Count -gt 0 -or $stats.Skipped -ne 4) {
            throw "Offline stage expects only four SessionFixationIT skips; found $($stats.Skipped)."
        }
        if (-not (Test-Path 'target\qr-browser-fixture.png')) {
            throw 'Full tests did not generate target\qr-browser-fixture.png.'
        }

        Invoke-Gate 'Booking invariant gate' {
            & powershell.exe -NoProfile -File scripts\booking-invariant-gate.ps1 -ConfigPath $testDbConfig
        }
        $invariantOffline = Get-Content -Raw -LiteralPath $invariantEvidence | ConvertFrom-Json
        if ([int64]$invariantOffline.violations -ne 0 -or [int64]$invariantOffline.outboxPending -ne 0) {
            throw 'Offline invariant evidence is not clean.'
        }

        Invoke-Gate 'Blocking Checkstyle' { & scripts\build.cmd checkstyle:check }
        Invoke-Gate 'WAR package' { & scripts\build.cmd package -DskipTests }

        $jspc = Join-Path $root 'target\jspc-release'
        $targetRoot = [IO.Path]::GetFullPath((Join-Path $root 'target'))
        $resolvedJspc = [IO.Path]::GetFullPath($jspc)
        if (-not $resolvedJspc.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar)) {
            throw "Unsafe JSPC output path: $resolvedJspc"
        }
        if (Test-Path -LiteralPath $resolvedJspc) {
            Remove-Item -LiteralPath $resolvedJspc -Recurse -Force
        }
        New-Item -ItemType Directory -Path $resolvedJspc | Out-Null
        $ant = 'C:\Program Files\NetBeans-25\netbeans\extide\ant\lib\ant.jar'
        $java = 'C:\Program Files\Java\jdk-25\bin\java.exe'
        $warDir = Join-Path $root 'target\Website-ban-ve-xem-phim-1.0-SNAPSHOT'
        $classpath = "$TomcatBase\lib\*;$TomcatBase\bin\tomcat-juli.jar;$ant;" +
                "target\classes;$warDir\WEB-INF\lib\*"
        Invoke-Gate 'JSP compiler' {
            & $java -cp $classpath org.apache.jasper.JspC -webapp src\main\webapp -d $resolvedJspc -p cinebook.jsp
        }

        Push-Location web
        try {
            Invoke-Gate 'npm ci' { & npm.cmd ci --no-audit --no-fund }
            Invoke-Gate 'Next unit tests' { & npm.cmd run test:unit }
            Invoke-Gate 'Next lint' { & npm.cmd run lint }
            Invoke-Gate 'Next production build' { & npm.cmd run build }
            Invoke-Gate 'Production dependency audit' { & npm.cmd audit --omit=dev }
            $tree = & npm.cmd ls next postcss sharp playwright-core --all 2>&1
            $treeText = $tree -join [Environment]::NewLine
            if ($LASTEXITCODE -ne 0) {
                throw ("npm dependency tree is invalid." + [Environment]::NewLine + $treeText)
            }
            if ($treeText -notmatch 'postcss@8\.5\.25' -or $treeText -notmatch 'sharp@0\.35\.3') {
                throw ("Dependency overrides did not resolve to the approved versions." +
                        [Environment]::NewLine + $treeText)
            }
        } finally {
            Pop-Location
        }

        if (-not (Test-Path -LiteralPath $warArtifact -PathType Leaf)) {
            throw "WAR package did not create $warArtifact"
        }
        if (-not (Test-Path -LiteralPath $explodedArtifact -PathType Container)) {
            throw "WAR package did not create $explodedArtifact"
        }
        $nextBuildIdFile = Join-Path $nextArtifact 'BUILD_ID'
        if (-not (Test-Path -LiteralPath $nextBuildIdFile -PathType Leaf)) {
            throw "Next build did not create $nextBuildIdFile"
        }
        $nextBuildId = (Get-Content -Raw -LiteralPath $nextBuildIdFile).Trim()

        New-Item -ItemType Directory -Path $markerDir -Force | Out-Null
        $marker = [ordered]@{
            stage = 'offline'
            passedAt = (Get-Date).ToString('o')
            runId = [guid]::NewGuid().ToString('D')
            fingerprint = Get-SourceFingerprint
            warSha256 = (Get-FileHash -LiteralPath $warArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
            explodedSha256 = Get-DirectoryFingerprint -Path $explodedArtifact
            nextBuildId = $nextBuildId
            nextArtifactSha256 = Get-DirectoryFingerprint -Path $nextArtifact `
                    -ExcludedRelativePaths @('cache', 'diagnostics', 'trace', 'trace-build')
            javaTests = $stats.Tests
            expectedLiveTests = 4
            postcss = '8.5.25'
            sharp = '0.35.3'
            stateContractVersion = $invariantOffline.stateContractVersion
            invariantCount = $invariantOffline.invariantCount
            invariantViolations = $invariantOffline.violations
            outboxPending = $invariantOffline.outboxPending
            database = $script:testDatabaseName
        }
        $marker | ConvertTo-Json | Set-Content -LiteralPath $offlineMarker -Encoding UTF8
        $offlineSucceeded = $true
        Write-Host "Offline release gates passed. Marker: $offlineMarker" -ForegroundColor Green
    } finally {
        Pop-Location
        if (-not $offlineSucceeded) { Remove-EphemeralTestDatabase }
    }
}

function Invoke-LiveGates {
    Assert-LoopbackUrl -Url $BaseUrl -Name 'BaseUrl'
    Assert-LoopbackUrl -Url $NextBaseUrl -Name 'NextBaseUrl'
    if (Test-Path -LiteralPath $liveMarker -PathType Leaf) {
        Remove-Item -LiteralPath $liveMarker -Force
    }
    if ([string]::IsNullOrWhiteSpace($TestPassword)) {
        throw 'Provide -TestPassword or CINEBOOK_TEST_PASSWORD for seeded test accounts.'
    }
    if (-not (Test-Path -LiteralPath $offlineMarker)) {
        throw 'Missing Offline marker. Run -Stage Offline before deploying and running Live.'
    }
    $offline = Get-Content -Raw -LiteralPath $offlineMarker | ConvertFrom-Json
    if ($offline.stage -cne 'offline') {
        throw 'Offline marker has an invalid stage.'
    }
    $offlineRunGuid = [guid]::Empty
    if (-not [guid]::TryParse([string]$offline.runId, [ref]$offlineRunGuid)) {
        throw 'Offline marker has no valid runId.'
    }
    $offlinePassedAt = [DateTimeOffset]::MinValue
    if (-not [DateTimeOffset]::TryParse(
            [string]$offline.passedAt, [ref]$offlinePassedAt)) {
        throw 'Offline marker has no valid passedAt timestamp.'
    }
    $fingerprint = Get-SourceFingerprint
    if ($offline.fingerprint -ne $fingerprint) {
        throw 'Source changed after Offline gates. Rerun -Stage Offline and redeploy.'
    }
    Assert-LiveTomcatUsesTestDatabase
    if ($offline.database -cne $script:testDatabaseName) {
        throw "Live config targets $script:testDatabaseName but Offline used $($offline.database)."
    }
    Assert-OfflineArtifactIdentity -OfflineEvidence $offline

    Assert-HttpOk "$BaseUrl/api/v1/health" 'Tomcat application'
    Assert-HttpOk "$NextBaseUrl/phim" 'Next application'

    $logName = "logs\localhost_access_log.$(Get-Date -Format 'yyyy-MM-dd').txt"
    $accessLog = Join-Path $TomcatBase $logName
    if (-not (Test-Path -LiteralPath $accessLog)) {
        throw "Tomcat access log is required for the HTTP 500 gate: $accessLog"
    }
    $beforeLines = @(Get-Content -LiteralPath $accessLog).Count

    Push-Location $root
    $oldPassword = $env:CINEBOOK_TEST_PASSWORD
    $env:CINEBOOK_TEST_PASSWORD = $TestPassword
    try {
        $sessionArgs = @(
            '-Dit.test=SessionFixationIT',
            "-Dcinebook.it.config=$testDbConfig",
            "-Dcinebook.it.database=$script:testDatabaseName",
            "-Dcinebook.it.baseUrl=$BaseUrl",
            "-Dcinebook.it.email=$MemberEmail",
            "-Dcinebook.it.password=$TestPassword",
            'failsafe:integration-test',
            'failsafe:verify'
        )
        Invoke-Gate 'Live SessionFixationIT' { & scripts\build.cmd @sessionArgs }
        $sessionReport = Get-ChildItem 'target\failsafe-reports' -Filter 'TEST-*SessionFixationIT.xml' |
                Select-Object -First 1
        if (-not $sessionReport) { throw 'SessionFixationIT report is missing.' }
        $session = ([xml](Get-Content -Raw $sessionReport.FullName)).testsuite
        if ([int]$session.tests -ne 4 -or [int]$session.failures -ne 0 -or
                [int]$session.errors -ne 0 -or [int]$session.skipped -ne 0) {
            throw 'SessionFixationIT must execute 4/4 with zero failure, error or skip.'
        }

        $routeArgs = @(
            '-NoProfile', '-File', 'scripts\route-check.ps1',
            '-BaseUrl', $BaseUrl, '-AdminEmail', $AdminEmail, '-AdminPassword', $TestPassword
        )
        Invoke-Gate 'Route manifest 41/41' { & powershell.exe @routeArgs }
        Invoke-Gate 'CSRF sweep 34/34' {
            & cmd.exe /c scripts\csrf-sweep.bat $BaseUrl $AdminEmail $TestPassword
        }
        $browserFixtureAttempted = $true
        try {
            Invoke-Gate 'Prepare browser TOO_EARLY ticket' {
                & powershell.exe -NoProfile -File scripts\prepare-browser-smoke-ticket.ps1 -Mode Prepare -PropertiesPath $testDbConfig
            }
            Push-Location web
            try {
                $env:CINEBOOK_JAVA_BASE = $BaseUrl
                $env:CINEBOOK_NEXT_BASE = $NextBaseUrl
                $env:CINEBOOK_TOO_EARLY_TICKET = 'CBROWSERTOOEARLY20260801'
                $env:CINEBOOK_CANCEL_TICKET = 'CBROWSERCANCEL20260801'
                $env:CINEBOOK_UPLOAD_DIR = $expectedUploadDir
                Invoke-Gate 'Browser console and role smoke' { & npm.cmd run test:browser }
            } finally {
                Remove-Item Env:CINEBOOK_JAVA_BASE -ErrorAction SilentlyContinue
                Remove-Item Env:CINEBOOK_NEXT_BASE -ErrorAction SilentlyContinue
                Remove-Item Env:CINEBOOK_TOO_EARLY_TICKET -ErrorAction SilentlyContinue
                Remove-Item Env:CINEBOOK_CANCEL_TICKET -ErrorAction SilentlyContinue
                Remove-Item Env:CINEBOOK_UPLOAD_DIR -ErrorAction SilentlyContinue
                Pop-Location
            }
        } finally {
            if ($browserFixtureAttempted) {
                & powershell.exe -NoProfile -File scripts\prepare-browser-smoke-ticket.ps1 -Mode Cleanup -PropertiesPath $testDbConfig
                if ($LASTEXITCODE -ne 0) {
                    throw "Browser ticket cleanup failed with exit code $LASTEXITCODE."
                }
            }
        }

        Invoke-Gate 'Live booking invariant gate' {
            & powershell.exe -NoProfile -File scripts\booking-invariant-gate.ps1 -ConfigPath $testDbConfig
        }
        $invariantLive = Get-Content -Raw -LiteralPath $invariantEvidence | ConvertFrom-Json
        if ([int64]$invariantLive.violations -ne 0 -or [int64]$invariantLive.outboxPending -ne 0) {
            throw 'Live invariant evidence is not clean.'
        }

        $allLines = @(Get-Content -LiteralPath $accessLog)
        $newLines = if ($allLines.Count -gt $beforeLines) {
            $allLines[$beforeLines..($allLines.Count - 1)]
        } else {
            @()
        }
        $http500 = @($newLines | Where-Object { $_ -match '\s500\s' })
        if ($http500.Count -gt 0) {
            throw ("HTTP 500 gate failed:" + [Environment]::NewLine +
                    ($http500 -join [Environment]::NewLine))
        }

        if ((Get-SourceFingerprint) -cne $fingerprint) {
            throw 'Source changed during Live gates. Rerun Offline and redeploy.'
        }
        Assert-OfflineArtifactIdentity -OfflineEvidence $offline
        $livePassedAt = [DateTimeOffset]::Now
        if ($livePassedAt -le $offlinePassedAt) {
            throw 'Live evidence must be newer than its Offline evidence.'
        }

        $marker = [ordered]@{
            stage = 'live'
            passedAt = $livePassedAt.ToString('o')
            offlineRunId = $offline.runId
            fingerprint = $fingerprint
            warSha256 = $offline.warSha256
            explodedSha256 = $offline.explodedSha256
            nextBuildId = $offline.nextBuildId
            nextArtifactSha256 = $offline.nextArtifactSha256
            sessionTests = 4
            routes = 41
            csrf = 34
            http500 = 0
            browserRoles = @('public', 'member', 'staff', 'manager', 'admin', 'next')
            stateContractVersion = $invariantLive.stateContractVersion
            invariantCount = $invariantLive.invariantCount
            invariantViolations = $invariantLive.violations
            outboxPending = $invariantLive.outboxPending
        }
        $marker | ConvertTo-Json | Set-Content -LiteralPath $liveMarker -Encoding UTF8
        Write-Host "Live release gates passed. Marker: $liveMarker" -ForegroundColor Green
    } finally {
        $env:CINEBOOK_TEST_PASSWORD = $oldPassword
        Pop-Location
        Remove-EphemeralTestDatabase
    }
}

if ($Stage -eq 'Offline') {
    Invoke-OfflineGates
} else {
    Invoke-LiveGates
}

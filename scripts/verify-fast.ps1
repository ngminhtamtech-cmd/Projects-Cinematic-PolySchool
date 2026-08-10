$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$mavenCommand = Get-Command mvn -ErrorAction SilentlyContinue
if ($mavenCommand) {
    $maven = $mavenCommand.Source
} else {
    $mavenCandidates = @(
        "C:\Program Files\NetBeans-25\netbeans\java\maven\bin\mvn.cmd",
        "C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"
    )
    $maven = $mavenCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $maven) {
    throw "Maven was not found. Install Maven or add mvn to PATH."
}

Push-Location $root
try {
    Write-Host "Running backend unit tests..."
    & $maven --batch-mode test
    if ($LASTEXITCODE -ne 0) { throw "Backend unit tests failed (exit $LASTEXITCODE)" }

    Write-Host "Running backend style and package verification..."
    & $maven --batch-mode checkstyle:check -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Checkstyle failed (exit $LASTEXITCODE)" }
    & $maven --batch-mode verify -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Backend verification failed (exit $LASTEXITCODE)" }

    Push-Location (Join-Path $root "web")
    try {
        Write-Host "Installing locked frontend dependencies..."
        & npm ci
        if ($LASTEXITCODE -ne 0) { throw "npm ci failed (exit $LASTEXITCODE)" }

        Write-Host "Running frontend unit tests..."
        & npm run test:unit
        if ($LASTEXITCODE -ne 0) { throw "Frontend unit tests failed (exit $LASTEXITCODE)" }

        Write-Host "Building the Next.js frontend..."
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw "Frontend build failed (exit $LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
} finally {
    Pop-Location
}

Write-Host "Fast verification completed successfully."

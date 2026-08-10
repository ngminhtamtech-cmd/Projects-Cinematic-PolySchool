<#
.SYNOPSIS
    Doi chieu ma HTTP that cua tung route voi route-manifest.txt (QA-01).

.DESCRIPTION
    Thay cho route-baseline.bat cu. Ba khac biet quan trong:

      1. Danh sach route lay tu route-manifest.txt (doi chieu voi web.xml), khong phai
         mot chuoi hard-code da loi thoi.
      2. Moi route co MA KY VONG rieng theo trang thai dang nhap. 404 khong bao gio
         duoc coi la dat cho route bat buoc — ban cu chap nhan 404 lam baseline nen
         bao "0 hoi quy" trong khi chuc nang khong ton tai.
      3. Kiem them noi dung: mot so trang tra HTTP 200 nhung than trang lai la trang
         loi 500 (HTTP-01). Script danh dau cac truong hop do la HONG.

.PARAMETER BaseUrl
    Goc ung dung. Mac dinh http://localhost:8080/Website-ban-ve-xem-phim

.PARAMETER AdminEmail / AdminPassword
    Neu cung cap, script dang nhap va kiem them cot ky vong danh cho admin.

.EXAMPLE
    powershell -File scripts\route-check.ps1
    powershell -File scripts\route-check.ps1 -AdminEmail admin@cinebook.local -AdminPassword '...'
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080/Website-ban-ve-xem-phim',
    [string]$ManifestPath,
    [string]$AdminEmail,
    [string]$AdminPassword
)

$ErrorActionPreference = 'Stop'
$ExpectedRouteCount = 41

$hasAdminEmail = -not [string]::IsNullOrWhiteSpace($AdminEmail)
$hasAdminPassword = -not [string]::IsNullOrWhiteSpace($AdminPassword)
if ($hasAdminEmail -xor $hasAdminPassword) {
    throw 'Admin credentials were supplied incompletely; provide both email and password.'
}
$adminCredentialsSupplied = $hasAdminEmail -and $hasAdminPassword

# Giai gia tri o day chu khong o phan param: Windows PowerShell 5.1 chua gan $PSScriptRoot
# tai thoi diem binding tham so mac dinh, nen duong dan se thanh "\route-manifest.txt".
if (-not $ManifestPath) {
    $ManifestPath = Join-Path $PSScriptRoot 'route-manifest.txt'
}

if (-not (Test-Path $ManifestPath)) {
    Write-Error "Khong tim thay manifest: $ManifestPath"
    exit 2
}

# --- Doc manifest ---------------------------------------------------------
$routes = @()
$seenRoutes = @{}
foreach ($line in Get-Content -Path $ManifestPath -Encoding UTF8) {
    $trimmed = $line.Trim()
    if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
    $parts = $trimmed -split '\s+'
    if ($parts.Count -ne 3) {
        throw "Invalid route manifest line: $trimmed"
    }
    if ($parts[0] -notmatch '^/' -or
            $parts[1] -notmatch '^\d{3}(\|\d{3})*$' -or
            $parts[2] -notmatch '^\d{3}(\|\d{3})*$') {
        throw "Invalid route manifest entry: $trimmed"
    }
    $allowedStatuses = @('200', '302', '401', '403')
    $declaredStatuses = @(($parts[1] -split '\|') + ($parts[2] -split '\|'))
    $unsafeStatuses = @($declaredStatuses | Where-Object { $allowedStatuses -notcontains $_ })
    if ($unsafeStatuses.Count -gt 0) {
        throw "Route manifest contains fail-open status codes: $($unsafeStatuses -join '|')"
    }
    $routeKey = $parts[0].ToLowerInvariant()
    if ($seenRoutes.ContainsKey($routeKey)) {
        throw "duplicate route in manifest: $($parts[0])"
    }
    $seenRoutes[$routeKey] = $true
    $routes += [pscustomobject]@{
        Path          = $parts[0]
        ExpectAnon    = $parts[1] -split '\|'
        ExpectAdmin   = $parts[2] -split '\|'
    }
}

if ($routes.Count -ne $ExpectedRouteCount) {
    throw "Route manifest must contain exactly $ExpectedRouteCount entries; found $($routes.Count)."
}

Write-Host "Kiem $($routes.Count) route tren $BaseUrl" -ForegroundColor Cyan

# Dung HttpWebRequest thay cho Invoke-WebRequest.
#
# Ly do: Invoke-WebRequest voi -MaximumRedirection 0 NEM exception cho ca 3xx, va trong
# Windows PowerShell 5.1 doi tuong loi khong phai luc nao cung mang theo Response — ket qua
# la moi route bi chuyen huong deu bao "0" thay vi 302. Chinh cac route can bao ve
# (/admin/*, /profile) deu tra 302, nen cong kiem tra tro nen vo dung.
#
# HttpWebRequest voi AllowAutoRedirect = $false tra ve 3xx nhu mot response binh thuong,
# nen doc duoc ca ma lan than trang mot cach xac dinh.
function Invoke-Route {
    param([string]$Url, [System.Net.CookieContainer]$Cookies)

    $request = [System.Net.HttpWebRequest]::Create($Url)
    $request.Method = 'GET'
    $request.AllowAutoRedirect = $false
    $request.Timeout = 20000
    $request.UserAgent = 'CineBook-route-check'
    if ($Cookies) { $request.CookieContainer = $Cookies }

    $response = $null
    try {
        $response = $request.GetResponse()
    } catch [System.Net.WebException] {
        $response = $_.Exception.Response
        if (-not $response) {
            return [pscustomobject]@{ Status = 0; Body = '' }
        }
    }

    $status = [int]$response.StatusCode
    $body = ''
    try {
        $stream = $response.GetResponseStream()
        if ($stream) {
            $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
            $body = $reader.ReadToEnd()
            $reader.Close()
        }
    } catch { }
    $response.Close()

    return [pscustomobject]@{ Status = $status; Body = $body }
}

# --- Dang nhap admin neu duoc cung cap ------------------------------------
$adminCookies = $null
if ($adminCredentialsSupplied) {
    $adminCookies = New-Object System.Net.CookieContainer

    # Lay CSRF token truoc: CsrfFilter map /* nen POST /login bat buoc phai co token.
    Invoke-Route -Url "$BaseUrl/api/v1/auth/csrf" -Cookies $adminCookies | Out-Null
    $token = ($adminCookies.GetCookies($BaseUrl) | Where-Object { $_.Name -eq 'XSRF-TOKEN' }).Value
    if (-not $token) {
        throw 'Admin credentials were supplied but the CSRF token could not be acquired.'
    } else {
        $form = "email=$([uri]::EscapeDataString($AdminEmail))" +
                "&password=$([uri]::EscapeDataString($AdminPassword))" +
                "&_csrf=$([uri]::EscapeDataString($token))"
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($form)

        $login = [System.Net.HttpWebRequest]::Create("$BaseUrl/login")
        $login.Method = 'POST'
        $login.AllowAutoRedirect = $false
        $login.ContentType = 'application/x-www-form-urlencoded'
        $login.CookieContainer = $adminCookies
        $login.ContentLength = $bytes.Length
        $stream = $login.GetRequestStream()
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Close()

        $loginStatus = 0
        try {
            $resp = $login.GetResponse()
            $loginStatus = [int]$resp.StatusCode
            $resp.Close()
        } catch [System.Net.WebException] {
            if ($_.Exception.Response) { $loginStatus = [int]$_.Exception.Response.StatusCode }
        }

        # 302 = dang nhap thanh cong roi chuyen huong. 200 = quay lai form -> sai thong tin.
        if ($loginStatus -eq 302) {
            Write-Host "Da dang nhap bang $AdminEmail" -ForegroundColor Cyan
        } else {
            throw "Admin login failed with HTTP $loginStatus; refusing an anonymous-only route pass."
        }
    }
}

# --- Kiem tung route ------------------------------------------------------
$failures = @()
foreach ($route in $routes) {
    $url = "$BaseUrl$($route.Path)"

    $anon = Invoke-Route -Url $url
    $anonOk = $route.ExpectAnon -contains "$($anon.Status)"

    # HTTP-01: trang loi duoc forward nhung van tra 200. Kiem ca than trang.
    $looksLikeErrorPage = $anon.Body -match 'data-error-page="500"'
    if ($anon.Status -eq 200 -and $looksLikeErrorPage) {
        $anonOk = $false
        $failures += "$($route.Path) [an danh] HTTP 200 nhung than trang la trang loi 500"
    } elseif (-not $anonOk) {
        $failures += "$($route.Path) [an danh] nhan $($anon.Status), ky vong $($route.ExpectAnon -join '|')"
    }

    $adminNote = ''
    if ($adminCookies) {
        $admin = Invoke-Route -Url $url -Cookies $adminCookies
        $adminOk = $route.ExpectAdmin -contains "$($admin.Status)"
        if ($admin.Status -eq 200 -and ($admin.Body -match 'data-error-page="500"')) {
            $adminOk = $false
            $failures += "$($route.Path) [admin] HTTP 200 nhung than trang la trang loi 500"
        } elseif (-not $adminOk) {
            $failures += "$($route.Path) [admin] nhan $($admin.Status), ky vong $($route.ExpectAdmin -join '|')"
        }
        $adminNote = " | admin=$($admin.Status)"
    }

    $color = if ($anonOk) { 'DarkGray' } else { 'Red' }
    Write-Host ("  {0,-30} anon={1}{2}" -f $route.Path, $anon.Status, $adminNote) -ForegroundColor $color
}

Write-Host ''
if ($failures.Count -eq 0) {
    Write-Host "0 route hoi quy — tat ca khop ma ky vong." -ForegroundColor Green
    exit 0
}
Write-Host "$($failures.Count) route KHONG dat:" -ForegroundColor Red
$failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
exit 1

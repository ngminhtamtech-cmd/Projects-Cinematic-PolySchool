# Gia dinh Chrome headless da chay san voi --remote-debugging-port=9333 (script kia da mo).
$ErrorActionPreference = "Stop"
$base    = "http://localhost:8080/Website-ban-ve-xem-phim"
$outDir  = "C:\Users\DELL\Documents\NetBeansProjects\back-up\back-up\docs\diagrams\screens"
$port    = 9333

function Invoke-Cdp {
    param($Ws, $Method, $Params = @{}, [ref]$MsgId)
    $id = $MsgId.Value
    $MsgId.Value++
    $payload = @{ id = $id; method = $Method; params = $Params } | ConvertTo-Json -Depth 10 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
    $seg = New-Object System.ArraySegment[byte] (,$bytes)
    $Ws.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [System.Threading.CancellationToken]::None).Wait()
    return $id
}

function Read-CdpUntil {
    param($Ws, $TargetId, $TimeoutSec = 15)
    $buffer = New-Object byte[] 4194304
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        $ms = New-Object System.IO.MemoryStream
        do {
            $seg = New-Object System.ArraySegment[byte] (,$buffer)
            $task = $Ws.ReceiveAsync($seg, [System.Threading.CancellationToken]::None)
            if (-not $task.Wait(3000)) { break }
            $result = $task.Result
            $ms.Write($buffer, 0, $result.Count)
        } while (-not $result.EndOfMessage)
        if ($ms.Length -eq 0) { continue }
        $text = [System.Text.Encoding]::UTF8.GetString($ms.ToArray())
        try { $obj = $text | ConvertFrom-Json } catch { continue }
        if ($obj.id -eq $TargetId) { return $obj }
    }
    throw "Timeout cho cdp id=$TargetId"
}

function Get-Session($email, $pass) {
    $s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $page = Invoke-WebRequest "$base/login" -WebSession $s -UseBasicParsing
    $csrf = ([regex]'name="_csrf"\s+value="([^"]+)"').Match($page.Content).Groups[1].Value
    Invoke-WebRequest "$base/login" -Method POST -WebSession $s -UseBasicParsing `
        -Body @{ email=$email; password=$pass; _csrf=$csrf } | Out-Null
    return ($s.Cookies.GetCookies($base) | Where-Object Name -eq "JSESSIONID").Value
}

$targetInfo = Invoke-RestMethod "http://localhost:$port/json/new?about:blank" -Method Put
$wsUrl = $targetInfo.webSocketDebuggerUrl
Write-Host "WS: $wsUrl"

$ws = New-Object System.Net.WebSockets.ClientWebSocket
$ws.ConnectAsync([Uri]$wsUrl, [System.Threading.CancellationToken]::None).Wait()
$msgId = 0

Invoke-Cdp -Ws $ws -Method "Network.enable" -MsgId ([ref]$msgId) | Out-Null
Read-CdpUntil -Ws $ws -TargetId ($msgId - 1) | Out-Null
Invoke-Cdp -Ws $ws -Method "Page.enable" -MsgId ([ref]$msgId) | Out-Null
Read-CdpUntil -Ws $ws -TargetId ($msgId - 1) | Out-Null

$accounts = @(
  @{ role="admin";  email="admin@cinebook.local";       pass="123456" },
  @{ role="member"; email="khanh.linh@cinebook.local";  pass="123456" },
  @{ role="staff";  email="staff01@cinebook.local";     pass="123456" }
)
$shots = @{
  admin  = @( @{f="fig06_films"   ; u="/admin/films";     w=1600; h=1600},
              @{f="fig07_reports" ; u="/admin/reports";  w=1600; h=2000},
              @{f="fig11_admin_dashboard" ; u="/admin/dashboard"; w=1600; h=1800} )
  member = @( @{f="fig09_orders_history"   ; u="/orders/history";  w=1600; h=1400},
              @{f="fig08_booking" ; u="/booking?showtimeId=20"; w=1600; h=1800} )
  staff  = @( @{f="fig10_staff_checkin"   ; u="/staff/checkin";  w=1600; h=1400} )
}

foreach ($acc in $accounts) {
    $jsession = Get-Session -email $acc.email -pass $acc.pass
    if (-not $jsession) { Write-Warning "Dang nhap that bai: $($acc.role)"; continue }
    Write-Host "Dang nhap $($acc.role) OK, JSESSIONID=$jsession"

    $cookieParams = @{ name="JSESSIONID"; value=$jsession; domain="localhost"; path="/Website-ban-ve-xem-phim"; httpOnly=$true }
    $cid = Invoke-Cdp -Ws $ws -Method "Network.setCookie" -Params $cookieParams -MsgId ([ref]$msgId)
    $cres = Read-CdpUntil -Ws $ws -TargetId $cid
    Write-Host "setCookie result: $($cres.result.success)"

    foreach ($shot in $shots[$acc.role]) {
        Invoke-Cdp -Ws $ws -Method "Emulation.setDeviceMetricsOverride" -Params @{ width=$shot.w; height=$shot.h; deviceScaleFactor=1; mobile=$false } -MsgId ([ref]$msgId) | Out-Null
        Read-CdpUntil -Ws $ws -TargetId ($msgId - 1) | Out-Null

        $navId = Invoke-Cdp -Ws $ws -Method "Page.navigate" -Params @{ url = "$base$($shot.u)" } -MsgId ([ref]$msgId)
        Read-CdpUntil -Ws $ws -TargetId $navId -TimeoutSec 20 | Out-Null
        Start-Sleep -Seconds 3

        $shotId = Invoke-Cdp -Ws $ws -Method "Page.captureScreenshot" -Params @{ format="png"; captureBeyondViewport=$true; clip=@{x=0;y=0;width=$shot.w;height=$shot.h;scale=1} } -MsgId ([ref]$msgId)
        $resp = Read-CdpUntil -Ws $ws -TargetId $shotId -TimeoutSec 20
        if ($resp.result.data) {
            $bytes = [Convert]::FromBase64String($resp.result.data)
            $dest = Join-Path $outDir ("$($shot.f).png")
            [IO.File]::WriteAllBytes($dest, $bytes)
            Write-Host "Da chup $($shot.f).png ($($bytes.Length) bytes)"
        } else {
            Write-Warning "Khong co du lieu anh cho $($shot.f): $($resp | ConvertTo-Json -Depth 5)"
        }
    }
}

$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "done", [System.Threading.CancellationToken]::None).Wait()
Write-Host "XONG."

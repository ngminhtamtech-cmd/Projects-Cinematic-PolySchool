# Chup 6 anh giao dien con thieu cho bao cao do an CineBook (Hinh 6-11)
# Dung tai khoan dev cuc bo, khong dua mat khau nay vao commit/CI.
$ErrorActionPreference = "Stop"
$base   = "http://localhost:8080/Website-ban-ve-xem-phim"
$out    = "docs\diagrams\screens"
$chrome = "C:\Program Files\Google\Chrome\Application\chrome.exe"

$accounts = @(
  @{ role="admin";  email="admin@cinebook.local";       pass="123456" },
  @{ role="member"; email="khanh.linh@cinebook.local";  pass="123456" },
  @{ role="staff";  email="staff01@cinebook.local";     pass="123456" }
)
$shots = @{
  admin  = @( @{f="6. Giao diện quản lý dữ liệu"       ; u="/admin/films";     h=1600},
              @{f="7. Giao diện tìm kiếm và báo cáo"    ; u="/admin/reports";  h=2000},
              @{f="11. Giao diện dashboard admin"       ; u="/admin/dashboard"; h=1800} )
  member = @( @{f="9. Giao diện dashboard khách hàng"   ; u="/orders/history";  h=1400},
              @{f="8. Giao diện đặt vé"                 ; u="/booking?showtimeId=20"; h=1800} )
  staff  = @( @{f="10. Giao diện dashboard nhân viên"   ; u="/staff/checkin";  h=1400} )
}

foreach ($acc in $accounts) {
  $profile = Join-Path $env:TEMP "cinebook-shot-$($acc.role)"
  Remove-Item $profile -Recurse -Force -ErrorAction SilentlyContinue

  $s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
  $page = Invoke-WebRequest "$base/login" -WebSession $s -UseBasicParsing
  $csrf = ([regex]'name="_csrf"\s+value="([^"]+)"').Match($page.Content).Groups[1].Value
  Invoke-WebRequest "$base/login" -Method POST -WebSession $s -UseBasicParsing `
    -Body @{ email=$acc.email; password=$acc.pass; _csrf=$csrf } | Out-Null
  $jsession = ($s.Cookies.GetCookies($base) | Where-Object Name -eq "JSESSIONID").Value
  if (-not $jsession) { Write-Warning "Dang nhap that bai: $($acc.role)"; continue }
  Write-Host "Da dang nhap $($acc.role), JSESSIONID=$jsession"

  foreach ($shot in $shots[$acc.role]) {
    $tmpShot = Join-Path $env:TEMP "cinebook-shot-tmp.png"
    if (Test-Path $tmpShot) { Remove-Item $tmpShot -Force }
    & $chrome --headless=new --disable-gpu --hide-scrollbars `
      --user-data-dir=$profile --window-size=1600,$($shot.h) `
      --screenshot=$tmpShot `
      "$base$($shot.u);jsessionid=$jsession"
    $waited = 0
    while (-not (Test-Path $tmpShot) -and $waited -lt 8000) {
      Start-Sleep -Milliseconds 500
      $waited += 500
    }
    if (Test-Path $tmpShot) {
      $dest = Join-Path $out ("$($shot.f).png")
      Copy-Item $tmpShot $dest -Force
      Write-Host "Da chup $($shot.f).png ($((Get-Item $dest).Length) bytes)"
    } else {
      Write-Warning "KHONG chup duoc: $($shot.f)"
    }
    Start-Sleep -Milliseconds 800
  }
}
Write-Host "XONG."

@echo off
setlocal
if "%CINEBOOK_BASE_URL%"=="" set "CINEBOOK_BASE_URL=http://localhost:8080/Website-ban-ve-xem-phim-1.0-SNAPSHOT"
if "%CINEBOOK_SHOWTIME_ID%"=="" (
  echo Set CINEBOOK_SHOWTIME_ID before running this script.
  exit /b 2
)
powershell -NoProfile -Command "$u='%CINEBOOK_BASE_URL%/api/v1/showtimes/%CINEBOOK_SHOWTIME_ID%/seats/version'; $jobs=1..100|ForEach-Object{Start-Job -ScriptBlock{param($x) try{(Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 $x).StatusCode}catch{0}} -ArgumentList $u}; $r=$jobs|Wait-Job|Receive-Job; $jobs|Remove-Job; $r|Group-Object|Format-Table Count,Name; if(($r|Where-Object{$_ -ne 200}).Count){exit 1}"

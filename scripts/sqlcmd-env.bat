@echo off
rem ============================================================================
rem  sqlcmd-env.bat - dinh vi sqlcmd va dung tham so ket noi dung chuan (DB-02).
rem
rem  VAN DE GOC
rem    Cac script van hanh goi thang "sqlcmd ... -E" va deu that bai voi loi ODBC
rem    18 lien quan TLS. Hai nguyen nhan chong len nhau:
rem      1. sqlcmd 18 KHONG nam trong PATH mac dinh cua may nay;
rem      2. ODBC Driver 18 bat buoc ma hoa ket noi, nen SQL Server dung chung chi
rem         tu ky se bi tu choi neu thieu -C (TrustServerCertificate).
rem    Ket qua: backup va test-restore chua bao gio chay duoc, tuc la quy trinh
rem    sao luu chi ton tai tren giay.
rem
rem  CACH DUNG
rem    call "%~dp0sqlcmd-env.bat"
rem    %SQLCMD% -S "%CINEBOOK_SQL_SERVER%" %SQLAUTH% -d master -Q "..."
rem
rem  XAC THUC
rem    Mac dinh dung Windows Authentication (-E).
rem    Muon dung SQL login thi dat truoc hai bien moi truong:
rem       set CINEBOOK_SQL_USER=sa
rem       set CINEBOOK_SQL_PASSWORD=...
rem    Mat khau KHONG duoc go thang vao dong lenh (se nam lai trong lich su shell
rem    va trong danh sach tien trinh) — chi truyen qua bien moi truong.
rem ============================================================================

if not defined CINEBOOK_SQL_SERVER set "CINEBOOK_SQL_SERVER=localhost"

rem --- 1. Tim sqlcmd: uu tien ban tren PATH, roi den cac vi tri cai dat chuan ---
set "SQLCMD="
where sqlcmd >nul 2>&1 && set "SQLCMD=sqlcmd"
if not defined SQLCMD (
  for %%V in (180 170 160) do (
    if not defined SQLCMD (
      if exist "%ProgramFiles%\Microsoft SQL Server\Client SDK\ODBC\%%V\Tools\Binn\sqlcmd.exe" (
        set "SQLCMD=%ProgramFiles%\Microsoft SQL Server\Client SDK\ODBC\%%V\Tools\Binn\sqlcmd.exe"
      )
    )
  )
)
if not defined SQLCMD (
  echo [LOI] Khong tim thay sqlcmd. Cai "Microsoft Command Line Utilities for SQL Server"
  echo       hoac them thu muc chua sqlcmd.exe vao PATH.
  exit /b 9
)

rem --- 2. Tham so xac thuc ---
if defined CINEBOOK_SQL_USER (
  if not defined CINEBOOK_SQL_PASSWORD (
    echo [LOI] Da dat CINEBOOK_SQL_USER nhung thieu CINEBOOK_SQL_PASSWORD.
    exit /b 9
  )
  set "SQLAUTH=-U %CINEBOOK_SQL_USER% -P %CINEBOOK_SQL_PASSWORD%"
) else (
  set "SQLAUTH=-E"
)

rem --- 3. Tham so bat buoc cho ODBC Driver 18 ---
rem  -C : tin chung chi cua server (SQL Server noi bo dung chung chi tu ky)
rem  -I : bat QUOTED_IDENTIFIER — bang Users co filtered index, thieu co nay thi
rem       moi UPDATE/INSERT len bang do bao Msg 1934
rem  -b : tra exit code khac 0 khi co loi, de script goi biet ma dung lai
set "SQLFLAGS=-C -I -b"
exit /b 0

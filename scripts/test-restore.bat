@echo off
setlocal
rem ============================================================================
rem  test-restore.bat - phuc hoi thu mot ban sao luu ra DB tam, chay DBCC CHECKDB
rem  roi xoa DB tam di. Muc dich: chung minh ban backup thuc su dung duoc chu
rem  khong chi ton tai tren dia.
rem
rem  Logic SQL nam o test-restore.sql (truyen tham so bang -v) — de inline trong
rem  .bat thi dau nhay bi batch nuot va cau lenh vo (DB-02).
rem  Tham so ket noi tap trung o sqlcmd-env.bat.
rem ============================================================================
if "%~1"=="" (
  echo Usage: test-restore.bat C:\path\CineBookDB_timestamp.bak
  exit /b 2
)
if not exist "%~1" (
  echo [LOI] Khong tim thay file backup: %~1
  exit /b 2
)
call "%~dp0sqlcmd-env.bat" || exit /b %errorlevel%

set "RESTORE_DB=CineBookDB_RestoreTest"
set "RESTORE_DATA=C:\tmp\%RESTORE_DB%.mdf"
set "RESTORE_LOG=C:\tmp\%RESTORE_DB%_log.ldf"

"%SQLCMD%" -S "%CINEBOOK_SQL_SERVER%" %SQLAUTH% %SQLFLAGS% -d master -f 65001 ^
  -v BackupFile="%~1" RestoreDb="%RESTORE_DB%" DataPath="%RESTORE_DATA%" LogPath="%RESTORE_LOG%" ^
  -i "%~dp0test-restore.sql"
if errorlevel 1 exit /b %errorlevel%
echo Restore and DBCC CHECKDB succeeded; temporary database removed.

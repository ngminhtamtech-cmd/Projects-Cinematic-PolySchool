@echo off
setlocal
rem ============================================================================
rem  backup-cinebook.bat - sao luu va tu kiem tra ban sao luu.
rem
rem  Tham so ket noi (duong dan sqlcmd, -C/-I/-b, xac thuc) tap trung o
rem  sqlcmd-env.bat — xem giai thich loi ODBC 18 trong file do (DB-02).
rem ============================================================================
call "%~dp0sqlcmd-env.bat" || exit /b %errorlevel%

if "%CINEBOOK_DB_NAME%"=="" set "CINEBOOK_DB_NAME=CineBookDB"
if "%CINEBOOK_BACKUP_DIR%"=="" set "CINEBOOK_BACKUP_DIR=C:\tmp\cinebook-backups"
if not exist "%CINEBOOK_BACKUP_DIR%" mkdir "%CINEBOOK_BACKUP_DIR%"
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "STAMP=%%i"
set "BACKUP_FILE=%CINEBOOK_BACKUP_DIR%\%CINEBOOK_DB_NAME%_%STAMP%.bak"

rem COPY_ONLY: khong cat chuoi sao luu dinh ky dang co.
rem KHONG dung WITH COMPRESSION: SQL Server Express khong ho tro (Msg 1844) va
rem se lam ca lenh that bai.
"%SQLCMD%" -S "%CINEBOOK_SQL_SERVER%" %SQLAUTH% %SQLFLAGS% -d master -Q "BACKUP DATABASE [%CINEBOOK_DB_NAME%] TO DISK=N'%BACKUP_FILE%' WITH COPY_ONLY, CHECKSUM, INIT; RESTORE VERIFYONLY FROM DISK=N'%BACKUP_FILE%' WITH CHECKSUM;"
if errorlevel 1 exit /b %errorlevel%
echo Backup verified: %BACKUP_FILE%

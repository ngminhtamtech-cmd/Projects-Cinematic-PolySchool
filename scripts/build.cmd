@echo off
setlocal enabledelayedexpansion
rem ===================================================================
rem CB-BLK-001 — build CineBook tu mot terminal sach, khong can mo IDE.
rem
rem Cach dung:
rem     scripts\build.cmd                  -> mvn -B clean package (unit tests)
rem     scripts\build.cmd test             -> mvn -B test (unit tests)
rem     scripts\build.cmd verify           -> mvn -B verify (unit + integration)
rem     scripts\build.cmd checkstyle:check
rem
rem Thu tu tim Maven (dung cai dau tien thay duoc):
rem     1. bien moi truong CINEBOOK_MVN  (tro thang vao mvn.cmd)
rem     2. %MAVEN_HOME%\bin\mvn.cmd
rem     3. %M2_HOME%\bin\mvn.cmd
rem     4. mvn tren PATH
rem     5. ban di kem NetBeans 25 (may nay dang dung cai nay)
rem
rem Khong chua credential; cau hinh DB doc tu ngoai WAR (xem CLAUDE.md).
rem ===================================================================

set "MVN_CMD="

if defined CINEBOOK_MVN if exist "%CINEBOOK_MVN%" set "MVN_CMD=%CINEBOOK_MVN%"
if not defined MVN_CMD if defined MAVEN_HOME if exist "%MAVEN_HOME%\bin\mvn.cmd" set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
if not defined MVN_CMD if defined M2_HOME if exist "%M2_HOME%\bin\mvn.cmd" set "MVN_CMD=%M2_HOME%\bin\mvn.cmd"
if not defined MVN_CMD (
    where mvn >nul 2>nul && set "MVN_CMD=mvn"
)
if not defined MVN_CMD if exist "C:\Program Files\NetBeans-25\netbeans\java\maven\bin\mvn.cmd" set "MVN_CMD=C:\Program Files\NetBeans-25\netbeans\java\maven\bin\mvn.cmd"

if not defined MVN_CMD (
    echo [build.cmd] Khong tim thay Maven.
    echo [build.cmd] Dat CINEBOOK_MVN hoac MAVEN_HOME, hoac them mvn vao PATH.
    echo [build.cmd] Chi tiet: docs\build-environment.md
    exit /b 1
)

set "GOALS=%*"
if "%GOALS%"=="" set "GOALS=clean package"

echo [build.cmd] Maven : %MVN_CMD%
echo [build.cmd] Goals : %GOALS%
call "%MVN_CMD%" -B %GOALS%
exit /b %ERRORLEVEL%

@echo off
setlocal enabledelayedexpansion
rem ============================================================================
rem  csrf-sweep.bat - P05. Ban vao MOI endpoint ghi mot request POST KHONG kem
rem  token CSRF, roi doi chieu ma tra ve.
rem
rem  Ky vong: KHONG endpoint nao duoc tra 200. Bat cu 200 nao deu la mot lo hong
rem  CSRF con ho.
rem
rem   403 = bi chan (CsrfFilter tu choi, hoac AuthFilter chan vai tro)  -> DAT
rem   302 = bi day ve /login (chua dang nhap)                          -> DAT
rem   401 = chua dang nhap (endpoint JSON)                             -> DAT
rem   200 = LOT LUOI                                                   -> HONG
rem
rem  Cach dung:
rem    scripts\csrf-sweep.bat
rem    scripts\csrf-sweep.bat http://localhost:8080/Website-ban-ve-xem-phim
rem    scripts\csrf-sweep.bat <base-url> <email> <mat-khau>
rem
rem  Chay khong kem tai khoan thi cac route /admin, /system, /staff chi den duoc
rem  AuthFilter (302). Muon quet den tan CsrfFilter thi truyen tai khoan admin.
rem  KHONG hardcode mat khau vao file nay.
rem ============================================================================

set "BASE=%~1"
if "%BASE%"=="" set "BASE=http://localhost:8080/Website-ban-ve-xem-phim"
set "EMAIL=%~2"
set "PASSWORD=%~3"

set "JAR=%TEMP%\cinebook-csrf-sweep.cookies"
if exist "%JAR%" del /q "%JAR%"

set /a PASS=0
set /a FAIL=0
set "EXPECTED=34"

echo.
echo === csrf-sweep === %BASE%
echo.

rem --- 1. Lay session an danh + token ban dau -------------------------------
curl -s -c "%JAR%" -o nul "%BASE%/login"
call :readToken
if "!TOKEN!"=="" (
    echo [LOI] Khong lay duoc cookie XSRF-TOKEN tu %BASE%/login
    echo       Tomcat da chay chua? Ung dung deploy dung context path chua?
    exit /b 2
)
echo Token an danh: !TOKEN:~0,12!...

rem --- 2. Dang nhap neu duoc cap tai khoan ----------------------------------
if not "%EMAIL%"=="" (
    set "LOGIN=000"
    for /f %%C in ('curl -s -b "%JAR%" -c "%JAR%" -o nul -w "%%{http_code}" -X POST ^
        -d "_csrf=!TOKEN!" -d "email=%EMAIL%" -d "password=%PASSWORD%" "%BASE%/login"') do set "LOGIN=%%C"
    call :readToken
    echo Dang nhap %EMAIL%: HTTP !LOGIN!  ^(token sau khi xoay: !TOKEN:~0,12!...^)
    if not "!LOGIN!"=="302" (
        echo [LOI] Dang nhap that bai; ky vong HTTP 302 nhung nhan !LOGIN!.
        exit /b 3
    )
    if "!TOKEN!"=="" (
        echo [LOI] Dang nhap xong nhung khong co XSRF-TOKEN.
        exit /b 3
    )
) else (
    echo Chay an danh - khong co tai khoan.
)
echo.
echo   MA   KET QUA  ENDPOINT
echo   ---- -------  ------------------------------------------------------

rem --- 3. Ban POST khong token vao tung endpoint ghi ------------------------
for %%R in (
    /login
    /register
    /forgot-password
    /logout
    /appeal
    /profile
    /member/loyalty
    /orders
    /orders/1/pay
    /ticket-refund-appeal
    /appeals
    /ticket-refund
    /admin/dashboard
    /admin/films
    /admin/cinemas
    /admin/rooms
    /admin/showtimes
    /admin/users
    /admin/staff
    /admin/comments
    /admin/promotions
    /admin/combos
    /admin/orders
    /admin/custom-content
    /admin/notifications
    /admin/appeals
    /staff/checkin
    /system/managers
    /system/config
    /system/audit-logs
    /system/backup
    /api/v1/auth/login
    /api/v1/auth/logout
    /api/v1/staff/checkin
) do (
    set "CODE=000"
    for /f %%C in ('curl -s -b "%JAR%" -o nul -w "%%{http_code}" -X POST -d "sweep=1" "%BASE%%%R"') do set "CODE=%%C"
    set "BLOCKED=0"
    if "!CODE!"=="302" set "BLOCKED=1"
    if "!CODE!"=="401" set "BLOCKED=1"
    if "!CODE!"=="403" set "BLOCKED=1"
    if "!BLOCKED!"=="1" (
        set /a PASS+=1
        echo   !CODE!  chan    %%R
    ) else (
        set /a FAIL+=1
        echo   !CODE!  SAI MA  %%R
    )
)

rem --- 4. Doi chung duong: co token thi phai qua duoc CsrfFilter ------------
echo.
call :readToken
set "CTRL=000"
for /f %%C in ('curl -s -b "%JAR%" -o nul -w "%%{http_code}" -X POST -d "_csrf=!TOKEN!" "%BASE%/login"') do set "CTRL=%%C"
echo   Doi chung: POST /login KEM token -^> HTTP !CTRL!
if not "!CTRL!"=="200" (
    echo   [LOI] Doi chung phai den duoc LoginServlet va tra 200; nhan !CTRL!.
    set /a FAIL+=1
)

if not !PASS! EQU !EXPECTED! (
    echo   [LOI] Ky vong dung !EXPECTED! endpoint bi chan; dem duoc !PASS!.
    set /a FAIL+=1
)

echo.
echo === Tong ket: !PASS! chan / !FAIL! lot luoi ===
if !FAIL! GTR 0 exit /b 1
exit /b 0

rem --------------------------------------------------------------------------
:readToken
set "TOKEN="
rem Cookie jar dinh dang Netscape: domain flag path secure expiry name value
for /f "tokens=7" %%A in ('findstr /C:"XSRF-TOKEN" "%JAR%" 2^>nul') do set "TOKEN=%%A"
goto :eof

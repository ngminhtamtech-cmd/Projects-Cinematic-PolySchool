@echo off
setlocal
rem ============================================================================
rem  route-baseline.bat — DA THAY THE bang route-check.ps1 (QA-01).
rem
rem  VI SAO BO CACH CU
rem    Script nay tung chup ma HTTP cua mot danh sach route hard-code roi so sanh
rem    voi docs\baseline-routes-before.txt. Cach do sai o hai diem:
rem
rem      1. Danh sach route da loi thoi (/auth/login, /booking/history,
rem         /api/v1/catalog/films). Cac route do khong con ton tai nen tra 404;
rem         route THAT (/login, /orders/history, /api/v1/films) khong he duoc kiem.
rem      2. File baseline goc cung chua chinh nhung 404 do, nen "diff" luon sach
rem         va script bao "0 route hoi quy" ngay ca khi chuc nang bien mat.
rem         Do la mau xanh gia — dung loai canh bao te nhat.
rem
rem  CACH MOI
rem    Ma ky vong duoc khai bao tuong minh trong scripts\route-manifest.txt, va
rem    404 khong bao gio duoc coi la dat cho mot route bat buoc. Script moi con
rem    kiem than trang de bat truong hop "HTTP 200 nhung noi dung la trang loi 500".
rem ============================================================================

echo route-baseline.bat da duoc thay the bang route-check.ps1.
echo Dang chuyen tiep sang script moi...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0route-check.ps1" %*
exit /b %errorlevel%

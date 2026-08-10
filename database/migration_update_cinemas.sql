-- ============================================================================
-- SQL Script: Bổ sung các cột thông tin mới cho Bảng Cinemas
-- Mô tả: Thêm các cột Phone và Status vào bảng Cinemas trong SQL Server
-- Ngày thực thi: 2026-07-24
-- Database: CineBookDB
-- ============================================================================

USE CineBookDB;
GO

-- 1. Bổ sung cột Số điện thoại Hotline rạp (Phone)
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Cinemas') AND name = N'Phone')
BEGIN
    ALTER TABLE Cinemas ADD Phone NVARCHAR(30) NULL;
    PRINT N'Đã thêm cột Phone vào bảng Cinemas thành công.';
END
ELSE
BEGIN
    PRINT N'Cột Phone đã tồn tại trong bảng Cinemas.';
END
GO

-- 2. Bổ sung cột Trạng thái rạp chiếu (Status: active - Đang hoạt động, maintenance - Bảo trì, closed - Tạm đóng)
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Cinemas') AND name = N'Status')
BEGIN
    ALTER TABLE Cinemas ADD Status NVARCHAR(20) NULL DEFAULT 'active';
    PRINT N'Đã thêm cột Status vào bảng Cinemas thành công.';
END
ELSE
BEGIN
    PRINT N'Cột Status đã tồn tại trong bảng Cinemas.';
END
GO

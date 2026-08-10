-- ============================================================================
-- SQL Script: Bổ sung các cột thông tin mới cho Bảng Films
-- Mô tả: Thêm các cột Country, Format, Status, Banner vào bảng Films trong SQL Server
-- Ngày thực thi: 2026-07-24
-- Database: CineBookDB
-- ============================================================================

USE CineBookDB;
GO

-- 1. Bổ sung cột Quốc gia sản xuất phim (Country)
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Films') AND name = N'Country')
BEGIN
    ALTER TABLE Films ADD Country NVARCHAR(100) NULL;
    PRINT N'Đã thêm cột Country vào bảng Films thành công.';
END
ELSE
BEGIN
    PRINT N'Cột Country đã tồn tại trong bảng Films.';
END
GO

-- 2. Bổ sung cột Định dạng chiếu (Format: 2D, 3D, IMAX 2D, 4DX...)
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Films') AND name = N'Format')
BEGIN
    ALTER TABLE Films ADD Format NVARCHAR(50) NULL;
    PRINT N'Đã thêm cột Format vào bảng Films thành công.';
END
ELSE
BEGIN
    PRINT N'Cột Format đã tồn tại trong bảng Films.';
END
GO

-- 3. Bổ sung cột Trạng thái chiếu (Status: showing - Đang chiếu, coming - Sắp chiếu, ended - Ngừng chiếu)
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Films') AND name = N'Status')
BEGIN
    ALTER TABLE Films ADD Status NVARCHAR(20) NULL DEFAULT 'showing';
    PRINT N'Đã thêm cột Status vào bảng Films thành công.';
END
ELSE
BEGIN
    PRINT N'Cột Status đã tồn tại trong bảng Films.';
END
GO

-- 4. Bổ sung cột Ảnh Banner ngang (Banner)
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Films') AND name = N'Banner')
BEGIN
    ALTER TABLE Films ADD Banner NVARCHAR(255) NULL;
    PRINT N'Đã thêm cột Banner vào bảng Films thành công.';
END
ELSE
BEGIN
    PRINT N'Cột Banner đã tồn tại trong bảng Films.';
END
GO

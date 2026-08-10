-- ============================================================================
-- SQL Script: Migration V2 - Nâng cấp Schema Bảng Films và Cinemas
-- Mô tả: Tổng hợp tất cả câu lệnh ALTER TABLE nâng cấp cho Quản lý Phim và Quản lý Rạp chiếu
-- Ngày thực thi: 2026-07-24
-- Database: CineBookDB
-- ============================================================================

USE CineBookDB;
GO

PRINT N'--- BẮT ĐẦU NÂNG CẤP BẢNG FILMS ---';

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Films') AND name = N'Country')
BEGIN
    ALTER TABLE Films ADD Country NVARCHAR(100) NULL;
    PRINT N'[Films] Đã thêm cột Country.';
END

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Films') AND name = N'Format')
BEGIN
    ALTER TABLE Films ADD Format NVARCHAR(50) NULL;
    PRINT N'[Films] Đã thêm cột Format.';
END

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Films') AND name = N'Status')
BEGIN
    ALTER TABLE Films ADD Status NVARCHAR(20) NULL DEFAULT 'showing';
    PRINT N'[Films] Đã thêm cột Status.';
END

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Films') AND name = N'Banner')
BEGIN
    ALTER TABLE Films ADD Banner NVARCHAR(255) NULL;
    PRINT N'[Films] Đã thêm cột Banner.';
END

GO

PRINT N'--- BẮT ĐẦU NÂNG CẤP BẢNG CINEMAS ---';

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Cinemas') AND name = N'Phone')
BEGIN
    ALTER TABLE Cinemas ADD Phone NVARCHAR(30) NULL;
    PRINT N'[Cinemas] Đã thêm cột Phone.';
END

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Cinemas') AND name = N'Status')
BEGIN
    ALTER TABLE Cinemas ADD Status NVARCHAR(20) NULL DEFAULT 'active';
    PRINT N'[Cinemas] Đã thêm cột Status.';
END

GO

PRINT N'=== HOÀN TẤT NÂNG CẤP SCHEMA V2 THÀNH CÔNG ===';

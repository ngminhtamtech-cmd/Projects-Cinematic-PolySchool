-- SQL Script: Thêm thuộc tính BannerUrl cho bảng Cinemas (Quản lý Banner Rạp Phim)
-- Ngày thực hiện: 24/07/2026

USE CineBookDB;
GO

IF NOT EXISTS (
    SELECT * FROM sys.columns 
    WHERE object_id = OBJECT_ID('Cinemas') AND name = 'BannerUrl'
)
BEGIN
    ALTER TABLE Cinemas ADD BannerUrl NVARCHAR(500) NULL;
    PRINT N'Đã thêm cột BannerUrl vào bảng Cinemas thành công!';
END
GO

-- Cập nhật dữ liệu Banner mặc định cho các rạp hiện tại
UPDATE Cinemas 
SET BannerUrl = N'/assets/img/cinemas/fpt-center-banner.jpg' 
WHERE BannerUrl IS NULL;
GO

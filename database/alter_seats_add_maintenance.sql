-- SQL Migration: Thêm loại ghế bảo trì (maintenance) và các nâng cấp ràng buộc ghế
USE CineBookDB;
GO

-- 1. Cập nhật CHECK constraint bảng Seats để chấp nhận SeatType 'maintenance'
IF EXISTS (SELECT * FROM sys.check_constraints WHERE name = 'CK__Seats__SeatType__43D61337' OR name LIKE '%Seats%SeatType%')
BEGIN
    -- Tìm và drop check constraint cũ trên bảng Seats nếu có
    DECLARE @chkName NVARCHAR(256);
    SELECT TOP 1 @chkName = name FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('Seats') AND name LIKE '%SeatType%';
    IF @chkName IS NOT NULL
    BEGIN
        EXEC('ALTER TABLE Seats DROP CONSTRAINT ' + @chkName);
    END
END
GO

ALTER TABLE Seats ADD CONSTRAINT CK_Seats_SeatType CHECK (SeatType IN ('standard','vip','couple','maintenance'));
GO

-- 2. Cập nhật CHECK constraint bảng ShowtimeSeats để chấp nhận Status 'maintenance'
IF EXISTS (SELECT * FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('ShowtimeSeats') AND name LIKE '%Status%')
BEGIN
    DECLARE @chkStatusName NVARCHAR(256);
    SELECT TOP 1 @chkStatusName = name FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('ShowtimeSeats') AND name LIKE '%Status%';
    IF @chkStatusName IS NOT NULL
    BEGIN
        EXEC('ALTER TABLE ShowtimeSeats DROP CONSTRAINT ' + @chkStatusName);
    END
END
GO

ALTER TABLE ShowtimeSeats ADD CONSTRAINT CK_ShowtimeSeats_Status CHECK (Status IN ('available','held','booked','maintenance'));
GO

-- 3. Đảm bảo Ràng buộc duy nhất (Unique Constraint) cho (RoomId, SeatKey)
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'UQ_Seats_Room_SeatKey' AND object_id = OBJECT_ID('Seats'))
BEGIN
    ALTER TABLE Seats ADD CONSTRAINT UQ_Seats_Room_SeatKey UNIQUE (RoomId, SeatKey);
END
GO

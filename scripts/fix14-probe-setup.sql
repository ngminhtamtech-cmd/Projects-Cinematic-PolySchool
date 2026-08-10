-- =====================================================================================
-- fix14-probe-setup.sql — dung DB thu nghiem CineBookDB_Fix14Probe cho N-03.
--
-- MUC DICH
--   Tai hien dung hinh dang DB "ban cu" ma fix14 phai xu ly, de kiem chung hai dieu:
--     1. co MOT dong OrderCode <> TicketCode thi KHONG cot nao bi go (Done khi cua N-03);
--     2. cot di san nam trong UNIQUE constraint / CHECK constraint van go duoc.
--
--   DB nay la DB rac, tao va xoa trong cung mot lan chay. KHONG dung CineBookDB hay
--   CineBookDB_Test: chay fix14 tren chung se doi schema that.
--
-- CHAY:  sqlcmd -S localhost -U sa -P <pw> -C -I -b -d master -i scripts\fix14-probe-setup.sql
-- =====================================================================================

SET NOCOUNT ON;
GO

IF DB_ID('CineBookDB_Fix14Probe') IS NOT NULL
BEGIN
    ALTER DATABASE CineBookDB_Fix14Probe SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE CineBookDB_Fix14Probe;
END
GO

CREATE DATABASE CineBookDB_Fix14Probe;
GO

USE CineBookDB_Fix14Probe;
GO

-- --- Bang cho PHAN A: deu THIEU cot ma code doc -------------------------------------
CREATE TABLE ComboFoods (
    Id    INT IDENTITY PRIMARY KEY,
    Name  NVARCHAR(100) NOT NULL,
    Price DECIMAL(19,2) NOT NULL DEFAULT 0);

CREATE TABLE Showtimes (
    Id        INT IDENTITY PRIMARY KEY,
    StartTime DATETIME NOT NULL,
    EndTime   DATETIME NOT NULL);

CREATE TABLE CommentReports (
    Id        INT IDENTITY PRIMARY KEY,
    CommentId INT NOT NULL);
GO

-- --- Bang cho PHAN B: deu CON cot di san --------------------------------------------
--
-- Orders.OrderCode nam trong UNIQUE constraint — ban cu B1 xu ly dung.
CREATE TABLE Orders (
    Id            INT IDENTITY PRIMARY KEY,
    TicketCode    NVARCHAR(50)  NULL,
    OrderCode     NVARCHAR(50)  NOT NULL,
    SeatSubtotal  DECIMAL(19,2) NOT NULL DEFAULT 0,
    ComboSubtotal DECIMAL(19,2) NOT NULL DEFAULT 0,
    TotalAmount   DECIMAL(19,2) NOT NULL DEFAULT 0,
    OriginalPrice DECIMAL(19,2) NOT NULL DEFAULT 0,
    FinalPrice    DECIMAL(19,2) NOT NULL DEFAULT 0,
    CONSTRAINT UQ_Orders_OrderCode UNIQUE (OrderCode));

-- OrderSeats.Price nam trong UNIQUE constraint — day la ca ban cu KHONG go duoc vi
-- B3 loc "is_unique_constraint = 0", nen DROP COLUMN chac chan loi (loi (b) cua N-03).
CREATE TABLE OrderSeats (
    OrderId        INT NOT NULL,
    ShowtimeSeatId INT NOT NULL,
    UnitPrice      DECIMAL(19,2) NOT NULL DEFAULT 0,
    Price          DECIMAL(19,2) NOT NULL,
    CONSTRAINT PK_OrderSeats PRIMARY KEY (OrderId, ShowtimeSeatId),
    CONSTRAINT UQ_OrderSeats_Price UNIQUE (OrderId, Price));

-- OrderComboFoods.Price bi mot CHECK constraint MUC BANG tham chieu — day la ca ban cu
-- khong xu ly (loi (c) cua N-03): khong khoi nao go check constraint.
CREATE TABLE OrderComboFoods (
    OrderId     INT NOT NULL,
    ComboFoodId INT NOT NULL,
    Quantity    INT NOT NULL DEFAULT 1,
    UnitPrice   DECIMAL(19,2) NOT NULL DEFAULT 0,
    Price       DECIMAL(19,2) NOT NULL,
    CONSTRAINT PK_OrderComboFoods PRIMARY KEY (OrderId, ComboFoodId),
    CONSTRAINT CK_OrderComboFoods_Price CHECK (Price >= 0 AND Quantity > 0));
GO

-- --- Du lieu: mot dong SACH + mot dong XUNG DOT (OrderCode <> TicketCode) ------------
INSERT INTO Orders (TicketCode, OrderCode, SeatSubtotal, ComboSubtotal, TotalAmount, OriginalPrice, FinalPrice)
VALUES (N'TK-SACH-001', N'TK-SACH-001', 90000, 0, 90000, 90000, 90000);

INSERT INTO Orders (TicketCode, OrderCode, SeatSubtotal, ComboSubtotal, TotalAmount, OriginalPrice, FinalPrice)
VALUES (N'TK-KHAC-002', N'OC-KHAC-002', 90000, 0, 90000, 90000, 90000);

INSERT INTO OrderSeats (OrderId, ShowtimeSeatId, UnitPrice, Price) VALUES (1, 11, 90000, 90000);
INSERT INTO OrderComboFoods (OrderId, ComboFoodId, Quantity, UnitPrice, Price) VALUES (1, 1, 2, 45000, 45000);
GO

PRINT '=== probe: da dung CineBookDB_Fix14Probe (1 dong OrderCode <> TicketCode) ===';
GO

-- =====================================================================================
-- ROLLBACK
--   ALTER DATABASE CineBookDB_Fix14Probe SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
--   DROP DATABASE CineBookDB_Fix14Probe;
-- =====================================================================================

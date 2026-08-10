-- ============================================================================
--  fix26_comments_duplicate_reviews.sql — don nhom danh gia trung dang chan fix25.
--
--  VAN DE GOC
--    fix25 co chu y KHONG xoa du lieu nguoi dung: gap nhom (UserId, FilmId) trung
--    thi no bao cao roi bo qua buoc tao UQ_Comments_UserId_FilmId. Tren CineBookDB
--    con ba dong Id 9/10/11 cua khanh.linh@cinebook.local cho phim 21 (du lieu do
--    dot kiem thu 31/07 sinh ra: "QA danh gia lan 1/2/3 - chua he xem phim nay"),
--    nen unique index chua bao gio ton tai. Luat "mot nguoi mot danh gia cho mot
--    phim" hien chi duoc tang service giu, khong co lop chan o CSDL.
--
--  QUYET DINH CUA CHU DU AN (01/08/2026)
--    Giu Id 11 (moi nhat), xoa Id 9 va 10.
--
--  PHAM VI HEP CO CHU Y
--    Script xoa THEO ID NEU TEN, va chi khi ca chu ky khop: dung tai khoan, dung
--    phim, va dong duoc giu lai (Id 11) van con dung cho. Khong quet rong kieu
--    "xoa moi dong khong phai MAX(Id) trong nhom trung" — mot cau nhu vay chay
--    tren DB khac se xoa danh gia that ma khong ai kip nhin.
--
--  Idempotent: chay lai nhieu lan khong doi ket qua.
-- ============================================================================
SET XACT_ABORT ON;
SET NOCOUNT ON;
BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @FilmId INT = 21;
    DECLARE @KeepId INT = 11;
    DECLARE @OwnerId INT = (SELECT Id FROM dbo.Users WHERE Email = N'khanh.linh@cinebook.local');

    IF @OwnerId IS NULL
    BEGIN
        PRINT 'fix26: khong co tai khoan khanh.linh@cinebook.local — khong co gi de don.';
    END
    ELSE IF NOT EXISTS (SELECT 1 FROM dbo.Comments
                        WHERE Id = @KeepId AND UserId = @OwnerId AND FilmId = @FilmId)
    BEGIN
        -- Da don roi, hoac DB nay khong phai tinh huong da duoc quyet dinh. Ca hai
        -- truong hop deu khong duoc xoa gi.
        PRINT 'fix26: khong thay danh gia Id 11 cua dung tai khoan/phim — BO QUA, khong xoa gi.';
    END
    ELSE
    BEGIN
        DELETE FROM dbo.Comments
        WHERE Id IN (9, 10) AND UserId = @OwnerId AND FilmId = @FilmId;
        PRINT 'fix26: da xoa ' + CAST(@@ROWCOUNT AS VARCHAR(10))
              + ' danh gia trung cua UserId ' + CAST(@OwnerId AS VARCHAR(10))
              + ' cho FilmId ' + CAST(@FilmId AS VARCHAR(10)) + ' (giu Id 11).';
    END;

    COMMIT TRANSACTION;
    PRINT 'fix26_comments_duplicate_reviews.sql: OK';
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- ROLLBACK:
--   Xoa dong thi khong tu quay lai duoc — dat lai bang chinh hai dong da xoa
--   (chup tu CineBookDB truoc khi chay, 01/08/2026):
--
--   SET IDENTITY_INSERT dbo.Comments ON;
--   INSERT INTO dbo.Comments (Id, UserId, FilmId, Rate, Content, Report, CreatedAt) VALUES
--     (9,  2, 21, 5, N'QA danh gia lan 1 - chua he xem phim nay', 0, '2026-07-31T22:43:11.767'),
--     (10, 2, 21, 5, N'QA danh gia lan 2 - chua he xem phim nay', 0, '2026-07-31T22:43:11.817');
--   SET IDENTITY_INSERT dbo.Comments OFF;
--   DROP INDEX UQ_Comments_UserId_FilmId ON dbo.Comments;

-- ============================================================================
--  fix25_comments_one_review_per_film.sql — mot nguoi mot danh gia cho mot phim
--  (BUG-08, INV-11).
--
--  VAN DE GOC
--    AdminService.addComment la mot cau INSERT tran. Ca FilmServlet lan
--    /api/v1/films/{id}/comments chi kiem Rate 1..5 va noi dung khong rong, nen
--    mot tai khoan danh gia bao nhieu lan cung duoc — diem trung binh cua phim
--    bi thao tung tuy y.
--
--  SCRIPT NAY KHONG XOA DU LIEU NGUOI DUNG.
--    Neu DB dang co dong vi pham luat moi, script chi BAO CAO va bo qua buoc tao
--    unique index; chu du an quyet dinh xu ly (giu dong nao, xoa dong nao) roi
--    chay lai. Tang service da chan tu ban ghi moi tro di nen khong con sinh them
--    vi pham trong luc cho.
--
--  Idempotent: chay lai nhieu lan khong doi ket qua.
-- ============================================================================
SET XACT_ABORT ON;
SET NOCOUNT ON;
BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @ViolatingGroups INT;
    SELECT @ViolatingGroups = COUNT(*)
    FROM (SELECT UserId, FilmId FROM dbo.Comments GROUP BY UserId, FilmId HAVING COUNT(*) > 1) d;

    IF @ViolatingGroups > 0
    BEGIN
        PRINT 'fix25: BO QUA tao unique index — dang co '
              + CAST(@ViolatingGroups AS VARCHAR(20))
              + ' cap (UserId, FilmId) co nhieu hon mot danh gia.';
        PRINT 'fix25: chi tiet cac dong vi pham —';
        SELECT c.UserId, u.Email, c.FilmId, f.Title AS FilmTitle, COUNT(*) AS SoDanhGia,
               MIN(c.Id) AS DongCuNhat, MAX(c.Id) AS DongMoiNhat
        FROM dbo.Comments c
        JOIN dbo.Users u ON u.Id = c.UserId
        JOIN dbo.Films f ON f.Id = c.FilmId
        GROUP BY c.UserId, u.Email, c.FilmId, f.Title
        HAVING COUNT(*) > 1
        ORDER BY SoDanhGia DESC;
        PRINT 'fix25: hay quyet dinh giu dong nao roi chay lai script nay.';
    END
    ELSE IF NOT EXISTS (SELECT 1 FROM sys.indexes
                        WHERE name = 'UQ_Comments_UserId_FilmId'
                          AND object_id = OBJECT_ID('dbo.Comments'))
    BEGIN
        CREATE UNIQUE NONCLUSTERED INDEX UQ_Comments_UserId_FilmId
            ON dbo.Comments (UserId, FilmId);
        PRINT 'fix25: da tao UQ_Comments_UserId_FilmId.';
    END
    ELSE
    BEGIN
        PRINT 'fix25: UQ_Comments_UserId_FilmId da ton tai.';
    END;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- ROLLBACK:
--   DROP INDEX UQ_Comments_UserId_FilmId ON dbo.Comments;

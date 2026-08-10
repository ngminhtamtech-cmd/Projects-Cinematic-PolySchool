-- Apply the approved irreversible tombstone to production film 21 after fix35.
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'CineBookDB'
    THROW 50150, 'prepare_fix35_cinebookdb_film21.sql may run only on CineBookDB.', 1;
IF COL_LENGTH(N'dbo.Films', N'DeletionMode') IS NULL
    THROW 50151, 'Run fix35_film_showtime_lifecycle.sql first.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF NOT EXISTS (SELECT 1 FROM dbo.Films
                   WHERE Id=21 AND Title=N'Kiểm thử lần 1'
                     AND EndDate=CONVERT(date,'2026-08-02') AND Status=N'ended')
        THROW 50152, 'Film 21 no longer matches the approved lifecycle state.', 1;
    IF (SELECT COUNT(*) FROM dbo.Showtimes WHERE FilmId=21) <> 3
        THROW 50153, 'Film 21 showtime count changed from the approved baseline (3).', 1;
    IF (SELECT COUNT(*) FROM dbo.Orders o JOIN dbo.Showtimes s ON s.Id=o.ShowtimeId WHERE s.FilmId=21) <> 7
        THROW 50154, 'Film 21 order count changed from the approved baseline (7).', 1;
    IF (SELECT COUNT(*) FROM dbo.Comments WHERE FilmId=21) <> 1
        THROW 50155, 'Film 21 comment count changed from the approved baseline (1).', 1;

    EXEC sys.sp_executesql N'
        UPDATE dbo.Films
        SET DeletedAt=COALESCE(DeletedAt,SYSDATETIME()), DeletedByUserId=NULL,
            DeletionMode=N''PRESERVE_COMMENTS'', Status=N''ended'', UpdatedAt=GETDATE()
        WHERE Id=21 AND (DeletedAt IS NULL OR DeletionMode=N''PRESERVE_COMMENTS'');';

    IF NOT EXISTS (SELECT 1 FROM dbo.AuditLogs
                   WHERE Action=N'MIGRATE_FILM_TOMBSTONE' AND TargetType=N'Film' AND TargetId=N'21')
        INSERT INTO dbo.AuditLogs(ActorUserId,Action,TargetType,TargetId,DetailJson,BeforeJson,AfterJson)
        VALUES(NULL,N'MIGRATE_FILM_TOMBSTONE',N'Film',N'21',
               N'{"actor":"SYSTEM","deletionMode":"PRESERVE_COMMENTS","source":"fix35"}',
               N'{"filmId":21,"title":"Kiểm thử lần 1","status":"ended","endDate":"2026-08-02","deletedAt":null}',
               N'{"filmId":21,"title":"Kiểm thử lần 1","status":"ended","endDate":"2026-08-02","deletionMode":"PRESERVE_COMMENTS","actor":"SYSTEM"}');

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

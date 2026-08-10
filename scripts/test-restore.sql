-- =====================================================================================
-- test-restore.sql — phuc hoi thu mot ban backup ra DB tam roi kiem tra tinh toan ven.
--
-- Goi qua scripts\test-restore.bat; ba bien duoc truyen bang -v:
--     BackupFile   duong dan file .bak
--     RestoreDb    ten DB tam
--     DataPath     duong dan file .mdf tam
--     LogPath      duong dan file .ldf tam
--
-- VI SAO DOC FILELISTONLY
--   Ten LOGIC cua file du lieu ben trong ban backup khong nhat thiet la 'CineBookDB':
--   backup cua CineBookDB_Test co ten logic khac, va ban backup lay tu may khac cung vay.
--   Ban script cu ghi cung 'CineBookDB' nen chi chay dung voi dung mot file. Doc tu header
--   thi script dung duoc voi moi file .bak.
-- =====================================================================================

SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @files TABLE (
    LogicalName SYSNAME, PhysicalName NVARCHAR(520), Type CHAR(1),
    FileGroupName SYSNAME NULL, Size NUMERIC(20,0), MaxSize NUMERIC(20,0),
    FileID BIGINT, CreateLSN NUMERIC(25,0), DropLSN NUMERIC(25,0) NULL,
    UniqueID UNIQUEIDENTIFIER, ReadOnlyLSN NUMERIC(25,0) NULL,
    ReadWriteLSN NUMERIC(25,0) NULL, BackupSizeInBytes BIGINT, SourceBlockSize INT,
    FileGroupID INT, LogGroupGUID UNIQUEIDENTIFIER NULL,
    DifferentialBaseLSN NUMERIC(25,0) NULL, DifferentialBaseGUID UNIQUEIDENTIFIER NULL,
    IsReadOnly BIT, IsPresent BIT, TDEThumbprint VARBINARY(32) NULL,
    SnapshotUrl NVARCHAR(360) NULL
);

INSERT INTO @files EXEC ('RESTORE FILELISTONLY FROM DISK = N''$(BackupFile)''');

DECLARE @dataName SYSNAME = (SELECT TOP (1) LogicalName FROM @files WHERE Type = 'D');
DECLARE @logName  SYSNAME = (SELECT TOP (1) LogicalName FROM @files WHERE Type = 'L');

IF @dataName IS NULL OR @logName IS NULL
    RAISERROR (N'Khong doc duoc danh sach file logic tu ban backup.', 16, 1);

IF DB_ID(N'$(RestoreDb)') IS NOT NULL
BEGIN
    ALTER DATABASE [$(RestoreDb)] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE [$(RestoreDb)];
END

DECLARE @sql NVARCHAR(MAX) = N'
    RESTORE DATABASE [$(RestoreDb)] FROM DISK = N''$(BackupFile)''
    WITH MOVE N''' + @dataName + N''' TO N''$(DataPath)'',
         MOVE N''' + @logName  + N''' TO N''$(LogPath)'',
         RECOVERY, REPLACE;';
EXEC sp_executesql @sql;

DBCC CHECKDB(N'$(RestoreDb)') WITH NO_INFOMSGS;

ALTER DATABASE [$(RestoreDb)] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
DROP DATABASE [$(RestoreDb)];

PRINT 'Restore + DBCC CHECKDB thanh cong; DB tam da duoc xoa.';

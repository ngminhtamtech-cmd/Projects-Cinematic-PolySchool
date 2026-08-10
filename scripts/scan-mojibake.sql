-- Quet tieng Viet bi hong (mojibake) tren moi cot kieu chuoi Unicode.
--
-- HAI LOI DA SUA O DAY (do ngay 07/08/2026):
--
--   1. STRING_AGG khong ep kieu NVARCHAR(MAX) nen ket qua bi chan o 8000 byte:
--      script vo thang Msg 9829 va CHUA BAO GIO chay duoc tren CineBookDB.
--      Mot script kiem tra khong chay duoc thi khong khac gi khong co.
--
--   2. Collation cua CineBookDB la SQL_Latin1_General_CP1_CI_AS — KHONG phan biet
--      hoa thuong. Nen LIKE N'%Ã%' khop luon chu 'ã' thuong, ma 'ã' la chu cai
--      tieng Viet hop le: "Nguyễn Trãi", "Doãn Quốc Đam", "uu-dai"... deu bi bao
--      la mojibake. Phai so sanh bang collation nhi phan thi dau hieu mojibake
--      (Ã hoa, â€) moi co y nghia.
--
-- Ket qua mong doi: 0 dong. Chay bang:
--   sqlcmd -S localhost -U sa -P <pw> -C -I -d CineBookDB -f 65001 -i scripts\scan-mojibake.sql
SET NOCOUNT ON;

DECLARE @sql NVARCHAR(MAX) = N'';
SELECT @sql = STRING_AGG(CONVERT(NVARCHAR(MAX),
    N'SELECT N''' + REPLACE(s.name + N'.' + t.name + N'.' + c.name, N'''', N'''''')
    + N''' AS SourceColumn, CONVERT(NVARCHAR(4000), ' + QUOTENAME(c.name)
    + N') AS BadValue FROM ' + QUOTENAME(s.name) + N'.' + QUOTENAME(t.name)
    + N' WHERE CONVERT(NVARCHAR(MAX), ' + QUOTENAME(c.name)
    + N') COLLATE Latin1_General_BIN2 LIKE N''%Ã%'' OR CONVERT(NVARCHAR(MAX), ' + QUOTENAME(c.name)
    + N') COLLATE Latin1_General_BIN2 LIKE N''%â€%'''),
    N' UNION ALL ')
FROM sys.columns c
JOIN sys.tables t ON t.object_id = c.object_id
JOIN sys.schemas s ON s.schema_id = t.schema_id
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE ty.name IN (N'nvarchar', N'nchar', N'ntext')
  AND t.is_ms_shipped = 0;

IF @sql IS NOT NULL AND LEN(@sql) > 0 EXEC sp_executesql @sql;

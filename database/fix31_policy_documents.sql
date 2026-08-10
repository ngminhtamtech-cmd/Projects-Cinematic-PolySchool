-- fix31_policy_documents.sql
-- Versioned, escaped policy content used by the refund-policy page.
SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.PolicyDocuments', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.PolicyDocuments (
            Id INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_PolicyDocuments PRIMARY KEY,
            PolicyKey NVARCHAR(80) NOT NULL,
            VersionNumber INT NOT NULL,
            Title NVARCHAR(200) NOT NULL,
            BodyText NVARCHAR(MAX) NOT NULL,
            Status NVARCHAR(20) NOT NULL CONSTRAINT CK_PolicyDocuments_Status
                CHECK (Status IN ('draft','published','archived')),
            UpdatedBy INT NULL,
            UpdatedAt DATETIME2(3) NOT NULL CONSTRAINT DF_PolicyDocuments_UpdatedAt DEFAULT SYSDATETIME(),
            PublishedAt DATETIME2(3) NULL,
            RowVersion ROWVERSION,
            CONSTRAINT FK_PolicyDocuments_UpdatedBy FOREIGN KEY (UpdatedBy) REFERENCES dbo.Users(Id)
        );
        CREATE UNIQUE INDEX UX_PolicyDocuments_KeyVersion
            ON dbo.PolicyDocuments(PolicyKey, VersionNumber);
        CREATE UNIQUE INDEX UX_PolicyDocuments_Current
            ON dbo.PolicyDocuments(PolicyKey, Status)
            WHERE Status IN ('draft','published');
    END;

    IF NOT EXISTS (SELECT 1 FROM dbo.PolicyDocuments WHERE PolicyKey=N'refund-policy' AND Status='published')
    BEGIN
        INSERT INTO dbo.PolicyDocuments
            (PolicyKey, VersionNumber, Title, BodyText, Status, UpdatedAt, PublishedAt)
        VALUES
            (N'refund-policy', 1, N'Điều kiện hoàn tiền',
             N'Đơn vé phải đã thanh toán và chưa check-in, chưa hoàn tiền hoặc bị từ chối trước đó.\n\n'
             + N'Hoàn tiền thông thường chỉ thực hiện khi còn đủ thời gian trước suất chiếu theo cấu hình hiện hành.\n\n'
             + N'Vé bỏ lỡ suất chỉ được gửi yêu cầu sau khi suất kết thúc để quản lý xem xét.\n\n'
             + N'Khi hoàn tiền thành công, ghế được trả lại, mã khuyến mãi/voucher được hoàn lượt dùng và điểm loyalty liên quan được đảo lại theo giao dịch.',
             'published', SYSDATETIME(), SYSDATETIME());
    END;

    UPDATE dbo.PolicyDocuments
       SET BodyText = REPLACE(BodyText, N'\n', CHAR(13)+CHAR(10))
     WHERE PolicyKey=N'refund-policy' AND BodyText LIKE N'%\n%';

    IF NOT EXISTS (SELECT 1 FROM dbo.SystemSettings WHERE SettingKey=N'booking.stateContractVersion')
        INSERT INTO dbo.SystemSettings(SettingKey, SettingValue, UpdatedAt)
        VALUES (N'booking.stateContractVersion', N'1', GETDATE());

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

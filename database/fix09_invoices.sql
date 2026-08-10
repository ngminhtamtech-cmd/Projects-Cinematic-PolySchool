SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.InvoiceSequence', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.InvoiceSequence (
        SequenceYear INT NOT NULL CONSTRAINT PK_InvoiceSequence PRIMARY KEY,
        NextValue INT NOT NULL,
        CONSTRAINT CK_InvoiceSequence_NextValue CHECK (NextValue > 0)
    );
END;

IF OBJECT_ID(N'dbo.Invoices', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Invoices (
        Id INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Invoices PRIMARY KEY,
        OrderId INT NOT NULL,
        InvoiceNo VARCHAR(20) NOT NULL,
        InvoiceType VARCHAR(20) NOT NULL CONSTRAINT DF_Invoices_Type DEFAULT 'sale',
        OriginalInvoiceId INT NULL,
        IssuedAt DATETIME2(0) NOT NULL CONSTRAINT DF_Invoices_IssuedAt DEFAULT GETDATE(),
        TaxCode NVARCHAR(50) NULL,
        BuyerName NVARCHAR(255) NOT NULL,
        BuyerTaxCode NVARCHAR(50) NULL,
        SubTotal DECIMAL(19,2) NOT NULL,
        VatRate DECIMAL(5,2) NOT NULL,
        VatAmount DECIMAL(19,2) NOT NULL,
        Total DECIMAL(19,2) NOT NULL,
        PdfPath NVARCHAR(500) NULL,
        CONSTRAINT UQ_Invoices_InvoiceNo UNIQUE (InvoiceNo),
        CONSTRAINT FK_Invoices_Order FOREIGN KEY (OrderId) REFERENCES dbo.Orders(Id),
        CONSTRAINT FK_Invoices_Original FOREIGN KEY (OriginalInvoiceId) REFERENCES dbo.Invoices(Id),
        CONSTRAINT CK_Invoices_Amounts CHECK (SubTotal >= 0 AND VatAmount >= 0 AND Total >= 0),
        CONSTRAINT CK_Invoices_VatRate CHECK (VatRate >= 0 AND VatRate <= 100),
        CONSTRAINT CK_Invoices_Type CHECK (InvoiceType IN ('sale', 'refund'))
    );
    CREATE UNIQUE INDEX UX_Invoices_Order_Sale ON dbo.Invoices(OrderId)
        WHERE InvoiceType = 'sale';
    CREATE INDEX IX_Invoices_OrderId ON dbo.Invoices(OrderId, IssuedAt DESC);
END;

COMMIT TRANSACTION;

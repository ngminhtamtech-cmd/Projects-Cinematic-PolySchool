package com.mycompany.website.ban.ve.xem.phim.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.InvoiceDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcInvoiceDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcOrderDAO;
import com.mycompany.website.ban.ve.xem.phim.model.Invoice;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal VAT invoices. Amounts stored on an order are VAT-inclusive, therefore an invoice
 * splits that immutable total instead of adding tax on top of it.
 */
public class InvoiceService {
    public static final BigDecimal DEFAULT_VAT_RATE = new BigDecimal("10.00");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final InvoiceDAO invoiceDAO;
    private final JdbcOrderDAO orderDAO;

    public InvoiceService() {
        this(new JdbcInvoiceDAO(), new JdbcOrderDAO());
    }

    InvoiceService(InvoiceDAO invoiceDAO, JdbcOrderDAO orderDAO) {
        this.invoiceDAO = invoiceDAO;
        this.orderDAO = orderDAO;
    }

    public Invoice issueSale(int orderId) {
        Optional<Invoice> existing = invoiceDAO.findSaleByOrderId(orderId);
        if (existing.isPresent()) return existing.get();
        return issue(orderId, null, "sale", null);
    }

    public Invoice issueRefundAdjustment(int orderId, BigDecimal amount) {
        Invoice original = issueSale(orderId);
        return issue(orderId, amount, "refund", original.getId());
    }

    private Invoice issue(int orderId, BigDecimal adjustedTotal, String type, Integer originalId) {
        Invoice invoice = new Invoice();
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                OrderSnapshot order = lockPaidOrder(c, orderId);
                if ("sale".equals(type)) {
                    Optional<Invoice> concurrentlyCreated = findSaleForUpdate(c, orderId);
                    if (concurrentlyCreated.isPresent()) {
                        c.rollback();
                        return concurrentlyCreated.get();
                    }
                }
                BigDecimal total = adjustedTotal == null ? order.total : adjustedTotal;
                BigDecimal rate = vatRate(c);
                VatBreakdown vat = calculateVat(total, rate);

                invoice.setOrderId(orderId);
                invoice.setInvoiceNo(nextInvoiceNo(c));
                invoice.setInvoiceType(type);
                invoice.setOriginalInvoiceId(originalId);
                invoice.setIssuedAt(order.now);
                invoice.setBuyerName(order.buyerName == null || order.buyerName.isBlank()
                        ? "Khách hàng CineBook" : order.buyerName);
                invoice.setSubTotal(vat.subTotal());
                invoice.setVatRate(rate);
                invoice.setVatAmount(vat.vatAmount());
                invoice.setTotal(total.setScale(2, RoundingMode.HALF_UP));
                invoice.setId(invoiceDAO.insert(c, invoice));
                c.commit();
            } catch (RuntimeException | SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể phát hành hóa đơn.", ex);
        }

        Path path = invoicePath(invoice);
        Path temp = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(temp)) {
                renderPdf(invoice, orderDAO.findById(orderId)
                        .orElseThrow(() -> new BookingException(404, "Không tìm thấy đơn hàng.")), out);
            }
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            invoice.setPdfPath(path.toString());
            invoiceDAO.updatePdfPath(invoice.getId(), invoice.getPdfPath());
            return invoice;
        } catch (IOException ex) {
            throw new BookingException(500, "Không thể ghi tập tin hóa đơn.", ex);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(InvoiceService.class.getName())
                        .warning("Cannot remove invoice temp file: " + ex.getMessage());
            }
        }
    }

    public void renderPdf(Invoice invoice, OrderRecord order, OutputStream output) {
        Document document = new Document(PageSize.A4, 42, 42, 42, 42);
        try {
            PdfWriter.getInstance(document, output);
            BaseFont base = BaseFont.createFont(fontPath().toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            Font title = new Font(base, 18, Font.BOLD);
            Font heading = new Font(base, 11, Font.BOLD);
            Font body = new Font(base, 10);
            document.open();

            Paragraph h = new Paragraph("HÓA ĐƠN GIÁ TRỊ GIA TĂNG", title);
            h.setAlignment(Element.ALIGN_CENTER);
            document.add(h);
            Paragraph no = new Paragraph("Số: " + invoice.getInvoiceNo(), heading);
            no.setAlignment(Element.ALIGN_CENTER);
            no.setSpacingAfter(18);
            document.add(no);

            document.add(new Paragraph("Đơn vị bán hàng: CINEBOOK", heading));
            document.add(new Paragraph("Người mua: " + invoice.getBuyerName(), body));
            document.add(new Paragraph("Ngày phát hành: " + invoice.getIssuedAt().format(DATE), body));
            document.add(new Paragraph("Mã đơn: #" + invoice.getOrderId(), body));
            document.add(new Paragraph("Mã vé: " + safe(order.getTicketCode()), body));
            document.add(new Paragraph("Phim: " + safe(order.getFilmTitle()), body));
            document.add(new Paragraph("Rạp / phòng: " + safe(order.getCinemaName())
                    + " / " + safe(order.getRoomName()), body));
            document.add(new Paragraph("Suất chiếu: "
                    + (order.getStartTime() == null ? "" : order.getStartTime().format(DATE)), body));

            PdfPTable table = new PdfPTable(new float[]{5, 2});
            table.setWidthPercentage(100);
            table.setSpacingBefore(18);
            addCell(table, "Nội dung", heading);
            addCell(table, "Số tiền (VND)", heading);
            addCell(table, "Vé xem phim và dịch vụ kèm theo", body);
            addCell(table, money(invoice.getSubTotal()), body);
            addCell(table, "Thuế GTGT (" + invoice.getVatRate().stripTrailingZeros().toPlainString() + "%)", body);
            addCell(table, money(invoice.getVatAmount()), body);
            addCell(table, "TỔNG CỘNG", heading);
            addCell(table, money(invoice.getTotal()), heading);
            document.add(table);
            document.add(new Paragraph("Ghế: " + order.getSeatSummary(), body));
            if (!order.getComboSummary().isBlank()) {
                document.add(new Paragraph("Combo: " + order.getComboSummary(), body));
            }
            if ("refund".equals(invoice.getInvoiceType())) {
                document.add(new Paragraph("HÓA ĐƠN ĐIỀU CHỈNH HOÀN TIỀN", heading));
            }
        } catch (Exception ex) {
            throw new BookingException(500, "Không thể tạo nội dung PDF hóa đơn.", ex);
        } finally {
            if (document.isOpen()) document.close();
        }
    }

    public static VatBreakdown calculateVat(BigDecimal inclusiveTotal, BigDecimal vatRate) {
        if (inclusiveTotal == null || inclusiveTotal.signum() < 0) {
            throw new IllegalArgumentException("Total must be non-negative");
        }
        if (vatRate == null || vatRate.signum() < 0 || vatRate.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("VAT rate must be between 0 and 100");
        }
        BigDecimal divisor = BigDecimal.ONE.add(vatRate.movePointLeft(2));
        BigDecimal subTotal = inclusiveTotal.divide(divisor, 2, RoundingMode.HALF_UP);
        BigDecimal total = inclusiveTotal.setScale(2, RoundingMode.HALF_UP);
        return new VatBreakdown(subTotal, total.subtract(subTotal));
    }

    public record VatBreakdown(BigDecimal subTotal, BigDecimal vatAmount) {}

    private OrderSnapshot lockPaidOrder(Connection c, int orderId) throws SQLException {
        String sql = """
                SELECT o.TotalAmount, u.FullName, GETDATE() AS DbNow
                FROM Orders o WITH (UPDLOCK, HOLDLOCK)
                JOIN Users u ON u.Id=o.UserId
                WHERE o.Id=? AND o.PaymentStatus IN ('paid', 'refunded')
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new BookingException(400, "Chỉ phát hành hóa đơn cho đơn đã thanh toán.");
                return new OrderSnapshot(rs.getBigDecimal("TotalAmount"), rs.getString("FullName"),
                        rs.getTimestamp("DbNow").toLocalDateTime());
            }
        }
    }

    private Optional<Invoice> findSaleForUpdate(Connection c, int orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT Id FROM Invoices WITH (UPDLOCK, HOLDLOCK) WHERE OrderId=? AND InvoiceType='sale'")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
            }
        }
        return invoiceDAO.findSaleByOrderId(orderId);
    }

    private String nextInvoiceNo(Connection c) throws SQLException {
        int year;
        try (PreparedStatement ps = c.prepareStatement("SELECT YEAR(GETDATE())");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            year = rs.getInt(1);
        }
        try (PreparedStatement ps = c.prepareStatement("""
                MERGE InvoiceSequence WITH (HOLDLOCK) AS target
                USING (SELECT ? AS SequenceYear) AS src
                ON target.SequenceYear=src.SequenceYear
                WHEN NOT MATCHED THEN INSERT(SequenceYear, NextValue) VALUES(src.SequenceYear, 2)
                WHEN MATCHED THEN UPDATE SET NextValue=target.NextValue+1
                OUTPUT inserted.NextValue-1;
                """)) {
            ps.setInt(1, year);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Invoice sequence returned no value");
                return "HD" + year + String.format("%06d", rs.getInt(1));
            }
        }
    }

    private BigDecimal vatRate(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT SettingValue FROM SystemSettings WHERE SettingKey='vat.rate'");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next() || rs.getString(1) == null || rs.getString(1).isBlank()) return DEFAULT_VAT_RATE;
            return new BigDecimal(rs.getString(1).trim());
        }
    }

    private Path invoicePath(Invoice invoice) {
        String catalina = System.getProperty("catalina.base");
        Path root = catalina == null || catalina.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "cinebook")
                : Path.of(catalina);
        return root.resolve("cinebook-invoices").resolve(invoice.getInvoiceNo() + ".pdf");
    }

    private Path fontPath() {
        String configured = System.getProperty("cinebook.invoice.font");
        if (configured == null || configured.isBlank()) configured = System.getenv("CINEBOOK_INVOICE_FONT");
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) return path;
            throw new BookingException(500, "Font hóa đơn không tồn tại: " + path);
        }
        Path windowsArial = Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"),
                "Fonts", "arial.ttf");
        if (Files.isRegularFile(windowsArial)) return windowsArial;
        throw new BookingException(500, "Thiếu font Unicode. Cấu hình cinebook.invoice.font.");
    }

    private void addCell(PdfPTable table, String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(7);
        table.addCell(cell);
    }

    private String money(BigDecimal value) {
        return String.format(java.util.Locale.US, "%,.0f", value).replace(',', '.') + " đ";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record OrderSnapshot(BigDecimal total, String buyerName, LocalDateTime now) {}
}

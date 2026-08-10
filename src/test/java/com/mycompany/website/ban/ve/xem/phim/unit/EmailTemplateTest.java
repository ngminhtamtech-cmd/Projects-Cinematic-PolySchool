package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.service.EmailService;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateTest {
    @Test
    void allFiveTemplatesRenderVietnameseWithoutUnresolvedPlaceholders() {
        assertTemplate("verify-email.txt", Map.of("name", "Nguyễn An", "verifyLink", "https://x/verify"));
        assertTemplate("showtime-cancelled.txt", Map.of(
                "name", "Nguyễn An", "film", "Mắt biếc", "showtime", "20:00",
                "cinema", "CineBook Huế", "orderId", "7"));
        assertTemplate("password-reset.txt", Map.of(
                "name", "Nguyễn An", "resetLink", "https://x/reset", "minutes", "30"));
        assertTemplate("counter-reminder.txt", Map.of(
                "name", "Nguyễn An", "orderId", "7", "ticketCode", "CBABC", "expiresAt", "20:00"));
    }

    private void assertTemplate(String name, Map<String, String> values) {
        String body = EmailService.renderTemplate(name, values);
        assertTrue(body.contains("Nguyễn An"));
        assertFalse(body.contains("{{"));
    }

    // ------------------------------------------------------------------ thu HTML (bang)

    @Test
    @DisplayName("Sau template HTML moi deu render het placeholder va nam trong khung chung")
    void allHtmlTemplatesRenderInsideTheSharedLayout() {
        assertHtml("promotion-created.html", Map.of(
                "name", "Nguyễn An", "code", "SALE50", "description", "Giảm nửa giá",
                "discount", "50%", "maxDiscount", "100.000 ₫", "endDate", "31/12/2026",
                "audience", "Mọi thành viên"));
        assertHtml("checkin-success.html", Map.of(
                "name", "Nguyễn An", "ticketCode", "CBABC", "film", "Mắt biếc",
                "cinema", "CineBook Huế", "showtime", "20:00 06/09/2026", "seats", "A1, A2",
                "redeemedAt", "19:45 06/09/2026"));
        assertHtml("appeal-approved.html", Map.of(
                "name", "Nguyễn An", "appealId", "7", "submittedAt", "01/09/2026",
                "adminResponse", "Đã xem xét lại"));
        assertHtml("appeal-rejected.html", Map.of(
                "name", "Nguyễn An", "appealId", "7", "submittedAt", "01/09/2026",
                "adminResponse", "Vi phạm lặp lại"));
        assertHtml("refund-approved.html", Map.of(
                "name", "Nguyễn An", "orderId", "7", "film", "Mắt biếc",
                "showtime", "20:00 06/09/2026", "refundAmount", "120.000 ₫",
                "reason", "Khách đổi lịch", "refundedAt", "10:00 05/09/2026"));
        assertHtml("refund-rejected.html", Map.of(
                "name", "Nguyễn An", "orderId", "7", "film", "Mắt biếc",
                "showtime", "20:00 06/09/2026", "reason", "Quá hạn hoàn tiền"));
    }

    /**
     * Thu ve phai tro toi anh QR nhung, khong phai mot duong dan.
     *
     * <p>Ban cu in ra dong {@code QR vé: /tickets/qr/CBxxxx} — duong dan <b>tuong doi</b>, trong
     * hop thu thi khong tro toi dau ca. Ca nay chot lai hai dieu: the {@code <img>} dung so do
     * {@code cid:} khop voi Content-ID ma {@code EmailService} gan, va khong con dau vet cua duong
     * dan cu.</p>
     */
    @Test
    @DisplayName("Thu ve nhung ma QR bang cid:, khong con duong dan tuong doi")
    void ticketMailEmbedsQrByContentId() {
        String body = EmailService.renderHtmlDocument("ticket-paid.html", "Vé điện tử #7", Map.of(
                "name", "Nguyễn An", "orderId", "7", "ticketCode", "CBABC",
                "film", "Mắt biếc", "cinema", "CineBook Huế", "showtime", "20:00 06/09/2026"));

        assertTrue(body.contains("src=\"cid:ticket-qr\""),
                "Anh QR phai tro toi Content-ID ma EmailService gan cho phan anh");
        assertFalse(body.contains("/tickets/qr/"),
                "Khong duoc con duong dan tuong doi — trong hop thu no khong tro toi dau ca");
        assertFalse(body.contains("{{"), "Con placeholder chua thay");
    }

    /**
     * Gia tri nhet vao thu HTML phai duoc escape.
     *
     * <p>Day la ca quan trong nhat cua ca bo: <b>ly do tu choi do quan tri vien tu go</b> di thang
     * vao {@code refund-rejected.html} va {@code appeal-rejected.html}. Thieu escape thi mot dau
     * {@code &} lam hong hien thi, con mot the {@code <} cho phep chen markup vao la thu gui cho
     * khach. O thu text thuan truoc day chung vo hai, nen khong co gi chan.</p>
     */
    @Test
    @DisplayName("Gia tri co ky tu HTML bi escape, khong lot nguyen the vao than thu")
    void valuesAreHtmlEscaped() {
        String body = EmailService.renderHtmlDocument("refund-rejected.html", "Tiêu đề", Map.of(
                "name", "Nguyễn An", "orderId", "7", "film", "Phim <script>alert(1)</script>",
                "showtime", "20:00", "reason", "Lý do có & và <b>đậm</b> và \"nháy\""));

        assertFalse(body.contains("<script>"), "The <script> phai bi escape, khong duoc lot vao thu");
        assertFalse(body.contains("<b>đậm</b>"), "Markup nguoi dung go phai bi escape");
        assertTrue(body.contains("&lt;script&gt;"), "Phai thay bang thuc the &lt;");
        assertTrue(body.contains("&amp;"), "Dau & phai thanh &amp;");
        assertTrue(body.contains("&quot;"), "Dau nhay kep phai thanh &quot;");
    }

    /**
     * O {{{content}}} phai chen THO.
     *
     * <p>Neu escape lan hai thi ca cai bang bien thanh chuoi &lt;table&gt;... hien ra man hinh —
     * dung nghia den la nguoi nhan doc duoc ma HTML thay vi thay bang.</p>
     */
    @Test
    @DisplayName("Khung chung chen noi dung tho, khong escape lan hai")
    void layoutInsertsContentRaw() {
        String body = EmailService.renderHtmlDocument("checkin-success.html", "Check-in", Map.of(
                "name", "An", "ticketCode", "CBABC", "film", "Phim", "cinema", "Rạp",
                "showtime", "20:00", "seats", "A1", "redeemedAt", "19:45"));

        assertTrue(body.contains("<table"), "Bang trong noi dung phai con la the that");
        assertFalse(body.contains("&lt;table"), "Bang bi escape lan hai — nguoi nhan se doc thay ma HTML");
    }

    private void assertHtml(String name, Map<String, String> values) {
        String body = EmailService.renderHtmlDocument(name, "Tiêu đề thư", values);
        assertFalse(body.contains("{{"), name + " con placeholder chua thay");
        assertTrue(body.contains("<!DOCTYPE html>"), name + " phai nam trong khung layout.html");
        assertTrue(body.contains("Nguyễn An") || body.contains("An"), name + " phai co ten nguoi nhan");
        assertTrue(body.contains("vui lòng không trả lời thư này"), name + " thieu chan thu cua khung");
    }

    /**
     * Ba chang SMTP deu phai co han cho.
     *
     * <p>JavaMail mac dinh cho <b>vo han</b>, ma pool gui chi co 2 thread: hai la thu gui toi mot
     * may chu khong phan hoi la chiem het pool, moi thu sau do xep hang vinh vien va khong bao gio
     * duoc gui, cung khong bao loi. Bo bat ky khoa nao trong ba khoa nay la mo lai dung duong do,
     * nen chot chung o day.</p>
     */
    @Test
    @DisplayName("Cau hinh SMTP luon co du ba han timeout, khong de JavaMail cho vo han")
    void smtpPropertiesAlwaysCarryAllThreeTimeouts() {
        Properties config = new Properties();
        config.setProperty("mail.smtp.host", "smtp.example.com");
        config.setProperty("mail.smtp.username", "mailer@example.com");
        config.setProperty("mail.smtp.password", "secret");

        Properties props = EmailService.smtpProperties(config);

        assertEquals("10000", props.getProperty("mail.smtp.connectiontimeout"));
        assertEquals("10000", props.getProperty("mail.smtp.timeout"));
        assertEquals("10000", props.getProperty("mail.smtp.writetimeout"));
        assertEquals("587", props.getProperty("mail.smtp.port"), "Cong mac dinh la 587 (STARTTLS)");
        assertEquals("true", props.getProperty("mail.smtp.starttls.enable"));
    }

    @Test
    @DisplayName("Ba han timeout deu doc de duoc tu file cau hinh")
    void smtpTimeoutsCanBeOverriddenFromConfig() {
        Properties config = new Properties();
        config.setProperty("mail.smtp.host", "smtp.example.com");
        config.setProperty("mail.smtp.connectiontimeout", "3000");
        config.setProperty("mail.smtp.timeout", "4000");
        config.setProperty("mail.smtp.writetimeout", "5000");

        Properties props = EmailService.smtpProperties(config);

        assertEquals("3000", props.getProperty("mail.smtp.connectiontimeout"));
        assertEquals("4000", props.getProperty("mail.smtp.timeout"));
        assertEquals("5000", props.getProperty("mail.smtp.writetimeout"));
    }
}

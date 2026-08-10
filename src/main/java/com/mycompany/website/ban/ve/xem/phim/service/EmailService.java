package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.config.DatabaseConfig;
import com.mycompany.website.ban.ve.xem.phim.util.QrCodeUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.Locale;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.activation.DataHandler;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

public class EmailService {
    private static final Logger LOG = Logger.getLogger(EmailService.class.getName());
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Object FILE_LOCK = new Object();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ExecutorService DELIVERY = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "cinebook-mail-delivery");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Han timeout mac dinh cho moi chang SMTP, tinh bang ms.
     *
     * <p>JavaMail mac dinh cho doi <b>vo han</b>. Pool gui chi co 2 thread, nen hai la thu gui
     * toi mot may chu SMTP khong phan hoi la chiem het pool; moi thu sau do xep hang trong
     * {@code LinkedBlockingQueue} khong gioi han va khong bao gio duoc gui, cung khong bao loi.
     * O che do {@code logfile} rui ro nay khong ton tai vi {@code send()} tra ve ngay.</p>
     */
    private static final String DEFAULT_SMTP_TIMEOUT_MS = "10000";

    /** Khung chung cua moi thu HTML — xem {@link #renderHtmlDocument}. */
    private static final String LAYOUT_TEMPLATE = "layout.html";

    /** Content-ID cua anh QR nhung trong thu ve; template tro toi bang {@code cid:} cung ten. */
    private static final String TICKET_QR_CID = "ticket-qr";

    private static final DateTimeFormatter DISPLAY_DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Cau hinh va che do gui doc lai moi 60 giay — doi trong /system/config van co tac dung. */
    private static final long CONFIG_CACHE_TTL_MS = 60_000L;

    private static volatile Cached<String> cachedMode;
    private static volatile Cached<Properties> cachedConfig;

    public void sendVerification(String email, String name, String verifyLink) {
        send(email, "CineBook — Xác thực email", "verify-email.txt",
                Map.of("name", safe(name), "verifyLink", verifyLink));
    }

    public void createAndSendVerification(int userId, String email, String name, String applicationBase) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = PasswordResetService.sha256Hex(token);
        // EmailVerifySentAt phai duoc ghi ngay tu la thu DAU TIEN. Bo trong no thi menh de
        // "EmailVerifySentAt IS NULL" cua resendVerification cho qua, nen nguoi vua dang ky bam
        // "Gui lai" phat nua van lot — cooldown coi nhu khong ton tai o dung lan de bi lam dung nhat.
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE Users SET EmailVerifyTokenHash=?, EmailVerifiedAt=NULL,
                       EmailVerifySentAt=GETDATE(), UpdatedAt=GETDATE() WHERE Id=?
                     """)) {
            ps.setString(1, hash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            LOG.log(Level.SEVERE, "Cannot create email verification token for userId=" + userId, ex);
            return;
        }
        sendVerification(email, name, applicationBase + "/verify-email?token=" + token);
    }

    public boolean verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return false;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE Users SET EmailVerifiedAt=GETDATE(), EmailVerifyTokenHash=NULL,
                       UpdatedAt=GETDATE()
                     WHERE EmailVerifyTokenHash=? AND EmailVerifiedAt IS NULL
                     """)) {
            ps.setString(1, PasswordResetService.sha256Hex(rawToken));
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            LOG.log(Level.SEVERE, "Cannot verify email", ex);
            return false;
        }
    }

    /** Ket qua cua mot lan bam "Xac thuc ngay". */
    public enum ResendOutcome {
        /** Da tao token moi va day email vao hang doi gui. */
        SENT,
        /** Tai khoan da xac thuc roi — khong can lam gi. */
        ALREADY_VERIFIED,
        /** Bam lai qua som; con phai cho het thoi gian cho. */
        COOLDOWN
    }

    /**
     * Gui lai email xac thuc cho mot tai khoan dang dang nhap (EM-01).
     *
     * <p><b>Vi sao khong dung mot link GET.</b> Banner cu chi co chu, khong co hanh dong. Cach
     * hay bi lam sai la them mot the {@code <a href="/verify-email">} — nhung endpoint do can
     * token, nen bam vao chi bao loi. Xac thuc lai ban chat la mot thao tac <i>ghi</i> (sinh
     * token moi, vo hieu token cu, gui mail), nen phai la POST co CSRF.</p>
     *
     * <p><b>Chong lam dung.</b> Moc gui gan nhat luu trong DB nen dang xuat, doi trinh duyet hay
     * restart Tomcat deu khong reset duoc bo dem. Token cu bi ghi de ngay khi sinh token moi:
     * moi thoi diem chi ton tai <b>mot</b> token hop le.</p>
     *
     * <p>Ham nay khong bao gio tiet lo email cua tai khoan khac: no chi lam viec voi
     * {@code userId} lay tu session.</p>
     */
    public ResendOutcome resendVerification(int userId, String applicationBase, int cooldownSeconds) {
        try (Connection connection = DBConnection.getConnection()) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            // N-15: mot cau lenh DUY NHAT vua kiem cooldown vua ghi moc gui.
            //
            // Ban cu tach lam hai: mot SELECT tinh CanSend roi mot UPDATE ghi
            // EmailVerifySentAt = GETDATE(). Hai cau roi nhau, auto-commit, khong khoa dong,
            // nen 20 POST /resend-verification dong thoi deu doc CanSend = 1 truoc khi ai kip
            // UPDATE — 20 email vao hang doi. Nay dieu kien cooldown nam trong chinh menh de
            // WHERE cua UPDATE: SQL Server khoa dong khi ghi va danh gia lai menh de sau khi
            // ban ghi truoc commit, nen dung MOT request thang.
            int updated;
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE Users SET EmailVerifyTokenHash=?, EmailVerifySentAt=GETDATE(),
                      UpdatedAt=GETDATE()
                    WHERE Id=? AND EmailVerifiedAt IS NULL
                      AND (EmailVerifySentAt IS NULL
                           OR DATEDIFF(SECOND, EmailVerifySentAt, GETDATE()) >= ?)
                    """)) {
                ps.setString(1, PasswordResetService.sha256Hex(token));
                ps.setInt(2, userId);
                ps.setInt(3, Math.max(0, cooldownSeconds));
                updated = ps.executeUpdate();
            }

            if (updated != 1) {
                return resendRejectionReason(connection, userId);
            }

            // Email/FullName doc sau khi da gianh duoc luot gui; hai truong nay khong doi
            // trong luong resend nen khong can nam trong cau UPDATE.
            String email = null;
            String fullName = null;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT Email, FullName FROM Users WHERE Id = ?")) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        email = rs.getString("Email");
                        fullName = rs.getString("FullName");
                    }
                }
            }
            if (email == null) {
                // Tai khoan bien mat giua hai buoc — khong gui di dau ca.
                return ResendOutcome.ALREADY_VERIFIED;
            }
            sendVerification(email, fullName, applicationBase + "/verify-email?token=" + token);
            return ResendOutcome.SENT;
        } catch (SQLException ex) {
            // Khong nuot: log kem ngu canh roi bao loi len tang tren. Nguoi dung phai biet la
            // email KHONG duoc gui, thay vi thay "da gui" roi ngoi cho mai.
            LOG.log(Level.SEVERE, "Cannot resend verification email for userId=" + userId, ex);
            throw new BookingException(500, "Không thể gửi lại email xác thực lúc này. Vui lòng thử lại sau.");
        }
    }

    /**
     * Vi sao cau UPDATE cua {@link #resendVerification} khong ap duoc dong nao.
     *
     * <p>Chi chay khi da chac chan KHONG gui email, nen doc them mot cau o day khong tao ra
     * dieu kien tranh chap moi. Tai khoan khong ton tai duoc bao la {@code ALREADY_VERIFIED}
     * de khong lo ra su ton tai cua tai khoan khac.</p>
     */
    private ResendOutcome resendRejectionReason(Connection connection, int userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT EmailVerifiedAt FROM Users WHERE Id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getTimestamp("EmailVerifiedAt") != null) {
                    return ResendOutcome.ALREADY_VERIFIED;
                }
                return ResendOutcome.COOLDOWN;
            }
        }
    }

    /**
     * Ve dien tu, <b>kem ma QR nhung thang trong thu</b>.
     *
     * <p><b>Vi sao nhung anh chu khong dat mot duong dan.</b> Ban cu in ra dong
     * {@code QR vé: /tickets/qr/CBxxxx} — mot duong dan <i>tuong doi</i>, trong hop thu thi khong
     * tro toi dau ca. Ma co viet thanh duong dan tuyet doi thi van hong: route do doi phien dang
     * nhap, va Gmail chan anh ngoai theo mac dinh. Anh nhung theo dang {@code cid:} di kem chinh
     * la thu nen hien duoc ngay, khong can mang, khong can dang nhap.</p>
     *
     * <p>Noi dung ma QR dung y het {@code TicketQrServlet}: {@code QrCodeUtil.signedPayload} o
     * 280px — quet o rap ra cung mot ket qua du khach mo tu thu hay tu web.</p>
     */
    public void sendTicket(String email, String name, int orderId, String ticketCode,
            String film, String cinema, String showtime) {
        send(email, "CineBook — Vé điện tử #" + orderId, "ticket-paid.html", Map.of(
                "name", safe(name), "orderId", String.valueOf(orderId), "ticketCode", safe(ticketCode),
                "film", safe(film), "cinema", safe(cinema), "showtime", safe(showtime)),
                ticketQr(ticketCode));
    }

    /**
     * Ma QR cua mot ve duoi dang PNG de nhung vao thu.
     *
     * <p>Sinh anh hong thi tra ve {@code null} chu khong nem: khach van phai nhan duoc thu voi day
     * du ma ve va thong tin suat chieu — thieu anh QR con hon khong co thu nao.</p>
     */
    private InlineImage ticketQr(String ticketCode) {
        try {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            QrCodeUtil.writePng(QrCodeUtil.signedPayload(ticketCode), 280, png);
            return new InlineImage(TICKET_QR_CID, "ve-" + safe(ticketCode) + ".png",
                    png.toByteArray(), "image/png");
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.SEVERE, "Khong the sinh ma QR cho ve " + ticketCode
                    + "; van gui thu nhung khong co anh QR.", ex);
            return null;
        }
    }

    /** Anh nhung trong thu, gui kem duoi dang {@code multipart/related}. */
    public record InlineImage(String contentId, String fileName, byte[] data, String mimeType) {
    }

    public void sendShowtimeCancellation(String email, String name, int orderId,
            String film, String cinema, String showtime) {
        send(email, "CineBook — Thông báo hủy suất chiếu", "showtime-cancelled.txt", Map.of(
                "name", safe(name), "orderId", String.valueOf(orderId), "film", safe(film),
                "cinema", safe(cinema), "showtime", safe(showtime)));
    }

    public void sendPasswordReset(String email, String name, String resetLink, int minutes) {
        send(email, "CineBook — Đặt lại mật khẩu", "password-reset.txt", Map.of(
                "name", safe(name), "resetLink", resetLink, "minutes", String.valueOf(minutes)));
    }

    public void sendCounterReminder(String email, String name, int orderId,
            String ticketCode, String expiresAt) {
        send(email, "CineBook — Đơn tại quầy sắp hết hạn", "counter-reminder.txt", Map.of(
                "name", safe(name), "orderId", String.valueOf(orderId),
                "ticketCode", safe(ticketCode), "expiresAt", safe(expiresAt)));
    }

    /**
     * Kieu noi dung suy ra tu <b>duoi file template</b>.
     *
     * <p>{@code .html} gui {@code text/html}, moi thu con lai giu {@code text/plain}. Chon theo duoi
     * file thay vi mot tham so boolean vi tham so thi quen duoc, con duoi file thi nam ngay trong
     * ten template tai cho goi — khong the lech giua noi dung va kieu khai bao.</p>
     */
    private static boolean isHtml(String template) {
        return template != null && template.toLowerCase().endsWith(".html");
    }

    // ------------------------------------------------------------------ thu nghiep vu (HTML)

    /** Uu dai moi vua duoc tao — gui cho member thuoc dung doi tuong. */
    public void sendPromotionAnnouncement(String email, String name, String code, String description,
            String discount, String maxDiscount, String endDate, String audience) {
        send(email, "CineBook — Ưu đãi mới: " + safe(code), "promotion-created.html", Map.of(
                "name", safe(name), "code", safe(code), "description", safe(description),
                "discount", safe(discount), "maxDiscount", safe(maxDiscount),
                "endDate", safe(endDate), "audience", safe(audience)));
    }

    /** Ve da duoc quet vao rap. */
    public void sendCheckInSuccess(String email, String name, String ticketCode, String film,
            String cinema, String showtime, String seats, String redeemedAt) {
        send(email, "CineBook — Check-in thành công", "checkin-success.html", Map.of(
                "name", safe(name), "ticketCode", safe(ticketCode), "film", safe(film),
                "cinema", safe(cinema), "showtime", safe(showtime), "seats", safe(seats),
                "redeemedAt", safe(redeemedAt)));
    }

    /**
     * Ket qua don khang cao tai khoan bi khoa.
     *
     * <p>Van gui cho tai khoan dang bi khoa — do chinh la nguoi can la thu nay nhat.
     * {@code IsLocked} chan dang nhap, khong chan nhan thu.</p>
     */
    public void sendAppealResult(String email, String name, int appealId, boolean approved,
            String submittedAt, String adminResponse) {
        String response = adminResponse == null || adminResponse.isBlank()
                ? "(Quản trị viên không để lại ghi chú)" : adminResponse;
        send(email,
                approved ? "CineBook — Đơn kháng cáo đã được duyệt" : "CineBook — Kết quả đơn kháng cáo",
                approved ? "appeal-approved.html" : "appeal-rejected.html", Map.of(
                        "name", safe(name), "appealId", String.valueOf(appealId),
                        "submittedAt", safe(submittedAt), "adminResponse", response));
    }

    /** Hoan tien da duoc duyet. */
    public void sendRefundApproved(String email, String name, int orderId, String film,
            String showtime, String refundAmount, String reason, String refundedAt) {
        send(email, "CineBook — Đã hoàn tiền đơn #" + orderId, "refund-approved.html", Map.of(
                "name", safe(name), "orderId", String.valueOf(orderId), "film", safe(film),
                "showtime", safe(showtime), "refundAmount", safe(refundAmount),
                "reason", blankTo(reason, "(Không có ghi chú)"), "refundedAt", safe(refundedAt)));
    }

    /** Hoan tien bi tu choi — ly do la chuoi do quan tri vien tu go, nen phai escape. */
    public void sendRefundRejected(String email, String name, int orderId, String film,
            String showtime, String reason) {
        send(email, "CineBook — Yêu cầu hoàn tiền đơn #" + orderId + " không được chấp thuận",
                "refund-rejected.html", Map.of(
                        "name", safe(name), "orderId", String.valueOf(orderId), "film", safe(film),
                        "showtime", safe(showtime), "reason", blankTo(reason, "(Không có ghi chú)")));
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Tien te dinh dang o tang Java, khong phai trong template.
     *
     * <p>{@code fmt:formatNumber} cua JSTL hong tren toan ung dung, va day khong phai JSP nen cung
     * khong co lua chon nao khac. De {@code BigDecimal.toString()} lot ra thu thi khach doc duoc
     * <i>"120000.00"</i> — dung so nhung khong ai viet tien nhu vay.</p>
     *
     * <p>{@code DecimalFormat} khong an toan da luong nen tao moi moi lan goi; so la thu gui ra
     * khong du de dieu do dang ke.</p>
     */
    public static String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "0 ₫";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", symbols).format(amount) + " ₫";
    }

    /** Moc thoi gian theo dung quy uoc hien thi cua du an: {@code dd/MM/yyyy HH:mm}. */
    public static String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DISPLAY_DATE_TIME);
    }

    /** Ngay theo quy uoc hien thi cua du an: {@code dd/MM/yyyy}. */
    public static String formatDate(LocalDate value) {
        return value == null ? "" : value.format(DISPLAY_DATE);
    }

    private void send(String recipient, String subject, String template, Map<String, String> values) {
        send(recipient, subject, template, values, null);
    }

    private void send(String recipient, String subject, String template, Map<String, String> values,
            InlineImage image) {
        boolean html = isHtml(template);
        String body = html ? renderHtmlDocument(template, subject, values) : renderTemplate(template, values);
        if ("logfile".equalsIgnoreCase(mode())) {
            // Che do logfile khong hien duoc anh; ghi ro co dinh kem de doi chieu duoc.
            writeLog(recipient, subject, image == null
                    ? body : body + System.lineSeparator() + "[Ảnh nhúng: " + image.fileName() + "]");
            return;
        }
        // execute(), KHONG phai submit().
        //
        // submit() goi Throwable vao mot Future ma khong ai doc, nen bat ky Error nao trong
        // luong gui deu bien mat khong de lai MOT dong log: khong warning, khong SEVERE, khong
        // dau vet trong cinebook-mail.log. Do dung la trieu chung gap khi kiem chung SMTP —
        // mot NoClassDefFoundError (jakarta.jakartaee-api che mat javax.mail that) lam ca hai
        // la thu bien mat lang le, va tu ben ngoai nhin y het nhu "da gui thanh cong".
        //
        // execute() de Throwable noi len UncaughtExceptionHandler cua thread thay vi bi nuot;
        // khoi catch duoi con ghi them dau vet de con lay lai duoc noi dung thu.
        DELIVERY.execute(() -> {
            try {
                deliverWithRetry(recipient, subject, body, html, image);
            } catch (RuntimeException | Error ex) {
                LOG.log(Level.SEVERE, "Email delivery aborted by an unexpected fault: " + subject, ex);
                writeFailureTrace(recipient, subject, body);
                throw ex;
            }
        });
    }

    private void deliverWithRetry(String recipient, String subject, String body, boolean html, InlineImage image) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                sendSmtp(recipient, subject, body, html, image);
                return;
            } catch (Exception ex) {
                if (attempt == 3) {
                    LOG.log(Level.SEVERE, "Email delivery failed after 3 attempts: " + subject, ex);
                    writeFailureTrace(recipient, subject, body);
                    return;
                }
                LOG.log(Level.WARNING, "Email attempt " + attempt + " failed; retrying", ex);
                try {
                    TimeUnit.SECONDS.sleep(attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    LOG.log(Level.SEVERE, "Email retry interrupted", interrupted);
                    writeFailureTrace(recipient, subject, body);
                    return;
                }
            }
        }
    }

    /**
     * Thu gui hong thi phai de lai noi dung o cho nguoi ta tim duoc.
     *
     * <p>Gui la bat dong bo, nen luc {@code deliverWithRetry} bo cuoc thi request da tra loi tu
     * lau: nguoi dang ky van doc <i>"Hay kiem tra email de xac thuc tai khoan"</i>, nguoi quen mat
     * khau van doc cau tra loi chung. Ca hai ngoi cho mot la thu khong bao gio toi, va khong ai
     * biet. Ghi lai vao {@code cinebook-mail.log} de quan tri vien mo dung mot file la lay duoc
     * link xac thuc / link dat lai ma xu ly tay cho nguoi dung.</p>
     *
     * <p>Khong bao gio nem tiep: day da la nhanh xu ly loi cuoi cung, hong not thi chi con log.</p>
     */
    private void writeFailureTrace(String recipient, String subject, String body) {
        try {
            writeLog(recipient, "[SMTP THẤT BẠI] " + subject, body);
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "Cannot record failed email trace: " + subject, ex);
        }
    }

    private void sendSmtp(String recipient, String subject, String body, boolean html, InlineImage image) throws Exception {
        Properties config = config();
        String username = required(config, "mail.smtp.username");
        String password = required(config, "mail.smtp.password");
        Properties props = smtpProperties(config);
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(config.getProperty("mail.from", username), "CineBook", "UTF-8"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient, false));
        message.setSubject(subject, "UTF-8");
        if (image != null) {
            message.setContent(relatedContent(body, image));
        } else if (html) {
            message.setContent(body, "text/html; charset=UTF-8");
        } else {
            message.setText(body, "UTF-8");
        }
        Transport.send(message);
    }

    /**
     * Than thu HTML cong anh nhung, dong goi theo {@code multipart/related}.
     *
     * <p>{@code related} chu khong phai {@code mixed}: anh la mot <b>phan cua noi dung</b> duoc the
     * {@code <img src="cid:...">} tro toi, khong phai file dinh kem roi. Dat sai kieu thi Gmail
     * hien ma QR thanh mot file tai ve o cuoi thu thay vi hien ngay trong bang.</p>
     *
     * <p>{@code Content-ID} phai boc trong dau ngoac nhon theo RFC 2392, con thuoc tinh
     * {@code src} thi khong — day la cho de sai va hong am tham (anh khong hien, khong bao loi).</p>
     */
    private MimeMultipart relatedContent(String body, InlineImage image) throws Exception {
        MimeMultipart related = new MimeMultipart("related");

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(body, "text/html; charset=UTF-8");
        related.addBodyPart(htmlPart);

        MimeBodyPart imagePart = new MimeBodyPart();
        imagePart.setDataHandler(new DataHandler(
                new ByteArrayDataSource(image.data(), image.mimeType())));
        imagePart.setContentID("<" + image.contentId() + ">");
        imagePart.setDisposition(MimeBodyPart.INLINE);
        imagePart.setFileName(image.fileName());
        related.addBodyPart(imagePart);

        return related;
    }

    /**
     * Bo thuoc tinh JavaMail cho mot lan gui — tach rieng de test kiem chung duoc ba khoa timeout.
     *
     * <p>Ba chang deu phai co han: {@code connectiontimeout} (bat tay TCP),
     * {@code timeout} (cho may chu tra loi), {@code writetimeout} (day du lieu len). Thieu bat ky
     * cai nao la con mot duong treo vinh vien. Moi khoa deu doc de duoc tu file cau hinh khi mot
     * may chu cu the can nguong khac.</p>
     */
    public static Properties smtpProperties(Properties config) {
        Properties props = new Properties();
        props.put("mail.smtp.host", required(config, "mail.smtp.host"));
        props.put("mail.smtp.port", config.getProperty("mail.smtp.port", "587"));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", config.getProperty("mail.smtp.starttls", "true"));
        props.put("mail.smtp.connectiontimeout",
                config.getProperty("mail.smtp.connectiontimeout", DEFAULT_SMTP_TIMEOUT_MS));
        props.put("mail.smtp.timeout",
                config.getProperty("mail.smtp.timeout", DEFAULT_SMTP_TIMEOUT_MS));
        props.put("mail.smtp.writetimeout",
                config.getProperty("mail.smtp.writetimeout", DEFAULT_SMTP_TIMEOUT_MS));
        return props;
    }

    public static String renderTemplate(String name, Map<String, String> values) {
        return render(name, values, false);
    }

    /**
     * Dung mot la thu HTML hoan chinh: noi dung rieng cua tung loai thu, dat trong khung chung.
     *
     * <p><b>Gia tri luon duoc escape.</b> Nhung thu nhet vao thu gom ten phim, ten khach, va
     * <i>ly do tu choi do quan tri vien tu go</i>. O thu text thuan chung vo hai; o HTML mot dau
     * {@code &} lam hong hien thi va mot the {@code <} cho phep chen markup vao thu gui cho khach.
     * Vi vay escape nam trong chinh ham render — khong the quen o mot template nao.</p>
     *
     * <p><b>O {@code {{{content}}}} la o THO.</b> Ba ngoac nhon, chen thang khong escape, vi thu
     * nhet vao la HTML da duoc dung tu {@link #render} nen ben trong da escape roi. Escape lan hai
     * se bien ca bang thanh chuoi the hien ra man hinh. Chen o tho <b>truoc</b> roi moi kiem
     * placeholder con sot, de phep kiem soi duoc ca noi dung ben trong.</p>
     */
    public static String renderHtmlDocument(String name, String title, Map<String, String> values) {
        String content = render(name, values, true);
        String layout = readTemplate(LAYOUT_TEMPLATE).replace("{{{content}}}", content);
        layout = layout.replace("{{title}}", htmlEscape(safe(title)));
        assertNoUnresolvedPlaceholder(layout, LAYOUT_TEMPLATE);
        return layout;
    }

    private static String render(String name, Map<String, String> values, boolean escapeValues) {
        String body = readTemplate(name);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String value = safe(entry.getValue());
            body = body.replace("{{" + entry.getKey() + "}}", escapeValues ? htmlEscape(value) : value);
        }
        assertNoUnresolvedPlaceholder(body, name);
        return body;
    }

    private static String readTemplate(String name) {
        String resource = "mail-templates/" + name;
        try (InputStream in = EmailService.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing mail template: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read mail template: " + resource, ex);
        }
    }

    private static void assertNoUnresolvedPlaceholder(String body, String name) {
        if (body.matches("(?s).*\\{\\{[^}]+}}.*")) {
            throw new IllegalArgumentException("Unresolved placeholder in mail-templates/" + name);
        }
    }

    /**
     * Escape 5 ky tu co nghia trong HTML.
     *
     * <p>Tu viet vi du an khong co ham escape nao phia Java — {@code fn:escapeXml} chi dung duoc
     * trong JSP. Nho hon nhieu so voi viec keo them mot thu vien chi de doi lay dung ham nay.</p>
     */
    public static String htmlEscape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void writeLog(String recipient, String subject, String body) {
        String record = """
                ================================================================
                [%s] %s
                Người nhận : %s
                ----------------------------------------------------------------
                %s
                ================================================================
                """.formatted(LocalDateTime.now().format(STAMP), subject, recipient, body);
        try {
            Path path = mailLogPath();
            synchronized (FILE_LOCK) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, record, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            LOG.info("Email logfile: " + subject + " -> " + recipient);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Cannot append cinebook-mail.log", ex);
        }
    }

    /**
     * Che do gui hien hanh, cache {@value #CONFIG_CACHE_TTL_MS} ms.
     *
     * <p>Truoc day moi la thu mo <b>mot connection DB moi</b>. Ham nay duoc goi tu {@code send()},
     * ma {@code send()} lai duoc goi tu {@code BookingService.payOrder} luc connection cua don
     * hang <i>van dang mo</i> — pool chi co 10–20. Cache lai de mot la thu khong con la mot
     * luot muon connection.</p>
     *
     * <p>Thu tu uu tien giu nguyen: {@code SystemSettings} trong DB thang, roi toi file cau hinh,
     * cuoi cung mac dinh {@code logfile}. TTL 60 giay de doi {@code mail.mode} o
     * {@code /system/config} van co tac dung ma khong can restart Tomcat.</p>
     */
    private String mode() {
        Cached<String> cached = cachedMode;
        long now = System.currentTimeMillis();
        if (cached != null && cached.fresh(now)) {
            return cached.value;
        }
        String resolved = readMode();
        cachedMode = new Cached<>(resolved, now);
        return resolved;
    }

    private String readMode() {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT SettingValue FROM SystemSettings WHERE SettingKey='mail.mode'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getString(1) != null && !rs.getString(1).isBlank()) return rs.getString(1);
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Cannot read mail.mode from database; using external config", ex);
        }
        return config().getProperty("mail.mode", "logfile");
    }

    /**
     * File cau hinh ngoai WAR, cache {@value #CONFIG_CACHE_TTL_MS} ms.
     *
     * <p>{@code DatabaseConfig.load()} doc lai file tu dia moi lan goi, nen truoc day mot la thu
     * gui hong ba lan la ba lan doc dia. Khong sua {@code DatabaseConfig} vi no con phuc vu
     * HikariCP luc khoi dong.</p>
     */
    private static Properties config() {
        Cached<Properties> cached = cachedConfig;
        long now = System.currentTimeMillis();
        if (cached != null && cached.fresh(now)) {
            return cached.value;
        }
        Properties loaded = DatabaseConfig.load();
        cachedConfig = new Cached<>(loaded, now);
        return loaded;
    }

    /** Xoa cache — test doi {@code mail.mode} xong goi ham nay de khong phai cho het TTL. */
    public static void clearCache() {
        cachedMode = null;
        cachedConfig = null;
    }

    private static final class Cached<T> {
        private final T value;
        private final long readAtMs;

        private Cached(T value, long readAtMs) {
            this.value = value;
            this.readAtMs = readAtMs;
        }

        private boolean fresh(long now) {
            return now - readAtMs < CONFIG_CACHE_TTL_MS;
        }
    }

    public static Path mailLogPath() {
        String catalina = System.getProperty("catalina.base");
        return catalina == null || catalina.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "cinebook-mail.log")
                : Path.of(catalina, "logs", "cinebook-mail.log");
    }

    public static void shutdown() {
        DELIVERY.shutdown();
        try {
            if (!DELIVERY.awaitTermination(5, TimeUnit.SECONDS)) DELIVERY.shutdownNow();
        } catch (InterruptedException ex) {
            DELIVERY.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static String required(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + key);
        return value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.config.SecuritySettings;
import com.mycompany.website.ban.ve.xem.phim.dao.PasswordResetTokenDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.UserDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcPasswordResetTokenDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserDAO;
import com.mycompany.website.ban.ve.xem.phim.model.PasswordResetToken;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.PasswordResetService;
import com.mycompany.website.ban.ve.xem.phim.util.PasswordUtil;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D11 — quen mat khau that: token bam SHA-256, han 30 phut, dung dung mot lan.
 */
@Tag("it")
public class PasswordResetIT {

    private static final String EMAIL = "reset_probe@test.com";
    private static final String OLD_PASSWORD = "CuKy!2026xyz";
    private static final String NEW_PASSWORD = "Rap*Phim88#z";
    private static final String IP = "203.0.113.90";

    static {
        System.setProperty("cinebook.db.config", new File(System.getProperty("cinebook.it.config", "target/db.it.properties")).getAbsolutePath());
        DBConnection.shutdown();
    }

    private PasswordResetService service;
    private UserDAO userDAO;
    private PasswordResetTokenDAO tokenDAO;

    @BeforeAll
    public static void initConnection() {
        System.setProperty("cinebook.db.config", new File(System.getProperty("cinebook.it.config", "target/db.it.properties")).getAbsolutePath());
        DBConnection.shutdown();
        SecuritySettings.clearCache();
    }

    @AfterAll
    public static void cleanUp() throws SQLException {
        purge();
        DBConnection.shutdown();
    }

    @BeforeEach
    public void setUp() throws SQLException {
        purge();
        service = new PasswordResetService();
        userDAO = new JdbcUserDAO();
        tokenDAO = new JdbcPasswordResetTokenDAO();

        User user = new User();
        user.setUsername("reset_probe");
        user.setFullName("Reset Probe");
        user.setEmail(EMAIL);
        user.setPasswordHash(PasswordUtil.hash(OLD_PASSWORD));
        user.setRole("member");
        userDAO.create(user);
    }

    /** Xoa token truoc roi moi xoa user — co khoa ngoai giua hai bang. */
    private static void purge() throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM PasswordResetTokens WHERE UserId IN (SELECT Id FROM Users WHERE Email = ?)")) {
                ps.setString(1, EMAIL);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM LoginAttempts WHERE Email = ?")) {
                ps.setString(1, EMAIL);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM Users WHERE Email = ?")) {
                ps.setString(1, EMAIL);
                ps.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName("Token luu trong DB la ban BAM, khong phai token tho")
    public void testTokenIsStoredHashed() {
        String rawToken = service.requestReset(EMAIL, IP, "http://localhost/reset-password").orElseThrow();

        assertTrue(tokenDAO.findByHash(rawToken).isEmpty(),
                "Tim thay token tho trong cot TokenHash — nghia la DB dang luu ban tho");

        Optional<PasswordResetToken> stored = tokenDAO.findByHash(PasswordResetService.sha256Hex(rawToken));
        assertTrue(stored.isPresent(), "Phai tim duoc phieu qua ban bam SHA-256");
        assertEquals(64, stored.get().getTokenHash().length(), "SHA-256 hex phai dai 64 ky tu");
        assertFalse(stored.get().isUsed(), "Phieu moi tao chua duoc dung");
    }

    @Test
    @DisplayName("Han dung do DB tinh, dung bang security.resetTokenMinutes (30 phut)")
    public void testTokenExpiryComesFromDatabase() {
        String rawToken = service.requestReset(EMAIL, IP, "http://localhost/reset-password").orElseThrow();
        PasswordResetToken token = tokenDAO.findByHash(PasswordResetService.sha256Hex(rawToken)).orElseThrow();

        long minutesLeft = java.time.Duration.between(DBConnection.dbNow(), token.getExpiresAt()).toMinutes();
        int configured = SecuritySettings.resetTokenMinutes();
        assertEquals(30, configured, "Fixture mong doi 30 phut");
        assertTrue(minutesLeft >= configured - 2 && minutesLeft <= configured,
                "Han con lai " + minutesLeft + " phut, mong doi ~" + configured);
    }

    @Test
    @DisplayName("Doi mat khau bang phieu hop le — mat khau moi co hieu luc that")
    public void testResetChangesPassword() {
        String rawToken = service.requestReset(EMAIL, IP, "http://localhost/reset-password").orElseThrow();

        PasswordResetService.ResetResult result = service.consume(rawToken, NEW_PASSWORD, NEW_PASSWORD);
        assertTrue(result.isSuccess(), "Le ra phai thanh cong, nhung: " + result.getMessage());

        User reloaded = userDAO.findByEmail(EMAIL).orElseThrow();
        assertTrue(PasswordUtil.matches(NEW_PASSWORD, reloaded.getPasswordHash()), "Mat khau moi phai dung");
        assertFalse(PasswordUtil.matches(OLD_PASSWORD, reloaded.getPasswordHash()), "Mat khau cu phai het hieu luc");
    }

    @Test
    @DisplayName("Phieu chi dung duoc MOT lan")
    public void testTokenIsSingleUse() {
        String rawToken = service.requestReset(EMAIL, IP, "http://localhost/reset-password").orElseThrow();

        assertTrue(service.consume(rawToken, NEW_PASSWORD, NEW_PASSWORD).isSuccess());

        PasswordResetService.ResetResult second = service.consume(rawToken, "Khac!Han2026z", "Khac!Han2026z");
        assertFalse(second.isSuccess(), "Lan thu hai phai bi tu choi");

        User reloaded = userDAO.findByEmail(EMAIL).orElseThrow();
        assertTrue(PasswordUtil.matches(NEW_PASSWORD, reloaded.getPasswordHash()),
                "Mat khau phai giu nguyen lan doi dau tien");
    }

    @Test
    @DisplayName("Phieu het han bi tu choi")
    public void testExpiredTokenRejected() throws SQLException {
        String rawToken = service.requestReset(EMAIL, IP, "http://localhost/reset-password").orElseThrow();

        // Day han ve qua khu bang gio DB, khong dung gio may ung dung.
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE PasswordResetTokens SET ExpiresAt = DATEADD(MINUTE, -1, SYSDATETIME()) WHERE TokenHash = ?")) {
            ps.setString(1, PasswordResetService.sha256Hex(rawToken));
            ps.executeUpdate();
        }

        assertFalse(service.isTokenUsable(rawToken), "Phieu het han phai bao khong dung duoc");
        assertFalse(service.consume(rawToken, NEW_PASSWORD, NEW_PASSWORD).isSuccess());

        User reloaded = userDAO.findByEmail(EMAIL).orElseThrow();
        assertTrue(PasswordUtil.matches(OLD_PASSWORD, reloaded.getPasswordHash()), "Mat khau khong duoc doi");
    }

    @Test
    @DisplayName("Mat khau moi yeu bi tu choi NHUNG phieu khong bi tieu oan")
    public void testWeakPasswordDoesNotBurnToken() {
        String rawToken = service.requestReset(EMAIL, IP, "http://localhost/reset-password").orElseThrow();

        PasswordResetService.ResetResult weak = service.consume(rawToken, "123456", "123456");
        assertFalse(weak.isSuccess(), "Mat khau '123456' phai bi chinh sach tu choi");

        assertTrue(service.isTokenUsable(rawToken),
                "Phieu phai con dung duoc: go nham mat khau yeu khong duoc lam mat luon lien ket");
        assertTrue(service.consume(rawToken, NEW_PASSWORD, NEW_PASSWORD).isSuccess(),
                "Go lai mat khau dat chuan bang chinh lien ket cu phai thanh cong");
    }

    @Test
    @DisplayName("Mat khau xac nhan khong khop bi tu choi, phieu van con")
    public void testMismatchedConfirmation() {
        String rawToken = service.requestReset(EMAIL, IP, "http://localhost/reset-password").orElseThrow();

        assertFalse(service.consume(rawToken, NEW_PASSWORD, "Khac!Han2026z").isSuccess());
        assertTrue(service.isTokenUsable(rawToken));
    }

    @Test
    @DisplayName("Email khong ton tai: khong tao phieu, khong nem loi, khong he lo dieu gi")
    public void testUnknownEmailIsSilent() throws SQLException {
        Optional<String> token = service.requestReset("khong_he_ton_tai_p10@test.com", IP, "http://localhost/reset-password");
        assertTrue(token.isEmpty(), "Khong duoc tao phieu cho email khong ton tai");

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM PasswordResetTokens");
             java.sql.ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertEquals(0, rs.getInt(1), "Khong duoc co phieu nao duoc sinh ra");
        }
        assertFalse(PasswordResetService.GENERIC_RESPONSE.toLowerCase().contains("không tồn tại"),
                "Cau tra loi chung khong duoc noi ve su ton tai cua email");
    }

    @Test
    @DisplayName("Xin phieu moi lam phieu cu het hieu luc")
    public void testNewRequestInvalidatesOldToken() throws SQLException {
        String first = service.requestReset(EMAIL, IP, "http://localhost/reset-password").orElseThrow();

        // Lui moc tao ve qua khu de vuot qua cooldown, giong cach testExpiredTokenRejected day
        // ExpiresAt ve truoc — dung gio DB, khong dung gio may ung dung. Co tinh KHONG tat cau
        // hinh cooldown: de nguyen thi doan ma chay o day dung la doan ma chay tren production.
        rewindCreatedAtBeyondCooldown();

        String second = service.requestReset(EMAIL, IP, "http://localhost/reset-password").orElseThrow();

        assertNotEquals(first, second);
        assertFalse(service.isTokenUsable(first), "Phieu cu phai het hieu luc khi co phieu moi");
        assertTrue(service.isTokenUsable(second));
    }

    /**
     * Cooldown cua {@code /forgot-password} — doi xung voi cooldown cua luong xac thuc email.
     *
     * <p>Route nay khong can dang nhap va moi lan POST la mot lan gui thu. Khi
     * {@code mail.mode=logfile} thi thieu gioi han vo hai, nen lo hong nay tung khong lo ra. Bat
     * SMTP that thi no la duong doi bom hop thu bat ky ai co tai khoan, dot han ngach gui, va co
     * the lam nha cung cap khoa luon tai khoan gui.</p>
     */
    @Test
    @DisplayName("Xin phieu lien tuc trong thoi gian cho: khong sinh phieu moi, khong gui thu")
    public void testRepeatedRequestIsThrottled() throws SQLException {
        String first = service.requestReset(EMAIL, IP, "http://localhost/reset-password").orElseThrow();

        Optional<String> second = service.requestReset(EMAIL, IP, "http://localhost/reset-password");
        assertTrue(second.isEmpty(), "Lan hai trong thoi gian cho khong duoc sinh phieu");

        assertEquals(1, tokenCount(), "Chi duoc ton tai dung mot phieu — lan hai khong duoc ghi gi");
        assertTrue(service.isTokenUsable(first),
                "Bi chan boi cooldown thi tuyet doi khong duoc dong toi phieu con han cua nguoi dung");
    }

    /**
     * Bi chan van phai tra ve dung mot cau tra loi — neu khong, cooldown thanh may do email.
     *
     * <p>Email co that bi chan tra {@code Optional.empty()}; email khong ton tai cung tra
     * {@code Optional.empty()}. Hai truong hop khong phan biet duoc tu ben ngoai, va tang web
     * hien cung mot {@code GENERIC_RESPONSE} cho ca hai.</p>
     */
    @Test
    @DisplayName("Bi cooldown chan va email khong ton tai tra ve ket qua khong the phan biet")
    public void testThrottledResponseIsIndistinguishableFromUnknownEmail() {
        service.requestReset(EMAIL, IP, "http://localhost/reset-password").orElseThrow();

        Optional<String> throttled = service.requestReset(EMAIL, IP, "http://localhost/reset-password");
        Optional<String> unknown =
                service.requestReset("khong_he_ton_tai_p10@test.com", IP, "http://localhost/reset-password");

        assertEquals(unknown, throttled,
                "Hai truong hop phai giong het nhau — khac nhau la lo ra email nao co that");
    }

    /** So phieu cua tai khoan thu nghiem. */
    private static int tokenCount() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM PasswordResetTokens WHERE UserId IN "
                     + "(SELECT Id FROM Users WHERE Email = ?)")) {
            ps.setString(1, EMAIL);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Day CreatedAt cua moi phieu ve qua khu bang gio DB de thoat khoi cua so cooldown. */
    private static void rewindCreatedAtBeyondCooldown() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE PasswordResetTokens SET CreatedAt = DATEADD(SECOND, -7200, SYSDATETIME()) "
                     + "WHERE UserId IN (SELECT Id FROM Users WHERE Email = ?)")) {
            ps.setString(1, EMAIL);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("Link duoc ghi ra cinebook-mail.log — che do log-to-file cua P10")
    public void testMailLogContainsLink() throws Exception {
        Path mailLog = PasswordResetService.mailLogPath();
        String before = Files.exists(mailLog) ? Files.readString(mailLog, StandardCharsets.UTF_8) : "";

        String rawToken = service.requestReset(EMAIL, IP, "http://localhost/reset-password").orElseThrow();

        assertTrue(Files.exists(mailLog), "Phai co file " + mailLog);
        String after = Files.readString(mailLog, StandardCharsets.UTF_8);
        // Cat theo do dai CHUOI, khong theo Files.size(): file UTF-8 co tieng Viet nen so byte
        // luon lon hon so ky tu, lay so byte lam chi so ky tu se truot.
        String appended = after.substring(before.length());
        assertTrue(appended.contains(rawToken), "Noi dung ghi them phai chua token that");
        assertTrue(appended.contains(EMAIL), "Phai ghi ro nguoi nhan");
        assertTrue(appended.contains("30 phút"), "Phai noi ro han dung trong 'email'");
    }
}

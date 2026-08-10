package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.service.EmailService;
import com.mycompany.website.ban.ve.xem.phim.service.EmailService.ResendOutcome;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gui lai email xac thuc (EM-01).
 *
 * <p>Kiem ba dieu ma ban quet yeu cau: token mot lan va bi xoay vong, co thoi gian cho giua hai
 * lan gui, va tai khoan da xac thuc thi khong gui nua.</p>
 */
@Tag("it")
@DisplayName("Xac thuc email — gui lai an toan")
public class EmailVerifyResendIT {

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private static final String BASE = "http://localhost:8080/Website-ban-ve-xem-phim";

    private static EmailService emailService;
    private int userId;

    @BeforeAll
    public static void setUp() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
        emailService = new EmailService();
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.shutdown();
    }

    @BeforeEach
    public void resetUser() throws SQLException {
        userId = scalar("SELECT TOP 1 Id FROM Users WHERE Role = 'member' ORDER BY Id");
        exec("UPDATE Users SET EmailVerifiedAt = NULL, EmailVerifyTokenHash = NULL, "
                + "EmailVerifySentAt = NULL WHERE Id = " + userId);
    }

    @Test
    @DisplayName("EM-01: lan gui dau tien sinh token va ghi moc thoi gian")
    public void firstResendCreatesToken() throws SQLException {
        assertEquals(ResendOutcome.SENT, emailService.resendVerification(userId, BASE, 120));

        assertNotNull(tokenHash(), "Phai sinh token moi");
        assertNotNull(sentAt(), "Phai ghi moc gui de con tinh thoi gian cho");
    }

    @Test
    @DisplayName("EM-01: bam lai ngay lap tuc bi chan boi thoi gian cho")
    public void secondResendWithinCooldownIsBlocked() throws SQLException {
        assertEquals(ResendOutcome.SENT, emailService.resendVerification(userId, BASE, 120));
        String firstToken = tokenHash();

        assertEquals(ResendOutcome.COOLDOWN, emailService.resendVerification(userId, BASE, 120),
                "Bam lien lan hai phai bi chan");
        assertEquals(firstToken, tokenHash(),
                "Bi chan thi khong duoc dong toi token dang hop le");
    }

    @Test
    @DisplayName("EM-01: het thoi gian cho thi gui lai duoc, va token cu bi vo hieu")
    public void resendAfterCooldownRotatesToken() throws SQLException {
        assertEquals(ResendOutcome.SENT, emailService.resendVerification(userId, BASE, 120));
        String firstToken = tokenHash();

        // Lui moc gui ve qua khu de mo phong "da doi du lau" ma khong phai ngu trong test.
        exec("UPDATE Users SET EmailVerifySentAt = DATEADD(SECOND, -300, GETDATE()) WHERE Id = " + userId);

        assertEquals(ResendOutcome.SENT, emailService.resendVerification(userId, BASE, 120));
        String secondToken = tokenHash();

        assertNotNull(secondToken);
        assertNotEquals(firstToken, secondToken,
                "Token phai duoc xoay vong — moi thoi diem chi mot token hop le");
    }

    /**
     * N-15 — thoi gian cho phai chiu duoc request SONG SONG.
     *
     * <p>Ban cu tach lam hai cau: mot SELECT tinh {@code CanSend} roi mot UPDATE ghi
     * {@code EmailVerifySentAt = GETDATE()}. Hai cau roi nhau, auto-commit, khong khoa dong,
     * nen 20 POST {@code /resend-verification} dong thoi (cung session, CSRF token hop le)
     * deu doc {@code CanSend = 1} truoc khi ai kip UPDATE — 20 email vao hang doi gui.</p>
     */
    @Test
    @DisplayName("N-15: 20 request dong thoi -> dung MOT lan gui, 19 lan con lai bi chan")
    public void concurrentResendRequestsYieldExactlyOneSend() throws Exception {
        int threads = 20;
        java.util.concurrent.CountDownLatch startLine = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<ResendOutcome>> results = new java.util.ArrayList<>();
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    startLine.await();
                    return emailService.resendVerification(userId, BASE, 120);
                }));
            }
            startLine.countDown();

            int sent = 0;
            int cooldown = 0;
            for (java.util.concurrent.Future<ResendOutcome> result : results) {
                ResendOutcome outcome = result.get(30, java.util.concurrent.TimeUnit.SECONDS);
                switch (outcome) {
                    case SENT -> sent++;
                    case COOLDOWN -> cooldown++;
                    default -> throw new AssertionError(
                            "Tai khoan chua xac thuc thi khong duoc tra " + outcome);
                }
            }

            assertEquals(1, sent,
                    "Dung mot request duoc gui email; " + sent + " lan gui nghia la thoi gian cho "
                            + "bi vuot bang request song song");
            assertEquals(threads - 1, cooldown, "Cac request con lai deu phai roi vao thoi gian cho");
            assertNotNull(tokenHash(), "Request thang cuoc phai de lai token hop le");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * La thu xac thuc DAU TIEN cung phai ghi moc gui.
     *
     * <p>{@code createAndSendVerification} tung bo trong {@code EmailVerifySentAt}. Menh de
     * {@code EmailVerifySentAt IS NULL} cua {@code resendVerification} coi do la "chua gui bao gio"
     * nen cho qua — nguoi vua dang ky bam "Gui lai" phat nua van lot, tuc cooldown khong ton tai
     * o dung lan de bi lam dung nhat. O che do {@code logfile} khong ai thay; bat SMTP that thi
     * do la hai la thu that cho mot thao tac.</p>
     */
    @Test
    @DisplayName("EM-01: thu xac thuc luc dang ky cung ghi moc gui, nen bam lai ngay bi chan")
    public void registrationSendStartsTheCooldown() throws SQLException {
        String email = stringScalar("SELECT Email FROM Users WHERE Id = " + userId);
        emailService.createAndSendVerification(userId, email, "Nguoi Dung Thu", BASE);

        assertNotNull(sentAt(),
                "La thu dau tien phai ghi EmailVerifySentAt, neu khong cooldown bi bo qua o lan resend dau");
        assertNotNull(tokenHash(), "Phai sinh token xac thuc");

        assertEquals(ResendOutcome.COOLDOWN, emailService.resendVerification(userId, BASE, 120),
                "Bam 'Gui lai' ngay sau khi dang ky phai bi chan");
    }

    @Test
    @DisplayName("EM-01: tai khoan da xac thuc thi khong gui nua")
    public void verifiedAccountIsNotResent() throws SQLException {
        exec("UPDATE Users SET EmailVerifiedAt = GETDATE() WHERE Id = " + userId);

        assertEquals(ResendOutcome.ALREADY_VERIFIED, emailService.resendVerification(userId, BASE, 120));
        assertNull(tokenHash(), "Khong duoc sinh token cho tai khoan da xac thuc");
    }

    @Test
    @DisplayName("EM-01: token dung mot lan — xac thuc lan hai that bai")
    public void verificationTokenIsSingleUse() throws SQLException {
        // Sinh token qua duong chinh thuc roi doc ban ro tu chinh luong tao.
        // Ta khong doc duoc token ro tu DB (chi luu hash), nen dung createAndSendVerification
        // va bat token qua ham verify voi gia tri da biet.
        String rawToken = "IT-token-" + System.nanoTime();
        exec("UPDATE Users SET EmailVerifyTokenHash = '"
                + com.mycompany.website.ban.ve.xem.phim.service.PasswordResetService.sha256Hex(rawToken)
                + "', EmailVerifiedAt = NULL WHERE Id = " + userId);

        assertTrue(emailService.verifyEmail(rawToken), "Lan xac thuc dau phai thanh cong");
        assertTrue(!emailService.verifyEmail(rawToken), "Dung lai chinh token do phai that bai");
        assertNull(tokenHash(), "Token phai bi xoa sau khi dung");
        assertNotNull(verifiedAt(), "Phai ghi moc da xac thuc");
    }

    @Test
    @DisplayName("EM-01: token sai hoac rong bi tu choi")
    public void invalidTokenIsRejected() {
        assertTrue(!emailService.verifyEmail(null));
        assertTrue(!emailService.verifyEmail(""));
        assertTrue(!emailService.verifyEmail("khong-ton-tai"));
    }

    // ---------------------------------------------------------------- helpers

    private String tokenHash() throws SQLException {
        return stringScalar("SELECT EmailVerifyTokenHash FROM Users WHERE Id = " + userId);
    }

    private String sentAt() throws SQLException {
        return stringScalar("SELECT CONVERT(VARCHAR(30), EmailVerifySentAt, 120) FROM Users WHERE Id = " + userId);
    }

    private String verifiedAt() throws SQLException {
        return stringScalar("SELECT CONVERT(VARCHAR(30), EmailVerifiedAt, 120) FROM Users WHERE Id = " + userId);
    }

    private static String stringScalar(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static int scalar(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void exec(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }
}

package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.config.SecuritySettings;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcLoginAttemptDAO;
import com.mycompany.website.ban.ve.xem.phim.service.LoginThrottleService;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D6 — chan do mat khau. Chay tren {@code CineBookDB_Test}.
 */
@Tag("it")
public class LoginThrottleIT {

    private static final String EMAIL = "throttle_probe@test.com";
    private static final String IP = "203.0.113.77";

    static {
        System.setProperty("cinebook.db.config", new File(System.getProperty("cinebook.it.config", "target/db.it.properties")).getAbsolutePath());
        DBConnection.shutdown();
    }

    private LoginThrottleService service;

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
    public void reset() throws SQLException {
        purge();
        service = new LoginThrottleService();
    }

    /** Xoa moi dau vet cua test nay — khong dung {@code TRUNCATE} de khong dung du lieu khac. */
    private static void purge() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM LoginAttempts WHERE Email LIKE 'throttle_%' OR IpAddress = ?")) {
            ps.setString(1, IP);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("Sai den lan thu 6 thi bi chan — dung nguong security.maxLoginAttempts")
    public void testBlockedAfterMaxAttempts() {
        int max = SecuritySettings.maxLoginAttempts();
        assertEquals(5, max, "Fixture mong doi nguong mac dinh 5");

        for (int attempt = 1; attempt <= max; attempt++) {
            assertFalse(service.check(EMAIL, IP).isBlocked(),
                    "Lan thu " + attempt + " (<= " + max + ") van phai duoc phep thu");
            service.recordFailure(EMAIL, IP);
        }

        LoginThrottleService.Decision decision = service.check(EMAIL, IP);
        assertTrue(decision.isBlocked(), "Sau " + max + " lan sai, lan thu tiep theo phai bi chan");
        assertTrue(decision.getRetryAfterSeconds() > 0, "Phai noi duoc con bao lau nua");
        assertTrue(decision.getRetryAfterSeconds() <= SecuritySettings.lockMinutes() * 60,
                "Thoi gian cho khong duoc vuot cua so " + SecuritySettings.lockMinutes() + " phut");
    }

    @Test
    @DisplayName("Thong bao khi bi chan khong he lo email co ton tai hay khong")
    public void testBlockMessageDoesNotLeakAccountExistence() {
        for (int i = 0; i < SecuritySettings.maxLoginAttempts(); i++) {
            service.recordFailure(EMAIL, IP);
        }
        String message = service.check(EMAIL, IP).getMessage().toLowerCase();
        assertFalse(message.contains("khong ton tai"), "Thong bao khong duoc noi ve su ton tai cua email");
        assertFalse(message.contains("không tồn tại"));
        assertFalse(message.contains(EMAIL), "Thong bao khong duoc nhac lai email");
        assertTrue(message.contains("phút") || message.contains("phut"), "Phai noi bao lau nua duoc thu lai");
    }

    @Test
    @DisplayName("Dang nhap dung mot lan la bo dem tu ve 0")
    public void testSuccessResetsCounter() {
        for (int i = 0; i < SecuritySettings.maxLoginAttempts(); i++) {
            service.recordFailure(EMAIL, IP);
        }
        assertTrue(service.check(EMAIL, IP).isBlocked(), "Dieu kien tien de: dang bi chan");

        service.recordSuccess(EMAIL, IP);

        LoginThrottleService.Decision after = service.check(EMAIL, IP);
        assertFalse(after.isBlocked(), "Sau mot lan dang nhap dung, bo dem phai ve 0");
        assertEquals(0, after.getFailures());
    }

    @Test
    @DisplayName("Nguong theo IP rong hon nguong theo email, nhung van chan ke rai tren nhieu email")
    public void testIpThresholdBlocksSpray() {
        int max = SecuritySettings.maxLoginAttempts();
        int ipLimit = max * 4;

        // Moi email chi sai 1 lan — nguong theo email khong bao gio cham toi.
        for (int i = 0; i < ipLimit; i++) {
            service.recordFailure("throttle_spray_" + i + "@test.com", IP);
        }

        LoginThrottleService.Decision decision = service.check("throttle_spray_moi@test.com", IP);
        assertTrue(decision.isBlocked(),
                "Rai " + ipLimit + " lan sai tu cung mot IP phai bi chan du moi email chi sai 1 lan");
    }

    @Test
    @DisplayName("Bo dem cua email nay khong anh huong email khac")
    public void testCounterIsPerEmail() {
        for (int i = 0; i < SecuritySettings.maxLoginAttempts(); i++) {
            service.recordFailure(EMAIL, "198.51.100.4");
        }
        assertTrue(service.check(EMAIL, "198.51.100.4").isBlocked());
        assertFalse(service.check("throttle_khac@test.com", "198.51.100.5").isBlocked(),
                "Email khac tu IP khac phai khong bi anh huong");
    }

    @Test
    @DisplayName("Email khong phan biet hoa thuong — khong lach duoc bang cach doi kieu chu")
    public void testEmailIsCaseInsensitive() {
        for (int i = 0; i < SecuritySettings.maxLoginAttempts(); i++) {
            service.recordFailure(EMAIL.toUpperCase(), IP);
        }
        assertTrue(service.check(EMAIL, IP).isBlocked(),
                "Ghi bang chu HOA, kiem bang chu thuong — van phai la cung mot bo dem");
    }

    @Test
    @DisplayName("Do tre tang dan co gioi han tren, khong giu thread qua lau")
    public void testProgressiveDelayIsCapped() {
        long start = System.currentTimeMillis();
        service.applyProgressiveDelay(100);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed <= 2_500L, "Do tre phai bi chan tran ~2s, do duoc " + elapsed + "ms");
    }

    @Test
    @DisplayName("clearForEmail go chan — duong cuu ho thu cong cua quan tri vien")
    public void testClearForEmailUnblocks() {
        for (int i = 0; i < SecuritySettings.maxLoginAttempts(); i++) {
            service.recordFailure(EMAIL, IP);
        }
        assertTrue(service.check(EMAIL, IP).isBlocked());

        new JdbcLoginAttemptDAO().clearForEmail(EMAIL);
        assertFalse(service.check(EMAIL, IP).isBlocked());
    }
}

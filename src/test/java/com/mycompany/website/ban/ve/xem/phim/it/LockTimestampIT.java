package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.UserDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserDAO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * F-006 — {@code LockedAt} phai cung nguon thoi gian voi {@code UpdatedAt}.
 *
 * <p>Ban cu ghi {@code LockedAt} bang {@code System.currentTimeMillis()} cua may ung dung con
 * {@code UpdatedAt} bang {@code GETDATE()} cua may DB, nen hai cot cua cung mot hanh dong khong bao
 * gio trung nhau (chenh do round-trip, cong them DATETIME cua SQL Server lam tron 1/300 giay).
 * Test doi hai cot bang nhau tuyet doi — dieu chi dat duoc khi ca hai do cung mot cau lenh SQL sinh.</p>
 */
@Tag("it")
public class LockTimestampIT {
    /** Thanh vien co san trong fixture; trang thai duoc phuc hoi nguyen ven o cuoi test. */
    private static final int MEMBER_ID = 1;

    @BeforeAll
    public static void setUpConfig() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private record LockState(boolean locked, String reason, Timestamp lockedAt) { }

    private static LockState readLockState() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT IsLocked, LockReason, LockedAt FROM Users WHERE Id = ?")) {
            ps.setInt(1, MEMBER_ID);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Fixture phai co user id " + MEMBER_ID);
                return new LockState(rs.getBoolean("IsLocked"), rs.getString("LockReason"),
                        rs.getTimestamp("LockedAt"));
            }
        }
    }

    private static void restore(LockState original) throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE Users SET IsLocked = ?, LockReason = ?, LockedAt = ? WHERE Id = ?")) {
            ps.setBoolean(1, original.locked());
            ps.setString(2, original.reason());
            if (original.lockedAt() == null) {
                ps.setNull(3, java.sql.Types.TIMESTAMP);
            } else {
                ps.setTimestamp(3, original.lockedAt());
            }
            ps.setInt(4, MEMBER_ID);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("Khoa tai khoan: LockedAt va UpdatedAt cung do gio DB sinh, bang nhau tuyet doi")
    public void testLockedAtUsesDatabaseClock() throws Exception {
        UserDAO userDAO = new JdbcUserDAO();
        LockState original = readLockState();
        try {
            assertTrue(userDAO.updateLockStatus(MEMBER_ID, true, "F-006 kiem tra nguon thoi gian"));

            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT LockedAt, UpdatedAt, GETDATE() AS DbNow FROM Users WHERE Id = ?")) {
                ps.setInt(1, MEMBER_ID);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    Timestamp lockedAt = rs.getTimestamp("LockedAt");
                    Timestamp updatedAt = rs.getTimestamp("UpdatedAt");
                    Timestamp dbNow = rs.getTimestamp("DbNow");
                    assertNotNull(lockedAt, "Khoa tai khoan phai ghi LockedAt");
                    assertEquals(updatedAt, lockedAt,
                            "LockedAt va UpdatedAt phai do cung mot GETDATE() sinh ra");
                    long secondsFromDbNow = Math.abs(dbNow.getTime() - lockedAt.getTime()) / 1000;
                    assertTrue(secondsFromDbNow < 60,
                            "LockedAt phai nam trong gio DB hien tai, lech " + secondsFromDbNow + "s");
                }
            }

            assertTrue(userDAO.updateLockStatus(MEMBER_ID, false, null));
            assertNull(readLockState().lockedAt(), "Mo khoa phai xoa LockedAt");
        } finally {
            restore(original);
        }
        LockState after = readLockState();
        assertEquals(original.locked(), after.locked(), "Trang thai khoa phai duoc phuc hoi");
        assertEquals(original.reason(), after.reason(), "Ly do khoa phai duoc phuc hoi");
    }
}

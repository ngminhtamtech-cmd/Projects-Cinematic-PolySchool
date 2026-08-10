package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.config.SettingsReader;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BUG-01 (INV-6) — nguong nghiep vu hien tren man hinh quan tri phai <b>that su</b> dieu khien
 * hanh vi.
 *
 * <p>Truoc khi sua: {@code BookingService.HOLD_MINUTES = 10} la hang so hard-code va
 * {@code SystemSettings.seat_hold_minutes} khong duoc doc o bat ky dau, nen doi cau hinh xuong
 * 5 phut van giu ghe 10 phut. Bai test nay do truc tiep {@code DATEDIFF(MINUTE, HeldAt, HeldUntil)}
 * cua ghe vua bi giu, tuc la do hanh vi that chu khong do gia tri tra ve cua mot getter.</p>
 */
@Tag("it")
public class Bug01SeatHoldMinutesIT {

    private static final int SHOWTIME_ID = 3;
    private static final int USER_ID = 1;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();

    @BeforeAll
    public static void setUpTestDb() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.shutdown();
    }

    @BeforeEach
    public void resetFixture() throws Exception {
        cleanShowtime();
        SettingsReader.clearCache();
    }

    @AfterEach
    public void restoreDefaultSetting() throws Exception {
        setHoldMinutes("10");
        SettingsReader.clearCache();
        cleanShowtime();
    }

    @Test
    @DisplayName("BUG-01: seat_hold_minutes=5 thi ghe phai duoc giu dung 5 phut")
    public void seatHoldMinutesSettingDrivesHoldWindow() throws Exception {
        setHoldMinutes("5");
        SettingsReader.clearCache();

        OrderRecord order = createDraftOnFirstAvailableSeat();

        assertEquals(5, heldMinutesOf(order.getId()),
                "DATEDIFF(MINUTE, HeldAt, HeldUntil) phai bang seat_hold_minutes dang cau hinh");
    }

    @Test
    @DisplayName("BUG-01: gia tri cau hinh vo ly bi tu choi, quay ve mac dinh 10 phut")
    public void absurdSettingFallsBackToDefault() throws Exception {
        setHoldMinutes("100000");
        SettingsReader.clearCache();

        OrderRecord order = createDraftOnFirstAvailableSeat();

        assertEquals(BookingService.HOLD_MINUTES, heldMinutesOf(order.getId()),
                "Mot o cau hinh go nham khong duoc khoa ghe hang gio");
    }

    private OrderRecord createDraftOnFirstAvailableSeat() {
        List<ShowtimeSeat> seats = bookingService.getSeatMap(SHOWTIME_ID);
        ShowtimeSeat available = seats.stream()
                .filter(s -> s.isAvailableFor(USER_ID) && !"couple".equalsIgnoreCase(s.getSeatType()))
                .findFirst()
                .orElseThrow();
        return bookingService.createDraftOrder(
                USER_ID, SHOWTIME_ID, List.of(available.getId()), Map.of(), null, "card");
    }

    /** Han giu that su ghi xuong DB, tinh bang chinh gio SQL Server. */
    private int heldMinutesOf(int orderId) throws Exception {
        String sql = """
                SELECT TOP 1 DATEDIFF(MINUTE, ss.HeldAt, ss.HeldUntil)
                FROM ShowtimeSeats ss
                JOIN OrderSeats os ON os.ShowtimeSeatId = ss.Id
                WHERE os.OrderId = ?
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Khong tim thay ghe cua don " + orderId);
                }
                return rs.getInt(1);
            }
        }
    }

    private static void setHoldMinutes(String value) throws Exception {
        String sql = """
                MERGE SystemSettings AS target
                USING (SELECT 'seat_hold_minutes' AS SettingKey) AS source
                   ON target.SettingKey = source.SettingKey
                WHEN MATCHED THEN UPDATE SET SettingValue = ?
                WHEN NOT MATCHED THEN INSERT (SettingKey, SettingValue) VALUES ('seat_hold_minutes', ?);
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private static void cleanShowtime() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps1 = conn.prepareStatement(
                     "DELETE FROM Invoices WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             PreparedStatement ps2 = conn.prepareStatement(
                     "DELETE FROM PromotionUsage WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             PreparedStatement ps3 = conn.prepareStatement(
                     "DELETE FROM RefundTransactions WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             PreparedStatement ps4 = conn.prepareStatement(
                     "DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             PreparedStatement ps5 = conn.prepareStatement("DELETE FROM Orders WHERE ShowtimeId = 3");
             PreparedStatement ps6 = conn.prepareStatement(
                     "UPDATE ShowtimeSeats SET Status = 'available', HeldByUserId = NULL, HeldAt = NULL,"
                     + " HeldUntil = NULL WHERE ShowtimeId = 3 AND Status != 'maintenance'")) {
            ps1.executeUpdate();
            ps2.executeUpdate();
            ps3.executeUpdate();
            ps4.executeUpdate();
            ps5.executeUpdate();
            ps6.executeUpdate();
        }
    }
}

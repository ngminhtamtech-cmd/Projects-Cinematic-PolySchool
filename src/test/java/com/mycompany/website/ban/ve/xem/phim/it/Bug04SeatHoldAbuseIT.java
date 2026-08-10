package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.config.SettingsReader;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG-04 (INV-1) — mot ghe tai mot thoi diem thuoc toi da mot nguoi, va han giu cho do <b>may
 * chu</b> quyet dinh: khach khong duoc gia han bang cach lap lai thao tac.
 *
 * <p>Hai lo cong huong thanh mot lo denial-of-booking do duoc:</p>
 * <ol>
 *   <li>{@code markHeld} dat {@code HeldUntil = DATEADD(MINUTE, ?, GETDATE())} VO DIEU KIEN, nen
 *       moi lan POST lai la mot lan gia han — giu ghe vinh vien.</li>
 *   <li>Khong co tran so don nhap: do thuc te 30/30 don {@code created} tren CUNG mot ghe, cung
 *       mot tai khoan. O phong 12 ghe chi can 12 vong lap la khoa ca suat chieu.</li>
 * </ol>
 */
@Tag("it")
public class Bug04SeatHoldAbuseIT {

    private static final int SHOWTIME_ID = 3;
    private static final int MEMBER = 1;
    private static final String SETTING_MAX_DRAFTS = "booking.maxOpenDraftsPerShowtime";

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
        deleteSetting();
        SettingsReader.clearCache();
    }

    @AfterEach
    public void cleanUp() throws Exception {
        restoreSeededDefault();
        SettingsReader.clearCache();
        cleanShowtime();
    }

    @Test
    @DisplayName("BUG-04a: POST lap lai tren ghe dang giu khong duoc dời han giu")
    public void repeatedDraftMustNotExtendExistingHold() throws Exception {
        int seatId = firstAvailableSeatId();

        OrderRecord first = bookingService.createDraftOrder(
                MEMBER, SHOWTIME_ID, List.of(seatId), Map.of(), null, "card");
        Timestamp heldUntilAfterFirst = heldUntilOf(seatId);

        // Phai vuot qua mot giay dong ho DB thi phep do moi phan biet duoc "giu nguyen" voi "gia han".
        Thread.sleep(1500L);

        OrderRecord second = bookingService.createDraftOrder(
                MEMBER, SHOWTIME_ID, List.of(seatId), Map.of(), null, "card");
        Timestamp heldUntilAfterSecond = heldUntilOf(seatId);

        assertTrue(first.getId() != second.getId(), "Fixture phai tao ra hai don rieng biet");
        assertEquals(heldUntilAfterFirst, heldUntilAfterSecond,
                "HeldUntil phai giu nguyen: khach khong duoc tu gia han giu cho bang thao tac lap");
    }

    @Test
    @DisplayName("BUG-04b: vuot tran so don nhap dang mo tren mot suat chieu phai bi 409")
    public void openDraftsPerShowtimeAreCapped() throws Exception {
        setMaxOpenDrafts("2");
        SettingsReader.clearCache();
        int seatId = firstAvailableSeatId();

        bookingService.createDraftOrder(MEMBER, SHOWTIME_ID, List.of(seatId), Map.of(), null, "card");
        bookingService.createDraftOrder(MEMBER, SHOWTIME_ID, List.of(seatId), Map.of(), null, "card");

        BookingException ex = null;
        try {
            bookingService.createDraftOrder(MEMBER, SHOWTIME_ID, List.of(seatId), Map.of(), null, "card");
        } catch (BookingException caught) {
            ex = caught;
        }

        assertTrue(ex != null, "Don thu ba vuot tran phai bi chan");
        assertEquals(409, ex.getStatusCode());
        assertEquals(2, openDraftCount(), "Khong duoc ghi them don nao sau khi vuot tran");
    }

    private int firstAvailableSeatId() {
        List<ShowtimeSeat> seats = bookingService.getSeatMap(SHOWTIME_ID);
        return seats.stream()
                .filter(s -> s.isAvailableFor(MEMBER) && !"couple".equalsIgnoreCase(s.getSeatType()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private static Timestamp heldUntilOf(int showtimeSeatId) throws Exception {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT HeldUntil FROM ShowtimeSeats WHERE Id = ?")) {
            ps.setInt(1, showtimeSeatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Khong tim thay ghe " + showtimeSeatId);
                }
                return rs.getTimestamp(1);
            }
        }
    }

    private static int openDraftCount() throws Exception {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM Orders WHERE UserId = ? AND ShowtimeId = ? AND OrderStatus = 'created'")) {
            ps.setInt(1, MEMBER);
            ps.setInt(2, SHOWTIME_ID);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void setMaxOpenDrafts(String value) throws Exception {
        String sql = """
                MERGE SystemSettings AS target
                USING (SELECT ? AS SettingKey) AS source
                   ON target.SettingKey = source.SettingKey
                WHEN MATCHED THEN UPDATE SET SettingValue = ?
                WHEN NOT MATCHED THEN INSERT (SettingKey, SettingValue) VALUES (?, ?);
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, SETTING_MAX_DRAFTS);
            ps.setString(2, value);
            ps.setString(3, SETTING_MAX_DRAFTS);
            ps.setString(4, value);
            ps.executeUpdate();
        }
    }

    /**
     * Tra bang cau hinh ve dung trang thai ma chuoi migration + seed de lai.
     *
     * <p>Truoc C.4 khoa nay khong co dong nao trong {@code SystemSettings}, nen "don dep" nghia
     * la xoa han. Nay no la mot dong seed that (fix27) va man hinh {@code /system/config} phai
     * thay duoc no — xoa di la de lai DB khac voi DB vua dung, lam
     * {@code C4MaxOpenDraftsSettingIT} do tuy theo thu tu chay.</p>
     */
    private static void restoreSeededDefault() throws Exception {
        // clampMaxOpenDrafts(0) tra ve dung mac dinh trong ma, tuc dung gia tri fix27 seed vao —
        // khong ghim cung so 3 o day de hai ben khong the lech nhau.
        setMaxOpenDrafts(String.valueOf(BookingService.clampMaxOpenDrafts(0)));
    }

    private static void deleteSetting() throws Exception {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM SystemSettings WHERE SettingKey = ?")) {
            ps.setString(1, SETTING_MAX_DRAFTS);
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

package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.ReportSummaryDto;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N-09 · N-10 · N-11 — du lieu thong bao va so lieu bao cao.
 */
@Tag("it")
@DisplayName("N-09/N-10/N-11 — so nhan thong bao va cong thuc bao cao")
public class NotificationAndReportIntegrityIT {

    private static final int CINEMA_ID = 1;

    private static AdminService adminService;
    private static User admin;
    private static User manager;

    @BeforeAll
    public static void setUp() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
        adminService = new AdminService();
        admin = new User();
        admin.setId(5);
        admin.setRole("admin");
        manager = new User();
        manager.setId(4);
        manager.setRole("manager");
        manager.setCinemaId(CINEMA_ID);
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.shutdown();
    }

    // ------------------------------------------------------------------------ N-09

    /**
     * {@code fix21_user_notifications.sql} ghi ro: khong khai duoc FK tu {@code NotificationId}
     * (cot nay tro toi hai bang tuy {@code SourceType}) nen "xoa thong bao thi xoa ca so nhan
     * cua no" phai lam tuong minh. Ban cu {@code deleteByTarget()} chi xoa
     * {@code AdminNotifications}, de lai so nhan mo coi — ban sua RM-01 chi doi cho mo coi
     * xuong bang duoi.
     */
    @Test
    @DisplayName("N-09: xoa phong -> so nhan cua thong bao phong do cung bien mat")
    public void hardDeletingARoomAlsoRemovesTheNotificationReceipts() throws SQLException {
        int roomId = insertRoom("IT N-09 phong co so nhan");
        int notificationId = insertNotification("Room", String.valueOf(roomId));
        insertRecipient(notificationId, admin.getId());
        insertRecipient(notificationId, manager.getId());
        assertEquals(2, countRecipients(notificationId), "tien de: phai co 2 dong so nhan");

        adminService.deleteRoom(roomId, admin, true);

        assertEquals(0, countNotifications("Room", String.valueOf(roomId)), "thong bao phai bi xoa");
        assertEquals(0, countRecipients(notificationId),
                "so nhan phai bi xoa theo — de lai la mot thong bao MOI trung Id se bi coi la da doc");
    }

    /**
     * Chieu nguoc lai cung phai dung: xoa THAT BAI thi khong duoc dong den so nhan.
     */
    @Test
    @DisplayName("N-09: xoa phong that bai -> so nhan van con nguyen")
    public void failedRoomDeleteKeepsTheReceipts() throws SQLException {
        int roomId = insertRoom("IT N-09 phong khong xoa duoc");
        int notificationId = insertNotification("Room", String.valueOf(roomId));
        insertRecipient(notificationId, admin.getId());
        int showtimeId = 0;
        try {
            showtimeId = insertShowtime(roomId, LocalDate.now().plusDays(40).atTime(20, 0));

            try {
                adminService.deleteRoom(roomId, admin, true);
            } catch (RuntimeException expected) {
                // DELETE FROM Rooms vo FK cua Showtimes — dung y cua ca kiem thu nay.
            }

            assertEquals(1, countRecipients(notificationId),
                    "xoa that bai thi so nhan phai con nguyen, khong duoc xoa truoc roi rollback nua voi");
            assertEquals(1, countNotifications("Room", String.valueOf(roomId)));
        } finally {
            if (showtimeId > 0) {
                exec("DELETE FROM ShowtimeSeats WHERE ShowtimeId = " + showtimeId);
                exec("DELETE FROM Showtimes WHERE Id = " + showtimeId);
            }
            exec("DELETE FROM NotificationRecipients WHERE NotificationId = " + notificationId
                    + " AND SourceType = 'admin'");
            exec("DELETE FROM AdminNotifications WHERE Id = " + notificationId);
            exec("DELETE FROM Seats WHERE RoomId = " + roomId);
            exec("DELETE FROM Rooms WHERE Id = " + roomId);
        }
    }

    // ------------------------------------------------------------------------ N-10

    /**
     * Ban cu lay tu so theo {@code o.CreatedAt} (ngay dat ve) va mau so theo
     * {@code s.StartTime} (ngay chieu). Ve ban THANG NAY cho suat THANG SAU vi vay vao tu so
     * thang nay ma ghe lai vao mau so thang sau — ti le lap ghe co the vuot 100%.
     */
    @Test
    @DisplayName("N-10: ve ban thang nay cho suat thang sau khong lam ti le lap ghe vuot 100%")
    public void occupancyRateStaysAnchoredToTheScreeningDate() throws SQLException {
        int roomId = insertRoom("IT N-10 phong bao cao");
        int showtimeId = 0;
        int orderId = 0;
        try {
            // Phong nhieu ghe hon tong so ghe cua cac suat trong thang nay: duoi cong thuc cu
            // (tu so theo ngay dat, mau so theo ngay chieu) ti le se vuot han 100%.
            insertSeats(roomId, 20);
            // Suat chieu nam o THANG SAU, don dat ve tao HOM NAY (thang nay).
            LocalDate nextMonthDay = YearMonth.from(LocalDate.now()).plusMonths(1).atDay(15);
            showtimeId = insertShowtime(roomId, nextMonthDay.atTime(20, 0));
            insertShowtimeSeats(showtimeId, roomId);
            orderId = insertPaidOrder(showtimeId);
            attachAllSeats(orderId, showtimeId);

            ReportSummaryDto summary = adminService.getReportSummary();

            assertTrue(summary.getCancelRateCurrent() >= 0, "bao cao phai chay duoc");
            double occupancy = occupancyPercent(summary);
            assertTrue(occupancy <= 100.0 + 1e-9,
                    "Ti le lap ghe thang nay khong duoc vuot 100% — dang la " + occupancy
                            + "%. Tu so va mau so phai cung neo vao ngay chieu.");
        } finally {
            if (orderId > 0) {
                exec("DELETE FROM OrderSeats WHERE OrderId = " + orderId);
                exec("DELETE FROM Orders WHERE Id = " + orderId);
            }
            if (showtimeId > 0) {
                exec("DELETE FROM ShowtimeSeats WHERE ShowtimeId = " + showtimeId);
                exec("DELETE FROM Showtimes WHERE Id = " + showtimeId);
            }
            exec("DELETE FROM Seats WHERE RoomId = " + roomId);
            exec("DELETE FROM Rooms WHERE Id = " + roomId);
        }
    }

    /**
     * Mau so phai la anh chup {@code ShowtimeSeats} cua suat, khong phai so ghe HIEN TAI cua
     * phong: sua so do ghe khong duoc lam thay doi bao cao cua nhung thang da qua.
     */
    @Test
    @DisplayName("N-10: them ghe cho phong khong lam doi ti le lap ghe cua suat da xep")
    public void changingRoomSeatLayoutDoesNotRewriteHistoricOccupancy() throws SQLException {
        int roomId = insertRoom("IT N-10 phong doi so do");
        int showtimeId = 0;
        int orderId = 0;
        try {
            insertSeats(roomId, 4);
            LocalDate thisMonthDay = YearMonth.from(LocalDate.now()).atDay(
                    Math.min(28, LocalDate.now().getDayOfMonth()));
            showtimeId = insertShowtime(roomId, thisMonthDay.atTime(23, 30));
            insertShowtimeSeats(showtimeId, roomId);
            orderId = insertPaidOrder(showtimeId);
            attachAllSeats(orderId, showtimeId);

            double before = occupancyPercent(adminService.getReportSummary());

            // Phong duoc mo rong sau khi suat da xep. ShowtimeSeats cua suat cu khong doi.
            insertSeats(roomId, 4, 100);
            double after = occupancyPercent(adminService.getReportSummary());

            assertEquals(before, after, 1e-9,
                    "Ti le lap ghe phai doc tu ShowtimeSeats cua suat, khong phai so ghe hien tai cua phong");
        } finally {
            if (orderId > 0) {
                exec("DELETE FROM OrderSeats WHERE OrderId = " + orderId);
                exec("DELETE FROM Orders WHERE Id = " + orderId);
            }
            if (showtimeId > 0) {
                exec("DELETE FROM ShowtimeSeats WHERE ShowtimeId = " + showtimeId);
                exec("DELETE FROM Showtimes WHERE Id = " + showtimeId);
            }
            exec("DELETE FROM Seats WHERE RoomId = " + roomId);
            exec("DELETE FROM Rooms WHERE Id = " + roomId);
        }
    }

    // ------------------------------------------------------------------------ N-11

    /**
     * O "chenh lech" cua dong "Don da thanh toan" truoc day lay phan tram thay doi cua VE
     * TRUNG BINH/NGAY — mot chi so khac han dang dung canh no.
     */
    @Test
    @DisplayName("N-11: chenh lech cua 'Don da thanh toan' tinh tu chinh so don, khong phai ve/ngay")
    public void paidOrdersMetricShowsItsOwnDifference() {
        ReportSummaryDto summary = adminService.getReportSummary(manager);

        ReportSummaryDto.MetricRow paidRow = summary.getMetrics().stream()
                .filter(row -> "Đơn đã thanh toán".equals(row.getName()))
                .findFirst().orElseThrow();

        double current = Double.parseDouble(paidRow.getCurrentValue());
        double previous = Double.parseDouble(paidRow.getPrevValue());
        String expected = expectedPercentText(current, previous);

        assertEquals(expected, paidRow.getDiffText(),
                "O chenh lech phai la % thay doi cua chinh paidOrders (" + current + " so voi "
                        + previous + "), khong phai cua avgTicketsPerDay ("
                        + summary.getAvgTicketsPerDayDiffPercent() + ")");
    }

    /** Cung cong thuc voi {@code calcDiffPercent} + {@code formatPercentDiffVal} cua service. */
    private static String expectedPercentText(double current, double previous) {
        double percent;
        if (previous == 0) {
            percent = current > 0 ? 100.0 : 0.0;
        } else {
            percent = (current - previous) * 100.0 / previous;
        }
        if (percent > 0) {
            return String.format(java.util.Locale.US, "+%.1f%%", percent);
        }
        if (percent < 0) {
            return String.format(java.util.Locale.US, "%.1f%%", percent);
        }
        return "0,0%";
    }

    private static double occupancyPercent(ReportSummaryDto summary) {
        String raw = summary.getMetrics().stream()
                .filter(row -> "Tỉ lệ lấp ghế".equals(row.getName()))
                .findFirst().orElseThrow()
                .getCurrentValue();
        return Double.parseDouble(raw.replace("%", ""));
    }

    // ---------------------------------------------------------------- fixtures

    private static int insertRoom(String name) throws SQLException {
        return insertReturningId("INSERT INTO Rooms (CinemaId, Name, Status) VALUES ("
                + CINEMA_ID + ", N'" + name + "', 'active')");
    }

    private static void insertSeats(int roomId, int count) throws SQLException {
        insertSeats(roomId, count, 0);
    }

    private static void insertSeats(int roomId, int count, int numberOffset) throws SQLException {
        for (int index = 1; index <= count; index++) {
            int number = numberOffset + index;
            exec("INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey, PriceSurcharge) "
                    + "VALUES (" + roomId + ", 'A', " + number + ", 'standard', 'A" + number + "', 0)");
        }
    }

    private static int insertShowtime(int roomId, java.time.LocalDateTime start) throws SQLException {
        int filmId = scalar("SELECT TOP 1 Id FROM Films ORDER BY Id");
        String startText = start.toString().replace('T', ' ');
        String endText = start.plusMinutes(120).toString().replace('T', ' ');
        return insertReturningId("INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice) "
                + "VALUES (" + filmId + ", " + CINEMA_ID + ", " + roomId + ", '" + startText + "', '"
                + endText + "', 90000)");
    }

    private static void insertShowtimeSeats(int showtimeId, int roomId) throws SQLException {
        exec("INSERT INTO ShowtimeSeats (ShowtimeId, SeatId, Status, ExtraFee) "
                + "SELECT " + showtimeId + ", Id, 'booked', 0 FROM Seats WHERE RoomId = " + roomId);
    }

    private static int insertPaidOrder(int showtimeId) throws SQLException {
        String ticketCode = "ITN10" + System.nanoTime();
        return insertReturningId("INSERT INTO Orders (UserId, ShowtimeId, SeatSubtotal, ComboSubtotal, "
                + "DiscountAmount, TotalAmount, TicketCode, PaymentMethod, PaymentStatus, OrderStatus) "
                + "VALUES (1, " + showtimeId + ", 90000, 0, 0, 90000, '" + ticketCode
                + "', 'card', 'paid', 'confirmed')");
    }

    private static void attachAllSeats(int orderId, int showtimeId) throws SQLException {
        exec("INSERT INTO OrderSeats (OrderId, ShowtimeSeatId, SeatKey, SeatType, UnitPrice) "
                + "SELECT " + orderId + ", ss.Id, se.SeatKey, 'standard', 90000 "
                + "FROM ShowtimeSeats ss JOIN Seats se ON se.Id = ss.SeatId "
                + "WHERE ss.ShowtimeId = " + showtimeId);
    }

    private static int insertNotification(String targetType, String targetId) throws SQLException {
        return insertReturningId(
                "INSERT INTO AdminNotifications (Title, Message, Category, Severity, TargetType, TargetId, IsRead) "
                + "VALUES (N'IT N-09', N'IT', 'IT', 'info', '" + targetType + "', '" + targetId + "', 0)");
    }

    private static void insertRecipient(int notificationId, int userId) throws SQLException {
        exec("INSERT INTO NotificationRecipients (SourceType, NotificationId, UserId) "
                + "VALUES ('admin', " + notificationId + ", " + userId + ")");
    }

    private static int countRecipients(int notificationId) throws SQLException {
        return scalar("SELECT COUNT(*) FROM NotificationRecipients "
                + "WHERE SourceType = 'admin' AND NotificationId = " + notificationId);
    }

    private static int countNotifications(String targetType, String targetId) throws SQLException {
        return scalar("SELECT COUNT(*) FROM AdminNotifications WHERE TargetType='"
                + targetType + "' AND TargetId='" + targetId + "'");
    }

    private static int insertReturningId(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
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

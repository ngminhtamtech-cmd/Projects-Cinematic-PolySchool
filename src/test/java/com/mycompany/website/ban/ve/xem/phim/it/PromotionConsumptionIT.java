package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * CB-ISS-001, CB-ISS-002, CB-ISS-003 — luot dung ma khuyen mai va tien giam gia.
 *
 * <p>Ha tang chan vuot luot da co san tu P12 nhung <b>chua he co test</b>: truoc phase nay khong co
 * file test nao cham tới promotion, voucher hay loyalty. Day la phan bu lai bang chung do.</p>
 */
@Tag("it")
public class PromotionConsumptionIT {
    private static final int SHOWTIME_ID = 3;
    private static final int MEMBER_A = 1;
    private static final int MEMBER_B = 2;
    private static final String CODE_PREFIX = "P4PROMO";

    private final BookingService bookingService = new BookingService();

    @BeforeAll
    public static void setUpTestDb() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
    }

    @BeforeEach
    @AfterEach
    public void resetFixtures() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement childrenA = conn.prepareStatement(
                     "DELETE FROM Invoices WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = " + SHOWTIME_ID + ")");
             PreparedStatement childrenB = conn.prepareStatement(
                     "DELETE FROM PromotionUsage WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = " + SHOWTIME_ID + ")");
             PreparedStatement childrenC = conn.prepareStatement(
                     "DELETE FROM RefundTransactions WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = " + SHOWTIME_ID + ")");
             PreparedStatement seats = conn.prepareStatement(
                     "DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = " + SHOWTIME_ID + ")");
             PreparedStatement orders = conn.prepareStatement(
                     "DELETE FROM Orders WHERE ShowtimeId = " + SHOWTIME_ID);
             PreparedStatement usage = conn.prepareStatement(
                     "DELETE FROM PromotionUsage WHERE PromotionId IN (SELECT Id FROM Promotions WHERE Code LIKE '"
                             + CODE_PREFIX + "%')");
             PreparedStatement promos = conn.prepareStatement(
                     "DELETE FROM Promotions WHERE Code LIKE '" + CODE_PREFIX + "%'");
             PreparedStatement showtimeSeats = conn.prepareStatement(
                     "UPDATE ShowtimeSeats SET Status = 'available', HeldByUserId = NULL, HeldUntil = NULL"
                             + " WHERE ShowtimeId = " + SHOWTIME_ID + " AND Status != 'maintenance'")) {
            childrenA.executeUpdate();
            childrenB.executeUpdate();
            childrenC.executeUpdate();
            seats.executeUpdate();
            orders.executeUpdate();
            usage.executeUpdate();
            promos.executeUpdate();
            showtimeSeats.executeUpdate();
        }
    }

    private int createPromotion(String code, Integer usageLimit, int usedCount,
            int perUserLimit, int startOffsetDays, int endOffsetDays) throws Exception {
        String sql = """
                INSERT INTO Promotions (Code, Description, DiscountPercent, MaxDiscount, StartDate, EndDate,
                    ConditionsJson, UsageLimit, UsedCount, Status, VoucherType, TargetTier, PointsRequired, PerUserLimit)
                VALUES (?, 'Phase 4 regression', 10, 50000, DATEADD(DAY, ?, CAST(GETDATE() AS DATE)),
                    DATEADD(DAY, ?, CAST(GETDATE() AS DATE)), NULL, ?, ?, 'active', 'PUBLIC', NULL, 0, ?)
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setInt(2, startOffsetDays);
            ps.setInt(3, endOffsetDays);
            if (usageLimit == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, usageLimit);
            }
            ps.setInt(5, usedCount);
            ps.setInt(6, perUserLimit);
            assertEquals(1, ps.executeUpdate());
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT Id FROM Promotions WHERE Code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private int usedCountOf(int promotionId) throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT UsedCount FROM Promotions WHERE Id = ?")) {
            ps.setInt(1, promotionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private String orderStateOf(int orderId) throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT OrderStatus + '/' + PaymentStatus FROM Orders WHERE Id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    /**
     * Hai nhom ghe doc lap de dat duoc hai don song song tren cung mot suat.
     *
     * <p>Fixture cua suat 3 chi co mot ghe thuong con trong, phan con lai la mot cap ghe doi —
     * ghe doi bat buoc dat ca cap nen nhom thu hai gom du hai ghe.</p>
     */
    private List<List<Integer>> twoIndependentSeatGroups() {
        List<ShowtimeSeat> seats = bookingService.getSeatMap(SHOWTIME_ID);
        List<Integer> singles = new ArrayList<>();
        List<Integer> couples = new ArrayList<>();
        for (ShowtimeSeat seat : seats) {
            if (!seat.isAvailableFor(MEMBER_A)) {
                continue;
            }
            if ("couple".equalsIgnoreCase(seat.getSeatType())) {
                couples.add(seat.getId());
            } else {
                singles.add(seat.getId());
            }
        }
        assertTrue(!singles.isEmpty(), "Fixture phai con it nhat mot ghe thuong");
        assertEquals(2, couples.size(), "Fixture phai con dung mot cap ghe doi");
        return List.of(List.of(singles.get(0)), List.copyOf(couples));
    }

    private List<Integer> oneSeat() {
        return twoIndependentSeatGroups().get(0);
    }

    @Test
    @DisplayName("CB-ISS-001: ma het luot thi thanh toan bi chan va don khong thanh paid/confirmed")
    public void testExhaustedPromotionBlocksPayment() throws Exception {
        String code = CODE_PREFIX + "FULL";
        int promotionId = createPromotion(code, 1, 1, 0, -1, 7);
        List<Integer> seatIds = oneSeat();

        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_A, SHOWTIME_ID, seatIds, Map.of(), null, "card");

        BookingException ex = assertThrows(BookingException.class,
                () -> bookingService.payOrder(MEMBER_A, draft.getId(), Map.of(), code, "card"));
        assertEquals(400, ex.getStatusCode(), ex.getMessage());

        assertEquals(1, usedCountOf(promotionId), "UsedCount khong duoc vuot UsageLimit");
        assertNotEquals("confirmed/paid", orderStateOf(draft.getId()),
                "Don khong duoc thanh paid/confirmed khi ma da het luot");
    }

    @Test
    @DisplayName("CB-ISS-001: hai luong tranh luot cuoi — dung mot thanh cong, UsedCount khong vuot")
    public void testConcurrentPaymentsCannotExceedUsageLimit() throws Exception {
        String code = CODE_PREFIX + "RACE";
        int promotionId = createPromotion(code, 1, 0, 0, -1, 7);
        List<List<Integer>> seatGroups = twoIndependentSeatGroups();

        OrderRecord draftA = bookingService.createDraftOrder(
                MEMBER_A, SHOWTIME_ID, seatGroups.get(0), Map.of(), null, "card");
        OrderRecord draftB = bookingService.createDraftOrder(
                MEMBER_B, SHOWTIME_ID, seatGroups.get(1), Map.of(), null, "card");

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        Runnable payA = payTask(start, done, succeeded, rejected, MEMBER_A, draftA.getId(), code);
        Runnable payB = payTask(start, done, succeeded, rejected, MEMBER_B, draftB.getId(), code);
        new Thread(payA, "pay-a").start();
        new Thread(payB, "pay-b").start();
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "Hai luong phai ket thuc trong 30 giay");

        assertEquals(1, succeeded.get(), "Chi mot luong duoc dung luot cuoi");
        assertEquals(1, rejected.get(), "Luong con lai phai bi tu choi");
        assertEquals(1, usedCountOf(promotionId), "UsedCount khong duoc vuot UsageLimit = 1");
    }

    private Runnable payTask(CountDownLatch start, CountDownLatch done, AtomicInteger succeeded,
            AtomicInteger rejected, int userId, int orderId, String code) {
        return () -> {
            try {
                start.await();
                bookingService.payOrder(userId, orderId, Map.of(), code, "card");
                succeeded.incrementAndGet();
            } catch (BookingException ex) {
                rejected.incrementAndGet();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        };
    }

    @Test
    @DisplayName("CB-ISS-001: moi lan thanh toan thanh cong chi tang UsedCount dung mot")
    public void testSuccessfulPaymentIncrementsUsedCountByOne() throws Exception {
        String code = CODE_PREFIX + "ONCE";
        int promotionId = createPromotion(code, 10, 3, 0, -1, 7);

        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_A, SHOWTIME_ID, oneSeat(), Map.of(), null, "card");
        OrderRecord paid = bookingService.payOrder(MEMBER_A, draft.getId(), Map.of(), code, "card");

        assertEquals("paid", paid.getPaymentStatus());
        assertEquals(4, usedCountOf(promotionId));
    }

    @Test
    @DisplayName("CB-ISS-001: PerUserLimit=1 thi cung tai khoan dung lan hai bi chan")
    public void testPerUserLimitBlocksSecondUseBySameAccount() throws Exception {
        String code = CODE_PREFIX + "PERUSER";
        createPromotion(code, null, 0, 1, -1, 7);
        List<List<Integer>> seatGroups = twoIndependentSeatGroups();

        OrderRecord first = bookingService.createDraftOrder(
                MEMBER_A, SHOWTIME_ID, seatGroups.get(0), Map.of(), null, "card");
        bookingService.payOrder(MEMBER_A, first.getId(), Map.of(), code, "card");

        OrderRecord second = bookingService.createDraftOrder(
                MEMBER_A, SHOWTIME_ID, seatGroups.get(1), Map.of(), null, "card");
        BookingException ex = assertThrows(BookingException.class,
                () -> bookingService.payOrder(MEMBER_A, second.getId(), Map.of(), code, "card"));
        assertEquals(400, ex.getStatusCode(), ex.getMessage());
        assertNotEquals("confirmed/paid", orderStateOf(second.getId()));
    }

    @Test
    @DisplayName("CB-ISS-002: backend tu choi ma het han, ma khong ton tai; ma hop le khac CINE10 van chay")
    public void testBackendValidatesPromotionRegardlessOfClient() throws Exception {
        String expired = CODE_PREFIX + "EXPIRED";
        createPromotion(expired, null, 0, 0, -30, -10);
        String usable = CODE_PREFIX + "OK";
        createPromotion(usable, null, 0, 0, -1, 7);

        assertEquals(400, assertThrows(BookingException.class,
                () -> bookingService.resolvePromotion(expired, MEMBER_A)).getStatusCode());
        assertEquals(400, assertThrows(BookingException.class,
                () -> bookingService.resolvePromotion(CODE_PREFIX + "NOSUCHCODE", MEMBER_A)).getStatusCode());
        assertEquals(usable, bookingService.resolvePromotion(usable, MEMBER_A).getCode(),
                "Ma hop le bat ky phai duoc xu ly, khong chi rieng CINE10");
        assertEquals(usable, bookingService.resolvePromotion(usable.toLowerCase(), MEMBER_A).getCode(),
                "Ma nhap chu thuong van phai nhan ra");
    }

    @Test
    @DisplayName("CB-ISS-003: server tu tinh lai giam gia va tong tien, khong nhan tong tu client")
    public void testServerRecomputesDiscountAndTotal() throws Exception {
        String code = CODE_PREFIX + "MONEY";
        createPromotion(code, null, 0, 0, -1, 7);

        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_A, SHOWTIME_ID, oneSeat(), Map.of(), null, "card");
        OrderRecord paid = bookingService.payOrder(MEMBER_A, draft.getId(), Map.of(), code, "card");

        BigDecimal seatSubtotal = paid.getSeatSubtotal();
        BigDecimal comboSubtotal = paid.getComboSubtotal() == null ? BigDecimal.ZERO : paid.getComboSubtotal();
        BigDecimal gross = seatSubtotal.add(comboSubtotal);
        BigDecimal expectedDiscount = gross.multiply(new BigDecimal("0.10"))
                .min(new BigDecimal("50000"))
                .setScale(0, java.math.RoundingMode.HALF_UP);

        assertEquals(0, expectedDiscount.compareTo(paid.getDiscountAmount()),
                "Giam gia phai do server tinh: 10% co tran 50.000");
        assertEquals(0, gross.subtract(paid.getDiscountAmount()).setScale(0, java.math.RoundingMode.HALF_UP)
                        .compareTo(paid.getTotalAmount()),
                "Tong phai bang (ghe + combo) - giam gia");
        assertTrue(paid.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0,
                "Ma hop le phai thuc su giam tien");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT TotalAmount, DiscountAmount FROM Orders WHERE Id = ?")) {
            ps.setInt(1, draft.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, paid.getTotalAmount().compareTo(rs.getBigDecimal("TotalAmount")),
                        "Tong trong DB phai khop tong tra ve");
                assertEquals(0, paid.getDiscountAmount().compareTo(rs.getBigDecimal("DiscountAmount")));
            }
        }
    }
}

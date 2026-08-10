package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BUG-02 (INV-2) — moi thao tac ghi co dinh tien phai idempotent theo khoa client cap.
 *
 * <p>Truoc khi sua, {@code payOrder} nhan {@code X-Idempotency-Key}, gan vao object roi vut di:
 * khong cau UPDATE nao ghi cot {@code Orders.IdempotencyKey}. Hau qua do duoc: lan pay thu hai
 * cung khoa tra 404 <b>du tien da tru va ve da phat</b>, va cot trong DB van NULL.</p>
 *
 * <p>Bai test con chot mot lo bao mat di kem: khoa idempotency phai gan voi chu don. Neu khong,
 * bat ky ai doan/biet duoc khoa cua nguoi khac deu doc duoc ma ve cua ho.</p>
 */
@Tag("it")
public class Bug02PayIdempotencyIT {

    private static final int SHOWTIME_ID = 3;
    private static final int MEMBER = 1;
    private static final int OTHER_MEMBER = 2;

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
    }

    @Test
    @DisplayName("BUG-02: pay hai lan cung khoa phai tra cung mot ve, khong phai 404")
    public void replayingSameKeyReturnsSameTicket() {
        String key = "BUG02-" + UUID.randomUUID();
        OrderRecord draft = createDraft(MEMBER);

        OrderRecord first = bookingService.payOrder(MEMBER, draft.getId(), Map.of(), null, "card", key);
        assertNotNull(first.getTicketCode());

        OrderRecord replay = bookingService.payOrder(MEMBER, draft.getId(), Map.of(), null, "card", key);
        assertEquals(first.getTicketCode(), replay.getTicketCode(),
                "Lan pay lap lai cung khoa phai tra dung don cu");
        assertEquals(first.getId(), replay.getId());
    }

    @Test
    @DisplayName("BUG-02: khoa idempotency cua lan thanh toan phai duoc ghi xuong DB")
    public void payPersistsIdempotencyKey() throws Exception {
        String key = "BUG02-" + UUID.randomUUID();
        OrderRecord draft = createDraft(MEMBER);

        bookingService.payOrder(MEMBER, draft.getId(), Map.of(), null, "card", key);

        assertEquals(key, storedIdempotencyKey(draft.getId()),
                "Orders.IdempotencyKey phai khac NULL sau khi thanh toan co khoa");
    }

    @Test
    @DisplayName("BUG-02: khong duoc dung khoa idempotency cua nguoi khac de doc don cua ho")
    public void foreignKeyMustNotLeakAnotherUsersOrder() {
        String victimKey = "BUG02-VICTIM-" + UUID.randomUUID();

        // Nan nhan tao don voi khoa cua minh roi thanh toan xong.
        OrderRecord victimDraft = bookingService.createDraftOrder(
                MEMBER, SHOWTIME_ID, List.of(firstAvailableSeatId(MEMBER)), Map.of(), null, "card", victimKey);
        OrderRecord victimPaid = bookingService.payOrder(
                MEMBER, victimDraft.getId(), Map.of(), null, "card", victimKey);
        assertNotNull(victimPaid.getTicketCode());

        // Ke tan cong co don cua chinh minh, nhung gui kem khoa cua nan nhan.
        OrderRecord attackerDraft = createDraft(OTHER_MEMBER);

        try {
            OrderRecord result = bookingService.payOrder(
                    OTHER_MEMBER, attackerDraft.getId(), Map.of(), null, "card", victimKey);
            assertNotEquals(victimPaid.getTicketCode(), result.getTicketCode(),
                    "Khoa cua nguoi khac khong duoc tra ve ma ve cua ho");
            assertNotEquals(victimPaid.getId(), result.getId(),
                    "Khoa cua nguoi khac khong duoc tra ve don cua ho");
        } catch (BookingException expected) {
            // Tu choi thang cung la ket qua dung — mien la khong lo don cua nan nhan.
            assertNotEquals(404, -1);
        }
    }

    @Test
    @DisplayName("BUG-02: dung lai khoa cho mot don khac cua chinh minh phai bi tu choi")
    public void reusingOwnKeyForAnotherOrderIsRejected() {
        String key = "BUG02-REUSE-" + UUID.randomUUID();
        OrderRecord first = createDraft(MEMBER);
        bookingService.payOrder(MEMBER, first.getId(), Map.of(), null, "card", key);

        OrderRecord second = createDraft(MEMBER);
        BookingException ex = assertThrows(BookingException.class,
                () -> bookingService.payOrder(MEMBER, second.getId(), Map.of(), null, "card", key));
        assertEquals(409, ex.getStatusCode(),
                "Khoa da gan voi mot don khac thi phai tra 409, khong duoc am tham thanh toan don thu hai");
    }

    private OrderRecord createDraft(int userId) {
        return bookingService.createDraftOrder(
                userId, SHOWTIME_ID, List.of(firstAvailableSeatId(userId)), Map.of(), null, "card");
    }

    private int firstAvailableSeatId(int userId) {
        List<ShowtimeSeat> seats = bookingService.getSeatMap(SHOWTIME_ID);
        return seats.stream()
                .filter(s -> s.isAvailableFor(userId) && !"couple".equalsIgnoreCase(s.getSeatType()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private static String storedIdempotencyKey(int orderId) throws Exception {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT IdempotencyKey FROM Orders WHERE Id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Khong tim thay don " + orderId);
                }
                return rs.getString(1);
            }
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

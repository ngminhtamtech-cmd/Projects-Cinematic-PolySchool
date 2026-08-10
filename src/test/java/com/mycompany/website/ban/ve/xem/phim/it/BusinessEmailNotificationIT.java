package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.Promotion;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.EmailService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nam la thu nghiep vu: uu dai, ve don quay, check-in, khang cao, hoan tien.
 *
 * <p><b>Moi ca deu co mot ve doi lap.</b> "Co gui thu" mot minh no khong chung minh duoc gi —
 * cai de sai la gui <i>thua</i>: sua uu dai cung gui, quet ve lan hai cung gui. Nen moi ca duong
 * di kem mot ca am tren dung nhanh de sai do.</p>
 *
 * <p>Chay o che do {@code mail.mode=logfile} (DB tam do {@code fix08} seed), nen "da gui" duoc
 * doc bang phan ghi them vao {@code cinebook-mail.log}.</p>
 */
@Tag("it")
@DisplayName("Thu nghiep vu — gui dung nhanh, dung mot lan")
public class BusinessEmailNotificationIT {

    private static final String PREFIX = "BMAIL-";
    private static final int ADMIN_ID = 5;
    private static final int MEMBER_BRONZE = 1;
    private static final String BRONZE_EMAIL = "member_bronze@test.com";
    private static final String DIAMOND_EMAIL = "member_diamond@test.com";

    static {
        System.setProperty("cinebook.db.config",
                System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    /** Suat chieu do lop nay tao ra — phai xoa het de khong lam nhieu lop test khac. */
    private static final java.util.List<Integer> CREATED_SHOWTIMES = new java.util.ArrayList<>();

    private final AdminService adminService = new AdminService();

    @BeforeAll
    public static void setUp() {
        System.setProperty("cinebook.db.config",
                System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
        // mail.mode duoc cache 60 giay trong process; xoa de chac chan doc lai tu DB tam.
        EmailService.clearCache();
    }

    @AfterAll
    public static void tearDown() throws SQLException {
        purge();
        DBConnection.shutdown();
    }

    /**
     * Mo khoa lai member sau moi ca.
     *
     * <p>Ca khang cao khoa {@code member_bronze} de dung voi tinh huong that. Khong mo lai thi cac
     * ca chay sau chi con MOT member dang hoat dong — va ca dem so nguoi nhan se do vi mot ly do
     * khong lien quan gi toi thu tu. JUnit khong dam bao thu tu chay, nen phai don sau moi ca chu
     * khong phai cuoi lop.</p>
     */
    @AfterEach
    public void unlockMember() throws SQLException {
        execute("UPDATE Users SET IsLocked = 0 WHERE Id = ?", MEMBER_BRONZE);
    }

    // ------------------------------------------------------------------ uu dai

    @Test
    @DisplayName("Tao uu dai moi thi gui thu; SUA chinh uu dai do thi khong gui nua")
    public void createAnnouncesButUpdateDoesNot() throws Exception {
        Promotion promotion = newPromotion(PREFIX + "NEW1", "ALL", "active");

        String created = captureMail(() -> adminService.savePromotion(promotion, admin()));
        assertTrue(created.contains(PREFIX + "NEW1"), "Tao moi phai gui thu kem ma uu dai");
        assertTrue(created.contains(BRONZE_EMAIL), "Member phai nam trong danh sach nhan");

        // Doi tuong da co Id > 0 nen lan goi nay di vao nhanh UPDATE.
        promotion.setDescription("Mo ta da sua");
        String updated = captureMail(() -> adminService.savePromotion(promotion, admin()));
        assertFalse(updated.contains(PREFIX + "NEW1"),
                "Sua uu dai KHONG duoc gui lai — moi lan bam Luu se thanh mot dot gui hang loat");
    }

    @Test
    @DisplayName("Uu dai tao san o trang thai inactive thi khong lam phien ai")
    public void inactivePromotionIsNotAnnounced() throws Exception {
        Promotion promotion = newPromotion(PREFIX + "DRAFT", "ALL", "inactive");

        String log = captureMail(() -> adminService.savePromotion(promotion, admin()));
        assertFalse(log.contains(PREFIX + "DRAFT"),
                "Uu dai chua bat khong duoc bao ra ngoai");
    }

    @Test
    @DisplayName("Uu dai gioi han hang chi toi dung hang do")
    public void tierRestrictedPromotionReachesOnlyThatTier() throws Exception {
        // Chot lai hang thanh vien ve dung gia tri fixture: cac lop test khac trong cung dot chay
        // co the da nang hang member_bronze, va luc do ca nay do vi mot ly do khong lien quan gi
        // toi viec loc theo hang.
        execute("UPDATE Users SET MembershipTier = 'BRONZE' WHERE Id = ?", MEMBER_BRONZE);
        execute("UPDATE Users SET MembershipTier = 'DIAMOND' WHERE Email = ?", DIAMOND_EMAIL);

        Promotion promotion = newPromotion(PREFIX + "DIA", "DIAMOND", "active");

        String log = captureMail(() -> adminService.savePromotion(promotion, admin()));
        assertTrue(log.contains(DIAMOND_EMAIL), "Member DIAMOND phai nhan duoc");
        assertFalse(log.contains(BRONZE_EMAIL),
                "Member BRONZE khong duoc nhan thu ve voucher ho khong dung duoc");
    }

    // ------------------------------------------------------------------ ve don quay

    @Test
    @DisplayName("Thu tien don tai quay thi khach nhan ve dien tu — lo hong cu")
    public void counterOrderPaidSendsTicket() throws Exception {
        int showtimeId = insertShowtime(120, 240);
        String ticket = PREFIX + "CNT1";
        int orderId = insertOrder(showtimeId, ticket, "counter", "pending", "confirmed");
        execute("UPDATE Orders SET CounterExpiresAt = DATEADD(MINUTE, 30, GETDATE()) WHERE Id = ?", orderId);

        String log = captureMail(() -> adminService.markCounterOrderPaid(orderId, admin()));

        assertTrue(log.contains("Vé điện tử #" + orderId), "Don quay phai nhan thu ve");
        assertTrue(log.contains(ticket), "Thu phai chua ma ve");
        assertTrue(log.contains(BRONZE_EMAIL), "Thu phai gui dung chu don");
    }

    // ------------------------------------------------------------------ check-in

    @Test
    @DisplayName("Check-in thanh cong gui thu cam on; quet lai lan hai thi khong gui them")
    public void checkInSendsThanksOnceOnly() throws Exception {
        // Cua so check-in mo tu 60 phut truoc gio chieu; dat suat bat dau sau 10 phut.
        int showtimeId = insertShowtime(10, 130);
        String ticket = PREFIX + "CHK1";
        int orderId = insertOrder(showtimeId, ticket, "card", "paid", "confirmed");
        attachSeat(orderId, showtimeId);

        String first = captureMail(() -> adminService.redeemTicket(ticket, admin()));
        assertTrue(first.contains("Check-in thành công"), "Phai gui thu check-in");
        assertTrue(first.contains(ticket), "Thu phai chua ma ve");
        assertTrue(first.contains("Chúc bạn xem phim"), "Thu check-in phai co loi chuc");

        String second = captureMail(() ->
                assertThrows(BookingException.class, () -> adminService.redeemTicket(ticket, admin())));
        assertFalse(second.contains("Check-in thành công"),
                "Quet lai ve da dung khong duoc sinh la thu thu hai");
    }

    // ------------------------------------------------------------------ hoan tien

    @Test
    @DisplayName("Duyet hoan tien gui thu kem so tien da dinh dang")
    public void refundApprovedSendsMail() throws Exception {
        int showtimeId = insertShowtime(120, 240);
        String ticket = PREFIX + "RFA1";
        int orderId = insertOrder(showtimeId, ticket, "card", "paid", "confirmed");

        String log = captureMail(() -> adminService.refundOrder(orderId,
                new BigDecimal("100000"), "Khach doi lich", admin(), true));

        assertTrue(log.contains("Đã hoàn tiền đơn #" + orderId), "Phai gui thu hoan tien");
        assertTrue(log.contains("100.000 ₫"),
                "So tien phai duoc dinh dang o tang Java, khong de BigDecimal.toString() lot ra");
    }

    /**
     * Ly do tu choi la chuoi <b>quan tri vien tu go</b> — day la duong duy nhat cho phep nguoi
     * dung dua ky tu tuy y vao mot la thu HTML, nen ca nay chot luon viec escape tren duong that
     * chu khong chi trong unit test.
     */
    @Test
    @DisplayName("Tu choi hoan tien gui thu kem ly do, va ly do bi escape dung cach")
    public void refundRejectedMailCarriesEscapedReason() throws Exception {
        int showtimeId = insertShowtime(120, 240);
        String ticket = PREFIX + "RFR1";
        int orderId = insertOrder(showtimeId, ticket, "card", "paid", "confirmed");

        String log = captureMail(() -> adminService.rejectRefund(orderId,
                "Quá hạn & <b>không</b> hợp lệ", admin()));

        assertTrue(log.contains("không được chấp thuận"), "Phai gui thu tu choi hoan tien");
        assertTrue(log.contains("&amp;"), "Dau & trong ly do phai duoc escape");
        assertFalse(log.contains("<b>không</b>"),
                "Markup quan tri vien go phai bi escape truoc khi vao than thu");
    }

    // ------------------------------------------------------------------ khang cao

    @Test
    @DisplayName("Duyet khang cao gui thu mo khoa; tu choi gui thu kem ly do")
    public void appealResolutionSendsMailBothWays() throws Exception {
        int approvedId = insertAppeal("Toi da doc lai quy dinh");
        String approved = captureMail(() ->
                adminService.resolveAppeal(approvedId, true, "Da xem xet lai", admin()));
        assertTrue(approved.contains("Đơn kháng cáo đã được duyệt"), "Phai gui thu duyet");
        assertTrue(approved.contains(BRONZE_EMAIL), "Thu phai toi chu don");

        execute("UPDATE Users SET IsLocked = 1 WHERE Id = ?", MEMBER_BRONZE);
        int rejectedId = insertAppeal("Xin xem xet lai lan nua");
        String rejected = captureMail(() ->
                adminService.resolveAppeal(rejectedId, false, "Vi pham lap lai", admin()));
        assertTrue(rejected.contains("Kết quả đơn kháng cáo"), "Phai gui thu tu choi");
        assertTrue(rejected.contains("Vi pham lap lai"), "Thu tu choi phai neu ly do cua admin");
    }

    // ---------------------------------------------------------------- helpers

    /** Chay mot thao tac roi tra ve phan VUA duoc ghi them vao cinebook-mail.log. */
    private String captureMail(ThrowingAction action) throws Exception {
        Path mailLog = EmailService.mailLogPath();
        int before = Files.exists(mailLog)
                ? Files.readString(mailLog, StandardCharsets.UTF_8).length() : 0;
        action.run();
        if (!Files.exists(mailLog)) {
            return "";
        }
        String after = Files.readString(mailLog, StandardCharsets.UTF_8);
        // Cat theo do dai CHUOI, khong theo so byte: file UTF-8 co tieng Viet nen hai con so lech nhau.
        return after.length() > before ? after.substring(before) : "";
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private User admin() {
        User actor = new User();
        actor.setId(ADMIN_ID);
        actor.setRole("admin");
        actor.setEmail("admin@test.com");
        actor.setFullName("Admin System Test");
        return actor;
    }

    private Promotion newPromotion(String code, String tier, String status) {
        Promotion promotion = new Promotion();
        promotion.setCode(code);
        promotion.setDescription("Uu dai kiem thu " + code);
        promotion.setDiscountPercent(10.0);
        promotion.setMaxDiscount(new BigDecimal("50000"));
        promotion.setStartDate(LocalDate.now().minusDays(1));
        promotion.setEndDate(LocalDate.now().plusDays(30));
        promotion.setStatus(status);
        promotion.setVoucherType("ALL".equalsIgnoreCase(tier) ? "PUBLIC" : "TIER_RESTRICTED");
        promotion.setTargetTier(tier);
        promotion.setPerUserLimit(1);
        return promotion;
    }

    /**
     * Suat chieu tam cho mot ca kiem thu.
     *
     * <p>Id duoc ghi lai de {@link #purge()} xoa het. Bo qua buoc nay thi cac suat chieu nay o lai
     * trong DB tam suot ca dot chay va lam do nhung lop test dem so suat chieu cua rap 1 — mot ca
     * do vi ly do khong lien quan gi toi no.</p>
     */
    private int insertShowtime(int startMinutes, int endMinutes) throws SQLException {
        int showtimeId = insert("""
                INSERT INTO Showtimes (FilmId,CinemaId,RoomId,StartTime,EndTime,BasePrice,Format,Version,Language)
                VALUES (1,1,1,DATEADD(MINUTE,?,GETDATE()),DATEADD(MINUTE,?,GETDATE()),
                        100000,'2D','Subtitle','Vietnamese')
                """, startMinutes, endMinutes);
        CREATED_SHOWTIMES.add(showtimeId);
        return showtimeId;
    }

    private int insertOrder(int showtimeId, String ticket, String method, String paymentStatus,
            String orderStatus) throws SQLException {
        return insert("""
                INSERT INTO Orders
                    (UserId,ShowtimeId,SeatSubtotal,ComboSubtotal,DiscountAmount,TotalAmount,
                     TicketCode,TicketQrUrl,PaymentMethod,PaymentStatus,TransactionId,OrderStatus)
                VALUES (?,?,100000,0,0,100000,?,?,?,?,?,?)
                """, MEMBER_BRONZE, showtimeId, ticket, "/tickets/qr/" + ticket, method,
                paymentStatus, PREFIX + "TX-" + ticket, orderStatus);
    }

    private void attachSeat(int orderId, int showtimeId) throws SQLException {
        int seatId = queryInt("SELECT TOP 1 Id FROM Seats WHERE RoomId=1 ORDER BY Id");
        int showtimeSeatId = insert(
                "INSERT INTO ShowtimeSeats (ShowtimeId,SeatId,Status,ExtraFee) VALUES (?,?,'booked',0)",
                showtimeId, seatId);
        execute("""
                INSERT INTO OrderSeats (OrderId,ShowtimeSeatId,SeatKey,SeatType,UnitPrice)
                SELECT ?,ss.Id,s.SeatKey,s.SeatType,100000
                FROM ShowtimeSeats ss JOIN Seats s ON s.Id=ss.SeatId WHERE ss.Id=?
                """, orderId, showtimeSeatId);
    }

    private int insertAppeal(String reason) throws SQLException {
        return insert("""
                INSERT INTO UserAppeals (UserId,Email,Reason,Status,AppealType)
                VALUES (?,?,?,'pending','account')
                """, MEMBER_BRONZE, BRONZE_EMAIL, PREFIX + reason);
    }

    private static void purge() throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            // Thu tu xoa bam theo khoa ngoai: hoan tien va thu tien tai quay deu sinh hoa don,
            // nen Invoices phai di truoc Orders.
            for (String sql : new String[] {
                "DELETE FROM UserAppeals WHERE Reason LIKE '" + PREFIX + "%'",
                "DELETE FROM Invoices WHERE OrderId IN (SELECT Id FROM Orders WHERE TicketCode LIKE '"
                        + PREFIX + "%')",
                "DELETE FROM RefundTransactions WHERE OrderId IN (SELECT Id FROM Orders "
                        + "WHERE TicketCode LIKE '" + PREFIX + "%')",
                "DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE TicketCode LIKE '"
                        + PREFIX + "%')",
                "DELETE FROM Orders WHERE TicketCode LIKE '" + PREFIX + "%'",
                "DELETE FROM Promotions WHERE Code LIKE '" + PREFIX + "%'",
                "UPDATE Users SET IsLocked = 0 WHERE Id = " + MEMBER_BRONZE }) {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.executeUpdate();
                }
            }
            // Suat chieu di sau cung: OrderSeats/Orders o tren da tro toi ShowtimeSeats cua chung.
            for (int showtimeId : CREATED_SHOWTIMES) {
                for (String sql : new String[] {
                    "DELETE FROM ShowtimeSeats WHERE ShowtimeId = ?",
                    "DELETE FROM Showtimes WHERE Id = ?" }) {
                    try (PreparedStatement ps = connection.prepareStatement(sql)) {
                        ps.setInt(1, showtimeId);
                        ps.executeUpdate();
                    }
                }
            }
            CREATED_SHOWTIMES.clear();
        }
    }

    private int insert(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, values);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, values);
            ps.executeUpdate();
        }
    }

    private int queryInt(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, values);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Khong co du lieu cho: " + sql);
                return rs.getInt(1);
            }
        }
    }

    private void bind(PreparedStatement ps, Object... values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            ps.setObject(i + 1, values[i]);
        }
    }

    /** Giu import assertEquals duoc dung — kiem so luong nguoi nhan cua uu dai toan he thong. */
    @Test
    @DisplayName("Uu dai toan he thong toi dung so member dang hoat dong")
    public void publicPromotionReachesEveryActiveMember() throws Exception {
        int activeMembers = queryInt(
                "SELECT COUNT(*) FROM Users WHERE Role='member' AND ISNULL(IsLocked,0)=0 "
                + "AND Email IS NOT NULL AND LTRIM(RTRIM(Email))<>''");
        Promotion promotion = newPromotion(PREFIX + "ALLM", "ALL", "active");

        String log = captureMail(() -> adminService.savePromotion(promotion, admin()));

        // Dem theo dong "Nguoi nhan" cua tung ban ghi log, KHONG theo chu de: chu de xuat hien
        // hai lan moi la thu (dong tieu de cua log, va the <title> cua khung HTML), nen dem chu de
        // se ra gap doi.
        int sent = log.split("Người nhận", -1).length - 1;
        assertEquals(activeMembers, sent,
                "So thu phai bang dung so member dang hoat dong, khong thua khong thieu");
    }
}

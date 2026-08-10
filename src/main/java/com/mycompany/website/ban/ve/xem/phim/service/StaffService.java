package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.dao.OrderDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcOrderDAO;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
import com.mycompany.website.ban.ve.xem.phim.util.QrCodeUtil;
import com.mycompany.website.ban.ve.xem.phim.util.ScopeUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Nghiep vu rieng cua quay ve.
 *
 * <p><b>Day la lop DUY NHAT chua luat nghiep vu moi cua tinh nang nay</b> - luat khung gio
 * check-in. Moi thao tac GHI deu uy quyen nguyen ven cho {@link AdminService}, noi da co san
 * transaction {@code UPDLOCK/HOLDLOCK}, kiem tra trang thai va audit log dung.
 * Khong sao chep lai logic ghi o day.</p>
 *
 * <p><b>Vi sao luat khung gio nam o day chu khong phai trong AdminService:</b>
 * {@code AdminService.redeemTicket} hien khong xet thoi gian, va {@code /admin/orders} cua
 * manager van goi thang vao no. Dat luat o lop nay giu nguyen duong di cu, dong thoi cho
 * manager quyen <i>override</i> check-in ngoai khung gio cho khach den muon - co chu y,
 * khong phai lo hong.</p>
 */
public class StaffService {

    /** Mo cong check-in 60 phut truoc gio chieu. */
    public static final int CHECK_IN_OPENS_MINUTES_BEFORE = 60;

    // BUG-13: da xoa CHECK_IN_CLOSES_MINUTES_AFTER = 30.
    //
    // Hang so do duoc khai bao va co javadoc noi "dong cong check-in 30 phut sau gio bat dau",
    // nhung KHONG noi nao dung den. Thuc te cong dong o EndTime (het phim) — xem nhanh 7-8 trong
    // lookupTicket. Tai lieu va hanh vi lech nhau, va giu mot hang so chet la moi de lan sau ai do
    // "sua cho dung tai lieu" ma khong biet minh dang doi luat van hanh quay ve.
    //
    // Chon giu HANH VI, xoa HANG SO: dong o EndTime nhan van hon voi khach den muon — ho van vao
    // xem duoc phan con lai cua phim thay vi bi tu choi o cua sau phut thu 30.

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter CLOCK_DATE = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final OrderDAO orderDAO;
    private final AdminService adminService;

    public StaffService() {
        this(new JdbcOrderDAO(), new AdminService());
    }

    /** Chi dung cho test: thay DAO doc don de kiem tra luat khung gio ma khong can DB. */
    StaffService(OrderDAO orderDAO) {
        this(orderDAO, new AdminService());
    }

    StaffService(OrderDAO orderDAO, AdminService adminService) {
        this.orderDAO = orderDAO;
        this.adminService = adminService;
    }

    /**
     * Ket qua tra cuu ve. {@code order} co the null khi khong tim thay ma.
     *
     * <p><b>Chi mang tinh tu van cho giao dien.</b> Quyet dinh that nam trong transaction
     * {@code UPDLOCK} cua {@link AdminService#redeemTicket}: hai nhan vien quet cung luc thi
     * mot nguoi thang, nguoi con lai nhan 409 "Ve da su dung".</p>
     */
    public static final class TicketLookup {
        private final Verdict verdict;
        private final String message;
        private final OrderRecord order;

        TicketLookup(Verdict verdict, String message, OrderRecord order) {
            this.verdict = verdict;
            this.message = message;
            this.order = order;
        }

        public Verdict getVerdict() {
            return verdict;
        }

        public String getVerdictName() {
            return verdict.name();
        }

        public String getMessage() {
            return message;
        }

        public OrderRecord getOrder() {
            return order;
        }

        public boolean isFound() {
            return order != null;
        }

        /** Chi khi READY moi duoc hien nut Check-in. */
        public boolean isCanCheckIn() {
            return verdict == Verdict.READY;
        }

        /** Chi khi NEEDS_PAYMENT moi duoc hien nut Thu tien. */
        public boolean isCanCollectPayment() {
            return verdict == Verdict.NEEDS_PAYMENT;
        }

        /** Dung chon mau/icon o giao dien: ok | warn | danger. */
        public String getTone() {
            if (verdict == Verdict.READY || verdict == Verdict.CHECKED_IN) {
                return "ok";
            }
            if (verdict == Verdict.NEEDS_PAYMENT || verdict == Verdict.TOO_EARLY) {
                return "warn";
            }
            return "danger";
        }
    }

    public enum Verdict {
        NOT_FOUND,
        CANCELLED,
        ALREADY_USED,
        NOT_CONFIRMED,
        NEEDS_PAYMENT,
        COUNTER_EXPIRED,
        UNPAID,
        SHOWTIME_MISSING,
        TOO_EARLY,
        TOO_LATE,
        READY,
        /**
         * Vua check-in thanh cong o ngay thao tac nay.
         *
         * <p>Tach khoi {@link #ALREADY_USED}: neu tra ve ALREADY_USED sau khi check-in thanh cong
         * thi client kiem tra verdict se tuong la that bai, du HTTP status van 200.</p>
         */
        CHECKED_IN
    }

    /**
     * Tra cuu ve trong <b>pham vi cum rap</b> cua nguoi dang tra (BUG-07, INV-4).
     *
     * <p>Thu tu kiem tra la co chu y: trang thai don truoc, thanh toan sau, thoi gian cuoi.
     * Neu xet thoi gian truoc, mot ve da bi huy se bao "qua gio" thay vi "da huy" — sai thong
     * tin cho nhan vien.</p>
     *
     * <p><b>C.2:</b> ban {@code lookupTicket(String)} da bi xoa. No truyen {@code actor = null},
     * ma {@code ScopeUtil.isOutOfCinemaScope(null, …)} tra {@code false} — tuc khong kiem pham vi
     * gi ca. Hai call site production deu truyen actor nen lo chua bi khai thac, nhung mot API
     * nhu vay chi cho doi den caller dau tien goi nham: khong loi bien dich, khong test nao do.
     * Cach chac chan nhat de no khong bi goi nham la no khong ton tai.</p>
     *
     * <p>Duong ghi ({@code redeemTicket}) von da kiem pham vi, duong doc thi chua — nen staff cua
     * rap nay doc duoc ten rap, ten phong, ten phim cua don o rap khac. Ve ngoai pham vi tra ve
     * <b>y het</b> ma ve khong ton tai: cung verdict, cung thong bao, va khong kem ban ghi don.
     * Mot verdict rieng kieu "ngoai pham vi" van la mot kenh do xem ma nao co that o rap khac.</p>
     */
    public TicketLookup lookupTicket(String ticketCode, User actor) {
        if (ticketCode == null || ticketCode.isBlank()) {
            return new TicketLookup(Verdict.NOT_FOUND, "Vui lòng quét mã QR hoặc nhập mã vé.", null);
        }

        String canonicalCode = canonicalTicketCode(ticketCode);
        if (canonicalCode == null) {
            return new TicketLookup(Verdict.NOT_FOUND,
                    "Ma QR khong hop le hoac da bi thay doi.", null);
        }

        Optional<OrderRecord> found = orderDAO.findByTicketCode(canonicalCode);
        if (found.isEmpty() || ScopeUtil.isOutOfCinemaScope(actor, found.get().getCinemaId())) {
            return new TicketLookup(Verdict.NOT_FOUND,
                    "Không tìm thấy vé với mã \"" + canonicalCode + "\".", null);
        }

        OrderRecord order = found.get();

        // F-004: doc gio nghiep vu (gio DB) mot lan cho ca luot tra cuu nay, roi ghim vao ban ghi.
        // Truoc day ham nay dung LocalDateTime.now() cua JVM trong khi StartTime/EndTime la gio DB;
        // may ung dung lech gio hoac khac timezone se cham diem sai khung gio check-in. Ghim mot moc
        // duy nhat cung dam bao ket luan tra nhan vien va nhan trang thai tren man hinh khong lech.
        LocalDateTime now = BusinessClock.now();
        order.setBusinessNow(now);

        // 1-4: trang thai don.
        if ("cancelled".equalsIgnoreCase(order.getOrderStatus())) {
            return new TicketLookup(Verdict.CANCELLED, "Đơn này đã bị hủy.", order);
        }
        if ("redeemed".equalsIgnoreCase(order.getOrderStatus())) {
            String at = order.getRedeemedAt() == null ? "" : " lúc " + order.getRedeemedAt().format(CLOCK_DATE);
            return new TicketLookup(Verdict.ALREADY_USED, "Vé này đã được check-in" + at + ".", order);
        }
        if (!"confirmed".equalsIgnoreCase(order.getOrderStatus())) {
            return new TicketLookup(Verdict.NOT_CONFIRMED,
                    "Đơn chưa hoàn tất đặt chỗ (trạng thái: " + order.getOrderStatus() + ").", order);
        }

        // 5-6: thanh toan.
        if (!"paid".equalsIgnoreCase(order.getPaymentStatus())) {
            if ("counter".equalsIgnoreCase(order.getPaymentMethod())) {
                LocalDateTime expiresAt = order.getCounterExpiresAt();
                if (expiresAt == null || !now.isBefore(expiresAt)) {
                    return new TicketLookup(Verdict.COUNTER_EXPIRED,
                            "Đơn thanh toán tại quầy đã hết hạn thu tiền. "
                            + "Không được nhận tiền; vui lòng tạo đơn mới.", order);
                }
                return new TicketLookup(Verdict.NEEDS_PAYMENT,
                        "Đơn thanh toán tại quầy — cần thu tiền trước khi cho vào.", order);
            }
            return new TicketLookup(Verdict.UNPAID,
                    "Đơn chưa thanh toán (" + order.getPaymentMethod() + ").", order);
        }

        // 7-8: khung gio.
        LocalDateTime startTime = order.getStartTime();
        if (startTime == null) {
            return new TicketLookup(Verdict.SHOWTIME_MISSING,
                    "Không xác định được suất chiếu của vé này. Vui lòng báo quản lý.", order);
        }

        LocalDateTime opensAt = startTime.minusMinutes(CHECK_IN_OPENS_MINUTES_BEFORE);
        LocalDateTime endTime = order.getEndTime();

        if (now.isBefore(opensAt)) {
            return new TicketLookup(Verdict.TOO_EARLY,
                    "Chưa đến giờ check-in. Vé mở cổng lúc " + opensAt.format(CLOCK)
                            + " (suất " + startTime.format(CLOCK_DATE) + ").", order);
        }
        if (endTime != null && now.isAfter(endTime)) {
            long lateMinutes = Duration.between(endTime, now).toMinutes();
            return new TicketLookup(Verdict.TOO_LATE,
                    "Phim đã kết thúc chiếu cách đây " + lateMinutes + " phút (kết thúc lúc "
                            + endTime.format(CLOCK) + "). Vui lòng hướng dẫn khách gửi yêu cầu xin hoàn tiền.", order);
        }

        if (now.isAfter(startTime)) {
            long lateMinutes = Duration.between(startTime, now).toMinutes();
            return new TicketLookup(Verdict.READY,
                    "Vé trễ giờ " + lateMinutes + " phút (phim đang chiếu) — Vẫn cho phép check-in cho khách vào phòng " + order.getRoomName() + ".", order);
        }

        return new TicketLookup(Verdict.READY,
                "Vé hợp lệ — mời khách vào phòng " + order.getRoomName() + ".", order);
    }

    /**
     * Check-in ve sau khi da qua kiem tra khung gio.
     *
     * <p>Kiem tra khung gio o day; toan bo kiem tra con lai (da thanh toan, chua dung,
     * chong quet trung) do {@link AdminService#redeemTicket} lo trong transaction cua no.</p>
     */
    public TicketLookup checkIn(String ticketCode, User actor) {
        TicketLookup lookup = lookupTicket(ticketCode, actor);
        if (!lookup.isCanCheckIn()) {
            throw new BookingException(statusFor(lookup.getVerdict()), lookup.getMessage());
        }
        String canonicalCode = lookup.getOrder().getTicketCode();
        adminService.redeemTicket(canonicalCode, actor);

        // Doc lai de lay RedeemedAt vua ghi, nhung tra verdict CHECKED_IN thay vi
        // ALREADY_USED - neu khong, thao tac THANH CONG lai bao trang thai nghe nhu that bai.
        OrderRecord refreshed = orderDAO.findByTicketCode(canonicalCode).orElse(lookup.getOrder());
        return new TicketLookup(Verdict.CHECKED_IN,
                "Đã check-in thành công. Mời khách vào phòng " + refreshed.getRoomName() + ".",
                refreshed);
    }

    /**
     * Thu tien mat cho don "thanh toan tai quay".
     *
     * <p><b>Khong</b> rang buoc khung gio: khach duoc phep tra tien truoc, roi quay lai
     * check-in dung gio.</p>
     */
    public void collectPayment(int orderId, User actor) {
        adminService.markCounterOrderPaid(orderId, actor);
    }

    /**
     * Chuan hoa du lieu tu hai duong vao cua quay ve.
     *
     * <p>QR san pham co dang {@code ticket.signature} va phai xac minh chu ky o server.
     * Ma khong co dau cham la duong nhap tay; giu tuong thich ca ma moi va ma legacy.
     * Payload co chu ky sai phai fail closed truoc khi cham vao DAO.</p>
     */
    private String canonicalTicketCode(String input) {
        String clean = input == null ? "" : input.trim();
        if (clean.isEmpty()) {
            return null;
        }
        return clean.indexOf('.') < 0 ? clean : QrCodeUtil.verifiedTicketCode(clean);
    }

    private int statusFor(Verdict verdict) {
        return switch (verdict) {
            case NOT_FOUND -> 404;
            case ALREADY_USED, COUNTER_EXPIRED -> 409;
            case SHOWTIME_MISSING -> 500;
            default -> 400;
        };
    }
}

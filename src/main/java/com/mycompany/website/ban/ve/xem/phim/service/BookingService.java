package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.config.SettingsReader;
import com.mycompany.website.ban.ve.xem.phim.dao.ComboFoodDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.OrderDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.PromotionDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.PromotionUsageDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.ShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.ShowtimeSeatDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.UserDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcComboFoodDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcOrderDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcPromotionDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcPromotionUsageDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcShowtimeSeatDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserDAO;
import com.mycompany.website.ban.ve.xem.phim.model.ComboFood;
import com.mycompany.website.ban.ve.xem.phim.model.OrderComboItem;
import com.mycompany.website.ban.ve.xem.phim.model.OrderHoldStatus;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.OrderSeatItem;
import com.mycompany.website.ban.ve.xem.phim.model.PaymentMethod;
import com.mycompany.website.ban.ve.xem.phim.model.Promotion;
import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.payment.PaymentGateway;
import com.mycompany.website.ban.ve.xem.phim.service.payment.PaymentResult;
import com.mycompany.website.ban.ve.xem.phim.service.payment.SimulatedGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BookingService {
    private static final Logger LOGGER = Logger.getLogger(BookingService.class.getName());
    private static final SecureRandom TICKET_RANDOM = new SecureRandom();
    private static final char[] TICKET_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    /**
     * Thong bao dung chung cho moi truong hop khoa idempotency lech chu don hoac lech don.
     *
     * <p>Trung tinh co chu y: noi ro don do la cua ai, hay phan biet "khong phai cua ban" voi
     * "khong phai don nay", deu bien 409 thanh mot kenh do thong tin.</p>
     */
    private static final String IDEMPOTENCY_KEY_CONFLICT =
            "Khoá idempotency này đã được dùng cho một đơn khác. Vui lòng thử lại với khoá mới.";
    /**
     * Han giu ghe <b>mac dinh</b>, dung khi {@code SystemSettings.seat_hold_minutes} thieu hoac
     * chua gia tri khong dung duoc.
     *
     * <p>Truoc day day la nguong that su duy nhat: o cau hinh {@code seat_hold_minutes} hien tren
     * man hinh quan tri nhung khong duoc doc o bat ky dau, nen doi no khong lam gi ca (BUG-01,
     * vi pham INV-6). Nay no chi con la gia tri lui.</p>
     */
    public static final int HOLD_MINUTES = 10;

    static final String SETTING_HOLD_MINUTES = "seat_hold_minutes";

    /**
     * Khoang hop le cua han giu ghe.
     *
     * <p>Khong kep thi mot o nhap sai (vi du go thua so 0) khoa ghe hang chuc gio: ghe khong ban
     * duoc ma cung khong ai thu hoi duoc trong ca ngay chieu.</p>
     */
    static final int MIN_HOLD_MINUTES = 1;
    static final int MAX_HOLD_MINUTES = 60;

    /** Tran so don nhap con mo cua mot nguoi tren mot suat chieu (BUG-04). */
    static final String SETTING_MAX_OPEN_DRAFTS = "booking.maxOpenDraftsPerShowtime";
    static final int DEFAULT_MAX_OPEN_DRAFTS = 3;

    /** Duoi muc nay thi khong ai dat duoc ve nao. */
    static final int MIN_MAX_OPEN_DRAFTS = 1;

    /**
     * Tren muc nay thi tran chong lam dung (BUG-04b) coi nhu khong con: mot tai khoan lai lap
     * {@code POST /orders} du lan de khoa sach ghe cua ca suat chieu.
     */
    static final int MAX_MAX_OPEN_DRAFTS = 20;

    /** Ket qua authority duy nhat cho moi be mat bat dau luong dat ve. */
    public record BookingEligibility(int showtimeId, boolean eligible, String code, String message) {
    }

    private record BookingCheck(Showtime showtime, BookingEligibility eligibility) {
    }

    /** Minimal order snapshot held under the cancellation transaction's update lock. */
    private record CancellationOrder(
            int id, int userId, String orderStatus, String paymentStatus) {
    }

    private final ShowtimeDAO showtimeDAO = new JdbcShowtimeDAO();
    private final ShowtimeSeatDAO showtimeSeatDAO = new JdbcShowtimeSeatDAO();
    private final ComboFoodDAO comboFoodDAO = new JdbcComboFoodDAO();
    private final PromotionDAO promotionDAO = new JdbcPromotionDAO();
    private final PromotionUsageDAO promotionUsageDAO = new JdbcPromotionUsageDAO();
    private final OrderDAO orderDAO = new JdbcOrderDAO();
    private final UserDAO userDAO = new JdbcUserDAO();
    private final LoyaltyService loyaltyService = new LoyaltyService();

    /**
     * Han giu ghe dang co hieu luc, cho cac duong <b>chi hien thi</b> (khong giu khoa nao).
     *
     * <p>Di qua {@link SettingsReader} nen co cache 60 giay va tu quay ve {@link #HOLD_MINUTES}
     * khi gia tri nam ngoai {@code [MIN_HOLD_MINUTES, MAX_HOLD_MINUTES]} hoac khong doc duoc.
     * <b>Khong</b> goi ham nay tu trong mot transaction dang giu khoa — no mo connection rieng;
     * ban nhan {@code Connection} o duoi moi la ban dung cho luong do.</p>
     */
    public static int holdMinutes() {
        return SettingsReader.readInt(SETTING_HOLD_MINUTES, HOLD_MINUTES, MIN_HOLD_MINUTES, MAX_HOLD_MINUTES);
    }

    /**
     * Han giu ghe doc bang chinh {@code Connection} cua transaction dang chay.
     *
     * <p>Duong dat ve nam trong transaction giu {@code UPDLOCK} tren ShowtimeSeats; mo them mot
     * connection o day la vi pham quy uoc "khong mo connection moi khi dang giu khoa" va la cach
     * tu tao deadlock voi pool 10-20 ket noi.</p>
     */
    private int holdMinutes(Connection connection) {
        int configured = settingInt(connection, SETTING_HOLD_MINUTES, HOLD_MINUTES);
        if (configured < MIN_HOLD_MINUTES || configured > MAX_HOLD_MINUTES) {
            LOGGER.warning("seat_hold_minutes = " + configured + " nam ngoai khoang ["
                    + MIN_HOLD_MINUTES + ", " + MAX_HOLD_MINUTES + "], dung mac dinh " + HOLD_MINUTES + ".");
            return HOLD_MINUTES;
        }
        return configured;
    }

    public Optional<Showtime> findShowtime(int showtimeId) {
        return showtimeDAO.findById(showtimeId);
    }

    /** Doc showtime va danh gia toan bo dieu kien mo ban tren cung mot connection DB. */
    public BookingEligibility bookingEligibility(int showtimeId) {
        return inspectBookingEligibility(showtimeId).eligibility();
    }

    /** Showtime hop le de render JSP, hoac nem loi nghiep vu giong luong tao/thanh toan don. */
    public Showtime requireBookableShowtime(int showtimeId) {
        BookingCheck check = inspectBookingEligibility(showtimeId);
        if (!check.eligibility().eligible()) {
            throw new BookingException(400, check.eligibility().message());
        }
        return check.showtime();
    }

    private BookingCheck inspectBookingEligibility(int showtimeId) {
        try (Connection connection = DBConnection.getConnection()) {
            Showtime showtime = showtimeDAO.findById(connection, showtimeId)
                    .orElseThrow(() -> new BookingException(404, "Khong tim thay suat chieu."));
            return new BookingCheck(showtime, evaluateBookingEligibility(connection, showtime));
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the kiem tra kha nang dat ve cua suat chieu.", ex);
        }
    }

    public List<ComboFood> findActiveCombos() {
        return List.of();
    }

    public List<ComboFood> findActiveCombos(Integer cinemaId) {
        return comboFoodDAO.findActiveForCinema(cinemaId);
    }

    /**
     * Combo ban duoc tai cum rap cua mot suat chieu (CB-01).
     *
     * <p>Sau khi combo co the thuoc rieng mot rap, trang dat ve khong duoc hien ca thuc don cua
     * rap khac. Loc theo rap cua chinh suat chieu dang dat.</p>
     */
    public List<ComboFood> findActiveCombosForShowtime(int showtimeId) {
        if (showtimeId <= 0) {
            return List.of();
        }
        var showtimeOpt = showtimeDAO.findById(showtimeId);
        if (showtimeOpt.isEmpty()) {
            return List.of();
        }
        return comboFoodDAO.findActiveForCinema(showtimeOpt.get().getCinemaId());
    }

    /**
     * So do ghe cua mot suat chieu.
     *
     * <p>B7: truoc day ham nay muon <b>hai</b> connection tu pool cho moi lan tai trang
     * (mot cho {@code releaseExpiredHolds}, mot cho {@code findSeatMap}). Pool chi co 10-20
     * connection nen day la nguon nghen that. Nay ca hai buoc dung chung dung mot connection.</p>
     */
    public List<ShowtimeSeat> getSeatMap(int showtimeId) {
        try (Connection connection = DBConnection.getConnection()) {
            showtimeSeatDAO.releaseExpiredHolds(connection, showtimeId);
            return showtimeSeatDAO.findSeatMap(connection, showtimeId);
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai so do ghe luc nay.", ex);
        }
    }

    public String seatMapVersion(int showtimeId) {
        String sql = """
                SELECT CONCAT(COUNT_BIG(*), ':',
                    COALESCE(CHECKSUM_AGG(BINARY_CHECKSUM(Id, Status, HeldByUserId, HeldUntil)), 0))
                FROM ShowtimeSeats WHERE ShowtimeId=?
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
            throw new BookingException(500, "Không thể tạo phiên bản sơ đồ ghế.");
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể đọc phiên bản sơ đồ ghế.", ex);
        }
    }

    /**
     * Trang thai giu ghe that cua don, dung cho dong ho dem nguoc tren UI (B3).
     * Nem 404 khi don khong ton tai <b>hoac</b> khong thuoc ve nguoi dung dang hoi.
     */
    public OrderHoldStatus getHoldStatus(int orderId, int userId) {
        return orderDAO.findHoldStatus(orderId, userId)
                .orElseThrow(() -> new BookingException(404, "Khong tim thay don hang."));
    }

    public OrderRecord createDraftOrder(int userId, int showtimeId, List<Integer> showtimeSeatIds,
                                        Map<Integer, Integer> comboSelections, String promotionCode, String paymentMethod) {
        return createDraftOrder(userId, showtimeId, showtimeSeatIds, comboSelections, promotionCode, paymentMethod, null);
    }

    public OrderRecord createDraftOrder(int userId, int showtimeId, List<Integer> showtimeSeatIds,
                                        Map<Integer, Integer> comboSelections, String promotionCode, String paymentMethod, String idempotencyKey) {
        String createKey = normalizeIdempotencyKey(idempotencyKey);
        if (createKey != null) {
            // A.2 (BUG-02, INV-2): khoa phai gan voi CHU DON. Ban cu tra thang don tim duoc,
            // nen mot khoa doan/lay duoc cua nguoi khac lam POST /orders tra ve orderId va
            // ticketCode that cua ho. Lech chu don -> 409 trung tinh.
            OrderRecord existing = ownedReplay(
                    orderDAO.findByIdempotencyKey(createKey).orElse(null), userId);
            if (existing != null) {
                existing.setReplayed(true);
                return existing;
            }
        }
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            if (PaymentMethod.fromCode(paymentMethod).isEmpty()) {
                throw new BookingException(400, "Phương thức thanh toán không hợp lệ.");
            }
        }

        if (showtimeSeatIds == null || showtimeSeatIds.isEmpty()) {
            throw new BookingException(400, "Vui long chon it nhat mot ghe.");
        }

        Optional<Showtime> optShowtime = showtimeDAO.findById(showtimeId);
        if (optShowtime.isEmpty()) {
            throw new BookingException(404, "Khong tim thay suat chieu.");
        }
        Showtime showtime = optShowtime.get();

        // BUG-05: kiem tra trang thai phong da chuyen vao ensureShowtimeBookable de duong dat ve va
        // duong thanh toan dung CHUNG mot chot. Khoi cu o day con mo mot Connection RIENG — vi pham
        // quy uoc "khong mo connection moi khi dang giu khoa"; chuyen vao la xoa luon vi pham do.

        String userTier = userDAO.findById(userId).map(User::getMembershipTier).orElse("BRONZE");

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Cùng application-lock với luồng suspend/delete: hoặc draft được tạo xong
                // trước khi ngưng bán, hoặc request ngưng bán thắng và draft mới bị chặn.
                ShowtimeLifecycleLock.acquire(connection, showtimeId);
                BookingCommandStore.Claim commandClaim = BookingCommandStore.begin(connection,
                        "user:" + userId,
                        com.mycompany.website.ban.ve.xem.phim.model.BookingCommandType.CREATE_DRAFT,
                        createKey,
                        BookingCommandStore.sha256(userId + "|" + showtimeId + "|"
                                + showtimeSeatIds + "|" + comboSelections + "|" + promotionCode + "|" + paymentMethod));
                if (commandClaim != null && commandClaim.replay()) {
                    OrderRecord replay = orderDAO.findByIdAndUserId(connection, commandClaim.orderId(), userId)
                            .orElseThrow(() -> new BookingException(409, "STATE_CONFLICT"));
                    hydrateSettledOrder(connection, replay);
                    replay.setReplayed(true);
                    connection.commit();
                    return replay;
                }
                ensureShowtimeBookable(connection, showtime);
                // B.1: Orders TRUOC, ShowtimeSeats SAU — cung chieu voi payOrder
                // (findPendingOrderForUpdate → lockSeats). Truoc day cho nay khoa ghe truoc roi
                // moi lay UPDLOCK tren Orders, tuc hai duong di nguoc chieu nhau: dung dinh nghia
                // cua mot vong deadlock.
                ensureDraftQuotaAvailable(connection, userId, showtimeId);
                showtimeSeatDAO.releaseExpiredHolds(connection, showtimeId);
                List<ShowtimeSeat> lockedSeats = showtimeSeatDAO.lockSeats(connection, showtimeId, showtimeSeatIds);
                if (lockedSeats.size() != showtimeSeatIds.size()) {
                    throw new BookingException(409, "Mot so ghe khong ton tai hoac da thay doi.");
                }
                for (ShowtimeSeat seat : lockedSeats) {
                    if ("maintenance".equalsIgnoreCase(seat.getStatus()) || "maintenance".equalsIgnoreCase(seat.getSeatType())) {
                        throw new BookingException(400, "Ghế " + seat.getSeatKey() + " đang bảo trì, không thể đặt vé.");
                    }
                    if (createKey != null && "held".equalsIgnoreCase(seat.getStatus())
                            && seat.getClaimedByOrderId() != null) {
                        throw new BookingException(409,
                                "Ghế " + seat.getSeatKey() + " đã thuộc một đơn giữ chỗ khác.");
                    }
                    if (!seat.isAvailableFor(userId)) {
                        throw new BookingException(409, "Ghe " + seat.getSeatKey() + " da duoc giu hoac dat boi nguoi khac.");
                    }
                }

                // Kiểm tra ghế đôi: Ghế đôi bắt buộc phải đặt cả cặp (không tách rời)
                List<ShowtimeSeat> fullMap = showtimeSeatDAO.findSeatMap(connection, showtimeId);
                for (ShowtimeSeat seat : lockedSeats) {
                    if ("couple".equalsIgnoreCase(seat.getSeatType())) {
                        int partnerNum = (seat.getSeatNumber() % 2 == 1) ? seat.getSeatNumber() + 1 : seat.getSeatNumber() - 1;
                        ShowtimeSeat partner = fullMap.stream()
                                .filter(s -> s.getRowLabel().equalsIgnoreCase(seat.getRowLabel()) && s.getSeatNumber() == partnerNum && "couple".equalsIgnoreCase(s.getSeatType()))
                                .findFirst().orElse(null);
                        if (partner != null && !showtimeSeatIds.contains(partner.getId())) {
                            throw new BookingException(400, "Ghế đôi (" + seat.getSeatKey() + ") là đơn vị đặt chỗ không thể tách rời. Vui lòng chọn cả cặp (" + seat.getSeatKey() + " & " + partner.getSeatKey() + ").");
                        }
                    }
                }

                Map<Integer, ComboFood> comboMap = loadComboMapForShowtime(connection, comboSelections, showtime.getCinemaId());
                List<OrderComboItem> combos = buildComboItems(comboSelections == null ? Map.of() : comboSelections, comboMap);
                List<OrderSeatItem> seats = buildSeatItems(lockedSeats, showtime.getBasePrice());
                BigDecimal seatSubtotal = sumSeatSubtotal(seats);
                BigDecimal comboSubtotal = sumComboSubtotal(combos);

                Promotion promotion = resolvePromotion(connection, promotionCode, userId, userTier);
                BigDecimal discountAmount = calculateDiscount(promotion, seatSubtotal.add(comboSubtotal));
                BigDecimal totalAmount = seatSubtotal.add(comboSubtotal).subtract(discountAmount).max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);

                OrderRecord order = new OrderRecord();
                order.setUserId(userId);
                order.setShowtimeId(showtimeId);
                order.setPromotionId(promotion == null ? null : promotion.getId());
                order.setSeatSubtotal(seatSubtotal);
                order.setComboSubtotal(comboSubtotal);
                order.setDiscountAmount(discountAmount);
                order.setTotalAmount(totalAmount);
                order.setTicketCode(generatePendingTicketCode());
                order.setTicketQrUrl(null);
                String cleanMethod = (paymentMethod == null || paymentMethod.isBlank()) ? "counter" : paymentMethod.trim().toLowerCase();
                order.setPaymentMethod(cleanMethod);
                order.setPaymentStatus("pending");
                order.setOrderStatus("created");
                order.setIdempotencyKey(createKey);

                int orderId = orderDAO.insertDraftOrder(connection, order);
                // The generated identity belongs to this in-memory result immediately. Keep it
                // populated throughout the remaining transaction so no successful hold can be
                // returned to the caller as order #0 under a high-contention interleaving.
                order.setId(orderId);
                orderDAO.insertOrderSeats(connection, orderId, seats);
                orderDAO.insertOrderCombos(connection, orderId, combos);
                showtimeSeatDAO.markHeld(connection, showtimeSeatIds, userId, orderId,
                        holdMinutes(connection));
                BookingTransitionService.record(connection, commandClaim, orderId,
                        com.mycompany.website.ban.ve.xem.phim.model.BookingCommandType.CREATE_DRAFT,
                        null, null, null, "created", "pending", order.getPaymentMethod(),
                        "user:" + userId, null);
                BookingCommandStore.complete(connection, commandClaim, orderId, 200,
                        "{\"orderId\":" + orderId + "}");

                // Read the rowversion on the same transaction/connection. Opening a second
                // pooled connection after COMMIT can fail under load and falsely report the
                // winning request as failed even though its hold was already persisted.
                OrderRecord result = orderDAO.findByIdAndUserId(connection, orderId, userId)
                        .orElse(order);
                connection.commit();
                result.setId(orderId);
                result.setFilmTitle(showtime.getFilmTitle());
                result.setCinemaName(showtime.getCinemaName());
                result.setRoomName(showtime.getRoomName());
                result.setStartTime(showtime.getStartTime());
                result.getSeats().addAll(seats);
                result.getCombos().addAll(combos);
                return result;
            } catch (RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tao don dat ve luc nay.", ex);
        }
    }

    public OrderRecord payOrder(int userId, int orderId, Map<Integer, Integer> comboSelections, String promotionCode, String paymentMethod) {
        return payOrder(userId, orderId, comboSelections, promotionCode, paymentMethod, null);
    }

    public OrderRecord payOrder(int userId, int orderId, Map<Integer, Integer> comboSelections, String promotionCode, String paymentMethod, String idempotencyKey) {
        String paymentKey = normalizeIdempotencyKey(idempotencyKey);
        if (paymentKey != null) {
            try (Connection connection = DBConnection.getConnection()) {
                OrderRecord replay = settledReplay(
                        orderDAO.findByIdempotencyKey(connection, paymentKey).orElse(null), userId, orderId);
                if (replay != null) {
                    replay.setReplayed(true);
                    return hydrateSettledOrder(connection, replay);
                }
            } catch (SQLException ex) {
                throw new BookingException(500, "Không thể kiểm tra khoá idempotency.", ex);
            }
        }

        if (paymentMethod != null && !paymentMethod.isBlank()) {
            if (PaymentMethod.fromCode(paymentMethod).isEmpty()) {
                throw new BookingException(400, "Phương thức thanh toán không hợp lệ.");
            }
        }

        AdminService adminService = new AdminService();
        String paymentMode = adminService.getSettingValue("payment.mode");
        if (paymentMode == null || paymentMode.isBlank()) {
            paymentMode = "simulated";
        }

        String userTier = userDAO.findById(userId).map(User::getMembershipTier).orElse("BRONZE");
        boolean soldOut = false;

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Integer lockedShowtimeId = findOrderShowtimeId(connection, orderId, userId);
                if (lockedShowtimeId == null) {
                    throw new BookingException(404, "Khong tim thay don cho thanh toan.");
                }
                // Khóa showtime trước Orders để giữ cùng thứ tự khóa với create/suspend/delete.
                ShowtimeLifecycleLock.acquire(connection, lockedShowtimeId);
                com.mycompany.website.ban.ve.xem.phim.model.BookingCommandType paymentCommand =
                        "counter".equalsIgnoreCase(paymentMethod)
                        ? com.mycompany.website.ban.ve.xem.phim.model.BookingCommandType.PLACE_COUNTER
                        : com.mycompany.website.ban.ve.xem.phim.model.BookingCommandType.PAY_CARD;
                BookingCommandStore.Claim commandClaim = BookingCommandStore.begin(connection,
                        "user:" + userId, paymentCommand, paymentKey,
                        BookingCommandStore.sha256(userId + "|" + orderId + "|" + comboSelections
                                + "|" + promotionCode + "|" + paymentMethod));
                Optional<OrderRecord> optOrder = orderDAO.findPendingOrderForUpdate(connection, orderId, userId);
                if (optOrder.isEmpty()) {
                    // BUG-02 (INV-2) — chong dua. Hai request cung khoa gui gan nhu cung luc deu
                    // truot lan kiem NGOAI transaction; ban thu hai vao toi day khi ban thu nhat
                    // da chot don, nen don khong con 'pending'. Nem 404 ngay tai day chinh la trieu
                    // chung do duoc: tien da tru, ve da phat, ma client van nhan "khong tim thay don".
                    OrderRecord replay = settledReplayInTransaction(connection, paymentKey, userId, orderId);
                    if (replay != null) {
                        hydrateSettledOrder(connection, replay);
                        replay.setReplayed(true);
                        connection.commit();
                        return replay;
                    }
                    throw new BookingException(404, "Khong tim thay don cho thanh toan.");
                }
                OrderRecord order = optOrder.get();
                if (commandClaim != null && commandClaim.replay()) {
                    OrderRecord replay = orderDAO.findByIdAndUserId(connection, commandClaim.orderId(), userId)
                            .orElseThrow(() -> new BookingException(409, "STATE_CONFLICT"));
                    hydrateSettledOrder(connection, replay);
                    replay.setReplayed(true);
                    connection.commit();
                    return replay;
                }
                String beforeOrderStatus = order.getOrderStatus();
                String beforePaymentStatus = order.getPaymentStatus();

                // B.3: chup trang thai truoc khi doi. Phai chup NGAY DAY — vai dong nua `order`
                // se bi ghi de bang gia tri sau thanh toan va khong con doc lai duoc gia tri cu.
                String orderBeforeJson = javax.json.Json.createObjectBuilder()
                        .add("paymentStatus", String.valueOf(order.getPaymentStatus()))
                        .add("orderStatus", String.valueOf(order.getOrderStatus()))
                        .add("paymentMethod", String.valueOf(order.getPaymentMethod()))
                        .add("totalAmount", order.getTotalAmount() == null
                                ? "null" : order.getTotalAmount().toPlainString())
                        .build().toString();

                if (paymentMethod != null && !paymentMethod.isBlank()) {
                    order.setPaymentMethod(paymentMethod.trim().toLowerCase());
                }

                Optional<Showtime> optShowtime = showtimeDAO.findById(connection, order.getShowtimeId());
                if (optShowtime.isEmpty()) {
                    throw new BookingException(404, "Khong tim thay suat chieu.");
                }
                Showtime showtime = optShowtime.get();
                ensureShowtimeBookable(connection, showtime, true, order.getCreatedAt());

                List<OrderSeatItem> seats = orderDAO.findSeatsByOrderId(connection, orderId);
                List<Integer> showtimeSeatIds = seats.stream().map(OrderSeatItem::getShowtimeSeatId).toList();
                List<ShowtimeSeat> lockedSeats = showtimeSeatDAO.lockSeats(connection, order.getShowtimeId(), showtimeSeatIds);
                if (lockedSeats.size() != showtimeSeatIds.size()) {
                    throw new BookingException(409, "Khong the xac minh trang thai ghe.");
                }
                for (ShowtimeSeat seat : lockedSeats) {
                    boolean stillHeldByCurrentUser = "held".equalsIgnoreCase(seat.getStatus())
                            && seat.getHeldByUserId() != null
                            && seat.getHeldByUserId() == userId
                            && Integer.valueOf(orderId).equals(seat.getClaimedByOrderId());
                    if (!stillHeldByCurrentUser) {
                        throw new BookingException(409, "Giu ghe da het han hoac bi thay doi. Vui long dat lai.");
                    }
                }
                // CB-ISS-005: kiem tra han giu bang GETDATE() ngay trong SQL. Vong lap tren chi thay
                // Status='held' va HeldByUserId, nen mot hold da qua han ma sweeper chua kip thu ve
                // van lot qua — thanh cong hay khong phu thuoc vao viec chay dua voi sweeper, trong
                // khi giao dien da khoa nut thanh toan tu luc het han.
                if (showtimeSeatDAO.countActiveHoldsByUser(
                        connection, order.getShowtimeId(), showtimeSeatIds, userId) != showtimeSeatIds.size()) {
                    throw new BookingException(409, "Giu ghe da het han hoac bi thay doi. Vui long dat lai.");
                }

                if (comboSelections != null) {
                    orderDAO.deleteOrderCombos(connection, orderId);
                    Map<Integer, ComboFood> comboMap = loadComboMapForShowtime(connection, comboSelections, showtime.getCinemaId());
                    List<OrderComboItem> combos = buildComboItems(comboSelections, comboMap);
                    orderDAO.insertOrderCombos(connection, orderId, combos);
                    order.setComboSubtotal(sumComboSubtotal(combos));
                    order.getCombos().addAll(combos);
                } else {
                    order.getCombos().addAll(orderDAO.findCombosByOrderId(connection, orderId));
                }

                Promotion promotion = null;
                if (promotionCode != null && !promotionCode.isBlank()) {
                    promotion = resolvePromotion(connection, promotionCode, userId, userTier);
                    order.setPromotionId(promotion == null ? null : promotion.getId());
                } else {
                    order.setPromotionId(null);
                }

                BigDecimal seatSubtotal = sumSeatSubtotal(seats);
                BigDecimal comboSubtotal = order.getComboSubtotal();
                BigDecimal discountAmount = calculateDiscount(promotion, seatSubtotal.add(comboSubtotal));
                BigDecimal totalAmount = seatSubtotal.add(comboSubtotal).subtract(discountAmount).max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);

                order.setSeatSubtotal(seatSubtotal);
                order.setDiscountAmount(discountAmount);
                order.setTotalAmount(totalAmount);

                orderDAO.updateOrderFinancials(connection, orderId, order.getSeatSubtotal(), order.getComboSubtotal(), order.getDiscountAmount(), order.getTotalAmount(), order.getPromotionId(), order.getPaymentMethod());

                String ticketCode = generateFinalTicketCode();
                String qrUrl = "/tickets/qr/" + ticketCode;

                if ("counter".equalsIgnoreCase(order.getPaymentMethod())) {
                    // Doc bang connection dang mo, khong mo connection moi trong luc giu UPDLOCK.
                    int counterExpiryMinutes = settingInt(connection, "counter.expiryMinutes", 30);
                    orderDAO.confirmCounterOrder(connection, orderId, ticketCode, qrUrl, counterExpiryMinutes);
                    order.setPaymentStatus("pending");
                    order.setPaymentProvider("counter");
                } else {
                    if ("live".equalsIgnoreCase(paymentMode)) {
                        throw new BookingException(503, "Cổng thanh toán trực tiếp chưa sẵn sàng. Vui lòng chọn thanh toán tại quầy hoặc thử lại sau.");
                    }
                    PaymentGateway gateway = new SimulatedGateway();
                    PaymentResult paymentResult = gateway.createPayment(order);
                    if (!paymentResult.isSuccess()) {
                        throw new BookingException(400, paymentResult.getMessage());
                    }
                    orderDAO.markPaid(connection, orderId, ticketCode, qrUrl, paymentResult.getTransactionId(), paymentResult.getProvider());
                    order.setTransactionId(paymentResult.getTransactionId());
                    order.setPaymentProvider(paymentResult.getProvider());
                    order.setPaymentStatus("paid");
                }

                // BUG-02 (INV-2): ghi khoa xuong DB TRONG chinh transaction thanh toan. Truoc day
                // khoa chi duoc gan vao object roi vut di — khong cau UPDATE nao mang no xuong
                // Orders.IdempotencyKey, nen lan pay thu hai cung khoa tra 404.
                if (paymentKey != null && orderDAO.applyIdempotencyKey(connection, orderId, paymentKey)) {
                    order.setIdempotencyKey(paymentKey);
                }

                showtimeSeatDAO.markBooked(connection, showtimeSeatIds, orderId);
                order.setOrderStatus("confirmed");
                order.setTicketCode(ticketCode);
                if (order.getPromotionId() != null) {
                    promotionDAO.incrementUsedCount(connection, order.getPromotionId());
                    if (promotion.getUserVoucherId() != null) {
                        promotionUsageDAO.consumeVoucher(
                                connection, promotion.getUserVoucherId(), userId, orderId);
                    } else if (promotion.getPerUserLimit() > 0) {
                        promotionUsageDAO.record(connection, promotion.getId(), userId, orderId);
                    }
                }

                // Chi doc trang thai trong transaction. Audit/thong bao mo ket noi rieng va chi
                // duoc chay sau commit de khong deadlock voi cac khoa thanh toan dang giu.
                try (PreparedStatement psCheck = connection.prepareStatement("SELECT COUNT(*) FROM ShowtimeSeats WHERE ShowtimeId = ? AND Status = 'available'")) {
                    psCheck.setInt(1, order.getShowtimeId());
                    try (ResultSet rsCheck = psCheck.executeQuery()) {
                        soldOut = rsCheck.next() && rsCheck.getInt(1) == 0;
                    }
                }

                if ("paid".equalsIgnoreCase(order.getPaymentStatus())) {
                    // orderId lam khoa idempotency: retry thanh toan khong duoc cong diem hai lan.
                    loyaltyService.addPointsOnPayment(connection, userId, order.getTotalAmount(), orderId);
                }

                BookingTransitionService.record(connection, commandClaim, orderId,
                        paymentCommand, beforeOrderStatus, beforePaymentStatus, order.getPaymentMethod(),
                        order.getOrderStatus(), order.getPaymentStatus(), order.getPaymentMethod(),
                        "user:" + userId, null);
                BookingCommandStore.complete(connection, commandClaim, orderId, 200,
                        "{\"orderId\":" + orderId + ",\"ticketCode\":\"" + ticketCode + "\"}");

                connection.commit();
                orderDAO.findById(orderId).ifPresent(persisted -> order.setStateVersion(persisted.getStateVersion()));

                // BUG-09 (INV-9): thanh toan la thao tac dung tien nhung truoc day khong de lai
                // dong audit nao — khi co tranh chap thi khong doi soat duoc.
                auditAfterCommit(userId, "PAY_ORDER", orderId, orderBeforeJson,
                        javax.json.Json.createObjectBuilder()
                                .add("paymentMethod", String.valueOf(order.getPaymentMethod()))
                                .add("paymentStatus", String.valueOf(order.getPaymentStatus()))
                                .add("totalAmount", order.getTotalAmount().toPlainString())
                                .add("ticketCode", String.valueOf(ticketCode))
                                .build().toString());
                if (soldOut) {
                    notifySoldOutAfterCommit(adminService, order, showtime);
                }

                if ("paid".equalsIgnoreCase(order.getPaymentStatus())) {
                    try {
                        new InvoiceService().issueSale(orderId);
                    } catch (RuntimeException ex) {
                        LOGGER.log(Level.SEVERE, "Khong the sinh hoa don orderId=" + orderId, ex);
                    }
                }

                order.setTicketCode(ticketCode);
                order.setTicketQrUrl(qrUrl);
                order.setOrderStatus("confirmed");
                order.getSeats().addAll(seats);

                order.setFilmTitle(showtime.getFilmTitle());
                order.setCinemaName(showtime.getCinemaName());
                order.setRoomName(showtime.getRoomName());
                order.setStartTime(showtime.getStartTime());

                if ("paid".equalsIgnoreCase(order.getPaymentStatus())) {
                    userDAO.findById(userId).ifPresent(user -> {
                        try {
                            new EmailService().sendTicket(user.getEmail(), user.getFullName(), orderId,
                                    ticketCode, showtime.getFilmTitle(), showtime.getCinemaName(),
                                    showtime.getStartTimeDisplay());
                        } catch (RuntimeException ex) {
                            LOGGER.log(Level.SEVERE, "Khong the xep lich gui ve orderId=" + orderId, ex);
                        }
                    });
                }
                return order;
            } catch (RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the xac nhan thanh toan luc nay.", ex);
        }
    }

    private void notifySoldOutAfterCommit(AdminService adminService, OrderRecord order, Showtime showtime) {
        String detail = "CẢNH BÁO CHÁY VÉ: Suất chiếu " + showtime.getFilmTitle() + " lúc "
                + showtime.getStartTimeDisplay() + " tại " + showtime.getCinemaName() + " ("
                + showtime.getRoomName() + ") đã ĐẶT HẾT VÉ!";
        try {
            adminService.logAction(null, "SHOWTIME_SOLD_OUT", "Showtime",
                    String.valueOf(order.getShowtimeId()), detail);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Khong ghi duoc audit suat chieu het ghe", ex);
        }
        try {
            adminService.notifyShowtimeSoldOut(order.getShowtimeId(), showtime.getFilmTitle(),
                    showtime.getCinemaName(), showtime.getRoomName(), showtime.getStartTimeDisplay());
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Khong tao duoc thong bao suat chieu het ghe", ex);
        }
    }

    /**
     * Tran so don nhap dang mo cua mot nguoi tren mot suat chieu (BUG-04b, INV-1).
     *
     * <p>Khong co tran thi mot tai khoan lap {@code POST /orders} du lan la khoa sach ghe cua ca
     * suat chieu: do thuc te 30/30 don {@code created} tren cung mot ghe. Nguong doc tu
     * {@code SystemSettings} chu khong hard-code — day dung la lop loi INV-6 vua sua o BUG-01.</p>
     *
     * <p>Dem chay bang chinh {@code Connection} cua transaction, <b>truoc</b> khi khoa ghe (B.1):
     * {@code payOrder} lay khoa tren {@code Orders} roi moi den {@code ShowtimeSeats}, nen duong
     * tao don phai di cung chieu. Doi lai, hai request chay song song co the cung thay so cu va
     * vuot tran dung mot don — chap nhan duoc, vi day la chot chong lam dung chu khong phai mot
     * bat bien tien te.</p>
     */
    private void ensureDraftQuotaAvailable(Connection connection, int userId, int showtimeId) {
        int maxOpenDrafts = clampMaxOpenDrafts(
                settingInt(connection, SETTING_MAX_OPEN_DRAFTS, DEFAULT_MAX_OPEN_DRAFTS));
        String sql = """
                SELECT COUNT(*)
                FROM Orders WITH (UPDLOCK)
                WHERE UserId = ? AND ShowtimeId = ? AND OrderStatus = 'created'
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int openDrafts = rs.getInt(1);
                if (openDrafts >= maxOpenDrafts) {
                    throw new BookingException(409, "Bạn đang có " + openDrafts
                            + " đơn giữ chỗ chưa thanh toán cho suất chiếu này (tối đa " + maxOpenDrafts
                            + "). Vui lòng thanh toán hoặc huỷ bớt đơn cũ trong mục Lịch sử đặt vé rồi thử lại.");
                }
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kiểm tra số đơn giữ chỗ đang mở.", ex);
        }
    }

    /**
     * Kep {@code booking.maxOpenDraftsPerShowtime} ve khoang dung duoc (C.4, INV-6).
     *
     * <p>Tu khi nguong nay len {@code /system/config}, ai cung go duoc so vao do. Hai dau deu
     * nguy hiem theo hai kieu khac nhau: {@code 0} hoac so am nghia la khong ai dat duoc ve nao,
     * con mot so rat lon lam tran chong lam dung (BUG-04b) bien mat — mot tai khoan lai khoa
     * duoc sach ghe cua ca suat chieu. Ngoai khoang thi ve mac dinh, kem canh bao.</p>
     */
    public static int clampMaxOpenDrafts(int configured) {
        if (configured < MIN_MAX_OPEN_DRAFTS || configured > MAX_MAX_OPEN_DRAFTS) {
            LOGGER.warning(SETTING_MAX_OPEN_DRAFTS + " = " + configured + " nam ngoai khoang ["
                    + MIN_MAX_OPEN_DRAFTS + ", " + MAX_MAX_OPEN_DRAFTS + "], dung mac dinh "
                    + DEFAULT_MAX_OPEN_DRAFTS + ".");
            return DEFAULT_MAX_OPEN_DRAFTS;
        }
        return configured;
    }

    private static String normalizeIdempotencyKey(String idempotencyKey) {
        return (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey.trim();
    }

    /** Don da di het duong tien: hoac da thu tien, hoac da chot giu ghe cho quay. */
    private static boolean isSettled(OrderRecord order) {
        return "paid".equalsIgnoreCase(order.getPaymentStatus())
                || "confirmed".equalsIgnoreCase(order.getOrderStatus());
    }

    /**
     * Don ung voi mot lan {@code pay} lap lai, hoac {@code null} neu day khong phai lan lap lai.
     *
     * <p>Khoa idempotency phai gan chat voi <b>chu don</b> va voi <b>chinh don</b> da dung no.
     * Thieu rang buoc nay thi bat ky ai biet khoa cua nguoi khac deu doc duoc ma ve cua ho — lo
     * do thuc te da do duoc truoc khi sua BUG-02. Ca hai truong hop lech deu tra cung mot thong
     * bao trung tinh de khong lo don do la cua ai.</p>
     */
    private OrderRecord settledReplay(OrderRecord found, int userId, int orderId) {
        OrderRecord owned = ownedReplay(found, userId);
        if (owned == null) {
            return null;
        }
        if (owned.getId() != orderId) {
            throw new BookingException(409, IDEMPOTENCY_KEY_CONFLICT);
        }
        return isSettled(owned) ? owned : null;
    }

    /**
     * Don ung voi khoa, sau khi da chac chan khoa do thuoc ve chinh nguoi goi (A.2, BUG-02).
     *
     * <p>Duong <b>tao don</b> truoc day tra thang {@code findByIdempotencyKey(...)} ma khong kiem
     * chu don, nen ai biet khoa cua nguoi khac deu doc duoc {@code orderId} + {@code ticketCode}
     * that cua ho qua {@code POST /orders}. Duong <b>pay</b> thi da kiem tu truoc. Tach rieng
     * buoc "khoa nay co phai cua anh khong" ra day de ca hai duong dung CHUNG mot chot va chung
     * mot thong bao trung tinh — dung mot duong thu hai la cho lo lai mo.</p>
     */
    private OrderRecord ownedReplay(OrderRecord found, int userId) {
        if (found == null) {
            return null;
        }
        if (found.getUserId() != userId) {
            throw new BookingException(409, IDEMPOTENCY_KEY_CONFLICT);
        }
        return found;
    }

    /**
     * Ban chay TRONG transaction, sau khi khong tim thay don nao con cho thanh toan.
     *
     * <p>Ngoai duong tra cuu theo khoa, con mot truong hop nua: don da mang khoa cua <b>buoc tao
     * don</b> nen khoa cua lan pay khong duoc ghi de len (xem
     * {@code OrderDAO#applyIdempotencyKey}). Khi do van la lan pay lap lai hop le, vi nguoi goi
     * vua chung minh ho biet id don <b>va</b> la chu don.</p>
     */
    private OrderRecord settledReplayInTransaction(
            Connection connection, String paymentKey, int userId, int orderId) {
        if (paymentKey == null) {
            return null;
        }
        OrderRecord byKey = settledReplay(
                orderDAO.findByIdempotencyKey(connection, paymentKey).orElse(null), userId, orderId);
        if (byKey != null) {
            return byKey;
        }
        OrderRecord byId = orderDAO.findByIdAndUserId(connection, orderId, userId).orElse(null);
        return byId != null && isSettled(byId) ? byId : null;
    }

    /**
     * Nap ghe, combo va thong tin suat chieu cho don tra ve o duong lap lai.
     *
     * <p>INV-2 doi "cung ket qua", khong phai "cung ma ve": ban tra ve lan hai phai day du nhu
     * lan dau. Dung chinh {@code Connection} duoc truyen vao — duong trong transaction goi ham
     * nay khi dang giu khoa.</p>
     */
    private OrderRecord hydrateSettledOrder(Connection connection, OrderRecord order) {
        order.getSeats().addAll(orderDAO.findSeatsByOrderId(connection, order.getId()));
        order.getCombos().addAll(orderDAO.findCombosByOrderId(connection, order.getId()));
        showtimeDAO.findById(connection, order.getShowtimeId()).ifPresent(showtime -> {
            order.setFilmTitle(showtime.getFilmTitle());
            order.setCinemaName(showtime.getCinemaName());
            order.setRoomName(showtime.getRoomName());
            order.setStartTime(showtime.getStartTime());
        });
        return order;
    }

    public List<OrderRecord> findOrderHistory(int userId) {
        return orderDAO.findHistoryByUserId(userId);
    }

    public void hideOrderFromUserHistory(int orderId, int userId) {
        orderDAO.hideOrderFromUserHistory(orderId, userId);
    }

    public void hideBatchOrdersFromUserHistory(List<Integer> orderIds, int userId) {
        orderDAO.hideBatchOrdersFromUserHistory(orderIds, userId);
    }

    public void hideAllHistoryOrdersFromUserHistory(int userId) {
        orderDAO.hideAllHistoryOrdersFromUserHistory(userId);
    }

    public Optional<OrderRecord> findTicketForUser(String ticketCode, int userId) {
        return orderDAO.findByTicketCodeAndUserId(ticketCode, userId);
    }

    private Map<Integer, ComboFood> loadComboMapForShowtime(Connection connection, Map<Integer, Integer> comboSelections, int cinemaId) {
        if (comboSelections == null || comboSelections.isEmpty()) {
            return Map.of();
        }
        List<Integer> ids = new ArrayList<>(comboSelections.keySet());
        List<ComboFood> list = comboFoodDAO.findByIds(connection, ids, cinemaId);
        return list.stream().collect(Collectors.toMap(ComboFood::getId, combo -> combo, (a, b) -> a));
    }

    private List<OrderComboItem> buildComboItems(Map<Integer, Integer> comboSelections, Map<Integer, ComboFood> comboMap) {
        List<OrderComboItem> combos = new ArrayList<>();
        if (comboSelections == null) return combos;
        for (Map.Entry<Integer, Integer> entry : comboSelections.entrySet()) {
            int comboId = entry.getKey();
            int quantity = entry.getValue();
            if (quantity <= 0) {
                continue;
            }
            ComboFood combo = comboMap.get(comboId);
            if (combo == null) {
                throw new BookingException(400, "Combo khong hop le: " + comboId);
            }
            OrderComboItem item = new OrderComboItem();
            item.setComboFoodId(comboId);
            item.setComboName(combo.getName());
            item.setQuantity(quantity);
            item.setUnitPrice(combo.getPrice().setScale(0, RoundingMode.HALF_UP));
            combos.add(item);
        }
        return combos;
    }

    private List<OrderSeatItem> buildSeatItems(List<ShowtimeSeat> lockedSeats, BigDecimal basePrice) {
        List<OrderSeatItem> seats = new ArrayList<>();
        for (ShowtimeSeat lockedSeat : lockedSeats) {
            OrderSeatItem item = new OrderSeatItem();
            item.setShowtimeSeatId(lockedSeat.getId());
            item.setSeatKey(lockedSeat.getSeatKey());
            item.setSeatType(lockedSeat.getSeatType());
            item.setUnitPrice(basePrice.add(lockedSeat.getExtraFee()).setScale(0, RoundingMode.HALF_UP));
            seats.add(item);
        }
        return seats;
    }

    public Promotion resolvePromotion(String promotionCode) {
        return resolvePromotion(promotionCode, -1);
    }

    public Promotion resolvePromotion(String promotionCode, String userTier) {
        return resolvePromotion(promotionCode, -1, userTier);
    }

    public Promotion resolvePromotion(String promotionCode, int userId) {
        String tier = userId > 0
                ? userDAO.findById(userId).map(User::getMembershipTier).orElse("BRONZE")
                : "BRONZE";
        return resolvePromotion(promotionCode, userId, tier);
    }

    public Promotion resolvePromotion(String promotionCode, int userId, String userTier) {
        try (Connection connection = DBConnection.getConnection()) {
            return resolvePromotion(connection, promotionCode, userId, userTier);
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the xac minh ma khuyen mai.", ex);
        }
    }

    public Promotion resolvePromotion(Connection connection, String promotionCode, String userTier) {
        return resolvePromotion(connection, promotionCode, -1, userTier);
    }

    public Promotion resolvePromotion(
            Connection connection, String promotionCode, int userId, String userTier) {
        if (promotionCode == null || promotionCode.isBlank()) {
            return null;
        }
        String normalized = promotionCode.trim().toUpperCase();
        Integer voucherId = userId > 0
                ? promotionUsageDAO.findUsableVoucherId(connection, normalized, userId)
                : null;
        Optional<Promotion> optPromo;
        if (voucherId != null) {
            Integer promotionId = promotionUsageDAO.findPromotionIdForVoucher(connection, voucherId);
            optPromo = promotionId == null ? Optional.empty() : promotionDAO.findById(connection, promotionId);
        } else {
            optPromo = promotionDAO.findByCode(connection, normalized);
        }
        if (optPromo.isEmpty()) {
            throw new BookingException(400, "Mã khuyến mãi không tồn tại.");
        }
        Promotion promotion = optPromo.get();
        if (voucherId != null) {
            promotion.setUserVoucherId(voucherId);
        }
        java.time.LocalDate today;
        try (PreparedStatement ps = connection.prepareStatement("SELECT CAST(GETDATE() AS DATE)");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            today = rs.getDate(1).toLocalDate();
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the doc ngay nghiep vu tu co so du lieu.", ex);
        }
        boolean active = "active".equalsIgnoreCase(promotion.getStatus())
                && promotion.getStartDate() != null
                && promotion.getEndDate() != null
                && !today.isBefore(promotion.getStartDate())
                && !today.isAfter(promotion.getEndDate());
        boolean usageAvailable = promotion.getUsageLimit() == null || promotion.getUsedCount() < promotion.getUsageLimit();
        if (!active || !usageAvailable) {
            throw new BookingException(400, "Mã khuyến mãi đã hết hạn hoặc hết lượt sử dụng.");
        }
        if (voucherId == null && userId > 0 && promotion.getPerUserLimit() > 0
                && promotionUsageDAO.countForUser(connection, promotion.getId(), userId)
                        >= promotion.getPerUserLimit()) {
            throw new BookingException(400, "Bạn đã dùng mã khuyến mãi này đủ số lần cho phép.");
        }
        if ("TIER_RESTRICTED".equalsIgnoreCase(promotion.getVoucherType())) {
            String effectiveTier = userTier == null || userTier.isBlank() ? "BRONZE" : userTier;
            if (rankTier(effectiveTier) < rankTier(promotion.getTargetTier())) {
                throw new BookingException(400, "Mã giảm giá " + promotion.getCode() + " chỉ dành riêng cho thành viên " + promotion.getTargetTierDisplay() + ".");
            }
        }
        return promotion;
    }

    private int rankTier(String tier) {
        if ("EMERALD".equalsIgnoreCase(tier)) return 4;
        if ("DIAMOND".equalsIgnoreCase(tier)) return 3;
        if ("SILVER".equalsIgnoreCase(tier)) return 2;
        return 1; // BRONZE
    }

    private BigDecimal calculateDiscount(Promotion promotion, BigDecimal grossTotal) {
        if (promotion == null || promotion.getDiscountPercent() == null || grossTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);
        }
        if (promotion.getDiscountPercent() < 0 || promotion.getDiscountPercent() > 100) {
            throw new BookingException(400, "Phần trăm giảm giá phải nằm trong khoảng 0 đến 100.");
        }
        BigDecimal discount = grossTotal
                .multiply(BigDecimal.valueOf(promotion.getDiscountPercent()))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        if (promotion.getMaxDiscount() != null && discount.compareTo(promotion.getMaxDiscount()) > 0) {
            discount = promotion.getMaxDiscount();
        }
        return discount.setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Chan dat ve khi suat chieu da qua han nhan don (FLOW-SHOWTIME-001).
     *
     * <p><b>Loi da sua.</b> Dieu kien cu la {@code GETDATE() <= DATEADD(MINUTE, cutoff, StartTime)},
     * tuc la con cho dat them {@code cutoff} phut SAU khi phim da bat dau — dung nguoc chieu so
     * voi y nghia cua {@code booking.cutoffMinutes}. Voi cutoff 15, mot suat bat dau sau 5 phut
     * van tao duoc don, va mot suat da chieu duoc 10 phut cung vay.</p>
     *
     * <p>Dieu kien dung: phai con it nhat {@code cutoff} phut nua moi toi gio chieu, tuc
     * {@code now + cutoff <= StartTime}. Ca hai ve deu tinh trong SQL nen dung gio DB.</p>
     */
    private Integer findOrderShowtimeId(Connection connection, int orderId, int userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT ShowtimeId FROM Orders WHERE Id = ? AND UserId = ?")) {
            ps.setInt(1, orderId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private void ensureShowtimeBookable(Connection connection, Showtime showtime) {
        ensureShowtimeBookable(connection, showtime, false, null);
    }

    private void ensureShowtimeBookable(Connection connection, Showtime showtime,
            boolean existingDraftPayment, LocalDateTime draftCreatedAt) {
        BookingEligibility eligibility = evaluateBookingEligibility(connection, showtime,
                existingDraftPayment, draftCreatedAt);
        if (!eligibility.eligible()) {
            throw new BookingException(400, eligibility.message());
        }
    }

    private BookingEligibility evaluateBookingEligibility(Connection connection, Showtime showtime) {
        return evaluateBookingEligibility(connection, showtime, false, null);
    }

    private BookingEligibility evaluateBookingEligibility(Connection connection, Showtime showtime,
            boolean existingDraftPayment, LocalDateTime draftCreatedAt) {
        if (showtime.getStartTime() == null) {
            return unavailable(showtime, "INVALID_SHOWTIME", "Suất chiếu chưa có giờ bắt đầu hợp lệ.");
        }
        BookingEligibility lifecycle = showtimeSaleEligibility(connection, showtime,
                existingDraftPayment, draftCreatedAt);
        if (!lifecycle.eligible()) {
            return lifecycle;
        }
        int cutoffMinutes = settingInt(connection, "booking.cutoffMinutes", 15);
        String sql = "SELECT CASE WHEN DATEADD(MINUTE, ?, GETDATE()) <= ? THEN 1 ELSE 0 END";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cutoffMinutes);
            ps.setTimestamp(2, java.sql.Timestamp.valueOf(showtime.getStartTime()));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) != 1) {
                    return unavailable(showtime, "CUTOFF_REACHED",
                            "Suất chiếu đã bắt đầu hoặc đã qua thời hạn nhận đặt vé.");
                }
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kiểm tra thời hạn đặt vé.", ex);
        }
        BookingEligibility film = filmBookingEligibility(connection, showtime);
        if (!film.eligible()) {
            return film;
        }
        return roomBookingEligibility(connection, showtime);
    }

    private BookingEligibility showtimeSaleEligibility(Connection connection, Showtime showtime,
            boolean existingDraftPayment, LocalDateTime draftCreatedAt) {
        String sql = "SELECT SaleStatus, DeleteRequestedAt FROM Showtimes WHERE Id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, showtime.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BookingException(404, "Không tìm thấy suất chiếu.");
                }
                String status = rs.getString("SaleStatus");
                if ("ON_SALE".equalsIgnoreCase(status)) {
                    return available(showtime);
                }
                java.sql.Timestamp requestedAt = rs.getTimestamp("DeleteRequestedAt");
                boolean grandfatheredDraft = existingDraftPayment && draftCreatedAt != null
                        && "SUSPENDED".equalsIgnoreCase(status) && requestedAt != null
                        && !draftCreatedAt.isAfter(requestedAt.toLocalDateTime());
                if (grandfatheredDraft) {
                    return available(showtime);
                }
                return unavailable(showtime, "SHOWTIME_NOT_ON_SALE",
                        "Suất chiếu đã ngưng bán hoặc chỉ còn dữ liệu lịch sử.");
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể xác minh trạng thái bán của suất chiếu.", ex);
        }
    }

    /**
     * Chan thu tien cho suat chieu o phong da ngung hoat dong (BUG-05, INV-7).
     *
     * <p>Truoc day chot nay chi nam o {@code createDraftOrder}, nen quan ly chuyen phong sang
     * {@code inactive} trong luc khach dang giu ghe thi khach van thanh toan xong — he thong nhan
     * tien cho mot cho ngoi khong con phuc vu duoc. Dat trong
     * {@link #ensureShowtimeBookable(Connection, Showtime)} de moi duong di qua day deu chiu chung
     * mot chot, va dung chinh {@code Connection} cua transaction.</p>
     */
    private BookingEligibility roomBookingEligibility(Connection connection, Showtime showtime) {
        String sql = "SELECT Name, ISNULL(Status, 'active') AS RoomStatus FROM Rooms WHERE Id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, showtime.getRoomId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && !"active".equalsIgnoreCase(rs.getString("RoomStatus"))) {
                    return unavailable(showtime, "ROOM_INACTIVE", "Phòng chiếu '" + rs.getString("Name")
                            + "' đang tạm ngưng hoạt động. Không thể đặt vé mới.");
                }
            }
            return available(showtime);
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể xác minh trạng thái phòng chiếu.", ex);
        }
    }

    /**
     * Chan dat ve cho phim da het lich chieu hoac bi rut (EX-01).
     *
     * <p>Cho phap dat ve truoc (Presale) mien la suat chieu dien ra tu ngay khoi chieu den ngay ket thuc.</p>
     */
    private BookingEligibility filmBookingEligibility(Connection connection, Showtime showtime) {
        String sql = """
                SELECT f.Title, f.Status, f.ReleaseDate, f.EndDate, f.DeletedAt,
                       CAST(GETDATE() AS DATE) AS Today
                FROM Films f WHERE f.Id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, showtime.getFilmId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BookingException(404, "Không tìm thấy phim của suất chiếu này.");
                }
                if (rs.getTimestamp("DeletedAt") != null) {
                    return unavailable(showtime, "FILM_DELETED",
                            "Phim này đã được xóa khỏi hệ thống công khai.");
                }
                if ("ended".equalsIgnoreCase(rs.getString("Status"))) {
                    return unavailable(showtime, "FILM_ENDED",
                            "Phim \"" + rs.getString("Title") + "\" đã ngừng chiếu nên không còn bán vé.");
                }
                java.sql.Date endDate = rs.getDate("EndDate");
                java.sql.Date today = rs.getDate("Today");
                if (endDate != null && today != null && today.toLocalDate().isAfter(endDate.toLocalDate())) {
                    return unavailable(showtime, "FILM_EXPIRED",
                            "Phim \"" + rs.getString("Title") + "\" đã kết thúc lịch chiếu ngày "
                            + endDate.toLocalDate() + " nên không còn bán vé.");
                }
                java.sql.Date releaseDate = rs.getDate("ReleaseDate");
                if (releaseDate != null && showtime.getStartTime() != null
                        && showtime.getStartTime().toLocalDate().isBefore(releaseDate.toLocalDate())) {
                    return unavailable(showtime, "BEFORE_RELEASE",
                            "Suất chiếu này nằm trước ngày khởi chiếu của phim \"" + rs.getString("Title") + "\" ("
                            + releaseDate.toLocalDate() + ").");
                }
                return available(showtime);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kiểm tra lịch chiếu của phim.", ex);
        }
    }

    private BookingEligibility available(Showtime showtime) {
        return new BookingEligibility(showtime.getId(), true, "AVAILABLE", "Suất chiếu đang nhận đặt vé.");
    }

    private BookingEligibility unavailable(Showtime showtime, String code, String message) {
        return new BookingEligibility(showtime.getId(), false, code, message);
    }

    private int settingInt(Connection connection, String key, int fallback) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT SettingValue FROM SystemSettings WHERE SettingKey = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    try {
                        return Math.max(0, Integer.parseInt(rs.getString(1)));
                    } catch (NumberFormatException ex) {
                        return fallback;
                    }
                }
            }
            return fallback;
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể đọc cấu hình nghiệp vụ.", ex);
        }
    }

    private BigDecimal sumSeatSubtotal(List<OrderSeatItem> seats) {
        return seats.stream()
                .map(OrderSeatItem::getUnitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal sumComboSubtotal(List<OrderComboItem> combos) {
        return combos.stream()
                .map(OrderComboItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);
    }

    private String generatePendingTicketCode() {
        return "PENDING-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String generateFinalTicketCode() {
        return generateTicketCode();
    }

    public static String generateTicketCode() {
        StringBuilder code = new StringBuilder("CB");
        for (int i = 0; i < 26; i++) {
            code.append(TICKET_ALPHABET[TICKET_RANDOM.nextInt(TICKET_ALPHABET.length)]);
        }
        return code.toString();
    }

    public static Map<Integer, Integer> parseComboSelections(String raw) {
        Map<Integer, Integer> selections = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return selections;
        }
        for (String token : raw.split(",")) {
            String[] pair = token.trim().split(":");
            if (pair.length != 2) {
                continue;
            }
            try {
                int comboId = Integer.parseInt(pair[0].trim());
                int quantity = Integer.parseInt(pair[1].trim());
                if (quantity > 0) {
                    selections.put(comboId, quantity);
                }
            } catch (NumberFormatException invalidComboToken) {
                LOGGER.log(Level.FINE, "Bo qua combo token khong hop le: {0}", token);
            }
        }
        return selections;
    }

    public static List<Integer> parseSeatIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Integer> seatIds = new ArrayList<>();
        for (String token : raw.split(",")) {
            try {
                seatIds.add(Integer.parseInt(token.trim()));
            } catch (NumberFormatException invalidSeatToken) {
                LOGGER.log(Level.FINE, "Bo qua seat token khong hop le: {0}", token);
            }
        }
        return seatIds;
    }

    public void cancelUserDraftOrder(int userId, int orderId) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                CancellationOrder order = lockOrderForCancellation(conn, orderId);
                if (order == null) {
                    throw new BookingException(404, "Không tìm thấy đơn hàng.");
                }
                afterCancellationOrderRead(orderId);
                if (order.userId() != userId) {
                    throw new BookingException(403, "Bạn không có quyền hủy đơn hàng này.");
                }
                if ("cancelled".equalsIgnoreCase(order.orderStatus())) {
                    conn.commit();
                    return;
                }
                boolean cancellable = "pending".equalsIgnoreCase(order.paymentStatus())
                        && ("created".equalsIgnoreCase(order.orderStatus())
                        || "pending".equalsIgnoreCase(order.orderStatus()));
                if (!cancellable) {
                    throw new BookingException(409,
                            "Đơn hàng đã được xác nhận hoặc thanh toán, không thể tự hủy.");
                }

                try (PreparedStatement psRelease = conn.prepareStatement(
                        """
                        UPDATE ss
                        SET ss.Status='available', ss.HeldByUserId=NULL,
                            ss.HeldAt=NULL, ss.HeldUntil=NULL, ss.ClaimedByOrderId=NULL
                        FROM ShowtimeSeats ss
                        JOIN OrderSeats os ON os.ShowtimeSeatId=ss.Id
                        WHERE os.OrderId=?
                          AND ss.Status='held'
                          AND ss.HeldByUserId=?
                          AND (ss.ClaimedByOrderId=os.OrderId OR ss.ClaimedByOrderId IS NULL)
                          AND NOT EXISTS (
                            SELECT 1
                            FROM OrderSeats otherSeats
                            JOIN Orders otherOrder ON otherOrder.Id=otherSeats.OrderId
                            WHERE otherSeats.ShowtimeSeatId=ss.Id
                              AND otherSeats.OrderId<>?
                              AND otherOrder.UserId=?
                              AND otherOrder.PaymentStatus='pending'
                              AND otherOrder.OrderStatus IN ('created','pending')
                          )
                        """)) {
                    psRelease.setInt(1, orderId);
                    psRelease.setInt(2, userId);
                    psRelease.setInt(3, orderId);
                    psRelease.setInt(4, userId);
                    psRelease.executeUpdate();
                }

                try (PreparedStatement psCancel = conn.prepareStatement(
                        """
                        UPDATE Orders
                        SET OrderStatus='cancelled', PaymentStatus='cancelled', UpdatedAt=GETDATE()
                        WHERE Id=? AND UserId=? AND PaymentStatus='pending'
                          AND OrderStatus IN ('created','pending')
                        """)) {
                    psCancel.setInt(1, orderId);
                    psCancel.setInt(2, userId);
                    if (psCancel.executeUpdate() != 1) {
                        throw new BookingException(409,
                                "Trạng thái đơn hàng đã thay đổi. Vui lòng tải lại lịch sử đặt vé.");
                    }
                }

                conn.commit();
                auditAfterCommit(userId, "CANCEL_DRAFT_ORDER", orderId,
                        javax.json.Json.createObjectBuilder()
                                .add("previousOrderStatus", String.valueOf(order.orderStatus()))
                                .add("previousPaymentStatus", String.valueOf(order.paymentStatus()))
                                .build().toString());
            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể hủy đơn hàng lúc này.", ex);
        }
    }

    /**
     * Reads and locks the order that is the serialization point for cancel versus pay.
     *
     * <p>Both paths acquire the {@code Orders} lock before touching {@code ShowtimeSeats}. The
     * lock remains held through authorization, state validation, seat release and the conditional
     * status update, so a payment can only observe the state before or after the whole cancel.</p>
     */
    private CancellationOrder lockOrderForCancellation(Connection connection, int orderId)
            throws SQLException {
        String sql = """
                SELECT Id,UserId,OrderStatus,PaymentStatus
                FROM Orders WITH (UPDLOCK,HOLDLOCK)
                WHERE Id=?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new CancellationOrder(
                        rs.getInt("Id"), rs.getInt("UserId"),
                        rs.getString("OrderStatus"), rs.getString("PaymentStatus"));
            }
        }
    }

    /**
     * No-op checkpoint used by deterministic concurrency tests.
     *
     * <p>Production callers never override this method. Keeping the checkpoint immediately after
     * the cancellation path reads its order lets the test pause that transaction without sleeps
     * or database-wide triggers.</p>
     */
    protected void afterCancellationOrderRead(int orderId) {
        // Intentionally empty.
    }

    /**
     * Ghi audit cho mot thao tac dung tien, <b>sau khi</b> transaction da commit (BUG-09, INV-9).
     *
     * <p>Phai nam ngoai transaction vi {@code logAction} mo connection rieng — goi trong luc con
     * giu {@code UPDLOCK} la tu tao deadlock voi pool 10-20 ket noi.</p>
     *
     * <p>Audit hong <b>khong</b> duoc lam hong giao dich da hoan tat: tien da chuyen, ve da phat,
     * khong the rollback nua. Ghi {@code SEVERE} kem nguyen nhan la hanh vi thay the duy nhat con
     * lai — dung nguyen tac ma {@code InvoiceService}/{@code EmailService} ngay ben canh dang dung.</p>
     */
    private void auditAfterCommit(int actorUserId, String action, int orderId, String detailJson) {
        auditAfterCommit(actorUserId, action, orderId, null, detailJson);
    }

    /**
     * Ban co {@code beforeJson} (B.3): trang thai don TRUOC khi thao tac doi no.
     *
     * <p>Thieu truong nay thi dong audit chi noi duoc "da lam gi", khong noi duoc "lam tren cai
     * gi" — khi khach khieu nai thi khong dung lai duoc hien truong.</p>
     */
    private void auditAfterCommit(int actorUserId, String action, int orderId,
            String beforeJson, String detailJson) {
        try {
            new AdminService().logAction(actorUserId, action, "Order", String.valueOf(orderId),
                    beforeJson, detailJson,
                    com.mycompany.website.ban.ve.xem.phim.util.RequestContext.ipAddress(),
                    com.mycompany.website.ban.ve.xem.phim.util.RequestContext.userAgent());
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Khong ghi duoc audit " + action + " cho orderId=" + orderId, ex);
        }
    }
}

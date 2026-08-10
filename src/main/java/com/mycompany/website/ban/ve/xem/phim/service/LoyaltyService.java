package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.PointTransaction;
import com.mycompany.website.ban.ve.xem.phim.model.Promotion;
import com.mycompany.website.ban.ve.xem.phim.model.UserVoucher;
import com.mycompany.website.ban.ve.xem.phim.model.MembershipTier;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoyaltyService {
    private static final Logger LOGGER = Logger.getLogger(LoyaltyService.class.getName());
    private static final SecureRandom VOUCHER_RANDOM = new SecureRandom();
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final String REDEMPTION_KEY_PREFIX = "REDEEM_REQUEST:";
    private static final int MAX_REDEMPTION_KEY_LENGTH = 100 - REDEMPTION_KEY_PREFIX.length();

    /**
     * Mot policy duy nhat cho ca kho qua GET va transaction POST.
     *
     * <p>{@code UsedCount} la so voucher da tieu vao don. Voucher ca nhan chua dung la mot
     * luot da duoc giu cho; khi voucher duoc dung, {@code UsedCount} tang mot va so voucher
     * chua dung giam mot, nen tong nay khong dem trung.</p>
     */
    private static final String REDEMPTION_POLICY_SQL = """
            p.Status = 'active'
            AND p.VoucherType = 'REDEEMABLE'
            AND p.PointsRequired > 0
            AND CAST(GETDATE() AS DATE) BETWEEN p.StartDate AND p.EndDate
            AND (p.UsageLimit IS NULL OR p.UsedCount + (
                SELECT COUNT(*) FROM UserVouchers quotaVoucher
                WHERE quotaVoucher.PromotionId = p.Id AND quotaVoucher.IsUsed = 0
            ) < p.UsageLimit)
            AND (p.PerUserLimit <= 0 OR (
                SELECT COUNT(*) FROM UserVouchers userVoucher
                WHERE userVoucher.PromotionId = p.Id AND userVoucher.UserId = u.Id
            ) + (
                SELECT COUNT(*) FROM PromotionUsage promotionUse
                WHERE promotionUse.PromotionId = p.Id AND promotionUse.UserId = u.Id
            ) < p.PerUserLimit)
            AND CASE UPPER(COALESCE(NULLIF(u.MembershipTier, ''), 'BRONZE'))
                    WHEN 'EMERALD' THEN 4
                    WHEN 'DIAMOND' THEN 3
                    WHEN 'SILVER' THEN 2
                    ELSE 1
                END >= CASE UPPER(COALESCE(NULLIF(p.TargetTier, ''), 'ALL'))
                    WHEN 'ALL' THEN 0
                    WHEN 'BRONZE' THEN 1
                    WHEN 'SILVER' THEN 2
                    WHEN 'DIAMOND' THEN 3
                    WHEN 'EMERALD' THEN 4
                    ELSE 5
                END
            """;

    /** SQL Server: 2601 = unique index, 2627 = unique constraint. */
    private static final Set<Integer> DUPLICATE_KEY_ERRORS = Set.of(2601, 2627);

    private static boolean isDuplicateKey(SQLException ex) {
        for (SQLException current = ex; current != null; current = current.getNextException()) {
            if (DUPLICATE_KEY_ERRORS.contains(current.getErrorCode())) {
                return true;
            }
        }
        return false;
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    public void addPointsOnPayment(int userId, BigDecimal orderTotal) {
        addPointsOnPayment(userId, orderTotal, null);
    }

    /**
     * Cong diem cho mot don da thanh toan (FLOW-LOY-004).
     *
     * @param orderId don goc; khac {@code null} thi lan cong thu hai cho cung don do bi bo qua
     */
    public void addPointsOnPayment(int userId, BigDecimal orderTotal, Integer orderId) {
        if (orderTotal == null || orderTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                addPointsOnPayment(connection, userId, orderTotal, orderId);
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể cập nhật điểm loyalty.", ex);
        }
    }

    public void addPointsOnPayment(Connection connection, int userId, BigDecimal orderTotal) throws SQLException {
        addPointsOnPayment(connection, userId, orderTotal, null);
    }

    /**
     * Cong diem trong transaction cua nguoi goi (FLOW-LOY-004).
     *
     * <p><b>Ty le.</b> 1.000 VNĐ = 1 diem, tuc 10.000 VNĐ = 10 diem, lam tron xuong.</p>
     *
     * <p><b>Chi cong mot lan cho mot don.</b> Khi co {@code orderId}, but toan mang khoa
     * {@code EARN_PURCHASE:<orderId>}. Unique index loc tren {@code IdempotencyKey} bien lan
     * cong thu hai thanh loi trung khoa; ham nay bat dung loi do va thoat im lang. Kiem tra
     * "da cong chua" bang mot SELECT truoc do se khong an toan — hai callback ve cung luc
     * deu doc thay "chua cong".</p>
     *
     * <p>{@code LifetimeEarnedPoints} chi tang, khong bao gio giam khi tieu diem — do la co so
     * de hang thanh vien khong tut chi vi doi voucher (FLOW-LOY-001).</p>
     */
    public void addPointsOnPayment(Connection connection, int userId, BigDecimal orderTotal,
            Integer orderId) throws SQLException {
        if (orderTotal == null || orderTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        // 10,000 VNĐ = 10 Loyalty Points (1000 VNĐ = 1 Point)
        int earnedPoints = orderTotal.divide(BigDecimal.valueOf(1000), 0, java.math.RoundingMode.FLOOR).intValue();
        String idempotencyKey = orderId == null ? null : "EARN_PURCHASE:" + orderId;

        // 1. But toan truoc: no la thu duy nhat chan duoc lan cong thu hai cho cung mot don.
        String transSql = """
                INSERT INTO PointTransactions
                    (UserId, Points, Type, Description, OrderId, IdempotencyKey)
                VALUES (?, ?, 'EARN_PURCHASE', ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(transSql)) {
            ps.setInt(1, userId);
            ps.setInt(2, earnedPoints);
            ps.setString(3, "Tích " + earnedPoints + " điểm từ đơn hàng " + String.format("%,d VNĐ", orderTotal.longValue()));
            setNullableInt(ps, 4, orderId);
            ps.setString(5, idempotencyKey);
            ps.executeUpdate();
        } catch (SQLException ex) {
            if (idempotencyKey != null && isDuplicateKey(ex)) {
                LOGGER.log(Level.INFO,
                        "Bo qua lan cong diem lap cho don {0} (idempotency key da ton tai)", orderId);
                return;
            }
            throw ex;
        }

        // 2. A refund may have left point debt.  New earned points settle that
        // debt first; only the remainder reaches the spendable wallet.
        int debtBalance;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT LoyaltyPointDebt FROM Users WITH (UPDLOCK, ROWLOCK) WHERE Id=?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("User not found for loyalty accrual");
                }
                debtBalance = rs.getInt(1);
            }
        }
        int debtSettled = Math.min(Math.max(debtBalance, 0), earnedPoints);

        // 3. So du tieu duoc va tong tich luy suot doi tang cung nhau; TotalSpent theo tien.
        String updateSql = """
                UPDATE Users
                SET LoyaltyPoints = LoyaltyPoints + ? - ?,
                    LoyaltyPointDebt = LoyaltyPointDebt - ?,
                    LifetimeEarnedPoints = LifetimeEarnedPoints + ?,
                    TotalSpent = TotalSpent + ?,
                    UpdatedAt = GETDATE()
                WHERE Id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
            ps.setInt(1, earnedPoints);
            ps.setInt(2, debtSettled);
            ps.setInt(3, debtSettled);
            ps.setInt(4, earnedPoints);
            ps.setBigDecimal(5, orderTotal);
            ps.setInt(6, userId);
            ps.executeUpdate();
        }

        if (debtSettled > 0) {
            String debtKey = orderId == null ? "DEBT_SETTLE:" + java.util.UUID.randomUUID()
                    : "DEBT_SETTLE:" + orderId;
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO LoyaltyPointDebtLedger
                        (UserId, OrderId, Points, Type, IdempotencyKey)
                    VALUES (?, ?, ?, 'DEBT_SETTLEMENT', ?)
                    """)) {
                ps.setInt(1, userId);
                setNullableInt(ps, 2, orderId);
                ps.setInt(3, debtSettled);
                ps.setString(4, debtKey);
                ps.executeUpdate();
            }
        }

        // 4. Auto-tier calculation based on lifetime earned / total spent.
        updateMembershipTierAuto(connection, userId);
    }

    private void updateMembershipTierAuto(Connection connection, int userId) throws SQLException {
        String query = "SELECT TotalSpent, MembershipTier FROM Users WHERE Id = ?";
        BigDecimal totalSpent = BigDecimal.ZERO;
        String currentTier = "BRONZE";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalSpent = rs.getBigDecimal("TotalSpent");
                    currentTier = rs.getString("MembershipTier");
                }
            }
        }

        if (totalSpent == null) totalSpent = BigDecimal.ZERO;

        String newTier = "BRONZE";
        if (totalSpent.compareTo(BigDecimal.valueOf(3000000)) >= 0) {
            newTier = "EMERALD";
        } else if (totalSpent.compareTo(BigDecimal.valueOf(1500000)) >= 0) {
            newTier = "DIAMOND";
        } else if (totalSpent.compareTo(BigDecimal.valueOf(500000)) >= 0) {
            newTier = "SILVER";
        }

        // Tier upgrades automatically (never degrades unless manually set by admin)
        if (tierRank(newTier) > tierRank(currentTier)) {
            String tierSql = "UPDATE Users SET MembershipTier = ?, UpdatedAt = GETDATE() WHERE Id = ?";
            try (PreparedStatement ps = connection.prepareStatement(tierSql)) {
                ps.setString(1, newTier);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
        }
    }

    /**
     * Doi diem lay mot voucher ca nhan (FLOW-LOY-005/006).
     *
     * <p><b>Vi sao khong con doc-roi-ghi.</b> Ban cu {@code SELECT LoyaltyPoints} roi
     * {@code UPDATE ... SET LoyaltyPoints = LoyaltyPoints - ?} khong co dieu kien: N request
     * cung doc thay 100 diem, ca N deu cho la du, va ca N deu tru — so du thanh am va nguoi
     * dung nhan N voucher voi gia cua mot. Do la loi da do duoc: 8 request dong thoi tren
     * 100 diem cap 2 voucher.</p>
     *
     * <p><b>Hai lop khoa.</b> {@code SELECT ... WITH (UPDLOCK, ROWLOCK)} xep hang cac request
     * tren dung dong Users do; {@code UPDATE ... WHERE LoyaltyPoints &gt;= ?} la dieu kien
     * nguyen tu quyet dinh ai thang. Chi rieng lop thu hai da du dung, nhung UPDLOCK bien
     * "tranh chap roi that bai" thanh "cho roi thay so du that", nen thong bao tra ve cho
     * nguoi dung dung voi thuc te. Lop thu ba la CHECK constraint trong DB (fix20).</p>
     *
     * <p>Thu tu ghi: tru diem truoc, roi moi cap voucher va ghi but toan. Nguoc lai thi mot
     * lan rollback giua chung se de lai voucher da phat ma khong tru diem.</p>
     */
    /** Returns only rewards that the same user can redeem at the database's current date. */
    public List<Promotion> listRedeemablePromotionsForUser(int userId) {
        String sql = """
                SELECT p.*
                FROM Promotions p
                JOIN Users u ON u.Id = ? AND u.Deleted = 0
                WHERE
                """ + REDEMPTION_POLICY_SQL + " ORDER BY p.CreatedAt DESC";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            List<Promotion> promotions = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    promotions.add(mapPromotion(rs));
                }
            }
            return promotions;
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai kho voucher doi diem.", ex);
        }
    }

    /** Caller noi bo khong co request key coi moi loi goi la mot giao dich moi. */
    public void redeemVoucherWithPoints(int userId, int promotionId) {
        redeemVoucherWithPoints(userId, promotionId, UUID.randomUUID().toString());
    }

    /**
     * Doi diem voi khoa idempotency do request cap.
     *
     * <p>Lap lai cung khoa, user va promotion tra thanh cong ma khong ghi them. Tai su dung
     * khoa cho user hoac promotion khac bi tu choi 409.</p>
     */
    public void redeemVoucherWithPoints(int userId, int promotionId, String redemptionKey) {
        String ledgerKey = normalizeRedemptionKey(redemptionKey);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (lockRedemptionRequest(connection, userId, promotionId, ledgerKey)) {
                    connection.commit();
                    return;
                }

                Promotion promo = loadRedeemablePromotion(connection, userId, promotionId);
                if (promo == null) {
                    throw new BookingException(400,
                            "Voucher khong ton tai hoac khong con du dieu kien doi diem.");
                }

                int required = promo.getPointsRequired();
                lockBalance(connection, userId);

                int debited;
                try (PreparedStatement ps = connection.prepareStatement("""
                        UPDATE Users
                        SET LoyaltyPoints = LoyaltyPoints - ?, UpdatedAt = GETDATE()
                        WHERE Id = ? AND LoyaltyPoints >= ?
                        """)) {
                    ps.setInt(1, required);
                    ps.setInt(2, userId);
                    ps.setInt(3, required);
                    debited = ps.executeUpdate();
                }
                if (debited != 1) {
                    throw new BookingException(400,
                            "Bạn không đủ điểm Loyalty. Cần " + required + " điểm.");
                }

                int voucherId = insertVoucher(connection, userId, promo.getId());
                recordRedemption(connection, userId, voucherId, required, promo.getCode(), ledgerKey);

                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            if (isDuplicateKey(ex) && committedRedemptionMatches(userId, promotionId, ledgerKey)) {
                return;
            }
            throw new BookingException(500, "Không thể đổi voucher.", ex);
        }
    }

    private String normalizeRedemptionKey(String redemptionKey) {
        if (redemptionKey == null || redemptionKey.isBlank()) {
            throw new BookingException(400, "Thieu khoa idempotency cho lan doi voucher.");
        }
        String normalized = redemptionKey.trim();
        if (normalized.length() > MAX_REDEMPTION_KEY_LENGTH) {
            throw new BookingException(400, "Khoa idempotency doi voucher khong hop le.");
        }
        return REDEMPTION_KEY_PREFIX + normalized;
    }

    /** Locks the unique-key range so concurrent copies of one request serialize. */
    private boolean lockRedemptionRequest(Connection connection, int userId, int promotionId,
            String ledgerKey) throws SQLException {
        String sql = """
                SELECT pt.UserId, uv.PromotionId
                FROM PointTransactions pt
                    WITH (UPDLOCK, HOLDLOCK, INDEX(UX_PointTransactions_IdempotencyKey))
                JOIN UserVouchers uv ON uv.Id = pt.VoucherId
                WHERE pt.IdempotencyKey = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ledgerKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                if (rs.getInt("UserId") == userId && rs.getInt("PromotionId") == promotionId) {
                    return true;
                }
                throw new BookingException(409,
                        "Khoa idempotency da duoc dung cho mot lan doi voucher khac.");
            }
        }
    }

    private Promotion loadRedeemablePromotion(Connection connection, int userId, int promotionId)
            throws SQLException {
        String sql = """
                SELECT p.*
                FROM Promotions p WITH (UPDLOCK, HOLDLOCK, ROWLOCK)
                JOIN Users u WITH (UPDLOCK, HOLDLOCK, ROWLOCK)
                    ON u.Id = ? AND u.Deleted = 0
                WHERE p.Id = ? AND
                """ + REDEMPTION_POLICY_SQL;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, promotionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapPromotion(rs) : null;
            }
        }
    }

    private boolean committedRedemptionMatches(int userId, int promotionId, String ledgerKey) {
        String sql = """
                SELECT pt.UserId, uv.PromotionId
                FROM PointTransactions pt
                JOIN UserVouchers uv ON uv.Id = pt.VoucherId
                WHERE pt.IdempotencyKey = ?
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ledgerKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                if (rs.getInt("UserId") == userId && rs.getInt("PromotionId") == promotionId) {
                    return true;
                }
                throw new BookingException(409,
                        "Khoa idempotency da duoc dung cho mot lan doi voucher khac.");
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the kiem tra lan doi voucher da ghi.", ex);
        }
    }

    private Promotion mapPromotion(ResultSet rs) throws SQLException {
        Promotion promotion = new Promotion();
        promotion.setId(rs.getInt("Id"));
        promotion.setCode(rs.getString("Code"));
        promotion.setDescription(rs.getString("Description"));
        double discount = rs.getDouble("DiscountPercent");
        promotion.setDiscountPercent(rs.wasNull() ? null : discount);
        promotion.setMaxDiscount(rs.getBigDecimal("MaxDiscount"));
        Date startDate = rs.getDate("StartDate");
        Date endDate = rs.getDate("EndDate");
        promotion.setStartDate(startDate == null ? null : startDate.toLocalDate());
        promotion.setEndDate(endDate == null ? null : endDate.toLocalDate());
        promotion.setConditionsJson(rs.getString("ConditionsJson"));
        int usageLimit = rs.getInt("UsageLimit");
        promotion.setUsageLimit(rs.wasNull() ? null : usageLimit);
        promotion.setUsedCount(rs.getInt("UsedCount"));
        promotion.setStatus(rs.getString("Status"));
        promotion.setVoucherType(rs.getString("VoucherType"));
        promotion.setTargetTier(rs.getString("TargetTier"));
        promotion.setPointsRequired(rs.getInt("PointsRequired"));
        promotion.setPerUserLimit(rs.getInt("PerUserLimit"));
        return promotion;
    }

    /** Giu dong so du cho den het transaction de cac request khac doc duoc so da tru. */
    private void lockBalance(Connection connection, int userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT LoyaltyPoints FROM Users WITH (UPDLOCK, ROWLOCK) WHERE Id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BookingException(404, "Không tìm thấy tài khoản.");
                }
            }
        }
    }

    private int insertVoucher(Connection connection, int userId, int promotionId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO UserVouchers (UserId, PromotionId, Code) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setInt(2, promotionId);
            ps.setString(3, generateVoucherCode());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Khong lay duoc Id cua voucher vua cap");
                }
                return keys.getInt(1);
            }
        }
    }

    /** But toan tru diem, gan vao dung voucher da cap — mot voucher, mot dong so. */
    private void recordRedemption(Connection connection, int userId, int voucherId,
            int points, String promotionCode, String ledgerKey) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO PointTransactions
                    (UserId, Points, Type, Description, VoucherId, IdempotencyKey)
                VALUES (?, ?, 'REDEEM_VOUCHER', ?, ?, ?)
                """)) {
            ps.setInt(1, userId);
            ps.setInt(2, -points);
            ps.setString(3, "Đổi " + points + " điểm lấy voucher " + promotionCode);
            ps.setInt(4, voucherId);
            ps.setString(5, ledgerKey);
            ps.executeUpdate();
        }
    }

    public List<UserVoucher> getUserVouchers(int userId) {
        String sql = """
                SELECT uv.*, p.Description AS PromotionDescription, p.DiscountPercent, p.MaxDiscount
                FROM UserVouchers uv
                JOIN Promotions p ON p.Id = uv.PromotionId
                WHERE uv.UserId = ?
                ORDER BY uv.CreatedAt DESC
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            List<UserVoucher> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UserVoucher uv = new UserVoucher();
                    uv.setId(rs.getInt("Id"));
                    uv.setUserId(rs.getInt("UserId"));
                    uv.setPromotionId(rs.getInt("PromotionId"));
                    uv.setCode(rs.getString("Code"));
                    uv.setUsed(rs.getBoolean("IsUsed"));
                    Timestamp usedAt = rs.getTimestamp("UsedAt");
                    uv.setUsedAt(usedAt == null ? null : usedAt.toLocalDateTime());
                    Timestamp createdAt = rs.getTimestamp("CreatedAt");
                    uv.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
                    uv.setPromotionDescription(rs.getString("PromotionDescription"));
                    uv.setDiscountPercent(rs.getDouble("DiscountPercent"));
                    uv.setMaxDiscount(rs.getBigDecimal("MaxDiscount"));
                    list.add(uv);
                }
            }
            return list;
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể nạp voucher cá nhân.", ex);
        }
    }

    public List<PointTransaction> getUserPointTransactions(int userId) {
        String sql = "SELECT * FROM PointTransactions WHERE UserId = ? ORDER BY CreatedAt DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            List<PointTransaction> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PointTransaction pt = new PointTransaction();
                    pt.setId(rs.getInt("Id"));
                    pt.setUserId(rs.getInt("UserId"));
                    pt.setPoints(rs.getInt("Points"));
                    pt.setType(rs.getString("Type"));
                    pt.setDescription(rs.getString("Description"));
                    Timestamp ts = rs.getTimestamp("CreatedAt");
                    pt.setCreatedAt(ts == null ? null : ts.toLocalDateTime());
                    list.add(pt);
                }
            }
            return list;
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể nạp lịch sử điểm.", ex);
        }
    }

    public void reversePointsOnRefund(Connection connection, int userId, BigDecimal refundAmount, String reason) throws SQLException {
        reversePointsOnRefund(connection, userId, null, refundAmount, reason);
    }

    /** Full-refund reversal with a durable point-debt ledger when the wallet is short. */
    public void reversePointsOnRefund(Connection connection, int userId, Integer orderId,
            BigDecimal refundAmount, String reason) throws SQLException {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        int deductPoints = refundAmount.divide(BigDecimal.valueOf(1000), 0, java.math.RoundingMode.FLOOR).intValue();
        int available;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT LoyaltyPoints, LoyaltyPointDebt FROM Users WITH (UPDLOCK, ROWLOCK) WHERE Id=?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("User not found for loyalty refund");
                }
                available = rs.getInt("LoyaltyPoints");
            }
        }
        int deducted = Math.min(Math.max(available, 0), deductPoints);
        int debt = deductPoints - deducted;
        String updateSql = """
                UPDATE Users
                SET LoyaltyPoints = LoyaltyPoints - ?,
                    LoyaltyPointDebt = LoyaltyPointDebt + ?,
                    TotalSpent = CASE WHEN TotalSpent >= ? THEN TotalSpent - ? ELSE 0 END,
                    UpdatedAt = GETDATE()
                WHERE Id = ? AND LoyaltyPoints >= ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
            ps.setInt(1, deducted);
            ps.setInt(2, debt);
            ps.setBigDecimal(3, refundAmount);
            ps.setBigDecimal(4, refundAmount);
            ps.setInt(5, userId);
            ps.setInt(6, deducted);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Loyalty balance changed during refund");
            }
        }

        String key = orderId == null ? "REFUND_DEDUCT:" + java.util.UUID.randomUUID()
                : "REFUND_DEDUCT:" + orderId;
        String transSql = """
                INSERT INTO PointTransactions
                    (UserId, Points, Type, Description, OrderId, IdempotencyKey)
                VALUES (?, ?, 'REFUND_DEDUCT', ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(transSql)) {
            ps.setInt(1, userId);
            ps.setInt(2, -deducted);
            ps.setString(3, "Trừ " + deducted + " điểm do hoàn tiền " + String.format("%,d VNĐ", refundAmount.longValue())
                    + " (" + (reason == null ? "Hoàn tiền" : reason) + ")");
            setNullableInt(ps, 4, orderId);
            ps.setString(5, key);
            ps.executeUpdate();
        }
        if (debt > 0) {
            String debtKey = orderId == null ? "REFUND_DEBT:" + java.util.UUID.randomUUID()
                    : "REFUND_DEBT:" + orderId;
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO LoyaltyPointDebtLedger
                        (UserId, OrderId, Points, Type, IdempotencyKey)
                    VALUES (?, ?, ?, 'REFUND_DEBT', ?)
                    """)) {
                ps.setInt(1, userId);
                setNullableInt(ps, 2, orderId);
                ps.setInt(3, debt);
                ps.setString(4, debtKey);
                ps.executeUpdate();
            }
        }
    }

    public void recomputeTier(int userId) {
        try (Connection connection = DBConnection.getConnection()) {
            String sql = """
                    UPDATE Users SET MembershipTier =
                        CASE WHEN TotalSpent >= 3000000 THEN 'EMERALD'
                             WHEN TotalSpent >= 1500000 THEN 'DIAMOND'
                             WHEN TotalSpent >= 500000 THEN 'SILVER'
                             ELSE 'BRONZE' END,
                        UpdatedAt = GETDATE()
                    WHERE Id = ?
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tính lại hạng thành viên.", ex);
        }
    }

    public static String generateVoucherCode() {
        StringBuilder code = new StringBuilder("UV");
        for (int i = 0; i < 18; i++) {
            code.append(CODE_ALPHABET[VOUCHER_RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }

    private int tierRank(String tier) {
        return MembershipTier.fromCode(tier).orElse(MembershipTier.BRONZE).rank();
    }
}

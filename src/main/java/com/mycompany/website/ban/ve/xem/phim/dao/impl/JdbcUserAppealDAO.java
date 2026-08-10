package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.dao.BaseDAO;
import com.mycompany.website.ban.ve.xem.phim.config.SettingsReader;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.UserAppealDAO;
import com.mycompany.website.ban.ve.xem.phim.model.UserAppeal;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** JDBC persistence for both account-unlock and missed-ticket refund appeals. */
public class JdbcUserAppealDAO extends BaseDAO implements UserAppealDAO {
    private static final int APPEAL_LOCK_TIMEOUT_MS = 10_000;

    @Override
    public int create(UserAppeal appeal) {
        try (Connection connection = getConnection()) {
            return create(connection, appeal);
        } catch (SQLException ex) {
            throw new DaoException("Cannot create appeal", ex);
        }
    }

    @Override
    public int create(Connection connection, UserAppeal appeal) {
        String sql = """
                INSERT INTO UserAppeals
                    (UserId, Email, Reason, TicketCode, BankAccountInfo, Status, AppealType, OrderId, CinemaId)
                VALUES (?, ?, ?, ?, ?, 'pending', ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, appeal.getUserId());
            statement.setString(2, appeal.getEmail());
            statement.setString(3, appeal.getReason());
            statement.setString(4, appeal.getTicketCode());
            statement.setString(5, appeal.getBankAccountInfo());
            boolean refund = appeal.isRefundAppeal();
            if (refund && appeal.getOrderId() == null) {
                throw new BookingException(409,
                        "Yêu cầu hoàn tiền chưa liên kết với đơn vé hợp lệ.");
            }
            statement.setString(6, refund ? "refund" : "account");
            if (refund) {
                statement.setInt(7, appeal.getOrderId());
            } else {
                statement.setNull(7, java.sql.Types.INTEGER);
            }
            if (appeal.getCinemaId() == null || appeal.getCinemaId() <= 0) {
                statement.setNull(8, java.sql.Types.INTEGER);
            } else {
                statement.setInt(8, appeal.getCinemaId());
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    appeal.setId(id);
                    return id;
                }
            }
            throw new DaoException("Cannot read generated appeal id", null);
        } catch (SQLException ex) {
            throw new DaoException("Cannot create appeal", ex);
        }
    }

    /**
     * Atomically validates and creates one pending refund appeal per ticket.
     * SQL Server's transaction-owned application lock serializes equal ticket
     * codes without adding a production schema migration.
     */
    @Override
    public int createRefundAppeal(UserAppeal appeal) {
        if (appeal == null || appeal.getUserId() <= 0
                || appeal.getTicketCode() == null || appeal.getTicketCode().isBlank()) {
            throw new BookingException(400, "Mã vé xin hoàn tiền không hợp lệ.");
        }
        String requestedTicket = appeal.getTicketCode().trim();
        int appealWindowHours = SettingsReader.readInt(
                "refund.appealWindowHours", 24, 1, 168);
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                acquireTransactionLock(connection, refundLockResource(requestedTicket));
                RefundOrderSnapshot order = lockOwnedRefundOrder(
                        connection, requestedTicket, appeal.getUserId(), appealWindowHours);
                validateRefundCandidate(order);
                if (findPendingByTicketCode(connection, order.ticketCode()).isPresent()) {
                    throw new BookingException(409,
                            "Mã vé này đã có yêu cầu hoàn tiền đang chờ xử lý.");
                }

                appeal.setTicketCode(order.ticketCode());
                appeal.setEmail(order.email());
                appeal.setOrderId(order.orderId());
                appeal.setCinemaId(order.cinemaId());
                appeal.setCinemaName(order.cinemaName());
                appeal.setFilmTitle(order.filmTitle());
                appeal.setOrderTotalAmount(order.totalAmount());
                appeal.setOrderPaymentStatus(order.paymentStatus());
                appeal.setOrderStatus(order.orderStatus());
                appeal.setShowtimeStartTime(order.startTime());
                appeal.setShowtimeEndTime(order.endTime());
                int appealId = create(connection, appeal);
                connection.commit();
                return appealId;
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (BookingException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tạo yêu cầu hoàn tiền.", ex);
        }
    }

    @Override
    public Optional<UserAppeal> findById(int id) {
        try (Connection connection = getConnection()) {
            return findById(connection, id, false);
        } catch (SQLException ex) {
            throw new DaoException("Cannot find appeal by id", ex);
        }
    }

    @Override
    public Optional<UserAppeal> findByIdForUpdate(Connection connection, int id) {
        return findById(connection, id, true);
    }

    private Optional<UserAppeal> findById(Connection connection, int id, boolean forUpdate) {
        String sql = selectPrefix(forUpdate) + " WHERE a.Id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot find appeal by id", ex);
        }
    }

    @Override
    public Optional<UserAppeal> findPendingByUserId(int userId) {
        String sql = selectPrefix(false) + """
                 WHERE a.UserId = ? AND a.Status = 'pending'
                   AND NULLIF(LTRIM(RTRIM(a.TicketCode)), '') IS NULL
                """;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot find pending account appeal for user", ex);
        }
    }

    @Override
    public Optional<UserAppeal> findPendingByTicketCode(String ticketCode) {
        if (ticketCode == null || ticketCode.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = getConnection()) {
            return findPendingByTicketCode(connection, ticketCode.trim());
        } catch (SQLException ex) {
            throw new DaoException("Cannot find pending refund appeal", ex);
        }
    }

    private Optional<UserAppeal> findPendingByTicketCode(Connection connection, String ticketCode) {
        String sql = selectPrefix(true) + """
                 WHERE a.TicketCode = ? AND a.Status = 'pending'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ticketCode);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot find pending refund appeal", ex);
        }
    }

    @Override
    public List<UserAppeal> findAll(String status) {
        return findAll(status, null);
    }

    /**
     * When cinemaId is present, the tenant predicate is part of SQL, before any
     * row or PII leaves the database.
     */
    @Override
    public List<UserAppeal> findAll(String status, Integer cinemaId) {
        StringBuilder sql = new StringBuilder(selectPrefix(false))
                .append(" WHERE a.AppealType IN ('account','refund')");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND a.Status = ?");
            params.add(status.trim());
        }
        if (cinemaId != null) {
            sql.append("""
                     AND a.CinemaId=?
                     AND (a.AppealType='refund'
                          OR (a.AppealType='account' AND u.Role IN ('manager','staff')))
                    """);
            params.add(cinemaId);
        }
        sql.append(" ORDER BY a.CreatedAt DESC, a.Id DESC");

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < params.size(); index++) {
                statement.setObject(index + 1, params.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                List<UserAppeal> appeals = new ArrayList<>();
                while (result.next()) {
                    appeals.add(map(result));
                }
                return appeals;
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot list appeals", ex);
        }
    }

    @Override
    public boolean updateStatus(int appealId, String status, String adminResponse,
            Integer resolvedByUserId) {
        try (Connection connection = getConnection()) {
            return updateStatus(connection, appealId, null, status, adminResponse, resolvedByUserId);
        } catch (SQLException ex) {
            throw new DaoException("Cannot update appeal status", ex);
        }
    }

    @Override
    public boolean updateStatus(Connection connection, int appealId, String expectedStatus,
            String status, String adminResponse, Integer resolvedByUserId) {
        String sql = """
                UPDATE UserAppeals
                SET Status = ?, AdminResponse = ?, ResolvedByUserId = ?, ResolvedAt = GETDATE()
                WHERE Id = ?
                """ + (expectedStatus == null ? "" : " AND Status = ?");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, adminResponse);
            if (resolvedByUserId == null) {
                statement.setNull(3, java.sql.Types.INTEGER);
            } else {
                statement.setInt(3, resolvedByUserId);
            }
            statement.setInt(4, appealId);
            if (expectedStatus != null) {
                statement.setString(5, expectedStatus);
            }
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DaoException("Cannot update appeal status", ex);
        }
    }

    private RefundOrderSnapshot lockOwnedRefundOrder(Connection connection, String ticketCode,
            int userId, int appealWindowHours) throws SQLException {
        String sql = """
                SELECT o.Id AS AppealOrderId, o.TicketCode, o.PaymentStatus, o.OrderStatus,
                       o.TotalAmount, o.RedeemedAt, o.RefundedAt, o.RefundRejectedAt,
                       s.CinemaId AS AppealCinemaId, s.StartTime AS AppealStartTime,
                       s.EndTime AS AppealEndTime, c.Name AS AppealCinemaName,
                       f.Title AS AppealFilmTitle, u.Email AS AppealUserEmail,
                       CASE WHEN s.EndTime IS NOT NULL AND s.EndTime <= GETDATE()
                            THEN 1 ELSE 0 END AS ShowtimeEnded,
                       CASE WHEN s.EndTime IS NOT NULL
                                  AND GETDATE() <= DATEADD(HOUR, ?, s.EndTime)
                            THEN 1 ELSE 0 END AS AppealWindowOpen
                FROM Orders o WITH (UPDLOCK, HOLDLOCK)
                JOIN Showtimes s ON s.Id=o.ShowtimeId
                JOIN Cinemas c ON c.Id=s.CinemaId
                JOIN Films f ON f.Id=s.FilmId
                JOIN Users u ON u.Id=o.UserId
                WHERE o.TicketCode=? AND o.UserId=?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, appealWindowHours);
            statement.setString(2, ticketCode);
            statement.setInt(3, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new BookingException(404, "Không tìm thấy mã vé thuộc tài khoản của bạn.");
                }
                return new RefundOrderSnapshot(
                        result.getInt("AppealOrderId"),
                        result.getString("TicketCode"),
                        result.getString("PaymentStatus"),
                        result.getString("OrderStatus"),
                        result.getBigDecimal("TotalAmount"),
                        result.getTimestamp("RedeemedAt"),
                        result.getTimestamp("RefundedAt"),
                        result.getTimestamp("RefundRejectedAt"),
                        result.getInt("AppealCinemaId"),
                        result.getString("AppealCinemaName"),
                        result.getString("AppealFilmTitle"),
                        result.getString("AppealUserEmail"),
                        toLocalDateTime(result.getTimestamp("AppealStartTime")),
                        toLocalDateTime(result.getTimestamp("AppealEndTime")),
                        result.getInt("ShowtimeEnded") == 1,
                        result.getInt("AppealWindowOpen") == 1);
            }
        }
    }

    private void validateRefundCandidate(RefundOrderSnapshot order) {
        if (order.refundedAt() != null || "refunded".equalsIgnoreCase(order.paymentStatus())) {
            throw new BookingException(409, "Vé này đã được hoàn tiền.");
        }
        if (order.refundRejectedAt() != null) {
            throw new BookingException(409, "Yêu cầu hoàn tiền cho vé này đã bị từ chối.");
        }
        if (order.redeemedAt() != null || "redeemed".equalsIgnoreCase(order.orderStatus())) {
            throw new BookingException(409, "Vé đã được check-in nên không thể xin hoàn tiền.");
        }
        if (!"paid".equalsIgnoreCase(order.paymentStatus())) {
            throw new BookingException(409, "Chỉ vé đã thanh toán mới được xin hoàn tiền.");
        }
        if (!"confirmed".equalsIgnoreCase(order.orderStatus())) {
            throw new BookingException(409, "Vé không còn ở trạng thái đã xác nhận.");
        }
        if (!order.showtimeEnded()) {
            throw new BookingException(409, "Chỉ gửi yêu cầu sau khi suất chiếu đã kết thúc.");
        }
        if (!order.appealWindowOpen()) {
            throw new BookingException(409,
                    "Bạn không đủ điều kiện hoàn tiền vì đã vượt quá 1 ngày gửi kháng cáo!");
        }
    }

    private void acquireTransactionLock(Connection connection, String resource) throws SQLException {
        // SQL Server's implicit transaction does not start for sp_getapplock itself.
        // Touching a table (TOP 0 is enough) activates the transaction without taking
        // a business-row lock, so LockOwner='Transaction' is valid and is released by
        // the surrounding commit/rollback.
        try (Statement transactionStarter = connection.createStatement()) {
            transactionStarter.execute("SELECT TOP (0) Id FROM UserAppeals");
        }
        String sql = """
                SET NOCOUNT ON;
                DECLARE @lockResult INT;
                EXEC @lockResult=sys.sp_getapplock
                     @Resource=?, @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=?;
                SELECT @lockResult AS LockResult;
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, resource);
            statement.setInt(2, APPEAL_LOCK_TIMEOUT_MS);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt("LockResult") < 0) {
                    throw new BookingException(503,
                            "Hệ thống đang xử lý một yêu cầu khác cho mã vé này. Vui lòng thử lại.");
                }
            }
        }
    }

    private String refundLockResource(String ticketCode) {
        return "cinebook:refund-appeal:"
                + ticketCode.trim().toUpperCase(Locale.ROOT);
    }

    private String selectPrefix(boolean forUpdate) {
        String hint = forUpdate ? " WITH (UPDLOCK, HOLDLOCK)" : "";
        return """
                SELECT a.*, u.FullName AS UserFullName, u.Role AS UserRole, u.LockReason, u.WarningCount,
                       o.Id AS AppealOrderId, o.TotalAmount AS AppealOrderTotal,
                       o.PaymentStatus AS AppealPaymentStatus, o.OrderStatus AS AppealOrderStatus,
                       a.CinemaId AS AppealCinemaId, c.Name AS AppealCinemaName,
                       f.Title AS AppealFilmTitle, s.StartTime AS AppealStartTime,
                       s.EndTime AS AppealEndTime
                FROM UserAppeals a%s
                JOIN Users u ON u.Id = a.UserId
                LEFT JOIN Orders o ON o.Id=a.OrderId
                LEFT JOIN Showtimes s ON s.Id=o.ShowtimeId
                LEFT JOIN Cinemas c ON c.Id=a.CinemaId
                LEFT JOIN Films f ON f.Id=s.FilmId
                """.formatted(hint);
    }

    private UserAppeal map(ResultSet result) throws SQLException {
        UserAppeal appeal = new UserAppeal();
        appeal.setId(result.getInt("Id"));
        appeal.setUserId(result.getInt("UserId"));
        appeal.setEmail(result.getString("Email"));
        appeal.setReason(result.getString("Reason"));
        appeal.setStatus(result.getString("Status"));
        appeal.setAdminResponse(result.getString("AdminResponse"));
        int resolvedBy = result.getInt("ResolvedByUserId");
        if (!result.wasNull()) {
            appeal.setResolvedByUserId(resolvedBy);
        }
        appeal.setTicketCode(result.getString("TicketCode"));
        appeal.setBankAccountInfo(result.getString("BankAccountInfo"));
        appeal.setAppealType(result.getString("AppealType"));
        appeal.setUserFullName(result.getString("UserFullName"));
        appeal.setUserRole(result.getString("UserRole"));
        appeal.setLockReason(result.getString("LockReason"));
        appeal.setWarningCount(result.getInt("WarningCount"));
        appeal.setCreatedAt(toLocalDateTime(result.getTimestamp("CreatedAt")));
        appeal.setResolvedAt(toLocalDateTime(result.getTimestamp("ResolvedAt")));
        if (hasColumn(result, "AppealOrderId")) {
            int orderId = result.getInt("AppealOrderId");
            appeal.setOrderId(result.wasNull() ? null : orderId);
            int cinemaId = result.getInt("AppealCinemaId");
            appeal.setCinemaId(result.wasNull() ? null : cinemaId);
            appeal.setCinemaName(result.getString("AppealCinemaName"));
            appeal.setFilmTitle(result.getString("AppealFilmTitle"));
            appeal.setOrderTotalAmount(result.getBigDecimal("AppealOrderTotal"));
            appeal.setOrderPaymentStatus(result.getString("AppealPaymentStatus"));
            appeal.setOrderStatus(result.getString("AppealOrderStatus"));
            appeal.setShowtimeStartTime(toLocalDateTime(result.getTimestamp("AppealStartTime")));
            appeal.setShowtimeEndTime(toLocalDateTime(result.getTimestamp("AppealEndTime")));
        }
        return appeal;
    }

    private boolean hasColumn(ResultSet result, String column) throws SQLException {
        for (int index = 1; index <= result.getMetaData().getColumnCount(); index++) {
            if (column.equalsIgnoreCase(result.getMetaData().getColumnLabel(index))) {
                return true;
            }
        }
        return false;
    }

    private static java.time.LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record RefundOrderSnapshot(
            int orderId,
            String ticketCode,
            String paymentStatus,
            String orderStatus,
            java.math.BigDecimal totalAmount,
            Timestamp redeemedAt,
            Timestamp refundedAt,
            Timestamp refundRejectedAt,
            int cinemaId,
            String cinemaName,
            String filmTitle,
            String email,
            java.time.LocalDateTime startTime,
            java.time.LocalDateTime endTime,
            boolean showtimeEnded,
            boolean appealWindowOpen) {
    }
}

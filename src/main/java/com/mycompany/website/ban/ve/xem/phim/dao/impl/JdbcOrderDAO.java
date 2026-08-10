package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.dao.BaseDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.OrderDAO;
import com.mycompany.website.ban.ve.xem.phim.model.OrderComboItem;
import com.mycompany.website.ban.ve.xem.phim.model.OrderHoldStatus;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.OrderSeatItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JdbcOrderDAO extends BaseDAO implements OrderDAO {
    private static final Logger LOGGER = Logger.getLogger(JdbcOrderDAO.class.getName());

    @Override
    public int insertDraftOrder(Connection connection, OrderRecord order) {
        String sql = """
                INSERT INTO Orders (
                    UserId, ShowtimeId, PromotionId, SeatSubtotal, ComboSubtotal, DiscountAmount, TotalAmount,
                    TicketCode, TicketQrUrl, PaymentMethod, PaymentStatus, TransactionId, PayRedirectUrl, OrderStatus,
                    PaymentProvider, IdempotencyKey, CounterExpiresAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, order.getUserId());
            ps.setInt(2, order.getShowtimeId());
            if (order.getPromotionId() == null) {
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(3, order.getPromotionId());
            }
            ps.setBigDecimal(4, order.getSeatSubtotal());
            ps.setBigDecimal(5, order.getComboSubtotal());
            ps.setBigDecimal(6, order.getDiscountAmount());
            ps.setBigDecimal(7, order.getTotalAmount());
            ps.setString(8, order.getTicketCode());
            ps.setString(9, order.getTicketQrUrl());
            ps.setString(10, order.getPaymentMethod());
            ps.setString(11, order.getPaymentStatus());
            ps.setString(12, order.getTransactionId());
            ps.setString(13, order.getPayRedirectUrl());
            ps.setString(14, order.getOrderStatus());
            ps.setString(15, order.getPaymentProvider());
            ps.setString(16, order.getIdempotencyKey());
            if (order.getCounterExpiresAt() == null) {
                ps.setNull(17, java.sql.Types.TIMESTAMP);
            } else {
                ps.setTimestamp(17, Timestamp.valueOf(order.getCounterExpiresAt()));
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            throw new DaoException("Cannot create order id", null);
        } catch (SQLException ex) {
            throw new DaoException("Cannot create draft order", ex);
        }
    }

    @Override
    public void insertOrderSeats(Connection connection, int orderId, List<OrderSeatItem> seats) {
        String sql = "INSERT INTO OrderSeats (OrderId, ShowtimeSeatId, SeatKey, SeatType, UnitPrice) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (OrderSeatItem seat : seats) {
                ps.setInt(1, orderId);
                ps.setInt(2, seat.getShowtimeSeatId());
                ps.setString(3, seat.getSeatKey());
                ps.setString(4, seat.getSeatType());
                ps.setBigDecimal(5, seat.getUnitPrice());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException ex) {
            throw new DaoException("Cannot create order seats", ex);
        }
    }

    @Override
    public void insertOrderCombos(Connection connection, int orderId, List<OrderComboItem> combos) {
        if (combos.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO OrderComboFoods (OrderId, ComboFoodId, Quantity, UnitPrice) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (OrderComboItem combo : combos) {
                ps.setInt(1, orderId);
                ps.setInt(2, combo.getComboFoodId());
                ps.setInt(3, combo.getQuantity());
                ps.setBigDecimal(4, combo.getUnitPrice());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException ex) {
            throw new DaoException("Cannot create order combo foods", ex);
        }
    }

    @Override
    public Optional<OrderRecord> findPendingOrderForUpdate(Connection connection, int orderId, int userId) {
        String sql = """
                SELECT *
                FROM Orders WITH (UPDLOCK, HOLDLOCK)
                WHERE Id = ? AND UserId = ? AND PaymentStatus = 'pending' AND OrderStatus IN ('created', 'pending')
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapOrder(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot lock pending order", ex);
        }
    }

    @Override
    public void markPaid(Connection connection, int orderId, String ticketCode, String ticketQrUrl, String transactionId) {
        markPaid(connection, orderId, ticketCode, ticketQrUrl, transactionId, "simulated");
    }

    @Override
    public void markPaid(Connection connection, int orderId, String ticketCode, String ticketQrUrl, String transactionId, String paymentProvider) {
        String sql = """
                UPDATE Orders
                SET TicketCode = ?, TicketQrUrl = ?, PaymentStatus = 'paid', OrderStatus = 'confirmed',
                    TransactionId = ?, PaymentProvider = ?, UpdatedAt = GETDATE()
                WHERE Id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ticketCode);
            ps.setString(2, ticketQrUrl);
            ps.setString(3, transactionId);
            ps.setString(4, paymentProvider);
            ps.setInt(5, orderId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Cannot mark order paid", ex);
        }
    }

    @Override
    public Optional<OrderRecord> findByIdempotencyKey(Connection connection, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM Orders WHERE IdempotencyKey = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapOrder(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot find order by idempotency key", ex);
        }
    }

    @Override
    public Optional<OrderRecord> findByIdempotencyKey(String idempotencyKey) {
        try (Connection connection = getConnection()) {
            return findByIdempotencyKey(connection, idempotencyKey);
        } catch (SQLException ex) {
            throw new DaoException("Cannot find order by idempotency key", ex);
        }
    }

    @Override
    public Optional<OrderRecord> findByIdAndUserId(Connection connection, int orderId, int userId) {
        String sql = "SELECT * FROM Orders WHERE Id = ? AND UserId = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapOrder(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load order by id and owner", ex);
        }
    }

    @Override
    public boolean applyIdempotencyKey(Connection connection, int orderId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return false;
        }
        String sql = "UPDATE Orders SET IdempotencyKey = ? WHERE Id = ? AND IdempotencyKey IS NULL";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, idempotencyKey);
            ps.setInt(2, orderId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DaoException("Cannot store payment idempotency key", ex);
        }
    }

    @Override
    public void deleteOrderCombos(Connection connection, int orderId) {
        String sql = "DELETE FROM OrderComboFoods WHERE OrderId = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Cannot delete order combos", ex);
        }
    }

    @Override
    public void updateOrderFinancials(Connection connection, int orderId, java.math.BigDecimal seatSubtotal, java.math.BigDecimal comboSubtotal, java.math.BigDecimal discountAmount, java.math.BigDecimal totalAmount, Integer promotionId, String paymentMethod) {
        String sql = """
                UPDATE Orders
                SET SeatSubtotal = ?, ComboSubtotal = ?, DiscountAmount = ?, TotalAmount = ?, PromotionId = ?, PaymentMethod = ?, UpdatedAt = GETDATE()
                WHERE Id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBigDecimal(1, seatSubtotal);
            ps.setBigDecimal(2, comboSubtotal);
            ps.setBigDecimal(3, discountAmount);
            ps.setBigDecimal(4, totalAmount);
            if (promotionId == null) {
                ps.setNull(5, java.sql.Types.INTEGER);
            } else {
                ps.setInt(5, promotionId);
            }
            ps.setString(6, paymentMethod);
            ps.setInt(7, orderId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Cannot update order financials", ex);
        }
    }

    @Override
    public void confirmCounterOrder(Connection connection, int orderId, String ticketCode, String ticketQrUrl,
            int counterExpiryMinutes) {
        String sql = """
                UPDATE Orders
                SET TicketCode = ?, TicketQrUrl = ?, PaymentStatus = 'pending', OrderStatus = 'confirmed',
                    CounterExpiresAt = DATEADD(MINUTE, ?, GETDATE()), UpdatedAt = GETDATE()
                WHERE Id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ticketCode);
            ps.setString(2, ticketQrUrl);
            ps.setInt(3, counterExpiryMinutes);
            ps.setInt(4, orderId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Cannot confirm counter order", ex);
        }
    }

    @Override
    public List<OrderRecord> findHistoryByUserId(int userId) {
        String sql = """
                SELECT o.*, f.Title AS FilmTitle, f.DurationMinutes, c.Name AS CinemaName,
                       r.Name AS RoomName, s.StartTime, s.EndTime, GETDATE() AS BusinessNow,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM UserAppeals ua
                           WHERE ua.OrderId=o.Id AND ua.AppealType='refund' AND ua.Status='pending'
                       ) THEN 1 ELSE 0 END AS HasPendingRefundAppeal
                FROM Orders o
                JOIN Showtimes s ON s.Id = o.ShowtimeId
                JOIN Films f ON f.Id = s.FilmId
                JOIN Cinemas c ON c.Id = s.CinemaId
                JOIN Rooms r ON r.Id = s.RoomId
                WHERE o.UserId = ? AND ISNULL(o.IsUserHidden, 0) = 0
                ORDER BY o.CreatedAt DESC
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            List<OrderRecord> orders = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapOrder(rs));
                }
            }
            // F-007 (cung lop loi): truoc day moi don ban them 2 query, mot khach co 200 don thi
            // trang lich su ban 401 query. Nap ghe va combo cua ca danh sach bang 2 query.
            loadChildrenForAll(connection, orders);
            return orders;
        } catch (SQLException ex) {
            throw new DaoException("Cannot find history by user id", ex);
        }
    }

    @Override
    public void hideOrderFromUserHistory(int orderId, int userId) {
        String sql = "UPDATE Orders SET IsUserHidden = 1 WHERE Id = ? AND UserId = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, userId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new DaoException("Không tìm thấy đơn hàng.", null);
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot hide order from user history", ex);
        }
    }

    @Override
    public void hideBatchOrdersFromUserHistory(List<Integer> orderIds, int userId) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        StringBuilder sql = new StringBuilder("UPDATE Orders SET IsUserHidden = 1 WHERE UserId = ? AND Id IN (");
        for (int i = 0; i < orderIds.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(")");
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, userId);
            for (int i = 0; i < orderIds.size(); i++) {
                ps.setInt(i + 2, orderIds.get(i));
            }
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Cannot batch hide orders from user history", ex);
        }
    }

    @Override
    public void hideAllHistoryOrdersFromUserHistory(int userId) {
        String sql = """
                UPDATE Orders
                SET IsUserHidden = 1
                WHERE UserId = ? AND ISNULL(IsUserHidden, 0) = 0
                  AND Id IN (
                      SELECT o.Id FROM Orders o
                      JOIN Showtimes s ON s.Id = o.ShowtimeId
                      WHERE o.UserId = ?
                        AND (o.OrderStatus IN ('redeemed', 'cancelled')
                             OR s.EndTime < GETDATE()
                             OR (o.PaymentStatus = 'pending' AND o.OrderStatus != 'cancelled'))
                  )
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Cannot hide all history orders from user history", ex);
        }
    }

    /** Nap ghe va combo cho toan bo danh sach don bang dung 2 query, khong phai 2 query moi don. */
    private void loadChildrenForAll(Connection connection, List<OrderRecord> orders) throws SQLException {
        if (orders.isEmpty()) {
            return;
        }
        Map<Integer, OrderRecord> byId = new LinkedHashMap<>();
        orders.forEach(order -> byId.put(order.getId(), order));
        String placeholders = String.join(",", java.util.Collections.nCopies(byId.size(), "?"));

        String seatSql = "SELECT * FROM OrderSeats WHERE OrderId IN (" + placeholders + ") ORDER BY OrderId, SeatKey";
        try (PreparedStatement ps = connection.prepareStatement(seatSql)) {
            bindIds(ps, byId.keySet());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderRecord owner = byId.get(rs.getInt("OrderId"));
                    if (owner == null) {
                        continue;
                    }
                    OrderSeatItem seat = new OrderSeatItem();
                    seat.setShowtimeSeatId(rs.getInt("ShowtimeSeatId"));
                    seat.setSeatKey(rs.getString("SeatKey"));
                    seat.setSeatType(rs.getString("SeatType"));
                    seat.setUnitPrice(rs.getBigDecimal("UnitPrice"));
                    owner.getSeats().add(seat);
                }
            }
        }

        String comboSql = """
                SELECT ocf.*, cf.Name
                FROM OrderComboFoods ocf
                JOIN ComboFoods cf ON cf.Id = ocf.ComboFoodId
                WHERE ocf.OrderId IN (""" + placeholders + ") ORDER BY ocf.OrderId, cf.Name";
        try (PreparedStatement ps = connection.prepareStatement(comboSql)) {
            bindIds(ps, byId.keySet());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderRecord owner = byId.get(rs.getInt("OrderId"));
                    if (owner == null) {
                        continue;
                    }
                    OrderComboItem combo = new OrderComboItem();
                    combo.setComboFoodId(rs.getInt("ComboFoodId"));
                    combo.setComboName(rs.getString("Name"));
                    combo.setQuantity(rs.getInt("Quantity"));
                    combo.setUnitPrice(rs.getBigDecimal("UnitPrice"));
                    owner.getCombos().add(combo);
                }
            }
        }
    }

    private void bindIds(PreparedStatement ps, Iterable<Integer> ids) throws SQLException {
        int index = 1;
        for (Integer id : ids) {
            ps.setInt(index++, id);
        }
    }

    // F-007: da xoa findAllOrdersForAdmin(). Ham do doc toan bo bang Orders khong phan trang roi
    // ban them hai query cho tung don (N+1), nhung khong con caller nao trong ca src/main va src/test
    // — /admin/orders da chuyen sang AdminService.listOrdersForAdmin(page, size, ...) tu P16, con
    // dashboard dung countOrdersForAdmin(). Giu lai chi de lai mot cai bay hieu nang cho nguoi sau.

    @Override
    public void redeemTicketByCode(String ticketCode) {
        if (directMutationDisabled()) {
            throw transitionOnly("redeemTicketByCode");
        }
        String sql = """
                UPDATE Orders
                SET OrderStatus = 'redeemed', RedeemedAt = GETDATE(), UpdatedAt = GETDATE()
                WHERE TicketCode = ?
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ticketCode);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new DaoException("Không tìm thấy đơn vé với mã ticket code: " + ticketCode, new SQLException("Order not found"));
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot redeem ticket by code", ex);
        }
    }

    @Override
    public void cancelOrderById(int orderId) {
        if (directMutationDisabled()) {
            throw transitionOnly("cancelOrderById");
        }
        String sql = """
                UPDATE Orders
                SET OrderStatus = 'cancelled', UpdatedAt = GETDATE()
                WHERE Id = ?
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Cannot cancel order", ex);
        }
    }

    @Override
    public Optional<OrderRecord> findById(int id) {
        String sql = """
                SELECT o.*, f.Title AS FilmTitle, f.DurationMinutes, c.Name AS CinemaName,
                       r.Name AS RoomName, s.StartTime, s.EndTime, GETDATE() AS BusinessNow,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM UserAppeals ua
                           WHERE ua.OrderId=o.Id AND ua.AppealType='refund' AND ua.Status='pending'
                       ) THEN 1 ELSE 0 END AS HasPendingRefundAppeal
                FROM Orders o
                LEFT JOIN Showtimes s ON s.Id = o.ShowtimeId
                LEFT JOIN Films f ON f.Id = s.FilmId
                LEFT JOIN Cinemas c ON c.Id = s.CinemaId
                LEFT JOIN Rooms r ON r.Id = s.RoomId
                WHERE o.Id = ?
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                OrderRecord order = mapOrder(rs);
                order.getSeats().addAll(findSeatsByOrderId(order.getId()));
                order.getCombos().addAll(findCombosByOrderId(order.getId()));
                return Optional.of(order);
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load order by id", ex);
        }
    }

    @Override
    public List<OrderSeatItem> findSeatsByOrderId(int orderId) {
        try (Connection connection = getConnection()) {
            return findSeatsByOrderId(connection, orderId);
        } catch (SQLException ex) {
            throw new DaoException("Cannot load order seats", ex);
        }
    }

    @Override
    public List<OrderSeatItem> findSeatsByOrderId(Connection connection, int orderId) {
        String sql = "SELECT * FROM OrderSeats WHERE OrderId = ? ORDER BY SeatKey";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                List<OrderSeatItem> seats = new ArrayList<>();
                while (rs.next()) {
                    OrderSeatItem seat = new OrderSeatItem();
                    seat.setShowtimeSeatId(rs.getInt("ShowtimeSeatId"));
                    seat.setSeatKey(rs.getString("SeatKey"));
                    seat.setSeatType(rs.getString("SeatType"));
                    seat.setUnitPrice(rs.getBigDecimal("UnitPrice"));
                    seats.add(seat);
                }
                return seats;
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load order seats", ex);
        }
    }

    @Override
    public List<OrderComboItem> findCombosByOrderId(int orderId) {
        try (Connection connection = getConnection()) {
            return findCombosByOrderId(connection, orderId);
        } catch (SQLException ex) {
            throw new DaoException("Cannot load order combos", ex);
        }
    }

    @Override
    public List<OrderComboItem> findCombosByOrderId(Connection connection, int orderId) {
        String sql = """
                SELECT ocf.*, cf.Name
                FROM OrderComboFoods ocf
                JOIN ComboFoods cf ON cf.Id = ocf.ComboFoodId
                WHERE ocf.OrderId = ?
                ORDER BY cf.Name
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                List<OrderComboItem> combos = new ArrayList<>();
                while (rs.next()) {
                    OrderComboItem combo = new OrderComboItem();
                    combo.setComboFoodId(rs.getInt("ComboFoodId"));
                    combo.setComboName(rs.getString("Name"));
                    combo.setQuantity(rs.getInt("Quantity"));
                    combo.setUnitPrice(rs.getBigDecimal("UnitPrice"));
                    combos.add(combo);
                }
                return combos;
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load order combos", ex);
        }
    }

    @Override
    public Optional<OrderRecord> findByTicketCodeAndUserId(String ticketCode, int userId) {
        String sql = """
                SELECT o.*, f.Title AS FilmTitle, f.DurationMinutes, c.Name AS CinemaName,
                       r.Name AS RoomName, s.StartTime, s.EndTime, GETDATE() AS BusinessNow
                FROM Orders o
                JOIN Showtimes s ON s.Id = o.ShowtimeId
                JOIN Films f ON f.Id = s.FilmId
                JOIN Cinemas c ON c.Id = s.CinemaId
                JOIN Rooms r ON r.Id = s.RoomId
                WHERE o.TicketCode = ? AND o.UserId = ?
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ticketCode);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                OrderRecord order = mapOrder(rs);
                order.getSeats().addAll(findSeatsByOrderId(order.getId()));
                order.getCombos().addAll(findCombosByOrderId(order.getId()));
                return Optional.of(order);
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load order by ticket code", ex);
        }
    }

    @Override
    public Optional<OrderRecord> findByTicketCode(String ticketCode) {
        String sql = """
                SELECT o.*, f.Title AS FilmTitle, f.DurationMinutes, c.Name AS CinemaName, r.Name AS RoomName, s.StartTime, s.EndTime,
                       s.CinemaId AS ShowtimeCinemaId,
                       u.Email AS UserEmail, u.FullName AS UserFullName,
                       GETDATE() AS BusinessNow
                FROM Orders o
                LEFT JOIN Showtimes s ON s.Id = o.ShowtimeId
                LEFT JOIN Films f ON f.Id = s.FilmId
                LEFT JOIN Cinemas c ON c.Id = s.CinemaId
                LEFT JOIN Rooms r ON r.Id = s.RoomId
                LEFT JOIN Users u ON u.Id = o.UserId
                WHERE o.TicketCode = ?
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ticketCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                OrderRecord order = mapOrder(rs);
                order.setUserEmail(rs.getString("UserEmail"));
                order.setUserFullName(rs.getString("UserFullName"));
                order.getSeats().addAll(findSeatsByOrderId(order.getId()));
                order.getCombos().addAll(findCombosByOrderId(order.getId()));
                return Optional.of(order);
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load order by ticket code", ex);
        }
    }

    private OrderRecord mapOrder(ResultSet rs) throws SQLException {
        OrderRecord order = new OrderRecord();
        order.setId(rs.getInt("Id"));
        order.setUserId(rs.getInt("UserId"));
        order.setShowtimeId(rs.getInt("ShowtimeId"));
        int promotionId = rs.getInt("PromotionId");
        order.setPromotionId(rs.wasNull() ? null : promotionId);
        order.setSeatSubtotal(rs.getBigDecimal("SeatSubtotal"));
        order.setComboSubtotal(rs.getBigDecimal("ComboSubtotal"));
        order.setDiscountAmount(rs.getBigDecimal("DiscountAmount"));
        order.setTotalAmount(rs.getBigDecimal("TotalAmount"));
        order.setTicketCode(rs.getString("TicketCode"));
        order.setTicketQrUrl(rs.getString("TicketQrUrl"));
        order.setPaymentMethod(rs.getString("PaymentMethod"));
        order.setPaymentStatus(rs.getString("PaymentStatus"));
        order.setTransactionId(rs.getString("TransactionId"));
        order.setPayRedirectUrl(rs.getString("PayRedirectUrl"));
        order.setOrderStatus(rs.getString("OrderStatus"));
        order.setRedeemedAt(toLocalDateTime(rs.getTimestamp("RedeemedAt")));
        order.setCreatedAt(toLocalDateTime(rs.getTimestamp("CreatedAt")));
        order.setUpdatedAt(toLocalDateTime(rs.getTimestamp("UpdatedAt")));
        optionalSet(order::setFilmTitle, rs, "FilmTitle");
        optionalSet(order::setCinemaName, rs, "CinemaName");
        optionalSetInteger(order::setCinemaId, rs, "ShowtimeCinemaId");
        optionalSet(order::setRoomName, rs, "RoomName");
        optionalSetTimestamp(order::setStartTime, rs, "StartTime");
        optionalSetTimestamp(order::setEndTime, rs, "EndTime");
        optionalSetTimestamp(order::setBusinessNow, rs, "BusinessNow");
        optionalSetBoolean(order::setPendingRefundAppeal, rs, "HasPendingRefundAppeal");
        optionalSetInt(order::setDurationMinutes, rs, "DurationMinutes");
        optionalSet(order::setPaymentProvider, rs, "PaymentProvider");
        optionalSet(order::setIdempotencyKey, rs, "IdempotencyKey");
        optionalSetTimestamp(order::setCounterExpiresAt, rs, "CounterExpiresAt");
        optionalSetTimestamp(order::setRefundedAt, rs, "RefundedAt");
        optionalSetBigDecimal(order::setRefundAmount, rs, "RefundAmount");
        optionalSet(order::setRefundReason, rs, "RefundReason");
        optionalSetInteger(order::setRefundedBy, rs, "RefundedBy");
        optionalSetTimestamp(order::setRefundRejectedAt, rs, "RefundRejectedAt");
        optionalSet(order::setRefundRejectReason, rs, "RefundRejectReason");
        optionalSetTimestamp(order::setCancelledAt, rs, "CancelledAt");
        optionalSet(order::setCancelReason, rs, "CancelReason");
        try {
            byte[] version = rs.getBytes("StateVersion");
            if (version != null) {
                order.setStateVersion(java.util.HexFormat.of().formatHex(version));
            }
        } catch (SQLException compatibilityException) {
            // Compatibility with a pre-fix28 database during rolling migration.
            LOGGER.log(Level.FINEST, "StateVersion is unavailable before fix28", compatibilityException);
        }
        return order;
    }

    @Override
    public void updateOrderRefund(Connection connection, int orderId, java.math.BigDecimal refundAmount, String reason, int refundedBy) {
        if (directMutationDisabled()) {
            throw transitionOnly("updateOrderRefund");
        }
        String sql = """
                UPDATE Orders
                SET PaymentStatus = 'refunded', OrderStatus = 'cancelled',
                    RefundedAt = GETDATE(), RefundAmount = ?, RefundReason = ?, RefundedBy = ?,
                    CancelledAt = GETDATE(), CancelReason = ?, UpdatedAt = GETDATE()
                WHERE Id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBigDecimal(1, refundAmount);
            ps.setString(2, reason);
            ps.setInt(3, refundedBy);
            ps.setString(4, reason);
            ps.setInt(5, orderId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Cannot update order refund", ex);
        }
    }

    @Override
    public void updateOrderCancel(Connection connection, int orderId, String reason) {
        if (directMutationDisabled()) {
            throw transitionOnly("updateOrderCancel");
        }
        String sql = """
                UPDATE Orders
                SET OrderStatus = 'cancelled', CancelledAt = GETDATE(), CancelReason = ?, UpdatedAt = GETDATE()
                WHERE Id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Cannot update order cancel", ex);
        }
    }

    private boolean directMutationDisabled() {
        return true;
    }

    private DaoException transitionOnly(String method) {
        return new DaoException("Order mutation must go through BookingTransitionService: " + method,
                new IllegalStateException("direct order status write is disabled"));
    }

    @Override
    public Optional<OrderHoldStatus> findHoldStatus(int orderId, int userId) {
        // So giay con lai do SQL Server tinh (GETDATE()), khong dung gio may chu ung dung - quy tac chot o P03.
        // LEFT JOIN de don khong con ghe nao dang giu van tra ve duoc (heldSeatCount = 0 => het han).
        String sql = """
                SELECT o.Id AS OrderId,
                       o.OrderStatus,
                       o.PaymentStatus,
                       MIN(ss.HeldUntil) AS HeldUntil,
                       ISNULL(DATEDIFF(SECOND, GETDATE(), MIN(ss.HeldUntil)), 0) AS RemainingSeconds,
                       COUNT(ss.Id) AS HeldSeatCount
                FROM Orders o
                LEFT JOIN OrderSeats os ON os.OrderId = o.Id
                LEFT JOIN ShowtimeSeats ss ON ss.Id = os.ShowtimeSeatId
                     AND ss.Status = 'held'
                     AND ss.HeldByUserId = o.UserId
                WHERE o.Id = ? AND o.UserId = ?
                GROUP BY o.Id, o.OrderStatus, o.PaymentStatus
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                OrderHoldStatus status = new OrderHoldStatus();
                status.setOrderId(rs.getInt("OrderId"));
                status.setOrderStatus(rs.getString("OrderStatus"));
                status.setPaymentStatus(rs.getString("PaymentStatus"));
                Timestamp heldUntil = rs.getTimestamp("HeldUntil");
                status.setHeldUntil(heldUntil == null ? null : heldUntil.toLocalDateTime());
                status.setRemainingSeconds(rs.getInt("RemainingSeconds"));
                status.setHeldSeatCount(rs.getInt("HeldSeatCount"));
                return Optional.of(status);
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load order hold status", ex);
        }
    }

    private void optionalSet(java.util.function.Consumer<String> setter, ResultSet rs, String column) throws SQLException {
        if (hasColumn(rs, column)) {
            setter.accept(rs.getString(column));
        }
    }

    private void optionalSetBigDecimal(java.util.function.Consumer<java.math.BigDecimal> setter, ResultSet rs, String column) throws SQLException {
        if (hasColumn(rs, column)) {
            setter.accept(rs.getBigDecimal(column));
        }
    }

    private void optionalSetInteger(java.util.function.Consumer<Integer> setter, ResultSet rs, String column) throws SQLException {
        if (hasColumn(rs, column)) {
            int val = rs.getInt(column);
            if (!rs.wasNull()) {
                setter.accept(val);
            }
        }
    }

    private void optionalSetInt(java.util.function.IntConsumer setter, ResultSet rs, String column) throws SQLException {
        if (hasColumn(rs, column)) {
            int val = rs.getInt(column);
            if (!rs.wasNull()) {
                setter.accept(val);
            }
        }
    }

    private void optionalSetBoolean(java.util.function.Consumer<Boolean> setter, ResultSet rs, String column) throws SQLException {
        try {
            boolean value = rs.getBoolean(column);
            if (!rs.wasNull()) {
                setter.accept(value);
            }
        } catch (SQLException compatibilityException) {
            LOGGER.log(Level.FINEST, column + " is unavailable", compatibilityException);
        }
    }

    private void optionalSetTimestamp(java.util.function.Consumer<java.time.LocalDateTime> setter, ResultSet rs, String column) throws SQLException {
        if (hasColumn(rs, column)) {
            setter.accept(toLocalDateTime(rs.getTimestamp(column)));
        }
    }

    private boolean hasColumn(ResultSet rs, String column) throws SQLException {
        java.sql.ResultSetMetaData metadata = rs.getMetaData();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            if (column.equalsIgnoreCase(metadata.getColumnLabel(index))) {
                return true;
            }
        }
        return false;
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}

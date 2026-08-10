package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.NotificationRecipientDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.UserNotificationDAO;
import com.mycompany.website.ban.ve.xem.phim.model.UserNotification;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JdbcUserNotificationDAO implements UserNotificationDAO {

    @Override
    public int create(Connection connection, UserNotification notification) throws SQLException {
        String sql = """
                INSERT INTO UserNotifications
                    (Title, Message, Severity, TargetType, TargetId, CinemaId, ActionUrl,
                     VisibleFrom, VisibleUntil, Status, CreatedByUserId)
                VALUES (?, ?, ?, ?, ?, ?, ?,
                        COALESCE(?, SYSDATETIME()), ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, notification.getTitle());
            ps.setString(2, notification.getMessage());
            ps.setString(3, notification.getSeverity() == null ? "info" : notification.getSeverity());
            ps.setString(4, notification.getTargetType());
            ps.setString(5, notification.getTargetId());
            setNullableInt(ps, 6, notification.getCinemaId());
            ps.setString(7, notification.getActionUrl());
            setNullableTimestamp(ps, 8, notification.getVisibleFrom());
            setNullableTimestamp(ps, 9, notification.getVisibleUntil());
            ps.setString(10, notification.getStatus() == null ? "active" : notification.getStatus());
            ps.setInt(11, notification.getCreatedByUserId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Khong lay duoc Id cua thong bao vua tao");
                }
                return keys.getInt(1);
            }
        }
    }

    /**
     * Bon pham vi, bon truy van. Deu chi lay tai khoan {@code member} dang hoat dong: gui thong
     * bao khuyen mai vao hop thu cua nhan vien hay tai khoan bi khoa la nhieu, khong phai tinh nang.
     */
    @Override
    public Set<Integer> resolveRecipients(Connection connection, UserNotification notification)
            throws SQLException {
        String base = "SELECT u.Id FROM Users u WHERE u.Role = '" + AppConstants.ROLE_MEMBER
                + "' AND ISNULL(u.IsLocked, 0) = 0";
        String targetType = notification.getTargetType() == null
                ? UserNotification.TARGET_ALL : notification.getTargetType().toUpperCase();

        String sql;
        Object parameter = null;
        switch (targetType) {
            case UserNotification.TARGET_USER -> {
                sql = base + " AND u.Id = ?";
                parameter = Integer.valueOf(requireNumericTarget(notification));
            }
            case UserNotification.TARGET_TIER -> {
                sql = base + " AND UPPER(ISNULL(u.MembershipTier, 'BRONZE')) = UPPER(?)";
                parameter = notification.getTargetId();
            }
            case UserNotification.TARGET_CINEMA -> {
                // Khach cua mot rap = nguoi da tung dat ve mot suat chieu tai rap do.
                sql = base + """
                         AND EXISTS (
                             SELECT 1 FROM Orders o JOIN Showtimes s ON s.Id = o.ShowtimeId
                             WHERE o.UserId = u.Id AND s.CinemaId = ? AND o.OrderStatus <> 'cancelled'
                         )
                        """;
                parameter = Integer.valueOf(requireNumericTarget(notification));
            }
            case UserNotification.TARGET_ALL -> sql = base;
            default -> throw new IllegalArgumentException("Pham vi thong bao khong hop le: " + targetType);
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (parameter != null) {
                ps.setObject(1, parameter);
            }
            Set<Integer> recipients = new HashSet<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    recipients.add(rs.getInt(1));
                }
            }
            return recipients;
        }
    }

    @Override
    public List<UserNotification> inbox(int userId, boolean unreadOnly) {
        String sql = """
                SELECT n.*, r.ReadAt
                FROM NotificationRecipients r
                JOIN UserNotifications n ON n.Id = r.NotificationId
                WHERE r.SourceType = ? AND r.UserId = ?
                  AND n.Status = 'active'
                  AND n.VisibleFrom <= SYSDATETIME()
                  AND (n.VisibleUntil IS NULL OR n.VisibleUntil > SYSDATETIME())
                  AND (? = 0 OR r.ReadAt IS NULL)
                ORDER BY r.DeliveredAt DESC, n.Id DESC
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, NotificationRecipientDAO.SOURCE_USER);
            ps.setInt(2, userId);
            ps.setInt(3, unreadOnly ? 1 : 0);
            List<UserNotification> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException ex) {
            throw new DaoException("Khong the tai hop thu thong bao", ex);
        }
    }

    private int requireNumericTarget(UserNotification notification) {
        try {
            return Integer.parseInt(notification.getTargetId());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Pham vi " + notification.getTargetType() + " can TargetId la so nguyen", ex);
        }
    }

    private UserNotification map(ResultSet rs) throws SQLException {
        UserNotification notification = new UserNotification();
        notification.setId(rs.getInt("Id"));
        notification.setTitle(rs.getString("Title"));
        notification.setMessage(rs.getString("Message"));
        notification.setSeverity(rs.getString("Severity"));
        notification.setTargetType(rs.getString("TargetType"));
        notification.setTargetId(rs.getString("TargetId"));
        int cinemaId = rs.getInt("CinemaId");
        notification.setCinemaId(rs.wasNull() ? null : cinemaId);
        notification.setActionUrl(rs.getString("ActionUrl"));
        notification.setVisibleFrom(toLocal(rs.getTimestamp("VisibleFrom")));
        notification.setVisibleUntil(toLocal(rs.getTimestamp("VisibleUntil")));
        notification.setStatus(rs.getString("Status"));
        notification.setCreatedByUserId(rs.getInt("CreatedByUserId"));
        notification.setCreatedAt(toLocal(rs.getTimestamp("CreatedAt")));
        notification.setReadAt(toLocal(rs.getTimestamp("ReadAt")));
        return notification;
    }

    private static java.time.LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setNullableTimestamp(PreparedStatement ps, int index,
            java.time.LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(value));
        }
    }
}

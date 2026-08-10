package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.AdminNotificationDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.NotificationRecipientDAO;
import com.mycompany.website.ban.ve.xem.phim.model.AdminNotification;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class JdbcAdminNotificationDAO implements AdminNotificationDAO {

    @Override
    public List<AdminNotification> findAll() {
        String sql = "SELECT * FROM AdminNotifications ORDER BY IsRead ASC, CreatedAt DESC";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<AdminNotification> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapNotification(rs));
            }
            return list;
        } catch (SQLException ex) {
            throw new DaoException("Khong the tai danh sach thong bao", ex);
        }
    }

    @Override
    public List<AdminNotification> findUnread() {
        String sql = "SELECT * FROM AdminNotifications WHERE IsRead = 0 AND ResolvedAt IS NULL ORDER BY CreatedAt DESC";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<AdminNotification> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapNotification(rs));
            }
            return list;
        } catch (SQLException ex) {
            throw new DaoException("Khong the tai thong bao chua doc", ex);
        }
    }

    @Override
    public int countUnread() {
        String sql = "SELECT COUNT(*) FROM AdminNotifications WHERE IsRead = 0 AND ResolvedAt IS NULL";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException ex) {
            throw new DaoException("Khong the dem thong bao chua doc", ex);
        }
    }

    @Override
    public void markAsRead(int notificationId) {
        String sql = "UPDATE AdminNotifications SET IsRead = 1 WHERE Id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, notificationId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Khong the cap nhat thong bao", ex);
        }
    }

    @Override
    public void markAsReadByTarget(String targetType, String targetId) {
        if (targetType == null || targetId == null) {
            return;
        }
        String sql = "UPDATE AdminNotifications SET IsRead = 1 WHERE TargetType = ? AND TargetId = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, targetType);
            ps.setString(2, targetId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Khong the cap nhat thong bao theo target", ex);
        }
    }

    @Override
    public void resolveByTarget(String targetType, String targetId, int resolvedBy, String resolution) {
        if (targetType == null || targetId == null) {
            return;
        }
        String sql = "UPDATE AdminNotifications SET IsRead = 1, ResolvedAt = SYSDATETIME(), "
                + "ResolvedBy = ?, Resolution = ? WHERE TargetType = ? AND TargetId = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, resolvedBy);
            ps.setString(2, resolution);
            ps.setString(3, targetType);
            ps.setString(4, targetId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Khong the dong thong bao theo target", ex);
        }
    }

    @Override
    public void resolveByTarget(Connection connection, String targetType, String targetId,
            int resolvedBy, String resolution) throws SQLException {
        if (targetType == null || targetId == null) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE AdminNotifications SET IsRead=1, ResolvedAt=SYSDATETIME(), ResolvedBy=?, Resolution=? "
                        + "WHERE TargetType=? AND TargetId=?")) {
            ps.setInt(1, resolvedBy);
            ps.setString(2, resolution);
            ps.setString(3, targetType);
            ps.setString(4, targetId);
            ps.executeUpdate();
        }
    }

    @Override
    public void markAllAsRead() {
        String sql = "UPDATE AdminNotifications SET IsRead = 1 WHERE IsRead = 0";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Khong the cap nhat tat ca thong bao", ex);
        }
    }

    @Override
    public void createNotification(AdminNotification notification) {
        String sql = """
                INSERT INTO AdminNotifications
                    (Title,Message,Category,Severity,TargetType,TargetId,ActionUrl,IsRead,CreatedAt,
                     CinemaId,CreatedByUserId,EventType,ApprovalRequestId)
                VALUES (?,?,?,?,?,?,?,?,GETDATE(),?,?,?,?)
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, notification.getTitle());
            ps.setString(2, notification.getMessage());
            ps.setString(3, notification.getCategory() == null ? "room" : notification.getCategory());
            ps.setString(4, notification.getSeverity() == null ? "info" : notification.getSeverity());
            ps.setString(5, notification.getTargetType());
            ps.setString(6, notification.getTargetId());
            ps.setString(7, notification.getActionUrl());
            ps.setBoolean(8, notification.isRead());
            setNullableInt(ps, 9, notification.getCinemaId());
            setNullableInt(ps, 10, notification.getCreatedByUserId());
            ps.setString(11, notification.getEventType());
            setNullableInt(ps, 12, notification.getApprovalRequestId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Khong the tao thong bao", ex);
        }
    }

    @Override
    public boolean existsNotificationForTarget(String category, String targetType, String targetId) {
        String sql = "SELECT COUNT(*) FROM AdminNotifications WHERE Category = ? AND TargetType = ? AND TargetId = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, category);
            ps.setString(2, targetType);
            ps.setString(3, targetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
            return false;
        } catch (SQLException ex) {
            throw new DaoException("Khong the kiem tra thong bao qua khuu", ex);
        }
    }

    /**
     * Xoa canh bao tro toi mot doi tuong, KEM so nhan cua chung.
     *
     * <p><b>Loi da sua (N-09).</b> Ban cu chi chay {@code DELETE FROM AdminNotifications},
     * de lai toan bo dong {@code NotificationRecipients} tro toi cac Id vua bien mat.
     * {@code fix21_user_notifications.sql} da ghi ro ly do phai don tay: {@code NotificationId}
     * tro toi hai bang khac nhau tuy {@code SourceType} nen khong khai duoc FOREIGN KEY, va
     * khong co cascade lam thay. Day lai la duong sua RM-01 ({@code deleteRoom},
     * {@code deleteShowtime}, {@code deleteFilm} deu goi vao day), nen ban sua "het ban ghi
     * mo coi" chi doi cho mo coi xuong bang duoi.</p>
     *
     * <p>Hau qua cu the neu bo qua: {@code AdminNotifications.Id} la IDENTITY nen mot canh
     * bao MOI co the trung Id voi canh bao da xoa; so nhan cu con lai lam canh bao moi bi
     * coi la "da doc" ngay tu luc sinh ra.</p>
     *
     * <p>Thu tu bat buoc: xoa so nhan TRUOC, khi con doc duoc Id cua thong bao.</p>
     */
    @Override
    public int deleteByTarget(Connection connection, String targetType, String targetId) throws SQLException {
        String recipientSql = "DELETE nr FROM NotificationRecipients nr"
                + " WHERE nr.SourceType = ?"
                + " AND nr.NotificationId IN ("
                + "     SELECT n.Id FROM AdminNotifications n WHERE n.TargetType = ? AND n.TargetId = ?)";
        try (PreparedStatement ps = connection.prepareStatement(recipientSql)) {
            ps.setString(1, NotificationRecipientDAO.SOURCE_ADMIN);
            ps.setString(2, targetType);
            ps.setString(3, targetId);
            ps.executeUpdate();
        }
        String sql = "DELETE FROM AdminNotifications WHERE TargetType = ? AND TargetId = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, targetType);
            ps.setString(2, targetId);
            return ps.executeUpdate();
        }
    }

    private AdminNotification mapNotification(ResultSet rs) throws SQLException {
        AdminNotification n = new AdminNotification();
        n.setId(rs.getInt("Id"));
        n.setTitle(rs.getString("Title"));
        n.setMessage(rs.getString("Message"));
        n.setCategory(rs.getString("Category"));
        n.setSeverity(rs.getString("Severity"));
        n.setTargetType(rs.getString("TargetType"));
        n.setTargetId(rs.getString("TargetId"));
        n.setActionUrl(rs.getString("ActionUrl"));
        int cinemaId = rs.getInt("CinemaId");
        n.setCinemaId(rs.wasNull() ? null : cinemaId);
        int createdByUserId = rs.getInt("CreatedByUserId");
        n.setCreatedByUserId(rs.wasNull() ? null : createdByUserId);
        n.setEventType(rs.getString("EventType"));
        int approvalRequestId = rs.getInt("ApprovalRequestId");
        n.setApprovalRequestId(rs.wasNull() ? null : approvalRequestId);
        n.setRead(rs.getBoolean("IsRead"));
        Timestamp resolvedAt = rs.getTimestamp("ResolvedAt");
        if (resolvedAt != null) {
            n.setResolvedAt(resolvedAt.toLocalDateTime());
        }
        int resolvedBy = rs.getInt("ResolvedBy");
        n.setResolvedBy(rs.wasNull() ? null : resolvedBy);
        n.setResolution(rs.getString("Resolution"));
        Timestamp ts = rs.getTimestamp("CreatedAt");
        if (ts != null) {
            n.setCreatedAt(ts.toLocalDateTime());
        }
        return n;
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null || value <= 0) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }
}

package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.NotificationRecipientDAO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class JdbcNotificationRecipientDAO implements NotificationRecipientDAO {

    /**
     * MERGE thay vi "SELECT roi INSERT": hai tab admin bam "da doc" cung luc deu thay chua co
     * so nhan roi cung INSERT, va unique index {@code UX_NotificationRecipients_Receipt} se lam
     * mot trong hai request bao loi cho mot thao tac vo hai. MERGE de DB tu quyet.
     */
    @Override
    public void markRead(String sourceType, int notificationId, int userId) {
        String sql = """
                MERGE NotificationRecipients AS target
                USING (SELECT ? AS SourceType, ? AS NotificationId, ? AS UserId) AS source
                   ON target.SourceType = source.SourceType
                  AND target.NotificationId = source.NotificationId
                  AND target.UserId = source.UserId
                WHEN MATCHED AND target.ReadAt IS NULL
                    THEN UPDATE SET ReadAt = SYSDATETIME()
                WHEN NOT MATCHED
                    THEN INSERT (SourceType, NotificationId, UserId, ReadAt)
                         VALUES (source.SourceType, source.NotificationId, source.UserId, SYSDATETIME());
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sourceType);
            ps.setInt(2, notificationId);
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Khong the ghi nhan trang thai da doc", ex);
        }
    }

    @Override
    public void markAllRead(String sourceType, Set<Integer> notificationIds, int userId) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return;
        }
        for (Integer notificationId : notificationIds) {
            markRead(sourceType, notificationId, userId);
        }
    }

    @Override
    public Set<Integer> readNotificationIds(String sourceType, int userId) {
        String sql = """
                SELECT NotificationId FROM NotificationRecipients
                WHERE SourceType = ? AND UserId = ? AND ReadAt IS NOT NULL
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sourceType);
            ps.setInt(2, userId);
            Set<Integer> ids = new HashSet<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
            return ids;
        } catch (SQLException ex) {
            throw new DaoException("Khong the doc trang thai thong bao theo nguoi nhan", ex);
        }
    }

    @Override
    public int deliver(Connection connection, int notificationId, Set<Integer> userIds)
            throws SQLException {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO NotificationRecipients (SourceType, NotificationId, UserId)
                SELECT ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM NotificationRecipients
                    WHERE SourceType = ? AND NotificationId = ? AND UserId = ?
                )
                """;
        int delivered = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Integer userId : userIds) {
                ps.setString(1, SOURCE_USER);
                ps.setInt(2, notificationId);
                ps.setInt(3, userId);
                ps.setString(4, SOURCE_USER);
                ps.setInt(5, notificationId);
                ps.setInt(6, userId);
                delivered += ps.executeUpdate();
            }
        }
        return delivered;
    }

    /**
     * {@code NotificationId} tro toi hai bang khac nhau tuy {@code SourceType}, nen khong khai
     * duoc FOREIGN KEY va cung khong co cascade. Don rac phai lam tuong minh sau moi lan xoa
     * thong bao — neu khong, so nhan cu se lam mot thong bao moi trung Id bi coi la "da doc".
     */
    @Override
    public int deleteOrphans(Connection connection, String sourceType, String notificationTable)
            throws SQLException {
        if (!"AdminNotifications".equals(notificationTable)
                && !"UserNotifications".equals(notificationTable)) {
            throw new IllegalArgumentException("Ten bang thong bao khong hop le: " + notificationTable);
        }
        String sql = "DELETE nr FROM NotificationRecipients nr"
                + " WHERE nr.SourceType = ?"
                + " AND NOT EXISTS (SELECT 1 FROM " + notificationTable + " n WHERE n.Id = nr.NotificationId)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sourceType);
            return ps.executeUpdate();
        }
    }
}

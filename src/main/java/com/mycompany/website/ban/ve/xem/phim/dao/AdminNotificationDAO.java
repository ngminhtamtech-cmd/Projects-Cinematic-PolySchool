package com.mycompany.website.ban.ve.xem.phim.dao;

import java.sql.Connection;
import java.sql.SQLException;
import com.mycompany.website.ban.ve.xem.phim.model.AdminNotification;
import java.util.List;

public interface AdminNotificationDAO {
    List<AdminNotification> findAll();
    List<AdminNotification> findUnread();
    int countUnread();
    void markAsRead(int notificationId);
    void markAsReadByTarget(String targetType, String targetId);
    void resolveByTarget(String targetType, String targetId, int resolvedBy, String resolution);
    default void resolveByTarget(Connection connection, String targetType, String targetId,
            int resolvedBy, String resolution) throws SQLException {
        throw new UnsupportedOperationException("Transactional notification resolution is not supported");
    }
    void markAllAsRead();
    void createNotification(AdminNotification notification);
    boolean existsNotificationForTarget(String category, String targetType, String targetId);

    /**
     * Xoa moi thong bao tro toi mot doi tuong da bi xoa han (RM-01).
     *
     * <p>Phai chay trong <b>cung connection/transaction</b> voi lenh xoa doi tuong: neu xoa
     * phong thanh cong ma xoa thong bao that bai, admin se thay mot the "Xoa vinh vien phong"
     * tro toi phong khong con ton tai — bam vao chi bao loi, va card do khong bao gio bien mat.
     * Do dung la trieu chung nguoi dung bao.</p>
     *
     * @return so thong bao da xoa
     */
    int deleteByTarget(java.sql.Connection connection, String targetType, String targetId)
            throws java.sql.SQLException;
}

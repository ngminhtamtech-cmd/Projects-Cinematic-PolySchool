package com.mycompany.website.ban.ve.xem.phim.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * So nhan thong bao: mot dong = "thong bao X da den user Y", kem moc doc rieng cua user do.
 *
 * <p>Bang nay phuc vu ca hai nguon thong bao, phan biet bang {@code sourceType}:</p>
 * <ul>
 *   <li>{@value #SOURCE_ADMIN} — canh bao van hanh trong {@code AdminNotifications}. Manager va
 *       admin cung nhin mot dong canh bao, nen trang thai da doc bat buoc phai tach theo nguoi
 *       nhan (FLOW-NOTIFY-SOLDOUT-004).</li>
 *   <li>{@value #SOURCE_USER} — thong bao manager/admin soan gui toi nguoi dung, luu o
 *       {@code UserNotifications} (FLOW-NOTIFY-USER-001).</li>
 * </ul>
 */
public interface NotificationRecipientDAO {
    String SOURCE_ADMIN = "admin";
    String SOURCE_USER = "user";

    /** Ghi nhan {@code userId} da doc thong bao. Goi lai nhieu lan khong tao them dong. */
    void markRead(String sourceType, int notificationId, int userId);

    /** Danh dau da doc hang loat trong mot lan goi. */
    void markAllRead(String sourceType, Set<Integer> notificationIds, int userId);

    /** Id cac thong bao ma rieng {@code userId} da doc. */
    Set<Integer> readNotificationIds(String sourceType, int userId);

    /** Phat mot thong bao nguoi dung toi danh sach nguoi nhan; bo qua nguoi da co so nhan. */
    int deliver(Connection connection, int notificationId, Set<Integer> userIds) throws SQLException;

    /** Xoa cac so nhan tro toi thong bao khong con ton tai. */
    int deleteOrphans(Connection connection, String sourceType, String notificationTable)
            throws SQLException;
}

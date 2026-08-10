package com.mycompany.website.ban.ve.xem.phim.dao;

import com.mycompany.website.ban.ve.xem.phim.model.UserNotification;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/** Thong bao manager/admin gui toi nguoi dung (FLOW-NOTIFY-USER-001). */
public interface UserNotificationDAO {

    /** Tao thong bao va tra ve Id vua sinh. */
    int create(Connection connection, UserNotification notification) throws SQLException;

    /**
     * Danh sach nguoi nhan suy ra tu pham vi cua thong bao.
     *
     * <p>Tinh trong SQL chu khong duyet trong Java: danh sach thanh vien co the rat dai, va
     * dieu kien "khach cua rap X" la mot phep join tren don hang.</p>
     */
    Set<Integer> resolveRecipients(Connection connection, UserNotification notification)
            throws SQLException;

    /**
     * Hop thu cua mot nguoi dung: chi thong bao dang trong cua so hien thi va con hieu luc,
     * kem moc da doc cua chinh nguoi do.
     */
    List<UserNotification> inbox(int userId, boolean unreadOnly);
}

package com.mycompany.website.ban.ve.xem.phim.dao;

import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.model.UserAuthState;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface UserDAO {
    Optional<User> findByEmail(String email);
    Optional<User> findById(int id);
    int create(User user);
    boolean updateProfile(User user);
    boolean changePassword(int id, String passwordHash);

    /** Ban dung chung connection — de goi trong mot transaction dang mo (quy tac P03). */
    Optional<User> findById(Connection connection, int id) throws SQLException;

    /** Ban dung chung connection — xem {@link #findById(Connection, int)}. */
    boolean changePassword(Connection connection, int id, String passwordHash) throws SQLException;

    /**
     * Doc <b>chi</b> cac cot quyet dinh mot phien con hop le hay khong (D5).
     *
     * <p>{@code AuthFilter} goi ham nay dinh ky nen no phai re: 6 cot, khong {@code SELECT *}.
     * Bo qua dieu kien {@code Deleted = 0} — nguoi da bi xoa mem <b>phai</b> doc duoc ra de con
     * biet duong cat phien cua ho.</p>
     */
    Optional<UserAuthState> findAuthState(int id);
    boolean softDelete(int id);
    boolean updateLockStatus(int userId, boolean isLocked, String lockReason);
    boolean incrementWarning(int userId);
    boolean resetWarnings(int userId);
}

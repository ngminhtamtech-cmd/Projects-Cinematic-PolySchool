package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.dao.BaseDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.UserDAO;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.model.UserAuthState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Optional;

public class JdbcUserDAO extends BaseDAO implements UserDAO {
    @Override
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM Users WHERE Email = ? AND Deleted = 0";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot find user by email", ex);
        }
    }

    @Override
    public Optional<User> findById(int id) {
        String sql = "SELECT * FROM Users WHERE Id = ? AND Deleted = 0";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot find user by id", ex);
        }
    }

    @Override
    public Optional<User> findById(Connection connection, int id) throws SQLException {
        String sql = "SELECT * FROM Users WHERE Id = ? AND Deleted = 0";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Truy van "phien con hop le khong" cua {@code AuthFilter} (D5).
     *
     * <p>Khong co {@code AND Deleted = 0}: tai khoan vua bi xoa mem chinh la truong hop can phat hien
     * nhat. Loc no o day thi filter se tuong nguoi dung khong ton tai va... cho phien chay tiep.</p>
     */
    @Override
    public Optional<UserAuthState> findAuthState(int id) {
        String sql = "SELECT Id, Role, IsLocked, Deleted, CinemaId, UpdatedAt FROM Users WHERE Id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int userId = rs.getInt("Id");
                String role = rs.getString("Role");
                boolean locked = rs.getBoolean("IsLocked");
                boolean deleted = rs.getBoolean("Deleted");
                // wasNull() noi ve cot vua doc, nen phai hoi ngay sau getInt("CinemaId"),
                // khong duoc de lot bat ky lenh doc nao vao giua.
                int cinemaIdValue = rs.getInt("CinemaId");
                Integer cinemaId = rs.wasNull() ? null : cinemaIdValue;
                return Optional.of(new UserAuthState(userId, role, locked, deleted, cinemaId,
                        toLocalDateTime(rs.getTimestamp("UpdatedAt"))));
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot read user auth state", ex);
        }
    }

    @Override
    public int create(User user) {
        String sql = """
                INSERT INTO Users (Username, FullName, Email, PasswordHash, Phone, Address, Avatar, Role)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getFullName());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPasswordHash());
            ps.setString(5, user.getPhone());
            ps.setString(6, user.getAddress());
            ps.setString(7, user.getAvatar());
            ps.setString(8, user.getRole());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new DaoException("Cannot read generated user id", null);
        } catch (SQLException ex) {
            throw new DaoException("Cannot create user", ex);
        }
    }

    @Override
    public boolean updateProfile(User user) {
        String sql = """
                UPDATE Users
                SET Username = ?, FullName = ?, Phone = ?, Address = ?, Avatar = ?, UpdatedAt = GETDATE()
                WHERE Id = ? AND Deleted = 0
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getFullName());
            ps.setString(3, user.getPhone());
            ps.setString(4, user.getAddress());
            ps.setString(5, user.getAvatar());
            ps.setInt(6, user.getId());
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DaoException("Cannot update user profile", ex);
        }
    }

    @Override
    public boolean changePassword(int id, String passwordHash) {
        String sql = "UPDATE Users SET PasswordHash = ?, UpdatedAt = GETDATE() WHERE Id = ? AND Deleted = 0";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DaoException("Cannot change user password", ex);
        }
    }

    @Override
    public boolean changePassword(Connection connection, int id, String passwordHash) throws SQLException {
        String sql = "UPDATE Users SET PasswordHash = ?, UpdatedAt = GETDATE() WHERE Id = ? AND Deleted = 0";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, id);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean softDelete(int id) {
        String sql = "UPDATE Users SET Deleted = 1, UpdatedAt = GETDATE() WHERE Id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DaoException("Cannot soft delete user", ex);
        }
    }

    @Override
    public boolean updateLockStatus(int userId, boolean isLocked, String lockReason) {
        // F-006: LockedAt tung lay System.currentTimeMillis() cua may ung dung trong khi UpdatedAt
        // cua cung mot cau lenh lay GETDATE() cua may DB — hai cot cua cung mot hanh dong co the
        // lech gio. Ca hai gio deu do SQL Server sinh trong cung mot cau lenh.
        // Chi doi nguon thoi gian; nghia cua IsLocked va quyen han khong doi.
        String sql = isLocked
                ? "UPDATE Users SET IsLocked = 1, LockReason = ?, LockedAt = GETDATE(), UpdatedAt = GETDATE() WHERE Id = ?"
                : "UPDATE Users SET IsLocked = 0, LockReason = ?, LockedAt = NULL, UpdatedAt = GETDATE() WHERE Id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lockReason);
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DaoException("Cannot update user lock status", ex);
        }
    }

    @Override
    public boolean incrementWarning(int userId) {
        String sql = "UPDATE Users SET WarningCount = WarningCount + 1, UpdatedAt = GETDATE() WHERE Id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DaoException("Cannot increment user warning count", ex);
        }
    }

    @Override
    public boolean resetWarnings(int userId) {
        String sql = "UPDATE Users SET WarningCount = 0, UpdatedAt = GETDATE() WHERE Id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DaoException("Cannot reset user warnings", ex);
        }
    }

    private User map(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("Id"));
        user.setUsername(rs.getString("Username"));
        user.setFullName(rs.getString("FullName"));
        user.setEmail(rs.getString("Email"));
        user.setPasswordHash(rs.getString("PasswordHash"));
        user.setPhone(rs.getString("Phone"));
        user.setAddress(rs.getString("Address"));
        user.setAvatar(rs.getString("Avatar"));
        user.setRole(rs.getString("Role"));
        user.setDeleted(rs.getBoolean("Deleted"));
        // P10: truoc day mapper nay bo qua CinemaId (P00 da ghi nhan la no ky thuat), nen moi
        // User doc len tu day deu co cinemaId = null. AccountStateGuard doi chieu phien voi DB
        // bang chinh truong nay, thieu no thi moi staff/manager co pham vi rap se bi cat phien
        // ngay lap tuc. Khong bao trong try/catch nuot loi: cot da nam trong kiem tra schema
        // fail-fast luc khoi dong.
        int cinemaIdValue = rs.getInt("CinemaId");
        user.setCinemaId(rs.wasNull() ? null : cinemaIdValue);
        user.setLoyaltyPoints(rs.getInt("LoyaltyPoints"));
        user.setTotalSpent(rs.getBigDecimal("TotalSpent"));
        user.setMembershipTier(rs.getString("MembershipTier"));
        user.setLocked(rs.getBoolean("IsLocked"));
        user.setLockReason(rs.getString("LockReason"));
        user.setWarningCount(rs.getInt("WarningCount"));
        user.setLockedAt(toLocalDateTime(rs.getTimestamp("LockedAt")));
        user.setCreatedAt(toLocalDateTime(rs.getTimestamp("CreatedAt")));
        user.setUpdatedAt(toLocalDateTime(rs.getTimestamp("UpdatedAt")));
        user.setEmailVerifiedAt(toLocalDateTime(rs.getTimestamp("EmailVerifiedAt")));
        return user;
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}

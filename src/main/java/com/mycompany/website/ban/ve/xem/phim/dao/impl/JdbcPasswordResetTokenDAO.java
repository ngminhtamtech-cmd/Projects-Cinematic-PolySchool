package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.dao.BaseDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.PasswordResetTokenDAO;
import com.mycompany.website.ban.ve.xem.phim.model.PasswordResetToken;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

public class JdbcPasswordResetTokenDAO extends BaseDAO implements PasswordResetTokenDAO {

    @Override
    public void create(Connection connection, String tokenHash, int userId, int expiresInMinutes, String requestIp)
            throws SQLException {
        String sql = """
                INSERT INTO PasswordResetTokens (TokenHash, UserId, ExpiresAt, CreatedAt, RequestIp)
                VALUES (?, ?, DATEADD(MINUTE, ?, SYSDATETIME()), SYSDATETIME(), ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            ps.setInt(2, userId);
            ps.setInt(3, expiresInMinutes);
            ps.setString(4, requestIp);
            ps.executeUpdate();
        }
    }

    /**
     * {@code UPDATE ... OUTPUT deleted.UserId} lam ca hai viec trong mot cau lenh: kiem dieu kien va
     * danh dau da dung. Neu tach thanh SELECT roi UPDATE thi hai request gui cung token trong cung
     * mot phan giay se cung di qua buoc SELECT — dung mot lan se khong con dung mot lan nua.
     */
    @Override
    public Optional<Integer> consume(Connection connection, String tokenHash) throws SQLException {
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        String sql = """
                UPDATE PasswordResetTokens
                SET UsedAt = SYSDATETIME()
                OUTPUT deleted.UserId AS UserId
                WHERE TokenHash = ?
                  AND UsedAt IS NULL
                  AND ExpiresAt > SYSDATETIME()
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt("UserId")) : Optional.empty();
            }
        }
    }

    /**
     * {@code INSERT ... SELECT ... WHERE NOT EXISTS} — kiem cooldown va ghi phieu trong mot cau.
     *
     * <p>{@code UPDLOCK, HOLDLOCK} tren cau con giu khoa khoang theo {@code UserId} toi luc commit,
     * nen hai request dong thoi bi xep hang thay vi cung doc "chua co phieu nao". Cung ky thuat
     * dang dung cho khoa ghe trong {@code BookingService}.</p>
     */
    @Override
    public boolean createIfOutsideCooldown(Connection connection, String tokenHash, int userId,
            int expiresInMinutes, String requestIp, int cooldownSeconds) throws SQLException {
        String sql = """
                INSERT INTO PasswordResetTokens (TokenHash, UserId, ExpiresAt, CreatedAt, RequestIp)
                SELECT ?, ?, DATEADD(MINUTE, ?, SYSDATETIME()), SYSDATETIME(), ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM PasswordResetTokens WITH (UPDLOCK, HOLDLOCK)
                    WHERE UserId = ?
                      AND CreatedAt > DATEADD(SECOND, -?, SYSDATETIME())
                )
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            ps.setInt(2, userId);
            ps.setInt(3, expiresInMinutes);
            ps.setString(4, requestIp);
            ps.setInt(5, userId);
            ps.setInt(6, Math.max(0, cooldownSeconds));
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public int invalidateAllForUser(Connection connection, int userId) throws SQLException {
        String sql = "UPDATE PasswordResetTokens SET UsedAt = SYSDATETIME() WHERE UserId = ? AND UsedAt IS NULL";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate();
        }
    }

    @Override
    public int invalidateOthersForUser(Connection connection, int userId, String keepTokenHash)
            throws SQLException {
        String sql = """
                UPDATE PasswordResetTokens SET UsedAt = SYSDATETIME()
                WHERE UserId = ? AND UsedAt IS NULL AND TokenHash <> ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, keepTokenHash);
            return ps.executeUpdate();
        }
    }

    @Override
    public Optional<PasswordResetToken> findByHash(String tokenHash) {
        String sql = """
                SELECT Id, TokenHash, UserId, ExpiresAt, UsedAt, CreatedAt, RequestIp
                FROM PasswordResetTokens WHERE TokenHash = ?
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot find password reset token", ex);
        }
    }

    private PasswordResetToken map(ResultSet rs) throws SQLException {
        PasswordResetToken token = new PasswordResetToken();
        token.setId(rs.getInt("Id"));
        token.setTokenHash(rs.getString("TokenHash"));
        token.setUserId(rs.getInt("UserId"));
        token.setExpiresAt(toLocalDateTime(rs.getTimestamp("ExpiresAt")));
        token.setUsedAt(toLocalDateTime(rs.getTimestamp("UsedAt")));
        token.setCreatedAt(toLocalDateTime(rs.getTimestamp("CreatedAt")));
        token.setRequestIp(rs.getString("RequestIp"));
        return token;
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}

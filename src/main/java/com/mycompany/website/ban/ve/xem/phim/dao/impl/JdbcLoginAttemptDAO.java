package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.dao.BaseDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.LoginAttemptDAO;
import com.mycompany.website.ban.ve.xem.phim.model.LoginAttemptSummary;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class JdbcLoginAttemptDAO extends BaseDAO implements LoginAttemptDAO {

    /**
     * Dem cac lan sai <b>sau</b> lan dung gan nhat va trong cua so thoi gian.
     *
     * <p>Nho menh de "sau lan dung gan nhat" ma dang nhap thanh cong mot lan la bo dem tu ve 0 —
     * khong can job don dep. {@code SYSDATETIME()} dam bao moc thoi gian lay tu DB.</p>
     *
     * <p>{@code UnlockInSeconds} tinh tu lan sai <b>gan nhat</b>: nguoi dung phai im lang du
     * {@code windowMinutes} phut moi duoc thu lai, do dung nghia "chan tam thoi".</p>
     */
    private static final String SUMMARY_BY_EMAIL = """
            SELECT COUNT(*) AS Failures,
                   ISNULL(DATEDIFF(SECOND, SYSDATETIME(), DATEADD(MINUTE, ?, MAX(AttemptAt))), 0) AS UnlockInSeconds
            FROM LoginAttempts
            WHERE Email = ?
              AND Success = 0
              AND AttemptAt > DATEADD(MINUTE, -?, SYSDATETIME())
              AND AttemptAt > ISNULL(
                    (SELECT MAX(AttemptAt) FROM LoginAttempts WHERE Email = ? AND Success = 1),
                    CAST('1900-01-01' AS DATETIME2))
            """;

    private static final String SUMMARY_BY_IP = """
            SELECT COUNT(*) AS Failures,
                   ISNULL(DATEDIFF(SECOND, SYSDATETIME(), DATEADD(MINUTE, ?, MAX(AttemptAt))), 0) AS UnlockInSeconds
            FROM LoginAttempts
            WHERE IpAddress = ?
              AND Success = 0
              AND AttemptAt > DATEADD(MINUTE, -?, SYSDATETIME())
              AND AttemptAt > ISNULL(
                    (SELECT MAX(AttemptAt) FROM LoginAttempts WHERE IpAddress = ? AND Success = 1),
                    CAST('1900-01-01' AS DATETIME2))
            """;

    @Override
    public void record(Connection connection, String email, String ipAddress, boolean success) throws SQLException {
        String sql = "INSERT INTO LoginAttempts (Email, IpAddress, AttemptAt, Success) VALUES (?, ?, SYSDATETIME(), ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, normalize(email));
            ps.setString(2, ipAddress);
            ps.setBoolean(3, success);
            ps.executeUpdate();
        }
    }

    @Override
    public void record(String email, String ipAddress, boolean success) {
        try (Connection connection = getConnection()) {
            record(connection, email, ipAddress, success);
        } catch (SQLException ex) {
            throw new DaoException("Cannot record login attempt", ex);
        }
    }

    @Override
    public LoginAttemptSummary summarizeByEmail(Connection connection, String email, int windowMinutes)
            throws SQLException {
        return summarize(connection, SUMMARY_BY_EMAIL, normalize(email), windowMinutes);
    }

    @Override
    public LoginAttemptSummary summarizeByIp(Connection connection, String ipAddress, int windowMinutes)
            throws SQLException {
        if (ipAddress == null || ipAddress.isBlank()) {
            return LoginAttemptSummary.empty();
        }
        return summarize(connection, SUMMARY_BY_IP, ipAddress, windowMinutes);
    }

    private LoginAttemptSummary summarize(Connection connection, String sql, String key, int windowMinutes)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, windowMinutes);
            ps.setString(2, key);
            ps.setInt(3, windowMinutes);
            ps.setString(4, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new LoginAttemptSummary(rs.getInt("Failures"), rs.getInt("UnlockInSeconds"));
                }
            }
        }
        return LoginAttemptSummary.empty();
    }

    @Override
    public int clearForEmail(String email) {
        String sql = "DELETE FROM LoginAttempts WHERE Email = ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, normalize(email));
            return ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Cannot clear login attempts", ex);
        }
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}

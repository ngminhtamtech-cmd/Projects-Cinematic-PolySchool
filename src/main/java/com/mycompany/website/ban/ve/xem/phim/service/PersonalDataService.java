package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.util.PasswordUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PersonalDataService {
    public Map<String, Object> exportForUser(int userId) {
        try (Connection c = DBConnection.getConnection()) {
            Map<String, Object> export = new LinkedHashMap<>();
            export.put("profile", one(c, """
                    SELECT Id, Username, FullName, Email, Phone, Address, Role, LoyaltyPoints,
                           TotalSpent, MembershipTier, CreatedAt, UpdatedAt
                    FROM Users WHERE Id=?
                    """, userId));
            export.put("orders", many(c, """
                    SELECT Id, ShowtimeId, SeatSubtotal, ComboSubtotal, DiscountAmount, TotalAmount,
                           TicketCode, PaymentMethod, PaymentStatus, OrderStatus, CreatedAt, UpdatedAt
                    FROM Orders WHERE UserId=? ORDER BY CreatedAt DESC
                    """, userId));
            export.put("vouchers", many(c, """
                    SELECT uv.Id, uv.Code, uv.IsUsed, uv.UsedAt, uv.CreatedAt, p.Name AS PromotionName
                    FROM UserVouchers uv JOIN Promotions p ON p.Id=uv.PromotionId
                    WHERE uv.UserId=? ORDER BY uv.CreatedAt DESC
                    """, userId));
            export.put("pointTransactions", many(c, """
                    SELECT Id, Points, Type, Description, CreatedAt
                    FROM PointTransactions WHERE UserId=? ORDER BY CreatedAt DESC
                    """, userId));
            return export;
        } catch (SQLException ex) {
            throw new DaoException("Cannot export personal data", ex);
        }
    }

    public void anonymize(int userId, String originalEmail) {
        String token = sha256(userId + ":" + originalEmail).substring(0, 24);
        String email = "deleted+" + token + "@anonymized.invalid";
        String username = "deleted_" + userId + "_" + token.substring(0, 8);
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE Users SET Username=?, FullName=N'[đã xoá]', Email=?, Phone=NULL,
                          Address=NULL, Avatar=NULL, PasswordHash=?, Deleted=1, IsLocked=1,
                          LockReason=N'Tài khoản đã được chủ sở hữu xoá', AnonymizedAt=GETDATE(),
                          UpdatedAt=GETDATE()
                        WHERE Id=? AND Deleted=0
                        """)) {
                    ps.setString(1, username);
                    ps.setString(2, email);
                    ps.setString(3, PasswordUtil.hash(UUID.randomUUID().toString()));
                    ps.setInt(4, userId);
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalStateException("Account is missing or already anonymized");
                    }
                }
                deleteByUser(c, "PasswordResetTokens", userId);
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM LoginAttempts WHERE Email=?")) {
                    ps.setString(1, originalEmail);
                    ps.executeUpdate();
                }
                c.commit();
                AccountStateGuard.invalidate(userId);
            } catch (RuntimeException | SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot anonymize account", ex);
        }
    }

    private void deleteByUser(Connection c, String table, int userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE UserId=?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    private Map<String, Object> one(Connection c, String sql, int userId) throws SQLException {
        List<Map<String, Object>> rows = query(c, sql, userId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private List<Map<String, Object>> many(Connection c, String sql, int userId) throws SQLException {
        return query(c, sql, userId);
    }

    private List<Map<String, Object>> query(Connection c, String sql, int userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private String sha256(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte value : bytes) out.append(String.format("%02x", value));
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}

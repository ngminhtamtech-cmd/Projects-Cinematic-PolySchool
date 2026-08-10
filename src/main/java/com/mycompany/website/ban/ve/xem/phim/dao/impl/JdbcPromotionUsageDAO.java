package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.PromotionUsageDAO;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class JdbcPromotionUsageDAO implements PromotionUsageDAO {
    @Override
    public Integer findUsableVoucherId(Connection connection, String code, int userId) {
        String sql = """
                SELECT Id FROM UserVouchers WITH (UPDLOCK, HOLDLOCK)
                WHERE Code = ? AND UserId = ? AND IsUsed = 0
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load personal voucher", ex);
        }
    }

    @Override
    public Integer findPromotionIdForVoucher(Connection connection, int voucherId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT PromotionId FROM UserVouchers WHERE Id = ?")) {
            ps.setInt(1, voucherId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load voucher promotion", ex);
        }
    }

    @Override
    public int countForUser(Connection connection, int promotionId, int userId) {
        String sql = "SELECT COUNT(*) FROM PromotionUsage WHERE PromotionId = ? AND UserId = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, promotionId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot count promotion usage", ex);
        }
    }

    @Override
    public void record(Connection connection, int promotionId, int userId, int orderId) {
        String sql = """
                INSERT INTO PromotionUsage (PromotionId, UserId, OrderId, UsedAt)
                VALUES (?, ?, ?, GETDATE())
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, promotionId);
            ps.setInt(2, userId);
            ps.setInt(3, orderId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 2601 || ex.getErrorCode() == 2627) {
                throw new BookingException(400, "Bạn đã dùng mã khuyến mãi này đủ số lần cho phép.");
            }
            throw new DaoException("Cannot record promotion usage", ex);
        }
    }

    @Override
    public void consumeVoucher(Connection connection, int voucherId, int userId, int orderId) {
        String sql = """
                UPDATE UserVouchers
                SET IsUsed = 1, UsedAt = GETDATE(), UsedOrderId = ?
                WHERE Id = ? AND UserId = ? AND IsUsed = 0
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, voucherId);
            ps.setInt(3, userId);
            if (ps.executeUpdate() != 1) {
                throw new BookingException(400, "Voucher cá nhân đã được dùng hoặc không thuộc tài khoản này.");
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot consume personal voucher", ex);
        }
    }
}

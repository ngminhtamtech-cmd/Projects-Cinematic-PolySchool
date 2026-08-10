package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.dao.BaseDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.PromotionDAO;
import com.mycompany.website.ban.ve.xem.phim.model.Promotion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class JdbcPromotionDAO extends BaseDAO implements PromotionDAO {
    @Override
    public Optional<Promotion> findByCode(String code) {
        try (Connection connection = getConnection()) {
            return findByCode(connection, code);
        } catch (SQLException ex) {
            throw new DaoException("Cannot load promotion", ex);
        }
    }

    @Override
    public Optional<Promotion> findByCode(Connection connection, String code) {
        String sql = "SELECT * FROM Promotions WHERE Code = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load promotion", ex);
        }
    }

    @Override
    public Optional<Promotion> findById(Connection connection, int promotionId) {
        String sql = "SELECT * FROM Promotions WHERE Id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, promotionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load promotion", ex);
        }
    }

    @Override
    public void incrementUsedCount(Connection connection, int promotionId) {
        String sql = "UPDATE Promotions SET UsedCount = UsedCount + 1, UpdatedAt = GETDATE() WHERE Id = ? AND (UsageLimit IS NULL OR UsedCount < UsageLimit)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, promotionId);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new com.mycompany.website.ban.ve.xem.phim.service.BookingException(400, "Mã khuyến mãi đã hết lượt sử dụng.");
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot update promotion usage", ex);
        }
    }

    private Promotion map(ResultSet rs) throws SQLException {
        Promotion promotion = new Promotion();
        promotion.setId(rs.getInt("Id"));
        promotion.setCode(rs.getString("Code"));
        promotion.setDescription(rs.getString("Description"));
        double discount = rs.getDouble("DiscountPercent");
        promotion.setDiscountPercent(rs.wasNull() ? null : discount);
        promotion.setMaxDiscount(rs.getBigDecimal("MaxDiscount"));
        java.sql.Date startDate = rs.getDate("StartDate");
        java.sql.Date endDate = rs.getDate("EndDate");
        promotion.setStartDate(startDate == null ? null : startDate.toLocalDate());
        promotion.setEndDate(endDate == null ? null : endDate.toLocalDate());
        promotion.setConditionsJson(rs.getString("ConditionsJson"));
        int usageLimit = rs.getInt("UsageLimit");
        promotion.setUsageLimit(rs.wasNull() ? null : usageLimit);
        promotion.setUsedCount(rs.getInt("UsedCount"));
        promotion.setStatus(rs.getString("Status"));
        // Cac cot tu migration loyalty. Thieu chung thi getVoucherType() luon tra ve
        // mac dinh "PUBLIC" va rang buoc hang thanh vien khong bao gio duoc ap dung.
        promotion.setVoucherType(rs.getString("VoucherType"));
        promotion.setTargetTier(rs.getString("TargetTier"));
        promotion.setPointsRequired(rs.getInt("PointsRequired"));
        promotion.setPerUserLimit(rs.getInt("PerUserLimit"));
        return promotion;
    }
}

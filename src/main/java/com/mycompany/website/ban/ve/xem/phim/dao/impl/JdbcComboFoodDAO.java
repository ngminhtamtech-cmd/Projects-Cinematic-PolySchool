package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.dao.BaseDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.ComboFoodDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.model.ComboFood;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JdbcComboFoodDAO extends BaseDAO implements ComboFoodDAO {
    @Override
    public List<ComboFood> findActive() {
        return List.of();
    }

    /**
     * Combo ban duoc tai mot cum rap: combo dung chung + combo rieng cua rap do (CB-01).
     *
     * <p>{@code cinemaId} rong tra ve toan bo combo dang ban — dung cho cac man hinh khong gan
     * voi mot rap cu the.</p>
     */
    @Override
    public List<ComboFood> findActiveForCinema(Integer cinemaId) {
        if (cinemaId == null || cinemaId <= 0) {
            return List.of();
        }
        String sql = "SELECT * FROM ComboFoods "
                + "WHERE (Status IS NULL OR Status = '' OR LOWER(TRIM(Status)) = 'active') "
                + "AND CinemaId = ? ORDER BY Name";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cinemaId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapMany(rs);
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load combo foods", ex);
        }
    }

    @Override
    public List<ComboFood> findByIds(List<Integer> ids) {
        try (Connection connection = getConnection()) {
            return findByIds(connection, ids);
        } catch (SQLException ex) {
            throw new DaoException("Cannot load combo foods by ids", ex);
        }
    }

    @Override
    public List<ComboFood> findByIds(Connection connection, List<Integer> ids) {
        return findByIds(connection, ids, null);
    }

    @Override
    public List<ComboFood> findByIds(Connection connection, List<Integer> ids, Integer cinemaId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (cinemaId == null || cinemaId <= 0) {
            return List.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT * FROM ComboFoods WHERE (Status IS NULL OR Status = '' OR LOWER(TRIM(Status)) = 'active') AND Id IN (" + placeholders + ")"
                + " AND CinemaId = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            for (Integer id : ids) {
                ps.setInt(index++, id);
            }
            ps.setInt(index, cinemaId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapMany(rs);
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load combo foods by ids", ex);
        }
    }

    @Override
    public List<ComboFood> findByIdsAnyStatus(Connection connection, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT * FROM ComboFoods WHERE Id IN (" + placeholders + ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            for (Integer id : ids) {
                ps.setInt(index++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return mapMany(rs);
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load combo foods by ids any status", ex);
        }
    }

    private List<ComboFood> mapMany(ResultSet rs) throws SQLException {
        List<ComboFood> combos = new ArrayList<>();
        while (rs.next()) {
            ComboFood combo = new ComboFood();
            combo.setId(rs.getInt("Id"));
            combo.setName(rs.getString("Name"));
            combo.setImage(rs.getString("Image"));
            combo.setPrice(rs.getBigDecimal("Price"));
            combo.setDescription(rs.getString("Description"));
            combo.setStatus(rs.getString("Status"));
            int cinemaId = rs.getInt("CinemaId");
            combo.setCinemaId(rs.wasNull() ? null : cinemaId);
            combos.add(combo);
        }
        return combos;
    }
}

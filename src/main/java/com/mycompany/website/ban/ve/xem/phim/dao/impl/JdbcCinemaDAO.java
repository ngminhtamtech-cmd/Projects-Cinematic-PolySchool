package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.dao.BaseDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.CinemaDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.model.Cinema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class JdbcCinemaDAO extends BaseDAO implements CinemaDAO {
    /**
     * Danh sach rap cho moi be mat cong khai.
     *
     * <p>Day la duong doc rap <b>duy nhat</b> ngoai trang quan tri — {@code HeaderDataFilter},
     * {@code CatalogApiServlet}, {@code CinemaServlet}, {@code FilmServlet},
     * {@code BookingPageServlet}, {@code CineTagServlet} va {@code CinemaCornerServlet} deu goi
     * vao day. Vi vay bo loc {@code Status <> 'deleted'} chi can dat o mot cho.</p>
     *
     * <p>Rap xoa mem van con nguyen dong trong bang de {@code JdbcOrderDAO}/{@code JdbcShowtimeDAO}
     * con {@code JOIN Cinemas} duoc — lich su don va ve cu khong duoc hong vi mot thao tac xoa.</p>
     */
    @Override
    public List<Cinema> findAll(Integer cityId) {
        String sql = """
                SELECT c.*, ci.Name AS CityName
                FROM Cinemas c
                JOIN Cities ci ON ci.Id = c.CityId
                WHERE (? IS NULL OR c.CityId = ?)
                  AND ISNULL(c.Status, 'active') <> 'deleted'
                ORDER BY ci.Name, c.Name
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (cityId == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, cityId);
                ps.setInt(2, cityId);
            }
            List<Cinema> cinemas = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cinemas.add(map(rs));
                }
            }
            return cinemas;
        } catch (SQLException ex) {
            throw new DaoException("Cannot load cinemas", ex);
        }
    }

    private Cinema map(ResultSet rs) throws SQLException {
        Cinema cinema = new Cinema();
        cinema.setId(rs.getInt("Id"));
        cinema.setCityId(rs.getInt("CityId"));
        cinema.setCityName(rs.getString("CityName"));
        cinema.setName(rs.getString("Name"));
        cinema.setAddress(rs.getString("Address"));
        cinema.setAvatar(rs.getString("Avatar"));
        cinema.setBannerUrl(rs.getString("BannerUrl"));
        cinema.setDescription(rs.getString("Description"));
        cinema.setPhone(rs.getString("Phone"));
        cinema.setStatus(rs.getString("Status"));
        cinema.setCreatedAt(toLocalDateTime(rs.getTimestamp("CreatedAt")));
        cinema.setUpdatedAt(toLocalDateTime(rs.getTimestamp("UpdatedAt")));
        return cinema;
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}

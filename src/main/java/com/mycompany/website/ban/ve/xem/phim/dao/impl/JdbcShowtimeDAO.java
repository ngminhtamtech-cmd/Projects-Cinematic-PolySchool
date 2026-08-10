package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.dao.BaseDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.ShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcShowtimeDAO extends BaseDAO implements ShowtimeDAO {
    /**
     * Danh sach suat chieu <b>cong khai</b>.
     *
     * <p>BUG-12 (INV-7): loc phong {@code inactive} ngay trong SQL. Sau khi phong ngung hoat dong,
     * dat ve moi da bi chan tu tang service, nhung suat van hien tren API/trang cong khai — khach
     * thay, bam vao, roi nhan loi. Loc o SQL chu khong o Java: loc sau khi phan trang tung lam
     * trang 1 rong (N-13).</p>
     *
     * <p>{@link #findById(int)} <b>khong</b> loc: ve da ban cho phong do van phai tra cuu va
     * check-in duoc.</p>
     */
    @Override
    public List<Showtime> findByFilmAndCinema(Integer filmId, Integer cinemaId) {
        String sql = """
                SELECT s.*, f.Title AS FilmTitle, f.Thumbnail AS FilmThumbnail, f.AgeRating AS AgeRating, c.Name AS CinemaName, r.Name AS RoomName, ISNULL(r.Status, 'active') AS RoomStatus, c.CityId AS CityId,
                       ISNULL(st.TotalSeats, 0) AS TotalSeats,
                       ISNULL(st.AvailableSeats, 0) AS AvailableSeats
                FROM Showtimes s
                JOIN Films f ON f.Id = s.FilmId
                JOIN Cinemas c ON c.Id = s.CinemaId
                JOIN Rooms r ON r.Id = s.RoomId
                LEFT JOIN (
                    SELECT ss.ShowtimeId,
                           SUM(CASE WHEN ss.Status != 'maintenance' THEN 1 ELSE 0 END) AS TotalSeats,
                           SUM(CASE WHEN ss.Status = 'available' AND (ss.HeldUntil IS NULL OR ss.HeldUntil < GETDATE()) THEN 1 ELSE 0 END) AS AvailableSeats
                    FROM ShowtimeSeats ss
                    GROUP BY ss.ShowtimeId
                ) st ON st.ShowtimeId = s.Id
                WHERE (? IS NULL OR s.FilmId = ?)
                  AND (? IS NULL OR s.CinemaId = ?)
                  AND s.StartTime > GETDATE()
                  AND s.SaleStatus = 'ON_SALE'
                  AND f.DeletedAt IS NULL
                  AND ISNULL(f.Status, 'showing') <> 'ended'
                  AND (f.EndDate IS NULL OR f.EndDate >= CAST(GETDATE() AS DATE))
                  AND ISNULL(r.Status, 'active') = 'active'
                ORDER BY s.StartTime ASC
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindInteger(ps, 1, filmId);
            bindInteger(ps, 2, filmId);
            bindInteger(ps, 3, cinemaId);
            bindInteger(ps, 4, cinemaId);
            List<Showtime> showtimes = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    showtimes.add(map(rs));
                }
            }
            return showtimes;
        } catch (SQLException ex) {
            throw new DaoException("Cannot load showtimes", ex);
        }
    }

    @Override
    public Optional<Showtime> findById(int id) {
        try (Connection conn = getConnection()) {
            return findById(conn, id);
        } catch (SQLException ex) {
            throw new DaoException("Cannot find showtime by id", ex);
        }
    }

    @Override
    public Optional<Showtime> findById(Connection conn, int id) {
        String sql = """
                SELECT s.*, f.Title AS FilmTitle, f.Thumbnail AS FilmThumbnail, f.AgeRating AS AgeRating, c.Name AS CinemaName, r.Name AS RoomName, ISNULL(r.Status, 'active') AS RoomStatus, c.CityId AS CityId,
                       ISNULL(st.TotalSeats, 0) AS TotalSeats,
                       ISNULL(st.AvailableSeats, 0) AS AvailableSeats
                FROM Showtimes s
                JOIN Films f ON f.Id = s.FilmId
                JOIN Cinemas c ON c.Id = s.CinemaId
                JOIN Rooms r ON r.Id = s.RoomId
                LEFT JOIN (
                    SELECT ss.ShowtimeId,
                           SUM(CASE WHEN ss.Status != 'maintenance' THEN 1 ELSE 0 END) AS TotalSeats,
                           SUM(CASE WHEN ss.Status = 'available' AND (ss.HeldUntil IS NULL OR ss.HeldUntil < GETDATE()) THEN 1 ELSE 0 END) AS AvailableSeats
                    FROM ShowtimeSeats ss
                    GROUP BY ss.ShowtimeId
                ) st ON st.ShowtimeId = s.Id
                WHERE s.Id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot find showtime by id", ex);
        }
    }

    private void bindInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private Showtime map(ResultSet rs) throws SQLException {
        Showtime showtime = new Showtime();
        showtime.setId(rs.getInt("Id"));
        showtime.setFilmId(rs.getInt("FilmId"));
        showtime.setCinemaId(rs.getInt("CinemaId"));
        showtime.setCityId(rs.getInt("CityId"));
        showtime.setRoomId(rs.getInt("RoomId"));
        showtime.setFilmTitle(rs.getString("FilmTitle"));
        showtime.setCinemaName(rs.getString("CinemaName"));
        showtime.setRoomName(rs.getString("RoomName"));
        showtime.setRoomStatus(rs.getString("RoomStatus"));
        showtime.setTotalSeats(rs.getInt("TotalSeats"));
        showtime.setAvailableSeats(rs.getInt("AvailableSeats"));
        showtime.setStartTime(toLocalDateTime(rs.getTimestamp("StartTime")));
        showtime.setEndTime(toLocalDateTime(rs.getTimestamp("EndTime")));
        showtime.setBasePrice(rs.getBigDecimal("BasePrice"));
        showtime.setThumbnail(rs.getString("FilmThumbnail"));
        showtime.setAgeRating(rs.getString("AgeRating"));
        showtime.setFormat(rs.getString("Format"));
        showtime.setVersion(rs.getString("Version"));
        showtime.setLanguage(rs.getString("Language"));
        showtime.setCreatedAt(toLocalDateTime(rs.getTimestamp("CreatedAt")));
        showtime.setUpdatedAt(toLocalDateTime(rs.getTimestamp("UpdatedAt")));
        showtime.setSaleStatus(rs.getString("SaleStatus"));
        showtime.setDeleteRequestedAt(toLocalDateTime(rs.getTimestamp("DeleteRequestedAt")));
        showtime.setDeleteNotBefore(toLocalDateTime(rs.getTimestamp("DeleteNotBefore")));
        int requestedBy = rs.getInt("DeleteRequestedByUserId");
        showtime.setDeleteRequestedByUserId(rs.wasNull() ? null : requestedBy);
        return showtime;
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}

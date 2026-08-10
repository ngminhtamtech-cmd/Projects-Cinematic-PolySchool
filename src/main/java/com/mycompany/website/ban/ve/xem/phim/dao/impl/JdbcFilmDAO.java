package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.dao.BaseDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.FilmDAO;
import com.mycompany.website.ban.ve.xem.phim.model.Film;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcFilmDAO extends BaseDAO implements FilmDAO {
    private static final String FILM_COLUMNS = "f.*, (SELECT STRING_AGG(c.Title, ', ') "
            + "WITHIN GROUP (ORDER BY c.Title) FROM FilmCategories fc "
            + "JOIN Categories c ON c.Id=fc.CategoryId WHERE fc.FilmId=f.Id) AS Categories";

    @Override
    public List<Film> findFeatured(int limit) {
        String sql = "SELECT TOP (?) " + FILM_COLUMNS
                + " FROM Films f WHERE f.DeletedAt IS NULL ORDER BY COALESCE(Rating, 0) DESC, CreatedAt DESC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            return readMany(ps);
        } catch (SQLException ex) {
            throw new DaoException("Cannot load featured films", ex);
        }
    }

    @Override
    public List<Film> search(String keyword, int offset, int limit) {
        String sql = "SELECT " + FILM_COLUMNS
                + " FROM Films f WHERE f.DeletedAt IS NULL AND (? = '' OR Title COLLATE Latin1_General_CI_AI LIKE ?"
                + " OR ISNULL(OtherTitles, '') COLLATE Latin1_General_CI_AI LIKE ?"
                + " OR ISNULL(Actors, '') COLLATE Latin1_General_CI_AI LIKE ?)"
                + " ORDER BY CreatedAt DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        String q = keyword == null ? "" : keyword.trim();
        String like = "%" + q + "%";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, q);
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setString(4, like);
            ps.setInt(5, Math.max(0, offset));
            ps.setInt(6, Math.max(1, limit));
            return readMany(ps);
        } catch (SQLException ex) {
            throw new DaoException("Cannot search films", ex);
        }
    }

    @Override
    public List<Film> search(String keyword, String status, int offset, int limit) {
        String sql = "SELECT " + FILM_COLUMNS
                + " FROM Films f WHERE f.DeletedAt IS NULL AND (? = '' OR Title COLLATE Latin1_General_CI_AI LIKE ?"
                + " OR ISNULL(OtherTitles, '') COLLATE Latin1_General_CI_AI LIKE ?"
                + " OR ISNULL(Actors, '') COLLATE Latin1_General_CI_AI LIKE ?)"
                + " AND (? = '' OR ISNULL(Status, 'showing') = ?)"
                + " ORDER BY CreatedAt DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        String q = keyword == null ? "" : keyword.trim();
        String st = status == null ? "" : status.trim();
        String like = "%" + q + "%";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, q);
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setString(4, like);
            ps.setString(5, st);
            ps.setString(6, st);
            ps.setInt(7, Math.max(0, offset));
            ps.setInt(8, Math.max(1, limit));
            return readMany(ps);
        } catch (SQLException ex) {
            throw new DaoException("Cannot search films", ex);
        }
    }

    @Override
    public long countSearch(String keyword, String status) {
        String sql = """
                SELECT COUNT(*)
                FROM Films
                WHERE DeletedAt IS NULL AND (? = '' OR Title COLLATE Latin1_General_CI_AI LIKE ?
                   OR ISNULL(OtherTitles, '') COLLATE Latin1_General_CI_AI LIKE ?
                   OR ISNULL(Actors, '') COLLATE Latin1_General_CI_AI LIKE ?)
                  AND (? = '' OR ISNULL(Status, 'showing') = ?)
                """;
        String q = keyword == null ? "" : keyword.trim();
        String st = status == null ? "" : status.trim();
        String like = "%" + q + "%";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, q);
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setString(4, like);
            ps.setString(5, st);
            ps.setString(6, st);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot count films", ex);
        }
    }

    /**
     * Ban SQL cua {@code FilmAvailabilityPolicy.isPubliclyVisible}: an phim bi bien tap rut
     * ({@code Status='ended'}) va phim da qua {@code EndDate}. {@code COMING} van hien de quang ba,
     * {@code EndDate} rong nghia la chua gioi han lich chieu.
     *
     * <p>Ngay lay tu {@code GETDATE()} — cung dong ho ma {@code BusinessClock} dong bo theo, nen
     * hai duong loc khong the ra hai ket qua khac nhau.</p>
     */
    private static final String PUBLIC_LIFECYCLE_PREDICATE = """
              AND ISNULL(f.Status, 'showing') <> 'ended'
              AND f.DeletedAt IS NULL
              AND (f.EndDate IS NULL OR f.EndDate >= CAST(GETDATE() AS DATE))
            """;

    private static final String PUBLIC_SEARCH_FILTER = """
            WHERE (? = '' OR f.Title COLLATE Latin1_General_CI_AI LIKE ?
               OR ISNULL(f.OtherTitles, '') COLLATE Latin1_General_CI_AI LIKE ?
               OR ISNULL(f.Actors, '') COLLATE Latin1_General_CI_AI LIKE ?)
              AND (? = '' OR ISNULL(f.Status, 'showing') = ?)
            """ + PUBLIC_LIFECYCLE_PREDICATE;

    @Override
    public List<Film> searchPublic(String keyword, String status, int offset, int limit) {
        String sql = "SELECT " + FILM_COLUMNS + "\nFROM Films f\n" + PUBLIC_SEARCH_FILTER
                + "ORDER BY CreatedAt DESC\nOFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        String q = keyword == null ? "" : keyword.trim();
        String st = status == null ? "" : status.trim();
        String like = "%" + q + "%";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindPublicSearch(ps, q, like, st);
            ps.setInt(7, Math.max(0, offset));
            ps.setInt(8, Math.max(1, limit));
            return readMany(ps);
        } catch (SQLException ex) {
            throw new DaoException("Cannot search public films", ex);
        }
    }

    @Override
    public long countSearchPublic(String keyword, String status) {
        String sql = "SELECT COUNT(*) FROM Films f\n" + PUBLIC_SEARCH_FILTER;
        String q = keyword == null ? "" : keyword.trim();
        String st = status == null ? "" : status.trim();
        String like = "%" + q + "%";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindPublicSearch(ps, q, like, st);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot count public films", ex);
        }
    }

    private static void bindPublicSearch(PreparedStatement ps, String q, String like, String st)
            throws SQLException {
        ps.setString(1, q);
        ps.setString(2, like);
        ps.setString(3, like);
        ps.setString(4, like);
        ps.setString(5, st);
        ps.setString(6, st);
    }

    @Override
    public Optional<Film> findById(int id) {
        String sql = "SELECT " + FILM_COLUMNS + " FROM Films f WHERE f.Id = ?"
                + PUBLIC_LIFECYCLE_PREDICATE;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot find film by id", ex);
        }
    }

    private List<Film> readMany(PreparedStatement ps) throws SQLException {
        List<Film> films = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                films.add(map(rs));
            }
        }
        return films;
    }

    private Film map(ResultSet rs) throws SQLException {
        Film film = new Film();
        film.setId(rs.getInt("Id"));
        film.setTitle(rs.getString("Title"));
        film.setOtherTitles(rs.getString("OtherTitles"));
        film.setActors(rs.getString("Actors"));
        film.setDirectors(rs.getString("Directors"));
        double rating = rs.getDouble("Rating");
        film.setRating(rs.wasNull() ? null : rating);
        Date releaseDate = rs.getDate("ReleaseDate");
        film.setReleaseDate(releaseDate == null ? null : releaseDate.toLocalDate());
        Date endDate = rs.getDate("EndDate");
        film.setEndDate(endDate == null ? null : endDate.toLocalDate());
        int duration = rs.getInt("DurationMinutes");
        film.setDurationMinutes(rs.wasNull() ? null : duration);
        film.setAgeRating(rs.getString("AgeRating"));
        film.setTrailerUrl(rs.getString("TrailerUrl"));
        film.setThumbnail(rs.getString("Thumbnail"));
        film.setLanguage(rs.getString("Language"));
        film.setSubtitles(rs.getString("Subtitles"));
        film.setDescription(rs.getString("Description"));
        film.setCountry(rs.getString("Country"));
        film.setFormat(rs.getString("Format"));
        film.setStatus(rs.getString("Status"));
        film.setBanner(rs.getString("Banner"));
        film.setCategories(rs.getString("Categories"));
        film.setCreatedAt(toLocalDateTime(rs.getTimestamp("CreatedAt")));
        film.setUpdatedAt(toLocalDateTime(rs.getTimestamp("UpdatedAt")));
        film.setDeletedAt(toLocalDateTime(rs.getTimestamp("DeletedAt")));
        int deletedBy = rs.getInt("DeletedByUserId");
        film.setDeletedByUserId(rs.wasNull() ? null : deletedBy);
        film.setDeletionMode(rs.getString("DeletionMode"));
        return film;
    }

    /**
     * Phim duoc gan cho mot cum rap.
     *
     * <p><b>Lo ro ri du lieu da bit o day (PU-01).</b> Ban cu nuot {@code SQLException} roi
     * {@code return search("", 0, 50)} — tra ve <i>toan bo</i> phim cua he thong. Hai duong dan
     * cung dan toi hau qua do:</p>
     * <ul>
     *   <li>Truy van loi (thieu bang/cot sau migration) -> im lang tra phim cua moi rap khac.</li>
     *   <li>Cum rap chua duoc gan phim nao -> cung tra toan bo phim, nen trang rap hien
     *       phim khong he chieu o day va nguoi dung dat ve vao suat khong ton tai.</li>
     * </ul>
     * <p>Gio: khong co mapping thi tra danh sach <b>rong</b> (empty state that), con loi truy van
     * thi nem {@link DaoException} kem nguyen nhan — hong phai bao la hong, khong duoc bien thanh
     * du lieu rong hon.</p>
     */
    @Override
    public List<Film> findByCinemaId(int cinemaId) {
        if (cinemaId <= 0) {
            return search("", 0, 50);
        }
        String sql = "SELECT " + FILM_COLUMNS
                + " FROM Films f INNER JOIN CinemaFilms cf ON f.Id = cf.FilmId"
                + " WHERE cf.CinemaId = ?"
                + " AND ISNULL(cf.Status, 'active') = 'active'"
                + PUBLIC_LIFECYCLE_PREDICATE
                + " ORDER BY f.CreatedAt DESC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cinemaId);
            return readMany(ps);
        } catch (SQLException ex) {
            throw new DaoException("Cannot load films for cinema " + cinemaId, ex);
        }
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}

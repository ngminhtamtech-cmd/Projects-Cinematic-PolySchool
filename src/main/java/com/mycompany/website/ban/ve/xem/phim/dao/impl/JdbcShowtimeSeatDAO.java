package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.dao.BaseDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.ShowtimeSeatDAO;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JdbcShowtimeSeatDAO extends BaseDAO implements ShowtimeSeatDAO {
    @Override
    public List<ShowtimeSeat> findSeatMap(int showtimeId) {
        try (Connection connection = getConnection()) {
            return findSeatMap(connection, showtimeId);
        } catch (SQLException ex) {
            throw new DaoException("Cannot load seat map", ex);
        }
    }

    @Override
    public List<ShowtimeSeat> findSeatMap(Connection connection, int showtimeId) {
        String sql = """
                SELECT ss.*, s.RoomId, s.RowLabel, s.SeatNumber, s.SeatType, s.SeatKey
                FROM ShowtimeSeats ss
                JOIN Seats s ON s.Id = ss.SeatId
                WHERE ss.ShowtimeId = ?
                ORDER BY s.RowLabel, s.SeatNumber
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapMany(rs);
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load seat map", ex);
        }
    }

    @Override
    public List<ShowtimeSeat> lockSeats(Connection connection, int showtimeId, List<Integer> showtimeSeatIds) {
        if (showtimeSeatIds == null || showtimeSeatIds.isEmpty()) {
            return List.of();
        }
        String placeholders = showtimeSeatIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = """
                SELECT ss.*, s.RoomId, s.RowLabel, s.SeatNumber, s.SeatType, s.SeatKey
                FROM ShowtimeSeats ss WITH (UPDLOCK, HOLDLOCK, ROWLOCK)
                JOIN Seats s ON s.Id = ss.SeatId
                WHERE ss.ShowtimeId = ? AND ss.Id IN (
                """ + placeholders + ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, showtimeId);
            for (int i = 0; i < showtimeSeatIds.size(); i++) {
                ps.setInt(i + 2, showtimeSeatIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return mapMany(rs);
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot lock showtime seats", ex);
        }
    }

    @Override
    public void releaseExpiredHolds(Connection connection, int showtimeId) {
        String sql = """
                UPDATE ShowtimeSeats
                SET Status = 'available', HeldByUserId = NULL, HeldAt = NULL, HeldUntil = NULL,
                    ClaimedByOrderId = NULL
                WHERE ShowtimeId = ? AND Status = 'held' AND HeldUntil IS NOT NULL AND HeldUntil < GETDATE()
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, showtimeId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Cannot release expired holds", ex);
        }
    }

    @Override
    public void releaseExpiredHolds(int showtimeId) {
        try (Connection connection = getConnection()) {
            releaseExpiredHolds(connection, showtimeId);
        } catch (SQLException ex) {
            throw new DaoException("Cannot open connection to release expired holds", ex);
        }
    }

    // Da xoa markHeld(..., LocalDateTime heldUntil): ban do ghi HeldUntil bang gio may ung dung
    // trong khi releaseExpiredHolds so sanh bang GETDATE() — dung lai chinh la loi B2/F-004.
    // Khong con caller nao; ban theo so phut duoi day de SQL Server tu tinh han giu.

    @Override
    public int countActiveHoldsByUser(
            Connection connection, int showtimeId, List<Integer> showtimeSeatIds, int userId) {
        if (showtimeSeatIds == null || showtimeSeatIds.isEmpty()) {
            return 0;
        }
        String placeholders = showtimeSeatIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        // Han giu duoc so sanh trong SQL bang GETDATE(): gio DB la nguon thoi gian duy nhat.
        String sql = """
                SELECT COUNT(*)
                FROM ShowtimeSeats
                WHERE ShowtimeId = ? AND Status = 'held' AND HeldByUserId = ?
                  AND HeldUntil IS NOT NULL AND HeldUntil > GETDATE()
                  AND Id IN (
                """ + placeholders + ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, showtimeId);
            ps.setInt(2, userId);
            for (int i = 0; i < showtimeSeatIds.size(); i++) {
                ps.setInt(i + 3, showtimeSeatIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot count active seat holds", ex);
        }
    }

    @Override
    public void markHeld(Connection connection, List<Integer> showtimeSeatIds, int userId, int holdMinutes) {
        markHeld(connection, showtimeSeatIds, userId, null, holdMinutes);
    }

    @Override
    public void markHeld(Connection connection, List<Integer> showtimeSeatIds, int userId, int orderId,
            int holdMinutes) {
        markHeld(connection, showtimeSeatIds, userId, Integer.valueOf(orderId), holdMinutes);
    }

    private void markHeld(Connection connection, List<Integer> showtimeSeatIds, int userId, Integer orderId,
            int holdMinutes) {
        if (showtimeSeatIds.isEmpty()) {
            return;
        }
        String placeholders = showtimeSeatIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        // BUG-04a (INV-1): han giu cho do MAY CHU quyet dinh. Ban cu dat lai HeldUntil vo dieu kien,
        // nen khach chi can POST lai la duoc gia han — giu ghe vinh vien va khoa ca suat chieu.
        // Ghe dang co hold con hieu luc CUA CHINH nguoi do thi giu nguyen ca HeldAt lan HeldUntil;
        // ghe available van duoc dat hold moi nhu cu. Ca hai ve so trong SQL nen dung gio DB.
        String sql = """
                UPDATE ShowtimeSeats
                SET Status = 'held',
                    HeldByUserId = ?,
                    ClaimedByOrderId = CASE
                        WHEN Status = 'held' AND HeldByUserId = ? AND HeldUntil > GETDATE()
                             AND ClaimedByOrderId IS NOT NULL THEN ClaimedByOrderId
                        ELSE ? END,
                    HeldAt = CASE
                        WHEN Status = 'held' AND HeldByUserId = ? AND HeldUntil > GETDATE() THEN HeldAt
                        ELSE GETDATE() END,
                    HeldUntil = CASE
                        WHEN Status = 'held' AND HeldByUserId = ? AND HeldUntil > GETDATE() THEN HeldUntil
                        ELSE DATEADD(MINUTE, ?, GETDATE()) END
                WHERE Id IN (
                """ + placeholders + ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            if (orderId == null) {
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(3, orderId);
            }
            ps.setInt(4, userId);
            ps.setInt(5, userId);
            ps.setInt(6, holdMinutes);
            for (int i = 0; i < showtimeSeatIds.size(); i++) {
                ps.setInt(i + 7, showtimeSeatIds.get(i));
            }
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Cannot mark seats held", ex);
        }
    }

    @Override
    public void markBooked(Connection connection, List<Integer> showtimeSeatIds) {
        if (showtimeSeatIds.isEmpty()) {
            return;
        }
        String placeholders = showtimeSeatIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = """
                UPDATE ShowtimeSeats
                SET Status = 'booked', HeldByUserId = NULL, HeldAt = NULL, HeldUntil = NULL,
                    ClaimedByOrderId = NULL
                WHERE Id IN (
                """ + placeholders + ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < showtimeSeatIds.size(); i++) {
                ps.setInt(i + 1, showtimeSeatIds.get(i));
            }
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Cannot mark seats booked", ex);
        }
    }

    @Override
    public void markBooked(Connection connection, List<Integer> showtimeSeatIds, int orderId) {
        if (showtimeSeatIds.isEmpty()) {
            return;
        }
        String placeholders = showtimeSeatIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = """
                UPDATE ShowtimeSeats
                SET Status = 'booked', HeldByUserId = NULL, HeldAt = NULL, HeldUntil = NULL,
                    ClaimedByOrderId = ?
                WHERE Id IN (
                """ + placeholders + ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            for (int i = 0; i < showtimeSeatIds.size(); i++) {
                ps.setInt(i + 2, showtimeSeatIds.get(i));
            }
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Cannot mark seats booked", ex);
        }
    }

    private List<ShowtimeSeat> mapMany(ResultSet rs) throws SQLException {
        List<ShowtimeSeat> seats = new ArrayList<>();
        while (rs.next()) {
            ShowtimeSeat seat = new ShowtimeSeat();
            seat.setId(rs.getInt("Id"));
            seat.setShowtimeId(rs.getInt("ShowtimeId"));
            seat.setSeatId(rs.getInt("SeatId"));
            seat.setRoomId(rs.getInt("RoomId"));
            seat.setSeatKey(rs.getString("SeatKey"));
            seat.setRowLabel(rs.getString("RowLabel"));
            seat.setSeatNumber(rs.getInt("SeatNumber"));
            seat.setSeatType(rs.getString("SeatType"));
            seat.setStatus(rs.getString("Status"));
            seat.setExtraFee(rs.getBigDecimal("ExtraFee"));
            int heldByUserId = rs.getInt("HeldByUserId");
            seat.setHeldByUserId(rs.wasNull() ? null : heldByUserId);
            int claimedByOrderId = rs.getInt("ClaimedByOrderId");
            seat.setClaimedByOrderId(rs.wasNull() ? null : claimedByOrderId);
            seat.setHeldAt(toLocalDateTime(rs.getTimestamp("HeldAt")));
            seat.setHeldUntil(toLocalDateTime(rs.getTimestamp("HeldUntil")));
            seats.add(seat);
        }
        return seats;
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}

package com.mycompany.website.ban.ve.xem.phim.dao;

import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import java.sql.Connection;
import java.util.List;

public interface ShowtimeSeatDAO {
    List<ShowtimeSeat> findSeatMap(int showtimeId);
    List<ShowtimeSeat> findSeatMap(Connection connection, int showtimeId);
    List<ShowtimeSeat> lockSeats(Connection connection, int showtimeId, List<Integer> showtimeSeatIds);
    void releaseExpiredHolds(Connection connection, int showtimeId);
    void releaseExpiredHolds(int showtimeId);
    void markHeld(Connection connection, List<Integer> showtimeSeatIds, int userId, int holdMinutes);
    void markHeld(Connection connection, List<Integer> showtimeSeatIds, int userId, int orderId,
            int holdMinutes);

    /**
     * So ghe trong danh sach ma nguoi dung nay <b>dang</b> giu hop le.
     *
     * <p>Han giu duoc so bang {@code GETDATE()} ngay trong SQL, nen mot hold da qua han khong con
     * duoc tinh du sweeper chua kip thu ghe ve. Dung de chan thanh toan sau khi het han giu ghe
     * (CB-ISS-005) mot cach xac dinh, thay vi phu thuoc vao viec chay dua voi sweeper.</p>
     */
    int countActiveHoldsByUser(Connection connection, int showtimeId, List<Integer> showtimeSeatIds, int userId);
    void markBooked(Connection connection, List<Integer> showtimeSeatIds);
    void markBooked(Connection connection, List<Integer> showtimeSeatIds, int orderId);
}

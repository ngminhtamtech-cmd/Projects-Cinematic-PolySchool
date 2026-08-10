package com.mycompany.website.ban.ve.xem.phim.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Serializes booking and administrative lifecycle commands for one showtime. */
final class ShowtimeLifecycleLock {
    private ShowtimeLifecycleLock() {
    }

    static void acquire(Connection connection, int showtimeId) throws SQLException {
        if (showtimeId <= 0) {
            throw new BookingException(400, "Suất chiếu không hợp lệ.");
        }
        // Activates the JDBC transaction before asking SQL Server for a transaction-owned lock.
        try (Statement starter = connection.createStatement()) {
            starter.execute("SELECT TOP (0) Id FROM Showtimes");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SET NOCOUNT ON;
                DECLARE @result INT;
                EXEC @result=sys.sp_getapplock
                     @Resource=?, @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=10000;
                SELECT @result AS LockResult;
                """)) {
            statement.setString(1, "cinebook:showtime-lifecycle:" + showtimeId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt("LockResult") < 0) {
                    throw new BookingException(503,
                            "Suất chiếu đang được xử lý ở một yêu cầu khác. Vui lòng thử lại.");
                }
            }
        }
    }
}

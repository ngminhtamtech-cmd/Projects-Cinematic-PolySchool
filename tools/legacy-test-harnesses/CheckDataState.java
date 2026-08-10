// Archived manual harness: excluded from Maven test compilation.
package com.mycompany.website.ban.ve.xem.phim;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckDataState {
    public static void main(String[] args) {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            System.out.println("=== DANH SACH PHONG CHIEU VA TRANG THAI REALTIME ===");
            try (ResultSet rs = stmt.executeQuery("SELECT r.Id, r.Name, ISNULL(r.Status, 'active') AS Status, c.Name AS CinemaName FROM Rooms r JOIN Cinemas c ON c.Id = r.CinemaId")) {
                while (rs.next()) {
                    System.out.println("Room ID #" + rs.getInt("Id") + " | " + rs.getString("Name") + " (" + rs.getString("CinemaName") + ") -> Status: [" + rs.getString("Status") + "]");
                }
            }

            System.out.println("\n=== DANH SACH THONG BAO ADMIN ===");
            try (ResultSet rs = stmt.executeQuery("SELECT Id, Title, TargetType, TargetId, CreatedAt FROM AdminNotifications")) {
                while (rs.next()) {
                    System.out.println("Note ID #" + rs.getInt("Id") + " | Title: " + rs.getString("Title") + " | TargetId: " + rs.getString("TargetId"));
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            DBConnection.shutdown();
        }
    }
}

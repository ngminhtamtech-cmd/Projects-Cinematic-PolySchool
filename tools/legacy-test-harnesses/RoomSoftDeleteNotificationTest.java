// Archived manual harness: excluded from Maven test compilation.
package com.mycompany.website.ban.ve.xem.phim;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.AdminNotification;
import com.mycompany.website.ban.ve.xem.phim.model.Room;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

public class RoomSoftDeleteNotificationTest {

    public static void main(String[] args) {
        System.out.println("=== BAT DAU KIEM THU SOFT DELETE PHONG CHIEU, AUDIT LOG & ADMIN NOTIFICATIONS ===");
        AdminService adminService = new AdminService();

        User mockAdmin = new User();
        mockAdmin.setId(1);
        mockAdmin.setUsername("admin_test");

        try {
            // 1. Lay danh sach phong hien co
            List<Room> rooms = adminService.listRooms();
            System.out.println("-> Tong so phong chieu hien co: " + rooms.size());

            // 1. Tao 1 phong chieu thu nghiem rieng biet cho test runner
            int roomId = 0;
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO Rooms (CinemaId, Name, Status) VALUES ((SELECT TOP 1 Id FROM Cinemas), 'Temp Test Room Soft Delete', 'active')", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) roomId = rs.getInt(1);
                }
            }

            if (roomId > 0) {
                System.out.println("-> Da tao phong chieu thu nghiem cho Test Runner ID #" + roomId);

                // Create a temporary showtime for this test room
                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement ps = conn.prepareStatement("INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice) VALUES ((SELECT TOP 1 Id FROM Films), (SELECT TOP 1 Id FROM Cinemas), ?, DATEADD(day, 1, GETDATE()), DATEADD(day, 1, DATEADD(hour, 2, GETDATE())), 100000)")) {
                    ps.setInt(1, roomId);
                    ps.executeUpdate();
                }

                // 2. Kiem tra impact info
                Map<String, Object> impact = adminService.getRoomDeleteImpactInfo(roomId);
                System.out.println("   + Showtimes count: " + impact.get("showtimeCount"));
                System.out.println("   + Total tickets count: " + impact.get("totalTicketCount"));
                System.out.println("   + Pending tickets count: " + impact.get("pendingTicketCount"));

                // 3. Thuc hien Soft Delete
                System.out.println("-> Thuc hien Soft Delete phong ID #" + roomId + "...");
                adminService.deleteRoom(roomId, mockAdmin);

                // 4. Kiem tra trang thai phong sau khi Soft Delete
                Room updatedRoom = adminService.findRoomById(roomId).orElse(null);
                if (updatedRoom != null) {
                    System.out.println("   + Trang thai phong sau delete: [" + updatedRoom.getStatus() + "]");
                    if ("inactive".equalsIgnoreCase(updatedRoom.getStatus())) {
                        System.out.println("✅ THUC HIEN SOFT DELETE THANH CONG: Phong chuyen sang 'inactive' de bao toan bao cao doanh thu!");
                    } else {
                        System.err.println("❌ LOI: Phong khong chuyen sang 'inactive'!");
                    }
                }

                // 5. Kiem tra ghi Audit Logs
                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement ps = conn.prepareStatement("SELECT TOP 1 Action, TargetType, TargetId, DetailJson FROM AuditLogs WHERE Action = 'SOFT_DELETE_ROOM' ORDER BY CreatedAt DESC");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("✅ KIEM TRA AUDIT LOG: Action=" + rs.getString("Action") + " | TargetId=" + rs.getString("TargetId") + " | Snapshot=" + rs.getString("DetailJson"));
                    } else {
                        System.err.println("❌ CHUA GHI DUOC AUDIT LOG FOR SOFT DELETE!");
                    }
                }

                // Cleanup temp test room and showtimes
                try (Connection conn = DBConnection.getConnection()) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Showtimes WHERE RoomId = ?")) {
                        ps.setInt(1, roomId);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Rooms WHERE Id = ?")) {
                        ps.setInt(1, roomId);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM AdminNotifications WHERE TargetId = ? AND Category = 'room'")) {
                        ps.setString(1, String.valueOf(roomId));
                        ps.executeUpdate();
                    }
                    System.out.println("-> Da dondep hoan toan phong test #" + roomId + " khoi DB.");
                }
            }

            // 7. Kiem tra Trung tam thong bao Admin
            adminService.checkAndNotifyCompletedInactiveRooms();
            List<AdminNotification> notifications = adminService.listAdminNotifications();
            int unreadCount = adminService.getUnreadNotificationCount();
            System.out.println("✅ ADMIN NOTIFICATION CENTER: Tong so thong bao=" + notifications.size() + " | Chua doc=" + unreadCount);

            System.out.println("=== HOAN THANH PERFECT TOAN BO TAP KIEM THU ===");

        } catch (Exception ex) {
            System.err.println("❌ CO LOI TRONG QUATRINH KIEM THU:");
            ex.printStackTrace();
        } finally {
            DBConnection.shutdown();
        }
    }
}

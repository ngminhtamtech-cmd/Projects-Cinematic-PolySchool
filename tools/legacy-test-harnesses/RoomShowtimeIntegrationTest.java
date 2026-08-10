// Archived manual harness: excluded from Maven test compilation.
package com.mycompany.website.ban.ve.xem.phim;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.model.Room;
import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

public class RoomShowtimeIntegrationTest {

    public static void main(String[] args) {
        System.out.println("=== BAT DAU KIEM THU 3 GIA DINH QUAN LY PHONG CHIEU ===");
        AdminService adminService = new AdminService();
        User actor = new User();
        actor.setId(1);
        actor.setRole("admin");

        try {
            // ----------------------------------------------------
            // GIA DINH 1: Xoa phong dang co suat chieu -> Khong duoc phep
            // ----------------------------------------------------
            System.out.println("\n--- [TEST 1] Kiem thu xoa phong dang co suat chieu ---");
            int roomWithShowtimeId = findRoomWithShowtimes();
            if (roomWithShowtimeId > 0) {
                try {
                    adminService.deleteRoom(roomWithShowtimeId, actor);
                    System.err.println("❌ TEST 1 THAT BAI: He thong cho phep xoa phong dang co suat chieu!");
                } catch (BookingException ex) {
                    if (ex.getMessage().contains("Không thể xóa phòng chiếu này vì phòng đã có suất chiếu tồn tại")) {
                        System.out.println("✅ TEST 1 THANH CONG: He thong chan xoa phong va thong bao loi chinh xac -> " + ex.getMessage());
                    } else {
                        System.out.println("⚠️ TEST 1 CANH BAO: Thong bao loi khac ky vong -> " + ex.getMessage());
                    }
                }
            } else {
                System.out.println("⚠️ TEST 1 SKIPPED: Khong tim thay phong nao dang co suat chieu trong DB.");
            }

            // ----------------------------------------------------
            // GIA DINH 2: Xoa phong rong -> Canh bao loss data & Ghi log audit
            // ----------------------------------------------------
            System.out.println("\n--- [TEST 2] Kiem thu xoa phong rong & Check AuditLogs ---");
            int validCinemaId = findFirstCinemaId();
            Room tempRoom = new Room();
            tempRoom.setCinemaId(validCinemaId);
            tempRoom.setName("Phòng Test Temp " + System.currentTimeMillis() % 10000);
            adminService.saveRoom(tempRoom, 2, 2, "A", false, actor);
            int tempRoomId = tempRoom.getId();
            System.out.println("-> Da tao phong thu nghiem Id=" + tempRoomId + ", Name=" + tempRoom.getName());

            adminService.deleteRoom(tempRoomId, actor);
            System.out.println("-> Da thuc hien xoa phong Id=" + tempRoomId);

            boolean auditLogged = checkAuditLogRecorded(actor.getId(), "DELETE_ROOM", String.valueOf(tempRoomId));
            if (auditLogged) {
                System.out.println("✅ TEST 2 THANH CONG: Xoa phong hop le thanh cong & Ban ghi AuditLogs da duoc ghi nhan!");
            } else {
                System.err.println("❌ TEST 2 THAT BAI: Xoa phong thanh cong nhung KHONG tim thay AuditLog!");
            }

            // ----------------------------------------------------
            // GIA DINH 3: Doi ten phong -> Khong anh huong suat chieu da tao
            // ----------------------------------------------------
            System.out.println("\n--- [TEST 3] Kiem thu doi ten phong & Hien thi suat chieu ---");
            List<Showtime> showtimesBefore = adminService.listShowtimes();
            if (!showtimesBefore.isEmpty()) {
                Showtime sampleShowtime = showtimesBefore.get(0);
                int targetRoomId = sampleShowtime.getRoomId();
                String originalRoomName = sampleShowtime.getRoomName();
                String newRoomName = "Phòng IMAX Test " + (System.currentTimeMillis() % 1000);

                System.out.println("-> Dang doi ten phong Id=" + targetRoomId + " tu '" + originalRoomName + "' thanh '" + newRoomName + "'");
                Room r = adminService.findRoomById(targetRoomId).orElseThrow();
                r.setName(newRoomName);
                adminService.saveRoom(r, 0, 0, null, false, actor);

                // Re-fetch showtimes to check if dynamic join reflects new name
                List<Showtime> showtimesAfter = adminService.listShowtimes();
                Showtime updatedShowtime = showtimesAfter.stream()
                        .filter(st -> st.getId() == sampleShowtime.getId())
                        .findFirst()
                        .orElse(null);

                if (updatedShowtime != null && newRoomName.equals(updatedShowtime.getRoomName())) {
                    System.out.println("✅ TEST 3 THANH CONG: Doi ten phong thanh cong, suat chieu tu dong cap nhat ten phong moi: '" + updatedShowtime.getRoomName() + "'");
                } else {
                    System.err.println("❌ TEST 3 THAT BAI: Suat chieu khong cap nhat ten phong moi!");
                }

                // Restore original room name
                r.setName(originalRoomName);
                adminService.saveRoom(r, 0, 0, null, false, actor);
                System.out.println("-> Da khoi phục ten phong ban dau: '" + originalRoomName + "'");
            } else {
                System.out.println("⚠️ TEST 3 SKIPPED: Khong tim thay suat chieu nao trong DB.");
            }

            System.out.println("\n=== HOAN THANH TOAN BO KIEM THU 3 GIA DINH ===");
        } catch (Exception ex) {
            System.err.println("❌ CO LOI KHONG MONG MUON TRONG QUA TRINH KIEM THU:");
            ex.printStackTrace();
        } finally {
            DBConnection.shutdown();
        }
    }

    private static int findRoomWithShowtimes() {
        String sql = "SELECT TOP 1 RoomId FROM Showtimes";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static boolean checkAuditLogRecorded(int actorUserId, String action, String targetId) {
        String sql = "SELECT COUNT(*) FROM AuditLogs WHERE ActorUserId = ? AND Action = ? AND TargetId = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, actorUserId);
            ps.setString(2, action);
            ps.setString(3, targetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    private static int findFirstCinemaId() {
        String sql = "SELECT TOP 1 Id FROM Cinemas";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 1;
    }
}

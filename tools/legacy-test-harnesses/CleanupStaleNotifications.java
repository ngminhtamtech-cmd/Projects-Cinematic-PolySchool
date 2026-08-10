// Archived manual harness: excluded from Maven test compilation.
package com.mycompany.website.ban.ve.xem.phim;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;


public class CleanupStaleNotifications {
    public static void main(String[] args) {
        System.out.println("=== THUC HIEN DON DEP THONG BAO STALE MOTO LOGIC DATA REALTIME ===");
        AdminService adminService = new AdminService();
        adminService.checkAndNotifyCompletedInactiveRooms();
        System.out.println("✅ Don dep thanh cong thong bao cua cac phong hien dang active!");
        DBConnection.shutdown();
    }
}

// Archived manual harness: excluded from Maven test compilation.
package com.mycompany.website.ban.ve.xem.phim;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import java.util.Map;

public class TestRoom20Impact {
    public static void main(String[] args) {
        System.out.println("=== BAT DAU TEST PHAN TICH TAC DONG PHONG CHIET REALTIME ===");
        AdminService adminService = new AdminService();
        try {
            // Find room #20 or any room named 'chạy kiểm thử xóa phòng'
            Map<String, Object> impact = adminService.getRoomDeleteImpactInfo(20);
            System.out.println("Result for Room ID #20:");
            for (Map.Entry<String, Object> entry : impact.entrySet()) {
                System.out.println(" - " + entry.getKey() + ": " + entry.getValue());
            }
        } catch (Exception ex) {
            System.err.println("❌ EXCEPTION:");
            ex.printStackTrace();
        } finally {
            DBConnection.shutdown();
        }
    }
}

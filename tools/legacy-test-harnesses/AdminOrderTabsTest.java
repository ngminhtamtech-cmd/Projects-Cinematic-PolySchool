// Archived manual harness: excluded from Maven test compilation.
package com.mycompany.website.ban.ve.xem.phim;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;

import java.util.List;
import java.util.stream.Collectors;

public class AdminOrderTabsTest {

    public static void main(String[] args) {
        System.out.println("=== BAT DAU KIEM THU 3 KHU VUC QUAN LY DON VE ADMIN & BAO TOAN DU LIEU ===");
        AdminService adminService = new AdminService();

        try {
            List<OrderRecord> allOrders = adminService.listOrdersForAdmin();
            System.out.println("-> Tong so don ve hien co trong Database: " + allOrders.size());

            List<OrderRecord> pendingCheckInOrders = allOrders.stream()
                    .filter(OrderRecord::isPendingCheckIn)
                    .collect(Collectors.toList());

            List<OrderRecord> redeemedOrders = allOrders.stream()
                    .filter(OrderRecord::isRedeemed)
                    .collect(Collectors.toList());

            List<OrderRecord> cancelledOrders = allOrders.stream()
                    .filter(OrderRecord::isCancelled)
                    .collect(Collectors.toList());

            System.out.println("-> So luong [Ve doi check-in]: " + pendingCheckInOrders.size());
            System.out.println("-> So luong [Ve da check-in]: " + redeemedOrders.size());
            System.out.println("-> So luong [Ve da huy]: " + cancelledOrders.size());

            int sumCategorized = pendingCheckInOrders.size() + redeemedOrders.size() + cancelledOrders.size();

            if (sumCategorized == allOrders.size()) {
                System.out.println("✅ TEST PHAN LOAI THANH CONG: Tong 3 Tab (" + sumCategorized + ") khop 100% voi tong so don ve (" + allOrders.size() + ") -> KHONG MAT BAT KY VE NAO!");
            } else {
                System.err.println("❌ TEST THAT BAI: Phat hien sai lech so luong don ve!");
            }

            System.out.println("=== HOAN THANH KIEM THU ADMIN ORDERS TABS ===");
        } catch (Exception ex) {
            System.err.println("❌ CO LOI TRONG QUA TRINH KIEM THU:");
            ex.printStackTrace();
        } finally {
            DBConnection.shutdown();
        }
    }
}

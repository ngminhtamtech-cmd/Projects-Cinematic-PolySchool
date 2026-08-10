// Archived manual harness: excluded from Maven test compilation.
package com.mycompany.website.ban.ve.xem.phim;

import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;

import java.time.LocalDateTime;

public class OrderRecordTest {

    public static void main(String[] args) {
        System.out.println("=== BAT DAU KIEM THU LOGIC PHAN LOAI VE HIEN TAI VAP VE DA SU DUNG ===");

        // Test Case 1: Vé mới mua, chưa xem, giờ chiếu ở tương lai -> Vé hiện tại
        OrderRecord futureTicket = new OrderRecord();
        futureTicket.setOrderStatus("confirmed");
        futureTicket.setPaymentStatus("paid");
        futureTicket.setStartTime(LocalDateTime.now().plusDays(1));

        if (futureTicket.isCurrentTicket() && !futureTicket.isUsedOrExpired()) {
            System.out.println("✅ TEST 1 THANH CONG: Ve tuong lai hop le duoc nhan dien la 'Ve hien tai' -> Label: " + futureTicket.getStatusLabel());
        } else {
            System.err.println("❌ TEST 1 THAT BAI!");
        }

        // Test Case 2: Vé đã qua giờ chiếu -> Vé đã sử dụng / Cũ
        OrderRecord pastTicket = new OrderRecord();
        pastTicket.setOrderStatus("confirmed");
        pastTicket.setPaymentStatus("paid");
        pastTicket.setStartTime(LocalDateTime.now().minusHours(2));

        if (!pastTicket.isCurrentTicket() && pastTicket.isUsedOrExpired()) {
            System.out.println("✅ TEST 2 THANH CONG: Ve qua gio chieu duoc nhan dien la 'Ve da su dung' -> Label: " + pastTicket.getStatusLabel());
        } else {
            System.err.println("❌ TEST 2 THAT BAI!");
        }

        // Test Case 3: Vé đã check-in (redeemed) -> Vé đã sử dụng
        OrderRecord redeemedTicket = new OrderRecord();
        redeemedTicket.setOrderStatus("redeemed");
        redeemedTicket.setPaymentStatus("paid");
        redeemedTicket.setStartTime(LocalDateTime.now().plusHours(5));

        if (!redeemedTicket.isCurrentTicket() && redeemedTicket.isUsedOrExpired()) {
            System.out.println("✅ TEST 3 THANH CONG: Ve da check-in duoc nhan dien la 'Ve da su dung' -> Label: " + redeemedTicket.getStatusLabel());
        } else {
            System.err.println("❌ TEST 3 THAT BAI!");
        }

        // Test Case 4: Vé đã bị hủy (cancelled) -> Vé đã sử dụng / Cũ
        OrderRecord cancelledTicket = new OrderRecord();
        cancelledTicket.setOrderStatus("cancelled");
        cancelledTicket.setPaymentStatus("paid");
        cancelledTicket.setStartTime(LocalDateTime.now().plusDays(2));

        if (!cancelledTicket.isCurrentTicket() && cancelledTicket.isUsedOrExpired()) {
            System.out.println("✅ TEST 4 THANH CONG: Ve da huy duoc nhan dien la 'Ve da su dung / Cũ' -> Label: " + cancelledTicket.getStatusLabel());
        } else {
            System.err.println("❌ TEST 4 THAT BAI!");
        }

        System.out.println("=== HOAN THANH TOAN BO TEST LOGIC VE ===");
    }
}

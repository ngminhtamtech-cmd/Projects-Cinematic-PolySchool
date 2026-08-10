package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminOrderActionPolicyTest {

    @Test
    void modelActionHintsMirrorCancellationAndCounterExpiryPolicy() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 12, 0);

        OrderRecord draft = order("card", "pending", "pending", null, now);
        assertTrue(draft.isAdminCancellable());
        assertFalse(draft.isCounterPaymentCollectable());

        OrderRecord liveCounter = order(
                "counter", "pending", "confirmed", now.plusMinutes(10), now);
        assertTrue(liveCounter.isAdminCancellable());
        assertTrue(liveCounter.isCounterPaymentCollectable());

        OrderRecord expiredCounter = order(
                "counter", "pending", "confirmed", now, now);
        assertTrue(expiredCounter.isAdminCancellable());
        assertFalse(expiredCounter.isCounterPaymentCollectable());

        OrderRecord paid = order("card", "paid", "confirmed", null, now);
        assertFalse(paid.isAdminCancellable());
        assertTrue(paid.isPaidConfirmed());
    }

    @Test
    void ordersPageUsesPolicyHelpersAndGuidesPaidOrdersToRefundWorkflow() throws Exception {
        String page = Files.readString(
                Path.of("src/main/webapp/WEB-INF/views/admin/orders.jsp"),
                StandardCharsets.UTF_8);

        assertTrue(page.contains("${order.adminCancellable}"));
        assertTrue(page.contains("${order.counterPaymentCollectable}"));
        assertTrue(page.contains("${order.paidConfirmed}"));
        assertTrue(page.contains("quy trình Duyệt/Từ chối hoàn tiền"));
        assertFalse(page.contains(
                "${order.orderStatus eq 'pending' or order.orderStatus eq 'confirmed'}"));
    }

    private OrderRecord order(String paymentMethod, String paymentStatus, String orderStatus,
            LocalDateTime counterExpiresAt, LocalDateTime businessNow) {
        OrderRecord order = new OrderRecord();
        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus(paymentStatus);
        order.setOrderStatus(orderStatus);
        order.setCounterExpiresAt(counterExpiresAt);
        order.setBusinessNow(businessNow);
        return order;
    }
}

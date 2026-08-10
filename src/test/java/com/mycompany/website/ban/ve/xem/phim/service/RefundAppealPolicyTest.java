package com.mycompany.website.ban.ve.xem.phim.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RefundAppealPolicyTest {

    @Test
    void permitsTheExactTwentyFourHourBoundary() {
        OrderRecord order = eligibleOrder();
        order.setBusinessNow(order.getEndTime().plusHours(24));
        assertTrue(RefundAppealPolicy.evaluate(order, false, 24).eligible());
    }

    @Test
    void rejectsAnythingPastTwentyFourHoursWithTheApprovedMessage() {
        OrderRecord order = eligibleOrder();
        order.setBusinessNow(order.getEndTime().plusHours(24).plusNanos(1));
        RefundAppealPolicy.Evaluation result = RefundAppealPolicy.evaluate(order, false, 24);
        assertEquals(RefundAppealPolicy.Status.EXPIRED, result.status());
        assertEquals(RefundAppealPolicy.EXPIRED_MESSAGE, result.message());
    }

    @Test
    void pendingAndRejectedTicketsCannotOpenAnotherAppeal() {
        OrderRecord order = eligibleOrder();
        assertEquals(RefundAppealPolicy.Status.PENDING,
                RefundAppealPolicy.evaluate(order, true, 24).status());
        order.setRefundRejectedAt(order.getBusinessNow());
        assertEquals(RefundAppealPolicy.Status.REJECTED,
                RefundAppealPolicy.evaluate(order, false, 24).status());
    }

    private OrderRecord eligibleOrder() {
        LocalDateTime end = LocalDateTime.of(2026, 8, 5, 14, 0);
        OrderRecord order = new OrderRecord();
        order.setPaymentStatus("paid");
        order.setOrderStatus("confirmed");
        order.setEndTime(end);
        order.setBusinessNow(end.plusHours(1));
        return order;
    }
}

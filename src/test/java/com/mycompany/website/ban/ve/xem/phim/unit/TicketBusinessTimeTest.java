package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F-004 — phan loai ve phai chay theo gio nghiep vu (gio DB) va dung o ranh gioi.
 *
 * <p>Hai ca "gio JVM lech" dung moc thoi gian nam rat xa hien tai (nam 2000 va nam 2099) nen neu
 * code con doc {@code LocalDateTime.now()} thi ket qua se nguoc han — day chinh la loi F-004.</p>
 */
class TicketBusinessTimeTest {
    private static final LocalDateTime START = LocalDateTime.of(2030, 5, 20, 19, 30);
    private static final int DURATION_MINUTES = 100;
    private static final LocalDateTime END = START.plusMinutes(DURATION_MINUTES);

    @AfterEach
    void restoreDefaultClock() {
        BusinessClock.resetForTesting();
    }

    private static OrderRecord paidTicketAt(LocalDateTime businessNow) {
        OrderRecord order = paidTicket();
        order.setBusinessNow(businessNow);
        return order;
    }

    private static OrderRecord paidTicket() {
        OrderRecord order = new OrderRecord();
        order.setOrderStatus("confirmed");
        order.setPaymentStatus("paid");
        order.setStartTime(START);
        order.setDurationMinutes(DURATION_MINUTES);
        return order;
    }

    @Test
    @DisplayName("Dung dung moc StartTime: van la ve cho check-in dung gio")
    void exactlyAtStartTimeIsOnTime() {
        OrderRecord order = paidTicketAt(START);

        assertTrue(order.isPendingOnTimeCheckIn());
        assertFalse(order.isLateCheckIn());
        assertTrue(order.isCurrentTicket());
        assertFalse(order.isRefundReview());
    }

    @Test
    @DisplayName("Mot phut sau StartTime: chuyen sang tre gio, chua phai hoan tien")
    void oneMinuteAfterStartIsLate() {
        OrderRecord order = paidTicketAt(START.plusMinutes(1));

        assertFalse(order.isPendingOnTimeCheckIn());
        assertTrue(order.isLateCheckIn());
        assertTrue(order.isCurrentTicket());
        assertFalse(order.isRefundReview());
        assertTrue(order.getStatusLabel().contains("Đang chiếu"), order.getStatusLabel());
    }

    @Test
    @DisplayName("Dung dung moc EndTime: ve con hieu luc, chua vao dien xem xet hoan tien")
    void exactlyAtEndTimeIsStillValid() {
        OrderRecord order = paidTicketAt(END);

        assertTrue(order.isCurrentTicket());
        assertTrue(order.isLateCheckIn());
        assertFalse(order.isRefundReview());
        assertEquals("badge-status-warning", order.getStatusBadgeClass());
    }

    @Test
    @DisplayName("Mot giay sau EndTime: het hieu luc va vao dien xem xet hoan tien")
    void oneSecondAfterEndTimeIsRefundReview() {
        OrderRecord order = paidTicketAt(END.plusSeconds(1));

        assertFalse(order.isCurrentTicket());
        assertFalse(order.isLateCheckIn());
        assertTrue(order.isRefundReview());
        assertTrue(order.getStatusLabel().contains("Bỏ lỡ"), order.getStatusLabel());
        assertEquals("badge-status-expired", order.getStatusBadgeClass());
    }

    @Test
    @DisplayName("Moi moc thoi gian chi thuoc dung mot trong ba trang thai check-in/hoan tien")
    void classificationIsMutuallyExclusive() {
        LocalDateTime[] instants = {
            START.minusDays(1), START.minusMinutes(1), START, START.plusMinutes(1),
            END.minusMinutes(1), END, END.plusSeconds(1), END.plusDays(1),
        };

        for (LocalDateTime instant : instants) {
            OrderRecord order = paidTicketAt(instant);
            int states = (order.isPendingOnTimeCheckIn() ? 1 : 0)
                    + (order.isLateCheckIn() ? 1 : 0)
                    + (order.isRefundReview() ? 1 : 0);
            assertEquals(1, states, "Moc " + instant + " phai thuoc dung mot trang thai");
        }
    }

    @Test
    @DisplayName("Gio JVM di truoc rat xa: phan loai van theo gio DB (truoc gio chieu)")
    void followsDatabaseClockWhenJvmClockRunsAhead() {
        LocalDateTime pastStart = LocalDateTime.of(2000, 1, 1, 19, 30);
        BusinessClock.useFixedTimeForTesting(pastStart.minusMinutes(10));

        OrderRecord order = new OrderRecord();
        order.setOrderStatus("confirmed");
        order.setPaymentStatus("paid");
        order.setStartTime(pastStart);
        order.setDurationMinutes(DURATION_MINUTES);

        assertTrue(order.isPendingOnTimeCheckIn(), "Gio DB noi chua den gio chieu");
        assertFalse(order.isLateCheckIn());
        assertFalse(order.isRefundReview());
        assertTrue(order.isCurrentTicket());
    }

    @Test
    @DisplayName("Gio JVM di sau rat xa: phan loai van theo gio DB (da qua suat chieu)")
    void followsDatabaseClockWhenJvmClockLagsBehind() {
        LocalDateTime futureStart = LocalDateTime.of(2099, 1, 1, 19, 30);
        BusinessClock.useFixedTimeForTesting(futureStart.plusMinutes(DURATION_MINUTES + 30));

        OrderRecord order = new OrderRecord();
        order.setOrderStatus("confirmed");
        order.setPaymentStatus("paid");
        order.setStartTime(futureStart);
        order.setDurationMinutes(DURATION_MINUTES);

        assertTrue(order.isRefundReview(), "Gio DB noi suat chieu da ket thuc");
        assertFalse(order.isCurrentTicket());
        assertFalse(order.isPendingOnTimeCheckIn());
        assertFalse(order.isLateCheckIn());
    }

    @Test
    @DisplayName("Don da huy/da check-in khong bao gio bi xep vao dien hoan tien")
    void terminalStatusesAreNeverRefundReview() {
        OrderRecord cancelled = paidTicketAt(END.plusDays(1));
        cancelled.setOrderStatus("cancelled");
        OrderRecord redeemed = paidTicketAt(END.plusDays(1));
        redeemed.setOrderStatus("redeemed");

        assertFalse(cancelled.isRefundReview());
        assertFalse(redeemed.isRefundReview());
        assertFalse(cancelled.isCurrentTicket());
        assertFalse(redeemed.isCurrentTicket());
    }
}

package com.mycompany.website.ban.ve.xem.phim.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.dao.OrderDAO;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F-004 — khung gio check-in cua quay ve phai cham diem bang gio DB.
 *
 * <p>Hai ca cuoi cho thay ro loi goc: neu {@code lookupTicket} con doc {@code LocalDateTime.now()}
 * thi mot suat chieu nam 2000 luon bi bao TOO_LATE va mot suat nam 2099 luon bi bao TOO_EARLY, bat
 * ke gio DB noi gi.</p>
 */
class StaffCheckInWindowTest {
    private static final String TICKET = "CB-STAFF-WINDOW";
    private static final int DURATION_MINUTES = 100;

    @AfterEach
    void restoreDefaultClock() {
        BusinessClock.resetForTesting();
    }

    private static OrderRecord paidOrder(LocalDateTime startTime) {
        OrderRecord order = new OrderRecord();
        order.setId(9001);
        order.setTicketCode(TICKET);
        order.setOrderStatus("confirmed");
        order.setPaymentStatus("paid");
        order.setPaymentMethod("card");
        order.setRoomName("P1");
        order.setStartTime(startTime);
        order.setDurationMinutes(DURATION_MINUTES);
        return order;
    }

    private static StaffService serviceFor(OrderRecord order) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("findByTicketCode".equals(method.getName())) {
                return TICKET.equalsIgnoreCase((String) args[0])
                        ? Optional.of(order)
                        : Optional.empty();
            }
            Class<?> type = method.getReturnType();
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return 0;
            }
            return type.isPrimitive() ? 0 : null;
        };
        OrderDAO dao = (OrderDAO) Proxy.newProxyInstance(
                StaffCheckInWindowTest.class.getClassLoader(), new Class<?>[] {OrderDAO.class}, handler);
        return new StaffService(dao);
    }

    /** Quan tri he thong: {@code CinemaId = null} nen nam trong pham vi moi rap. */
    private static User systemAdmin() {
        User user = new User();
        user.setId(5);
        user.setRole("admin");
        user.setCinemaId(null);
        return user;
    }

    private static StaffService.TicketLookup lookupWithDatabaseTime(
            LocalDateTime startTime, LocalDateTime databaseNow) {
        BusinessClock.useFixedTimeForTesting(databaseNow);
        // C.2: ban khong co actor da bi xoa. Quan tri he thong (CinemaId = null) nam trong pham
        // vi moi rap, nen khong lam doi y nghia cua cac ca do khung gio check-in o day.
        return serviceFor(paidOrder(startTime)).lookupTicket(TICKET, systemAdmin());
    }

    @Test
    @DisplayName("61 phut truoc suat chieu: chua mo cong check-in")
    void beforeGateOpensIsTooEarly() {
        LocalDateTime start = LocalDateTime.of(2030, 5, 20, 19, 30);

        StaffService.TicketLookup lookup = lookupWithDatabaseTime(start, start.minusMinutes(61));

        assertEquals(StaffService.Verdict.TOO_EARLY, lookup.getVerdict(), lookup.getMessage());
    }

    @Test
    @DisplayName("Dung 60 phut truoc suat chieu: cong vua mo, cho check-in")
    void exactlyAtGateOpenIsReady() {
        LocalDateTime start = LocalDateTime.of(2030, 5, 20, 19, 30);

        StaffService.TicketLookup lookup = lookupWithDatabaseTime(
                start, start.minusMinutes(StaffService.CHECK_IN_OPENS_MINUTES_BEFORE));

        assertEquals(StaffService.Verdict.READY, lookup.getVerdict(), lookup.getMessage());
    }

    @Test
    @DisplayName("Dung moc EndTime: van check-in duoc")
    void exactlyAtEndTimeIsStillReady() {
        LocalDateTime start = LocalDateTime.of(2030, 5, 20, 19, 30);

        StaffService.TicketLookup lookup = lookupWithDatabaseTime(
                start, start.plusMinutes(DURATION_MINUTES));

        assertEquals(StaffService.Verdict.READY, lookup.getVerdict(), lookup.getMessage());
    }

    @Test
    @DisplayName("Mot phut sau EndTime: qua gio, huong khach sang xin hoan tien")
    void oneMinuteAfterEndTimeIsTooLate() {
        LocalDateTime start = LocalDateTime.of(2030, 5, 20, 19, 30);

        StaffService.TicketLookup lookup = lookupWithDatabaseTime(
                start, start.plusMinutes(DURATION_MINUTES + 1));

        assertEquals(StaffService.Verdict.TOO_LATE, lookup.getVerdict(), lookup.getMessage());
    }

    @Test
    @DisplayName("Gio JVM di truoc 30 nam: gio DB noi dang trong khung gio thi phai READY")
    void followsDatabaseClockWhenJvmClockRunsAhead() {
        LocalDateTime start = LocalDateTime.of(2000, 1, 1, 19, 30);

        StaffService.TicketLookup lookup = lookupWithDatabaseTime(start, start.minusMinutes(10));

        assertEquals(StaffService.Verdict.READY, lookup.getVerdict(), lookup.getMessage());
    }

    @Test
    @DisplayName("Gio JVM di sau 70 nam: gio DB noi da het suat thi phai TOO_LATE")
    void followsDatabaseClockWhenJvmClockLagsBehind() {
        LocalDateTime start = LocalDateTime.of(2099, 1, 1, 19, 30);

        StaffService.TicketLookup lookup = lookupWithDatabaseTime(
                start, start.plusMinutes(DURATION_MINUTES + 30));

        assertEquals(StaffService.Verdict.TOO_LATE, lookup.getVerdict(), lookup.getMessage());
    }

    @Test
    @DisplayName("Ket luan va nhan trang thai tren giao dien dung cung mot moc gio")
    void verdictAndDisplayShareTheSameInstant() {
        LocalDateTime start = LocalDateTime.of(2000, 1, 1, 19, 30);
        LocalDateTime databaseNow = start.plusMinutes(10);

        StaffService.TicketLookup lookup = lookupWithDatabaseTime(start, databaseNow);

        assertEquals(StaffService.Verdict.READY, lookup.getVerdict(), lookup.getMessage());
        assertEquals(databaseNow, lookup.getOrder().getBusinessNow(),
                "Ban ghi tra ve phai ghim dung moc gio da dung de cham diem");
        assertTrue(lookup.getOrder().isLateCheckIn(),
                "Nhan trang thai phai noi 'dang chieu' giong ket luan tre gio cua nhan vien");
    }
}

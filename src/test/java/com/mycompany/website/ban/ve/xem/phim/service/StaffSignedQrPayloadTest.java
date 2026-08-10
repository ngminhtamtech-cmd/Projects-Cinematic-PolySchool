package com.mycompany.website.ban.ve.xem.phim.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mycompany.website.ban.ve.xem.phim.dao.OrderDAO;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
import com.mycompany.website.ban.ve.xem.phim.util.QrCodeUtil;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Proves that staff consumes the signed payload emitted by the real ticket QR endpoint. */
@DisplayName("Staff signed QR payload")
class StaffSignedQrPayloadTest {
    private static final String NEW_CODE = "CBABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LEGACY_CODE = "CB1722240000000ABCD";
    private static final LocalDateTime START = LocalDateTime.of(2030, 8, 1, 20, 0);
    private static String previousDatabaseConfig;

    @BeforeAll
    static void configureSecret() {
        previousDatabaseConfig = System.getProperty("cinebook.db.config");
        System.setProperty("cinebook.db.config",
                Path.of("src/test/resources/qr-test.properties").toAbsolutePath().toString());
    }

    @AfterAll
    static void restoreConfig() {
        if (previousDatabaseConfig == null) {
            System.clearProperty("cinebook.db.config");
        } else {
            System.setProperty("cinebook.db.config", previousDatabaseConfig);
        }
    }

    @AfterEach
    void resetClock() {
        BusinessClock.resetForTesting();
    }

    @Test
    @DisplayName("signed product QR is canonicalized before lookup")
    void signedQrLooksUpBareTicketCode() {
        Fixture fixture = fixture(NEW_CODE);
        BusinessClock.useFixedTimeForTesting(START.minusMinutes(10));

        StaffService.TicketLookup lookup = fixture.service.lookupTicket(
                QrCodeUtil.signedPayload(NEW_CODE), admin());

        assertEquals(StaffService.Verdict.READY, lookup.getVerdict());
        assertEquals(List.of(NEW_CODE), fixture.lookups);
    }

    @Test
    @DisplayName("tampered signature fails closed without querying a ticket")
    void tamperedQrNeverTouchesDaoOrRedeem() {
        Fixture fixture = fixture(NEW_CODE);
        String tampered = QrCodeUtil.signedPayload(NEW_CODE) + "x";

        StaffService.TicketLookup lookup = fixture.service.lookupTicket(tampered, admin());
        BookingException error = assertThrows(BookingException.class,
                () -> fixture.service.checkIn(tampered, admin()));

        assertEquals(StaffService.Verdict.NOT_FOUND, lookup.getVerdict());
        assertEquals(404, error.getStatusCode());
        assertEquals(List.of(), fixture.lookups);
        assertNull(fixture.adminService.redeemedCode);
    }

    @Test
    @DisplayName("manual entry accepts a bare current ticket code")
    void bareCurrentTicketRemainsUsable() {
        Fixture fixture = fixture(NEW_CODE);
        BusinessClock.useFixedTimeForTesting(START.minusMinutes(10));

        StaffService.TicketLookup lookup = fixture.service.lookupTicket(NEW_CODE, admin());

        assertEquals(StaffService.Verdict.READY, lookup.getVerdict());
        assertEquals(List.of(NEW_CODE), fixture.lookups);
    }

    @Test
    @DisplayName("legacy unsigned ticket remains usable")
    void legacyUnsignedTicketRemainsUsable() {
        Fixture fixture = fixture(LEGACY_CODE);
        BusinessClock.useFixedTimeForTesting(START.minusMinutes(10));

        StaffService.TicketLookup lookup = fixture.service.lookupTicket(LEGACY_CODE, admin());

        assertEquals(StaffService.Verdict.READY, lookup.getVerdict());
        assertEquals(List.of(LEGACY_CODE), fixture.lookups);
    }

    @Test
    @DisplayName("check-in forwards only the canonical ticket code")
    void signedQrCheckInRedeemsAndRefreshesByBareCode() {
        Fixture fixture = fixture(NEW_CODE);
        BusinessClock.useFixedTimeForTesting(START.minusMinutes(10));

        StaffService.TicketLookup lookup = fixture.service.checkIn(
                QrCodeUtil.signedPayload(NEW_CODE), admin());

        assertEquals(StaffService.Verdict.CHECKED_IN, lookup.getVerdict());
        assertEquals(NEW_CODE, fixture.adminService.redeemedCode);
        assertEquals(List.of(NEW_CODE, NEW_CODE), fixture.lookups);
    }

    private static Fixture fixture(String ticketCode) {
        OrderRecord order = new OrderRecord();
        order.setId(9101);
        order.setTicketCode(ticketCode);
        order.setOrderStatus("confirmed");
        order.setPaymentStatus("paid");
        order.setPaymentMethod("card");
        order.setRoomName("P1");
        order.setStartTime(START);
        order.setDurationMinutes(120);

        List<String> lookups = new ArrayList<>();
        OrderDAO dao = (OrderDAO) Proxy.newProxyInstance(
                StaffSignedQrPayloadTest.class.getClassLoader(),
                new Class<?>[]{OrderDAO.class},
                (proxy, method, args) -> {
                    if ("findByTicketCode".equals(method.getName())) {
                        String requested = (String) args[0];
                        lookups.add(requested);
                        return ticketCode.equals(requested) ? Optional.of(order) : Optional.empty();
                    }
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) {
                        return false;
                    }
                    if (type == int.class) {
                        return 0;
                    }
                    return type.isPrimitive() ? 0 : null;
                });
        RecordingAdminService adminService = new RecordingAdminService();
        return new Fixture(new StaffService(dao, adminService), adminService, lookups);
    }

    private static User admin() {
        User actor = new User();
        actor.setId(5);
        actor.setRole("admin");
        return actor;
    }

    private static final class RecordingAdminService extends AdminService {
        private String redeemedCode;

        @Override
        public void redeemTicket(String ticketCode, User actor) {
            redeemedCode = ticketCode;
        }
    }

    private record Fixture(StaffService service, RecordingAdminService adminService,
            List<String> lookups) {
    }
}

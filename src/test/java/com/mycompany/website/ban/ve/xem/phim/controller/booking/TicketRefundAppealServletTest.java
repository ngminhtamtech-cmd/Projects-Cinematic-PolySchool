package com.mycompany.website.ban.ve.xem.phim.controller.booking;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.dao.AdminNotificationDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.OrderDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.UserAppealDAO;
import com.mycompany.website.ban.ve.xem.phim.model.AdminNotification;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.model.UserAppeal;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-001 — member chi duoc doc va tao khang cao cho don hang cua chinh minh.
 */
public class TicketRefundAppealServletTest {
    private static final int OWNER_ID = 101;
    private static final String OWNER_TICKET = "TICKET-OWN";
    private static final String FOREIGN_TICKET = "TICKET-FOREIGN";

    private User owner;
    private OrderRecord ownedOrder;
    private OrderRecord foreignOrder;
    private DaoState daoState;
    private TicketRefundAppealServlet servlet;

    @BeforeEach
    public void setUp() {
        owner = user(OWNER_ID, "owner@test.local", "Owner");
        ownedOrder = order(11, OWNER_ID, OWNER_TICKET, "Phim cua owner");
        foreignOrder = order(22, 202, FOREIGN_TICKET, "Phim cua nguoi khac");
        daoState = new DaoState(ownedOrder, foreignOrder);
        servlet = new TicketRefundAppealServlet(
                fakeOrderDao(daoState), fakeAppealDao(daoState), fakeNotificationDao(daoState));
    }

    @Test
    @DisplayName("GET ma ve cua user B: 404, khong fallback lookup va khong render don")
    public void testForeignTicketGetIsNotDisclosed() throws Exception {
        Exchange exchange = new Exchange(owner);
        exchange.params.put("ticketCode", FOREIGN_TICKET);

        servlet.doGet(exchange.request(), exchange.response());

        assertEquals(HttpServletResponse.SC_NOT_FOUND, exchange.status);
        assertEquals(0, daoState.unrestrictedLookupCount);
        assertNull(exchange.requestAttributes.get("targetOrder"));
        assertFalse(exchange.forwarded);
    }

    @Test
    @DisplayName("POST ma ve cua user B: 404, khong tao appeal hay notification")
    public void testForeignTicketPostHasNoSideEffects() throws Exception {
        Exchange exchange = validPost(FOREIGN_TICKET);

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals(HttpServletResponse.SC_NOT_FOUND, exchange.status);
        assertEquals(0, daoState.unrestrictedLookupCount);
        assertEquals(0, daoState.appealCreateCount);
        assertEquals(0, daoState.notificationCreateCount);
        assertNull(exchange.redirect);
    }

    @Test
    @DisplayName("POST ticket user B van 404 neu reason malformed, khong tao side effect")
    public void testForeignTicketIsRejectedBeforeReasonValidation() throws Exception {
        Exchange exchange = validPost(FOREIGN_TICKET);
        exchange.params.put("reason", "");

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals(HttpServletResponse.SC_NOT_FOUND, exchange.status);
        assertEquals(0, daoState.appealCreateCount);
        assertEquals(0, daoState.notificationCreateCount);
        assertNull(exchange.redirect);
    }

    @Test
    @DisplayName("Servlet van chan neu owner-scoped DAO tra nham don cua user B")
    public void testOwnerScopedDaoMismatchIsRejectedInDepth() throws Exception {
        daoState.ownerLookupReturnsForeign = true;
        Exchange exchange = validPost(FOREIGN_TICKET);

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals(HttpServletResponse.SC_NOT_FOUND, exchange.status);
        assertEquals(0, daoState.appealCreateCount);
        assertEquals(0, daoState.notificationCreateCount);
    }

    @Test
    @DisplayName("GET ma ve cua chinh user: render dung don qua owner-scoped lookup")
    public void testOwnedTicketGetStillWorks() throws Exception {
        Exchange exchange = new Exchange(owner);
        exchange.params.put("ticketCode", OWNER_TICKET);

        servlet.doGet(exchange.request(), exchange.response());

        assertEquals(HttpServletResponse.SC_OK, exchange.status);
        assertEquals(0, daoState.unrestrictedLookupCount);
        assertSame(ownedOrder, exchange.requestAttributes.get("targetOrder"));
        assertEquals(OWNER_TICKET, exchange.requestAttributes.get("ticketCode"));
        assertTrue(exchange.forwarded);
    }

    @Test
    @DisplayName("POST ma ve cua chinh user: appeal va notification cung tro mot don")
    public void testOwnedTicketPostKeepsIdentifiersAligned() throws Exception {
        Exchange exchange = validPost(OWNER_TICKET.toLowerCase());

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals(1, daoState.appealCreateCount);
        assertEquals(1, daoState.notificationCreateCount);
        assertEquals(0, daoState.unrestrictedLookupCount);
        assertEquals(OWNER_ID, daoState.createdAppeal.getUserId());
        assertEquals(ownedOrder.getUserId(), daoState.createdAppeal.getUserId());
        assertEquals(OWNER_TICKET, daoState.createdAppeal.getTicketCode());
        assertEquals("UserAppeal", daoState.createdNotification.getTargetType());
        assertEquals("501", daoState.createdNotification.getTargetId());
        assertTrue(daoState.createdNotification.getTitle().contains(OWNER_TICKET));
        assertEquals("/cinebook/orders", exchange.redirect);
    }

    @Test
    @DisplayName("POST ve chua ket thuc: redirect kem flash va khong tao side effect")
    public void testFutureTicketPostHasNoSideEffects() throws Exception {
        ownedOrder.setStartTime(LocalDateTime.now().plusDays(1));
        ownedOrder.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        Exchange exchange = validPost(OWNER_TICKET);

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals("/cinebook/orders?tab=past", exchange.redirect);
        assertTrue(exchange.sessionAttributes.containsKey(AppConstants.FLASH_ERROR));
        assertEquals(0, daoState.appealCreateCount);
        assertEquals(0, daoState.notificationCreateCount);
    }

    @Test
    @DisplayName("POST ve redeemed/cancelled/refunded/rejected: redirect an toan va khong co side effect")
    public void testTerminalTicketStatesHaveNoSideEffects() throws Exception {
        assertIneligibleState("redeemed", "paid", false);
        assertIneligibleState("cancelled", "paid", false);
        assertIneligibleState("cancelled", "refunded", false);
        assertIneligibleState("confirmed", "paid", true);
    }

    @Test
    @DisplayName("POST trung appeal pending cung ma ve: redirect kem flash va khong tao notification")
    public void testDuplicatePendingAppealHasNoSideEffects() throws Exception {
        daoState.pendingRefundAppeal = true;
        Exchange exchange = validPost(OWNER_TICKET);

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals("/cinebook/orders?tab=past", exchange.redirect);
        assertTrue(exchange.sessionAttributes.containsKey(AppConstants.FLASH_ERROR));
        assertEquals(0, daoState.appealCreateCount);
        assertEquals(0, daoState.notificationCreateCount);
    }

    private void assertIneligibleState(String orderStatus, String paymentStatus, boolean rejected)
            throws Exception {
        ownedOrder.setOrderStatus(orderStatus);
        ownedOrder.setPaymentStatus(paymentStatus);
        ownedOrder.setRedeemedAt("redeemed".equals(orderStatus) ? LocalDateTime.now().minusHours(1) : null);
        ownedOrder.setRefundedAt("refunded".equals(paymentStatus) ? LocalDateTime.now().minusHours(1) : null);
        ownedOrder.setRefundRejectedAt(rejected ? LocalDateTime.now().minusHours(1) : null);
        Exchange exchange = validPost(OWNER_TICKET);

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals("/cinebook/orders?tab=past", exchange.redirect);
        assertTrue(exchange.sessionAttributes.containsKey(AppConstants.FLASH_ERROR));
        assertEquals(0, daoState.appealCreateCount);
        assertEquals(0, daoState.notificationCreateCount);
    }

    private Exchange validPost(String ticketCode) {
        Exchange exchange = new Exchange(owner);
        exchange.params.put("ticketCode", ticketCode);
        exchange.params.put("reason", "Can doi chieu");
        exchange.params.put("bankAccountInfo", "TEST-ONLY");
        exchange.params.put("contactPhone", "");
        return exchange;
    }

    private static User user(int id, String email, String fullName) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(AppConstants.ROLE_MEMBER);
        return user;
    }

    private static OrderRecord order(int id, int userId, String ticketCode, String filmTitle) {
        OrderRecord order = new OrderRecord();
        order.setId(id);
        order.setUserId(userId);
        order.setTicketCode(ticketCode);
        order.setFilmTitle(filmTitle);
        order.setPaymentStatus("paid");
        order.setOrderStatus("confirmed");
        order.setTotalAmount(new BigDecimal("100000"));
        order.setStartTime(LocalDateTime.now().minusHours(3));
        order.setEndTime(LocalDateTime.now().minusHours(1));
        order.setBusinessNow(LocalDateTime.now());
        return order;
    }

    private OrderDAO fakeOrderDao(DaoState state) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "findByTicketCodeAndUserId": {
                    String code = (String) args[0];
                    int userId = (Integer) args[1];
                    if (state.ownerLookupReturnsForeign
                            && code.equalsIgnoreCase(state.foreignOrder.getTicketCode())) {
                        return Optional.of(state.foreignOrder);
                    }
                    if (userId == state.ownedOrder.getUserId()
                            && code.equalsIgnoreCase(state.ownedOrder.getTicketCode())) {
                        return Optional.of(state.ownedOrder);
                    }
                    return Optional.empty();
                }
                case "findByTicketCode":
                    state.unrestrictedLookupCount++;
                    String code = (String) args[0];
                    if (code.equalsIgnoreCase(state.ownedOrder.getTicketCode())) {
                        return Optional.of(state.ownedOrder);
                    }
                    if (code.equalsIgnoreCase(state.foreignOrder.getTicketCode())) {
                        return Optional.of(state.foreignOrder);
                    }
                    return Optional.empty();
                case "findHistoryByUserId":
                case "findSeatsByOrderId":
                    return List.of();
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (OrderDAO) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {OrderDAO.class}, handler);
    }

    private UserAppealDAO fakeAppealDao(DaoState state) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("create".equals(method.getName()) || "createRefundAppeal".equals(method.getName())) {
                state.appealCreateCount++;
                state.createdAppeal = (UserAppeal) args[0];
                return 501;
            }
            if ("findPendingByTicketCode".equals(method.getName())) {
                return state.pendingRefundAppeal
                        ? Optional.of(state.createdAppeal == null ? new UserAppeal() : state.createdAppeal)
                        : Optional.empty();
            }
            return defaultValue(method.getReturnType());
        };
        return (UserAppealDAO) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {UserAppealDAO.class}, handler);
    }

    private AdminNotificationDAO fakeNotificationDao(DaoState state) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("createNotification".equals(method.getName())) {
                state.notificationCreateCount++;
                state.createdNotification = (AdminNotification) args[0];
            }
            return defaultValue(method.getReturnType());
        };
        return (AdminNotificationDAO) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {AdminNotificationDAO.class}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        return 0;
    }

    private static final class DaoState {
        private final OrderRecord ownedOrder;
        private final OrderRecord foreignOrder;
        private int unrestrictedLookupCount;
        private int appealCreateCount;
        private int notificationCreateCount;
        private boolean ownerLookupReturnsForeign;
        private boolean pendingRefundAppeal;
        private UserAppeal createdAppeal;
        private AdminNotification createdNotification;

        private DaoState(OrderRecord ownedOrder, OrderRecord foreignOrder) {
            this.ownedOrder = ownedOrder;
            this.foreignOrder = foreignOrder;
        }
    }

    private static final class Exchange {
        private final Map<String, String> params = new HashMap<>();
        private final Map<String, Object> requestAttributes = new HashMap<>();
        private final Map<String, Object> sessionAttributes = new HashMap<>();
        private HttpServletRequest request;
        private HttpServletResponse response;
        private int status = HttpServletResponse.SC_OK;
        private String redirect;
        private boolean forwarded;

        private Exchange(User currentUser) {
            sessionAttributes.put(AppConstants.SESSION_USER, currentUser);
        }

        private HttpServletRequest request() {
            if (request == null) {
                HttpSession session = session();
                RequestDispatcher dispatcher = dispatcher();
                InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                    case "getParameter" -> params.get((String) args[0]);
                    case "getContextPath" -> "/cinebook";
                    case "getSession" -> session;
                    case "setAttribute" -> {
                        requestAttributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "getAttribute" -> requestAttributes.get((String) args[0]);
                    case "getRequestDispatcher" -> dispatcher;
                    default -> defaultValue(method.getReturnType());
                };
                request = (HttpServletRequest) Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {HttpServletRequest.class}, handler);
            }
            return request;
        }

        private HttpSession session() {
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "getAttribute" -> sessionAttributes.get((String) args[0]);
                case "setAttribute" -> {
                    sessionAttributes.put((String) args[0], args[1]);
                    yield null;
                }
                case "removeAttribute" -> {
                    sessionAttributes.remove((String) args[0]);
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            };
            return (HttpSession) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {HttpSession.class}, handler);
        }

        private RequestDispatcher dispatcher() {
            return (RequestDispatcher) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {RequestDispatcher.class},
                    (proxy, method, args) -> {
                        if ("forward".equals(method.getName())) {
                            forwarded = true;
                        }
                        return null;
                    });
        }

        private HttpServletResponse response() {
            if (response == null) {
                InvocationHandler handler = (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "sendError":
                            status = (Integer) args[0];
                            break;
                        case "sendRedirect":
                            redirect = (String) args[0];
                            break;
                        case "setStatus":
                            status = (Integer) args[0];
                            break;
                        case "getStatus":
                            return status;
                        default:
                            break;
                    }
                    return defaultValue(method.getReturnType());
                };
                response = (HttpServletResponse) Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {HttpServletResponse.class}, handler);
            }
            return response;
        }
    }
}

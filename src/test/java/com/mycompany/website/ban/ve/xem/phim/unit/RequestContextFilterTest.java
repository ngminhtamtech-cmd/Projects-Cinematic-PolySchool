package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.filter.RequestContextFilter;
import com.mycompany.website.ban.ve.xem.phim.util.RequestContext;
import java.lang.reflect.Proxy;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BUG-09 — {@link RequestContextFilter} phai <b>luon</b> xoa ngu canh khi ra khoi chuoi filter.
 *
 * <p>Tomcat tai dung thread giua cac request. Quen xoa la IP cua nguoi nay duoc ghi vao dong audit
 * cua nguoi khac — mot dong audit sai nguon con te hon mot dong audit trong. Bai test quan trong
 * nhat o day la truong hop chuoi filter <b>nem exception</b>: do la duong de quen {@code finally}
 * nhat.</p>
 */
public class RequestContextFilterTest {

    @AfterEach
    public void clearLeakedContext() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("Ngu canh co san trong chuoi filter va bi xoa sau khi ra")
    public void contextIsVisibleInsideChainAndClearedAfter() throws Exception {
        String[] seen = new String[2];
        FilterChain chain = (request, response) -> {
            seen[0] = RequestContext.ipAddress();
            seen[1] = RequestContext.userAgent();
        };

        new RequestContextFilter().doFilter(
                request("10.0.0.5", Map.of("User-Agent", "CineBookProbe/2.0")), response(), chain);

        assertEquals("10.0.0.5", seen[0]);
        assertEquals("CineBookProbe/2.0", seen[1]);
        assertNull(RequestContext.ipAddress(), "Ngu canh phai bi xoa khi ra khoi filter");
        assertNull(RequestContext.userAgent());
    }

    @Test
    @DisplayName("Chuoi filter nem exception thi ngu canh van phai bi xoa")
    public void contextIsClearedEvenWhenChainThrows() {
        FilterChain exploding = (request, response) -> {
            throw new IllegalStateException("loi giua chuoi filter");
        };

        assertThrows(IllegalStateException.class, () -> new RequestContextFilter().doFilter(
                request("10.0.0.5", Map.of("User-Agent", "CineBookProbe/2.0")), response(), exploding));

        assertNull(RequestContext.ipAddress(),
                "Quen xoa o duong loi la ro ri ngu canh sang request cua nguoi khac");
        assertNull(RequestContext.userAgent());
    }

    @Test
    @DisplayName("X-Forwarded-For nhieu chang thi chi lay IP client dau tien")
    public void forwardedForKeepsOnlyTheClientHop() throws Exception {
        String[] seen = new String[1];
        FilterChain chain = (request, response) -> seen[0] = RequestContext.ipAddress();

        new RequestContextFilter().doFilter(
                request("10.0.0.5", Map.of("X-Forwarded-For", "203.0.113.7, 70.41.3.18, 150.172.238.178")),
                response(), chain);

        assertEquals("203.0.113.7", seen[0]);
    }

    // ---------------------------------------------------------------- fake servlet API

    private static ServletRequest request(String remoteAddr, Map<String, String> headers) {
        return (ServletRequest) Proxy.newProxyInstance(
                RequestContextFilterTest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHeader" -> headers.get((String) args[0]);
                    case "getRemoteAddr" -> remoteAddr;
                    case "toString" -> "FakeRequest";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static ServletResponse response() {
        return (ServletResponse) Proxy.newProxyInstance(
                RequestContextFilterTest.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "FakeResponse";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}

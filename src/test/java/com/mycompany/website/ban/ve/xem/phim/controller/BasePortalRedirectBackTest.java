package com.mycompany.website.ban.ve.xem.phim.controller;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Functional coverage for the redirect emitted by {@link BasePortalServlet#redirectBack}. */
@DisplayName("BasePortal redirectBack same-origin whitelist")
public class BasePortalRedirectBackTest {
    private static final String CONTEXT = "/Website-ban-ve-xem-phim";
    private static final String FALLBACK = CONTEXT + "/admin/dashboard";
    private final ExposedPortal portal = new ExposedPortal();

    @Test
    @DisplayName("same-context relative and same-origin absolute Referer are accepted")
    void trustedReferersArePreservedAsInternalPaths() throws IOException {
        assertAll(
                () -> assertEquals(CONTEXT + "/admin/orders?status=pending",
                        redirect(CONTEXT + "/admin/orders?status=pending")),
                () -> assertEquals(CONTEXT + "/admin/orders?page=2",
                        redirect("http://LOCALHOST:8080" + CONTEXT + "/admin/orders?page=2")));
    }

    @Test
    @DisplayName("foreign and protocol-relative Referer fall back inside the current context")
    void externalReferersFallBack() throws IOException {
        assertAll(
                () -> assertEquals(FALLBACK, redirect("https://evil.example/phish")),
                () -> assertEquals(FALLBACK,
                        redirect("http://localhost:9999" + CONTEXT + "/admin/orders")),
                () -> assertEquals(FALLBACK,
                        redirect("https://localhost:8080" + CONTEXT + "/admin/orders")),
                () -> assertEquals(FALLBACK,
                        redirect("http://localhost" + CONTEXT + "/admin/orders")),
                () -> assertEquals(FALLBACK, redirect("//evil.example/phish")),
                () -> assertEquals(FALLBACK, redirect("/\\evil.example/phish")));
    }

    @Test
    @DisplayName("control characters, malformed URLs and context traversal are rejected")
    void ambiguousOrEscapingReferersFallBack() throws IOException {
        assertAll(
                () -> assertEquals(FALLBACK,
                        redirect(CONTEXT + "/admin/orders\r\nLocation: https://evil.example")),
                () -> assertEquals(FALLBACK, redirect(CONTEXT + "/admin/%ZZ")),
                () -> assertEquals(FALLBACK, redirect(CONTEXT + "/../manager/dashboard")),
                () -> assertEquals(FALLBACK, redirect(CONTEXT + "/%2e%2e/manager/dashboard")),
                () -> assertEquals(FALLBACK, redirect("/another-app/dashboard")));
    }

    @Test
    @DisplayName("missing Referer uses context plus the caller's fallback")
    void missingRefererFallsBack() throws IOException {
        assertAll(
                () -> assertEquals(FALLBACK, redirect(null)),
                () -> assertEquals(FALLBACK, redirect("")),
                () -> assertEquals(FALLBACK, redirect("   ")));
    }

    private String redirect(String referer) throws IOException {
        AtomicReference<String> location = new AtomicReference<>();
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {HttpServletResponse.class},
                (proxy, method, args) -> {
                    if ("sendRedirect".equals(method.getName())) {
                        location.set((String) args[0]);
                    }
                    return defaultValue(method.getReturnType());
                });
        portal.callRedirectBack(request(referer), response, "/admin/dashboard");
        return location.get();
    }

    private HttpServletRequest request(String referer) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHeader" -> "Referer".equalsIgnoreCase((String) args[0]) ? referer : null;
                    case "getContextPath" -> CONTEXT;
                    case "getScheme" -> "http";
                    case "getServerName" -> "localhost";
                    case "getServerPort" -> 8080;
                    default -> defaultValue(method.getReturnType());
                });
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
        return 0;
    }

    private static final class ExposedPortal extends BasePortalServlet {
        private void callRedirectBack(HttpServletRequest request, HttpServletResponse response,
                String fallback) throws IOException {
            redirectBack(request, response, fallback);
        }
    }
}

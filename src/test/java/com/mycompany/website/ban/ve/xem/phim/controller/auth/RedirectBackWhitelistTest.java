package com.mycompany.website.ban.ve.xem.phim.controller.auth;

import java.lang.reflect.Proxy;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ghi chu kem N-15 — {@code AuthServlet.redirectBack()} khong duoc tin header {@code Referer}.
 *
 * <p>Ban cu chuyen huong thang toi gia tri {@code Referer} chua kiem chung: mot trang ngoai co
 * the dua nguoi dung bam nut "Gui lai email xac thuc" roi nhan lai ho tren mien cua ke tan cong.
 * CSRF token khong dong duoc lo hong nay vi {@code Referer} do trinh duyet gui, khong nam trong
 * pham vi bao ve cua token.</p>
 *
 * <p>Khong dung thu vien mock: {@code HttpServletRequest} duoc gia lap bang {@link Proxy}, du cho
 * ba phuong thuc ma ham can.</p>
 */
public class RedirectBackWhitelistTest {

    private static final String CONTEXT = "/Website-ban-ve-xem-phim";
    private static final String HOME = CONTEXT + "/home";

    @Test
    @DisplayName("Duong dan noi bo cung context path duoc giu nguyen")
    public void internalPathIsKept() {
        assertEquals(CONTEXT + "/profile", resolve(CONTEXT + "/profile"));
        assertEquals(CONTEXT + "/films?q=abc", resolve(CONTEXT + "/films?q=abc"));
        assertEquals(CONTEXT, resolve(CONTEXT));
    }

    @Test
    @DisplayName("URL tuyet doi cung host duoc rut ve duong dan noi bo")
    public void absoluteUrlOnSameHostIsReducedToItsPath() {
        assertEquals(CONTEXT + "/profile",
                resolve("http://localhost:8080" + CONTEXT + "/profile"));
        assertEquals(CONTEXT + "/films?q=abc",
                resolve("http://LOCALHOST:8080" + CONTEXT + "/films?q=abc"));
    }

    @Test
    @DisplayName("Host la bi tu choi — day la chinh mau open redirect")
    public void foreignHostFallsBackToHome() {
        assertEquals(HOME, resolve("http://evil.example.com/phish"));
        assertEquals(HOME, resolve("https://evil.example.com" + CONTEXT + "/profile"));
        assertEquals(HOME, resolve("http://localhost:9999" + CONTEXT + "/profile"));
    }

    @Test
    @DisplayName("Duong dan giao thuc //evil.com bi tu choi")
    public void protocolRelativeUrlFallsBackToHome() {
        assertEquals(HOME, resolve("//evil.example.com/phish"));
        assertEquals(HOME, resolve("/\\evil.example.com/phish"));
    }

    @Test
    @DisplayName("Duong dan ngoai context path bi tu choi")
    public void pathOutsideContextFallsBackToHome() {
        assertEquals(HOME, resolve("/QLTV-WEB/index.jsp"));
        assertEquals(HOME, resolve("/manager/dashboard"));
    }

    @Test
    @DisplayName("Ky tu xuong dong (tach header Location) bi tu choi")
    public void headerInjectionAttemptFallsBackToHome() {
        assertEquals(HOME, resolve(CONTEXT + "/profile\r\nSet-Cookie: a=b"));
        assertEquals(HOME, resolve(CONTEXT + "/profile\nLocation: http://evil.example.com"));
    }

    @Test
    @DisplayName("Thieu Referer hoac Referer rong -> trang chu")
    public void missingRefererFallsBackToHome() {
        assertEquals(HOME, resolve(null));
        assertEquals(HOME, resolve(""));
        assertEquals(HOME, resolve("   "));
    }

    private static String resolve(String referer) {
        return AuthServlet.internalPathOrHome(fakeRequest(), referer);
    }

    private static HttpServletRequest fakeRequest() {
        return (HttpServletRequest) Proxy.newProxyInstance(
                RedirectBackWhitelistTest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getContextPath" -> CONTEXT;
                    case "getScheme" -> "http";
                    case "getServerName" -> "localhost";
                    case "getServerPort" -> 8080;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}

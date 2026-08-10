package com.mycompany.website.ban.ve.xem.phim.filter;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

/**
 * Gan cac header bao mat co ban cho moi response.
 *
 * <p>Dat dau chuoi filter de header con nguyen ca tren duong tra loi loi (403 CSRF, 404, 500).
 * {@link com.mycompany.website.ban.ve.xem.phim.api.CsrfFilter} goi {@link #apply} lai mot lan nua
 * sau khi no {@code reset()} response, vi {@code reset()} xoa sach header da dat truoc do.</p>
 *
 * <p><b>Ve {@code 'unsafe-inline'}:</b> 49 file JSP cua du an dang dung {@code <script>} noi tuyen
 * va thuoc tinh {@code style=} khap noi, nen CSP buoc phai cho phep inline o giai doan nay. Gia tri
 * that su chan duoc o day la {@code object-src 'none'}, {@code base-uri 'self'},
 * {@code frame-ancestors 'none'} va {@code form-action 'self'} - chung chan clickjacking,
 * chiem quyen dieu huong form va nhung plugin doc. Siet {@code script-src} chi lam duoc sau khi
 * tach het script noi tuyen ra file rieng (no ky thuat, xem so ban giao P05).</p>
 */
public class SecurityHeaderFilter implements Filter {

    /**
     * Host ngoai duy nhat ma giao dien dang dung: YouTube (modal trailer) va Google Fonts.
     * Them host moi vao day thay vi noi long thanh {@code *}.
     */
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "base-uri 'self'",
            "object-src 'none'",
            "frame-ancestors 'none'",
            "form-action 'self'",
            "script-src 'self' 'unsafe-inline'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' data: https://fonts.gstatic.com",
            "img-src 'self' data: blob: https:",
            "media-src 'self' https:",
            "frame-src 'self' https://www.youtube.com https://www.youtube-nocookie.com",
            "connect-src 'self'");

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        if (servletResponse instanceof HttpServletResponse) {
            apply((HttpServletResponse) servletResponse);
        }
        chain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
    }

    /** Dat lai toan bo header bao mat. Idempotent - goi bao nhieu lan cung cho ket qua nhu nhau. */
    public static void apply(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
    }
}

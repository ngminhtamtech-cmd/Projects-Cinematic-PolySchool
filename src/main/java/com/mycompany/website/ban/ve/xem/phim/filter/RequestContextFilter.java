package com.mycompany.website.ban.ve.xem.phim.filter;

import com.mycompany.website.ban.ve.xem.phim.util.RequestContext;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * Gan IP va User-Agent cua request vao {@link RequestContext} cho ca chuoi xu ly (BUG-09, INV-9).
 *
 * <p>Dat gan dau chuoi filter de moi thao tac quan tri phia sau — du di qua servlet nao — deu co
 * ngu canh de ghi vao {@code AuditLogs.IpAddress}/{@code UserAgent}.</p>
 *
 * <p><b>Xoa trong {@code finally} la bat buoc.</b> Tomcat tai dung thread giua cac request; neu
 * khong xoa thi IP cua nguoi nay se duoc ghi vao dong audit cua nguoi khac. Mot dong audit sai
 * nguon con te hon mot dong audit trong.</p>
 */
public class RequestContextFilter implements Filter {

    /**
     * Header proxy dat truoc IP client. Chi lay <b>phan tu dau</b> vi chuoi co dang
     * {@code client, proxy1, proxy2}.
     *
     * <p>Luu y van hanh: gia tri nay do client gui len nen co the bi gia mao. No dung de <i>truy
     * vet</i>, khong duoc dung lam co so phan quyen.</p>
     */
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        if (servletRequest instanceof HttpServletRequest) {
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            RequestContext.set(clientIp(request), request.getHeader("User-Agent"));
        }
        try {
            chain.doFilter(servletRequest, servletResponse);
        } finally {
            RequestContext.clear();
        }
    }

    @Override
    public void destroy() {
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma < 0 ? forwarded.trim() : forwarded.substring(0, comma).trim();
        }
        return request.getRemoteAddr();
    }
}

package com.mycompany.website.ban.ve.xem.phim.filter;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.api.ApiEnvelope;
import com.mycompany.website.ban.ve.xem.phim.api.Json;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AccountStateGuard;
import com.mycompany.website.ban.ve.xem.phim.util.JsonUtil;
import com.mycompany.website.ban.ve.xem.phim.util.RoleUtil;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class AuthFilter implements Filter {
    private final AccountStateGuard accountStateGuard;

    public AuthFilter() {
        this(new AccountStateGuard());
    }

    AuthFilter(AccountStateGuard accountStateGuard) {
        this.accountStateGuard = accountStateGuard;
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String routePath = path.replaceFirst("/+$", "");

        String requiredRole = requiredRole(routePath, request.getMethod());
        if (requiredRole == null) {
            chain.doFilter(request, response);
            return;
        }

        User user = currentUser(request);

        // D5 — phien phai phan anh trang thai THAT cua tai khoan, khong phai ban chup luc dang nhap.
        // Ban cu chi doc user.isLocked() tu session, nen khoa mot tai khoan dang dang nhap khong
        // co tac dung gi cho toi khi ho tu dang xuat.
        if (user != null) {
            AccountStateGuard.Verdict verdict = accountStateGuard.evaluate(user);
            if (verdict.isUnavailable()) {
                if (wantsJson(request)) {
                    writeJsonError(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                            "SERVICE_UNAVAILABLE", verdict.getMessage());
                } else {
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                            verdict.getMessage());
                }
                return;
            }
            if (verdict.isRevoked()) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                if (wantsJson(request)) {
                    writeJsonError(request, response, HttpServletResponse.SC_FORBIDDEN,
                            apiErrorCode(verdict), verdict.getMessage());
                } else {
                    response.sendRedirect(request.getContextPath() + "/login?error=" + errorCode(verdict));
                }
                return;
            }
        }

        if (user == null) {
            if (wantsJson(request)) {
                writeJsonError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                        "UNAUTHORIZED", "Vui long dang nhap.");
            } else {
                String reqUrl = path;
                String query = request.getQueryString();
                if (query != null && !query.isBlank()) {
                    reqUrl += "?" + query;
                }
                String encoded = java.net.URLEncoder.encode(reqUrl, "UTF-8");
                response.sendRedirect(request.getContextPath() + "/login?returnUrl=" + encoded);
            }
            return;
        }

        if (!RoleUtil.isAtLeast(user.getRole(), requiredRole)) {
            if (wantsJson(request)) {
                writeJsonError(request, response, HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN", "Ban khong co quyen truy cap.");
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            }
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

    private String requiredRole(String path, String method) {
        if (path.startsWith("/tickets/qr")) {
            return AppConstants.ROLE_MEMBER;
        }
        if (path.matches("/films/\\d+/comments")) {
            return AppConstants.ROLE_MEMBER;
        }
        if ("POST".equalsIgnoreCase(method)) {
            // Report comment la endpoint cong khai theo hop dong hien tai; cac duong con lai
            // cua POST comments deu la thao tac cua member, ke ca path malformed.
            if (path.matches("/api/v1/films/\\d+/comments/[^/]+/report")) {
                return null;
            }
            if (path.matches("/api/v1/films/\\d+/comments(?:/.*)?")) {
                return AppConstants.ROLE_MEMBER;
            }
        }
        if (path.startsWith("/api/v1/staff")) {
            return AppConstants.ROLE_STAFF;
        }
        if (path.startsWith("/api/v1/orders")) {
            return AppConstants.ROLE_MEMBER;
        }
        if ("/api/v1/auth/me".equals(path) || path.startsWith("/api/v1/auth/me/")) {
            return AppConstants.ROLE_MEMBER;
        }
        if (path.startsWith("/invoices")) {
            return AppConstants.ROLE_MEMBER;
        }
        if (path.startsWith("/system")) {
            return AppConstants.ROLE_ADMIN;
        }
        if (path.startsWith("/admin")) {
            return AppConstants.ROLE_MANAGER;
        }
        // Quay ve: staff tro len. Manager/admin cung vao duoc nho thang bac cua RoleUtil.
        if (path.startsWith("/staff")) {
            return AppConstants.ROLE_STAFF;
        }
        if (path.startsWith("/orders") || path.startsWith("/profile")
                || path.startsWith("/member") || path.startsWith("/ticket-refund")
                || path.startsWith("/appeals")) {
            return AppConstants.ROLE_MEMBER;
        }
        return null;
    }

    /** Ma loi tren query string de {@code auth/login.jsp} chon dung thong bao. */
    private String errorCode(AccountStateGuard.Verdict verdict) {
        return switch (verdict) {
            case LOCKED -> "locked";
            case DELETED -> "deleted";
            case CHANGED -> "rolechanged";
            default -> "session";
        };
    }

    private String apiErrorCode(AccountStateGuard.Verdict verdict) {
        return switch (verdict) {
            case LOCKED -> "ACCOUNT_LOCKED";
            case DELETED -> "ACCOUNT_DELETED";
            case CHANGED -> "SESSION_ROLE_CHANGED";
            default -> "SESSION_REVOKED";
        };
    }

    private User currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object user = session.getAttribute(AppConstants.SESSION_USER);
        return user instanceof User ? (User) user : null;
    }

    private boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        return isApiRequest(request) || (accept != null && accept.contains("application/json"))
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith);
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.contains("/api/v1/");
    }

    private void writeJsonError(HttpServletRequest request, HttpServletResponse response,
            int status, String code, String message) throws IOException {
        response.setStatus(status);
        if (isApiRequest(request)) {
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            Json.mapper().writeValue(response.getWriter(), ApiEnvelope.fail(code, message));
        } else {
            JsonUtil.error(response, status, message);
        }
    }
}

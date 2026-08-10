package com.mycompany.website.ban.ve.xem.phim.util;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public final class ServletUtil {
    private ServletUtil() {
    }

    public static String param(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value.trim();
    }

    public static void flashSuccess(HttpServletRequest request, String message) {
        request.getSession().setAttribute(AppConstants.FLASH_SUCCESS, message);
    }

    public static void flashError(HttpServletRequest request, String message) {
        request.getSession().setAttribute(AppConstants.FLASH_ERROR, message);
    }

    public static void consumeFlash(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        request.setAttribute(AppConstants.FLASH_SUCCESS, session.getAttribute(AppConstants.FLASH_SUCCESS));
        request.setAttribute(AppConstants.FLASH_ERROR, session.getAttribute(AppConstants.FLASH_ERROR));
        session.removeAttribute(AppConstants.FLASH_SUCCESS);
        session.removeAttribute(AppConstants.FLASH_ERROR);
    }

    /**
     * Returns an untrusted redirect target only when it stays on the request origin and context.
     *
     * <p>Root-relative paths must remain inside {@link HttpServletRequest#getContextPath()}.
     * Absolute URLs are reduced to path/query/fragment and additionally require the same scheme,
     * host and effective port. Invalid, ambiguous or escaping values use {@code contextPath +
     * fallbackPath}. The fallback goes through the same path checks, with {@code /home} as the
     * final closed default.</p>
     */
    public static String internalPathOrFallback(HttpServletRequest request, String untrustedTarget,
            String fallbackPath) {
        String contextPath = request.getContextPath();
        String home = contextPath + "/home";
        String fallback = trustedInternalPath(request, contextPath + fallbackPath);
        if (fallback == null) {
            fallback = home;
        }
        String accepted = trustedInternalPath(request, untrustedTarget);
        return accepted == null ? fallback : accepted;
    }

    private static String trustedInternalPath(HttpServletRequest request, String value) {
        if (value == null || value.isBlank() || hasControlCharacter(value)) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.indexOf('\\') >= 0) {
            return null;
        }

        URI uri;
        try {
            uri = URI.create(candidate);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (uri.isOpaque()) {
            return null;
        }

        String rawPath;
        if (uri.isAbsolute()) {
            if (!sameOrigin(request, uri)) {
                return null;
            }
            rawPath = uri.getRawPath();
            if (rawPath == null || rawPath.isEmpty()) {
                rawPath = "/";
            }
        } else {
            if (uri.getScheme() != null || uri.getRawAuthority() != null) {
                return null;
            }
            rawPath = uri.getRawPath();
        }

        if (!safeContextPath(request.getContextPath(), rawPath)
                || !safeComponent(uri.getRawQuery()) || !safeComponent(uri.getRawFragment())) {
            return null;
        }
        StringBuilder internal = new StringBuilder(rawPath);
        if (uri.getRawQuery() != null) {
            internal.append('?').append(uri.getRawQuery());
        }
        if (uri.getRawFragment() != null) {
            internal.append('#').append(uri.getRawFragment());
        }
        return internal.toString();
    }

    private static boolean sameOrigin(HttpServletRequest request, URI uri) {
        if (uri.getRawUserInfo() != null || uri.getHost() == null
                || request.getScheme() == null
                || !request.getScheme().equalsIgnoreCase(uri.getScheme())
                || !request.getServerName().equalsIgnoreCase(uri.getHost())) {
            return false;
        }
        return effectivePort(uri.getScheme(), uri.getPort())
                == effectivePort(request.getScheme(), request.getServerPort());
    }

    private static int effectivePort(String scheme, int port) {
        if (port >= 0) {
            return port;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return -1;
    }

    private static boolean safeContextPath(String contextPath, String rawPath) {
        if (rawPath == null || !rawPath.startsWith("/") || rawPath.startsWith("//")) {
            return false;
        }
        String lowered = rawPath.toLowerCase(Locale.ROOT);
        if (lowered.contains("%2e") || lowered.contains("%2f")
                || lowered.contains("%5c") || lowered.contains("%25")) {
            return false;
        }

        String decoded = decode(rawPath);
        if (decoded == null || hasControlCharacter(decoded) || decoded.indexOf('\\') >= 0
                || decoded.startsWith("//") || hasDotSegment(decoded)) {
            return false;
        }
        try {
            String normalized = new URI(null, null, decoded, null).normalize().getPath();
            return contextPath.isEmpty()
                    || normalized.equals(contextPath)
                    || normalized.startsWith(contextPath + "/");
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private static boolean hasDotSegment(String path) {
        for (String segment : path.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean safeComponent(String rawValue) {
        if (rawValue == null) {
            return true;
        }
        String decoded = decode(rawValue);
        return decoded != null && !hasControlCharacter(decoded);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean hasControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x20 || character == 0x7F) {
                return true;
            }
        }
        return false;
    }
}

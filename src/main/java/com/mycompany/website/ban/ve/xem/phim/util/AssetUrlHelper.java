package com.mycompany.website.ban.ve.xem.phim.util;

import java.util.Locale;
import java.util.regex.Pattern;

/** Builds browser-facing media URLs without losing the deployed servlet context. */
public final class AssetUrlHelper {

    private static final Pattern URI_SCHEME =
            Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");

    private AssetUrlHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Prefix a local media path with exactly one servlet context path.
     *
     * <p>Only HTTP(S) and scheme-relative external URLs are passed through. Other schemes fail
     * closed so a persisted media field cannot become an executable browser URL.</p>
     *
     * @param contextPath current servlet context path
     * @param rawUrl stored media URL or path
     * @return a browser-safe URL, or an empty string for a blank/unsupported value
     */
    public static String withContext(String contextPath, String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        String value = rawUrl.trim();
        if (value.startsWith("//")) {
            return value;
        }
        if (URI_SCHEME.matcher(value).find()) {
            String lower = value.toLowerCase(Locale.ROOT);
            return lower.startsWith("http://") || lower.startsWith("https://") ? value : "";
        }

        String context = normalizeContext(contextPath);
        if (!context.isEmpty()
                && (value.equals(context) || value.startsWith(context + "/"))) {
            return value;
        }
        String localPath = value.startsWith("/") ? value : "/" + value;
        return context + localPath;
    }

    private static String normalizeContext(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath.trim())) {
            return "";
        }
        String context = contextPath.trim();
        if (!context.startsWith("/")) {
            context = "/" + context;
        }
        while (context.endsWith("/")) {
            context = context.substring(0, context.length() - 1);
        }
        return context;
    }
}

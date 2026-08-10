package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JSP media URLs preserve the deployed servlet context")
class JspAssetContextContractTest {

    private static final Path VIEW_ROOT = Path.of("src", "main", "webapp", "WEB-INF", "views");

    @Test
    @DisplayName("Every server-rendered media surface uses the shared context-safe helper")
    void mediaSurfacesUseSharedHelper() throws IOException {
        List<String> surfaces = List.of(
                "shared/public-header.jspf",
                "admin/sidebar.jspf",
                "home.jsp",
                "cinema-corner.jsp",
                "cinetag.jsp",
                "events.jsp",
                "special-cinemas.jsp",
                "film/list.jsp",
                "film/detail.jsp",
                "booking/page.jsp",
                "admin/cinema-films.jsp",
                "admin/cinema-form.jsp",
                "admin/cinemas.jsp",
                "admin/combos.jsp",
                "admin/film-form.jsp",
                "admin/films.jsp",
                "admin/showtime-form.jsp",
                "admin/showtimes.jsp",
                "showtime/list.jsp");

        for (String surface : surfaces) {
            String source = Files.readString(VIEW_ROOT.resolve(surface), StandardCharsets.UTF_8);
            assertTrue(source.contains("cbf:assetUrl("),
                    () -> surface + " must normalize dynamic media through cbf:assetUrl");
        }
    }

    @Test
    @DisplayName("Client-rendered custom content applies the same context rule")
    void customContentUsesClientAssetNormalizer() throws IOException {
        String source = Files.readString(
                VIEW_ROOT.resolve("admin/custom-content.jsp"), StandardCharsets.UTF_8);

        assertTrue(source.contains("function assetUrl(value)"));
        assertTrue(source.contains("assetUrl(item.imageUrl) || DEFAULT_IMG"));
    }

    @Test
    @DisplayName("Every complete JSP declares a context-safe favicon")
    void completePagesDeclareContextSafeFavicon() throws IOException {
        try (Stream<Path> paths = Files.walk(VIEW_ROOT)) {
            List<Path> pages = paths
                    .filter(path -> path.getFileName().toString().endsWith(".jsp"))
                    .toList();

            for (Path page : pages) {
                String source = Files.readString(page, StandardCharsets.UTF_8);
                if (source.contains("<head")) {
                    assertTrue(source.contains("/WEB-INF/views/shared/favicon.jspf"),
                            () -> VIEW_ROOT.relativize(page)
                                    + " must include the context-safe favicon fragment");
                }
            }
        }
    }
}

package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Post-A/B/C release-hardening contracts")
class PostAbcHardeningContractTest {
    private static final Path JS_QR = Path.of(
            "src", "main", "webapp", "assets", "js", "jsQR.js");
    private static final Path JS_QR_NOTICE = Path.of(
            "src", "main", "webapp", "assets", "js", "jsQR.NOTICE.txt");
    private static final Path JS_QR_RUNTIME = Path.of(
            "src", "main", "webapp", "assets", "js", "qr-codec.js");
    private static final Path STAFF_CHECKIN = Path.of(
            "src", "main", "webapp", "WEB-INF", "views", "staff", "checkin.jsp");
    private static final String JS_QR_SHA256 =
            "bc40c8a15196236b2314db0856f72ca0b49980cd5413b8c852a7349f5fee0859";

    @Test
    @DisplayName("D.1: vendored jsQR is the complete pinned browser bundle")
    void jsQrBundleIsCompleteAndPinned() throws IOException, NoSuchAlgorithmException {
        String source = Files.readString(JS_QR, StandardCharsets.UTF_8);

        assertTrue(Files.size(JS_QR) > 250_000, "truncated jsQR bundles must fail the build");
        for (int module = 9; module <= 12; module++) {
            assertTrue(source.contains("/* " + module + " */"),
                    "jsQR webpack module " + module + " must be present");
        }
        assertEquals(JS_QR_SHA256, sha256(JS_QR));
        assertEquals(JS_QR_SHA256, sha256(JS_QR_RUNTIME),
                "the browser-safe runtime alias must remain the pinned bundle");
    }

    @Test
    @DisplayName("D.1: jsQR provenance, license and checksum are recorded")
    void jsQrProvenanceIsRecorded() throws IOException {
        assertTrue(Files.isRegularFile(JS_QR_NOTICE));
        String notice = Files.readString(JS_QR_NOTICE, StandardCharsets.UTF_8);

        assertTrue(notice.contains("jsQR 1.4.0"));
        assertTrue(notice.contains("Apache-2.0"));
        assertTrue(notice.contains(JS_QR_SHA256));
        assertTrue(notice.contains("https://github.com/cozmo/jsQR"));
    }

    @Test
    @DisplayName("D.1: staff check-in cache-busts the replaced jsQR bundle")
    void staffCheckinPinsJsQrAssetUrlToChecksumPrefix() throws IOException {
        String jsp = Files.readString(STAFF_CHECKIN, StandardCharsets.UTF_8);
        assertTrue(jsp.contains("/assets/js/qr-codec.js?v=1.4.0-bc40c8a1"),
                "a persistent browser must not reuse the previously truncated jsQR asset");
        assertTrue(jsp.contains("/assets/js/qr-scanner.js?v=1.0.6-9672d985"),
                "the readiness check must not be hidden by a cached scanner script");
    }

    @Test
    @DisplayName("D.2: Next root and booking handoff routes are implemented")
    void nextPublicEntryAndBookingHandoffExist() throws IOException {
        String root = read("web/app/page.tsx");
        Path handoff = Path.of("web", "app", "dat-ve", "page.tsx");

        assertTrue(root.contains("redirect(\"/phim\")"));
        assertFalse(root.contains("To get started"));
        assertTrue(Files.isRegularFile(handoff));
        String page = Files.readString(handoff, StandardCharsets.UTF_8);
        assertTrue(page.contains("showtimeId"));
        assertTrue(page.contains("bookingUrl"));
        assertTrue(page.contains("ApiError"));
    }

    @Test
    @DisplayName("D.2: local and example runtime configuration target the real context")
    void nextRuntimeConfigurationUsesRealTomcatContext() throws IOException {
        String local = read("web/.env.local");
        String example = read("web/.env.example");
        String proxy = read("web/app/api/proxy/[...path]/route.ts");
        String nextConfig = read("web/next.config.ts");
        String expected = "http://localhost:8080/Website-ban-ve-xem-phim";

        for (String source : new String[]{local, example}) {
            assertTrue(source.contains("CINEBOOK_API_BASE=" + expected + "/api/v1"));
            assertTrue(source.contains("NEXT_PUBLIC_ASSET_BASE=" + expected));
            assertTrue(source.contains("NEXT_PUBLIC_JSP_BASE=" + expected));
            assertFalse(source.contains("8081/cinebook"));
        }
        assertTrue(proxy.contains(expected + "/api/v1"));
        assertFalse(proxy.contains("8081/cinebook"));
        assertTrue(nextConfig.contains("defaultAssetBase = \"" + expected + "\""));
    }

    @Test
    @DisplayName("D.3: promotion navigation is available to admin and manager")
    void promotionLinksAreAvailableToCinemaManagers() throws IOException {
        String dashboard = read("src/main/webapp/WEB-INF/views/admin/dashboard.jsp");
        String sidebar = read("src/main/webapp/WEB-INF/views/admin/sidebar.jspf");

        assertTrue(dashboard.contains("/admin/promotions"));
        assertTrue(sidebar.contains("/admin/promotions"));
    }

    @Test
    @DisplayName("D.3: promotion GET and POST dispatch use the shared admin/manager capability")
    void promotionDispatchUsesPromotionCapability() throws IOException {
        String servlet = read(
                "src/main/java/com/mycompany/website/ban/ve/xem/phim/controller/admin/"
                        + "ManagerPortalServlet.java");
        String dispatch = "case \"/admin/promotions\"";
        int getDispatch = servlet.indexOf(dispatch);
        int postDispatch = servlet.indexOf(dispatch, getDispatch + dispatch.length());

        assertTrue(caseBody(servlet, "/admin/promotions", getDispatch)
                .contains("CinemaCapabilityPolicy.canCreatePromotion(actor)"));
        assertTrue(caseBody(servlet, "/admin/promotions", postDispatch)
                .contains("CinemaCapabilityPolicy.canCreatePromotion(actor)"));
    }

    @Test
    @DisplayName("D.3: marketing content is scoped by cinema while legal content remains admin-only")
    void customContentUsesCinemaScope() throws IOException {
        String servlet = read(
                "src/main/java/com/mycompany/website/ban/ve/xem/phim/controller/admin/"
                        + "ManagerPortalServlet.java");
        String sidebar = read("src/main/webapp/WEB-INF/views/admin/sidebar.jspf");
        String service = read(
                "src/main/java/com/mycompany/website/ban/ve/xem/phim/service/CinemaContentService.java");

        assertTrue(servlet.contains("contentCinemaForWrite(request, actor)"));
        assertTrue(servlet.contains("CinemaCapabilityPolicy.requireManagerCinema(actor)"));
        int specialAdminGate = servlet.indexOf("if (isGlobalAdmin(actor)) {");
        int specialSync = servlet.indexOf("adminService.syncSpecialCinema", specialAdminGate);
        assertTrue(specialAdminGate >= 0 && specialSync > specialAdminGate);
        assertTrue(sidebar.contains("nội dung marketing theo rạp; nội dung pháp lý chỉ admin"));
        assertTrue(service.contains("CinemaCapabilityPolicy.requireCinema(actor, cinemaId)"));
        assertTrue(service.contains("CinemaContents"));
    }

    @Test
    @DisplayName("D.4: seeded showtimes have a matching CinemaFilms assignment")
    void seedLinksFilmToCinemaBeforeShowtimes() throws IOException {
        String seed = read("database/seed_test_fixtures.sql");
        int assignment = seed.indexOf("INSERT INTO CinemaFilms");
        int showtimes = seed.indexOf("INSERT INTO Showtimes");

        assertTrue(assignment >= 0, "seed must insert CinemaFilms");
        assertTrue(assignment < showtimes, "cinema-film assignment must exist before showtimes");
        assertTrue(seed.substring(assignment, showtimes).contains("(1, 1)"));
    }

    @Test
    @DisplayName("D.4: Checkstyle is blocking and the known warning is removed")
    void checkstyleIsBlockingAndWarningFree() throws IOException {
        String pom = read("pom.xml");
        String tokenTest = read(
                "src/test/java/com/mycompany/website/ban/ve/xem/phim/api/v1/"
                        + "AuthenticationTokenContractTest.java");

        assertTrue(pom.contains("<failOnViolation>true</failOnViolation>"));
        assertFalse(tokenTest.contains("import com.fasterxml.jackson.databind.JsonNode;"));
    }

    @Test
    @DisplayName("D.4: repeatable release gate makes live verification mandatory")
    void releaseGateRequiresOfflineAndLiveEvidence() throws IOException {
        String gate = read("scripts/release-gate.ps1");

        assertTrue(gate.contains("SessionFixationIT must execute 4/4"));
        assertTrue(gate.contains("Route manifest 41/41"));
        assertTrue(gate.contains("CSRF sweep 34/34"));
        assertTrue(gate.contains("Browser console and role smoke"));
        assertTrue(gate.contains("HTTP 500 gate failed"));
        assertTrue(gate.contains("Source changed after Offline gates"));
    }

    private static String caseBody(String source, String path, int fromIndex) {
        int start = source.indexOf("case \"" + path + "\"", Math.max(0, fromIndex));
        assertTrue(start >= 0, "missing dispatch for " + path);
        int end = source.indexOf("case \"", start + 8);
        return source.substring(start, end < 0 ? source.length() : end);
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return HexFormat.of().formatHex(digest);
    }
}

package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Browser release-gate contracts")
class BrowserSmokeGateContractTest {

    @Test
    @DisplayName("D.1: live browser gate manually looks up a deterministic TOO_EARLY ticket")
    void browserGateExercisesManualTooEarlyLookup() throws IOException {
        String browser = read("web/tests/browser-smoke.mjs");
        String gate = read("scripts/release-gate.ps1");

        assertTrue(browser.contains("CINEBOOK_TOO_EARLY_TICKET"));
        assertTrue(browser.contains("/tickets/qr/${tooEarlyTicket}"));
        assertTrue(browser.contains("#scannedTicketCode"));
        assertTrue(browser.contains("#scanForm"));
        assertTrue(browser.contains("#manualTicketCode"));
        assertTrue(browser.contains("TOO_EARLY"));
        assertTrue(gate.contains("prepare-browser-smoke-ticket.ps1"));
        assertTrue(gate.contains("CINEBOOK_TOO_EARLY_TICKET"));
        assertTrue(gate.contains("-Mode Prepare"));
        assertTrue(gate.contains("-Mode Cleanup"));
    }

    @Test
    @DisplayName("D.4: member browser gate cancels a deterministic pending order with CSRF")
    void browserGateExercisesMemberCancellation() throws IOException {
        String browser = read("web/tests/browser-smoke.mjs");
        String fixture = read("scripts/prepare-browser-smoke-ticket.ps1");
        String gate = read("scripts/release-gate.ps1");

        assertTrue(browser.contains("CINEBOOK_CANCEL_TICKET"));
        assertTrue(browser.contains("input[name=\"_csrf\"]"));
        assertTrue(browser.contains("Đã hủy"));
        assertTrue(fixture.contains("CBROWSERCANCEL20260801"));
        assertTrue(gate.contains("CINEBOOK_CANCEL_TICKET"));
    }

    @Test
    @DisplayName("D.1: browser ticket fixture is hard-bound to ephemeral CineBookIT databases")
    void browserFixtureCannotTargetProduction() throws IOException {
        Path fixture = Path.of("scripts", "prepare-browser-smoke-ticket.ps1");

        assertTrue(Files.isRegularFile(fixture));
        String source = Files.readString(fixture, StandardCharsets.UTF_8);
        assertTrue(source.contains("^CineBookIT_"));
        assertTrue(source.contains("DB_NAME()"));
        assertTrue(source.contains("CBROWSERTOOEARLY20260801"));
        assertTrue(source.contains("ValidateSet('Prepare', 'Cleanup')"));
    }

    @Test
    @DisplayName("D.4: browser gate fails on every unexpected console or HTTP resource error")
    void browserGateDoesNotGloballyIgnoreResourceFailures() throws IOException {
        String browser = read("web/tests/browser-smoke.mjs");

        assertFalse(browser.contains("isBrowserResourceStatus"));
        assertTrue(browser.contains("response.status() >= 400"));
        assertTrue(browser.contains("message.type() === \"error\""));
    }

    @Test
    @DisplayName("D.5: live gate uploads a poster and verifies the Next image bridge")
    void browserGateExercisesRealUploadedPoster() throws IOException {
        String browser = read("web/tests/browser-smoke.mjs");
        String servlet = read(
                "src/main/java/com/mycompany/website/ban/ve/xem/phim/controller/admin/"
                        + "ManagerPortalServlet.java");
        String nextConfig = read("web/next.config.ts");

        assertTrue(browser.contains("thumbnailFile"));
        assertTrue(browser.contains("bannerFile"));
        assertTrue(browser.contains("CINEBOOK_UPLOAD_DIR"));
        assertTrue(browser.contains("/_next/image?url="));
        assertTrue(browser.contains("for (const uploadedPath of uploadedFilesToCleanup)"));
        assertTrue(browser.contains("fs.existsSync(uploadedPath)"));
        assertTrue(browser.contains("fs.unlinkSync(uploadedPath)"));
        assertTrue(servlet.contains("return \"/uploads/\" + fileName;"));
        assertTrue(nextConfig.contains("${assetPath}/uploads/**"));
    }

    @Test
    @DisplayName("D.5: live gate proves the JVM effective upload directory")
    void liveGateRequiresExactUploadDirectoryProperty() throws IOException {
        String gate = read("scripts/release-gate.ps1");

        assertTrue(gate.contains("Tomcat JVM is missing -Dcinebook.upload.dir"));
        assertTrue(gate.contains("-Dcinebook.upload.dir=<expected>"));
        assertTrue(gate.contains("$expectedUploadDir"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Release gate fails closed")
class ReleaseGateFailClosedContractTest {

    @Test
    @DisplayName("Checkstyle warnings are release-blocking violations")
    void checkstyleWarningsBlockTheBuild() throws IOException {
        String pom = read("pom.xml");

        assertTrue(pom.contains("<failOnViolation>true</failOnViolation>"));
        assertTrue(pom.contains("<violationSeverity>warning</violationSeverity>"),
                "warning-level rules must not be ignored by checkstyle:check");
    }

    @Test
    @DisplayName("Offline invalidates old evidence and Live is bound to that Offline run")
    void offlineAndLiveMarkersAreOrderedAndArtifactBound() throws IOException {
        String gate = read("scripts/release-gate.ps1");

        assertTrue(gate.contains("Clear-ReleaseMarkers"));
        assertTrue(gate.contains("runId = [guid]::NewGuid().ToString('D')"));
        assertTrue(gate.contains("offlineRunId = $offline.runId"));
        assertTrue(gate.contains("Assert-OfflineArtifactIdentity"));
        assertTrue(gate.contains("warSha256"));
        assertTrue(gate.contains("explodedSha256"));
        assertTrue(gate.contains("nextBuildId"));
        assertTrue(gate.contains("nextArtifactSha256"));
    }

    @Test
    @DisplayName("Live refuses non-loopback URLs and a Tomcat JVM not pinned to runtime IT config")
    void liveStageRejectsProductionTargetsBeforeMutation() throws IOException {
        String gate = read("scripts/release-gate.ps1");

        assertTrue(gate.contains("Assert-LoopbackUrl"));
        assertTrue(gate.contains("Assert-LiveTomcatUsesTestDatabase"));
        assertTrue(gate.contains("target\\db.it.properties"));
        assertTrue(gate.contains("^CineBookIT_"));
        assertTrue(gate.contains("Get-NetTCPConnection"));
        assertTrue(gate.contains("Get-CimInstance Win32_Process"));
        assertTrue(gate.contains("CINEBOOK_UPLOAD_DIR"));
        assertTrue(gate.contains("expectedUploadDir"));
    }

    @Test
    @DisplayName("Live accepts Tomcat's quoted catalina.base while comparing the resolved path")
    void liveStageParsesQuotedCatalinaBase() throws IOException {
        String gate = read("scripts/release-gate.ps1");

        assertTrue(gate.contains("$catalinaBaseMatch = [regex]::Match("));
        assertTrue(gate.contains("Tomcat JVM is missing -Dcatalina.base."));
        assertTrue(gate.contains(
                "$resolvedActualTomcatBase = [IO.Path]::GetFullPath($actualTomcatBase)"));
        assertTrue(gate.contains("$resolvedActualTomcatBase, $resolvedTomcatBase"));
    }

    @Test
    @DisplayName("Offline pins the whole Maven JVM to the absolute test config and probes DB_NAME")
    void offlineStagePinsAndProbesTheTestDatabase() throws IOException {
        String gate = read("scripts/release-gate.ps1");

        assertTrue(gate.contains("$testDbConfig = [IO.Path]::GetFullPath"));
        assertTrue(gate.contains("-Dcinebook.it.config=$testDbConfig"));
        assertTrue(gate.contains("-Dcinebook.it.database=$script:testDatabaseName"));
        assertTrue(gate.contains("SELECT DB_NAME()"));
        assertTrue(gate.contains("Assert-TestDatabaseConfiguration"));
        assertTrue(gate.contains("expected CineBookIT_<timestamp>_<random>"));
        assertTrue(gate.contains("Remove-EphemeralTestDatabase"));
    }

    @Test
    @DisplayName("Source identity covers the complete web input tree but skips generated output")
    void fingerprintCoversWebInputsAndExcludesGeneratedDirectories() throws IOException {
        String gate = read("scripts/release-gate.ps1");

        assertTrue(gate.contains("'web'"), "public assets and build config must be fingerprinted");
        assertTrue(gate.contains("'web\\.next'"));
        assertTrue(gate.contains("'web\\node_modules'"));
        assertTrue(gate.contains("'web\\coverage'"));
        assertTrue(gate.contains("'web\\out'"));
    }

    @Test
    @DisplayName("Route gate requires exactly 41 valid unique entries and successful admin login")
    void routeManifestAndAdminAuthenticationFailClosed() throws IOException {
        String routeCheck = read("scripts/route-check.ps1");

        assertTrue(routeCheck.contains("$ExpectedRouteCount = 41"));
        assertTrue(routeCheck.contains("$routes.Count -ne $ExpectedRouteCount"));
        assertTrue(routeCheck.contains("duplicate route"));
        assertTrue(routeCheck.contains("Admin credentials were supplied"));
        assertTrue(routeCheck.contains("Admin login failed"));
    }

    @Test
    @DisplayName("CSRF sweep accepts only closed-set rejection codes and a strict positive control")
    void csrfSweepRejectsTransportRoutingAndServerErrors() throws IOException {
        String sweep = read("scripts/csrf-sweep.bat");

        assertTrue(sweep.contains("set \"EXPECTED=34\""));
        assertTrue(sweep.contains("if \"!CODE!\"==\"302\" set \"BLOCKED=1\""));
        assertTrue(sweep.contains("if \"!CODE!\"==\"401\" set \"BLOCKED=1\""));
        assertTrue(sweep.contains("if \"!CODE!\"==\"403\" set \"BLOCKED=1\""));
        assertTrue(sweep.contains("if not \"!CTRL!\"==\"200\""));
        assertTrue(sweep.contains("if not !PASS! EQU !EXPECTED!"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

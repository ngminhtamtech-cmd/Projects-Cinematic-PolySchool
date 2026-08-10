package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Route manifest fail-closed regression")
@Tag("it")
class RouteGateFailClosedRegressionTest {
    private static final Path SCRIPT = Path.of("scripts", "route-check.ps1").toAbsolutePath();
    private static final Path MANIFEST = Path.of("scripts", "route-manifest.txt");

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("a 40-route manifest fails before any HTTP request")
    void exactRouteCountIsRequired() throws Exception {
        List<String> routes = routeEntries();
        Path manifest = writeManifest(routes.subList(0, 40));

        ScriptResult result = run(manifest);

        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("exactly 41"), result.output());
    }

    @Test
    @DisplayName("duplicate route paths fail even when the manifest still has 41 rows")
    void duplicateRoutesAreRejected() throws Exception {
        List<String> routes = new ArrayList<>(routeEntries());
        routes.set(routes.size() - 1, routes.get(0));

        ScriptResult result = run(writeManifest(routes));

        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("duplicate route"), result.output());
    }

    @Test
    @DisplayName("404 cannot be declared as an acceptable route result")
    void failOpenExpectedStatusesAreRejected() throws Exception {
        List<String> routes = new ArrayList<>(routeEntries());
        routes.set(0, routes.get(0).replaceFirst("\\s+200\\s+", " 404 "));

        ScriptResult result = run(writeManifest(routes));

        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("fail-open status codes"), result.output());
    }

    @Test
    @DisplayName("supplying only one admin credential is a hard failure")
    void incompleteAdminCredentialsAreRejected() throws Exception {
        ProcessBuilder builder = command(MANIFEST.toAbsolutePath());
        builder.command().add("-AdminEmail");
        builder.command().add("admin@test.com");

        ScriptResult result = run(builder);

        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("supplied incompletely"), result.output());
    }

    private Path writeManifest(List<String> routes) throws Exception {
        Path manifest = tempDir.resolve("route-manifest.txt");
        Files.write(manifest, routes, StandardCharsets.UTF_8);
        return manifest;
    }

    private static List<String> routeEntries() throws Exception {
        return Files.readAllLines(MANIFEST, StandardCharsets.UTF_8).stream()
                .map(line -> line.replace("\uFEFF", "").trim())
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    private static ScriptResult run(Path manifest) throws Exception {
        return run(command(manifest));
    }

    private static ProcessBuilder command(Path manifest) {
        ProcessBuilder builder = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-File", SCRIPT.toString(),
                "-ManifestPath", manifest.toString(), "-BaseUrl", "http://127.0.0.1:1");
        builder.redirectErrorStream(true);
        return builder;
    }

    private static ScriptResult run(ProcessBuilder builder) throws Exception {
        Process process = builder.start();
        boolean completed = process.waitFor(15, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new IllegalStateException("route-check.ps1 did not finish within 15 seconds");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ScriptResult(process.exitValue(), output);
    }

    private record ScriptResult(int exitCode, String output) {
    }
}

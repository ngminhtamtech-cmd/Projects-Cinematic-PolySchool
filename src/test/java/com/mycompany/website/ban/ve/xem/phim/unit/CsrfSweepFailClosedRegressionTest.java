package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CSRF sweep closed-set status regression")
@Tag("it")
class CsrfSweepFailClosedRegressionTest {
    private static final Path SWEEP = Path.of("scripts", "csrf-sweep.bat").toAbsolutePath();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("34 rejection responses plus a strict positive control pass")
    void exactRejectionSetPasses() throws Exception {
        SweepResult result = runSweep("403", "200");

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("34 chan / 0 lot luoi"), result.output());
    }

    @ParameterizedTest(name = "HTTP {0} must fail")
    @ValueSource(strings = {"000", "404", "500"})
    @DisplayName("transport, missing-route and server errors never count as CSRF blocks")
    void nonRejectionCodesFail(String status) throws Exception {
        SweepResult result = runSweep(status, "200");

        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("SAI MA"), result.output());
    }

    @Test
    @DisplayName("the valid-token positive control must return exactly HTTP 200")
    void positiveControlIsStrict() throws Exception {
        SweepResult result = runSweep("403", "403");

        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("Doi chung phai"), result.output());
    }

    private SweepResult runSweep(String blockedCode, String controlCode) throws Exception {
        int rejection = Integer.parseInt(blockedCode);
        int control = Integer.parseInt(controlCode);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, rejection, control));
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            ProcessBuilder builder = new ProcessBuilder(
                    "cmd.exe", "/d", "/c", SWEEP.toString(), baseUrl);
            builder.redirectErrorStream(true);
            builder.environment().put("TEMP", tempDir.toString());

            Process process = builder.start();
            boolean completed = process.waitFor(20, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("csrf-sweep.bat did not finish within 20 seconds");
            }
            String output = new String(
                    process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new SweepResult(process.exitValue(), output);
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, int rejection, int control)
            throws IOException {
        String method = exchange.getRequestMethod();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if ("GET".equals(method) && "/login".equals(exchange.getRequestURI().getPath())) {
            exchange.getResponseHeaders().add("Set-Cookie", "XSRF-TOKEN=test-token; Path=/");
            exchange.sendResponseHeaders(200, -1);
        } else if ("POST".equals(method) && "/login".equals(exchange.getRequestURI().getPath())
                && body.contains("_csrf=")) {
            exchange.sendResponseHeaders(control, -1);
        } else if (rejection == 0) {
            exchange.close();
            return;
        } else {
            exchange.sendResponseHeaders(rejection, -1);
        }
        exchange.close();
    }

    private record SweepResult(int exitCode, String output) {
    }
}

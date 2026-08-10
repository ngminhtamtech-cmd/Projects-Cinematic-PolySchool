package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.util.QrCodeUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrBrowserFixtureTest {
    static final String PAYLOAD = "CINEBOOK-QR-SMOKE";
    private static final Path OUTPUT = Path.of("target", "qr-browser-fixture.png");

    @Test
    @DisplayName("D.1: deterministic QR fixture is generated for the browser release gate")
    void generateBrowserFixture() throws IOException {
        Files.createDirectories(OUTPUT.getParent());
        try (OutputStream output = Files.newOutputStream(OUTPUT)) {
            QrCodeUtil.writePng(PAYLOAD, 280, output);
        }
        assertTrue(Files.size(OUTPUT) > 250, "generated QR fixture must be a non-empty PNG");
    }
}

package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mycompany.website.ban.ve.xem.phim.config.DatabaseConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLocationTest {
    @TempDir
    Path tempDir;

    @Test
    void explicitExternalConfigHasHighestPriority() throws Exception {
        Path config = tempDir.resolve("db.properties");
        Files.writeString(config, "db.url=external-url\nmarker=external\n");
        String previous = System.getProperty("cinebook.db.config");
        try {
            System.setProperty("cinebook.db.config", config.toString());
            assertEquals("external", DatabaseConfig.load().getProperty("marker"));
        } finally {
            if (previous == null) {
                System.clearProperty("cinebook.db.config");
            } else {
                System.setProperty("cinebook.db.config", previous);
            }
        }
    }
}

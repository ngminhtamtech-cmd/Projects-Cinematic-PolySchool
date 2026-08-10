package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.config.AppContextListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("it")
public class DBConnectionSmokeTest {

    @Test
    @DisplayName("Fail-fast schema check on test database should succeed")
    public void testSchemaVerification() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        assertDoesNotThrow(AppContextListener::verifySchemaFailFast);
    }
}

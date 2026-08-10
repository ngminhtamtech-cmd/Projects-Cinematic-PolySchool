package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import com.mycompany.website.ban.ve.xem.phim.util.QrCodeUtil;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TicketCodeTest {
    private static String previousDatabaseConfig;

    @BeforeAll
    static void configureSecret() {
        previousDatabaseConfig = System.getProperty("cinebook.db.config");
        System.setProperty("cinebook.db.config",
                Path.of("src/test/resources/qr-test.properties").toAbsolutePath().toString());
    }

    @AfterAll
    static void restoreConfig() {
        if (previousDatabaseConfig == null) {
            System.clearProperty("cinebook.db.config");
        } else {
            System.setProperty("cinebook.db.config", previousDatabaseConfig);
        }
    }

    @Test
    void generatesUniqueBase32CodesWithOneHundredThirtyBits() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String code = BookingService.generateTicketCode();
            assertTrue(code.matches("CB[A-Z2-7]{26}"));
            assertTrue(codes.add(code));
        }
    }

    @Test
    void signedQrRoundTripsAndTamperingIsRejected() {
        String code = BookingService.generateTicketCode();
        String signed = QrCodeUtil.signedPayload(code);
        assertEquals(code, QrCodeUtil.verifiedTicketCode(signed));
        assertNull(QrCodeUtil.verifiedTicketCode(signed + "x"));
        assertNull(QrCodeUtil.verifiedTicketCode(code));
    }

    @Test
    void legacyUnsignedTicketRemainsRedeemable() {
        assertEquals("CB1722240000000ABCD", QrCodeUtil.verifiedTicketCode("CB1722240000000ABCD"));
    }
}

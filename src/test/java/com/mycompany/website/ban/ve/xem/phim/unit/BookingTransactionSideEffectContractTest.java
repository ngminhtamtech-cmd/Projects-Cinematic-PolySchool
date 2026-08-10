package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Booking transaction side-effect contracts")
class BookingTransactionSideEffectContractTest {

    private static final Path BOOKING_SERVICE = Path.of(
            "src", "main", "java", "com", "mycompany", "website", "ban", "ve", "xem", "phim",
            "service", "BookingService.java");

    @Test
    @DisplayName("Sold-out audit and notification are deferred until payment commits")
    void payOrderDoesNotOpenAdminConnectionsInsideItsTransaction() throws IOException {
        String source = Files.readString(BOOKING_SERVICE, StandardCharsets.UTF_8);
        String body = methodBody(source,
                "public OrderRecord payOrder(int userId, int orderId, Map<Integer, Integer> comboSelections,"
                        + " String promotionCode, String paymentMethod, String idempotencyKey)");
        assertFalse(body.contains("adminService.logAction("),
                "payOrder must not open an audit connection while payment locks are held");
        assertTrue(body.contains("notifySoldOutAfterCommit("),
                "a committed sold-out payment must still produce its operational alert");
    }

    private static String methodBody(String source, String signature) {
        int signatureStart = source.indexOf(signature);
        assertTrue(signatureStart >= 0, "Cannot find method signature: " + signature);
        int openBrace = source.indexOf('{', signatureStart);
        int depth = 0;
        for (int index = openBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') depth++;
            if (current == '}' && --depth == 0) return source.substring(openBrace + 1, index);
        }
        throw new AssertionError("Unclosed method body: " + signature);
    }
}

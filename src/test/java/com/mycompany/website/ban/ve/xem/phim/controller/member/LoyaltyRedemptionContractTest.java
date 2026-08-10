package com.mycompany.website.ban.ve.xem.phim.controller.member;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Keeps the member GET form and forged POST path wired to the service policy. */
@DisplayName("Loyalty servlet redemption contract")
class LoyaltyRedemptionContractTest {
    private static final Path SERVLET = Path.of(
            "src", "main", "java", "com", "mycompany", "website", "ban", "ve", "xem", "phim",
            "controller", "member", "LoyaltyServlet.java");
    private static final Path SERVICE = Path.of(
            "src", "main", "java", "com", "mycompany", "website", "ban", "ve", "xem", "phim",
            "service", "LoyaltyService.java");
    private static final Path PAGE = Path.of(
            "src", "main", "webapp", "WEB-INF", "views", "member", "loyalty.jsp");

    @Test
    @DisplayName("GET delegates the redeemable list to LoyaltyService, not AdminService")
    void getUsesTheSharedServicePolicy() throws IOException {
        String source = read(SERVLET);

        assertTrue(source.contains("loyaltyService.listRedeemablePromotionsForUser(user.getId())"),
                "GET must use the exact server-side redemption policy");
        assertFalse(source.contains("adminService.listPromotions()"),
                "Filtering the admin list in Java drifts from POST and from the DB clock");
    }

    @Test
    @DisplayName("POST requires and forwards an idempotency key and surfaces policy failures as 4xx")
    void postCarriesIdempotencyAndReturnsClientErrors() throws IOException {
        String servlet = read(SERVLET);
        String page = read(PAGE);

        assertTrue(page.contains("name=\"redemptionKey\""),
                "Each redemption form must carry its stable request key");
        assertTrue(servlet.contains("ServletUtil.param(request, \"redemptionKey\")"));
        assertTrue(servlet.contains(
                "redeemVoucherWithPoints(currentUser.getId(), promotionId, redemptionKey)"));
        assertTrue(servlet.contains("response.sendError(ex.getStatusCode(), ex.getMessage())"),
                "Forged/ineligible redemption must be an HTTP 4xx, not a success redirect");
    }

    @Test
    @DisplayName("service policy is DB-clock, quota-aware, locked and idempotent")
    void serviceContainsTheRequiredPolicyGates() throws IOException {
        String source = read(SERVICE);

        assertTrue(source.contains("CAST(GETDATE() AS DATE)"));
        assertTrue(source.contains("VoucherType = 'REDEEMABLE'"));
        assertTrue(source.contains("PointsRequired > 0"));
        assertTrue(source.contains("UsageLimit"));
        assertTrue(source.contains("IsUsed = 0"));
        assertTrue(source.contains("UPDLOCK"));
        assertTrue(source.contains("HOLDLOCK"));
        assertTrue(source.contains("IdempotencyKey"));
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

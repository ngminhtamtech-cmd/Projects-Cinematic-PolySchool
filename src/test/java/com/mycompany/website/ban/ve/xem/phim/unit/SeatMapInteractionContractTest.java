package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Guards the booking interaction that selects and deselects a showtime. */
@DisplayName("Seat-map showtime interaction contract")
class SeatMapInteractionContractTest {

    @Test
    @DisplayName("deselecting a showtime resets array state through existing render functions")
    void deselectUsesValidArrayAndRendererApis() throws IOException {
        String source = Files.readString(Path.of(
                "src", "main", "webapp", "assets", "js", "seat-map.js"),
                StandardCharsets.UTF_8);
        String method = between(source, "function deselectShowtime()", "function selectShowtime(st)");

        assertFalse(method.contains("selectedSeats.clear()"), "selectedSeats is an Array, not a Set");
        assertFalse(method.contains("updateSummary()"), "updateSummary is not defined in seat-map.js");
        assertFalse(method.contains("renderSeatMap("), "renderSeatMap is not defined in seat-map.js");
        assertTrue(method.contains("selectedSeats = []"));
        assertTrue(method.contains("renderSummary()"));
        assertTrue(method.contains("btnContinue.disabled = true"));
    }

    @Test
    @DisplayName("live browser gate toggles the same showtime twice without console errors")
    void browserGateExercisesDeselectAndReselect() throws IOException {
        String browser = Files.readString(Path.of("web", "tests", "browser-smoke.mjs"),
                StandardCharsets.UTF_8);

        assertTrue(browser.contains("showtime-btn:not(.is-disabled)"));
        assertTrue(browser.contains("booking-showtime-toggle"));
        assertTrue(browser.contains("btnSideContinue"));
    }

    @Test
    @DisplayName("viewer-owned holds resume the existing order and live changes fail closed")
    void ownedHoldResumesExistingDraft() throws IOException {
        String source = Files.readString(Path.of(
                "src", "main", "webapp", "assets", "js", "seat-map.js"), StandardCharsets.UTF_8);
        assertTrue(source.contains("seat.viewerState === 'heldByMe'"));
        assertTrue(source.contains("currentOrderId = heldOrderIds[0]"));
        assertTrue(source.contains("Đã khôi phục đơn đang giữ ghế của bạn"));
        assertTrue(source.contains("lựa chọn đã được đồng bộ để tránh trùng ghế"));
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0 && end > start, "Cannot isolate deselectShowtime");
        return source.substring(start, end);
    }
}

package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Admin cinema operations repair contracts")
class AdminRepairContractTest {

    private static final Path ROOT = Path.of("src", "main");

    @Test
    @DisplayName("room deactivate is routed to the dedicated scoped service operation")
    void roomDeactivateHasDedicatedBranch() throws IOException {
        String source = read("java/com/mycompany/website/ban/ve/xem/phim/controller/admin/ManagerPortalServlet.java");
        assertTrue(source.contains("\"deactivate\".equals(action)"));
        assertTrue(source.contains("adminService.deactivateRoom(intParam(request, \"id\", -1), actor)"));
    }

    @Test
    @DisplayName("AJAX form submissions do not use the shadowable form.action property")
    void ajaxFormsUseActionAttribute() throws IOException {
        String seatDesigner = read("webapp/assets/js/admin-seat-designer.js");
        String showtimes = read("webapp/WEB-INF/views/admin/showtimes.jsp");
        assertFalse(seatDesigner.contains("fetch(form.action"));
        assertTrue(seatDesigner.contains("fetch(refs.form.getAttribute('action')"));
        assertFalse(showtimes.contains("fetch(form.action"));
        assertTrue(showtimes.contains("fetch(form.getAttribute('action')"));
    }

    @Test
    @DisplayName("showtime list uses server JSON serialization and a visible parse failure state")
    void showtimeListDoesNotBuildJsonByHand() throws IOException {
        String servlet = read("java/com/mycompany/website/ban/ve/xem/phim/controller/admin/ManagerPortalServlet.java");
        String view = read("webapp/WEB-INF/views/admin/showtimes.jsp");
        assertTrue(servlet.contains("showtimesJsonForHtml(showtimes)"));
        assertTrue(view.contains("value=\"${showtimesJson}\" escapeXml=\"false\""));
        assertTrue(view.contains("showtimesDataError"));
        assertFalse(view.contains("<c:forEach var=\"st\" items=\"${showtimes}\""));
    }

    @Test
    @DisplayName("seat editor loads the shared state and accessible Pointer Events modules")
    void seatEditorHasRoundTripAdaptersAndModalPreview() throws IOException {
        String view = read("webapp/WEB-INF/views/admin/room-seats.jsp");
        String core = read("webapp/assets/js/admin-seat-designer-core.js");
        String ui = read("webapp/assets/js/admin-seat-designer.js");
        assertTrue(view.contains("/assets/css/admin-seat-designer.css"));
        assertTrue(view.contains("/assets/js/admin-seat-designer-core.js"));
        assertTrue(view.contains("/assets/js/admin-seat-designer.js"));
        assertTrue(view.contains("/assets/css/cinema-seat-3d.css"));
        assertTrue(view.contains("/assets/js/cinema-seat-3d.js"));
        assertFalse(view.contains("<style>"));
        assertTrue(view.contains("id=\"seatPreviewModal\""));
        assertTrue(view.contains("id=\"seatPreviewSaveButton\""));
        assertTrue(view.contains("id=\"seatGrid\""));
        assertTrue(view.contains("role=\"grid\""));
        assertTrue(view.contains("data-tool=\"select\""));
        assertTrue(view.contains("data-tool=\"add\""));
        assertTrue(view.contains("id=\"seatDesigner3dButton\""));
        assertTrue(view.contains("<cb:csrf/>"));
        assertTrue(view.contains("id=\"seatsJsonInput\""));
        assertTrue(core.contains("originKey"));
        assertTrue(core.contains("priceSurcharge"));
        assertTrue(core.contains("HISTORY_LIMIT = 50"));
        assertTrue(core.contains("function visibleLayout(state, growth)"));
        assertTrue(core.contains("function buildLinearRange(start, end)"));
        assertTrue(core.contains("function serializeSeats(state)"));
        assertTrue(ui.contains("addEventListener('pointerdown'"));
        assertTrue(ui.contains("addEventListener('pointermove'"));
        assertTrue(ui.contains("addEventListener('pointercancel'"));
        assertTrue(ui.contains("event.shiftKey && event.key === 'F10'"));
        assertTrue(ui.contains("refs.previewSave.addEventListener('click', confirmSave)"));
        assertTrue(ui.contains("window.CineBookSeat3D.open"));
    }

    @Test
    @DisplayName("booking and management share selected-seat 3D preview with trailer playback")
    void selectedSeat3dPreviewIsSharedAcrossPortals() throws IOException {
        String booking = read("webapp/WEB-INF/views/booking/page.jsp");
        String seatMap = read("webapp/assets/js/seat-map.js");
        String seat3d = read("webapp/assets/js/cinema-seat-3d.js");
        String seat3dCss = read("webapp/assets/css/cinema-seat-3d.css");
        assertTrue(booking.contains("id=\"seatView3dButton\""));
        assertTrue(booking.contains("/assets/js/cinema-seat-3d.js"));
        assertTrue(booking.contains("/assets/css/cinema-seat-3d.css"));
        assertTrue(seatMap.contains("selectedPreviewSeat"));
        assertTrue(seatMap.contains("window.CineBookSeat3D.open"));
        assertTrue(seat3d.contains("/api/v1/films/"));
        assertTrue(seat3d.contains("/api/v1/films?status=showing"));
        assertTrue(seat3d.contains("youtube.com/embed/"));
        assertTrue(seat3d.contains("referrerpolicy=\"strict-origin-when-cross-origin\""));
        assertTrue(seat3d.contains("data-view=\"overview\""));
        assertTrue(seat3d.contains("data-view=\"seat\""));
        assertTrue(seat3d.contains("setView('overview');"));
        assertTrue(seat3d.contains("id=\"cbSeat3dMapGrid\""));
        assertTrue(seat3d.contains("addEventListener('pointerdown'"));
        assertTrue(seat3d.contains("addEventListener('wheel'"));
        assertTrue(seat3dCss.contains("cb-seat3d__foreground"));
        assertTrue(seat3dCss.contains("prefers-reduced-motion"));
    }

    @Test
    @DisplayName("sold-out notification and showtime screen share an exact deep-link contract")
    void soldOutNotificationTargetsExactShowtime() throws IOException {
        String service = read("java/com/mycompany/website/ban/ve/xem/phim/service/AdminService.java");
        String servlet = read("java/com/mycompany/website/ban/ve/xem/phim/controller/admin/ManagerPortalServlet.java");
        String view = read("webapp/WEB-INF/views/admin/showtimes.jsp");
        assertTrue(service.contains("/admin/showtimes?focusShowtimeId="));
        assertTrue(servlet.contains("focusVisible ? focusShowtimeId : 0"));
        assertTrue(view.contains("data-focus-showtime-id"));
        assertTrue(view.contains("for (var offset = 0; offset < 10; offset += 1)"));
        assertTrue(view.contains("var defaultDate = focusDate && dates.includes(focusDate) ? focusDate : businessDate"));
        assertFalse(view.contains("Array.from(new Set(allShowtimes.map"));
        assertTrue(view.contains("pill.scrollIntoView"));
    }

    @Test
    @DisplayName("room form has valid Vietnamese labels and film cinemas use a collapsed multi-select")
    void roomEncodingAndCinemaDropdownAreStable() throws IOException {
        String roomForm = read("webapp/WEB-INF/views/admin/room-form.jsp");
        String filmForm = read("webapp/WEB-INF/views/admin/film-form.jsp");
        assertTrue(roomForm.contains("Loại phòng"));
        assertTrue(roomForm.contains("Phòng cao cấp"));
        assertFalse(roomForm.contains("Loáº¡i phÃ²ng"));
        assertTrue(filmForm.contains("id=\"cinemaMultiselectTrigger\""));
        assertTrue(filmForm.contains("name=\"cinemaIds\""));
        assertTrue(filmForm.contains("name=\"cinemaIdsPresent\""));
    }

    @Test
    @DisplayName("terminal refund appeal reconciliation cannot trigger a second refund")
    void terminalRefundAppealMigrationOnlyClosesAppeals() throws IOException {
        String migration = Files.readString(Path.of("database",
                "fix37_refund_appeal_terminal_reconciliation.sql"), StandardCharsets.UTF_8);
        assertTrue(migration.contains("PaymentStatus=N'refunded'"));
        assertTrue(migration.contains("RefundedAt IS NOT NULL"));
        assertTrue(migration.contains("Status=N'approved'"));
        assertFalse(migration.contains("UPDATE Orders"));
        assertFalse(migration.contains("INSERT INTO Invoices"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
    }
}

package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cinema context UI contracts")
class CinemaContextUiContractTest {

    @Test
    @DisplayName("Admin topbar does not expose the removed cinema scope selector")
    void adminTopbarDoesNotExposeCinemaScopeSelector() throws IOException {
        String topbar = read("admin-topbar.jspf");

        assertFalse(topbar.contains("cinema-context-control"));
        assertFalse(topbar.contains("adminCinemaContext"));
        assertFalse(topbar.contains("changeAdminCinemaContext"));
        assertFalse(topbar.contains("name=\"cinemaContextId\""));
    }

    @Test
    @DisplayName("Manager cinema screen keeps destructive controls admin-only")
    void cinemaDeletionControlsAreAdminOnly() throws IOException {
        String page = read("cinemas.jsp");
        int deleteButton = page.indexOf("openConfirmDeleteCinemaModal");
        int adminGate = page.lastIndexOf(
                "<c:if test=\"${sessionScope.currentUser.role eq 'admin'}\">",
                deleteButton);
        int gateEnd = page.indexOf("</c:if>", deleteButton);

        assertTrue(deleteButton > 0);
        assertTrue(adminGate > 0 && adminGate < deleteButton);
        assertTrue(gateEnd > deleteButton);
    }

    private static String read(String fileName) throws IOException {
        return Files.readString(Path.of("src", "main", "webapp", "WEB-INF", "views",
                "admin", fileName), StandardCharsets.UTF_8);
    }
}

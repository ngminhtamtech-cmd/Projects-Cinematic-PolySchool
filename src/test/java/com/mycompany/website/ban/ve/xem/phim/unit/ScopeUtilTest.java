package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.util.ScopeUtil;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScopeUtilTest {
    private static final List<String> RESOURCE_TYPES = List.of(
            "film", "cinema", "room", "seat", "showtime",
            "order", "report", "combo", "promotion", "notification");

    @Test
    void managerCanOnlyAccessAssignedCinemaForEveryResourceCategory() {
        User manager = actor("manager", 7);
        for (String resource : RESOURCE_TYPES) {
            assertDoesNotThrow(() -> ScopeUtil.assertCinemaScope(manager, 7), resource);
            BookingException ex = assertThrows(BookingException.class,
                    () -> ScopeUtil.assertCinemaScope(manager, 8), resource);
            assertEquals(403, ex.getStatusCode());
        }
    }

    @Test
    void systemAdminIsUnscoped() {
        User admin = actor("admin", null);
        assertDoesNotThrow(() -> ScopeUtil.assertCinemaScope(admin, 999));
        assertEquals("1=1", ScopeUtil.scopeFilter(admin, "s.CinemaId").sql());
    }

    @Test
    void managerWithoutAssignmentFailsClosed() {
        assertThrows(BookingException.class,
                () -> ScopeUtil.assertCinemaScope(actor("manager", null), 1));
    }

    private User actor(String role, Integer cinemaId) {
        User user = new User();
        user.setRole(role);
        user.setCinemaId(cinemaId);
        return user;
    }
}

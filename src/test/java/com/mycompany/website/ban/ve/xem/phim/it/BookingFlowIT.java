package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.Promotion;
import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("it")
public class BookingFlowIT {

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private BookingService bookingService;

    @BeforeAll
    public static void setUpTestDb() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("Find showtime by ID on seeded test database")
    public void testFindShowtime() {
        bookingService = new BookingService();
        Optional<Showtime> opt = bookingService.findShowtime(3);
        assertTrue(opt.isPresent());
        Showtime showtime = opt.get();
        assertEquals(1, showtime.getFilmId());
        assertEquals(1, showtime.getRoomId());
    }

    @Test
    @DisplayName("Get seat map for future showtime returns seeded seats")
    public void testGetSeatMap() {
        bookingService = new BookingService();
        List<ShowtimeSeat> seatMap = bookingService.getSeatMap(3);
        assertNotNull(seatMap);
        assertFalse(seatMap.isEmpty());
    }

    @Test
    @DisplayName("Resolve promotion PUBLIC10 should succeed")
    public void testResolvePublicPromotion() {
        bookingService = new BookingService();
        Promotion promo = bookingService.resolvePromotion("PUBLIC10", "BRONZE");
        assertNotNull(promo);
        assertEquals("PUBLIC10", promo.getCode());
        assertEquals(Double.valueOf(10.0), promo.getDiscountPercent());
    }

    @Test
    @DisplayName("Resolve exhausted promotion should throw BookingException 400")
    public void testResolveExhaustedPromotion() {
        bookingService = new BookingService();
        BookingException ex = assertThrows(BookingException.class, () -> {
            bookingService.resolvePromotion("EXHAUSTED", "BRONZE");
        });
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("Resolve TIER_RESTRICTED promotion for BRONZE user should throw BookingException 400")
    public void testResolveTierRestrictedPromotionForbidden() {
        bookingService = new BookingService();
        BookingException ex = assertThrows(BookingException.class, () -> {
            bookingService.resolvePromotion("DIAMOND50", "BRONZE");
        });
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("Resolve TIER_RESTRICTED promotion for DIAMOND user should succeed")
    public void testResolveTierRestrictedPromotionAllowed() {
        bookingService = new BookingService();
        Promotion promo = bookingService.resolvePromotion("DIAMOND50", "DIAMOND");
        assertNotNull(promo);
        assertEquals("DIAMOND50", promo.getCode());
    }
}

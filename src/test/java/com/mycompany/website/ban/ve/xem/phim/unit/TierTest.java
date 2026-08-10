package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TierTest {

    private BookingService bookingService;
    private Method rankTierMethod;

    @BeforeEach
    public void setUp() throws Exception {
        bookingService = new BookingService();
        rankTierMethod = BookingService.class.getDeclaredMethod("rankTier", String.class);
        rankTierMethod.setAccessible(true);
    }

    private int invokeRankTier(String tier) throws Exception {
        return (Integer) rankTierMethod.invoke(bookingService, tier);
    }

    @Test
    @DisplayName("Tier ranks should satisfy BRONZE < SILVER < DIAMOND < EMERALD")
    public void testTierRankingOrder() throws Exception {
        int bronzeRank = invokeRankTier("BRONZE");
        int silverRank = invokeRankTier("SILVER");
        int diamondRank = invokeRankTier("DIAMOND");
        int emeraldRank = invokeRankTier("EMERALD");

        assertTrue(bronzeRank < silverRank);
        assertTrue(silverRank < diamondRank);
        assertTrue(diamondRank < emeraldRank);
    }

    @Test
    @DisplayName("Case insensitive tier names should return correct rank")
    public void testCaseInsensitiveTier() throws Exception {
        assertEquals(1, invokeRankTier("bronze"));
        assertEquals(2, invokeRankTier("Silver"));
        assertEquals(3, invokeRankTier("DiaMonD"));
        assertEquals(4, invokeRankTier("emerald"));
    }

    @Test
    @DisplayName("Unknown or null tier should default to BRONZE rank (1)")
    public void testUnknownTierDefault() throws Exception {
        assertEquals(1, invokeRankTier("UNKNOWN_TIER"));
        assertEquals(1, invokeRankTier(null));
        assertEquals(1, invokeRankTier(""));
    }
}

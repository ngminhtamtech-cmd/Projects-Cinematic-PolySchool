package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ParseTest {

    @Test
    @DisplayName("Parse seat IDs string '101, 102, 103' into List<Integer>")
    public void testParseSeatIdsNormal() {
        List<Integer> seatIds = BookingService.parseSeatIds("101, 102, 103");
        assertEquals(List.of(101, 102, 103), seatIds);
    }

    @Test
    @DisplayName("Parse seat IDs with invalid tokens should filter invalid tokens")
    public void testParseSeatIdsInvalidTokens() {
        List<Integer> seatIds = BookingService.parseSeatIds("101, abc, 105, ");
        assertEquals(List.of(101, 105), seatIds);
    }

    @Test
    @DisplayName("Parse empty or null seat IDs string should return empty list")
    public void testParseSeatIdsEmptyOrNull() {
        assertTrue(BookingService.parseSeatIds(null).isEmpty());
        assertTrue(BookingService.parseSeatIds("").isEmpty());
        assertTrue(BookingService.parseSeatIds("   ").isEmpty());
    }

    @Test
    @DisplayName("Parse combo selections string '1:2, 2:1' into Map<Integer, Integer>")
    public void testParseComboSelectionsNormal() {
        Map<Integer, Integer> combos = BookingService.parseComboSelections("1:2, 2:1");
        assertEquals(2, combos.size());
        assertEquals(2, combos.get(1));
        assertEquals(1, combos.get(2));
    }

    @Test
    @DisplayName("Parse combo selections should ignore non-positive quantities or malformed pairs")
    public void testParseComboSelectionsMalformed() {
        Map<Integer, Integer> combos = BookingService.parseComboSelections("1:0, 2:-1, 3:2, invalid_token");
        assertEquals(1, combos.size());
        assertEquals(2, combos.get(3));
    }
}

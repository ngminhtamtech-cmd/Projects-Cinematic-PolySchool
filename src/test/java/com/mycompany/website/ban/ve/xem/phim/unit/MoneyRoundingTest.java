package com.mycompany.website.ban.ve.xem.phim.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

public class MoneyRoundingTest {

    @Test
    @DisplayName("VND calculations must round to 0 decimal places with HALF_UP")
    void testVndRoundingHalfUp() {
        BigDecimal price = new BigDecimal("125000");
        BigDecimal discountPercent = new BigDecimal("15"); // 15% of 125,000 = 18,750

        BigDecimal discount = price.multiply(discountPercent).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("18750"), discount);

        BigDecimal priceOdd = new BigDecimal("125005");
        BigDecimal discountOdd = priceOdd.multiply(new BigDecimal("15")).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
        // 125,005 * 0.15 = 18750.75 -> HALF_UP = 18751
        assertEquals(new BigDecimal("18751"), discountOdd);
    }

    @Test
    @DisplayName("Large order amounts > 100 Million VND must not overflow or lose precision")
    void testLargeOrderAmount() {
        // Renting whole cinema or corporate event: 500 seats * 500,000 VND + 200 combos * 1,000,000 VND
        BigDecimal seatsTotal = new BigDecimal("500").multiply(new BigDecimal("500000")); // 250,000,000 VND
        BigDecimal combosTotal = new BigDecimal("200").multiply(new BigDecimal("1000000")); // 200,000,000 VND
        BigDecimal grandTotal = seatsTotal.add(combosTotal).setScale(0, RoundingMode.HALF_UP);

        assertEquals(new BigDecimal("450000000"), grandTotal); // 450 Million VND
        assertTrue(grandTotal.compareTo(new BigDecimal("100000000")) > 0);
    }
}

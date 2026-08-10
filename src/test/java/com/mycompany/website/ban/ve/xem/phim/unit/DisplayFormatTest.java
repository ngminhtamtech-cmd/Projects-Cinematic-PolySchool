package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mycompany.website.ban.ve.xem.phim.util.DisplayFormat;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Vietnamese display number formatter")
class DisplayFormatTest {

    @Test
    void formatsMoneyAndWholeNumbersWithoutRawDecimals() {
        assertEquals("429.000", DisplayFormat.whole(new BigDecimal("429000.00")));
        assertEquals("0", DisplayFormat.whole(null));
        assertEquals("12.345", DisplayFormat.whole(12345));
    }

    @Test
    void formatsPercentageWithVietnameseDecimalSeparator() {
        assertEquals("10", DisplayFormat.decimal(10.0d));
        assertEquals("10,5", DisplayFormat.decimal(new BigDecimal("10.50")));
    }
}

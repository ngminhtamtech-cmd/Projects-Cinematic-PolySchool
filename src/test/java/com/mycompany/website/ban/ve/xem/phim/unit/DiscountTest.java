package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.model.Promotion;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DiscountTest {

    private BookingService bookingService;
    private Method calculateDiscountMethod;

    @BeforeEach
    public void setUp() throws Exception {
        bookingService = new BookingService();
        calculateDiscountMethod = BookingService.class.getDeclaredMethod("calculateDiscount", Promotion.class, BigDecimal.class);
        calculateDiscountMethod.setAccessible(true);
    }

    private BigDecimal invokeCalculateDiscount(Promotion promo, BigDecimal grossTotal) throws Exception {
        return (BigDecimal) calculateDiscountMethod.invoke(bookingService, promo, grossTotal);
    }

    @Test
    @DisplayName("Discount 10% on 100,000 VND should return 10,000 VND")
    public void testPercentageDiscountNormal() throws Exception {
        Promotion promo = new Promotion();
        promo.setDiscountPercent(10.0);

        BigDecimal discount = invokeCalculateDiscount(promo, new BigDecimal("100000.00"));
        assertEquals(0, new BigDecimal("10000.00").compareTo(discount));
    }

    @Test
    @DisplayName("Discount should be capped at MaxDiscount if calculated amount exceeds MaxDiscount")
    public void testMaxDiscountCap() throws Exception {
        Promotion promo = new Promotion();
        promo.setDiscountPercent(50.0);
        promo.setMaxDiscount(new BigDecimal("30000.00"));

        BigDecimal discount = invokeCalculateDiscount(promo, new BigDecimal("100000.00"));
        assertEquals(0, new BigDecimal("30000.00").compareTo(discount));
    }

    @Test
    @DisplayName("Null promotion should return 0 discount")
    public void testNullPromotion() throws Exception {
        BigDecimal discount = invokeCalculateDiscount(null, new BigDecimal("100000.00"));
        assertEquals(BigDecimal.ZERO, discount);
    }

    @Test
    @DisplayName("Zero or negative total should return 0 discount")
    public void testZeroOrNegativeTotal() throws Exception {
        Promotion promo = new Promotion();
        promo.setDiscountPercent(20.0);

        assertEquals(BigDecimal.ZERO, invokeCalculateDiscount(promo, BigDecimal.ZERO));
        assertEquals(BigDecimal.ZERO, invokeCalculateDiscount(promo, new BigDecimal("-50000.00")));
    }
}

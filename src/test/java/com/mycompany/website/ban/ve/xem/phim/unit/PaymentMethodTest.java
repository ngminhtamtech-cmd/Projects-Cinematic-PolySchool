package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.model.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class PaymentMethodTest {

    @Test
    @DisplayName("Whitelisted payment methods 'card' and 'counter' must be valid")
    void testValidPaymentMethods() {
        Optional<PaymentMethod> card = PaymentMethod.fromCode("card");
        assertTrue(card.isPresent());
        assertEquals(PaymentMethod.CARD, card.get());

        Optional<PaymentMethod> counter = PaymentMethod.fromCode("counter");
        assertTrue(counter.isPresent());
        assertEquals(PaymentMethod.COUNTER, counter.get());
    }

    @Test
    @DisplayName("Payment method parsing must be case-insensitive")
    void testCaseInsensitivity() {
        assertTrue(PaymentMethod.fromCode("CARD").isPresent());
        assertTrue(PaymentMethod.fromCode("Counter").isPresent());
    }

    @Test
    @DisplayName("Invalid or garbage payment methods must return empty and fail isValid")
    void testInvalidPaymentMethods() {
        assertFalse(PaymentMethod.isValid("crypto"));
        assertFalse(PaymentMethod.isValid("vnpay_fake"));
        assertFalse(PaymentMethod.isValid("free_ticket"));
        assertFalse(PaymentMethod.isValid(""));
        assertFalse(PaymentMethod.isValid(null));
        assertTrue(PaymentMethod.fromCode("paypal").isEmpty());
    }
}

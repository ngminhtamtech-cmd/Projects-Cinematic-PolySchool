package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.service.InvoiceService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InvoiceVatTest {
    @Test
    void vatInclusiveBreakdownAlwaysReconcilesToOrderTotal() {
        var result = InvoiceService.calculateVat(new BigDecimal("110000"), new BigDecimal("10"));
        assertEquals(new BigDecimal("100000.00"), result.subTotal());
        assertEquals(new BigDecimal("10000.00"), result.vatAmount());
        assertEquals(new BigDecimal("110000.00"), result.subTotal().add(result.vatAmount()));
    }

    @Test
    void roundingStillReconcilesExactly() {
        var result = InvoiceService.calculateVat(new BigDecimal("99999"), new BigDecimal("10"));
        assertEquals(new BigDecimal("99999.00"), result.subTotal().add(result.vatAmount()));
    }
}

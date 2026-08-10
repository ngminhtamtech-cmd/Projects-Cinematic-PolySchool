package com.mycompany.website.ban.ve.xem.phim.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HeaderFilterSkipTest {
    @Test
    void postAndPrivateRoutesNeverLoadHeaderData() {
        assertFalse(HeaderDataFilter.shouldLoadHeader("POST", "/booking/create"));
        assertFalse(HeaderDataFilter.shouldLoadHeader("POST", "/home"));
        assertFalse(HeaderDataFilter.shouldLoadHeader("GET", "/admin/orders"));
        assertFalse(HeaderDataFilter.shouldLoadHeader("GET", "/api/v1/catalog/films"));
        assertFalse(HeaderDataFilter.shouldLoadHeader("GET", "/assets/app.js"));
        assertTrue(HeaderDataFilter.shouldLoadHeader("GET", "/films"));
    }
}

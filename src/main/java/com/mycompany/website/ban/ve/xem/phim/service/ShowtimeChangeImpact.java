package com.mycompany.website.ban.ve.xem.phim.service;

/** Typed preview for a material showtime edit. */
public record ShowtimeChangeImpact(int showtimeId, int orderCount, int customerCount,
        int occupiedSeatCount, boolean requiresConfirmation) {
}

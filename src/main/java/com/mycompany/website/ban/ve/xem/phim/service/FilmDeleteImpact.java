package com.mycompany.website.ban.ve.xem.phim.service;

/** Typed preview returned before an irreversible film delete/tombstone command. */
public record FilmDeleteImpact(int filmId, String title, int currentOrFutureShowtimeCount,
        int historicalShowtimeCount, int activeHoldCount, int activeDraftOrderCount,
        int committedOrderCount, int historicalOrderCount, int commentCount,
        int commentReportCount, int cinemaCount, boolean expiredOrWithdrawn,
        boolean eligible, String blockedReason) {
}

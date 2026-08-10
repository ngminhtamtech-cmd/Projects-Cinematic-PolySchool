package com.mycompany.website.ban.ve.xem.phim.service;

import java.time.LocalDateTime;

/** Live, server-authoritative readiness snapshot for two-phase showtime deletion. */
public record ShowtimeDeletionImpact(int showtimeId, String filmTitle, String cinemaName,
        String roomName, LocalDateTime startTime, LocalDateTime endTime, String saleStatus,
        LocalDateTime deleteRequestedAt, LocalDateTime deleteNotBefore,
        int activeHoldCount, int activeDraftOrderCount, int committedOrderCount,
        int terminalOrderCount, long secondsRemaining, boolean ready, String blockedReason) {
}


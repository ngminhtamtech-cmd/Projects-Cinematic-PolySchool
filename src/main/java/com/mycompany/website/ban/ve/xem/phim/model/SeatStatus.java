package com.mycompany.website.ban.ve.xem.phim.model;

import java.util.Locale;
import java.util.Optional;

/** Canonical values accepted by ShowtimeSeats.Status. */
public enum SeatStatus {
    AVAILABLE("available"),
    HELD("held"),
    BOOKED("booked"),
    MAINTENANCE("maintenance");

    private final String code;

    SeatStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<SeatStatus> fromCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (SeatStatus status : values()) {
            if (status.code.equals(normalized)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
    
    public boolean hasHoldMetadata() {
        return this == HELD;
    }
}

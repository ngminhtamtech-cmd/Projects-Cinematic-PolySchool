package com.mycompany.website.ban.ve.xem.phim.model;

import java.util.Locale;
import java.util.Optional;

public enum MembershipTier {
    BRONZE(1),
    SILVER(2),
    DIAMOND(3),
    EMERALD(4);

    private final int rank;

    MembershipTier(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public static Optional<MembershipTier> fromCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}

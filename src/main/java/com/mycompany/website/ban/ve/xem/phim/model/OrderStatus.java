package com.mycompany.website.ban.ve.xem.phim.model;

import java.util.Locale;
import java.util.Optional;

/** Canonical values accepted by Orders.OrderStatus. */
public enum OrderStatus {
    CREATED("created"),
    PENDING("pending"),
    CONFIRMED("confirmed"),
    CANCELLED("cancelled"),
    COMPLETED("completed"),
    REDEEMED("redeemed");

    private final String code;

    OrderStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<OrderStatus> fromCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (OrderStatus status : values()) {
            if (status.code.equals(normalized)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}

package com.mycompany.website.ban.ve.xem.phim.model;

import java.util.Locale;
import java.util.Optional;

/** Canonical values accepted by Orders.PaymentStatus. */
public enum PaymentStatus {
    PENDING("pending"),
    PAID("paid"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    REFUNDED("refunded");

    private final String code;

    PaymentStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<PaymentStatus> fromCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (PaymentStatus status : values()) {
            if (status.code.equals(normalized)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}

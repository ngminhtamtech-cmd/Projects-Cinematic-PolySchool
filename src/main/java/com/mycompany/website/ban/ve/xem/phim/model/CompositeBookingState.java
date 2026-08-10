package com.mycompany.website.ban.ve.xem.phim.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Domain state derived from the two legacy Orders status columns.
 *
 * <p>The database columns remain the wire/storage contract.  This type is the
 * single application-level vocabulary used to validate their combinations.</p>
 */
public enum CompositeBookingState {
    DRAFT_HELD("DRAFT_HELD"),
    PAYMENT_FAILED_RETRYABLE("PAYMENT_FAILED_RETRYABLE"),
    COUNTER_AWAITING_PAYMENT("COUNTER_AWAITING_PAYMENT"),
    PAID_CONFIRMED("PAID_CONFIRMED"),
    PAID_REDEEMED("PAID_REDEEMED"),
    CANCELLED_UNPAID("CANCELLED_UNPAID"),
    FULLY_REFUNDED("FULLY_REFUNDED"),
    COMPLETED_LEGACY("COMPLETED_LEGACY"),
    UNKNOWN("UNKNOWN");

    private final String code;

    CompositeBookingState(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<CompositeBookingState> fromCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (CompositeBookingState state : values()) {
            if (state.code.equals(normalized)) {
                return Optional.of(state);
            }
        }
        return Optional.empty();
    }

    public boolean isTerminal() {
        return this == CANCELLED_UNPAID || this == FULLY_REFUNDED
                || this == PAID_REDEEMED || this == COMPLETED_LEGACY;
    }

    public static CompositeBookingState classify(String orderStatus, String paymentStatus,
            String paymentMethod) {
        String order = normalize(orderStatus);
        String payment = normalize(paymentStatus);
        String method = normalize(paymentMethod);
        if (("created".equals(order) || "pending".equals(order)) && "pending".equals(payment)) {
            return DRAFT_HELD;
        }
        if (("created".equals(order) || "pending".equals(order)) && "failed".equals(payment)) {
            return PAYMENT_FAILED_RETRYABLE;
        }
        if ("confirmed".equals(order) && "pending".equals(payment) && "counter".equals(method)) {
            return COUNTER_AWAITING_PAYMENT;
        }
        if ("confirmed".equals(order) && "paid".equals(payment)) {
            return PAID_CONFIRMED;
        }
        if ("redeemed".equals(order) && "paid".equals(payment)) {
            return PAID_REDEEMED;
        }
        if ("cancelled".equals(order) && "cancelled".equals(payment)) {
            return CANCELLED_UNPAID;
        }
        if ("cancelled".equals(order) && "refunded".equals(payment)) {
            return FULLY_REFUNDED;
        }
        if ("completed".equals(order) && "paid".equals(payment)) {
            return COMPLETED_LEGACY;
        }
        return UNKNOWN;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

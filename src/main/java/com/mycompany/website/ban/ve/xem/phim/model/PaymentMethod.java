package com.mycompany.website.ban.ve.xem.phim.model;

import java.util.Optional;

public enum PaymentMethod {
    CARD("card", "Thanh toán thẻ/trực tuyến"),
    COUNTER("counter", "Thanh toán tại quầy");

    private final String code;
    private final String displayName;

    PaymentMethod(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Optional<PaymentMethod> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String clean = code.trim().toLowerCase();
        for (PaymentMethod method : values()) {
            if (method.code.equalsIgnoreCase(clean)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    public static boolean isValid(String code) {
        return fromCode(code).isPresent();
    }
}

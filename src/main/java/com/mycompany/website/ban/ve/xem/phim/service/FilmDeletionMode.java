package com.mycompany.website.ban.ve.xem.phim.service;

import java.util.Locale;

/** Explicit policy for user-generated comments when an historical film is tombstoned. */
public enum FilmDeletionMode {
    PURGE_COMMENTS,
    PRESERVE_COMMENTS;

    public static FilmDeletionMode fromCode(String value) {
        if (value == null || value.isBlank()) {
            return PRESERVE_COMMENTS;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BookingException(400, "Chế độ xóa phim không hợp lệ.");
        }
    }
}


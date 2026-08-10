package com.mycompany.website.ban.ve.xem.phim.service;

public class BookingException extends RuntimeException {
    private final int statusCode;

    public BookingException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public BookingException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}

package com.mycompany.website.ban.ve.xem.phim.service.payment;

public class PaymentResult {
    private final boolean success;
    private final String transactionId;
    private final String provider;
    private final String redirectUrl;
    private final String message;

    public PaymentResult(boolean success, String transactionId, String provider, String redirectUrl, String message) {
        this.success = success;
        this.transactionId = transactionId;
        this.provider = provider;
        this.redirectUrl = redirectUrl;
        this.message = message;
    }

    public static PaymentResult success(String transactionId, String provider) {
        return new PaymentResult(true, transactionId, provider, null, "Thanh toán giả lập thành công");
    }

    public static PaymentResult success(String transactionId, String provider, String redirectUrl) {
        return new PaymentResult(true, transactionId, provider, redirectUrl, "Thanh toán giả lập thành công");
    }

    public static PaymentResult failure(String provider, String message) {
        return new PaymentResult(false, null, provider, null, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getProvider() {
        return provider;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public String getMessage() {
        return message;
    }
}

package com.mycompany.website.ban.ve.xem.phim.model;

import java.time.LocalDateTime;

/**
 * Trang thai giu ghe that cua mot don hang, doc truc tiep tu DB.
 *
 * <p>Ton tai de sua B3: dong ho dem nguoc tren trinh duyet truoc day chay doc lap 600 giay,
 * khong lien quan gi toi {@code ShowtimeSeats.HeldUntil} trong DB. Khi may khach lech gio,
 * F5 lai trang, hoac sweeper da giai phong ghe som hon, UI van dem tiep va nguoi dung bam
 * thanh toan de nhan 409.</p>
 *
 * <p>{@code remainingSeconds} duoc <b>SQL Server tinh</b> ({@code DATEDIFF(SECOND, GETDATE(), HeldUntil)}),
 * khong tinh bang gio may chu ung dung - dung quy tac "gio DB la nguon thoi gian duy nhat" chot o P03.</p>
 */
public class OrderHoldStatus {
    private int orderId;
    private String orderStatus;
    private String paymentStatus;
    private LocalDateTime heldUntil;
    private int remainingSeconds;
    private int heldSeatCount;

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public LocalDateTime getHeldUntil() {
        return heldUntil;
    }

    public void setHeldUntil(LocalDateTime heldUntil) {
        this.heldUntil = heldUntil;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public int getHeldSeatCount() {
        return heldSeatCount;
    }

    public void setHeldSeatCount(int heldSeatCount) {
        this.heldSeatCount = heldSeatCount;
    }

    /** Het han khi khong con ghe nao dang duoc giu cho don nay, hoac han giu da troi qua. */
    public boolean isExpired() {
        return heldSeatCount <= 0 || heldUntil == null || remainingSeconds <= 0;
    }
}

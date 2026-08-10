package com.mycompany.website.ban.ve.xem.phim.model;

import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class OrderRecord {
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private int id;
    private int userId;
    private int showtimeId;
    private Integer promotionId;
    private BigDecimal seatSubtotal;
    private BigDecimal comboSubtotal;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String ticketCode;
    private String ticketQrUrl;
    private String paymentMethod;
    private String paymentStatus;
    private String transactionId;
    private String payRedirectUrl;
    private String paymentProvider;
    private String idempotencyKey;
    private LocalDateTime counterExpiresAt;
    private LocalDateTime refundedAt;
    private BigDecimal refundAmount;
    private String refundReason;
    private Integer refundedBy;
    private LocalDateTime refundRejectedAt;
    private String refundRejectReason;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private String orderStatus;
    private LocalDateTime redeemedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String stateVersion;
    private boolean replayed;
    private String filmTitle;
    /** Cum rap cua suat chieu — dung de kiem pham vi o duong doc (BUG-07). Null neu truy van khong chon. */
    private Integer cinemaId;
    private String cinemaName;
    private String roomName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int durationMinutes;
    private String userEmail;
    private String userFullName;
    private LocalDateTime businessNow;
    private boolean pendingRefundAppeal;
    private final List<OrderSeatItem> seats = new ArrayList<>();
    private final List<OrderComboItem> combos = new ArrayList<>();

    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }
    public Integer getRefundedBy() { return refundedBy; }
    public void setRefundedBy(Integer refundedBy) { this.refundedBy = refundedBy; }
    public LocalDateTime getRefundRejectedAt() { return refundRejectedAt; }
    public void setRefundRejectedAt(LocalDateTime refundRejectedAt) { this.refundRejectedAt = refundRejectedAt; }
    public String getRefundRejectReason() { return refundRejectReason; }
    public void setRefundRejectReason(String refundRejectReason) { this.refundRejectReason = refundRejectReason; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public int getShowtimeId() { return showtimeId; }
    public void setShowtimeId(int showtimeId) { this.showtimeId = showtimeId; }
    public Integer getPromotionId() { return promotionId; }
    public void setPromotionId(Integer promotionId) { this.promotionId = promotionId; }
    public BigDecimal getSeatSubtotal() { return seatSubtotal; }
    public void setSeatSubtotal(BigDecimal seatSubtotal) { this.seatSubtotal = seatSubtotal; }
    public BigDecimal getComboSubtotal() { return comboSubtotal; }
    public void setComboSubtotal(BigDecimal comboSubtotal) { this.comboSubtotal = comboSubtotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }
    public String getTicketQrUrl() { return ticketQrUrl; }
    public void setTicketQrUrl(String ticketQrUrl) { this.ticketQrUrl = ticketQrUrl; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getPayRedirectUrl() { return payRedirectUrl; }
    public void setPayRedirectUrl(String payRedirectUrl) { this.payRedirectUrl = payRedirectUrl; }
    public String getPaymentProvider() { return paymentProvider; }
    public void setPaymentProvider(String paymentProvider) { this.paymentProvider = paymentProvider; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public LocalDateTime getCounterExpiresAt() { return counterExpiresAt; }
    public void setCounterExpiresAt(LocalDateTime counterExpiresAt) { this.counterExpiresAt = counterExpiresAt; }
    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
    public LocalDateTime getRedeemedAt() { return redeemedAt; }
    public void setRedeemedAt(LocalDateTime redeemedAt) { this.redeemedAt = redeemedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getStateVersion() { return stateVersion; }
    public void setStateVersion(String stateVersion) { this.stateVersion = stateVersion; }
    public boolean isReplayed() { return replayed; }
    public void setReplayed(boolean replayed) { this.replayed = replayed; }
    public String getFilmTitle() { return filmTitle; }
    public void setFilmTitle(String filmTitle) { this.filmTitle = filmTitle; }
    public Integer getCinemaId() { return cinemaId; }
    public void setCinemaId(Integer cinemaId) { this.cinemaId = cinemaId; }
    public String getCinemaName() { return cinemaName; }
    public void setCinemaName(String cinemaName) { this.cinemaName = cinemaName; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() {
        if (endTime != null) return endTime;
        if (startTime != null) {
            int minutes = durationMinutes > 0 ? durationMinutes : 120;
            return startTime.plusMinutes(minutes);
        }
        return null;
    }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getUserFullName() { return userFullName; }
    public void setUserFullName(String userFullName) { this.userFullName = userFullName; }

    /**
     * Ghim gio nghiep vu dung de phan loai ve cua ban ghi nay (F-004).
     *
     * <p>Khi mot request da doc gio DB roi (vi du {@code StaffService.lookupTicket}), no ghim lai
     * o day de ket luan tra ve nhan vien va nhan trang thai tren giao dien duoc tinh tu <b>cung
     * mot moc thoi gian</b>. Neu khong ghim, cac ham phan loai tu doc
     * {@link BusinessClock#now()}.</p>
     */
    public void setBusinessNow(LocalDateTime businessNow) { this.businessNow = businessNow; }
    public boolean isPendingRefundAppeal() { return pendingRefundAppeal; }
    public void setPendingRefundAppeal(boolean pendingRefundAppeal) { this.pendingRefundAppeal = pendingRefundAppeal; }

    public boolean isRefundAppealEligible() {
        return com.mycompany.website.ban.ve.xem.phim.service.RefundAppealPolicy.evaluate(this).eligible();
    }

    public String getRefundAppealMessage() {
        return com.mycompany.website.ban.ve.xem.phim.service.RefundAppealPolicy.evaluate(this).message();
    }

    /** Gio nghiep vu dang duoc dung de phan loai ban ghi nay. */
    public LocalDateTime getBusinessNow() {
        return businessNow != null ? businessNow : BusinessClock.now();
    }
    public List<OrderSeatItem> getSeats() { return seats; }
    public List<OrderComboItem> getCombos() { return combos; }
    public String getStartTimeDisplay() { return startTime == null ? "" : startTime.format(DISPLAY_DATE_TIME); }
    public String getEndTimeDisplay() {
        LocalDateTime et = getEndTime();
        return et == null ? "" : et.format(DISPLAY_DATE_TIME);
    }

    /**
     * Tong tien dang "245.000" (nhom hang nghin bang dau cham theo cach viet Viet Nam).
     *
     * <p>Dinh dang o phia server de man hinh quay ve luon co so tien on dinh cho nhan vien
     * thu tien mat. Cac JSP khac dung chung ham EL {@code cbf:whole}/{@code cbf:decimal}
     * thay cho formatter JSTL khong hoat dong trong runtime nay.</p>
     *
     * <p>Tao {@link DecimalFormat} mo^i lan goi vi lop nay khong an toan da luong,
     * ma servlet thi chay da luong.</p>
     */
    public String getTotalAmountDisplay() {
        if (totalAmount == null) {
            return "0";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", symbols).format(totalAmount);
    }
    public String getCreatedAtDisplay() { return createdAt == null ? "" : createdAt.format(DISPLAY_DATE_TIME); }
    public String getSeatSummary() {
        if (seats.isEmpty()) {
            return "N/A";
        }
        return seats.stream().map(OrderSeatItem::getSeatKey).collect(Collectors.joining(", "));
    }
    public String getSeatsDisplay() { return getSeatSummary(); }
    public String getComboSummary() {
        if (combos.isEmpty()) {
            return "";
        }
        return combos.stream()
                .map(item -> item.getComboName() + " x" + item.getQuantity())
                .collect(Collectors.joining(", "));
    }
    public String getCombosDisplay() { return getComboSummary(); }

    public boolean isCurrentTicket() {
        if ("cancelled".equalsIgnoreCase(orderStatus) || "redeemed".equalsIgnoreCase(orderStatus)) {
            return false;
        }
        LocalDateTime et = getEndTime();
        if (et == null) {
            return false;
        }
        return !getBusinessNow().isAfter(et);
    }

    public boolean isUsedOrExpired() {
        return !isCurrentTicket();
    }

    public boolean isPendingCheckIn() {
        return !"redeemed".equalsIgnoreCase(orderStatus) && !"cancelled".equalsIgnoreCase(orderStatus);
    }

    /** Mirrors the authoritative AdminService cancellation policy for button visibility. */
    public boolean isAdminCancellable() {
        boolean unpaidDraft = "pending".equalsIgnoreCase(paymentStatus)
                && ("created".equalsIgnoreCase(orderStatus)
                || "pending".equalsIgnoreCase(orderStatus));
        boolean pendingCounter = "counter".equalsIgnoreCase(paymentMethod)
                && "pending".equalsIgnoreCase(paymentStatus)
                && "confirmed".equalsIgnoreCase(orderStatus);
        return unpaidDraft || pendingCounter;
    }

    /** UI hint only; the write path rechecks the deadline against GETDATE() under a row lock. */
    public boolean isCounterPaymentCollectable() {
        return "counter".equalsIgnoreCase(paymentMethod)
                && "pending".equalsIgnoreCase(paymentStatus)
                && "confirmed".equalsIgnoreCase(orderStatus)
                && counterExpiresAt != null
                && getBusinessNow().isBefore(counterExpiresAt);
    }

    public boolean isPendingCounterPayment() {
        return "counter".equalsIgnoreCase(paymentMethod)
                && "pending".equalsIgnoreCase(paymentStatus)
                && "confirmed".equalsIgnoreCase(orderStatus);
    }

    public boolean isPaidConfirmed() {
        return "paid".equalsIgnoreCase(paymentStatus)
                && "confirmed".equalsIgnoreCase(orderStatus);
    }

    /** 1. Ve doi check-in: Dung gio (chua den StartTime hoặc dang trong gio khoi dau) */
    public boolean isPendingOnTimeCheckIn() {
        if (!isPaidConfirmed()) return false;
        if (startTime == null) return true;
        return !getBusinessNow().isAfter(startTime);
    }

    /** 2. Ve qua gio check-in (Phim đang chiếu: StartTime < now <= EndTime) */
    public boolean isLateCheckIn() {
        if (!isPaidConfirmed()) return false;
        if (startTime == null) return false;
        LocalDateTime now = getBusinessNow();
        LocalDateTime et = getEndTime();
        return now.isAfter(startTime) && (et == null || !now.isAfter(et));
    }

    /**
     * 3. Ve xem xet hoan tien (Đã qua giờ EndTime, chua check-in, đã trả tiền, chưa hoàn tiền).
     *
     * <p>BUG-10: don da bi quan ly tu choi hoan tien roi thi khong con "cho xem xet" nua. Truoc
     * day khong co dau vet nao cua quyet dinh tu choi nen don nam lai trong tab nay vinh vien.</p>
     */
    public boolean isRefundReview() {
        if (!isPaidConfirmed() || isRefunded()) return false;
        if (refundRejectedAt != null) return false;
        LocalDateTime et = getEndTime();
        if (et == null) return false;
        return getBusinessNow().isAfter(et);
    }

    public boolean isRefunded() {
        return refundedAt != null || "refunded".equalsIgnoreCase(paymentStatus);
    }

    public boolean isRefundRejected() {
        return refundRejectedAt != null;
    }

    public boolean isRedeemed() {
        return "redeemed".equalsIgnoreCase(orderStatus);
    }

    public boolean isCancelled() {
        return "cancelled".equalsIgnoreCase(orderStatus);
    }

    public String getStatusLabel() {
        if (isRefunded()) {
            return "Đã hoàn tiền";
        }
        if ("cancelled".equalsIgnoreCase(orderStatus)) {
            return "Đã hủy";
        }
        if ("redeemed".equalsIgnoreCase(orderStatus)) {
            return "Đã check-in";
        }
        if (isRefundRejected()) {
            return "Yêu cầu hoàn tiền đã bị từ chối";
        }
        if (pendingRefundAppeal) {
            return "Đang chờ xét hoàn tiền";
        }
        LocalDateTime now = getBusinessNow();
        LocalDateTime et = getEndTime();
        if (startTime != null && now.isAfter(startTime) && (et == null || !now.isAfter(et))) {
            return "⏰ Đang chiếu (Quá giờ check-in)";
        }
        if (et != null && now.isAfter(et)) {
            return "⚠️ Bỏ lỡ suất chiếu (Chờ xem xét hoàn tiền)";
        }
        if ("paid".equalsIgnoreCase(paymentStatus)) {
            return "Vé hợp lệ (Chờ xem)";
        }
        return "Chờ thanh toán";
    }

    public String getStatusBadgeClass() {
        if (isRefunded()) {
            return "badge-status-refunded";
        }
        if ("cancelled".equalsIgnoreCase(orderStatus)) {
            return "badge-status-cancelled";
        }
        if ("redeemed".equalsIgnoreCase(orderStatus)) {
            return "badge-status-redeemed";
        }
        LocalDateTime now = getBusinessNow();
        LocalDateTime et = getEndTime();
        if (startTime != null && now.isAfter(startTime) && (et == null || !now.isAfter(et))) {
            return "badge-status-warning";
        }
        if (et != null && now.isAfter(et)) {
            return "badge-status-expired";
        }
        if ("paid".equalsIgnoreCase(paymentStatus)) {
            return "badge-status-valid";
        }
        return "badge-status-pending";
    }

    public String getFormattedTotalAmount() {
        if (totalAmount == null) {
            return "0";
        }
        return String.format("%,.0f", totalAmount);
    }
}

package com.mycompany.website.ban.ve.xem.phim.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class UserAppeal {
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private int id;
    private int userId;
    private String email;
    private String userFullName;
    private String userRole;
    private String lockReason;
    private int warningCount;
    private String reason;
    private String ticketCode;
    private String bankAccountInfo;
    private String appealType;
    private String status = "pending"; // pending, approved, rejected
    private String adminResponse;
    private Integer resolvedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private Integer orderId;
    private Integer cinemaId;
    private String cinemaName;
    private String filmTitle;
    private BigDecimal orderTotalAmount;
    private String orderPaymentStatus;
    private String orderStatus;
    private LocalDateTime showtimeStartTime;
    private LocalDateTime showtimeEndTime;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUserFullName() { return userFullName; }
    public void setUserFullName(String userFullName) { this.userFullName = userFullName; }
    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }
    public String getLockReason() { return lockReason; }
    public void setLockReason(String lockReason) { this.lockReason = lockReason; }
    public int getWarningCount() { return warningCount; }
    public void setWarningCount(int warningCount) { this.warningCount = warningCount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }
    public String getBankAccountInfo() { return bankAccountInfo; }
    public void setBankAccountInfo(String bankAccountInfo) { this.bankAccountInfo = bankAccountInfo; }
    public String getAppealType() { return appealType; }
    public void setAppealType(String appealType) { this.appealType = appealType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAdminResponse() { return adminResponse; }
    public void setAdminResponse(String adminResponse) { this.adminResponse = adminResponse; }
    public Integer getResolvedByUserId() { return resolvedByUserId; }
    public void setResolvedByUserId(Integer resolvedByUserId) { this.resolvedByUserId = resolvedByUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }
    public Integer getCinemaId() { return cinemaId; }
    public void setCinemaId(Integer cinemaId) { this.cinemaId = cinemaId; }
    public String getCinemaName() { return cinemaName; }
    public void setCinemaName(String cinemaName) { this.cinemaName = cinemaName; }
    public String getFilmTitle() { return filmTitle; }
    public void setFilmTitle(String filmTitle) { this.filmTitle = filmTitle; }
    public BigDecimal getOrderTotalAmount() { return orderTotalAmount; }
    public void setOrderTotalAmount(BigDecimal orderTotalAmount) { this.orderTotalAmount = orderTotalAmount; }
    public String getOrderPaymentStatus() { return orderPaymentStatus; }
    public void setOrderPaymentStatus(String orderPaymentStatus) { this.orderPaymentStatus = orderPaymentStatus; }
    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
    public LocalDateTime getShowtimeStartTime() { return showtimeStartTime; }
    public void setShowtimeStartTime(LocalDateTime showtimeStartTime) { this.showtimeStartTime = showtimeStartTime; }
    public LocalDateTime getShowtimeEndTime() { return showtimeEndTime; }
    public void setShowtimeEndTime(LocalDateTime showtimeEndTime) { this.showtimeEndTime = showtimeEndTime; }
    public boolean isRefundAppeal() {
        return "refund".equalsIgnoreCase(appealType)
                || (appealType == null && ticketCode != null && !ticketCode.isBlank());
    }
    public boolean isAccountAppeal() { return !isRefundAppeal(); }
    public String getAppealTypeLabel() { return isRefundAppeal() ? "Hoàn tiền vé" : "Mở khóa tài khoản"; }
    public String getOrderTotalAmountDisplay() {
        return orderTotalAmount == null ? "" : String.format(java.util.Locale.US, "%,.0f", orderTotalAmount)
                .replace(',', '.');
    }
    public String getShowtimeStartDisplay() {
        return showtimeStartTime == null ? "" : showtimeStartTime.format(DISPLAY_DATE_TIME);
    }
    public String getShowtimeEndDisplay() {
        return showtimeEndTime == null ? "" : showtimeEndTime.format(DISPLAY_DATE_TIME);
    }
    public String getCreatedAtDisplay() { return createdAt == null ? "" : createdAt.format(DISPLAY_DATE_TIME); }
    public String getResolvedAtDisplay() { return resolvedAt == null ? "" : resolvedAt.format(DISPLAY_DATE_TIME); }
}

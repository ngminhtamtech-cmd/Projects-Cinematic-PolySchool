package com.mycompany.website.ban.ve.xem.phim.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Thong bao do manager/admin soan gui toi nguoi dung (FLOW-NOTIFY-USER-001).
 *
 * <p>Khac {@link AdminNotification} — von la canh bao van hanh do he thong tu sinh cho nguoi
 * quan tri. Thong bao o day co tac gia, co lich hien thi va co danh sach nguoi nhan tuong minh.</p>
 */
public class UserNotification {
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Pham vi nguoi nhan hop le. */
    public static final String TARGET_ALL = "ALL";
    public static final String TARGET_CINEMA = "CINEMA";
    public static final String TARGET_TIER = "TIER";
    public static final String TARGET_USER = "USER";

    private int id;
    private String title;
    private String message;
    private String severity = "info";
    private String targetType = TARGET_ALL;
    private String targetId;
    private Integer cinemaId;
    private String actionUrl;
    private LocalDateTime visibleFrom;
    private LocalDateTime visibleUntil;
    private String status = "active";
    private int createdByUserId;
    private LocalDateTime createdAt;
    /** Chi co gia tri khi doc hop thu cua mot nguoi dung cu the. */
    private LocalDateTime readAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public Integer getCinemaId() {
        return cinemaId;
    }

    public void setCinemaId(Integer cinemaId) {
        this.cinemaId = cinemaId;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    public LocalDateTime getVisibleFrom() {
        return visibleFrom;
    }

    public void setVisibleFrom(LocalDateTime visibleFrom) {
        this.visibleFrom = visibleFrom;
    }

    public LocalDateTime getVisibleUntil() {
        return visibleUntil;
    }

    public void setVisibleUntil(LocalDateTime visibleUntil) {
        this.visibleUntil = visibleUntil;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(int createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public String getCreatedAtDisplay() {
        return createdAt == null ? "" : createdAt.format(DISPLAY_DATE_TIME);
    }
}

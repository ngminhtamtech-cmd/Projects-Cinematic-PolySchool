package com.mycompany.website.ban.ve.xem.phim.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AdminNotification {
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private int id;
    private String title;
    private String message;
    private String category = "room"; // room, showtime, system, booking
    private String severity = "info"; // info, warning, success, danger
    private String targetType;
    private String targetId;
    private String actionUrl;
    private Integer cinemaId;
    private Integer createdByUserId;
    private String eventType;
    private Integer approvalRequestId;
    private boolean isRead;
    private LocalDateTime resolvedAt;
    private Integer resolvedBy;
    private String resolution;
    private LocalDateTime createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getActionUrl() { return actionUrl; }
    public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }
    public Integer getCinemaId() { return cinemaId; }
    public void setCinemaId(Integer cinemaId) { this.cinemaId = cinemaId; }
    public Integer getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Integer createdByUserId) { this.createdByUserId = createdByUserId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Integer getApprovalRequestId() { return approvalRequestId; }
    public void setApprovalRequestId(Integer approvalRequestId) { this.approvalRequestId = approvalRequestId; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public Integer getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Integer resolvedBy) { this.resolvedBy = resolvedBy; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedAtDisplay() { return createdAt == null ? "" : createdAt.format(DISPLAY_DATE_TIME); }
}

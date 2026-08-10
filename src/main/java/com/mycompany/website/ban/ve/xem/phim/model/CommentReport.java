package com.mycompany.website.ban.ve.xem.phim.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CommentReport {
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private int id;
    private int commentId;
    private Integer reporterUserId;
    private String reporterFullName;
    private String reason;
    private String status = "pending"; // pending, warned, locked, dismissed
    private LocalDateTime createdAt;
    
    // Additional fields for displaying
    private int commentUserId;
    private String commentUserFullName;
    private String commentUserEmail;
    private int commentUserWarningCount;
    private boolean commentUserIsLocked;
    private String commentContent;
    private String filmTitle;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getCommentId() { return commentId; }
    public void setCommentId(int commentId) { this.commentId = commentId; }
    public Integer getReporterUserId() { return reporterUserId; }
    public void setReporterUserId(Integer reporterUserId) { this.reporterUserId = reporterUserId; }
    public String getReporterFullName() { return reporterFullName; }
    public void setReporterFullName(String reporterFullName) { this.reporterFullName = reporterFullName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedAtDisplay() { return createdAt == null ? "" : createdAt.format(DISPLAY_DATE_TIME); }

    public int getCommentUserId() { return commentUserId; }
    public void setCommentUserId(int commentUserId) { this.commentUserId = commentUserId; }
    public String getCommentUserFullName() { return commentUserFullName; }
    public void setCommentUserFullName(String commentUserFullName) { this.commentUserFullName = commentUserFullName; }
    public String getCommentUserEmail() { return commentUserEmail; }
    public void setCommentUserEmail(String commentUserEmail) { this.commentUserEmail = commentUserEmail; }
    public int getCommentUserWarningCount() { return commentUserWarningCount; }
    public void setCommentUserWarningCount(int commentUserWarningCount) { this.commentUserWarningCount = commentUserWarningCount; }
    public boolean isCommentUserIsLocked() { return commentUserIsLocked; }
    public void setCommentUserIsLocked(boolean commentUserIsLocked) { this.commentUserIsLocked = commentUserIsLocked; }
    public String getCommentContent() { return commentContent; }
    public void setCommentContent(String commentContent) { this.commentContent = commentContent; }
    public String getFilmTitle() { return filmTitle; }
    public void setFilmTitle(String filmTitle) { this.filmTitle = filmTitle; }
}

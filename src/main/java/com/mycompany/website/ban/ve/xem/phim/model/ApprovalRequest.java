package com.mycompany.website.ban.ve.xem.phim.model;

import java.time.LocalDateTime;

/** Read model for the shared manager/admin approval queue. */
public class ApprovalRequest {
    private int id;
    private String requestType;
    private int cinemaId;
    private String cinemaName;
    private int requestedByUserId;
    private String requestedByName;
    private String requestKey;
    private String status;
    private LocalDateTime requestedAt;
    private Integer reviewedByUserId;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private String resolvedEntityType;
    private Integer resolvedEntityId;
    private Integer existingFilmId;
    private String subjectName;
    private String roomType;
    private Integer layoutRows;
    private Integer seatsPerRow;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public int getCinemaId() { return cinemaId; }
    public void setCinemaId(int cinemaId) { this.cinemaId = cinemaId; }
    public String getCinemaName() { return cinemaName; }
    public void setCinemaName(String cinemaName) { this.cinemaName = cinemaName; }
    public int getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(int requestedByUserId) { this.requestedByUserId = requestedByUserId; }
    public String getRequestedByName() { return requestedByName; }
    public void setRequestedByName(String requestedByName) { this.requestedByName = requestedByName; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
    public Integer getReviewedByUserId() { return reviewedByUserId; }
    public void setReviewedByUserId(Integer reviewedByUserId) { this.reviewedByUserId = reviewedByUserId; }
    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String reviewedByName) { this.reviewedByName = reviewedByName; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public String getResolvedEntityType() { return resolvedEntityType; }
    public void setResolvedEntityType(String resolvedEntityType) { this.resolvedEntityType = resolvedEntityType; }
    public Integer getResolvedEntityId() { return resolvedEntityId; }
    public void setResolvedEntityId(Integer resolvedEntityId) { this.resolvedEntityId = resolvedEntityId; }
    public Integer getExistingFilmId() { return existingFilmId; }
    public void setExistingFilmId(Integer existingFilmId) { this.existingFilmId = existingFilmId; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
    public Integer getLayoutRows() { return layoutRows; }
    public void setLayoutRows(Integer layoutRows) { this.layoutRows = layoutRows; }
    public Integer getSeatsPerRow() { return seatsPerRow; }
    public void setSeatsPerRow(Integer seatsPerRow) { this.seatsPerRow = seatsPerRow; }

    public boolean isPending() { return "PENDING".equalsIgnoreCase(status); }

    public String getTypeDisplay() {
        if (requestType == null) return "Yêu cầu";
        return switch (requestType) {
            case "FILM_ASSIGN" -> "Gán phim";
            case "FILM_CREATE" -> "Phim mới";
            case "FILM_UPDATE" -> "Cập nhật phim";
            case "FILM_UNASSIGN" -> "Gỡ phim";
            case "ROOM_CREATE" -> "Phòng mới";
            default -> requestType;
        };
    }
}

package com.mycompany.website.ban.ve.xem.phim.model;

import java.time.LocalDateTime;

public class Room {
    private int id;
    private int cinemaId;
    private String cinemaName;
    private String name;
    private int seatCount;
    private String status = "active";
    private String roomType = "STANDARD";
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getCinemaId() { return cinemaId; }
    public void setCinemaId(int cinemaId) { this.cinemaId = cinemaId; }
    public String getCinemaName() { return cinemaName; }
    public void setCinemaName(String cinemaName) { this.cinemaName = cinemaName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getSeatCount() { return seatCount; }
    public void setSeatCount(int seatCount) { this.seatCount = seatCount; }
    public String getStatus() { return status == null ? "active" : status; }
    public void setStatus(String status) { this.status = status; }
    public String getRoomType() { return roomType == null ? "STANDARD" : roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
    public boolean isActive() { return "active".equalsIgnoreCase(status); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

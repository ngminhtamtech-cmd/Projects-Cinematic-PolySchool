package com.mycompany.website.ban.ve.xem.phim.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Showtime {
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private int id;
    private int filmId;
    private int cinemaId;
    private int cityId;
    private int roomId;
    private String filmTitle;
    private String cinemaName;
    private String roomName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal basePrice;
    private String thumbnail;
    private String ageRating;
    private String format;
    private String version;
    private String language;
    private String roomStatus = "active";
    private int totalSeats;
    private int availableSeats = -1; // -1 means not loaded
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String saleStatus = "ON_SALE";
    private LocalDateTime deleteRequestedAt;
    private LocalDateTime deleteNotBefore;
    private Integer deleteRequestedByUserId;

    public int getTotalSeats() { return totalSeats; }
    public void setTotalSeats(int totalSeats) { this.totalSeats = totalSeats; }
    public int getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(int availableSeats) { this.availableSeats = availableSeats; }
    public boolean isSoldOut() { return availableSeats == 0; }
    public String getSeatStatusLabel() {
        if (availableSeats < 0) return "";
        if (availableSeats == 0) return "Hết ghế";
        return "Còn " + availableSeats + (totalSeats > 0 ? "/" + totalSeats : "") + " ghế";
    }


    public String getRoomStatus() { return roomStatus == null || roomStatus.isBlank() ? "active" : roomStatus; }
    public void setRoomStatus(String roomStatus) { this.roomStatus = roomStatus; }
    public boolean isRoomActive() { return !"inactive".equalsIgnoreCase(getRoomStatus()); }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getFilmId() { return filmId; }
    public void setFilmId(int filmId) { this.filmId = filmId; }
    public int getCinemaId() { return cinemaId; }
    public void setCinemaId(int cinemaId) { this.cinemaId = cinemaId; }
    public int getCityId() { return cityId; }
    public void setCityId(int cityId) { this.cityId = cityId; }

    public int getRoomId() { return roomId; }
    public void setRoomId(int roomId) { this.roomId = roomId; }
    public String getFilmTitle() { return filmTitle; }
    public void setFilmTitle(String filmTitle) { this.filmTitle = filmTitle; }
    public String getCinemaName() { return cinemaName; }
    public void setCinemaName(String cinemaName) { this.cinemaName = cinemaName; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }
    public String getAgeRating() { return ageRating; }
    public void setAgeRating(String ageRating) { this.ageRating = ageRating; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getSaleStatus() { return saleStatus == null || saleStatus.isBlank() ? "ON_SALE" : saleStatus; }
    public void setSaleStatus(String saleStatus) { this.saleStatus = saleStatus; }
    public LocalDateTime getDeleteRequestedAt() { return deleteRequestedAt; }
    public void setDeleteRequestedAt(LocalDateTime deleteRequestedAt) { this.deleteRequestedAt = deleteRequestedAt; }
    public LocalDateTime getDeleteNotBefore() { return deleteNotBefore; }
    public void setDeleteNotBefore(LocalDateTime deleteNotBefore) { this.deleteNotBefore = deleteNotBefore; }
    public Integer getDeleteRequestedByUserId() { return deleteRequestedByUserId; }
    public void setDeleteRequestedByUserId(Integer value) { this.deleteRequestedByUserId = value; }
    public boolean isOnSale() { return "ON_SALE".equalsIgnoreCase(getSaleStatus()); }
    public boolean isSuspended() { return "SUSPENDED".equalsIgnoreCase(getSaleStatus()); }
    public boolean isDeleted() { return "DELETED".equalsIgnoreCase(getSaleStatus()); }
    public String getStartTimeDisplay() { return startTime == null ? "" : startTime.format(DISPLAY_DATE_TIME); }
    public String getEndTimeDisplay() { return endTime == null ? "" : endTime.format(DISPLAY_DATE_TIME); }
    public String getDateDisplay() { return startTime == null ? "" : startTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); }
    public String getTimeDisplay() { return startTime == null ? "" : startTime.format(DateTimeFormatter.ofPattern("HH:mm")); }
    public String getDayOfWeekDisplay() {
        if (startTime == null) return "";
        switch (startTime.getDayOfWeek()) {
            case MONDAY: return "Thứ Hai";
            case TUESDAY: return "Thứ Ba";
            case WEDNESDAY: return "Thứ Tư";
            case THURSDAY: return "Thứ Năm";
            case FRIDAY: return "Thứ Sáu";
            case SATURDAY: return "Thứ Bảy";
            case SUNDAY: return "Chủ Nhật";
            default: return "";
        }
    }

    public String getFormat() { return format == null || format.isBlank() ? "2D" : format; }
    public void setFormat(String format) { this.format = format; }

    public String getVersion() { return version == null || version.isBlank() ? "Phụ đề" : version; }
    public void setVersion(String version) { this.version = version; }

    public String getLanguage() { return language == null || language.isBlank() ? "Tiếng Việt" : language; }
    public void setLanguage(String language) { this.language = language; }

    public String getFormatVersionDisplay() {
        String fmt = getFormat();
        String ver = getVersion();
        return fmt + " " + ver;
    }

    public String getRoomFormatLabel() {
        String room = getRoomName();
        if (room == null || room.isBlank()) {
            room = "Phòng chiếu";
        }
        String fmt = getFormat();
        String ver = getVersion();
        return room + " (" + fmt + " " + ver + ")";
    }
}

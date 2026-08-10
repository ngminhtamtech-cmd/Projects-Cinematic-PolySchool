package com.mycompany.website.ban.ve.xem.phim.model;

import com.mycompany.website.ban.ve.xem.phim.service.FilmAvailabilityPolicy;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Film {
    private int id;
    private String title;
    private String otherTitles;
    private String actors;
    private String directors;
    private Double rating;
    private LocalDate releaseDate;
    /** Ngay chieu cuoi cung, bao gom ca ngay do; admin commands bat buoc gia tri nay. */
    private LocalDate endDate;
    private Integer durationMinutes;
    private String ageRating;
    private String trailerUrl;
    private String thumbnail;
    private String language;
    private String subtitles;
    private String description;
    private String country;
    private String format;
    private String status;
    private String banner;
    private String categories;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Integer deletedByUserId;
    private String deletionMode;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOtherTitles() { return otherTitles; }
    public void setOtherTitles(String otherTitles) { this.otherTitles = otherTitles; }
    public String getActors() { return actors; }
    public void setActors(String actors) { this.actors = actors; }
    public String getDirectors() { return directors; }
    public void setDirectors(String directors) { this.directors = directors; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getAgeRating() { return ageRating; }
    public void setAgeRating(String ageRating) { this.ageRating = ageRating; }
    public String getTrailerUrl() { return trailerUrl; }
    public void setTrailerUrl(String trailerUrl) { this.trailerUrl = trailerUrl; }
    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getSubtitles() { return subtitles; }
    public void setSubtitles(String subtitles) { this.subtitles = subtitles; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    /**
     * Trang thai hien thi, suy ra tu vong doi thuc te chu khong doc thang cot {@code Status}.
     *
     * <p>Ban cu so {@code ReleaseDate} voi {@code LocalDate.now()} cua JVM va khong biet toi
     * {@code EndDate}, nen phim da het chieu van bao "showing". Gio uy quyen cho
     * {@link FilmAvailabilityPolicy} — cung mot quy tac voi header, API va trang public.</p>
     */
    public String getStatus() {
        return switch (FilmAvailabilityPolicy.evaluate(this)) {
            case WITHDRAWN -> "ended";
            case COMING -> "coming";
            case EXPIRED -> "expired";
            case EXPIRING_SOON, SHOWING -> "showing";
        };
    }

    /** Gia tri tho trong cot {@code Status} — dung cho form quan tri va cho chinh policy. */
    public String getRawStatus() { return status; }

    // --- Trang thai vong doi cho JSP (EX-01) ---------------------------------------------
    // JSP khong goi duoc static method, nen bo cac getter mong nay de dung ${film.expiringSoon}.

    /** Con 0..N ngay la het chieu; van dat ve duoc, chi hien badge nhac. */
    public boolean isExpiringSoon() {
        return FilmAvailabilityPolicy.evaluate(this) == FilmAvailabilityPolicy.Availability.EXPIRING_SOON;
    }

    /** Da qua {@code EndDate} — an khoi public, admin/manager van thay. */
    public boolean isExpired() {
        return FilmAvailabilityPolicy.evaluate(this) == FilmAvailabilityPolicy.Availability.EXPIRED;
    }

    /** Con trong thoi gian chieu va dang mo ban ve. */
    public boolean isBookable() {
        return FilmAvailabilityPolicy.isBookable(this);
    }

    /** So ngay con lai toi ngay chieu cuoi. */
    public Long getDaysUntilEnd() {
        return FilmAvailabilityPolicy.daysUntilEnd(this);
    }
    public void setStatus(String status) { this.status = status; }
    public String getBanner() { return banner; }
    public void setBanner(String banner) { this.banner = banner; }
    public String getCategories() { return categories; }
    public void setCategories(String categories) { this.categories = categories; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public Integer getDeletedByUserId() { return deletedByUserId; }
    public void setDeletedByUserId(Integer deletedByUserId) { this.deletedByUserId = deletedByUserId; }
    public String getDeletionMode() { return deletionMode; }
    public void setDeletionMode(String deletionMode) { this.deletionMode = deletionMode; }
    public boolean isDeleted() { return deletedAt != null; }

    // Aliases for JSP compatibility
    public Integer getDuration() { return durationMinutes != null ? durationMinutes : 120; }
    public void setDuration(Integer duration) { this.durationMinutes = duration; }
    public String getGenre() { return categories != null ? categories : ""; }
    public void setGenre(String genre) { this.categories = genre; }
    public String getCast() { return actors != null ? actors : ""; }
    public void setCast(String cast) { this.actors = cast; }
    public String getDirector() { return directors != null ? directors : ""; }
    public void setDirector(String director) { this.directors = director; }
}

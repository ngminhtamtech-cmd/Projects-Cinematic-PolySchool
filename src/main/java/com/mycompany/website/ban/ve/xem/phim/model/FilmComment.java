package com.mycompany.website.ban.ve.xem.phim.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FilmComment {
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private int id;
    private int userId;
    private int filmId;
    private int rate;
    private String content;
    private boolean report;
    private LocalDateTime createdAt;
    private String filmTitle;
    private String userFullName;
    private String userEmail;
    private int userWarningCount;
    private boolean userIsLocked;
    private boolean filmDeleted;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public int getFilmId() { return filmId; }
    public void setFilmId(int filmId) { this.filmId = filmId; }
    public int getRate() { return rate; }
    public void setRate(int rate) { this.rate = rate; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isReport() { return report; }
    public void setReport(boolean report) { this.report = report; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getFilmTitle() { return filmTitle; }
    public void setFilmTitle(String filmTitle) { this.filmTitle = filmTitle; }
    public String getUserFullName() { return userFullName; }
    public void setUserFullName(String userFullName) { this.userFullName = userFullName; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public int getUserWarningCount() { return userWarningCount; }
    public void setUserWarningCount(int userWarningCount) { this.userWarningCount = userWarningCount; }
    public boolean isUserIsLocked() { return userIsLocked; }
    public void setUserIsLocked(boolean userIsLocked) { this.userIsLocked = userIsLocked; }
    public boolean isFilmDeleted() { return filmDeleted; }
    public void setFilmDeleted(boolean filmDeleted) { this.filmDeleted = filmDeleted; }
    public String getCreatedAtDisplay() { return createdAt == null ? "" : createdAt.format(DISPLAY_DATE_TIME); }
}

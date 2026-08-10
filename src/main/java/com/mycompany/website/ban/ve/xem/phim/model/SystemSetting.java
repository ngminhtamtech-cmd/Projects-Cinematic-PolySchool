package com.mycompany.website.ban.ve.xem.phim.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SystemSetting {
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private String settingKey;
    private String settingValue;
    private LocalDateTime updatedAt;

    public String getSettingKey() { return settingKey; }
    public void setSettingKey(String settingKey) { this.settingKey = settingKey; }
    public String getSettingValue() { return settingValue; }
    public void setSettingValue(String settingValue) { this.settingValue = settingValue; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedAtDisplay() { return updatedAt == null ? "" : updatedAt.format(DISPLAY_DATE_TIME); }
}

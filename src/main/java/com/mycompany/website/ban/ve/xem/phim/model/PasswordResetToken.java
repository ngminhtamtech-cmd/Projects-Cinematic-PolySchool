package com.mycompany.website.ban.ve.xem.phim.model;

import java.time.LocalDateTime;

/**
 * Mot phieu dat lai mat khau (D11).
 *
 * <p>Chi chua <b>bam SHA-256</b> cua token, khong bao gio chua token tho: doc duoc bang nay
 * cung khong dung no de chiem tai khoan duoc.</p>
 */
public class PasswordResetToken {
    private int id;
    private String tokenHash;
    private int userId;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
    private String requestIp;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getRequestIp() { return requestIp; }
    public void setRequestIp(String requestIp) { this.requestIp = requestIp; }

    public boolean isUsed() {
        return usedAt != null;
    }
}

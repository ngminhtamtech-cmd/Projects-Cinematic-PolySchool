package com.mycompany.website.ban.ve.xem.phim.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class User {
    private int id;
    private String username;
    private String fullName;
    private String email;
    private String passwordHash;
    private String phone;
    private String address;
    private String avatar;
    private String role;
    private boolean deleted;
    private int loyaltyPoints;
    private BigDecimal totalSpent = BigDecimal.ZERO;
    private String membershipTier = "BRONZE";
    private boolean isLocked;
    private String lockReason;
    private int warningCount;
    private Integer cinemaId;
    private String cinemaName;
    private LocalDateTime lockedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime emailVerifiedAt;

    public Integer getCinemaId() { return cinemaId; }
    public void setCinemaId(Integer cinemaId) { this.cinemaId = cinemaId; }
    public String getCinemaName() { return cinemaName; }
    public void setCinemaName(String cinemaName) { this.cinemaName = cinemaName; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public int getLoyaltyPoints() { return loyaltyPoints; }
    public void setLoyaltyPoints(int loyaltyPoints) { this.loyaltyPoints = loyaltyPoints; }
    public BigDecimal getTotalSpent() { return totalSpent; }
    public void setTotalSpent(BigDecimal totalSpent) { this.totalSpent = totalSpent; }
    public String getMembershipTier() { return membershipTier == null ? "BRONZE" : membershipTier; }
    public void setMembershipTier(String membershipTier) { this.membershipTier = membershipTier; }
    public boolean isLocked() { return isLocked; }
    public void setLocked(boolean locked) { isLocked = locked; }
    public String getLockReason() { return lockReason; }
    public void setLockReason(String lockReason) { this.lockReason = lockReason; }
    public int getWarningCount() { return warningCount; }
    public void setWarningCount(int warningCount) { this.warningCount = warningCount; }
    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getEmailVerifiedAt() { return emailVerifiedAt; }
    public void setEmailVerifiedAt(LocalDateTime emailVerifiedAt) { this.emailVerifiedAt = emailVerifiedAt; }
    public boolean isEmailVerified() { return emailVerifiedAt != null; }

    public String getTierDisplayName() {
        String tier = getMembershipTier().toUpperCase();
        return switch (tier) {
            case "SILVER" -> "Bạc";
            case "DIAMOND" -> "Kim Cương";
            case "EMERALD" -> "Lục Bảo";
            default -> "Đồng";
        };
    }

    public String getTierBadgeClass() {
        String tier = getMembershipTier().toUpperCase();
        return switch (tier) {
            case "SILVER" -> "silver-badge";
            case "DIAMOND" -> "diamond-badge";
            case "EMERALD" -> "emerald-badge";
            default -> "bronze-badge";
        };
    }
}

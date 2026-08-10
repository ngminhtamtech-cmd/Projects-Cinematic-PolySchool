package com.mycompany.website.ban.ve.xem.phim.model;

import java.time.LocalDateTime;

public class UserVoucher {
    private int id;
    private int userId;
    private int promotionId;
    private String code;
    private boolean isUsed;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
    private String promotionDescription;
    private Double discountPercent;
    private java.math.BigDecimal maxDiscount;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public int getPromotionId() { return promotionId; }
    public void setPromotionId(int promotionId) { this.promotionId = promotionId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public boolean isUsed() { return isUsed; }
    public void setUsed(boolean used) { isUsed = used; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getPromotionDescription() { return promotionDescription; }
    public void setPromotionDescription(String promotionDescription) { this.promotionDescription = promotionDescription; }
    public Double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Double discountPercent) { this.discountPercent = discountPercent; }
    public java.math.BigDecimal getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(java.math.BigDecimal maxDiscount) { this.maxDiscount = maxDiscount; }
}

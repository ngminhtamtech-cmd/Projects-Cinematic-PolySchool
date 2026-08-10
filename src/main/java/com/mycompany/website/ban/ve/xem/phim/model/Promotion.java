package com.mycompany.website.ban.ve.xem.phim.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Promotion {
    private int id;
    private String code;
    private String description;
    private Double discountPercent;
    private BigDecimal maxDiscount;
    private LocalDate startDate;
    private LocalDate endDate;
    private String conditionsJson;
    private Integer usageLimit;
    private int usedCount;
    private String status;
    private String voucherType = "PUBLIC";
    private String targetTier = "ALL";
    private int pointsRequired = 0;
    private int perUserLimit;
    private Integer userVoucherId;
    private Integer createdByUserId;
    private String createdByName;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Double discountPercent) { this.discountPercent = discountPercent; }
    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(BigDecimal maxDiscount) { this.maxDiscount = maxDiscount; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getConditionsJson() { return conditionsJson; }
    public void setConditionsJson(String conditionsJson) { this.conditionsJson = conditionsJson; }
    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }
    public int getUsedCount() { return usedCount; }
    public void setUsedCount(int usedCount) { this.usedCount = usedCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getVoucherType() { return voucherType == null ? "PUBLIC" : voucherType; }
    public void setVoucherType(String voucherType) { this.voucherType = voucherType; }
    public String getTargetTier() { return targetTier == null ? "ALL" : targetTier; }
    public void setTargetTier(String targetTier) { this.targetTier = targetTier; }
    public int getPointsRequired() { return pointsRequired; }
    public void setPointsRequired(int pointsRequired) { this.pointsRequired = pointsRequired; }
    public int getPerUserLimit() { return perUserLimit; }
    public void setPerUserLimit(int perUserLimit) { this.perUserLimit = perUserLimit; }
    public Integer getUserVoucherId() { return userVoucherId; }
    public void setUserVoucherId(Integer userVoucherId) { this.userVoucherId = userVoucherId; }
    public Integer getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Integer createdByUserId) { this.createdByUserId = createdByUserId; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public String getVoucherTypeDisplay() {
        String type = getVoucherType().toUpperCase();
        return switch (type) {
            case "TIER_RESTRICTED" -> "Theo Hạng Thành Viên";
            case "REDEEMABLE" -> "Đổi Bằng Điểm";
            default -> "Công Khai Dùng Chung";
        };
    }

    public String getTargetTierDisplay() {
        String tier = getTargetTier().toUpperCase();
        return switch (tier) {
            case "BRONZE" -> "Hạng Đồng";
            case "SILVER" -> "Hạng Bạc";
            case "DIAMOND" -> "Hạng Kim Cương";
            case "EMERALD" -> "Hạng Lục Bảo";
            default -> "Tất Cả Hạng";
        };
    }
}

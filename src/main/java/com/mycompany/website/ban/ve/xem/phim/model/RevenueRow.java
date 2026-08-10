package com.mycompany.website.ban.ve.xem.phim.model;

import java.math.BigDecimal;

public class RevenueRow {
    private String label;
    private int orderCount;
    private BigDecimal totalRevenue;

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getOrderCount() { return orderCount; }
    public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

    public String getFormattedTotalRevenue() {
        if (totalRevenue == null) return "0 đ";
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance(java.util.Locale.forLanguageTag("vi-VN"));
        return nf.format(totalRevenue) + " đ";
    }
}

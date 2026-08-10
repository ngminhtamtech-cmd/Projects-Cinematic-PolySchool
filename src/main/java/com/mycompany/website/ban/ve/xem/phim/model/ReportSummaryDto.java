package com.mycompany.website.ban.ve.xem.phim.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ReportSummaryDto {
    private String currentMonthLabel;
    private String prevMonthLabel;

    // Stat cards
    private BigDecimal totalRevenueCurrent = BigDecimal.ZERO;
    private BigDecimal totalRevenuePrev = BigDecimal.ZERO;
    private double totalRevenueDiffPercent;

    // Ve trung binh/ngay giu 1 chu so thap phan. Truoc day la int va duoc tinh bang phep chia
    // nguyen, nen moi thang ban duoc it ve hon so ngay trong thang deu hien "0 ve" (23/31 -> 0).
    private BigDecimal avgTicketsPerDayCurrent = BigDecimal.ZERO;
    private BigDecimal avgTicketsPerDayPrev = BigDecimal.ZERO;
    private double avgTicketsPerDayDiffPercent;

    private double cancelRateCurrent;
    private double cancelRatePrev;
    private double cancelRateDiffPoint;

    // Detailed metrics rows
    private List<MetricRow> metrics = new ArrayList<>();

    public static class MetricRow {
        private String name;
        private String currentValue;
        private String prevValue;
        private String diffText;
        private boolean trendUp;
        private boolean isPointDiff;

        public MetricRow(String name, String currentValue, String prevValue, String diffText, boolean trendUp, boolean isPointDiff) {
            this.name = name;
            this.currentValue = currentValue;
            this.prevValue = prevValue;
            this.diffText = diffText;
            this.trendUp = trendUp;
            this.isPointDiff = isPointDiff;
        }

        public String getName() { return name; }
        public String getCurrentValue() { return currentValue; }
        public String getPrevValue() { return prevValue; }
        public String getDiffText() { return diffText; }
        public boolean isTrendUp() { return trendUp; }
        public boolean isPointDiff() { return isPointDiff; }
    }

    public String getCurrentMonthLabel() { return currentMonthLabel; }
    public void setCurrentMonthLabel(String currentMonthLabel) { this.currentMonthLabel = currentMonthLabel; }

    public String getPrevMonthLabel() { return prevMonthLabel; }
    public void setPrevMonthLabel(String prevMonthLabel) { this.prevMonthLabel = prevMonthLabel; }

    public BigDecimal getTotalRevenueCurrent() { return totalRevenueCurrent; }
    public void setTotalRevenueCurrent(BigDecimal totalRevenueCurrent) { this.totalRevenueCurrent = totalRevenueCurrent; }

    public BigDecimal getTotalRevenuePrev() { return totalRevenuePrev; }
    public void setTotalRevenuePrev(BigDecimal totalRevenuePrev) { this.totalRevenuePrev = totalRevenuePrev; }

    public double getTotalRevenueDiffPercent() { return totalRevenueDiffPercent; }
    public void setTotalRevenueDiffPercent(double totalRevenueDiffPercent) { this.totalRevenueDiffPercent = totalRevenueDiffPercent; }

    public String getFormattedTotalRevenueCurrent() {
        if (totalRevenueCurrent == null) return "0 đ";
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance(java.util.Locale.forLanguageTag("vi-VN"));
        return nf.format(totalRevenueCurrent) + " đ";
    }

    public String getFormattedTotalRevenueDiffPercent() {
        if (totalRevenueDiffPercent > 0) return String.format(java.util.Locale.US, "+%.1f%%", totalRevenueDiffPercent);
        if (totalRevenueDiffPercent < 0) return String.format(java.util.Locale.US, "%.1f%%", totalRevenueDiffPercent);
        return "0,0%";
    }

    public String getFormattedAvgTicketsDiffPercent() {
        if (avgTicketsPerDayDiffPercent > 0) return String.format(java.util.Locale.US, "+%.1f%%", avgTicketsPerDayDiffPercent);
        if (avgTicketsPerDayDiffPercent < 0) return String.format(java.util.Locale.US, "%.1f%%", avgTicketsPerDayDiffPercent);
        return "0,0%";
    }

    public String getFormattedCancelRateCurrent() {
        return String.format(java.util.Locale.US, "%.1f%%", cancelRateCurrent);
    }

    public String getFormattedCancelRateDiffPoint() {
        if (cancelRateDiffPoint > 0) return String.format(java.util.Locale.US, "+%.1f điểm", cancelRateDiffPoint);
        if (cancelRateDiffPoint < 0) return String.format(java.util.Locale.US, "%.1f điểm", cancelRateDiffPoint);
        return "0,0 điểm";
    }

    public BigDecimal getAvgTicketsPerDayCurrent() { return avgTicketsPerDayCurrent; }
    public void setAvgTicketsPerDayCurrent(BigDecimal avgTicketsPerDayCurrent) { this.avgTicketsPerDayCurrent = avgTicketsPerDayCurrent; }

    public BigDecimal getAvgTicketsPerDayPrev() { return avgTicketsPerDayPrev; }
    public void setAvgTicketsPerDayPrev(BigDecimal avgTicketsPerDayPrev) { this.avgTicketsPerDayPrev = avgTicketsPerDayPrev; }

    public double getAvgTicketsPerDayDiffPercent() { return avgTicketsPerDayDiffPercent; }
    public void setAvgTicketsPerDayDiffPercent(double avgTicketsPerDayDiffPercent) { this.avgTicketsPerDayDiffPercent = avgTicketsPerDayDiffPercent; }

    public double getCancelRateCurrent() { return cancelRateCurrent; }
    public void setCancelRateCurrent(double cancelRateCurrent) { this.cancelRateCurrent = cancelRateCurrent; }

    public double getCancelRatePrev() { return cancelRatePrev; }
    public void setCancelRatePrev(double cancelRatePrev) { this.cancelRatePrev = cancelRatePrev; }

    public double getCancelRateDiffPoint() { return cancelRateDiffPoint; }
    public void setCancelRateDiffPoint(double cancelRateDiffPoint) { this.cancelRateDiffPoint = cancelRateDiffPoint; }

    public List<MetricRow> getMetrics() { return metrics; }
    public void setMetrics(List<MetricRow> metrics) { this.metrics = metrics; }
}

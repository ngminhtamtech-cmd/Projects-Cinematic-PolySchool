package com.mycompany.website.ban.ve.xem.phim.model;

import java.math.BigDecimal;

public class TopFilmRow {
    private int filmId;
    private String filmTitle;
    private int soldSeats;
    private BigDecimal totalRevenue;

    public int getFilmId() { return filmId; }
    public void setFilmId(int filmId) { this.filmId = filmId; }
    public String getFilmTitle() { return filmTitle; }
    public void setFilmTitle(String filmTitle) { this.filmTitle = filmTitle; }
    public int getSoldSeats() { return soldSeats; }
    public void setSoldSeats(int soldSeats) { this.soldSeats = soldSeats; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

    public String getFormattedTotalRevenue() {
        if (totalRevenue == null) return "0 đ";
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance(java.util.Locale.forLanguageTag("vi-VN"));
        return nf.format(totalRevenue) + " đ";
    }
}

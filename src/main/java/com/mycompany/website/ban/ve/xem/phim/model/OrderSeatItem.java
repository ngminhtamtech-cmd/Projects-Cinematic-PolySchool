package com.mycompany.website.ban.ve.xem.phim.model;

import java.math.BigDecimal;

public class OrderSeatItem {
    private int showtimeSeatId;
    private String seatKey;
    private String seatType;
    private BigDecimal unitPrice;

    public int getShowtimeSeatId() { return showtimeSeatId; }
    public void setShowtimeSeatId(int showtimeSeatId) { this.showtimeSeatId = showtimeSeatId; }
    public String getSeatKey() { return seatKey; }
    public void setSeatKey(String seatKey) { this.seatKey = seatKey; }
    public String getSeatType() { return seatType; }
    public void setSeatType(String seatType) { this.seatType = seatType; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}

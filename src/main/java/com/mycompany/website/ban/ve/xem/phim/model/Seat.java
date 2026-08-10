package com.mycompany.website.ban.ve.xem.phim.model;

import java.math.BigDecimal;

public class Seat {
    private int id;
    private int roomId;
    private String rowLabel;
    private int seatNumber;
    private String seatType; // 'standard', 'vip', 'couple'
    private String seatKey;  // e.g. "A1", "G9", "I1-2"
    private BigDecimal priceSurcharge = BigDecimal.ZERO;
    private boolean occupied;

    public Seat() {}

    public Seat(int roomId, String rowLabel, int seatNumber, String seatType, String seatKey) {
        this.roomId = roomId;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.seatType = seatType;
        this.seatKey = seatKey;
    }

    public Seat(int roomId, String rowLabel, int seatNumber, String seatType, String seatKey, BigDecimal priceSurcharge) {
        this(roomId, rowLabel, seatNumber, seatType, seatKey);
        this.priceSurcharge = priceSurcharge;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getRoomId() { return roomId; }
    public void setRoomId(int roomId) { this.roomId = roomId; }
    public String getRowLabel() { return rowLabel; }
    public void setRowLabel(String rowLabel) { this.rowLabel = rowLabel; }
    public int getSeatNumber() { return seatNumber; }
    public void setSeatNumber(int seatNumber) { this.seatNumber = seatNumber; }
    public String getSeatType() { return seatType; }
    public void setSeatType(String seatType) { this.seatType = seatType; }
    public String getSeatKey() { return seatKey; }
    public void setSeatKey(String seatKey) { this.seatKey = seatKey; }
    public BigDecimal getPriceSurcharge() { return priceSurcharge; }
    public void setPriceSurcharge(BigDecimal priceSurcharge) { this.priceSurcharge = priceSurcharge; }
    public boolean isOccupied() { return occupied; }
    public void setOccupied(boolean occupied) { this.occupied = occupied; }
}

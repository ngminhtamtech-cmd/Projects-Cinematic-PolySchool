package com.mycompany.website.ban.ve.xem.phim.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ShowtimeSeat {
    private int id;
    private int showtimeId;
    private int seatId;
    private int roomId;
    private String seatKey;
    private String rowLabel;
    private int seatNumber;
    private String seatType;
    private String status;
    private BigDecimal extraFee;
    private Integer heldByUserId;
    private Integer claimedByOrderId;
    private LocalDateTime heldAt;
    private LocalDateTime heldUntil;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getShowtimeId() { return showtimeId; }
    public void setShowtimeId(int showtimeId) { this.showtimeId = showtimeId; }
    public int getSeatId() { return seatId; }
    public void setSeatId(int seatId) { this.seatId = seatId; }
    public int getRoomId() { return roomId; }
    public void setRoomId(int roomId) { this.roomId = roomId; }
    public String getSeatKey() { return seatKey; }
    public void setSeatKey(String seatKey) { this.seatKey = seatKey; }
    public String getRowLabel() { return rowLabel; }
    public void setRowLabel(String rowLabel) { this.rowLabel = rowLabel; }
    public int getSeatNumber() { return seatNumber; }
    public void setSeatNumber(int seatNumber) { this.seatNumber = seatNumber; }
    public String getSeatType() { return seatType; }
    public void setSeatType(String seatType) { this.seatType = seatType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getExtraFee() { return extraFee; }
    public void setExtraFee(BigDecimal extraFee) { this.extraFee = extraFee; }
    public Integer getHeldByUserId() { return heldByUserId; }
    public void setHeldByUserId(Integer heldByUserId) { this.heldByUserId = heldByUserId; }
    public Integer getClaimedByOrderId() { return claimedByOrderId; }
    public void setClaimedByOrderId(Integer claimedByOrderId) { this.claimedByOrderId = claimedByOrderId; }
    public LocalDateTime getHeldAt() { return heldAt; }
    public void setHeldAt(LocalDateTime heldAt) { this.heldAt = heldAt; }
    public LocalDateTime getHeldUntil() { return heldUntil; }
    public void setHeldUntil(LocalDateTime heldUntil) { this.heldUntil = heldUntil; }

    public boolean isAvailableFor(int userId) {
        if ("available".equalsIgnoreCase(status)) {
            return true;
        }
        return "held".equalsIgnoreCase(status) && heldByUserId != null && heldByUserId == userId;
    }

    /** Viewer-specific state; never exposes another customer's identity or draft id. */
    public String viewerState(int userId) {
        if ("available".equalsIgnoreCase(status)) {
            return "available";
        }
        if ("held".equalsIgnoreCase(status)) {
            return heldByUserId != null && heldByUserId == userId ? "heldByMe" : "heldByOther";
        }
        if ("booked".equalsIgnoreCase(status) || "sold".equalsIgnoreCase(status)) {
            return "booked";
        }
        return "maintenance";
    }

    public Integer heldOrderIdFor(int userId) {
        return "heldByMe".equals(viewerState(userId)) ? claimedByOrderId : null;
    }
}

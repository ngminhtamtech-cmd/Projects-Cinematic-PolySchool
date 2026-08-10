package com.mycompany.website.ban.ve.xem.phim.model;

import java.math.BigDecimal;

public class OrderComboItem {
    private int comboFoodId;
    private String comboName;
    private int quantity;
    private BigDecimal unitPrice;

    public int getComboFoodId() { return comboFoodId; }
    public void setComboFoodId(int comboFoodId) { this.comboFoodId = comboFoodId; }
    public String getComboName() { return comboName; }
    public void setComboName(String comboName) { this.comboName = comboName; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getLineTotal() { return unitPrice == null ? BigDecimal.ZERO : unitPrice.multiply(BigDecimal.valueOf(quantity)); }
}

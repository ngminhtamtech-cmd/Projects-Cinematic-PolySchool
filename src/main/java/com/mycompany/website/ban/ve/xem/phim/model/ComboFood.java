package com.mycompany.website.ban.ve.xem.phim.model;

import java.math.BigDecimal;

public class ComboFood {
    private int id;
    private String name;
    private String image;
    private BigDecimal price;
    private String description;
    private String status;

    /**
     * Cum rap so huu combo (CB-01).
     *
     * Moi combo nghiep vu bat buoc thuoc dung mot rap.
     */
    private Integer cinemaId;

    /** Ten rap so huu — chi de hien thi o man hinh quan tri, khong phai cot cua bang. */
    private String cinemaName;

    /**
     * Khong phai cot cua bang ComboFoods — la so luong da ban, chi
     * AdminService.listCombos() tinh va do vao (LEFT JOIN OrderComboFoods).
     * Cac mapper khac (JdbcComboFoodDAO) de nguyen 0; tang dat ve khong dung gia tri nay.
     */
    private int soldQuantity;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getSoldQuantity() { return soldQuantity; }
    public void setSoldQuantity(int soldQuantity) { this.soldQuantity = soldQuantity; }
    public Integer getCinemaId() { return cinemaId; }
    public void setCinemaId(Integer cinemaId) { this.cinemaId = cinemaId; }
    public String getCinemaName() { return cinemaName; }
    public void setCinemaName(String cinemaName) { this.cinemaName = cinemaName; }

    /** Chi con dung de nhan dien du lieu legacy inactive chua duoc don dep. */
    public boolean isGlobal() { return cinemaId == null; }
}

package com.mycompany.website.ban.ve.xem.phim.model;

import java.time.LocalDateTime;

/** Versioned, plain-text policy content shown to customers and operators. */
public class PolicyDocument {
    private int id;
    private String policyKey;
    private int versionNumber;
    private String title;
    private String bodyText;
    private String status;
    private Integer updatedBy;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getPolicyKey() { return policyKey; }
    public void setPolicyKey(String policyKey) { this.policyKey = policyKey; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}

package com.mycompany.website.ban.ve.xem.phim.service;

/** Typed preview for promotion hard-delete versus inactive transition. */
public record PromotionDeleteImpact(int promotionId, String code, int orderRefs,
        int usageRefs, int voucherRefs, int usedCount) {
    public boolean canHardDelete() {
        return orderRefs == 0 && usageRefs == 0 && voucherRefs == 0 && usedCount == 0;
    }
}

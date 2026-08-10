package com.mycompany.website.ban.ve.xem.phim.dao;

import java.sql.Connection;

public interface PromotionUsageDAO {
    Integer findUsableVoucherId(Connection connection, String code, int userId);
    Integer findPromotionIdForVoucher(Connection connection, int voucherId);
    int countForUser(Connection connection, int promotionId, int userId);
    void record(Connection connection, int promotionId, int userId, int orderId);
    void consumeVoucher(Connection connection, int voucherId, int userId, int orderId);
}

package com.mycompany.website.ban.ve.xem.phim.dao;

import com.mycompany.website.ban.ve.xem.phim.model.Promotion;
import java.sql.Connection;
import java.util.Optional;

public interface PromotionDAO {
    Optional<Promotion> findByCode(String code);
    Optional<Promotion> findByCode(Connection connection, String code);
    Optional<Promotion> findById(Connection connection, int promotionId);
    void incrementUsedCount(Connection connection, int promotionId);
}

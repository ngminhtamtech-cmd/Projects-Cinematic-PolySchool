package com.mycompany.website.ban.ve.xem.phim.dao;

import com.mycompany.website.ban.ve.xem.phim.model.ComboFood;
import java.sql.Connection;
import java.util.List;

public interface ComboFoodDAO {
    List<ComboFood> findActive();

    /**
     * Combo dang ban tai mot cum rap: combo dung chung ({@code CinemaId IS NULL}) cong combo
     * rieng cua rap do (CB-01). {@code null} = khong loc theo rap.
     */
    List<ComboFood> findActiveForCinema(Integer cinemaId);

    List<ComboFood> findByIds(List<Integer> ids);

    /**
     * Doc combo theo id, chi lay combo ban duoc tai {@code cinemaId}.
     *
     * <p>Buoc loc nay la chot bao mat, khong phai tien ich hien thi: neu khong loc, khach co the
     * gui id cua combo thuoc rap khac trong request dat ve va van mua duoc.</p>
     */
    List<ComboFood> findByIds(Connection connection, List<Integer> ids, Integer cinemaId);

    List<ComboFood> findByIds(Connection connection, List<Integer> ids);

    List<ComboFood> findByIdsAnyStatus(Connection connection, List<Integer> ids);
}

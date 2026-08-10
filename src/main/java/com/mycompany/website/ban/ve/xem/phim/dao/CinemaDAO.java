package com.mycompany.website.ban.ve.xem.phim.dao;

import com.mycompany.website.ban.ve.xem.phim.model.Cinema;
import java.util.List;

public interface CinemaDAO {
    List<Cinema> findAll(Integer cityId);
}

package com.mycompany.website.ban.ve.xem.phim.dao;

import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface ShowtimeDAO {
    List<Showtime> findByFilmAndCinema(Integer filmId, Integer cinemaId);
    Optional<Showtime> findById(int id);
    Optional<Showtime> findById(Connection connection, int id);
}

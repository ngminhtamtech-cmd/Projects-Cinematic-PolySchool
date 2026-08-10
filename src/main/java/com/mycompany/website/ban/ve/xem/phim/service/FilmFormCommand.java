package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.model.Film;
import java.util.List;

/** Immutable boundary command for create/edit film requests. */
public record FilmFormCommand(Film film, List<Integer> cinemaIds) {
    public FilmFormCommand {
        if (film == null) {
            throw new IllegalArgumentException("film is required");
        }
        cinemaIds = cinemaIds == null ? null : List.copyOf(cinemaIds);
    }
}

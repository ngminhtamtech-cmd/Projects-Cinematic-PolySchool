package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.model.Cinema;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/** Resolves the immutable manager cinema or the admin's persisted viewing context. */
public final class CinemaContextResolver {
    public static final String SESSION_ADMIN_CINEMA_ID = "adminCinemaContextId";

    private CinemaContextResolver() {
    }

    public static Integer prepare(HttpServletRequest request, User actor, List<Cinema> cinemas) {
        if (actor == null) return null;
        request.setAttribute("cinemaContextOptions", cinemas == null ? List.of() : cinemas);
        if (!CinemaCapabilityPolicy.isAdmin(actor)) {
            int cinemaId = CinemaCapabilityPolicy.requireManagerCinema(actor);
            String cinemaName = actor.getCinemaName();
            if ((cinemaName == null || cinemaName.isBlank()) && cinemas != null) {
                cinemaName = cinemas.stream().filter(cinema -> cinema.getId() == cinemaId)
                        .map(Cinema::getName).findFirst().orElse("Rạp #" + cinemaId);
            }
            request.setAttribute("cinemaContextId", cinemaId);
            request.setAttribute("cinemaContextName", cinemaName);
            request.setAttribute("cinemaContextLocked", Boolean.TRUE);
            return cinemaId;
        }

        HttpSession session = request.getSession();
        String selection = request.getParameter("cinemaContextId");
        if (selection != null) {
            if (selection.isBlank() || "all".equalsIgnoreCase(selection) || "0".equals(selection)) {
                session.removeAttribute(SESSION_ADMIN_CINEMA_ID);
            } else {
                int parsed;
                try {
                    parsed = Integer.parseInt(selection);
                } catch (NumberFormatException ex) {
                    throw new BookingException(400, "Rạp được chọn không hợp lệ.");
                }
                boolean exists = cinemas != null && cinemas.stream().anyMatch(cinema -> cinema.getId() == parsed);
                if (!exists) throw new BookingException(404, "Không tìm thấy rạp được chọn.");
                session.setAttribute(SESSION_ADMIN_CINEMA_ID, parsed);
            }
        }

        Integer selected = session.getAttribute(SESSION_ADMIN_CINEMA_ID) instanceof Integer id ? id : null;
        final Integer selectedForLookup = selected;
        Cinema selectedCinema = selectedForLookup == null || cinemas == null ? null
                : cinemas.stream().filter(cinema -> cinema.getId() == selectedForLookup)
                        .findFirst().orElse(null);
        if (selected != null && selectedCinema == null) {
            session.removeAttribute(SESSION_ADMIN_CINEMA_ID);
            selected = null;
        }
        request.setAttribute("cinemaContextId", selected);
        request.setAttribute("cinemaContextName", selectedCinema == null
                ? "Toàn hệ thống" : selectedCinema.getName());
        request.setAttribute("cinemaContextLocked", Boolean.FALSE);
        return selected;
    }

    /**
     * Existing read services use manager scoping. This adapter applies the admin's selected
     * cinema without weakening the real actor used for mutations and capability checks.
     */
    public static User scopedReadActor(User actor, Integer cinemaId) {
        if (!CinemaCapabilityPolicy.isAdmin(actor) || cinemaId == null || cinemaId <= 0) return actor;
        User scoped = new User();
        scoped.setId(actor.getId());
        scoped.setRole("manager");
        scoped.setCinemaId(cinemaId);
        scoped.setFullName(actor.getFullName());
        scoped.setEmail(actor.getEmail());
        return scoped;
    }
}

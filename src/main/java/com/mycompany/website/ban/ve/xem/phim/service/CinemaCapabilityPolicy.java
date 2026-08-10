package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.util.ScopeUtil;

/** Central capability rules for cinema governance. */
public final class CinemaCapabilityPolicy {
    private CinemaCapabilityPolicy() {
    }

    public static boolean isAdmin(User actor) {
        return actor != null && "admin".equalsIgnoreCase(actor.getRole());
    }

    public static boolean isManager(User actor) {
        return ScopeUtil.isManager(actor);
    }

    public static void requireAdmin(User actor) {
        if (!isAdmin(actor) || actor.isDeleted() || actor.isLocked()) {
            throw new BookingException(403, "Chỉ quản trị viên hệ thống được thực hiện thao tác này.");
        }
    }

    public static int requireManagerCinema(User actor) {
        if (!isManager(actor) || actor.isDeleted() || actor.isLocked()) {
            throw new BookingException(403, "Chỉ quản lý rạp đang hoạt động được thực hiện thao tác này.");
        }
        Integer cinemaId = actor.getCinemaId();
        if (cinemaId == null || cinemaId <= 0) {
            throw new BookingException(403, "Tài khoản quản lý chưa được gán rạp.");
        }
        return cinemaId;
    }

    public static void requireCinema(User actor, int cinemaId) {
        if (isAdmin(actor)) {
            return;
        }
        ScopeUtil.assertCinemaScope(actor, cinemaId);
    }

    public static boolean canCreateCinema(User actor) {
        return isAdmin(actor);
    }

    public static boolean canMutateGlobalFilm(User actor) {
        return isAdmin(actor);
    }

    public static boolean canCreateRoomDirectly(User actor) {
        return isAdmin(actor);
    }

    public static boolean canCreateShowtime(User actor) {
        return isAdmin(actor) || isManager(actor);
    }

    public static boolean canCreatePromotion(User actor) {
        return actor != null && !actor.isDeleted() && !actor.isLocked()
                && (isAdmin(actor) || isManager(actor));
    }
}

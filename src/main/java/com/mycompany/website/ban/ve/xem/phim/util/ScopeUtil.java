package com.mycompany.website.ban.ve.xem.phim.util;

import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;

public final class ScopeUtil {
    private ScopeUtil() {}

    public static boolean isManager(User actor) {
        return actor != null && "manager".equalsIgnoreCase(actor.getRole());
    }

    public static boolean isStaff(User actor) {
        return actor != null && "staff".equalsIgnoreCase(actor.getRole());
    }

    public static boolean isCinemaBound(User actor) {
        return isManager(actor) || isStaff(actor);
    }

    public static void assertCinemaScope(User actor, int cinemaId) {
        if (!isCinemaBound(actor)) return;
        Integer assigned = actor.getCinemaId();
        if (assigned == null || assigned <= 0 || assigned != cinemaId) {
            throw new BookingException(403, "Bạn không có quyền truy cập dữ liệu của rạp này.");
        }
    }

    /**
     * Tai nguyen cua cum rap {@code cinemaId} co nam ngoai pham vi cua {@code actor} khong (BUG-07).
     *
     * <p>Khac {@link #assertCinemaScope} o hai diem, va ca hai deu co chu y:</p>
     * <ul>
     *   <li>Ap cho <b>ca staff lan manager</b>. {@code assertCinemaScope} chi chan manager, nen
     *       nhan vien quay ve cua rap nay van doc duoc du lieu cua rap khac — dung lo hong
     *       BUG-07 do duoc tren duong tra cuu ve.</li>
     *   <li>Tra {@code boolean} thay vi nem: duong <b>doc</b> phai co the tra loi "khong tim thay"
     *       y het truong hop that su khong ton tai. Nem mot loi rieng cho truong hop ngoai pham vi
     *       chinh la kenh de do xem ma nao co that o rap khac.</li>
     * </ul>
     *
     * <p>Manager/staff thiếu {@code CinemaId} luôn bị chặn (fail closed). Chỉ admin và các vai trò
     * không vận hành rạp mới không bị ràng buộc bởi hàm này.</p>
     */
    public static boolean isOutOfCinemaScope(User actor, Integer cinemaId) {
        if (actor == null) {
            return false;
        }
        if (!isCinemaBound(actor)) {
            return false;
        }
        Integer assigned = actor.getCinemaId();
        if (assigned == null || assigned <= 0 || cinemaId == null || cinemaId <= 0) {
            return true;
        }
        return !assigned.equals(cinemaId);
    }

    public static ScopeFilter scopeFilter(User actor, String cinemaExpression) {
        if (!isCinemaBound(actor)) return new ScopeFilter("1=1", null);
        if (actor.getCinemaId() == null || actor.getCinemaId() <= 0) {
            throw new BookingException(403, "Tài khoản vận hành chưa được gán rạp.");
        }
        return new ScopeFilter(cinemaExpression + " = ?", actor.getCinemaId());
    }

    public record ScopeFilter(String sql, Integer cinemaId) {}
}

package com.mycompany.website.ban.ve.xem.phim.model;

import java.time.LocalDateTime;

/**
 * Anh chup <b>nho</b> trang thai tai khoan, chi gom nhung truong quyet dinh mot phien con hop le
 * hay khong (D5).
 *
 * <p>Co tinh khong phai {@link User}: {@code AuthFilter} doi chieu no moi 30 giay, nen truy van
 * phai re — 6 cot, khong {@code SELECT *}, khong keo theo {@code PasswordHash} vao bo nho.</p>
 */
public class UserAuthState {
    private final int id;
    private final String role;
    private final boolean locked;
    private final boolean deleted;
    private final Integer cinemaId;
    private final LocalDateTime updatedAt;

    public UserAuthState(int id, String role, boolean locked, boolean deleted,
                         Integer cinemaId, LocalDateTime updatedAt) {
        this.id = id;
        this.role = role;
        this.locked = locked;
        this.deleted = deleted;
        this.cinemaId = cinemaId;
        this.updatedAt = updatedAt;
    }

    public int getId() { return id; }
    public String getRole() { return role; }
    public boolean isLocked() { return locked; }
    public boolean isDeleted() { return deleted; }
    public Integer getCinemaId() { return cinemaId; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /**
     * Trang thai trong DB co con khop voi ban da cache trong session khong.
     *
     * <p>So ca {@code CinemaId} vi P17 se dung no lam pham vi cum rap: doi rap cua mot manager
     * ma phien cu van chay tiep la mot lo IDOR mo san.</p>
     */
    public boolean matchesSession(User sessionUser) {
        if (sessionUser == null) {
            return false;
        }
        boolean sameRole = role != null && role.equalsIgnoreCase(sessionUser.getRole());
        boolean sameCinema = cinemaId == null
                ? sessionUser.getCinemaId() == null
                : cinemaId.equals(sessionUser.getCinemaId());
        return sameRole && sameCinema;
    }
}

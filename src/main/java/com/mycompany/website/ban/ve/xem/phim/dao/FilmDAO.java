package com.mycompany.website.ban.ve.xem.phim.dao;

import com.mycompany.website.ban.ve.xem.phim.model.Film;
import java.util.List;
import java.util.Optional;

public interface FilmDAO {
    List<Film> findFeatured(int limit);
    List<Film> search(String keyword, int offset, int limit);
    Optional<Film> findById(int id);

    /**
     * Tim kiem co loc them trang thai chieu, phuc vu tang REST.
     *
     * @param status "showing" | "coming" | "ended", null hoac rong = khong loc
     */
    List<Film> search(String keyword, String status, int offset, int limit);

    /** Tong so ban ghi khop dieu kien, de tinh meta phan trang. */
    long countSearch(String keyword, String status);

    /**
     * Nhu {@link #search(String, String, int, int)} nhung chi tra phim con duoc hien public.
     *
     * <p>Bo loc vong doi phai nam trong SQL, khong phai loc lai trong Java sau khi da phan
     * trang: loc sau khi cat trang lam mot trang 20 dong tra ve it hon 20 phim ma tong so van
     * dem ca phim da an (FLOW-CATALOG-002).</p>
     */
    List<Film> searchPublic(String keyword, String status, int offset, int limit);

    /** Tong so phim public khop dieu kien — phai dung dung bo loc cua {@link #searchPublic}. */
    long countSearchPublic(String keyword, String status);

    /** Lay danh sach phim duoc gan cho rap chieu theo CinemaFilms table. */
    List<Film> findByCinemaId(int cinemaId);
}

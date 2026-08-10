package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.model.Film;
import com.mycompany.website.ban.ve.xem.phim.service.FilmAvailabilityPolicy;
import com.mycompany.website.ban.ve.xem.phim.service.FilmAvailabilityPolicy.Availability;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vong doi phim theo ngay (EX-01).
 *
 * <p>Dung overload {@code evaluate(film, today, window)} nen moi ranh gioi ngay duoc kiem
 * truc tiep, khong phu thuoc dong ho he thong va khong can DB.</p>
 */
@DisplayName("FilmAvailabilityPolicy — vong doi phim theo ngay")
public class FilmAvailabilityPolicyTest {

    private static final int WINDOW = 3;

    private static Film film(String status, LocalDate release, LocalDate end) {
        Film film = new Film();
        film.setStatus(status);
        film.setReleaseDate(release);
        film.setEndDate(end);
        return film;
    }

    /**
     * Ca chinh trong ban quet: khoi chieu 30/07/2026, ket thuc 20/08/2026.
     */
    @Nested
    @DisplayName("Ca nghiem thu chinh: Release 30/07/2026, End 20/08/2026")
    class MainAcceptanceCase {
        private final Film subject = film("showing",
                LocalDate.of(2026, 7, 30), LocalDate.of(2026, 8, 20));

        @Test
        @DisplayName("29/07 — chua toi ngay khoi chieu thi la 'sap chieu'")
        void beforeRelease() {
            assertEquals(Availability.COMING,
                    FilmAvailabilityPolicy.evaluate(subject, LocalDate.of(2026, 7, 29), WINDOW));
        }

        @Test
        @DisplayName("30/07 — dung ngay khoi chieu thi da la 'dang chieu'")
        void onReleaseDay() {
            assertEquals(Availability.SHOWING,
                    FilmAvailabilityPolicy.evaluate(subject, LocalDate.of(2026, 7, 30), WINDOW));
        }

        @Test
        @DisplayName("16/08 — con 4 ngay thi chua hien badge sap het chieu")
        void fourDaysLeftIsStillPlainShowing() {
            assertEquals(Availability.SHOWING,
                    FilmAvailabilityPolicy.evaluate(subject, LocalDate.of(2026, 8, 16), WINDOW));
        }

        @Test
        @DisplayName("17/08 den 20/08 — con 0..3 ngay thi hien badge, VAN dat ve duoc")
        void lastThreeDaysShowBadgeButRemainBookable() {
            for (int day = 17; day <= 20; day++) {
                LocalDate today = LocalDate.of(2026, 8, day);
                Availability availability = FilmAvailabilityPolicy.evaluate(subject, today, WINDOW);
                assertEquals(Availability.EXPIRING_SOON, availability, "ngay " + today);
                assertTrue(availability.isOnScreen(), "ngay " + today + " van phai dat ve duoc");
                assertTrue(FilmAvailabilityPolicy.isPubliclyVisible(availability), "ngay " + today);
            }
        }

        @Test
        @DisplayName("20/08 — ngay cuoi cung VAN duoc chieu (khoang dong ca hai dau)")
        void endDateIsInclusive() {
            Availability availability =
                    FilmAvailabilityPolicy.evaluate(subject, LocalDate.of(2026, 8, 20), WINDOW);
            assertTrue(availability.isOnScreen(), "ngay ket thuc van phai ban ve duoc");
        }

        @Test
        @DisplayName("21/08 — qua ngay ket thuc thi het han va bien khoi trang public")
        void dayAfterEndDateIsExpired() {
            Availability availability =
                    FilmAvailabilityPolicy.evaluate(subject, LocalDate.of(2026, 8, 21), WINDOW);
            assertEquals(Availability.EXPIRED, availability);
            assertFalse(FilmAvailabilityPolicy.isPubliclyVisible(availability));
            assertFalse(availability.isOnScreen());
        }
    }

    @Test
    @DisplayName("EndDate rong = chua gioi han, khong bao gio het han")
    void nullEndDateNeverExpires() {
        Film subject = film("showing", LocalDate.of(2020, 1, 1), null);
        assertEquals(Availability.SHOWING,
                FilmAvailabilityPolicy.evaluate(subject, LocalDate.of(2030, 1, 1), WINDOW));
        assertTrue(FilmAvailabilityPolicy.isPubliclyVisible(subject));
    }

    @Test
    @DisplayName("Status 'ended' chan public bat ke ngay thang")
    void editorialWithdrawalBeatsDates() {
        Film subject = film("ended", LocalDate.of(2026, 1, 1), LocalDate.of(2099, 1, 1));
        Availability availability =
                FilmAvailabilityPolicy.evaluate(subject, LocalDate.of(2026, 6, 1), WINDOW);
        assertEquals(Availability.WITHDRAWN, availability);
        assertFalse(FilmAvailabilityPolicy.isPubliclyVisible(availability));
    }

    @Test
    @DisplayName("Phim sap chieu hien public de quang ba va duoc dat ve truoc (presale)")
    void comingIsVisibleAndBookable() {
        Film subject = film("coming", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
        Availability availability =
                FilmAvailabilityPolicy.evaluate(subject, LocalDate.of(2026, 11, 1), WINDOW);
        assertEquals(Availability.COMING, availability);
        assertTrue(FilmAvailabilityPolicy.isPubliclyVisible(availability),
                "phim sap chieu van phai hien de quang ba");
        assertTrue(availability.isOnScreen(), "va duoc ban ve presale");
    }

    @Test
    @DisplayName("Cua so canh bao = 0 thi chi ngay cuoi cung moi hien badge")
    void zeroWindowOnlyFlagsFinalDay() {
        Film subject = film("showing", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20));
        assertEquals(Availability.SHOWING,
                FilmAvailabilityPolicy.evaluate(subject, LocalDate.of(2026, 8, 19), 0));
        assertEquals(Availability.EXPIRING_SOON,
                FilmAvailabilityPolicy.evaluate(subject, LocalDate.of(2026, 8, 20), 0));
    }

    @Test
    @DisplayName("publicOnly() loc bo phim het han va phim bi rut")
    void publicOnlyFiltersHiddenFilms() {
        // Dung ngay co dinh qua evaluate() de khong phu thuoc dong ho khi kiem tung phim.
        LocalDate today = LocalDate.of(2026, 8, 21);
        Film expired = film("showing", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 20));
        Film withdrawn = film("ended", LocalDate.of(2026, 7, 1), null);
        Film live = film("showing", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30));

        assertFalse(FilmAvailabilityPolicy.isPubliclyVisible(
                FilmAvailabilityPolicy.evaluate(expired, today, WINDOW)));
        assertFalse(FilmAvailabilityPolicy.isPubliclyVisible(
                FilmAvailabilityPolicy.evaluate(withdrawn, today, WINDOW)));
        assertTrue(FilmAvailabilityPolicy.isPubliclyVisible(
                FilmAvailabilityPolicy.evaluate(live, today, WINDOW)));
    }

    @Test
    @DisplayName("Film.getStatus() suy ra tu vong doi chu khong doc thang cot Status")
    void derivedStatusReflectsLifecycle() {
        Film expired = film("showing", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1));
        // Cot Status trong DB van la 'showing' — day chinh la ly do khong duoc tin no.
        assertEquals("showing", expired.getRawStatus());
        assertEquals("expired", expired.getStatus(),
                "phim qua EndDate phai bao 'expired' du cot Status con ghi 'showing'");
    }

    @Test
    @DisplayName("Phim null coi nhu khong hien public, khong nem exception")
    void nullFilmIsHidden() {
        assertEquals(Availability.WITHDRAWN,
                FilmAvailabilityPolicy.evaluate(null, LocalDate.of(2026, 1, 1), WINDOW));
    }
}

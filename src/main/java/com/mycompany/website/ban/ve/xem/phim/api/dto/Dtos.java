package com.mycompany.website.ban.ve.xem.phim.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Toan bo DTO cua tang REST, gom trong mot file duoi dang record long nhau.
 *
 * <p>Ly do ton tai lop nay: <b>khong bao gio serialize thang model</b>.
 * {@code User} chua {@code passwordHash}; {@code Film}/{@code Showtime} chua cac alias
 * chi phuc vu JSP ({@code getGenre}, {@code getCast}, {@code getDuration}...) se lam
 * phinh payload va bien chi tiet noi bo thanh hop dong cong khai.</p>
 */
public final class Dtos {

    private Dtos() {
    }

    // ------------------------------------------------------------- catalogue

    public record CityDto(int id, String name) {
    }

    public record CinemaDto(
            int id,
            int cityId,
            String cityName,
            String name,
            String address,
            String phone,
            String status,
            String avatar,
            String bannerUrl,
            String description,
            Integer roomCount) {
    }

    /** Ban rut gon cho danh sach/grid - tranh keo mo ta dai va danh sach dien vien. */
    public record FilmSummaryDto(
            int id,
            String title,
            String thumbnail,
            String banner,
            Double rating,
            String ageRating,
            Integer durationMinutes,
            LocalDate releaseDate,
            LocalDate endDate,
            String status,
            /** COMING | SHOWING | EXPIRING_SOON | EXPIRED | WITHDRAWN — xem FilmAvailabilityPolicy. */
            String availability,
            /** Con bao nhieu ngay toi ngay chieu cuoi; null khi phim khong gioi han ngay. */
            Long daysUntilEnd,
            String format,
            String country) {
    }

    public record FilmDto(
            int id,
            String title,
            String otherTitles,
            String actors,
            String directors,
            List<String> categories,
            Double rating,
            LocalDate releaseDate,
            LocalDate endDate,
            Integer durationMinutes,
            String ageRating,
            String trailerUrl,
            String thumbnail,
            String banner,
            String language,
            String subtitles,
            String description,
            String country,
            String format,
            String status,
            /** COMING | SHOWING | EXPIRING_SOON | EXPIRED | WITHDRAWN — xem FilmAvailabilityPolicy. */
            String availability,
            Long daysUntilEnd) {
    }

    public record ShowtimeDto(
            int id,
            int filmId,
            String filmTitle,
            String filmThumbnail,
            String ageRating,
            int cinemaId,
            String cinemaName,
            int cityId,
            int roomId,
            String roomName,
            // BUG-12: da bo roomStatus/roomActive. Do la trang thai VAN HANH NOI BO cua rap, khong
            // phai thong tin khach can. Sau khi danh sach cong khai loc phong inactive ngay trong
            // SQL, hai truong nay cung khong con y nghia — moi suat tra ra deu o phong dang chay.
            LocalDateTime startTime,
            LocalDateTime endTime,
            BigDecimal basePrice,
            String format,
            String version,
            String language,
            String formatVersionDisplay) {
    }

    public record SeatDto(
            int id,
            int seatId,
            String seatKey,
            String rowLabel,
            int seatNumber,
            String seatType,
            String status,
            BigDecimal extraFee,
            boolean selectable,
            String viewerState,
            Integer heldOrderId,
            LocalDateTime heldUntil) {
    }

    public record ComboDto(
            int id,
            String name,
            String image,
            BigDecimal price,
            String description) {
    }

    /**
     * Trang thai giu ghe that cua mot don, phuc vu dong ho dem nguoc tren UI (B3).
     *
     * <p>{@code remainingSeconds} do SQL Server tinh nen may khach lech gio khong lam sai.
     * Front-end phai tin {@code expired} chu khong tu suy tu {@code heldUntil}.</p>
     */
    public record HoldStatusDto(
            int orderId,
            String orderStatus,
            String paymentStatus,
            LocalDateTime heldUntil,
            int remainingSeconds,
            int heldSeatCount,
            boolean expired) {
    }

    public record CommentDto(
            int id,
            int filmId,
            String userFullName,
            int rate,
            String content,
            LocalDateTime createdAt) {
    }

    /** Du lieu cho mega-menu header. Thay the HeaderDataFilter (2 truy van DB moi request). */
    public record HeaderDataDto(
            List<FilmSummaryDto> nowShowing,
            List<FilmSummaryDto> upcoming,
            List<CinemaDto> cinemas) {
    }

    // ------------------------------------------------------------------ auth

    /** Ho so cong khai cua nguoi dung. Tuyet doi khong chua passwordHash. */
    public record UserDto(
            int id,
            String username,
            String fullName,
            String email,
            String phone,
            String address,
            String avatar,
            String role,
            int loyaltyPoints,
            BigDecimal totalSpent,
            String membershipTier,
            String tierDisplayName) {
    }

    /**
     * Ket qua dang nhap/lam moi cua tang REST (FLOW-AUTH-ACCESS-001).
     *
     * <p>{@code accessToken} la JWT song {@code expiresIn} giay. <b>Refresh token co y khong
     * nam trong DTO nay</b>: no chi di bang cookie {@code HttpOnly}, nen ma JavaScript khong
     * doc duoc va khong the vo tinh ghi vao {@code localStorage} — xem design muc 6.</p>
     */
    /**
     * BUG-15: da bo {@code accessToken}/{@code expiresIn}. He thong khong con phat JWT access
     * token — xac thuc chi con session {@code JSESSIONID} + refresh token opaque.
     */
    public record SessionDto(UserDto user, String csrfToken) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record RegisterRequest(String fullName, String email, String password, String confirmPassword) {
    }

    public record CsrfDto(String csrfToken) {
    }

    /** Than request cua {@code POST /api/v1/auth/forgot-password} (P10 — D11). */
    public record ForgotPasswordRequest(String email) {
    }

    /** Cau tra loi trung tinh: khong he lo email co ton tai trong he thong hay khong. */
    public record MessageDto(String message) {
    }

    // -------------------------------------------------------------- promotion

    public record PromotionDto(
            int id,
            String code,
            String description,
            Double discountPercent,
            BigDecimal maxDiscount,
            LocalDate startDate,
            LocalDate endDate,
            Integer usageLimit,
            int usedCount,
            String status,
            String voucherType,
            String targetTier,
            String targetTierDisplay,
            int pointsRequired) {
    }

    // ------------------------------------------------------------- quay ve

    /**
     * Ve nhin tu quay soat ve.
     *
     * <p>Co y KHONG chua {@code userId}, {@code promotionId} hay chi tiet gia von:
     * nhan vien chi can du thong tin de doi chieu khach va cho vao phong.</p>
     */
    public record StaffTicketDto(
            int orderId,
            String ticketCode,
            String filmTitle,
            String cinemaName,
            String roomName,
            LocalDateTime startTime,
            String seats,
            String combos,
            String customerName,
            BigDecimal totalAmount,
            String paymentMethod,
            String paymentStatus,
            String orderStatus,
            LocalDateTime redeemedAt) {
    }

    /**
     * Ket qua cham diem mot ma ve.
     *
     * <p>{@code verdict} la ten hang trong {@code StaffService.Verdict}.
     * {@code canCheckIn}/{@code canCollectPayment} chi de giao dien bat/tat nut -
     * quyet dinh that van do transaction phia server dam bao.</p>
     */
    public record StaffLookupDto(
            String verdict,
            String message,
            boolean canCheckIn,
            boolean canCollectPayment,
            StaffTicketDto ticket) {
    }
}

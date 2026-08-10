package com.mycompany.website.ban.ve.xem.phim.api.dto;

import com.mycompany.website.ban.ve.xem.phim.model.Cinema;
import com.mycompany.website.ban.ve.xem.phim.model.ComboFood;
import com.mycompany.website.ban.ve.xem.phim.model.Film;
import com.mycompany.website.ban.ve.xem.phim.service.FilmAvailabilityPolicy;
import com.mycompany.website.ban.ve.xem.phim.model.FilmComment;
import com.mycompany.website.ban.ve.xem.phim.model.OrderHoldStatus;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.Promotion;
import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.StaffService;
import java.util.List;
import java.util.Map;

/** Chuyen model noi bo sang DTO cong khai. Thuan tuy anh xa, khong chua nghiep vu. */
public final class DtoMapper {

    private DtoMapper() {
    }

    /**
     * Chuan hoa duong dan anh ve dang khong phu thuoc context path.
     *
     * <p>Trinh upload cu luu ca context path vao DB, vi du
     * {@code /Website-ban-ve-xem-phim/assets/uploads/a.jpg}. Gia tri do chi dung khi ung dung
     * duoc deploy dung ten context cu - doi ten context hoac chuyen sang Next.js deu hong anh.
     * Tang REST luon tra ve {@code /assets/uploads/a.jpg} de client tu ghep tien to.</p>
     *
     * <p>URL tuyet doi (http/https) duoc giu nguyen.</p>
     */
    static String asset(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String value = path.trim();
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("//")) {
            return value;
        }
        for (String prefix : new String[]{"/assets/", "/uploads/"}) {
            int marker = value.indexOf(prefix);
            if (marker >= 0) {
                return value.substring(marker);
            }
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    public static Dtos.CityDto city(int id, String name) {
        return new Dtos.CityDto(id, name);
    }

    public static List<Dtos.CityDto> cities(Map<Integer, String> options) {
        return options.entrySet().stream()
                .map(entry -> city(entry.getKey(), entry.getValue()))
                .toList();
    }

    public static Dtos.CinemaDto cinema(Cinema cinema) {
        return new Dtos.CinemaDto(
                cinema.getId(),
                cinema.getCityId(),
                cinema.getCityName(),
                cinema.getName(),
                cinema.getAddress(),
                cinema.getPhone(),
                cinema.getStatus(),
                asset(cinema.getAvatar()),
                asset(cinema.getBannerUrl()),
                cinema.getDescription(),
                cinema.getRoomCount());
    }

    public static List<Dtos.CinemaDto> cinemas(List<Cinema> list) {
        return list.stream().map(DtoMapper::cinema).toList();
    }

    public static Dtos.FilmSummaryDto filmSummary(Film film) {
        return new Dtos.FilmSummaryDto(
                film.getId(),
                film.getTitle(),
                asset(film.getThumbnail()),
                asset(film.getBanner()),
                film.getRating(),
                film.getAgeRating(),
                film.getDurationMinutes(),
                film.getReleaseDate(),
                film.getEndDate(),
                film.getStatus(),
                FilmAvailabilityPolicy.evaluate(film).name(),
                FilmAvailabilityPolicy.daysUntilEnd(film),
                film.getFormat(),
                film.getCountry());
    }

    public static List<Dtos.FilmSummaryDto> filmSummaries(List<Film> list) {
        return list.stream().map(DtoMapper::filmSummary).toList();
    }

    public static Dtos.FilmDto film(Film film) {
        return new Dtos.FilmDto(
                film.getId(),
                film.getTitle(),
                film.getOtherTitles(),
                film.getActors(),
                film.getDirectors(),
                splitCategories(film.getCategories()),
                film.getRating(),
                film.getReleaseDate(),
                film.getEndDate(),
                film.getDurationMinutes(),
                film.getAgeRating(),
                film.getTrailerUrl(),
                asset(film.getThumbnail()),
                asset(film.getBanner()),
                film.getLanguage(),
                film.getSubtitles(),
                film.getDescription(),
                film.getCountry(),
                film.getFormat(),
                film.getStatus(),
                FilmAvailabilityPolicy.evaluate(film).name(),
                FilmAvailabilityPolicy.daysUntilEnd(film));
    }

    private static List<String> splitCategories(String categories) {
        if (categories == null || categories.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(categories.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    public static Dtos.ShowtimeDto showtime(Showtime showtime) {
        return new Dtos.ShowtimeDto(
                showtime.getId(),
                showtime.getFilmId(),
                showtime.getFilmTitle(),
                asset(showtime.getThumbnail()),
                showtime.getAgeRating(),
                showtime.getCinemaId(),
                showtime.getCinemaName(),
                showtime.getCityId(),
                showtime.getRoomId(),
                showtime.getRoomName(),
                showtime.getStartTime(),
                showtime.getEndTime(),
                showtime.getBasePrice(),
                showtime.getFormat(),
                showtime.getVersion(),
                showtime.getLanguage(),
                showtime.getFormatVersionDisplay());
    }

    public static List<Dtos.ShowtimeDto> showtimes(List<Showtime> list) {
        return list.stream().map(DtoMapper::showtime).toList();
    }

    /**
     * @param viewerUserId id nguoi dung dang xem, hoac -1 neu khach vang lai.
     *                     Quyet dinh co the chon ghe hay khong (ghe minh dang giu van chon duoc).
     */
    public static Dtos.SeatDto seat(ShowtimeSeat seat, int viewerUserId) {
        return new Dtos.SeatDto(
                seat.getId(),
                seat.getSeatId(),
                seat.getSeatKey(),
                seat.getRowLabel(),
                seat.getSeatNumber(),
                seat.getSeatType(),
                seat.getStatus(),
                seat.getExtraFee(),
                seat.isAvailableFor(viewerUserId),
                seat.viewerState(viewerUserId),
                seat.heldOrderIdFor(viewerUserId),
                seat.getHeldUntil());
    }

    public static List<Dtos.SeatDto> seats(List<ShowtimeSeat> list, int viewerUserId) {
        return list.stream().map(seat -> seat(seat, viewerUserId)).toList();
    }

    public static Dtos.HoldStatusDto holdStatus(OrderHoldStatus status) {
        return new Dtos.HoldStatusDto(
                status.getOrderId(),
                status.getOrderStatus(),
                status.getPaymentStatus(),
                status.getHeldUntil(),
                Math.max(0, status.getRemainingSeconds()),
                status.getHeldSeatCount(),
                status.isExpired());
    }

    public static Dtos.ComboDto combo(ComboFood combo) {
        return new Dtos.ComboDto(
                combo.getId(),
                combo.getName(),
                asset(combo.getImage()),
                combo.getPrice(),
                combo.getDescription());
    }

    public static List<Dtos.ComboDto> combos(List<ComboFood> list) {
        return list.stream().map(DtoMapper::combo).toList();
    }

    public static Dtos.CommentDto comment(FilmComment comment) {
        return new Dtos.CommentDto(
                comment.getId(),
                comment.getFilmId(),
                comment.getUserFullName(),
                comment.getRate(),
                comment.getContent(),
                comment.getCreatedAt());
    }

    public static List<Dtos.CommentDto> comments(List<FilmComment> list) {
        return list.stream().map(DtoMapper::comment).toList();
    }

    public static Dtos.UserDto user(User user) {
        return new Dtos.UserDto(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getAddress(),
                asset(user.getAvatar()),
                user.getRole(),
                user.getLoyaltyPoints(),
                user.getTotalSpent(),
                user.getMembershipTier(),
                user.getTierDisplayName());
    }

    public static Dtos.PromotionDto promotion(Promotion promotion) {
        return new Dtos.PromotionDto(
                promotion.getId(),
                promotion.getCode(),
                promotion.getDescription(),
                promotion.getDiscountPercent(),
                promotion.getMaxDiscount(),
                promotion.getStartDate(),
                promotion.getEndDate(),
                promotion.getUsageLimit(),
                promotion.getUsedCount(),
                promotion.getStatus(),
                promotion.getVoucherType(),
                promotion.getTargetTier(),
                promotion.getTargetTierDisplay(),
                promotion.getPointsRequired());
    }

    public static List<Dtos.PromotionDto> promotions(List<Promotion> list) {
        return list.stream().map(DtoMapper::promotion).toList();
    }

    // ------------------------------------------------------------------ quay ve

    public static Dtos.StaffTicketDto staffTicket(OrderRecord order) {
        if (order == null) {
            return null;
        }
        return new Dtos.StaffTicketDto(
                order.getId(),
                order.getTicketCode(),
                order.getFilmTitle(),
                order.getCinemaName(),
                order.getRoomName(),
                order.getStartTime(),
                order.getSeatSummary(),
                order.getComboSummary(),
                order.getUserFullName(),
                order.getTotalAmount(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.getOrderStatus(),
                order.getRedeemedAt());
    }

    public static Dtos.StaffLookupDto staffLookup(StaffService.TicketLookup lookup) {
        return new Dtos.StaffLookupDto(
                lookup.getVerdictName(),
                lookup.getMessage(),
                lookup.isCanCheckIn(),
                lookup.isCanCollectPayment(),
                staffTicket(lookup.getOrder()));
    }
}

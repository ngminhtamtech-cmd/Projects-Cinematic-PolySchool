package com.mycompany.website.ban.ve.xem.phim.api.v1;

import com.mycompany.website.ban.ve.xem.phim.api.ApiException;
import com.mycompany.website.ban.ve.xem.phim.api.BaseApiServlet;
import com.mycompany.website.ban.ve.xem.phim.api.dto.DtoMapper;
import com.mycompany.website.ban.ve.xem.phim.dao.FilmDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.ShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcFilmDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.model.Film;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.FilmAvailabilityPolicy;
import java.io.IOException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * /api/v1/films
 * /api/v1/films/{id}
 * /api/v1/films/{id}/showtimes
 * /api/v1/films/{id}/comments   (GET cong khai, POST yeu cau dang nhap)
 */
public class FilmApiServlet extends BaseApiServlet {
    private static final Set<String> ALLOWED_STATUS = Set.of("showing", "coming", "ended");

    private final FilmDAO filmDAO = new JdbcFilmDAO();
    private final ShowtimeDAO showtimeDAO = new JdbcShowtimeDAO();
    private final AdminService adminService = new AdminService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] segments = segments(request);
        if (segments.length == 0) {
            list(request, response);
            return;
        }
        int filmId = requireIntSegment(segments, 0, "filmId");
        if (segments.length == 1) {
            detail(response, filmId);
            return;
        }
        switch (segments[1]) {
            case "showtimes" -> writeData(response,
                    DtoMapper.showtimes(showtimeDAO.findByFilmAndCinema(filmId, intOrNull(request, "cinemaId"))));
            case "comments" -> writeData(response, DtoMapper.comments(adminService.listCommentsByFilm(filmId)));
            default -> throw ApiException.notFound("Khong tim thay tai nguyen con.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] segments = segments(request);
        int filmId = requireIntSegment(segments, 0, "filmId");

        if (segments.length == 4 && "comments".equals(segments[1]) && "report".equals(segments[3])) {
            int commentId = requireIntSegment(segments, 2, "commentId");
            ReportRequest reportBody = readBody(request, ReportRequest.class);
            String reason = reportBody != null && reportBody.reason() != null ? reportBody.reason() : "Nội dung vi phạm quy định cộng đồng.";
            User user = currentUser(request);
            Integer reporterId = user == null ? null : user.getId();
            adminService.reportComment(commentId, reporterId, reason);
            writeData(response, Map.of("success", true, "commentId", commentId));
            return;
        }

        if (segments.length < 2 || !"comments".equals(segments[1])) {
            throw ApiException.notFound("Khong tim thay endpoint.");
        }
        User user = requireMember(request);

        if (filmDAO.findById(filmId).isEmpty()) {
            throw ApiException.notFound("Khong tim thay phim.");
        }

        CommentRequest body = readBody(request, CommentRequest.class);
        int rate = body.rate();
        String content = body.content() == null ? "" : body.content().trim();
        if (rate < 1 || rate > 5) {
            throw ApiException.badRequest("Danh gia phai tu 1 den 5 sao.");
        }
        if (content.isBlank()) {
            throw ApiException.badRequest("Vui long nhap noi dung binh luan.");
        }

        adminService.addComment(user.getId(), filmId, rate, content);
        writeCreated(response, Map.of("filmId", filmId, "rate", rate));
    }

    private void list(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer cinemaId = intOrNull(request, "cinemaId");
        if (cinemaId != null && cinemaId > 0) {
            // EX-01: API public khong duoc tra phim da het chieu — tang Next.js dung chung
            // nguon nay nen bo loc phai nam o day chu khong phai o component.
            List<Film> films = FilmAvailabilityPolicy.publicOnly(filmDAO.findByCinemaId(cinemaId));
            writePage(response, DtoMapper.filmSummaries(films), 1, Math.max(1, films.size()), films.size());
            return;
        }

        String keyword = str(request, "q");
        String status = str(request, "status");
        if (!status.isBlank() && !ALLOWED_STATUS.contains(status)) {
            throw ApiException.badRequest("status chi nhan: showing, coming, ended.");
        }
        int page = pageParam(request);
        int size = sizeParam(request);
        int offset = (page - 1) * size;

        // FLOW-CATALOG-002: items va total phai di ra tu cung mot bo loc. Ban cu dem bang
        // countSearch (khong biet vong doi) roi moi loc phim het han khoi trang da cat, nen mot
        // phim da het chieu van lam total=1 va totalPages=1 trong khi danh sach rong.
        long total = filmDAO.countSearchPublic(keyword, status);
        writePage(response,
                DtoMapper.filmSummaries(
                        FilmAvailabilityPolicy.publicOnly(
                                filmDAO.searchPublic(keyword, status, offset, size))),
                page, size, total);
    }

    private void detail(HttpServletResponse response, int filmId) throws IOException {
        Film film = filmDAO.findById(filmId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay phim."));

        // EX-01: phim het chieu coi nhu khong con tren API public. Dung 404 (khong phai 410)
        // vi ApiException chuan hoa envelope loi theo cac ma da co; client Next.js xu ly 404
        // bang trang "khong tim thay" san co.
        if (!FilmAvailabilityPolicy.isPubliclyVisible(film)) {
            throw ApiException.notFound("Phim này đã kết thúc lịch chiếu.");
        }

        // Gop du lieu trang chi tiet vao mot lan goi de Server Component chi can fetch mot lan.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("film", DtoMapper.film(film));
        payload.put("showtimes", DtoMapper.showtimes(showtimeDAO.findByFilmAndCinema(filmId, null)));
        payload.put("comments", DtoMapper.comments(adminService.listCommentsByFilm(filmId)));
        writeData(response, payload);
    }

    public record CommentRequest(int rate, String content) {
    }

    public record ReportRequest(String reason) {
    }
}

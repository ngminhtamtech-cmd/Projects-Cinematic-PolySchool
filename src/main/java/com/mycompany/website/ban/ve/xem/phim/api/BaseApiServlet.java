package com.mycompany.website.ban.ve.xem.phim.api;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.util.RoleUtil;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Lop nen cho moi servlet REST duoi /api/v1.
 *
 * <p>Trach nhiem: bao bi response, dich exception sang HTTP status, doc tham so va body.
 * <b>Khong chua nghiep vu</b> - toan bo logic van nam o BookingService/AdminService/DAO.</p>
 */
public abstract class BaseApiServlet extends HttpServlet {

    /** Trang mac dinh va tran kich thuoc trang de tranh client keo ca bang. */
    protected static final int DEFAULT_PAGE_SIZE = 20;
    protected static final int MAX_PAGE_SIZE = 100;

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        try {
            super.service(request, response);
        } catch (ApiException ex) {
            writeError(response, ex.getStatus(), ex.getCode(), ex.getMessage());
        } catch (BookingException ex) {
            // BookingException da mang san HTTP status tu tang service.
            writeError(response, ex.getStatusCode(), ApiException.codeForStatus(ex.getStatusCode()), ex.getMessage());
        } catch (DaoException ex) {
            log("DAO error on " + request.getRequestURI(), ex);
            writeError(response, 500, "INTERNAL_ERROR", "Loi truy cap du lieu.");
        } catch (RuntimeException ex) {
            log("Unhandled error on " + request.getRequestURI(), ex);
            writeError(response, 500, "INTERNAL_ERROR", "Loi he thong.");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        throw new ApiException(405, "METHOD_NOT_ALLOWED", "GET khong duoc ho tro cho endpoint nay.");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        throw new ApiException(405, "METHOD_NOT_ALLOWED", "POST khong duoc ho tro cho endpoint nay.");
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        throw new ApiException(405, "METHOD_NOT_ALLOWED", "PUT khong duoc ho tro cho endpoint nay.");
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        throw new ApiException(405, "METHOD_NOT_ALLOWED", "DELETE khong duoc ho tro cho endpoint nay.");
    }

    // ---------------------------------------------------------------- output

    protected void writeData(HttpServletResponse response, Object data) throws IOException {
        write(response, 200, ApiEnvelope.ok(data));
    }

    protected void writeCreated(HttpServletResponse response, Object data) throws IOException {
        write(response, 201, ApiEnvelope.ok(data));
    }

    protected void writePage(HttpServletResponse response, List<?> items, int page, int size, long total)
            throws IOException {
        write(response, 200, ApiEnvelope.page(items, page, size, total));
    }

    protected void writeNoContent(HttpServletResponse response) throws IOException {
        write(response, 200, ApiEnvelope.ok(null));
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.reset();
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        write(response, status, ApiEnvelope.fail(code, message));
    }

    private void write(HttpServletResponse response, int status, ApiEnvelope envelope) throws IOException {
        response.setStatus(status);
        Json.mapper().writeValue(response.getOutputStream(), envelope);
    }

    // ----------------------------------------------------------------- input

    protected <T> T readBody(HttpServletRequest request, Class<T> type) {
        try {
            T value = Json.mapper().readValue(request.getInputStream(), type);
            if (value == null) {
                throw ApiException.badRequest("Body rong.");
            }
            return value;
        } catch (IOException ex) {
            throw ApiException.badRequest("Body khong phai JSON hop le.");
        }
    }

    /** Tach cac doan cua pathInfo, vi du "/12/seats" -> ["12", "seats"]. */
    protected String[] segments(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank() || "/".equals(pathInfo)) {
            return new String[0];
        }
        return pathInfo.replaceAll("^/+", "").replaceAll("/+$", "").split("/");
    }

    protected String str(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value.trim();
    }

    protected Integer intOrNull(HttpServletRequest request, String name) {
        String raw = str(request, name);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("Tham so '" + name + "' phai la so nguyen.");
        }
    }

    protected int intOr(HttpServletRequest request, String name, int fallback) {
        Integer value = intOrNull(request, name);
        return value == null ? fallback : value;
    }

    protected LocalDate dateOrNull(HttpServletRequest request, String name) {
        String raw = str(request, name);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException ex) {
            throw ApiException.badRequest("Tham so '" + name + "' phai theo dinh dang yyyy-MM-dd.");
        }
    }

    protected int requireIntSegment(String[] segments, int index, String what) {
        if (segments.length <= index) {
            throw ApiException.notFound("Thieu " + what + " tren duong dan.");
        }
        try {
            return Integer.parseInt(segments[index]);
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest(what + " phai la so nguyen.");
        }
    }

    protected int pageParam(HttpServletRequest request) {
        return Math.max(1, intOr(request, "page", 1));
    }

    protected int sizeParam(HttpServletRequest request) {
        int size = intOr(request, "size", DEFAULT_PAGE_SIZE);
        return Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    }

    protected BigDecimal decimalOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("Gia tri so tien khong hop le: " + raw);
        }
    }

    // ------------------------------------------------------------------ auth

    /** Nguoi dung hien tai, hoac null neu chua dang nhap. */
    protected User currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(AppConstants.SESSION_USER);
        return value instanceof User ? (User) value : null;
    }

    protected User requireUser(HttpServletRequest request) {
        User user = currentUser(request);
        if (user == null) {
            throw ApiException.unauthorized("Vui long dang nhap.");
        }
        return user;
    }

    /**
     * Yeu cau vai tro toi thieu. Dung lai thu tu cap bac cua RoleUtil
     * (admin &gt; manager &gt; member) de khong lech voi AuthFilter cua phan JSP.
     */
    protected User requireRole(HttpServletRequest request, String minimumRole) {
        User user = requireUser(request);
        if (!RoleUtil.isAtLeast(user.getRole(), minimumRole)) {
            throw ApiException.forbidden("Ban khong co quyen thuc hien thao tac nay.");
        }
        return user;
    }

    protected User requireMember(HttpServletRequest request) {
        return requireRole(request, AppConstants.ROLE_MEMBER);
    }

    /** Nhan vien quay ve tro len (manager/admin cung thoa nho thang bac RoleUtil). */
    protected User requireStaff(HttpServletRequest request) {
        return requireRole(request, AppConstants.ROLE_STAFF);
    }

    protected User requireManager(HttpServletRequest request) {
        return requireRole(request, AppConstants.ROLE_MANAGER);
    }

    protected User requireAdmin(HttpServletRequest request) {
        return requireRole(request, AppConstants.ROLE_ADMIN);
    }
}

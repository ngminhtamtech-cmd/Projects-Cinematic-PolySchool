package com.mycompany.website.ban.ve.xem.phim.api.v1;

import com.mycompany.website.ban.ve.xem.phim.api.ApiException;
import com.mycompany.website.ban.ve.xem.phim.api.BaseApiServlet;
import com.mycompany.website.ban.ve.xem.phim.api.CsrfFilter;
import com.mycompany.website.ban.ve.xem.phim.api.dto.DtoMapper;
import com.mycompany.website.ban.ve.xem.phim.api.dto.Dtos;
import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.config.SecuritySettings;
import com.mycompany.website.ban.ve.xem.phim.dao.UserDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserDAO;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AccountStateGuard;
import com.mycompany.website.ban.ve.xem.phim.service.LoginThrottleService;
import com.mycompany.website.ban.ve.xem.phim.service.PasswordResetService;
import com.mycompany.website.ban.ve.xem.phim.service.auth.RefreshTokenService;
import com.mycompany.website.ban.ve.xem.phim.util.PasswordPolicy;
import com.mycompany.website.ban.ve.xem.phim.util.PasswordUtil;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * /api/v1/auth/*
 *
 * <p>Giu nguyen co che session {@code JSESSIONID} va {@code PasswordUtil} (BCrypt cost 12)
 * cua he thong hien tai - khong doi sang JWT de tranh rui ro thua.</p>
 */
public class AuthApiServlet extends BaseApiServlet {
    private final UserDAO userDAO = new JdbcUserDAO();
    private final LoginThrottleService loginThrottle = new LoginThrottleService();
    private final PasswordResetService passwordResetService = new PasswordResetService();
    private final RefreshTokenService refreshTokens = new RefreshTokenService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] segments = segments(request);
        String action = segments.length == 0 ? "" : segments[0];
        switch (action) {
            case "csrf" -> writeData(response, new Dtos.CsrfDto(CsrfFilter.currentToken(request)));
            case "me" -> writeData(response, DtoMapper.user(requireUser(request)));
            default -> throw ApiException.notFound("Khong tim thay endpoint auth.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] segments = segments(request);
        String action = segments.length == 0 ? "" : segments[0];
        switch (action) {
            case "login" -> login(request, response);
            case "logout" -> logout(request, response);
            case "logout-all" -> logoutAll(request, response);
            case "refresh" -> refresh(request, response);
            case "register" -> register(request, response);
            case "forgot-password" -> forgotPassword(request, response);
            default -> throw ApiException.notFound("Khong tim thay endpoint auth.");
        }
    }

    private void login(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Dtos.LoginRequest body = readBody(request, Dtos.LoginRequest.class);
        String email = body.email() == null ? "" : body.email().trim().toLowerCase();
        String password = body.password() == null ? "" : body.password();

        if (email.isBlank() || password.isBlank()) {
            throw ApiException.badRequest("Vui long nhap email va mat khau.");
        }

        String ip = clientIp(request);

        // D6 — cung han muc, cung bang LoginAttempts voi tang JSP. Neu chi siet mot trong hai duong
        // thi ke do mat khau chi viec chuyen sang duong con lai.
        LoginThrottleService.Decision decision = loginThrottle.check(email, ip);
        if (decision.isBlocked()) {
            throw new ApiException(429, "TOO_MANY_ATTEMPTS", decision.getMessage());
        }

        Optional<User> found = userDAO.findByEmail(email);
        if (found.isEmpty() || !PasswordUtil.matches(password, found.get().getPasswordHash())) {
            int failures = loginThrottle.recordFailure(email, ip);
            loginThrottle.applyProgressiveDelay(failures);
            // Tra ve cung mot thong bao cho ca hai truong hop de khong lo email nao ton tai.
            throw ApiException.unauthorized("Email hoac mat khau khong dung.");
        }

        User user = found.get();
        if (user.isLocked()) {
            // Mat khau dung nhung tai khoan bi khoa: khong tinh vao bo dem do mat khau,
            // va cung khong duoc cap phien.
            throw ApiException.forbidden("Tai khoan cua ban da bi khoa.");
        }
        loginThrottle.recordSuccess(email, ip);
        AccountStateGuard.invalidate(user.getId());

        // Chong session fixation: huy session cu roi cap session moi sau khi xac thuc thanh cong.
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(AppConstants.SESSION_USER, user);
        String csrfToken = CsrfFilter.rotate(request);
        CsrfFilter.writeCookie(request, response, csrfToken);

        // FLOW-AUTH-REF-004: refresh token roi mang bang cookie HttpOnly, khong bao gio vao JSON.
        RefreshTokenService.IssuedRefreshToken refresh =
                refreshTokens.issue(user.getId(), ip);
        writeRefreshCookie(request, response, refresh.token());

        // BUG-15: khong con phat accessToken. Xem ghi chu o cuoi lop.
        writeData(response, new Dtos.SessionDto(DtoMapper.user(user), csrfToken));
    }

    private void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Thu hoi truoc khi huy session: sau invalidate() van con cookie refresh trong trinh duyet,
        // bo qua no la de lai mot credential dai han cho mot phien da dong.
        refreshTokens.revokeFamilyOf(readRefreshCookie(request), "logout");
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        clearRefreshCookie(request, response);
        // Cap token moi cho session an danh ke tiep de client con thao tac tiep duoc.
        String csrfToken = CsrfFilter.rotate(request);
        CsrfFilter.writeCookie(request, response, csrfToken);
        writeData(response, new Dtos.CsrfDto(csrfToken));
    }

    /**
     * FLOW-AUTH-REF-005/009/010 — doi refresh token lay the he ke tiep.
     *
     * <p>Moi ly do tu choi (khong ton tai, het han, da dung, family da bi thu hoi) deu tra
     * cung mot 401 va cung mot cau. Phan biet chung se bien endpoint nay thanh cong cu do
     * trang thai token cua nguoi khac.</p>
     *
     * <p>Khong xoa cookie o nhanh tu choi: {@code BaseApiServlet} goi {@code response.reset()}
     * truoc khi ghi envelope loi, nen moi header dat truoc do deu bi bo. Client tu bo cookie
     * khi nhan 401.</p>
     */
    private void refresh(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String presented = readRefreshCookie(request);
        RefreshTokenService.IssuedRefreshToken rotated = refreshTokens
                .rotate(presented, clientIp(request))
                .orElseThrow(() -> ApiException.unauthorized("Phien dang nhap khong con hieu luc."));

        User user = userDAO.findById(rotated.userId())
                .orElseThrow(() -> ApiException.unauthorized("Phien dang nhap khong con hieu luc."));
        if (user.isLocked()) {
            // Tai khoan bi khoa giua chung: cat toan bo phien thay vi cap tiep access token.
            refreshTokens.revokeAllForUser(user.getId(), "account-locked");
            throw ApiException.forbidden("Tai khoan cua ban da bi khoa.");
        }

        writeRefreshCookie(request, response, rotated.token());
        writeData(response, new Dtos.SessionDto(DtoMapper.user(user),
                CsrfFilter.currentToken(request)));
    }

    /**
     * FLOW-AUTH-REF-006/011 — dang xuat khoi moi thiet bi.
     *
     * <p>Danh tinh lay theo thu tu: session JSP dang mo, roi chinh refresh cookie dang cam. Client
     * REST thuan tuy khong co session nhung van cam cookie refresh, nen endpoint nay van dung duoc
     * o cho can no nhat.</p>
     *
     * <p>BUG-15: da bo nhanh nhan dien bang Bearer. Xem ghi chu cuoi lop.</p>
     */
    private void logoutAll(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = resolveActorId(request);
        refreshTokens.revokeAllForUser(userId, "logout-all");
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        clearRefreshCookie(request, response);
        String csrfToken = CsrfFilter.rotate(request);
        CsrfFilter.writeCookie(request, response, csrfToken);
        writeData(response, new Dtos.MessageDto("Da dang xuat khoi moi thiet bi."));
    }

    /**
     * BUG-15 — <b>da bo hoan toan viec phat JWT access token.</b>
     *
     * <p>Hien trang truoc khi bo: {@code POST /api/v1/auth/login|refresh} phat mot JWT HS256 TTL
     * 600s, nhung {@code AuthFilter} khong bao gio doc header {@code Authorization} — token do
     * <b>khong mo duoc route nao</b>. No chi con mot cong dung duy nhat la nhan dien nguoi goi
     * {@code POST /logout-all}, tuc la mot credential chi du suc dang xuat nguoi khac neu bi lo.</p>
     *
     * <p>Hai huong da can nhac:</p>
     * <ul>
     *   <li>Cho {@code AuthFilter} chap nhan Bearer — nhung khi do BAT BUOC phai kiem
     *       {@code tokenVersion} voi {@code Users.UpdatedAt} va chay {@code AccountStateGuard} cho
     *       duong Bearer, neu khong se tao ra dung lo "JWT cu giu quyen cu" ma hien chua co.</li>
     *   <li><b>Bo han</b> — huong da chon. Khong client nao dung no: tang Next.js di qua BFF proxy
     *       bang cookie, tang JSP dung session. Phat mot credential khong ai dung la be mat tan
     *       cong vo ich.</li>
     * </ul>
     *
     * <p>Xac thuc cua he thong tu day chi con <b>session {@code JSESSIONID}</b> +
     * <b>refresh token opaque</b> (xoay vong theo family). {@code logout-all} nhan dien nguoi goi
     * bang session hoac chinh cookie refresh dang cam.</p>
     */
    private int resolveActorId(HttpServletRequest request) {
        User sessionUser = currentUser(request);
        if (sessionUser != null) {
            return sessionUser.getId();
        }
        return refreshTokens.ownerOfUsableToken(readRefreshCookie(request))
                .orElseThrow(() -> ApiException.unauthorized("Vui long dang nhap."));
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (RefreshTokenService.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * Phat cookie refresh token.
     *
     * <p><b>Vi sao viet tay header.</b> Servlet 4.0 khong co API {@code SameSite}, va Tomcat chi
     * ap {@code sameSiteCookies} cho cookie tao bang {@link Cookie}. Refresh token la credential
     * dai han nen phai co du {@code HttpOnly}, {@code Secure}, {@code SameSite=Strict}. Cung ly do
     * voi {@code CsrfFilter.buildCookieHeader}.</p>
     *
     * <p><b>Vi sao hai duong dan.</b> Design chot {@code Path=/api/v1/auth} — dung khi ung dung
     * chay o context goc. Ban deploy hien tai nam duoi {@code /Website-ban-ve-xem-phim}, va
     * trinh duyet doi chieu {@code Path} voi duong dan DAY DU, nen mot cookie
     * {@code Path=/api/v1/auth} se khong bao gio duoc gui toi
     * {@code /Website-ban-ve-xem-phim/api/v1/auth/refresh} — do kiem chung bang HTTP that: 401
     * vi request khong he mang cookie. Phat them ban co tien to context path lam luong refresh
     * chay duoc o ca hai kieu trien khai ma van khong noi rong pham vi: ca hai deu chi tro toi
     * nhom endpoint auth.</p>
     */
    private void writeRefreshCookie(HttpServletRequest request, HttpServletResponse response,
            String token) {
        long maxAge = RefreshTokenService.ABSOLUTE_LIFETIME_DAYS * 86_400L;
        for (String path : refreshCookiePaths(request)) {
            response.addHeader("Set-Cookie", RefreshTokenService.COOKIE_NAME + "=" + token
                    + "; Path=" + path + "; Max-Age=" + maxAge
                    + "; HttpOnly; Secure; SameSite=Strict");
        }
    }

    private void clearRefreshCookie(HttpServletRequest request, HttpServletResponse response) {
        for (String path : refreshCookiePaths(request)) {
            response.addHeader("Set-Cookie", RefreshTokenService.COOKIE_NAME + "="
                    + "; Path=" + path + "; Max-Age=0; HttpOnly; Secure; SameSite=Strict");
        }
    }

    /**
     * Duong dan cookie: pham vi chuan cua design truoc, ban co tien to context path sau.
     * Deploy o context goc thi hai duong dan trung nhau va chi phat mot cookie.
     */
    private List<String> refreshCookiePaths(HttpServletRequest request) {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String scoped = contextPath + RefreshTokenService.COOKIE_PATH;
        return scoped.equals(RefreshTokenService.COOKIE_PATH)
                ? List.of(RefreshTokenService.COOKIE_PATH)
                : List.of(RefreshTokenService.COOKIE_PATH, scoped);
    }

    private void register(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Dtos.RegisterRequest body = readBody(request, Dtos.RegisterRequest.class);
        String fullName = body.fullName() == null ? "" : body.fullName().trim();
        String email = body.email() == null ? "" : body.email().trim().toLowerCase();
        String password = body.password() == null ? "" : body.password();
        String confirmPassword = body.confirmPassword() == null ? "" : body.confirmPassword();

        if (fullName.isBlank() || email.isBlank()) {
            throw ApiException.badRequest("Vui long nhap day du ho ten va email.");
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw ApiException.badRequest("Email khong dung dinh dang.");
        }
        if (!password.equals(confirmPassword)) {
            throw ApiException.badRequest("Mat khau xac nhan khong khop.");
        }
        // D10 — dung chinh sach voi tang JSP: mot noi dinh nghia, hai duong vao cung tuan theo.
        PasswordPolicy.Result policy =
                PasswordPolicy.validate(password, SecuritySettings.passwordMinLength(), email);
        if (!policy.isValid()) {
            throw ApiException.badRequest(policy.getMessage());
        }
        if (userDAO.findByEmail(email).isPresent()) {
            throw ApiException.conflict("Email da duoc su dung.");
        }

        User user = new User();
        user.setFullName(fullName);
        int atIndex = email.indexOf('@');
        user.setUsername(atIndex > 0 ? email.substring(0, atIndex) : email);
        user.setEmail(email);
        user.setPasswordHash(PasswordUtil.hash(password));
        user.setRole(AppConstants.ROLE_MEMBER);
        int newId = userDAO.create(user);
        user.setId(newId);

        writeCreated(response, DtoMapper.user(user));
    }

    /**
     * D11 — khong con tra 501. Sinh phieu that, ghi link ra {@code cinebook-mail.log}
     * (P18 se thay bang email that).
     *
     * <p>Luon tra <b>200 voi cung mot cau</b> du email co ton tai hay khong: tra 404 cho email
     * khong ton tai la bien endpoint nay thanh may liet ke tai khoan.</p>
     */
    private void forgotPassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Dtos.ForgotPasswordRequest body = readBody(request, Dtos.ForgotPasswordRequest.class);
        String email = body.email() == null ? "" : body.email().trim().toLowerCase();
        if (!email.isBlank()) {
            passwordResetService.requestReset(email, clientIp(request), resetUrlBase(request));
        }
        writeData(response, new Dtos.MessageDto(PasswordResetService.GENERIC_RESPONSE));
    }

    /** Xem ghi chu o {@code AuthServlet.clientIp}: co tinh khong tin {@code X-Forwarded-For}. */
    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote;
    }

    /** Link dat lai mat khau tro ve man hinh JSP — tang REST chua co man hinh rieng. */
    private String resetUrlBase(HttpServletRequest request) {
        StringBuffer url = request.getRequestURL();
        String path = request.getRequestURI();
        String origin = url.substring(0, url.length() - path.length());
        return origin + request.getContextPath() + "/reset-password";
    }
}

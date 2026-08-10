package com.mycompany.website.ban.ve.xem.phim.controller.auth;

import com.mycompany.website.ban.ve.xem.phim.api.CsrfFilter;
import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.config.SecuritySettings;
import com.mycompany.website.ban.ve.xem.phim.config.SettingsReader;
import com.mycompany.website.ban.ve.xem.phim.dao.UserDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserDAO;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AccountStateGuard;
import com.mycompany.website.ban.ve.xem.phim.service.LoginThrottleService;
import com.mycompany.website.ban.ve.xem.phim.service.PasswordResetService;
import com.mycompany.website.ban.ve.xem.phim.service.EmailService;
import com.mycompany.website.ban.ve.xem.phim.util.PasswordPolicy;
import com.mycompany.website.ban.ve.xem.phim.util.PasswordUtil;
import com.mycompany.website.ban.ve.xem.phim.util.ServletUtil;
import java.io.IOException;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class AuthServlet extends HttpServlet {
    private final UserDAO userDAO = new JdbcUserDAO();
    private final LoginThrottleService loginThrottle = new LoginThrottleService();
    private final PasswordResetService passwordResetService = new PasswordResetService();
    private final EmailService emailService = new EmailService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ServletUtil.consumeFlash(request);
        String path = request.getServletPath();
        switch (path) {
            case "/register" -> forward(request, response, "auth/register.jsp");
            case "/forgot-password" -> forward(request, response, "auth/forgot-password.jsp");
            case "/reset-password" -> showResetForm(request, response);
            case "/verify-email" -> verifyEmail(request, response);
            case "/logout" -> logout(request, response);
            default -> {
                request.setAttribute("returnUrl", request.getParameter("returnUrl"));
                forward(request, response, "auth/login.jsp");
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        switch (path) {
            case "/register" -> register(request, response);
            case "/forgot-password" -> forgotPassword(request, response);
            case "/reset-password" -> resetPassword(request, response);
            case "/resend-verification" -> resendVerification(request, response);
            case "/logout" -> logout(request, response);
            default -> login(request, response);
        }
    }

    private void login(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            String email = ServletUtil.param(request, "email").toLowerCase();
            String password = ServletUtil.param(request, "password");
            String returnUrl = request.getParameter("returnUrl");
            String ip = clientIp(request);

            // D6 — kiem tra han muc TRUOC khi so mat khau. So truoc thi moi lan do lai ton mot phep
            // BCrypt cost 12, tuc ke tan cong dieu khien duoc tai CPU cua may chu.
            LoginThrottleService.Decision decision = loginThrottle.check(email, ip);
            if (decision.isBlocked()) {
                request.setAttribute(AppConstants.FLASH_ERROR, decision.getMessage());
                request.setAttribute("returnUrl", returnUrl);
                forward(request, response, "auth/login.jsp");
                return;
            }

            Optional<User> user = userDAO.findByEmail(email);
            if (user.isEmpty() || !PasswordUtil.matches(password, user.get().getPasswordHash())) {
                int failures = loginThrottle.recordFailure(email, ip);
                loginThrottle.applyProgressiveDelay(failures);
                // Mot thong bao duy nhat cho ca "khong co email" lan "sai mat khau": noi khac di
                // la bien trang dang nhap thanh may do email hop le.
                request.setAttribute(AppConstants.FLASH_ERROR, "Email hoac mat khau khong dung.");
                request.setAttribute("returnUrl", returnUrl);
                forward(request, response, "auth/login.jsp");
                return;
            }

            if (user.get().isLocked()) {
                String reason = user.get().getLockReason() != null ? user.get().getLockReason() : "Vi phạm quy định cộng đồng";
                request.setAttribute(AppConstants.FLASH_ERROR, "🔒 TÀI KHOẢN CỦA BẠN ĐÃ BỊ KHÓA! Nguyên nhân: " + reason + " (Số lần cảnh cáo: " + user.get().getWarningCount() + "/3). Vui lòng gửi đơn Kháng cáo để Admin xem xét mở khóa.");
                request.setAttribute("lockedUserEmail", user.get().getEmail());
                request.setAttribute("isLockedAccount", true);
                request.setAttribute("returnUrl", returnUrl);
                forward(request, response, "auth/login.jsp");
                return;
            }

            loginThrottle.recordSuccess(email, ip);
            // Phien moi phai doc lai trang thai tai khoan tu DB ngay, khong dung ket qua cache cua
            // phien truoc do (vi du vua duoc mo khoa xong dang nhap lai).
            AccountStateGuard.invalidate(user.get().getId());

            // D2 - chong session fixation: doi session id TRUOC khi gan nguoi dung vao session,
            // roi sinh token CSRF moi va phat lai cookie. Thieu buoc phat cookie thi moi thao tac
            // ghi ngay sau khi dang nhap se bi CsrfFilter tu choi 403.
            newSessionForLogin(request);
            request.getSession().setAttribute(AppConstants.SESSION_USER, user.get());
            CsrfFilter.writeCookie(request, response, CsrfFilter.rotate(request));

            if (returnUrl != null && !returnUrl.isBlank()) {
                response.sendRedirect(request.getContextPath() + returnUrl);
            } else if (AppConstants.ROLE_ADMIN.equals(user.get().getRole())) {
                response.sendRedirect(request.getContextPath() + "/system/dashboard");
            } else if (AppConstants.ROLE_MANAGER.equals(user.get().getRole())) {
                response.sendRedirect(request.getContextPath() + "/admin/dashboard");
            } else if (AppConstants.ROLE_STAFF.equals(user.get().getRole())) {
                response.sendRedirect(request.getContextPath() + "/staff/checkin");
            } else {
                response.sendRedirect(request.getContextPath() + "/home");
            }
        } catch (RuntimeException ex) {
            request.setAttribute(AppConstants.FLASH_ERROR, "Khong the dang nhap vi DB chua san sang. Kiem tra file db.properties.");
            forward(request, response, "auth/login.jsp");
        }
    }

    private void register(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        String fullName = ServletUtil.param(request, "fullName");
        String email = ServletUtil.param(request, "email").toLowerCase();
        String password = ServletUtil.param(request, "password");
        String confirmPassword = ServletUtil.param(request, "confirmPassword");

        if (fullName.isBlank() || email.isBlank()) {
            request.setAttribute(AppConstants.FLASH_ERROR, "Vui long nhap day du ho ten va email.");
            request.setAttribute("formFullName", fullName);
            request.setAttribute("formEmail", email);
            forward(request, response, "auth/register.jsp");
            return;
        }
        if (!password.equals(confirmPassword)) {
            request.setAttribute(AppConstants.FLASH_ERROR, "Mat khau xac nhan khong khop.");
            request.setAttribute("formFullName", fullName);
            request.setAttribute("formEmail", email);
            forward(request, response, "auth/register.jsp");
            return;
        }
        // D10 — chinh sach do manh chi ap cho mat khau MOI. Nguoi dung cu khong bi buoc doi ngay.
        PasswordPolicy.Result policy =
                PasswordPolicy.validate(password, SecuritySettings.passwordMinLength(), email);
        if (!policy.isValid()) {
            request.setAttribute(AppConstants.FLASH_ERROR, policy.getMessage());
            request.setAttribute("formFullName", fullName);
            request.setAttribute("formEmail", email);
            forward(request, response, "auth/register.jsp");
            return;
        }
        try {
            if (userDAO.findByEmail(email).isPresent()) {
                request.setAttribute(AppConstants.FLASH_ERROR, "Email da duoc su dung.");
                forward(request, response, "auth/register.jsp");
                return;
            }

            User user = new User();
            user.setFullName(fullName);
            user.setUsername(email.substring(0, email.indexOf("@") > 0 ? email.indexOf("@") : email.length()));
            user.setEmail(email);
            user.setPasswordHash(PasswordUtil.hash(password));
            user.setRole(AppConstants.ROLE_MEMBER);
            int userId = userDAO.create(user);
            emailService.createAndSendVerification(userId, user.getEmail(), user.getFullName(),
                    applicationBase(request));
            ServletUtil.flashSuccess(request, "Đăng ký thành công. Hãy kiểm tra email để xác thực tài khoản.");
            response.sendRedirect(request.getContextPath() + "/login");
        } catch (RuntimeException ex) {
            request.setAttribute(AppConstants.FLASH_ERROR, "Khong the dang ky vi DB chua san sang. Kiem tra file db.properties.");
            forward(request, response, "auth/register.jsp");
        }
    }

    /**
     * Xac nhan email tu link trong thu.
     *
     * <p><b>Loi da sua (EM-01).</b> Ban cu cap nhat DB roi day thang ve {@code /login}. Nguoi
     * dung <i>dang dang nhap</i> bam link se: (a) bi day ve trang dang nhap du dang co phien, va
     * (b) van thay banner "chua xac thuc" vi doi tuong {@code User} trong session la ban chup
     * cu, khong biet DB vua doi. Nay sau khi xac thuc thanh cong, neu dung tai khoan dang dang
     * nhap thi nap lai user vao session va dua ve trang chu — banner bien mat ngay.</p>
     */
    private void verifyEmail(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean verified = emailService.verifyEmail(request.getParameter("token"));
        User current = (User) request.getSession().getAttribute(AppConstants.SESSION_USER);

        if (!verified) {
            ServletUtil.flashError(request, "Liên kết xác thực email không hợp lệ hoặc đã được dùng.");
            response.sendRedirect(request.getContextPath() + (current == null ? "/login" : "/home"));
            return;
        }

        if (current != null) {
            // Dong bo lai ban chup trong session voi DB.
            userDAO.findById(current.getId()).ifPresent(fresh ->
                    request.getSession().setAttribute(AppConstants.SESSION_USER, fresh));
            ServletUtil.flashSuccess(request, "Email của bạn đã được xác thực thành công.");
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }
        ServletUtil.flashSuccess(request, "Email đã được xác thực. Bạn có thể đăng nhập.");
        response.sendRedirect(request.getContextPath() + "/login");
    }

    /**
     * Gui lai email xac thuc cho chinh tai khoan dang dang nhap (EM-01).
     *
     * <p>La POST nen di qua {@code CsrfFilter} (da map {@code /*}). Bat buoc dang nhap: khong
     * nhan email tu form, nen khong the dung endpoint nay de do xem mot email co ton tai hay
     * khong. Thong bao tra ve khong tiet lo dia chi email.</p>
     */
    private void resendVerification(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        User current = (User) request.getSession().getAttribute(AppConstants.SESSION_USER);
        if (current == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        int cooldown = SettingsReader.readInt("security.verifyResendCooldownSeconds", 120, 0, 3600);
        EmailService.ResendOutcome outcome =
                emailService.resendVerification(current.getId(), applicationBase(request), cooldown);

        switch (outcome) {
            case SENT -> ServletUtil.flashSuccess(request,
                    "Đã gửi lại email xác thực. Vui lòng kiểm tra hộp thư (kể cả mục Spam).");
            case ALREADY_VERIFIED -> {
                userDAO.findById(current.getId()).ifPresent(fresh ->
                        request.getSession().setAttribute(AppConstants.SESSION_USER, fresh));
                ServletUtil.flashSuccess(request, "Email của bạn đã được xác thực rồi.");
            }
            case COOLDOWN -> ServletUtil.flashError(request,
                    "Bạn vừa yêu cầu gửi email xác thực. Vui lòng đợi " + cooldown
                    + " giây rồi thử lại.");
            // Da phu het ResendOutcome. Nhanh nay chi chay neu them ket qua moi ma quen
            // xu ly — bao loi con hon im lang khong phan hoi gi cho nguoi dung.
            default -> throw new IllegalStateException("Ket qua gui lai email chua duoc xu ly: " + outcome);
        }
        redirectBack(request, response);
    }

    /**
     * Quay lai trang truoc do, mac dinh la trang chu.
     *
     * <p><b>Loi da sua (ghi chu kem N-15).</b> Ban cu chuyen huong THANG toi header
     * {@code Referer} chua kiem chung: mot trang ngoai co the dua nguoi dung bam nut o day roi
     * nhan lai ho tren mien cua ke tan cong — mau open redirect kinh dien. CSRF lam giam kha
     * nang khai thac nhung khong dong duoc lo hong, vi {@code Referer} do TRINH DUYET gui va
     * khong nam trong pham vi bao ve cua CSRF token.</p>
     *
     * <p>Nay chi chap nhan duong dan NOI BO cung context path. Moi thu khac — host la, duong
     * dan giao thuc ({@code //evil.com}), ky tu xuong dong de tach header — deu roi ve trang chu.</p>
     */
    private void redirectBack(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(internalPathOrHome(request, request.getHeader("Referer")));
    }

    /** Duong dan noi bo lay tu {@code referer}, hoac {@code /home} neu khong tin duoc. */
    static String internalPathOrHome(HttpServletRequest request, String referer) {
        return ServletUtil.internalPathOrFallback(request, referer, "/home");
    }

    private String applicationBase(HttpServletRequest request) {
        StringBuilder base = new StringBuilder(request.getScheme()).append("://")
                .append(request.getServerName());
        boolean defaultPort = ("http".equals(request.getScheme()) && request.getServerPort() == 80)
                || ("https".equals(request.getScheme()) && request.getServerPort() == 443);
        if (!defaultPort) base.append(':').append(request.getServerPort());
        return base.append(request.getContextPath()).toString();
    }

    /**
     * D11 — quen mat khau that.
     *
     * <p>Tra ve <b>dung mot</b> cau tra loi cho moi truong hop: co email hay khong, tai khoan bi
     * khoa hay khong. Phan biet trong thong bao la bien man hinh nay thanh may liet ke email
     * co that trong he thong.</p>
     *
     * <p>Giai doan nay link duoc ghi ra {@code ${catalina.base}/logs/cinebook-mail.log},
     * chua gui email — P18 se thay bang {@code EmailService}.</p>
     */
    private void forgotPassword(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String email = ServletUtil.param(request, "email").toLowerCase();
        try {
            passwordResetService.requestReset(email, clientIp(request), resetUrlBase(request));
        } catch (RuntimeException ex) {
            // Khong lo chi tiet ky thuat ra man hinh, nhung cung khong gia vo thanh cong:
            // ghi log day du roi bao mot cau trung tinh.
            log("Khong tao duoc phieu dat lai mat khau", ex);
            ServletUtil.flashError(request, "He thong dang ban, vui long thu lai sau it phut.");
            response.sendRedirect(request.getContextPath() + "/forgot-password");
            return;
        }
        ServletUtil.flashSuccess(request, PasswordResetService.GENERIC_RESPONSE);
        response.sendRedirect(request.getContextPath() + "/forgot-password");
    }

    /** Man hinh nhap mat khau moi. Kiem phieu ngay o day de khong bat nguoi dung go xong moi biet hong. */
    private void showResetForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String token = ServletUtil.param(request, "token");
        boolean usable = passwordResetService.isTokenUsable(token);
        if (!usable) {
            request.setAttribute(AppConstants.FLASH_ERROR,
                    "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Hãy gửi lại yêu cầu.");
        }
        request.setAttribute("resetToken", token);
        request.setAttribute("tokenUsable", usable);
        request.setAttribute("passwordMinLength", SecuritySettings.passwordMinLength());
        forward(request, response, "auth/reset-password.jsp");
    }

    private void resetPassword(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String token = ServletUtil.param(request, "token");
        String password = ServletUtil.param(request, "password");
        String confirmPassword = ServletUtil.param(request, "confirmPassword");

        PasswordResetService.ResetResult result;
        try {
            result = passwordResetService.consume(token, password, confirmPassword);
        } catch (RuntimeException ex) {
            log("Khong doi duoc mat khau bang phieu", ex);
            request.setAttribute(AppConstants.FLASH_ERROR, "He thong dang ban, vui long thu lai sau it phut.");
            request.setAttribute("resetToken", token);
            request.setAttribute("tokenUsable", true);
            request.setAttribute("passwordMinLength", SecuritySettings.passwordMinLength());
            forward(request, response, "auth/reset-password.jsp");
            return;
        }

        if (result.isSuccess()) {
            ServletUtil.flashSuccess(request, result.getMessage());
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        request.setAttribute(AppConstants.FLASH_ERROR, result.getMessage());
        request.setAttribute("resetToken", token);
        // Phieu chi bi tieu khi doi mat khau THANH CONG (service rollback o cac nhanh con lai),
        // nen van cho go lai tren chinh man hinh nay.
        request.setAttribute("tokenUsable", true);
        request.setAttribute("passwordMinLength", SecuritySettings.passwordMinLength());
        forward(request, response, "auth/reset-password.jsp");
    }

    /**
     * Dia chi IP dung cho bo dem chan do mat khau.
     *
     * <p>Co tinh <b>khong</b> doc {@code X-Forwarded-For}: header do client gui thi client sua duoc,
     * nen tin no la tu tay trao cho ke do mat khau mot bo dem moi sau moi request. Khi trien khai
     * sau reverse proxy, hay bat {@code RemoteIpValve} cua Tomcat — luc do {@code getRemoteAddr()}
     * tra ve dung IP that va doan ma nay khong can doi.</p>
     */
    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote;
    }

    /** URL tuyet doi cua man hinh dat lai mat khau, de ghi vao "email". */
    private String resetUrlBase(HttpServletRequest request) {
        StringBuffer url = request.getRequestURL();
        String path = request.getRequestURI();
        String origin = url.substring(0, url.length() - path.length());
        return origin + request.getContextPath() + "/reset-password";
    }

    /**
     * Cap session id moi cho phien vua xac thuc.
     *
     * <p>{@code changeSessionId()} giu nguyen thuoc tinh session (ke ca token CSRF) va chi doi id -
     * dung cai can de vo hieu id ma ke tan cong da gieo truoc do. Neu chua co session nao thi tao
     * moi la du.</p>
     */
    private void newSessionForLogin(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }
    }

    /**
     * Huy phien roi cap ngay token CSRF cho phien an danh ke tiep.
     *
     * <p>Khong cap lai token thi trang dang nhap hien ra sau do se mang token cua session da chet,
     * va cu bam "Dang nhap" dau tien bi 403.</p>
     */
    private void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        CsrfFilter.writeCookie(request, response, CsrfFilter.rotate(request));
        response.sendRedirect(request.getContextPath() + "/login");
    }

    private void forward(HttpServletRequest request, HttpServletResponse response, String view)
            throws ServletException, IOException {
        request.getRequestDispatcher("/WEB-INF/views/" + view).forward(request, response);
    }
}

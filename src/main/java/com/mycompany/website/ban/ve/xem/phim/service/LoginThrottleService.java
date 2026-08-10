package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.config.SecuritySettings;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.LoginAttemptDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcLoginAttemptDAO;
import com.mycompany.website.ban.ve.xem.phim.model.LoginAttemptSummary;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Chan do mat khau (D6): dem so lan sai theo <b>email</b> va theo <b>dia chi IP</b>,
 * vuot nguong thi tu choi tam thoi.
 *
 * <p>Khong dung CAPTCHA (can dich vu ngoai, ke hoach P10 cam), chi rate limit + do tre tang dan.</p>
 *
 * <p>Dem theo ca hai chieu vi moi chieu bit mot lo hong:</p>
 * <ul>
 *   <li><b>Theo email</b>: chan ke do mat khau cua mot tai khoan tu nhieu may.</li>
 *   <li><b>Theo IP</b>: chan ke rai mot mat khau pho bien len hang tram email khac nhau —
 *       kieu tan cong ma bo dem theo email khong bao gio thay.</li>
 * </ul>
 *
 * <p>Nguong IP duoc noi rong gap 4 lan nguong email: mot van phong hay mot nha mang NAT co the
 * co nhieu nguoi dung that cung dia chi.</p>
 */
public class LoginThrottleService {
    private static final Logger LOG = Logger.getLogger(LoginThrottleService.class.getName());

    /** He so noi rong nguong cho IP dung chung (NAT, wifi cong ty). */
    private static final int IP_LIMIT_FACTOR = 4;

    /** Do tre cong them cho moi lan sai, va tran tren de khong giu thread Tomcat qua lau. */
    private static final long DELAY_STEP_MS = 250L;
    private static final long DELAY_MAX_MS = 2_000L;

    private final LoginAttemptDAO loginAttemptDAO;

    public LoginThrottleService() {
        this(new JdbcLoginAttemptDAO());
    }

    public LoginThrottleService(LoginAttemptDAO loginAttemptDAO) {
        this.loginAttemptDAO = loginAttemptDAO;
    }

    /** Ket qua kiem tra truoc khi cho phep thu mat khau. */
    public static final class Decision {
        private final boolean blocked;
        private final int retryAfterSeconds;
        private final int failures;

        private Decision(boolean blocked, int retryAfterSeconds, int failures) {
            this.blocked = blocked;
            this.retryAfterSeconds = retryAfterSeconds;
            this.failures = failures;
        }

        public boolean isBlocked() {
            return blocked;
        }

        public int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }

        public int getRetryAfterMinutes() {
            return (retryAfterSeconds + 59) / 60;
        }

        public int getFailures() {
            return failures;
        }

        /**
         * Thong bao cho nguoi dung. Khong noi la email co ton tai hay khong, cung khong noi con
         * bao nhieu lan thu — chi noi da bi chan va bao lau nua duoc thu lai.
         */
        public String getMessage() {
            int minutes = Math.max(1, getRetryAfterMinutes());
            return "Bạn đã thử đăng nhập sai quá nhiều lần. Vui lòng thử lại sau " + minutes + " phút.";
        }
    }

    /**
     * Kiem tra xem email/IP nay con duoc phep thu mat khau khong.
     *
     * <p>Goi <b>truoc</b> khi so mat khau, de mot lan thu bi chan khong ton mot phep BCrypt
     * (cost 12 — chinh la thu ma ke tan cong muon bat may chu lam that nhieu).</p>
     */
    public Decision check(String email, String ipAddress) {
        int maxAttempts = SecuritySettings.maxLoginAttempts();
        int windowMinutes = SecuritySettings.lockMinutes();

        try (Connection connection = DBConnection.getConnection()) {
            LoginAttemptSummary byEmail = loginAttemptDAO.summarizeByEmail(connection, email, windowMinutes);
            if (byEmail.getFailures() >= maxAttempts) {
                LOG.warning("Dang nhap bi chan tam thoi (nguong email): email=" + mask(email)
                        + ", so lan sai=" + byEmail.getFailures() + ", con " + byEmail.getUnlockInSeconds() + "s.");
                return new Decision(true, byEmail.getUnlockInSeconds(), byEmail.getFailures());
            }

            LoginAttemptSummary byIp = loginAttemptDAO.summarizeByIp(connection, ipAddress, windowMinutes);
            int ipLimit = maxAttempts * IP_LIMIT_FACTOR;
            if (byIp.getFailures() >= ipLimit) {
                LOG.warning("Dang nhap bi chan tam thoi (nguong IP): ip=" + ipAddress
                        + ", so lan sai=" + byIp.getFailures() + ", con " + byIp.getUnlockInSeconds() + "s.");
                return new Decision(true, byIp.getUnlockInSeconds(), byIp.getFailures());
            }

            return new Decision(false, 0, byEmail.getFailures());
        } catch (SQLException ex) {
            // Khong nuot: nem tiep de tang tren bao "DB chua san sang". Cho qua im lang o day
            // dong nghia voi vo hieu hoa chong do mat khau moi khi DB tro chung.
            throw new DaoException("Cannot evaluate login throttle", ex);
        }
    }

    /** Ghi lai mot lan sai va tra ve so lan sai lien tiep sau khi ghi. */
    public int recordFailure(String email, String ipAddress) {
        int windowMinutes = SecuritySettings.lockMinutes();
        try (Connection connection = DBConnection.getConnection()) {
            loginAttemptDAO.record(connection, email, ipAddress, false);
            return loginAttemptDAO.summarizeByEmail(connection, email, windowMinutes).getFailures();
        } catch (SQLException ex) {
            throw new DaoException("Cannot record failed login attempt", ex);
        }
    }

    /** Ghi lai lan dang nhap dung — bo dem tu ve 0 vi moi phep dem chi tinh sau moc nay. */
    public void recordSuccess(String email, String ipAddress) {
        loginAttemptDAO.record(email, ipAddress, true);
    }

    /**
     * Do tre tang dan sau moi lan sai: 250ms, 500ms, ... toi da 2 giay.
     *
     * <p>Muc dich la lam cham may do tu dong ma nguoi go nham mot lan gan nhu khong cam nhan duoc.
     * Tran 2 giay de khong giu thread Tomcat lam mot kieu tu lam nghen chinh minh.</p>
     */
    public void applyProgressiveDelay(int failures) {
        if (failures <= 0) {
            return;
        }
        long delay = Math.min(failures * DELAY_STEP_MS, DELAY_MAX_MS);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Che phan dinh danh khi ghi log — log khong phai cho de lo email nguoi dung. */
    private String mask(String email) {
        if (email == null || email.isBlank()) {
            return "(rong)";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}

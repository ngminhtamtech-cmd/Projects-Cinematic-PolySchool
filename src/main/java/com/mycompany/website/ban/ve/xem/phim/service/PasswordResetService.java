package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.config.SecuritySettings;
import com.mycompany.website.ban.ve.xem.phim.config.SettingsReader;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.LoginAttemptDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.PasswordResetTokenDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.UserDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcLoginAttemptDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcPasswordResetTokenDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserDAO;
import com.mycompany.website.ban.ve.xem.phim.model.PasswordResetToken;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.util.PasswordPolicy;
import com.mycompany.website.ban.ve.xem.phim.util.PasswordUtil;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Quen mat khau that (D11) — thay cho ham cu chi hien mot dong flash roi khong lam gi.
 *
 * <p>Ba tinh chat bat buoc cua ke hoach P10:</p>
 * <ul>
 *   <li>Token luu duoi dang <b>SHA-256</b>, khong bao gio luu ban tho.</li>
 *   <li>Han <b>30 phut</b> (doc tu {@code security.resetTokenMinutes}), do <b>DB</b> tinh.</li>
 *   <li><b>Dung mot lan</b> — dam bao bang mot cau {@code UPDATE ... OUTPUT} nguyen tu.</li>
 * </ul>
 *
 * <p>Giai doan nay <b>chua gui email</b>: link duoc ghi ra {@code ${catalina.base}/logs/cinebook-mail.log}.
 * P18 se thay cho ghi log bang {@code EmailService} that.</p>
 */
public class PasswordResetService {
    private static final Logger LOG = Logger.getLogger(PasswordResetService.class.getName());

    /** Ten file "hop thu" tam thoi cho toi khi P18 gan SMTP that. */
    public static final String MAIL_LOG_FILE = "cinebook-mail.log";

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Khoang cho toi thieu giua hai lan xin phieu cua cung mot tai khoan, tinh bang giay.
     *
     * <p>Doi xung voi {@code security.verifyResendCooldownSeconds} cua luong xac thuc email.
     * Khi con o che do {@code mail.mode=logfile} thi thieu no vo hai; bat SMTP that thi trang
     * quen mat khau tro thanh may doi bom hop thu nguoi khac, dot het han ngach gui va co the
     * lam khoa luon tai khoan gui — hong ca ba luong cung luc.</p>
     */
    public static final String KEY_REQUEST_COOLDOWN_SECONDS = "security.resetRequestCooldownSeconds";

    /** Mac dinh khi chua co ban ghi trong {@code SystemSettings}. */
    public static final int DEFAULT_REQUEST_COOLDOWN_SECONDS = 120;

    /**
     * Cung mot cau tra loi cho moi truong hop — email ton tai hay khong, tai khoan bi khoa hay khong.
     * Noi khac di la bien trang quen mat khau thanh may do email.
     */
    public static final String GENERIC_RESPONSE =
            "Nếu email này có trong hệ thống, chúng tôi đã gửi hướng dẫn đặt lại mật khẩu. "
            + "Vui lòng kiểm tra hộp thư trong vòng 30 phút.";

    private final UserDAO userDAO;
    private final PasswordResetTokenDAO tokenDAO;
    private final LoginAttemptDAO loginAttemptDAO;

    public PasswordResetService() {
        this(new JdbcUserDAO(), new JdbcPasswordResetTokenDAO(), new JdbcLoginAttemptDAO());
    }

    public PasswordResetService(UserDAO userDAO, PasswordResetTokenDAO tokenDAO, LoginAttemptDAO loginAttemptDAO) {
        this.userDAO = userDAO;
        this.tokenDAO = tokenDAO;
        this.loginAttemptDAO = loginAttemptDAO;
    }

    /** Ket qua doi mat khau bang phieu. */
    public static final class ResetResult {
        private final boolean success;
        private final String message;

        private ResetResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Nhan yeu cau dat lai mat khau.
     *
     * @return token tho <b>chi de test/chan doan</b>; luong web khong duoc hien no ra man hinh.
     *         Rong khi email khong ton tai — nhung nguoi goi phai tra ve {@link #GENERIC_RESPONSE}
     *         trong ca hai truong hop.
     */
    public Optional<String> requestReset(String email, String ipAddress, String resetUrlBase) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        Optional<User> found = userDAO.findByEmail(normalized);
        if (found.isEmpty()) {
            // Van ton mot chut thoi gian giong nhu truong hop co that, va khong he lo ra ngoai.
            LOG.info("Yeu cau dat lai mat khau cho email khong ton tai — bo qua, van tra loi chung.");
            return Optional.empty();
        }

        User user = found.get();
        String rawToken = generateToken();
        String tokenHash = sha256Hex(rawToken);
        int minutes = SecuritySettings.resetTokenMinutes();
        int cooldownSeconds = SettingsReader.readInt(
                KEY_REQUEST_COOLDOWN_SECONDS, DEFAULT_REQUEST_COOLDOWN_SECONDS, 0, 3600);

        boolean issued;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Thu tu co chu y: TAO truoc, vo hieu phieu cu sau.
                //
                // Ban cu vo hieu truoc roi moi tao. Khi them cooldown, thu tu do tro thanh mot cach
                // pha hoai: nguoi dung bam "Gui lai" nham trong 120 giay se bi vo hieu mat lien ket
                // con han o buoc mot, roi buoc hai bi cooldown chan nen khong co lien ket moi —
                // ho mat sach duong vao. Nay chi vo hieu phieu cu KHI da chac chan cap duoc phieu moi.
                issued = tokenDAO.createIfOutsideCooldown(
                        connection, tokenHash, user.getId(), minutes, ipAddress, cooldownSeconds);
                if (issued) {
                    // Mot email chi nen co mot phieu song: xin phieu moi thi phieu cu het gia tri ngay.
                    tokenDAO.invalidateOthersForUser(connection, user.getId(), tokenHash);
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot create password reset token", ex);
        }

        if (!issued) {
            // Con trong thoi gian cho. Khong sinh phieu, khong gui thu — va tuyet doi khong noi
            // gi khac di: nguoi goi van tra ve GENERIC_RESPONSE. Bao "ban vua yeu cau roi" la
            // bien cooldown thanh may do email, dung cai ma GENERIC_RESPONSE sinh ra de chan.
            LOG.info("Yeu cau dat lai mat khau bi chan boi cooldown — khong gui thu, van tra loi chung.");
            return Optional.empty();
        }

        String base = resetUrlBase == null || resetUrlBase.isBlank() ? "/reset-password" : resetUrlBase;
        String link = base + (base.contains("?") ? "&" : "?") + "token=" + rawToken;
        new EmailService().sendPasswordReset(user.getEmail(), user.getFullName(), link, minutes);
        return Optional.of(rawToken);
    }

    /**
     * Kiem tra phieu con dung duoc khong — <b>khong</b> tieu phieu.
     * Dung cho man hinh GET /reset-password de bao som thay vi de nguoi dung go xong mat khau moi biet.
     */
    public boolean isTokenUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        Optional<PasswordResetToken> token = tokenDAO.findByHash(sha256Hex(rawToken));
        if (token.isEmpty() || token.get().isUsed()) {
            return false;
        }
        LocalDateTime now = DBConnection.dbNow();
        LocalDateTime expiresAt = token.get().getExpiresAt();
        return expiresAt != null && expiresAt.isAfter(now);
    }

    /**
     * Doi mat khau bang phieu, trong <b>mot</b> transaction tren <b>mot</b> connection.
     *
     * <p>Thu tu co chu y: tieu phieu truoc, kiem tra chinh sach mat khau sau. Neu mat khau moi
     * khong dat chuan thi <b>rollback</b> — phieu chua bi tieu, nguoi dung go lai mat khau khac
     * bang chinh link cu. Neu lam nguoc lai, moi lan go mat khau yeu la mot lan mat phieu.</p>
     */
    public ResetResult consume(String rawToken, String newPassword, String confirmPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            return new ResetResult(false, "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
        }
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            return new ResetResult(false, "Mật khẩu xác nhận không khớp.");
        }

        String tokenHash = sha256Hex(rawToken);
        int minLength = SecuritySettings.passwordMinLength();

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Integer> userId = tokenDAO.consume(connection, tokenHash);
                if (userId.isEmpty()) {
                    connection.rollback();
                    return new ResetResult(false, "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
                }

                Optional<User> user = userDAO.findById(connection, userId.get());
                if (user.isEmpty()) {
                    connection.rollback();
                    return new ResetResult(false, "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
                }

                PasswordPolicy.Result policy =
                        PasswordPolicy.validate(newPassword, minLength, user.get().getEmail());
                if (!policy.isValid()) {
                    connection.rollback();
                    return new ResetResult(false, policy.getMessage());
                }

                userDAO.changePassword(connection, user.get().getId(), PasswordUtil.hash(newPassword));
                tokenDAO.invalidateAllForUser(connection, user.get().getId());
                // Doi duoc mat khau thi bo luon phan chan do mat khau: chu tai khoan da chung minh
                // minh kiem soat hop thu, khong co ly do bat ho cho het 15 phut.
                loginAttemptDAO.record(connection, user.get().getEmail(), null, true);
                connection.commit();

                LOG.info("Da dat lai mat khau cho userId=" + user.get().getId() + " bang phieu con hieu luc.");
                return new ResetResult(true, "Đã đặt lại mật khẩu. Bạn có thể đăng nhập bằng mật khẩu mới.");
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot consume password reset token", ex);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex thuong, dung 64 ky tu — khop kieu {@code CHAR(64)} cua bang. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 la thuat toan bat buoc cua moi JRE; thieu no thi khong the chay tiep an toan.
            throw new IllegalStateException("SHA-256 khong kha dung tren JRE nay", ex);
        }
    }

    /** {@code ${catalina.base}/logs/} khi chay trong Tomcat; ngoai Tomcat thi rot ve thu muc tam. */
    public static Path mailLogPath() {
        return EmailService.mailLogPath();
    }
}
